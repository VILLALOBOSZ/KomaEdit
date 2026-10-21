package com.komainu.moxia.selection;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Ampliar, reducir e invertir una selección de bloques sueltos. Hay dos maneras:
 *
 * <ul>
 *     <li><b>En volumen</b>: crece o encoge en las tres direcciones, paso a paso, con la forma elegida (cubo, octaedro
 *     o esfera redondeada) y metiendo también el aire. Invertir es todo lo que no estaba dentro de su caja.</li>
 *     <li><b>Por la superficie</b>: para lo que deja el lazo sobre un terreno o una fachada. Se mira hacia dónde da la
 *     cara visible de la selección (arriba en un terreno) y se trabaja por columnas en esa dirección: reducir quita
 *     las columnas del borde enteras y ampliar añade las de alrededor, con el bloque que se ve en cada una y tantos
 *     hacia dentro como tenía la columna de al lado. Invertir son las columnas de su rectángulo que no estaban.</li>
 * </ul>
 *
 * Lógica pura; lo del mundo llega por {@link Voxels}. Todas comprueban el máximo de bloques mientras crecen.
 */
public final class SelectionMorphology {
    public static final int MAX_STEPS = 16;
    /** Lo que puede subir o bajar el terreno de una columna a la siguiente al ampliar por la superficie. */
    public static final int SLOPE = 8;
    /** Lo que se busca por encima y por debajo de la selección al invertir por la superficie. */
    public static final int INVERT_SEARCH = 64;
    private static final int[][] FACES = offsets(6);
    private static final int[][] EDGES = offsets(18);
    private static final int[][] CORNERS = offsets(26);
    private static final int[][] SIDE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private SelectionMorphology() {
    }

    // ── Volumen ──────────────────────────────────────────────────────────────────────────────────────────

    /** Crece {@code steps} bloques en todas direcciones, aire incluido. */
    public static BlockSet grow(BlockSet set, int steps, BrushShape shape, int maxBlocks) {
        checkSteps(steps);
        BlockSet result = set.copy();
        LongArrayList frontier = border(set);
        for (int step = 0; step < steps; step++) {
            int[][] kernel = kernel(shape);
            LongArrayList added = new LongArrayList();
            for (int index = 0; index < frontier.size(); index++) {
                long block = frontier.getLong(index);
                int x = BlockSet.x(block);
                int y = BlockSet.y(block);
                int z = BlockSet.z(block);
                for (int[] offset : kernel) {
                    int ny = y + offset[1];
                    if (!BlockSet.storable(ny)) {
                        continue;
                    }
                    long next = BlockSet.pack(x + offset[0], ny, z + offset[2]);
                    if (result.addPacked(next)) {
                        added.add(next);
                        if (result.size() > maxBlocks) {
                            throw tooMany(maxBlocks);
                        }
                    }
                }
            }
            frontier = added;
        }
        return result;
    }

    /** Encoge {@code steps} bloques: en cada paso se va lo que tiene al lado algo que no está seleccionado. */
    public static BlockSet shrink(BlockSet set, int steps, BrushShape shape) {
        checkSteps(steps);
        BlockSet result = set.copy();
        LongOpenHashSet candidates = new LongOpenHashSet(set.size());
        set.forEach(candidates::add);
        for (int step = 0; step < steps && !result.isEmpty(); step++) {
            int[][] kernel = kernel(shape);
            LongArrayList removed = new LongArrayList();
            LongIterator iterator = candidates.iterator();
            while (iterator.hasNext()) {
                long block = iterator.nextLong();
                if (result.containsPacked(block) && exposed(result, block, kernel)) {
                    removed.add(block);
                }
            }
            candidates = new LongOpenHashSet();
            for (int index = 0; index < removed.size(); index++) {
                result.removePacked(removed.getLong(index));
            }
            // Solo lo que estaba al lado de lo quitado puede quedar al descubierto en el paso siguiente.
            for (int index = 0; index < removed.size(); index++) {
                long block = removed.getLong(index);
                for (int[] offset : CORNERS) {
                    int ny = BlockSet.y(block) + offset[1];
                    if (BlockSet.storable(ny)) {
                        long next = BlockSet.pack(BlockSet.x(block) + offset[0], ny, BlockSet.z(block) + offset[2]);
                        if (result.containsPacked(next)) {
                            candidates.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Todo lo que no estaba seleccionado dentro de la caja que contiene la selección. */
    public static BlockSet invert(BlockSet set, int maxBlocks) {
        int[] box = set.bounds();
        if (box == null) {
            throw new IllegalArgumentException("No hay selección que invertir");
        }
        long volume = (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
        if (volume - set.size() > maxBlocks) {
            throw tooMany(maxBlocks);
        }
        BlockSet result = new BlockSet();
        for (int x = box[0]; x <= box[3]; x++) {
            for (int y = box[1]; y <= box[4]; y++) {
                for (int z = box[2]; z <= box[5]; z++) {
                    if (!set.contains(x, y, z)) {
                        result.add(x, y, z);
                    }
                }
            }
        }
        return result;
    }

    // ── Superficie ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * La cara hacia la que mira la selección (0 −X, 1 +X, 2 −Y, 3 +Y, 4 −Z, 5 +Z): la que más veces da al aire (o a
     * lo que no cuenta). En un empate gana +Y, y sin ninguna al aire, la que más veces da fuera de la selección.
     */
    public static int facing(BlockSet set, Voxels voxels, boolean nonSolid) {
        long[] open = new long[6];
        long[] outside = new long[6];
        LongIterator iterator = set.iterator();
        while (iterator.hasNext()) {
            long block = iterator.nextLong();
            int x = BlockSet.x(block);
            int y = BlockSet.y(block);
            int z = BlockSet.z(block);
            for (int direction = 0; direction < 6; direction++) {
                int[] offset = FACE_ORDER[direction];
                int nx = x + offset[0];
                int ny = y + offset[1];
                int nz = z + offset[2];
                if (set.contains(nx, ny, nz)) {
                    continue;
                }
                outside[direction]++;
                if (!voxels.counts(nx, ny, nz, nonSolid)) {
                    open[direction]++;
                }
            }
        }
        long[] counts = max(open) > 0 ? open : outside;
        int best = 3;
        for (int direction = 0; direction < 6; direction++) {
            if (counts[direction] > counts[best]) {
                best = direction;
            }
        }
        return best;
    }

    /** Reduce por la superficie: quita enteras las columnas que están a menos de {@code steps} del borde. */
    public static BlockSet shrinkSurface(BlockSet set, int steps, BrushShape shape, Voxels voxels, boolean nonSolid) {
        checkSteps(steps);
        Columns columns = Columns.of(set, facing(set, voxels, nonSolid));
        LongOpenHashSet kept = new LongOpenHashSet(columns.surface.keySet());
        for (int step = 0; step < steps && !kept.isEmpty(); step++) {
            int[][] kernel = flatKernel(shape, step);
            LongArrayList removed = new LongArrayList();
            LongIterator iterator = kept.iterator();
            while (iterator.hasNext()) {
                long column = iterator.nextLong();
                for (int[] offset : kernel) {
                    if (!kept.contains(Columns.key(Columns.u(column) + offset[0], Columns.v(column) + offset[1]))) {
                        removed.add(column);
                        break;
                    }
                }
            }
            kept.removeAll(removed);
        }
        BlockSet result = new BlockSet();
        LongIterator iterator = set.iterator();
        while (iterator.hasNext()) {
            long block = iterator.nextLong();
            if (kept.contains(columns.columnOf(block))) {
                result.addPacked(block);
            }
        }
        return result;
    }

    /**
     * Amplía por la superficie: cada paso añade las columnas de alrededor con el bloque que se ve en ellas (buscado a
     * {@value #SLOPE} bloques arriba o abajo del de la columna vecina) y tantos hacia dentro como tenía la vecina.
     */
    public static BlockSet growSurface(BlockSet set, int steps, BrushShape shape, Voxels voxels, boolean nonSolid,
                                       int maxBlocks) {
        checkSteps(steps);
        int face = facing(set, voxels, nonSolid);
        Columns columns = Columns.of(set, face);
        BlockSet result = set.copy();
        LongOpenHashSet known = new LongOpenHashSet(columns.surface.keySet());
        LongArrayList frontier = new LongArrayList(columns.surface.keySet());
        Long2IntOpenHashMap surface = new Long2IntOpenHashMap(columns.surface);
        Long2IntOpenHashMap thickness = new Long2IntOpenHashMap(columns.thickness);
        for (int step = 0; step < steps; step++) {
            int[][] kernel = flatKernel(shape, step);
            LongArrayList added = new LongArrayList();
            for (int index = 0; index < frontier.size(); index++) {
                long from = frontier.getLong(index);
                for (int[] offset : kernel) {
                    long column = Columns.key(Columns.u(from) + offset[0], Columns.v(from) + offset[1]);
                    if (known.contains(column)) {
                        continue;
                    }
                    int found = columns.findSurface(column, surface.get(from), SLOPE, voxels, nonSolid);
                    if (found == Integer.MIN_VALUE) {
                        continue;
                    }
                    known.add(column);
                    added.add(column);
                    surface.put(column, found);
                    thickness.put(column, thickness.get(from));
                    columns.fill(result, column, found, thickness.get(from), voxels, nonSolid);
                    if (result.size() > maxBlocks) {
                        throw tooMany(maxBlocks);
                    }
                }
            }
            frontier = added;
        }
        return result;
    }

    /**
     * Invierte por la superficie: las columnas del rectángulo de la selección que no estaban, con su bloque visible
     * y el grosor más repetido de la selección.
     */
    public static BlockSet invertSurface(BlockSet set, Voxels voxels, boolean nonSolid, int maxBlocks) {
        if (set.isEmpty()) {
            throw new IllegalArgumentException("No hay selección que invertir");
        }
        Columns columns = Columns.of(set, facing(set, voxels, nonSolid));
        int minU = Integer.MAX_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int maxV = Integer.MIN_VALUE;
        Int2IntOpenHashMap thicknesses = new Int2IntOpenHashMap();
        for (long column : columns.surface.keySet()) {
            minU = Math.min(minU, Columns.u(column));
            maxU = Math.max(maxU, Columns.u(column));
            minV = Math.min(minV, Columns.v(column));
            maxV = Math.max(maxV, Columns.v(column));
            thicknesses.addTo(columns.thickness.get(column), 1);
        }
        int usual = 1;
        int seen = 0;
        for (Int2IntOpenHashMap.Entry entry : thicknesses.int2IntEntrySet()) {
            if (entry.getIntValue() > seen || entry.getIntValue() == seen && entry.getIntKey() < usual) {
                usual = entry.getIntKey();
                seen = entry.getIntValue();
            }
        }
        int middle = (columns.lowest + columns.highest) / 2;
        int search = (columns.highest - columns.lowest) / 2 + INVERT_SEARCH;
        BlockSet result = new BlockSet();
        for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
                long column = Columns.key(u, v);
                if (columns.surface.containsKey(column)) {
                    continue;
                }
                int found = columns.findSurface(column, middle, search, voxels, nonSolid);
                if (found != Integer.MIN_VALUE) {
                    columns.fill(result, column, found, usual, voxels, nonSolid);
                    if (result.size() > maxBlocks) {
                        throw tooMany(maxBlocks);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Las columnas de una selección en la dirección {@code face}: {@code u} y {@code v} son las otras dos
     * coordenadas (en orden x, y, z) y {@code w} la de la dirección; «fuera» es hacia donde mira {@code face}.
     */
    static final class Columns {
        final int axis;
        final int outward;
        /** Columna → coordenada {@code w} de su bloque más de fuera. */
        final Long2IntOpenHashMap surface = new Long2IntOpenHashMap();
        /** Columna → bloques que tiene. */
        final Long2IntOpenHashMap thickness = new Long2IntOpenHashMap();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        private Columns(int face) {
            axis = face >> 1;
            outward = (face & 1) == 1 ? 1 : -1;
        }

        static Columns of(BlockSet set, int face) {
            Columns columns = new Columns(face);
            LongIterator iterator = set.iterator();
            while (iterator.hasNext()) {
                long block = iterator.nextLong();
                long column = columns.columnOf(block);
                int w = columns.w(block);
                columns.lowest = Math.min(columns.lowest, w);
                columns.highest = Math.max(columns.highest, w);
                if (!columns.surface.containsKey(column) || (w - columns.surface.get(column)) * columns.outward > 0) {
                    columns.surface.put(column, w);
                }
                columns.thickness.addTo(column, 1);
            }
            return columns;
        }

        long columnOf(long block) {
            int[] coordinates = {BlockSet.x(block), BlockSet.y(block), BlockSet.z(block)};
            return switch (axis) {
                case 0 -> key(coordinates[1], coordinates[2]);
                case 1 -> key(coordinates[0], coordinates[2]);
                default -> key(coordinates[0], coordinates[1]);
            };
        }

        int w(long block) {
            return switch (axis) {
                case 0 -> BlockSet.x(block);
                case 1 -> BlockSet.y(block);
                default -> BlockSet.z(block);
            };
        }

        /** El bloque {@code (x, y, z)} de la columna a la altura {@code w}. */
        int[] at(long column, int w) {
            int u = u(column);
            int v = v(column);
            return switch (axis) {
                case 0 -> new int[]{w, u, v};
                case 1 -> new int[]{u, w, v};
                default -> new int[]{u, v, w};
            };
        }

        /**
         * El bloque que se ve en la columna más cerca de {@code around} (como mucho a {@code range}): uno que cuenta con
         * lo de fuera libre. Se busca alrededor y no desde arriba para seguir el mismo suelo: bajo un árbol, el suelo y
         * no la copa. {@link Integer#MIN_VALUE} si no hay.
         */
        int findSurface(long column, int around, int range, Voxels voxels, boolean nonSolid) {
            for (int step = 0; step <= 2 * range; step++) {
                // 0, +1, −1, +2, −2…: hacia fuera primero, para que en un escalón gane el de arriba.
                int offset = (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
                int w = around + offset * outward;
                int[] here = at(column, w);
                if (!BlockSet.storable(here[1]) || !voxels.counts(here[0], here[1], here[2], nonSolid)) {
                    continue;
                }
                int[] outside = at(column, w + outward);
                if (!voxels.counts(outside[0], outside[1], outside[2], nonSolid)) {
                    return w;
                }
            }
            return Integer.MIN_VALUE;
        }

        /** Mete en {@code into} el bloque visible de la columna y los de debajo, hasta {@code depth} y sin salir. */
        void fill(BlockSet into, long column, int surfaceW, int depth, Voxels voxels, boolean nonSolid) {
            for (int layer = 0; layer < depth; layer++) {
                int[] block = at(column, surfaceW - layer * outward);
                if (!BlockSet.storable(block[1])
                        || layer > 0 && !voxels.counts(block[0], block[1], block[2], nonSolid)) {
                    return;
                }
                into.add(block[0], block[1], block[2]);
            }
        }

        static long key(int u, int v) {
            return (long) u << 32 | v & 0xFFFFFFFFL;
        }

        static int u(long key) {
            return (int) (key >> 32);
        }

        static int v(long key) {
            return (int) key;
        }
    }

    // ── Comunes ──────────────────────────────────────────────────────────────────────────────────────────

    /** Orden de las caras: 0 −X, 1 +X, 2 −Y, 3 +Y, 4 −Z, 5 +Z. */
    private static final int[][] FACE_ORDER = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    /** Los vecinos de cada paso: el cubo, los 26; el octaedro, los 6; la esfera, los 18 (sin las esquinas). */
    private static int[][] kernel(BrushShape shape) {
        return switch (shape) {
            case CUBE -> CORNERS;
            case OCTAHEDRON -> FACES;
            case SPHERE -> EDGES;
        };
    }

    /** Los vecinos de cada paso en las columnas: cuadrado, rombo o, en la esfera, uno y otro por turnos. */
    private static int[][] flatKernel(BrushShape shape, int step) {
        return switch (shape) {
            case CUBE -> AROUND;
            case OCTAHEDRON -> SIDE;
            case SPHERE -> step % 2 == 0 ? AROUND : SIDE;
        };
    }

    /** Los bloques de la selección con algún vecino de cara fuera de ella: los únicos que hacen crecer el primer paso. */
    private static LongArrayList border(BlockSet set) {
        LongArrayList border = new LongArrayList();
        LongIterator iterator = set.iterator();
        while (iterator.hasNext()) {
            long block = iterator.nextLong();
            if (exposed(set, block, FACES)) {
                border.add(block);
            }
        }
        return border;
    }

    private static boolean exposed(BlockSet set, long block, int[][] kernel) {
        int x = BlockSet.x(block);
        int y = BlockSet.y(block);
        int z = BlockSet.z(block);
        for (int[] offset : kernel) {
            if (!set.contains(x + offset[0], y + offset[1], z + offset[2])) {
                return true;
            }
        }
        return false;
    }

    private static int[][] offsets(int count) {
        int[][] result = new int[count][];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    boolean wanted = switch (count) {
                        case 6 -> manhattan == 1;
                        case 18 -> manhattan == 1 || manhattan == 2;
                        default -> manhattan > 0;
                    };
                    if (wanted) {
                        result[index++] = new int[]{dx, dy, dz};
                    }
                }
            }
        }
        return result;
    }

    private static long max(long[] values) {
        long result = 0;
        for (long value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    private static void checkSteps(int steps) {
        if (steps < 1 || steps > MAX_STEPS) {
            throw new IllegalArgumentException("Se amplía o reduce de 1 a " + MAX_STEPS + " bloques");
        }
    }

    private static IllegalArgumentException tooMany(int maxBlocks) {
        return new IllegalArgumentException("La selección pasaría de " + maxBlocks + " bloques");
    }
}
