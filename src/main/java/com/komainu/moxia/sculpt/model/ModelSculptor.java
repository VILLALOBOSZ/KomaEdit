package com.komainu.moxia.sculpt.model;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Los pinceles de modelado sobre una {@link ModelGrid}. Cada pasada decide primero qué celdas quedan con masa y
 * después qué material lleva cada una nueva; nunca toca lo que la máscara deja fuera ({@link ModelGrid#editable})
 * ni lo que queda más allá del radio. Es determinista: la misma rejilla y los mismos ajustes dan lo mismo.
 *
 * <ul>
 *     <li><b>Esculpir</b>: calcula la normal media de la superficie bajo el pincel con los vecinos y desplaza la
 *     forma a lo largo de ella {@code fuerza · caída} bloques (hacia fuera o, invertido, hacia dentro), leyendo la
 *     ocupación suavizada en el punto desplazado; después puede suavizar.</li>
 *     <li><b>Roca</b>: una bola deformada por ruido fractal, unida a lo que hay con un mínimo suave (o restada si se
 *     invierte), así que se funde con el terreno en vez de quedar pegada.</li>
 *     <li><b>Soldar y fundir</b>: desenfoque gaussiano de la ocupación mezclado con la caída y cortado por un umbral;
 *     soldar solo añade (rincones, huecos entre piezas) y fundir solo quita (aristas, esquinas).</li>
 *     <li><b>Rugosidad</b>: mueve la superficie hacia dentro y hacia fuera con un ruido de media cero sobre la
 *     distancia con signo: más caras expuestas con casi el mismo volumen.</li>
 *     <li><b>Grietas</b>: las aristas de un Voronoi (en 3D o en columnas a lo largo de un eje) sobre los bloques que ya
 *     hay, con un ancho y hasta una profundidad, rellenas de aire o de un material.</li>
 * </ul>
 */
public final class ModelSculptor {
    /** Densidad a partir de la que una celda cuenta como llena al leer la ocupación suavizada. */
    private static final double SOLID_CUT = 0.5;

    private ModelSculptor() {
    }

    /** Resultado de una pasada: los materiales de antes y de después de cada celda de la rejilla. */
    public record Result(ModelGrid before, int[] after, int added, int removed, int replaced) {
        public int changes() {
            return added + removed + replaced;
        }

        public ModelGrid grid() {
            return before.withMaterials(after);
        }

        public boolean changed(int index) {
            return before.material[index] != after[index];
        }
    }

    /**
     * @param grid  la foto de la zona
     * @param added material de lo que aparece (el de alrededor o una paleta)
     * @param fill  material de las grietas (aire o una paleta); los demás modos no lo usan
     */
    public static Result apply(ModelGrid grid, ModelSettings settings, MaterialChooser added, MaterialChooser fill,
                               Runnable checkCancelled) {
        boolean[] before = new boolean[grid.size()];
        for (int index = 0; index < before.length; index++) {
            before[index] = grid.material[index] != ModelGrid.FREE;
        }
        boolean[] cracks = null;
        boolean[] after = switch (settings.mode()) {
            case DRAW -> draw(grid, settings, before, checkCancelled);
            case ROCK -> rock(grid, settings, before, checkCancelled);
            case WELD -> blurThreshold(grid, settings, before, settings.threshold(), true, false);
            case MELT -> blurThreshold(grid, settings, before, settings.threshold(), false, true);
            case ROUGHEN -> roughen(grid, settings, before, checkCancelled);
            case SHATTER -> {
                cracks = shatter(grid, settings, before, checkCancelled);
                yield before;
            }
        };
        boolean skin = settings.mode() != ModelMode.SHATTER;
        return materials(grid, before, after, cracks, added, fill, skin);
    }

    /**
     * Desenfoque y umbral sin restricción de sentido (puede añadir y quitar): con un umbral bajo engorda, con uno
     * alto adelgaza. Es la base de soldar y fundir.
     */
    public static Result blur(ModelGrid grid, ModelSettings settings, double threshold, MaterialChooser added) {
        boolean[] before = new boolean[grid.size()];
        for (int index = 0; index < before.length; index++) {
            before[index] = grid.material[index] != ModelGrid.FREE;
        }
        boolean[] after = blurThreshold(grid, settings, before, threshold, true, true);
        return materials(grid, before, after, null, added, MaterialChooser.AIR, true);
    }

    // ------------------------------------------------------------------------------------------------ Esculpir

    private static boolean[] draw(ModelGrid grid, ModelSettings settings, boolean[] before, Runnable cancel) {
        double[] normal = brushNormal(grid, settings);
        float[] density = ModelFields.gaussian(grid, grid.density(), 0.75);
        boolean[] after = before.clone();
        // La caída se mide de lado (perpendicular a la normal): así el centro sube exactamente la fuerza.
        double reach = settings.radius() + settings.strength() + 1.0;
        Box box = Box.around(grid, settings, (int) Math.ceil(reach));
        double direction = settings.invert() ? 1.0 : -1.0;
        for (int y = box.minY; y <= box.maxY; y++) {
            cancel.run();
            for (int z = box.minZ; z <= box.maxZ; z++) {
                for (int x = box.minX; x <= box.maxX; x++) {
                    double weight = lateralWeight(settings, normal, reach, x, y, z);
                    int index = grid.index(x, y, z);
                    if (weight <= 0.0 || !grid.editable[index]) {
                        continue;
                    }
                    double height = settings.strength() * weight;
                    double sample = ModelFields.sample(grid, density, x + 0.5 + direction * height * normal[0],
                            y + 0.5 + direction * height * normal[1], z + 0.5 + direction * height * normal[2]);
                    if (settings.invert()) {
                        after[index] = before[index] && sample > SOLID_CUT;
                    } else {
                        after[index] = before[index] || sample > SOLID_CUT;
                    }
                }
            }
        }
        for (int pass = 0; pass < settings.smoothing(); pass++) {
            cancel.run();
            after = smoothPass(grid, settings, after);
        }
        return after;
    }

    /** Una pasada de suavizado dentro del pincel, mezclada con la caída para no tocar el borde. */
    private static boolean[] smoothPass(ModelGrid grid, ModelSettings settings, boolean[] occupancy) {
        float[] density = new float[occupancy.length];
        for (int index = 0; index < density.length; index++) {
            density[index] = occupancy[index] ? 1.0F : 0.0F;
        }
        float[] blurred = ModelFields.gaussian(grid, density, 1.0);
        boolean[] result = occupancy.clone();
        Box box = Box.around(grid, settings, settings.radius());
        for (int y = box.minY; y <= box.maxY; y++) {
            for (int z = box.minZ; z <= box.maxZ; z++) {
                for (int x = box.minX; x <= box.maxX; x++) {
                    double weight = weight(settings, x, y, z);
                    int index = grid.index(x, y, z);
                    if (weight <= 0.0 || !grid.editable[index]) {
                        continue;
                    }
                    double mixed = ModelFields.lerp(density[index], blurred[index], weight);
                    result[index] = mixed > SOLID_CUT;
                }
            }
        }
        return result;
    }

    /** Normal media bajo el pincel; si no hay superficie, la cara pulsada. */
    static double[] brushNormal(ModelGrid grid, ModelSettings settings) {
        int radius = Math.max(2, settings.radius());
        double[] normal = SurfaceNormals.average(grid, settings.centerX(), settings.centerY(), settings.centerZ(),
                radius, (x, y, z) -> {
                    double dx = x - settings.centerX();
                    double dy = y - settings.centerY();
                    double dz = z - settings.centerZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz) / radius;
                    return distance > 1.0 ? 0.0 : Math.max(0.05, settings.falloff().apply(distance));
                });
        if (normal[0] == 0.0 && normal[1] == 0.0 && normal[2] == 0.0) {
            double[] hint = settings.hint();
            normal = SurfaceNormals.normalize(hint[0], hint[1], hint[2]);
            if (normal[0] == 0.0 && normal[1] == 0.0 && normal[2] == 0.0) {
                normal = new double[]{0.0, 1.0, 0.0};
            }
        }
        return normal;
    }

    // ------------------------------------------------------------------------------------------------ Roca

    private static boolean[] rock(ModelGrid grid, ModelSettings settings, boolean[] before, Runnable cancel) {
        double[] normal = brushNormal(grid, settings);
        float[] distance = ModelFields.signedDistance(grid);
        double radius = settings.radius();
        // El centro asoma un poco de la superficie para que la roca salga del suelo y no quede enterrada.
        double lift = settings.invert() ? 0.0 : radius * 0.3;
        double centerX = settings.centerX() + 0.5 + normal[0] * lift;
        double centerY = settings.centerY() + 0.5 + normal[1] * lift;
        double centerZ = settings.centerZ() + 0.5 + normal[2] * lift;
        double[][] faces = rockFaces(settings.seed(), settings.roughness());
        double reach = radius * (1.25 + settings.roughness()) + settings.blend() + 1.0;
        boolean[] after = before.clone();
        Box box = Box.around(grid, (int) Math.floor(centerX), (int) Math.floor(centerY), (int) Math.floor(centerZ),
                (int) Math.ceil(reach));
        for (int y = box.minY; y <= box.maxY; y++) {
            cancel.run();
            for (int z = box.minZ; z <= box.maxZ; z++) {
                for (int x = box.minX; x <= box.maxX; x++) {
                    int index = grid.index(x, y, z);
                    if (!grid.editable[index] || (settings.invert() ? !before[index] : before[index])) {
                        continue;
                    }
                    double px = x + 0.5;
                    double py = y + 0.5;
                    double pz = z + 0.5;
                    double dx = px - centerX;
                    double dy = py - centerY;
                    double dz = pz - centerZ;
                    if (dx * dx + dy * dy + dz * dz > reach * reach) {
                        continue;
                    }
                    double rock = rockDistance(dx, dy, dz, radius, faces, settings, px, py, pz);
                    double existing = distance[index];
                    if (settings.invert()) {
                        after[index] = ModelFields.smoothSubtract(existing, rock, settings.blend()) < 0.0;
                    } else {
                        after[index] = ModelFields.smoothMin(existing, rock, settings.blend()) < 0.0;
                    }
                }
            }
        }
        return after;
    }

    /**
     * Distancia con signo a la roca: un poliedro de caras al azar (el corte de varios planos tangentes a una esfera)
     * redondeado contra la propia esfera y deformado con ruido fractal. Las caras dan las aristas de roca; el ruido,
     * que no haya dos iguales.
     */
    private static double rockDistance(double dx, double dy, double dz, double radius, double[][] faces,
                                       ModelSettings settings, double px, double py, double pz) {
        double polyhedron = Double.NEGATIVE_INFINITY;
        for (double[] face : faces) {
            polyhedron = Math.max(polyhedron, face[0] * dx + face[1] * dy + face[2] * dz - radius * face[3]);
        }
        double sphere = Math.sqrt(dx * dx + dy * dy + dz * dz) - radius * 1.12;
        double noise = ModelFields.fractal(px, py, pz, settings.scale(), 3, settings.seed());
        return Math.max(polyhedron, sphere) - radius * settings.roughness() * 0.45 * noise;
    }

    /** Normales al azar de las caras de la roca y la distancia de cada una al centro (en radios). */
    static double[][] rockFaces(long seed, double roughness) {
        int count = 14;
        double[][] faces = new double[count][];
        double spin = ModelFields.unit(0, 0, 0, seed) * Math.PI * 2.0;
        for (int face = 0; face < count; face++) {
            // Direcciones repartidas por la esfera (espiral de Fibonacci), movidas y giradas con la semilla.
            double u = ModelFields.unit(face, 1, 0, seed) * 0.6 - 0.3;
            double offset = Math.clamp((face + 0.5 + u) / count, 0.0, 1.0);
            double polar = Math.acos(1.0 - 2.0 * offset);
            double azimuth = face * Math.PI * (3.0 - Math.sqrt(5.0)) + spin;
            double nx = Math.sin(polar) * Math.cos(azimuth);
            double ny = Math.cos(polar);
            double nz = Math.sin(polar) * Math.sin(azimuth);
            double depth = 0.82 + 0.3 * roughness * ModelFields.unit(face, 2, 0, seed);
            faces[face] = new double[]{nx, ny, nz, depth};
        }
        return faces;
    }

    // ------------------------------------------------------------------------------------------------ Soldar

    private static boolean[] blurThreshold(ModelGrid grid, ModelSettings settings, boolean[] before,
                                           double threshold, boolean add, boolean remove) {
        float[] blurred = ModelFields.gaussian(grid, grid.density(), settings.sigma());
        boolean[] after = before.clone();
        Box box = Box.around(grid, settings, settings.radius());
        for (int y = box.minY; y <= box.maxY; y++) {
            for (int z = box.minZ; z <= box.maxZ; z++) {
                for (int x = box.minX; x <= box.maxX; x++) {
                    double weight = weight(settings, x, y, z);
                    int index = grid.index(x, y, z);
                    if (weight <= 0.0 || !grid.editable[index]) {
                        continue;
                    }
                    double mixed = ModelFields.lerp(before[index] ? 1.0 : 0.0, blurred[index], weight);
                    boolean solid = mixed > threshold;
                    if (solid && !before[index] && add) {
                        after[index] = true;
                    } else if (!solid && before[index] && remove) {
                        after[index] = false;
                    }
                }
            }
        }
        return after;
    }

    // ------------------------------------------------------------------------------------------------ Rugosidad

    private static boolean[] roughen(ModelGrid grid, ModelSettings settings, boolean[] before, Runnable cancel) {
        float[] distance = ModelFields.signedDistance(grid);
        boolean[] after = before.clone();
        Box box = Box.around(grid, settings, settings.radius());
        for (int y = box.minY; y <= box.maxY; y++) {
            cancel.run();
            for (int z = box.minZ; z <= box.maxZ; z++) {
                for (int x = box.minX; x <= box.maxX; x++) {
                    double weight = weight(settings, x, y, z);
                    int index = grid.index(x, y, z);
                    // Meseta: la caída solo recorta el borde; dentro, la amplitud es la pedida.
                    double amplitude = settings.strength() * Math.min(1.0, weight * 2.0);
                    if (weight <= 0.0 || !grid.editable[index] || Math.abs(distance[index]) > amplitude + 0.5) {
                        continue;
                    }
                    // Ruido de valor (bultos de «escala» bloques) más un salto por bloque, los dos de media cero;
                    // el de valor se concentra cerca de 0 y se estira para que llegue a ±1 a menudo.
                    double smooth = ModelFields.fractal(x + 0.5, y + 0.5, z + 0.5, settings.scale(), 2,
                            settings.seed());
                    double jitter = ModelFields.unit(x, y, z, settings.seed() ^ 0x7F4A7C15L) * 2.0 - 1.0;
                    double noise = Math.clamp(1.6 * smooth + 0.55 * jitter, -1.0, 1.0);
                    after[index] = distance[index] < amplitude * noise;
                }
            }
        }
        return after;
    }

    // ------------------------------------------------------------------------------------------------ Grietas

    /** Celdas de grieta: con masa antes, dentro del pincel y de la profundidad, sobre una arista del Voronoi. */
    private static boolean[] shatter(ModelGrid grid, ModelSettings settings, boolean[] before, Runnable cancel) {
        float[] distance = settings.depth() > 0 ? ModelFields.signedDistance(grid) : null;
        boolean[] cracks = new boolean[before.length];
        Box box = Box.around(grid, settings, settings.radius());
        for (int y = box.minY; y <= box.maxY; y++) {
            cancel.run();
            for (int z = box.minZ; z <= box.maxZ; z++) {
                for (int x = box.minX; x <= box.maxX; x++) {
                    int index = grid.index(x, y, z);
                    double weight = weight(settings, x, y, z);
                    if (!before[index] || weight <= 0.0 || !grid.editable[index]) {
                        continue;
                    }
                    if (distance != null && 0.5 - distance[index] > settings.depth()) {
                        continue;
                    }
                    double halfWidth = settings.width() * 0.5 * weight;
                    if (Voronoi.edgeDistance(x + 0.5, y + 0.5, z + 0.5, settings.scale(), settings.axis(),
                            settings.seed()) < halfWidth) {
                        cracks[index] = true;
                    }
                }
            }
        }
        return cracks;
    }

    // ------------------------------------------------------------------------------------------------ Materiales

    private static Result materials(ModelGrid grid, boolean[] before, boolean[] after, boolean[] cracks,
                                    MaterialChooser added, MaterialChooser fill, boolean skin) {
        int[] result = grid.material.clone();
        int[] nearest = null;
        int addedCount = 0;
        int removedCount = 0;
        int replacedCount = 0;
        for (int index = 0; index < result.length; index++) {
            int x = grid.x(index);
            int y = grid.y(index);
            int z = grid.z(index);
            if (cracks != null && cracks[index]) {
                boolean covered = grid.contains(x, y + 1, z) && before[grid.index(x, y + 1, z)];
                int material = fill.choose(x, y, z, grid.material[index], covered);
                if (material == ModelGrid.FREE) {
                    result[index] = ModelGrid.FREE;
                    removedCount++;
                } else if (material != grid.material[index]) {
                    result[index] = material;
                    replacedCount++;
                }
                continue;
            }
            if (before[index] == after[index]) {
                continue;
            }
            if (!after[index]) {
                result[index] = ModelGrid.FREE;
                removedCount++;
                continue;
            }
            int inherited = neighborMaterial(grid, x, y, z);
            if (inherited == ModelGrid.FREE) {
                if (nearest == null) {
                    nearest = nearestMaterial(grid);
                }
                inherited = nearest[index];
            }
            boolean covered = grid.contains(x, y + 1, z) && after[grid.index(x, y + 1, z)];
            int material = added.choose(x, y, z, inherited, covered);
            if (material != ModelGrid.FREE) {
                result[index] = material;
                addedCount++;
            }
        }
        if (skin) {
            replacedCount += lowerSkin(grid, before, after, result);
        }
        return new Result(grid, result, addedCount, removedCount, replacedCount);
    }

    /**
     * Si se quitó la capa de arriba de una columna (la hierba de un terreno que se hunde), el primer bloque que queda
     * debajo toma su material: el terreno baja con su piel en vez de dejar tierra o piedra al aire. Solo en
     * vertical y solo en bloques que la máscara deja tocar.
     */
    private static int lowerSkin(ModelGrid grid, boolean[] before, boolean[] after, int[] result) {
        int replaced = 0;
        for (int index = 0; index < before.length; index++) {
            if (!before[index] || after[index]) {
                continue;
            }
            int x = grid.x(index);
            int y = grid.y(index);
            int z = grid.z(index);
            if (grid.contains(x, y + 1, z) && before[grid.index(x, y + 1, z)]) {
                continue;
            }
            int below = y - 1;
            while (below >= grid.minimumY && !after[grid.index(x, below, z)]) {
                below--;
            }
            if (below < grid.minimumY) {
                continue;
            }
            int target = grid.index(x, below, z);
            int top = grid.material[index];
            if (grid.editable[target] && before[target] && result[target] != top
                    && result[target] == grid.material[target]) {
                result[target] = top;
                replaced++;
            }
        }
        return replaced;
    }

    /**
     * El material más repetido entre los 26 vecinos con masa de antes (los de cara pesan el doble, y a igualdad
     * gana el número más bajo), o {@link ModelGrid#FREE} si no hay ninguno.
     */
    static int neighborMaterial(ModelGrid grid, int x, int y, int z) {
        int[] materials = new int[26];
        int[] votes = new int[26];
        int distinct = 0;
        for (int[] offset : ModelGrid.Neighbors.AROUND) {
            int material = grid.material(x + offset[0], y + offset[1], z + offset[2]);
            if (material == ModelGrid.FREE) {
                continue;
            }
            int vote = Math.abs(offset[0]) + Math.abs(offset[1]) + Math.abs(offset[2]) == 1 ? 2 : 1;
            int slot = -1;
            for (int existing = 0; existing < distinct; existing++) {
                if (materials[existing] == material) {
                    slot = existing;
                    break;
                }
            }
            if (slot < 0) {
                slot = distinct++;
                materials[slot] = material;
            }
            votes[slot] += vote;
        }
        int best = ModelGrid.FREE;
        int bestVotes = 0;
        for (int slot = 0; slot < distinct; slot++) {
            if (votes[slot] > bestVotes || votes[slot] == bestVotes && materials[slot] < best) {
                best = materials[slot];
                bestVotes = votes[slot];
            }
        }
        return best;
    }

    /** Material de la celda con masa más cercana (recorrido en anchura por caras), o libre si no hay masa. */
    static int[] nearestMaterial(ModelGrid grid) {
        int[] nearest = new int[grid.size()];
        Arrays.fill(nearest, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < nearest.length; index++) {
            if (grid.material[index] != ModelGrid.FREE) {
                nearest[index] = grid.material[index];
                queue.add(index);
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int x = grid.x(index);
            int y = grid.y(index);
            int z = grid.z(index);
            for (int[] offset : ModelGrid.Neighbors.FACES) {
                int nx = x + offset[0];
                int ny = y + offset[1];
                int nz = z + offset[2];
                if (!grid.contains(nx, ny, nz)) {
                    continue;
                }
                int next = grid.index(nx, ny, nz);
                if (nearest[next] < 0) {
                    nearest[next] = nearest[index];
                    queue.add(next);
                }
            }
        }
        for (int index = 0; index < nearest.length; index++) {
            if (nearest[index] < 0) {
                nearest[index] = ModelGrid.FREE;
            }
        }
        return nearest;
    }

    // ------------------------------------------------------------------------------------------------ Común

    /** Caída del pincel en una celda: 0 fuera de la bola. */
    static double weight(ModelSettings settings, int x, int y, int z) {
        double dx = x - settings.centerX();
        double dy = y - settings.centerY();
        double dz = z - settings.centerZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz) / (settings.radius() + 0.5);
        if (distance > 1.0) {
            return 0.0;
        }
        return settings.falloff().apply(distance);
    }

    /** Caída según la distancia a la recta del centro a lo largo de la normal, dentro de una bola de alcance. */
    private static double lateralWeight(ModelSettings settings, double[] normal, double reach, int x, int y, int z) {
        double dx = x - settings.centerX();
        double dy = y - settings.centerY();
        double dz = z - settings.centerZ();
        double squared = dx * dx + dy * dy + dz * dz;
        if (squared > reach * reach) {
            return 0.0;
        }
        double along = dx * normal[0] + dy * normal[1] + dz * normal[2];
        double lateral = Math.sqrt(Math.max(0.0, squared - along * along)) / (settings.radius() + 0.5);
        return lateral > 1.0 ? 0.0 : settings.falloff().apply(lateral);
    }

    /** Caja de celdas a recorrer: la bola del pincel recortada a la rejilla. */
    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Box around(ModelGrid grid, ModelSettings settings, int radius) {
            return around(grid, settings.centerX(), settings.centerY(), settings.centerZ(), radius);
        }

        static Box around(ModelGrid grid, int x, int y, int z, int radius) {
            return new Box(
                    Math.max(grid.minimumX, x - radius),
                    Math.max(grid.minimumY, y - radius),
                    Math.max(grid.minimumZ, z - radius),
                    Math.min(grid.minimumX + grid.sizeX - 1, x + radius),
                    Math.min(grid.minimumY + grid.sizeY - 1, y + radius),
                    Math.min(grid.minimumZ + grid.sizeZ - 1, z + radius));
        }
    }
}
