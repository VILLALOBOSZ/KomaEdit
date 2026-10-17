package com.komainu.moxia.placement;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.Vec3;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;

/**
 * Dónde caen las estructuras de {@code scatter}:
 *
 * <ol>
 *     <li>Superficies: bloques que no son aire, dentro de la región y con alguna cara al aire.</li>
 *     <li>Normal de cada superficie: la media de las direcciones hacia el aire en un radio de 2 bloques.</li>
 *     <li>Filtros: dirección ({@code -d}/{@code -e}) y máscara ({@code -m}).</li>
 *     <li>Cantidad: la densidad ({@code -n}) en porcentaje de las superficies que quedan.</li>
 *     <li>Uniformidad ({@code -u}): se empieza al azar y en cada vuelta los puntos se repelen y vuelven a la
 *     superficie más cercana.</li>
 * </ol>
 *
 * <p>Con la misma semilla y el mismo mundo sale siempre lo mismo.</p>
 */
public final class ScatterPlanner {
    public static final int MAX_PLACEMENTS = 5_000;
    private static final int NORMAL_RADIUS = 2;
    private static final int[][] FACES = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    private ScatterPlanner() {
    }

    /** Filtro de posiciones (la máscara de WorldEdit, ya ligada a la foto). */
    @FunctionalInterface
    public interface PositionFilter {
        boolean test(int x, int y, int z);
    }

    /**
     * @param directions direcciones de {@code -d}, normalizadas; vacía para no filtrar
     * @param density    porcentaje (0–100) de las superficies que reciben estructura
     */
    public record Settings(List<Vec3> directions, double threshold, double density, int iterations, long seed) {
    }

    /** Una superficie elegida: el bloque, su normal y el bloque de aire donde cae el origen de la estructura. */
    public record Point(int x, int y, int z, Vec3 normal, int anchorX, int anchorY, int anchorZ) {
        public Vec3 anchorCenter() {
            return new Vec3(anchorX + 0.5, anchorY + 0.5, anchorZ + 0.5);
        }
    }

    /**
     * @param points   colocaciones elegidas
     * @param surfaces superficies que pasaron los filtros (posiciones empaquetadas), para {@code -l}
     * @param found    superficies antes de filtrar
     */
    public record Result(List<Point> points, LongArrayList surfaces, int found) {
    }

    public static Result plan(RegionSnapshot snapshot, Settings settings, PositionFilter mask) {
        LongArrayList surfaces = new LongArrayList();
        List<Vec3> normals = new ArrayList<>();
        int found = 0;
        int checks = 0;
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    if ((++checks & 4_095) == 0) {
                        checkCancelled();
                    }
                    if (!snapshot.selected(x, y, z) || snapshot.isAir(x, y, z) || !touchesAir(snapshot, x, y, z)) {
                        continue;
                    }
                    found++;
                    Vec3 normal = normal(snapshot, x, y, z);
                    if (!settings.directions().isEmpty() && !facing(normal, settings)) {
                        continue;
                    }
                    if (mask != null && !mask.test(x, y, z)) {
                        continue;
                    }
                    surfaces.add(PackedBlockPlan.pack(x, y, z));
                    normals.add(normal);
                }
            }
        }
        SplittableRandom random = new SplittableRandom(settings.seed());
        double expected = surfaces.size() * settings.density() / 100.0;
        int count = (int) Math.floor(expected);
        if (random.nextDouble() < expected - count) {
            count++;
        }
        if (count > MAX_PLACEMENTS) {
            throw new IllegalArgumentException("Saldrían " + count + " estructuras (máximo " + MAX_PLACEMENTS
                    + "); baja la densidad con -n o reduce la región");
        }
        if (count == 0) {
            return new Result(List.of(), surfaces, found);
        }
        int[] chosen = choose(surfaces.size(), count, random);
        if (settings.iterations() > 0 && count > 1) {
            relax(surfaces, chosen, settings.iterations());
        }
        List<Point> points = new ArrayList<>(count);
        for (int index : chosen) {
            long packed = surfaces.getLong(index);
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            Vec3 normal = normals.get(index);
            int[] anchor = anchor(snapshot, x, y, z, normal);
            points.add(new Point(x, y, z, normal, anchor[0], anchor[1], anchor[2]));
        }
        return new Result(points, surfaces, found);
    }

    static boolean touchesAir(RegionSnapshot snapshot, int x, int y, int z) {
        for (int[] face : FACES) {
            if (snapshot.isAir(x + face[0], y + face[1], z + face[2])) {
                return true;
            }
        }
        return false;
    }

    /** Media de las direcciones hacia el aire cercano; en una superficie plana es su perpendicular. */
    public static Vec3 normal(RegionSnapshot snapshot, int x, int y, int z) {
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        for (int dy = -NORMAL_RADIUS; dy <= NORMAL_RADIUS; dy++) {
            for (int dz = -NORMAL_RADIUS; dz <= NORMAL_RADIUS; dz++) {
                for (int dx = -NORMAL_RADIUS; dx <= NORMAL_RADIUS; dx++) {
                    int squared = dx * dx + dy * dy + dz * dz;
                    if (squared == 0 || squared > NORMAL_RADIUS * NORMAL_RADIUS + 1) {
                        continue;
                    }
                    if (snapshot.isAir(x + dx, y + dy, z + dz)) {
                        double length = Math.sqrt(squared);
                        sumX += dx / length;
                        sumY += dy / length;
                        sumZ += dz / length;
                    }
                }
            }
        }
        Vec3 normal = new Vec3(sumX, sumY, sumZ);
        return normal.length() < 1.0E-6 ? Alignment.UP : normal.normalized();
    }

    private static boolean facing(Vec3 normal, Settings settings) {
        for (Vec3 direction : settings.directions()) {
            if (normal.dot(direction) >= settings.threshold()) {
                return true;
            }
        }
        return false;
    }

    /** El vecino de aire que más mira hacia la normal: ahí cae el origen (sobre la superficie, no dentro). */
    static int[] anchor(RegionSnapshot snapshot, int x, int y, int z, Vec3 normal) {
        int[] best = null;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (int[] face : FACES) {
            if (!snapshot.isAir(x + face[0], y + face[1], z + face[2])) {
                continue;
            }
            double dot = face[0] * normal.x() + face[1] * normal.y() + face[2] * normal.z();
            if (dot > bestDot + 1.0E-9) {
                bestDot = dot;
                best = face;
            }
        }
        if (best == null) {
            best = FACES[0];
        }
        return new int[]{x + best[0], y + best[1], z + best[2]};
    }

    /** {@code count} índices distintos al azar (Fisher–Yates parcial). */
    private static int[] choose(int total, int count, SplittableRandom random) {
        int[] indices = new int[total];
        for (int index = 0; index < total; index++) {
            indices[index] = index;
        }
        for (int index = 0; index < count; index++) {
            int swap = index + random.nextInt(total - index);
            int value = indices[swap];
            indices[swap] = indices[index];
            indices[index] = value;
        }
        return java.util.Arrays.copyOf(indices, count);
    }

    /**
     * Repulsión: cada punto se aleja de los vecinos más cercanos que la separación ideal y se recoloca en la
     * superficie libre más cercana. Se calculan todos los empujes antes de mover ninguno, así el orden no importa.
     */
    static void relax(LongArrayList surfaces, int[] chosen, int iterations) {
        int count = chosen.length;
        double spacing = Math.sqrt(surfaces.size() / (double) count);
        double reach = 2.0 * spacing;
        int cell = Math.max(2, (int) Math.ceil(spacing));
        Long2ObjectOpenHashMap<IntArrayList> grid = new Long2ObjectOpenHashMap<>();
        for (int index = 0; index < surfaces.size(); index++) {
            long packed = surfaces.getLong(index);
            grid.computeIfAbsent(cellKey(PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                    PackedBlockPlan.unpackZ(packed), cell), key -> new IntArrayList()).add(index);
        }
        double[] px = new double[count];
        double[] py = new double[count];
        double[] pz = new double[count];
        for (int iteration = 0; iteration < iterations; iteration++) {
            checkCancelled();
            for (int index = 0; index < count; index++) {
                long packed = surfaces.getLong(chosen[index]);
                px[index] = PackedBlockPlan.unpackX(packed);
                py[index] = PackedBlockPlan.unpackY(packed);
                pz[index] = PackedBlockPlan.unpackZ(packed);
            }
            int bucket = Math.max(1, (int) Math.ceil(reach));
            Long2ObjectOpenHashMap<IntArrayList> buckets = new Long2ObjectOpenHashMap<>();
            for (int index = 0; index < count; index++) {
                buckets.computeIfAbsent(cellKey((int) px[index], (int) py[index], (int) pz[index], bucket),
                        key -> new IntArrayList()).add(index);
            }
            double[] moveX = new double[count];
            double[] moveY = new double[count];
            double[] moveZ = new double[count];
            for (int index = 0; index < count; index++) {
                int bx = Math.floorDiv((int) px[index], bucket);
                int by = Math.floorDiv((int) py[index], bucket);
                int bz = Math.floorDiv((int) pz[index], bucket);
                double forceX = 0;
                double forceY = 0;
                double forceZ = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            IntArrayList neighbors = buckets.get(pack(bx + dx, by + dy, bz + dz));
                            if (neighbors == null) {
                                continue;
                            }
                            for (int other : neighbors) {
                                if (other == index) {
                                    continue;
                                }
                                double ox = px[index] - px[other];
                                double oy = py[index] - py[other];
                                double oz = pz[index] - pz[other];
                                double distance = Math.sqrt(ox * ox + oy * oy + oz * oz);
                                if (distance >= reach) {
                                    continue;
                                }
                                double push = 1.0 - distance / reach;
                                if (distance < 1.0E-6) {
                                    // Dos puntos en el mismo sitio: se separan en una dirección fija por índice.
                                    double angle = (index * 2.399963) % (Math.PI * 2);
                                    ox = Math.cos(angle);
                                    oz = Math.sin(angle);
                                    oy = 0;
                                    distance = 1.0;
                                }
                                forceX += ox / distance * push;
                                forceY += oy / distance * push;
                                forceZ += oz / distance * push;
                            }
                        }
                    }
                }
                double length = Math.sqrt(forceX * forceX + forceY * forceY + forceZ * forceZ);
                double step = Math.min(length, 1.0) * spacing * 0.5;
                if (length > 1.0E-9) {
                    moveX[index] = forceX / length * step;
                    moveY[index] = forceY / length * step;
                    moveZ[index] = forceZ / length * step;
                }
            }
            LongOpenHashSet taken = new LongOpenHashSet(count * 2);
            for (int index : chosen) {
                taken.add(surfaces.getLong(index));
            }
            for (int index = 0; index < count; index++) {
                if (moveX[index] == 0 && moveY[index] == 0 && moveZ[index] == 0) {
                    continue;
                }
                taken.remove(surfaces.getLong(chosen[index]));
                int nearest = nearest(surfaces, grid, cell, px[index] + moveX[index], py[index] + moveY[index],
                        pz[index] + moveZ[index], taken);
                if (nearest >= 0) {
                    chosen[index] = nearest;
                }
                taken.add(surfaces.getLong(chosen[index]));
            }
        }
    }

    private static int nearest(
            LongArrayList surfaces,
            Long2ObjectOpenHashMap<IntArrayList> grid,
            int cell,
            double x,
            double y,
            double z,
            LongOpenHashSet taken
    ) {
        int cx = Math.floorDiv((int) Math.floor(x), cell);
        int cy = Math.floorDiv((int) Math.floor(y), cell);
        int cz = Math.floorDiv((int) Math.floor(z), cell);
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    IntArrayList candidates = grid.get(pack(cx + dx, cy + dy, cz + dz));
                    if (candidates == null) {
                        continue;
                    }
                    for (int candidate : candidates) {
                        long packed = surfaces.getLong(candidate);
                        if (taken.contains(packed)) {
                            continue;
                        }
                        double ox = PackedBlockPlan.unpackX(packed) - x;
                        double oy = PackedBlockPlan.unpackY(packed) - y;
                        double oz = PackedBlockPlan.unpackZ(packed) - z;
                        double distance = ox * ox + oy * oy + oz * oz;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static long cellKey(int x, int y, int z, int cell) {
        return pack(Math.floorDiv(x, cell), Math.floorDiv(y, cell), Math.floorDiv(z, cell));
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }
}
