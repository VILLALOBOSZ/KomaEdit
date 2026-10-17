package com.komainu.moxia.deform;

import com.komainu.moxia.noise.NoiseSpec;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.SplittableRandom;

/**
 * Deformaciones que remuestrean la selección ({@code /komaedit deform}). Cada una es una función de punto: para
 * un punto del resultado dice de qué punto de la selección original sale su material, o que queda hueco. Al ser
 * continuas, las losas y escaleras de {@code -w} miden la forma deformada dentro de cada bloque.
 */
public final class Deformations {
    private Deformations() {
    }

    /**
     * Columnas hexagonales: el plano perpendicular al eje de las columnas se reparte en celdas de una red y cada
     * columna copia la línea que pasa por el centro de su celda, así quedan columnas de tapa plana.
     *
     * @param offsetAngle ángulo entre los dos vectores de la red: 60 da hexágonos regulares y 90 cuadrados
     */
    public static DeformField hexagonalize(
            Volume volume,
            double size,
            double airGap,
            double xRotation,
            double zRotation,
            double offsetAngle
    ) {
        requirePositive(size, "El tamaño");
        double[] center = center(volume);
        int[] bounds = volume.selectionBounds();
        double[][] frame = rotation(xRotation, zRotation);
        double[] u = frame[0];
        double[] axis = frame[1];
        double[] w = frame[2];
        double angle = Math.toRadians(Math.clamp(offsetAngle, 1.0, 90.0));
        double b2s = size * Math.cos(angle);
        double b2t = size * Math.sin(angle);
        double halfGap = Math.max(0.0, airGap) / 2.0;
        return new Resampled(volume, false) {
            @Override
            int sample(double x, double y, double z) {
                double dx = x - center[0];
                double dy = y - center[1];
                double dz = z - center[2];
                double s = dx * u[0] + dy * u[1] + dz * u[2];
                double t = dx * w[0] + dy * w[1] + dz * w[2];
                double h = dx * axis[0] + dy * axis[1] + dz * axis[2];
                double j = t / b2t;
                double i = (s - j * b2s) / size;
                int baseI = (int) Math.floor(i);
                int baseJ = (int) Math.floor(j);
                double bestS = 0.0;
                double bestT = 0.0;
                double bestDistance = Double.POSITIVE_INFINITY;
                for (int ci = baseI - 1; ci <= baseI + 2; ci++) {
                    for (int cj = baseJ - 1; cj <= baseJ + 2; cj++) {
                        double cs = ci * size + cj * b2s;
                        double ct = cj * b2t;
                        double distance = (s - cs) * (s - cs) + (t - ct) * (t - ct);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestS = cs;
                            bestT = ct;
                        }
                    }
                }
                if (halfGap > 0.0) {
                    for (int ci = baseI - 1; ci <= baseI + 2; ci++) {
                        for (int cj = baseJ - 1; cj <= baseJ + 2; cj++) {
                            double cs = ci * size + cj * b2s;
                            double ct = cj * b2t;
                            double ns = cs - bestS;
                            double nt = ct - bestT;
                            double length = Math.sqrt(ns * ns + nt * nt);
                            if (length < 1.0E-9) {
                                continue;
                            }
                            double edge = length / 2.0 - ((s - bestS) * ns + (t - bestT) * nt) / length;
                            if (edge < halfGap) {
                                return 0;
                            }
                        }
                    }
                }
                // Las celdas del borde pueden tener el centro fuera de la selección: se lee en el punto más
                // cercano de su caja para que esas columnas no desaparezcan.
                return volume.sourceAt(
                        clamp(center[0] + u[0] * bestS + w[0] * bestT + axis[0] * h, bounds[0], bounds[3]),
                        clamp(center[1] + u[1] * bestS + w[1] * bestT + axis[1] * h, bounds[1], bounds[4]),
                        clamp(center[2] + u[2] * bestS + w[2] * bestT + axis[2] * h, bounds[2], bounds[5]));
            }
        };
    }

    /**
     * Desplaza cada punto según tres campos de ruido (uno por eje).
     *
     * @param strength desplazamiento máximo en bloques
     * @param zoom     escala del ruido: 2 hace los rasgos el doble de grandes
     */
    public static DeformField noise(
            Volume volume,
            NoiseSpec spec,
            double strength,
            double zoom,
            long seed,
            boolean horizontalOnly,
            boolean verticalOnly
    ) {
        requirePositive(zoom, "El zoom");
        NoiseSpec.NoiseField fieldX = spec.build(seed);
        NoiseSpec.NoiseField fieldY = spec.build(seed + 0x9E3779B9L);
        NoiseSpec.NoiseField fieldZ = spec.build(seed + 0x3C6EF372L);
        boolean moveHorizontal = !verticalOnly || horizontalOnly;
        boolean moveVertical = !horizontalOnly || verticalOnly;
        return new Resampled(volume, false) {
            @Override
            int sample(double x, double y, double z) {
                double qx = x / zoom;
                double qy = y / zoom;
                double qz = z / zoom;
                double offsetX = moveHorizontal ? strength * (2.0 * fieldX.sample(qx, qy, qz) - 1.0) : 0.0;
                double offsetY = moveVertical ? strength * (2.0 * fieldY.sample(qx + 1_031.7, qy, qz) - 1.0) : 0.0;
                double offsetZ = moveHorizontal ? strength * (2.0 * fieldZ.sample(qx, qy, qz + 2_063.3) - 1.0) : 0.0;
                return volume.sourceAt(x - offsetX, y - offsetY, z - offsetZ);
            }
        };
    }

    /**
     * Gira la selección {@code angle} grados en el sentido de las agujas del reloj mirando a lo largo de
     * {@code axis}, alrededor de {@code center}. Lo que sale de la selección se escribe fuera de ella.
     */
    public static DeformField rotate(Volume volume, double[] axis, double angle, double[] center) {
        double[] unit = normalize(axis);
        double radians = -Math.toRadians(angle);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Resampled(volume, true) {
            @Override
            int sample(double x, double y, double z) {
                double[] rotated = rodrigues(x - center[0], y - center[1], z - center[2], unit, cos, sin);
                return volume.sourceAt(rotated[0] + center[0], rotated[1] + center[1], rotated[2] + center[2]);
            }
        };
    }

    /** Caja que ocupa la selección después de girarla, unida a la propia selección. */
    public static int[] rotatedBounds(int[] selection, double[] axis, double angle, double[] center) {
        double[] unit = normalize(axis);
        double radians = Math.toRadians(angle);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int[] bounds = selection.clone();
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? selection[0] : selection[3] + 1;
            double y = (corner & 2) == 0 ? selection[1] : selection[4] + 1;
            double z = (corner & 4) == 0 ? selection[2] : selection[5] + 1;
            double[] rotated = rodrigues(x - center[0], y - center[1], z - center[2], unit, cos, sin);
            bounds[0] = Math.min(bounds[0], (int) Math.floor(rotated[0] + center[0]) - 1);
            bounds[1] = Math.min(bounds[1], (int) Math.floor(rotated[1] + center[1]) - 1);
            bounds[2] = Math.min(bounds[2], (int) Math.floor(rotated[2] + center[2]) - 1);
            bounds[3] = Math.max(bounds[3], (int) Math.floor(rotated[0] + center[0]) + 1);
            bounds[4] = Math.max(bounds[4], (int) Math.floor(rotated[1] + center[1]) + 1);
            bounds[5] = Math.max(bounds[5], (int) Math.floor(rotated[2] + center[2]) + 1);
        }
        return bounds;
    }

    /**
     * Celdas de Voronoi al azar (un sitio por celda de una rejilla de lado {@code size}): cada celda queda entera
     * si su sitio cae dentro de la forma y desaparece si no. Con hueco, las celdas se separan.
     */
    public static DeformField voronoialize(Volume volume, double size, double airGap, long seed) {
        requirePositive(size, "El tamaño");
        double halfGap = Math.max(0.0, airGap) / 2.0;
        return new Resampled(volume, false) {
            private final double[][] sites = new double[27][];

            @Override
            int sample(double x, double y, double z) {
                int cellX = (int) Math.floor(x / size);
                int cellY = (int) Math.floor(y / size);
                int cellZ = (int) Math.floor(z / size);
                double[] best = null;
                double bestDistance = Double.POSITIVE_INFINITY;
                int count = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            double[] site = site(cellX + dx, cellY + dy, cellZ + dz, size, seed);
                            sites[count++] = site;
                            double distance = squared(site[0] - x, site[1] - y, site[2] - z);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                best = site;
                            }
                        }
                    }
                }
                if (halfGap > 0.0) {
                    for (double[] site : sites) {
                        if (site == best) {
                            continue;
                        }
                        double nx = site[0] - best[0];
                        double ny = site[1] - best[1];
                        double nz = site[2] - best[2];
                        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                        double edge = length / 2.0 - ((x - best[0]) * nx + (y - best[1]) * ny + (z - best[2]) * nz)
                                / length;
                        if (edge < halfGap) {
                            return 0;
                        }
                    }
                }
                int seedMaterial = volume.sourceAt(best[0], best[1], best[2]);
                if (seedMaterial == 0) {
                    return 0;
                }
                int own = volume.sourceAt(x, y, z);
                return own != 0 ? own : seedMaterial;
            }
        };
    }

    /**
     * Celdas de Voronoi repartidas por la superficie: {@code amount} puntos de la superficie, separados entre sí
     * con {@code uniformity} pasadas de repulsión, cada uno con el plano tangente de su zona. El sitio de cada
     * celda se hunde {@code normalOffset} bloques hacia dentro y, dentro de su celda, la forma queda cortada por ese
     * plano: la superficie se vuelve facetas planas que siguen la forma original.
     */
    public static DeformField voronoialize2(
            Volume volume,
            int amount,
            double airGap,
            long seed,
            int uniformity,
            double normalOffset,
            Runnable checkCancelled
    ) {
        if (amount < 1) {
            throw new IllegalArgumentException("Hace falta al menos una celda");
        }
        boolean[] occupancy = new boolean[volume.size()];
        for (int index = 0; index < occupancy.length; index++) {
            occupancy[index] = volume.selected[index] && volume.material[index] != 0;
        }
        boolean[] surface = new boolean[occupancy.length];
        IntArrayList surfaceIndices = new IntArrayList();
        for (int index = 0; index < occupancy.length; index++) {
            checkCancelled.run();
            if (occupancy[index] && exposed(volume, occupancy, index)) {
                surface[index] = true;
                surfaceIndices.add(index);
            }
        }
        if (surfaceIndices.isEmpty()) {
            return new Resampled(volume, false) {
                @Override
                int sample(double x, double y, double z) {
                    return volume.sourceAt(x, y, z);
                }
            };
        }
        int sites = Math.min(Math.min(amount, 4_096), surfaceIndices.size());
        SplittableRandom random = new SplittableRandom(seed);
        int[] chosen = surfaceIndices.toIntArray();
        for (int index = 0; index < sites; index++) {
            int swap = index + random.nextInt(chosen.length - index);
            int value = chosen[index];
            chosen[index] = chosen[swap];
            chosen[swap] = value;
        }
        DistanceTransform.Result toSurface = DistanceTransform.compute(volume, surface, 1.0, 1.0, 1.0);
        double[] points = new double[sites * 3];
        for (int site = 0; site < sites; site++) {
            setCenter(volume, chosen[site], points, site);
        }
        double spacing = Math.max(1.0, Math.sqrt((double) surfaceIndices.size() / sites));
        for (int iteration = 0; iteration < Math.clamp(uniformity, 0, 64); iteration++) {
            checkCancelled.run();
            PointIndex index = new PointIndex(points.clone(), spacing * 2.0);
            double[] moved = new double[points.length];
            int[] pair = new int[2];
            for (int site = 0; site < sites; site++) {
                double x = points[site * 3];
                double y = points[site * 3 + 1];
                double z = points[site * 3 + 2];
                index.nearestTwo(x, y, z, pair);
                double fx = 0.0;
                double fy = 0.0;
                double fz = 0.0;
                int neighbour = pair[0] == site ? pair[1] : pair[0];
                if (neighbour >= 0) {
                    double ox = x - index.x(neighbour);
                    double oy = y - index.y(neighbour);
                    double oz = z - index.z(neighbour);
                    double length = Math.sqrt(ox * ox + oy * oy + oz * oz);
                    double push = Math.max(0.0, spacing * 1.5 - length) * 0.5;
                    if (length < 1.0E-6) {
                        ox = random.nextDouble() - 0.5;
                        oy = random.nextDouble() - 0.5;
                        oz = random.nextDouble() - 0.5;
                        length = Math.sqrt(ox * ox + oy * oy + oz * oz);
                    }
                    fx = ox / length * push;
                    fy = oy / length * push;
                    fz = oz / length * push;
                }
                int snapped = snap(volume, toSurface, x + fx, y + fy, z + fz, chosen[site]);
                chosen[site] = snapped;
                setCenter(volume, snapped, moved, site);
            }
            points = moved;
        }
        double[] planes = new double[sites * 6];
        double[] cells = new double[sites * 3];
        for (int site = 0; site < sites; site++) {
            double[] normal = normal(volume, occupancy, chosen[site]);
            double px = points[site * 3];
            double py = points[site * 3 + 1];
            double pz = points[site * 3 + 2];
            planes[site * 6] = px;
            planes[site * 6 + 1] = py;
            planes[site * 6 + 2] = pz;
            planes[site * 6 + 3] = normal[0];
            planes[site * 6 + 4] = normal[1];
            planes[site * 6 + 5] = normal[2];
            cells[site * 3] = px - normal[0] * normalOffset;
            cells[site * 3 + 1] = py - normal[1] * normalOffset;
            cells[site * 3 + 2] = pz - normal[2] * normalOffset;
        }
        PointIndex cellIndex = new PointIndex(cells, spacing * 2.0);
        DistanceTransform.Result toMaterial = DistanceTransform.compute(volume, occupancy, 1.0, 1.0, 1.0);
        // Las facetas solo actúan cerca de la superficie original: sin este límite, con pocas celdas el plano de una
        // celda lejana rellenaría huecos (el agujero de un toro) o vaciaría el interior.
        float[] signed = SurfaceOps.SignedDistance.of(volume, occupancy).signed;
        double band = Math.max(2.0, Math.min(spacing * 0.6, normalOffset + 1.0));
        double halfGap = Math.max(0.0, airGap) / 2.0;
        int[] chosenSites = chosen;
        return new Resampled(volume, false) {
            private final int[] pair = new int[2];

            @Override
            int sample(double x, double y, double z) {
                int bx = (int) Math.floor(x);
                int by = (int) Math.floor(y);
                int bz = (int) Math.floor(z);
                if (volume.contains(bx, by, bz) && Math.abs(signed[volume.index(bx, by, bz)]) > band) {
                    return volume.sourceAt(x, y, z);
                }
                cellIndex.nearestTwo(x, y, z, pair);
                int site = pair[0];
                if (halfGap > 0.0 && pair[1] >= 0 && cellIndex.bisector(x, y, z, site, pair[1]) < halfGap) {
                    return 0;
                }
                double side = (x - planes[site * 6]) * planes[site * 6 + 3]
                        + (y - planes[site * 6 + 1]) * planes[site * 6 + 4]
                        + (z - planes[site * 6 + 2]) * planes[site * 6 + 5];
                if (side > 0.0) {
                    return 0;
                }
                int own = volume.sourceAt(x, y, z);
                if (own != 0) {
                    return own;
                }
                if (volume.contains(bx, by, bz)) {
                    int nearest = toMaterial.nearest()[volume.index(bx, by, bz)];
                    if (nearest >= 0) {
                        return volume.material[nearest];
                    }
                }
                return volume.material[chosenSites[site]];
            }
        };
    }

    /**
     * Cuboides más grandes: la selección se reparte en celdas de {@code scales} bloques (por eje de la rejilla) y
     * cada celda queda llena si más de la mitad estaba ocupada, o vacía si no. Lo que se rellena toma el material
     * mayoritario de la celda.
     *
     * @param primary   eje de mundo que hace de «y» de la rejilla ({@link Axis})
     * @param secondary eje de mundo que hace de «x» de la rejilla
     */
    public static DeformField voxelize(
            Volume volume,
            double[] scales,
            double airGap,
            double distortion,
            Axis primary,
            Axis secondary,
            long seed,
            boolean horizontalOnly,
            boolean verticalOnly,
            Runnable checkCancelled
    ) {
        if (primary.index() == secondary.index()) {
            throw new IllegalArgumentException("Los ejes -i y -j no pueden ser el mismo");
        }
        int tertiary = 3 - primary.index() - secondary.index();
        double[] worldScale = new double[3];
        worldScale[secondary.index()] = verticalOnly && !horizontalOnly ? 1.0 : scales[0];
        worldScale[primary.index()] = horizontalOnly && !verticalOnly ? 1.0 : scales[1];
        worldScale[tertiary] = verticalOnly && !horizontalOnly ? 1.0 : scales[2];
        for (double scale : worldScale) {
            requirePositive(scale, "La escala");
        }
        int[] sign = new int[3];
        sign[secondary.index()] = secondary.sign();
        sign[primary.index()] = primary.sign();
        sign[tertiary] = 1;
        int[] selection = volume.selectionBounds();
        if (selection == null) {
            throw new IllegalArgumentException("La selección está vacía");
        }
        double jitter = Math.clamp(distortion, 0.0, 1.0) * 0.8;
        Grid grid = new Grid(worldScale, sign, selection, jitter, seed);
        Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
        int[] key = new int[3];
        for (int index = 0; index < volume.size(); index++) {
            checkCancelled.run();
            if (!volume.selected[index]) {
                continue;
            }
            grid.cell(volume.x(index) + 0.5, volume.y(index) + 0.5, volume.z(index) + 0.5, key);
            Cell cell = cells.computeIfAbsent(Grid.pack(key), ignored -> new Cell());
            cell.total++;
            int material = volume.material[index];
            if (material != 0) {
                cell.occupied++;
                cell.materials.addTo(material, 1);
            }
        }
        double halfGap = Math.max(0.0, airGap) / 2.0;
        return new Resampled(volume, false) {
            private final int[] cellKey = new int[3];

            @Override
            int sample(double x, double y, double z) {
                double edge = grid.cell(x, y, z, cellKey);
                Cell cell = cells.get(Grid.pack(cellKey));
                if (cell == null || cell.occupied * 2 <= cell.total || edge < halfGap) {
                    return 0;
                }
                // Cada bloque conserva su material y los huecos que se llenan toman el mayoritario de la celda.
                int own = volume.sourceAt(x, y, z);
                return own != 0 ? own : cell.majority();
            }
        };
    }

    /** Eje de mundo con signo, para {@code -i} y {@code -j}. */
    public enum Axis {
        X(0, 1), NEG_X(0, -1), Y(1, 1), NEG_Y(1, -1), Z(2, 1), NEG_Z(2, -1);

        private final int index;
        private final int sign;

        Axis(int index, int sign) {
            this.index = index;
            this.sign = sign;
        }

        public int index() {
            return index;
        }

        public int sign() {
            return sign;
        }

        public double[] vector() {
            double[] vector = new double[3];
            vector[index] = sign;
            return vector;
        }

        public static Axis parse(String input) {
            String text = input.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (text) {
                case "x", "+x", "e", "east", "este" -> X;
                case "-x", "w", "west", "oeste" -> NEG_X;
                case "y", "+y", "u", "up", "arriba" -> Y;
                case "-y", "d", "down", "abajo" -> NEG_Y;
                case "z", "+z", "s", "south", "sur" -> Z;
                case "-z", "n", "north", "norte" -> NEG_Z;
                default -> throw new IllegalArgumentException("Eje desconocido: " + input + " (x, -x, y, -y, z, -z)");
            };
        }
    }

    /** Recuento de una celda de voxelize. */
    private static final class Cell {
        int total;
        int occupied;
        final Int2IntOpenHashMap materials = new Int2IntOpenHashMap(2);

        int majority() {
            int best = 0;
            int bestCount = -1;
            for (Int2IntOpenHashMap.Entry entry : materials.int2IntEntrySet()) {
                if (entry.getIntValue() > bestCount
                        || entry.getIntValue() == bestCount && entry.getIntKey() < best) {
                    best = entry.getIntKey();
                    bestCount = entry.getIntValue();
                }
            }
            return best;
        }
    }

    /** Rejilla alineada con los ejes, con los planos de corte desplazados al azar según la distorsión. */
    private record Grid(double[] scale, int[] sign, int[] selection, double jitter, long seed) {
        /** @return distancia al plano de corte más cercano */
        double cell(double x, double y, double z, int[] result) {
            double edge = Double.POSITIVE_INFINITY;
            double[] point = {x, y, z};
            for (int axis = 0; axis < 3; axis++) {
                double along = sign[axis] > 0 ? point[axis] - selection[axis] : selection[axis + 3] + 1 - point[axis];
                double size = scale[axis];
                int cell = (int) Math.floor(along / size);
                while (along < boundary(axis, cell)) {
                    cell--;
                }
                while (along >= boundary(axis, cell + 1)) {
                    cell++;
                }
                result[axis] = cell;
                if (size > 1.0) {
                    edge = Math.min(edge, Math.min(along - boundary(axis, cell), boundary(axis, cell + 1) - along));
                }
            }
            return edge;
        }

        private double boundary(int axis, int cell) {
            double offset = jitter == 0.0 ? 0.0 : (unit(cell, axis, 0, seed) - 0.5) * jitter * scale[axis];
            return cell * scale[axis] + offset;
        }

        static long pack(int[] key) {
            return ((long) (key[0] & 0x1FFFFF) << 42) | ((long) (key[1] & 0x1FFFFF) << 21) | (key[2] & 0x1FFFFF);
        }
    }

    /** Punto → material de la selección original, con la regla de qué se escribe fuera de ella. */
    abstract static class Resampled implements DeformField {
        private final Volume volume;
        private final boolean spillsOut;

        Resampled(Volume volume, boolean spillsOut) {
            this.volume = volume;
            this.spillsOut = spillsOut;
        }

        abstract int sample(double x, double y, double z);

        @Override
        public int material(int x, int y, int z) {
            if (!volume.contains(x, y, z)) {
                return KEEP;
            }
            int material = sample(x + 0.5, y + 0.5, z + 0.5);
            if (volume.selected(x, y, z)) {
                return material;
            }
            return spillsOut && material != AIR ? material : KEEP;
        }

        @Override
        public boolean solid(double x, double y, double z) {
            return sample(x, y, z) != AIR;
        }
    }

    static double[] center(Volume volume) {
        int[] bounds = volume.selectionBounds();
        if (bounds == null) {
            throw new IllegalArgumentException("La selección está vacía");
        }
        return new double[]{
                (bounds[0] + bounds[3] + 1) / 2.0,
                (bounds[1] + bounds[4] + 1) / 2.0,
                (bounds[2] + bounds[5] + 1) / 2.0
        };
    }

    /** Base girada: {@code [u, eje, w]} tras girar {@code xRotation} sobre X y luego {@code zRotation} sobre Z. */
    static double[][] rotation(double xRotation, double zRotation) {
        double[][] frame = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        double[] xAxis = {1, 0, 0};
        double[] zAxis = {0, 0, 1};
        double cx = Math.cos(Math.toRadians(xRotation));
        double sx = Math.sin(Math.toRadians(xRotation));
        double cz = Math.cos(Math.toRadians(zRotation));
        double sz = Math.sin(Math.toRadians(zRotation));
        for (int row = 0; row < 3; row++) {
            double[] v = frame[row];
            double[] afterX = rodrigues(v[0], v[1], v[2], xAxis, cx, sx);
            frame[row] = rodrigues(afterX[0], afterX[1], afterX[2], zAxis, cz, sz);
        }
        return frame;
    }

    /** Dentro del bloque {@code minimum…maximum} (ambos incluidos). */
    static double clamp(double value, int minimum, int maximum) {
        return Math.clamp(value, minimum, maximum + 0.999);
    }

    static double[] rodrigues(double x, double y, double z, double[] k, double cos, double sin) {
        double dot = k[0] * x + k[1] * y + k[2] * z;
        double crossX = k[1] * z - k[2] * y;
        double crossY = k[2] * x - k[0] * z;
        double crossZ = k[0] * y - k[1] * x;
        return new double[]{
                x * cos + crossX * sin + k[0] * dot * (1.0 - cos),
                y * cos + crossY * sin + k[1] * dot * (1.0 - cos),
                z * cos + crossZ * sin + k[2] * dot * (1.0 - cos)
        };
    }

    static double[] normalize(double[] vector) {
        double length = Math.sqrt(squared(vector[0], vector[1], vector[2]));
        if (!(length > 1.0E-9)) {
            throw new IllegalArgumentException("El eje no puede ser nulo");
        }
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }

    static double squared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    /** Número determinista en [0, 1) a partir de tres enteros y una semilla. */
    static double unit(int x, int y, int z, long seed) {
        long hash = seed ^ 0x9E3779B97F4A7C15L;
        hash = mix(hash + x * 0xBF58476D1CE4E5B9L);
        hash = mix(hash + y * 0x94D049BB133111EBL);
        hash = mix(hash + z * 0xD6E8FEB86659FD93L);
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static double[] site(int cellX, int cellY, int cellZ, double size, long seed) {
        return new double[]{
                (cellX + 0.1 + 0.8 * unit(cellX, cellY, cellZ, seed)) * size,
                (cellY + 0.1 + 0.8 * unit(cellX, cellY, cellZ, seed + 1)) * size,
                (cellZ + 0.1 + 0.8 * unit(cellX, cellY, cellZ, seed + 2)) * size
        };
    }

    private static boolean exposed(Volume volume, boolean[] occupancy, int index) {
        int x = volume.x(index);
        int y = volume.y(index);
        int z = volume.z(index);
        return !occupied(volume, occupancy, x + 1, y, z) || !occupied(volume, occupancy, x - 1, y, z)
                || !occupied(volume, occupancy, x, y + 1, z) || !occupied(volume, occupancy, x, y - 1, z)
                || !occupied(volume, occupancy, x, y, z + 1) || !occupied(volume, occupancy, x, y, z - 1);
    }

    private static boolean occupied(Volume volume, boolean[] occupancy, int x, int y, int z) {
        return volume.contains(x, y, z) && occupancy[volume.index(x, y, z)];
    }

    private static void setCenter(Volume volume, int index, double[] points, int point) {
        points[point * 3] = volume.x(index) + 0.5;
        points[point * 3 + 1] = volume.y(index) + 0.5;
        points[point * 3 + 2] = volume.z(index) + 0.5;
    }

    private static int snap(Volume volume, DistanceTransform.Result toSurface, double x, double y, double z,
                            int fallback) {
        int bx = Math.clamp((long) Math.floor(x), volume.minimumX, volume.minimumX + volume.sizeX - 1);
        int by = Math.clamp((long) Math.floor(y), volume.minimumY, volume.minimumY + volume.sizeY - 1);
        int bz = Math.clamp((long) Math.floor(z), volume.minimumZ, volume.minimumZ + volume.sizeZ - 1);
        int nearest = toSurface.nearest()[volume.index(bx, by, bz)];
        return nearest >= 0 ? nearest : fallback;
    }

    /** Normal hacia fuera: el contrario del gradiente de ocupación en una caja de radio 2. */
    private static double[] normal(Volume volume, boolean[] occupancy, int index) {
        int x = volume.x(index);
        int y = volume.y(index);
        int z = volume.z(index);
        double gx = 0.0;
        double gy = 0.0;
        double gz = 0.0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (occupied(volume, occupancy, x + dx, y + dy, z + dz)) {
                        gx += dx;
                        gy += dy;
                        gz += dz;
                    }
                }
            }
        }
        double length = Math.sqrt(squared(gx, gy, gz));
        if (length < 1.0E-6) {
            return new double[]{0.0, 1.0, 0.0};
        }
        return new double[]{-gx / length, -gy / length, -gz / length};
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " debe ser mayor que 0");
        }
    }
}
