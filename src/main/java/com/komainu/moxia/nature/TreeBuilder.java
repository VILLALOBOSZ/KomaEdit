package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.noise.FastNoiseLite;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.tube.TubeLimb;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Convierte el esqueleto en celdas: madera y raíces con el motor de tubo, la copa a partir de los grupos de hojas y
 * las hojas colgantes. Garantiza tres cosas que comprueban las pruebas: la madera está unida cara con cara al suelo,
 * cada hoja llega a la madera por hojas en {@link NatureVoxels#LEAF_REACH} pasos o menos (las colgantes en
 * {@link NatureVoxels#HANGING_REACH}) y nada se sale de la foto ni pisa lo que no debe.
 */
final class TreeBuilder {
    static final int[] DX = {1, -1, 0, 0, 0, 0};
    static final int[] DY = {0, 0, 1, -1, 0, 0};
    static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private TreeBuilder() {
    }

    static NatureVoxels build(TreeSkeleton skeleton, TreeSettings settings, NatureTerrain terrain, Bounds bounds,
                              int baseY, long seed, int limit, BooleanSupplier cancelled) {
        NatureVoxels voxels = new NatureVoxels(limit);
        // Por debajo de esta altura el tronco puede ocupar suelo blando: así se planta también en laderas.
        int planted = baseY + 1;
        for (TreeSkeleton.Limb limb : skeleton.wood) {
            checkCancelled(cancelled);
            TubeLimb.voxelize(limb.points(), limb.radii(), limit, (x, y, z, axis, progress) -> {
                boolean ground = y <= planted;
                if (bounds.contains(x, y, z) && allowsWood(terrain.kind(x, y, z), ground)) {
                    voxels.add(x, y, z, NatureVoxels.Role.WOOD, axis, 0, 0, ground);
                }
            });
        }
        for (TreeSkeleton.Limb root : skeleton.roots) {
            checkCancelled(cancelled);
            TubeLimb.voxelize(root.points(), root.radii(), limit, (x, y, z, axis, progress) -> {
                if (bounds.contains(x, y, z) && allowsWood(terrain.kind(x, y, z), true)) {
                    voxels.add(x, y, z, NatureVoxels.Role.ROOT, axis, 0, 0, true);
                }
            });
        }
        connectWood(voxels, terrain, bounds, planted);
        if (settings.leafMaterial() != null) {
            checkCancelled(cancelled);
            LongOpenHashSet leaves = canopy(skeleton, settings, terrain, bounds, voxels, seed);
            checkCancelled(cancelled);
            hanging(skeleton, settings, terrain, bounds, voxels, leaves, seed);
            fronds(skeleton, terrain, bounds, voxels);
        }
        return voxels;
    }

    static boolean allowsWood(NatureTerrain.Kind kind, boolean ground) {
        return switch (kind) {
            case FREE, LEAVES, LIQUID -> true;
            case SOIL -> ground;
            case STONE, HARD -> false;
        };
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException();
        }
    }

    // ── Madera unida ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * Toda la madera tiene que estar unida cara con cara a algo apoyado en el suelo. Los trozos sueltos (un borde de
     * rama que solo toca por una arista, o lo que corta una piedra) se unen con un puente corto o se quitan.
     */
    static void connectWood(NatureVoxels voxels, NatureTerrain terrain, Bounds bounds, int planted) {
        LongOpenHashSet wood = new LongOpenHashSet();
        long[] keys = voxels.sortedKeys();
        for (long key : keys) {
            if (NatureVoxels.role(voxels.info(key)).wood()) {
                wood.add(key);
            }
        }
        LongOpenHashSet reached = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (long key : keys) {
            if (!wood.contains(key)) {
                continue;
            }
            int info = voxels.info(key);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            if (NatureVoxels.ground(info) && y < planted || touchesGround(terrain, voxels, x, y, z)) {
                reached.add(key);
                queue.enqueue(key);
            }
        }
        flood(queue, reached, wood);
        if (reached.size() == wood.size()) {
            return;
        }
        for (long key : keys) {
            if (!wood.contains(key) || reached.contains(key)) {
                continue;
            }
            LongArrayList component = component(key, wood, reached);
            if (!bridge(component, voxels, terrain, bounds, planted, wood, reached)) {
                for (int index = 0; index < component.size(); index++) {
                    long cell = component.getLong(index);
                    voxels.remove(cell);
                    wood.remove(cell);
                }
            }
        }
    }

    private static boolean touchesGround(NatureTerrain terrain, NatureVoxels voxels, int x, int y, int z) {
        for (int face = 0; face < 6; face++) {
            if (face == 2) {
                continue;
            }
            int nx = x + DX[face];
            int ny = y + DY[face];
            int nz = z + DZ[face];
            if (!voxels.contains(nx, ny, nz) && terrain.kind(nx, ny, nz).ground()) {
                return true;
            }
        }
        return false;
    }

    private static void flood(LongArrayFIFOQueue queue, LongOpenHashSet reached, LongOpenHashSet allowed) {
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int face = 0; face < 6; face++) {
                long next = PackedBlockPlan.pack(x + DX[face], y + DY[face], z + DZ[face]);
                if (allowed.contains(next) && reached.add(next)) {
                    queue.enqueue(next);
                }
            }
        }
    }

    private static LongArrayList component(long start, LongOpenHashSet wood, LongOpenHashSet reached) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList cells = new LongArrayList();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(start);
        queue.enqueue(start);
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            cells.add(key);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int face = 0; face < 6; face++) {
                long next = PackedBlockPlan.pack(x + DX[face], y + DY[face], z + DZ[face]);
                if (wood.contains(next) && !reached.contains(next) && seen.add(next)) {
                    queue.enqueue(next);
                }
            }
        }
        cells.sort(null);
        return cells;
    }

    /** Une un trozo suelto con la madera apoyada por el camino más corto de hasta cuatro bloques. */
    private static boolean bridge(LongArrayList component, NatureVoxels voxels, NatureTerrain terrain, Bounds bounds,
                                  int planted, LongOpenHashSet wood, LongOpenHashSet reached) {
        long bestFrom = 0L;
        long bestTo = 0L;
        int best = Integer.MAX_VALUE;
        for (int index = 0; index < component.size() && best > 2; index++) {
            long key = component.getLong(index);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int steps = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (steps == 0 || steps >= best || steps > 4) {
                            continue;
                        }
                        long other = PackedBlockPlan.pack(x + dx, y + dy, z + dz);
                        if (reached.contains(other)) {
                            best = steps;
                            bestFrom = key;
                            bestTo = other;
                        }
                    }
                }
            }
        }
        if (best == Integer.MAX_VALUE) {
            return false;
        }
        int x = PackedBlockPlan.unpackX(bestFrom);
        int y = PackedBlockPlan.unpackY(bestFrom);
        int z = PackedBlockPlan.unpackZ(bestFrom);
        int tx = PackedBlockPlan.unpackX(bestTo);
        int ty = PackedBlockPlan.unpackY(bestTo);
        int tz = PackedBlockPlan.unpackZ(bestTo);
        LongArrayList path = new LongArrayList();
        int[] axes = new int[best];
        while (x != tx || y != ty || z != tz) {
            int axis;
            if (Math.abs(ty - y) >= Math.abs(tx - x) && Math.abs(ty - y) >= Math.abs(tz - z)) {
                y += Integer.signum(ty - y);
                axis = 1;
            } else if (Math.abs(tx - x) >= Math.abs(tz - z)) {
                x += Integer.signum(tx - x);
                axis = 0;
            } else {
                z += Integer.signum(tz - z);
                axis = 2;
            }
            long cell = PackedBlockPlan.pack(x, y, z);
            if (cell == bestTo) {
                break;
            }
            if (!wood.contains(cell)) {
                if (!bounds.contains(x, y, z) || voxels.contains(cell)
                        || !allowsWood(terrain.kind(x, y, z), y <= planted)) {
                    return false;
                }
                axes[path.size()] = axis;
                path.add(cell);
            }
        }
        for (int index = 0; index < path.size(); index++) {
            long cell = path.getLong(index);
            int cy = PackedBlockPlan.unpackY(cell);
            voxels.set(cell, NatureVoxels.Role.WOOD, axes[index], 0, 0, cy <= planted);
            wood.add(cell);
        }
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (int index = 0; index < path.size(); index++) {
            reached.add(path.getLong(index));
            queue.enqueue(path.getLong(index));
        }
        for (int index = 0; index < component.size(); index++) {
            if (reached.add(component.getLong(index))) {
                queue.enqueue(component.getLong(index));
            }
        }
        flood(queue, reached, wood);
        return true;
    }

    // ── Copa ───────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Copa a partir de los grupos de hojas: cada grupo es un elipsoide deformado por ruido, aplanado por debajo. Luego
     * se mide la distancia a la madera por hojas (se quitan las que pasan de 6), se vacía el interior dejando la
     * superficie casi llena y se rehace el camino de cada hoja que queda hasta la madera.
     */
    private static LongOpenHashSet canopy(TreeSkeleton skeleton, TreeSettings settings, NatureTerrain terrain,
                                          Bounds bounds, NatureVoxels voxels, long seed) {
        FastNoiseLite noise = new FastNoiseLite((int) (seed ^ (seed >>> 32)));
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.21f);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(2);
        LongOpenHashSet candidates = new LongOpenHashSet();
        for (TreeSkeleton.Clump clump : skeleton.clumps) {
            Vec3 center = clump.center();
            double width = clump.radius();
            double top = width * clump.flatten();
            double bottom = top * 0.55;
            int minX = (int) Math.floor(center.x() - width * 1.35);
            int maxX = (int) Math.floor(center.x() + width * 1.35);
            int minY = (int) Math.floor(center.y() - bottom * 1.35);
            int maxY = (int) Math.floor(center.y() + top * 1.35);
            int minZ = (int) Math.floor(center.z() - width * 1.35);
            int maxZ = (int) Math.floor(center.z() + width * 1.35);
            for (int x = minX; x <= maxX; x++) {
                double dx = (x + 0.5 - center.x()) / width;
                for (int z = minZ; z <= maxZ; z++) {
                    double dz = (z + 0.5 - center.z()) / width;
                    double horizontal = dx * dx + dz * dz;
                    if (horizontal > 1.9) {
                        continue;
                    }
                    for (int y = minY; y <= maxY; y++) {
                        double offset = y + 0.5 - center.y();
                        double dy = offset / (offset < 0.0 ? bottom : top);
                        double distance = Math.sqrt(horizontal + dy * dy);
                        if (distance > 1.35) {
                            continue;
                        }
                        double wobble = noise.GetNoise(x, y, z);
                        if (distance > 1.0 + 0.32 * wobble) {
                            continue;
                        }
                        long key = PackedBlockPlan.pack(x, y, z);
                        if (!candidates.contains(key) && bounds.contains(x, y, z) && !voxels.contains(key)
                                && terrain.kind(x, y, z) == NatureTerrain.Kind.FREE) {
                            candidates.add(key);
                        }
                    }
                }
            }
        }
        Long2IntOpenHashMap distance = new Long2IntOpenHashMap();
        distance.defaultReturnValue(-1);
        Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        LongArrayList order = reachFromWood(voxels, candidates, distance, parent, NatureVoxels.LEAF_REACH);

        Long2IntOpenHashMap depth = depth(order, distance, voxels);
        TreeSpecies.Form form = settings.species().form();
        double density = settings.density();
        double shell = adjust(0.965, density);
        double second = adjust(0.86, density);
        double inner = adjust(form.inner, density);
        LongOpenHashSet kept = new LongOpenHashSet();
        LongArrayList sorted = new LongArrayList(order);
        sorted.sort(null);
        for (int index = 0; index < sorted.size(); index++) {
            long key = sorted.getLong(index);
            int layer = depth.get(key);
            double chance = layer <= 1 ? shell : layer == 2 ? second : inner;
            if (distance.get(key) == 1) {
                // Hojas pegadas a la madera: visten las ramas.
                chance = Math.max(chance, adjust(0.9, density));
            }
            if (unit(key, seed) < chance) {
                kept.add(key);
            }
        }
        // Cada hoja que queda necesita su camino hasta la madera.
        LongArrayList keptOrder = new LongArrayList(kept);
        keptOrder.sort(null);
        for (int index = 0; index < keptOrder.size(); index++) {
            long step = parent.get(keptOrder.getLong(index));
            while (distance.get(step) > 0 && kept.add(step)) {
                step = parent.get(step);
            }
        }
        LongArrayList result = new LongArrayList(kept);
        result.sort(null);
        for (int index = 0; index < result.size(); index++) {
            long key = result.getLong(index);
            voxels.set(key, NatureVoxels.Role.LEAF, 0, distance.get(key), 0, false);
        }
        return kept;
    }

    private static double adjust(double chance, double density) {
        if (density <= 1.0) {
            return chance * Math.pow(Math.max(0.0, density), 0.6);
        }
        return chance + (1.0 - chance) * Math.min(1.0, density - 1.0);
    }

    /**
     * Distancia por hojas desde la madera (búsqueda en anchura cara con cara, hasta {@code reach}), con el paso
     * anterior de cada celda. Devuelve las celdas alcanzadas en orden de distancia.
     */
    private static LongArrayList reachFromWood(NatureVoxels voxels, LongOpenHashSet candidates,
                                               Long2IntOpenHashMap distance, Long2LongOpenHashMap parent, int reach) {
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongArrayList order = new LongArrayList();
        for (long key : voxels.sortedKeys()) {
            if (!NatureVoxels.role(voxels.info(key)).wood()) {
                continue;
            }
            distance.put(key, 0);
            queue.enqueue(key);
        }
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            int current = distance.get(key);
            if (current >= reach) {
                continue;
            }
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int face = 0; face < 6; face++) {
                long next = PackedBlockPlan.pack(x + DX[face], y + DY[face], z + DZ[face]);
                if (candidates.contains(next) && !distance.containsKey(next)) {
                    distance.put(next, current + 1);
                    parent.put(next, key);
                    order.add(next);
                    queue.enqueue(next);
                }
            }
        }
        return order;
    }

    /** Profundidad de cada hoja dentro de la copa: 1 en la superficie, hasta 4 en el interior. */
    private static Long2IntOpenHashMap depth(LongArrayList cells, Long2IntOpenHashMap distance, NatureVoxels voxels) {
        Long2IntOpenHashMap depth = new Long2IntOpenHashMap();
        depth.defaultReturnValue(4);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (int index = 0; index < cells.size(); index++) {
            long key = cells.getLong(index);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int face = 0; face < 6; face++) {
                long next = PackedBlockPlan.pack(x + DX[face], y + DY[face], z + DZ[face]);
                if (distance.get(next) < 0 && !voxels.contains(next)) {
                    depth.put(key, 1);
                    queue.enqueue(key);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            int current = depth.get(key);
            if (current >= 4) {
                continue;
            }
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int face = 0; face < 6; face++) {
                long next = PackedBlockPlan.pack(x + DX[face], y + DY[face], z + DZ[face]);
                if (distance.get(next) > 0 && !depth.containsKey(next)) {
                    depth.put(next, current + 1);
                    queue.enqueue(next);
                }
            }
        }
        return depth;
    }

    // ── Hojas colgantes ────────────────────────────────────────────────────────────────────────────────────

    /** Cortinas que cuelgan del borde inferior de la copa (sauce, cerezo, fantástico). */
    private static void hanging(TreeSkeleton skeleton, TreeSettings settings, NatureTerrain terrain, Bounds bounds,
                                NatureVoxels voxels, LongOpenHashSet leaves, long seed) {
        TreeSpecies.Form form = settings.species().form();
        if (form.hanging <= 0.0 || leaves.isEmpty()) {
            return;
        }
        double inner = skeleton.crownRadius * 0.35;
        double chance = form.hanging * Math.min(1.0, settings.density());
        int longest = Math.max(1, (int) Math.round(form.hangingLength * settings.height()));
        LongArrayList sorted = new LongArrayList(leaves);
        sorted.sort(null);
        for (int index = 0; index < sorted.size(); index++) {
            long key = sorted.getLong(index);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            double offsetX = x + 0.5 - skeleton.axisX;
            double offsetZ = z + 0.5 - skeleton.axisZ;
            double radial = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
            if (radial < inner || voxels.contains(x, y - 1, z) || unit(key, seed + 77) >= chance) {
                continue;
            }
            int length = 1 + (int) Math.floor((0.35 + 0.65 * unit(key, seed + 91)) * longest);
            int outX = Math.abs(offsetX) >= Math.abs(offsetZ) ? (int) Math.signum(offsetX) : 0;
            int outZ = outX == 0 ? (int) Math.signum(offsetZ) : 0;
            int step = NatureVoxels.distance(voxels.info(key)) + 1;
            int cx = x;
            int cy = y - 1;
            int cz = z;
            for (int piece = 0; piece < length && step <= NatureVoxels.HANGING_REACH; piece++) {
                if (!placeHanging(voxels, terrain, bounds, cx, cy, cz, step)) {
                    break;
                }
                long cell = PackedBlockPlan.pack(cx, cy, cz);
                if (piece < length / 3 && unit(cell, seed + 13) < 0.3) {
                    // Más gruesa arriba: una hoja al lado, hacia fuera.
                    placeHanging(voxels, terrain, bounds, cx + outX, cy, cz + outZ, step + 1);
                }
                if (piece > 0 && unit(cell, seed + 29) < 0.16 && step + 1 <= NatureVoxels.HANGING_REACH
                        && placeHanging(voxels, terrain, bounds, cx + outX, cy, cz + outZ, step + 1)) {
                    cx += outX;
                    cz += outZ;
                    step++;
                }
                cy--;
                step++;
            }
        }
    }

    private static boolean placeHanging(NatureVoxels voxels, NatureTerrain terrain, Bounds bounds, int x, int y,
                                        int z, int distance) {
        if (distance > NatureVoxels.HANGING_REACH || !bounds.contains(x, y, z) || voxels.contains(x, y, z)
                || terrain.kind(x, y, z) != NatureTerrain.Kind.FREE) {
            return false;
        }
        voxels.add(x, y, z, NatureVoxels.Role.HANGING, 0, distance, 0, false);
        return true;
    }

    /** Frondas de palmera: el nervio y los foliolos, unidos cara con cara desde la corona. */
    private static void fronds(TreeSkeleton skeleton, NatureTerrain terrain, Bounds bounds, NatureVoxels voxels) {
        if (skeleton.fronds.isEmpty()) {
            return;
        }
        LongOpenHashSet candidates = new LongOpenHashSet();
        for (List<Vec3> line : skeleton.fronds) {
            for (int index = 1; index < line.size(); index++) {
                line(line.get(index - 1), line.get(index), key -> {
                    int x = PackedBlockPlan.unpackX(key);
                    int y = PackedBlockPlan.unpackY(key);
                    int z = PackedBlockPlan.unpackZ(key);
                    if (bounds.contains(x, y, z) && !voxels.contains(key)
                            && terrain.kind(x, y, z) == NatureTerrain.Kind.FREE) {
                        candidates.add(key);
                    }
                });
            }
        }
        // Búsqueda por niveles: las fuentes (madera a 0, hojas a su distancia) entran en su nivel y cada fronda
        // toma el camino más corto.
        LongArrayList[] levels = new LongArrayList[NatureVoxels.HANGING_REACH + 1];
        for (int level = 0; level < levels.length; level++) {
            levels[level] = new LongArrayList();
        }
        for (long key : voxels.sortedKeys()) {
            int info = voxels.info(key);
            NatureVoxels.Role role = NatureVoxels.role(info);
            if (role.wood()) {
                levels[0].add(key);
            } else if (role.foliage()) {
                levels[Math.min(NatureVoxels.HANGING_REACH, NatureVoxels.distance(info))].add(key);
            }
        }
        Long2IntOpenHashMap distance = new Long2IntOpenHashMap();
        distance.defaultReturnValue(-1);
        for (int level = 0; level < NatureVoxels.HANGING_REACH; level++) {
            LongArrayList current = levels[level];
            for (int index = 0; index < current.size(); index++) {
                long key = current.getLong(index);
                int x = PackedBlockPlan.unpackX(key);
                int y = PackedBlockPlan.unpackY(key);
                int z = PackedBlockPlan.unpackZ(key);
                for (int face = 0; face < 6; face++) {
                    long next = PackedBlockPlan.pack(x + DX[face], y + DY[face], z + DZ[face]);
                    if (candidates.contains(next) && !distance.containsKey(next)) {
                        distance.put(next, level + 1);
                        levels[level + 1].add(next);
                    }
                }
            }
        }
        LongArrayList sorted = new LongArrayList(candidates);
        sorted.sort(null);
        for (int index = 0; index < sorted.size(); index++) {
            long key = sorted.getLong(index);
            int value = distance.get(key);
            if (value > 0) {
                voxels.set(key, NatureVoxels.Role.HANGING, 0, value, 0, false);
            }
        }
    }

    /** Celdas de un segmento, unidas cara con cara. */
    static void line(Vec3 from, Vec3 to, LongConsumer sink) {
        double length = from.distance(to);
        int samples = Math.max(1, (int) Math.ceil(length / 0.2));
        int px = (int) Math.floor(from.x());
        int py = (int) Math.floor(from.y());
        int pz = (int) Math.floor(from.z());
        sink.accept(PackedBlockPlan.pack(px, py, pz));
        for (int sample = 1; sample <= samples; sample++) {
            Vec3 point = from.lerp(to, sample / (double) samples);
            int x = (int) Math.floor(point.x());
            int y = (int) Math.floor(point.y());
            int z = (int) Math.floor(point.z());
            while (px != x || py != y || pz != z) {
                if (px != x) {
                    px += Integer.signum(x - px);
                } else if (py != y) {
                    py += Integer.signum(y - py);
                } else {
                    pz += Integer.signum(z - pz);
                }
                sink.accept(PackedBlockPlan.pack(px, py, pz));
            }
        }
    }

    /** Número en [0, 1) fijo para cada celda y semilla. */
    static double unit(long key, long seed) {
        return (TreeGrower.mix(key * 0x9E37_79B9_7F4A_7C15L + seed * 0xC2B2_AE3D_27D4_EB4FL) >>> 11) * 0x1.0p-53;
    }
}
