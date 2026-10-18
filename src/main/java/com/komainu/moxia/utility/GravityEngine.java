package com.komainu.moxia.utility;

import com.komainu.moxia.region.RegionSnapshot;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

import java.util.BitSet;

/**
 * Gravedad sobre la selección, sin Minecraft: cada columna se compacta hacia abajo como si los bloques fueran arena,
 * conservando su orden y su número.
 *
 * <ul>
 *     <li>Una columna se corta donde termina la selección y en los bloques fijos (los que no pasan la máscara, los
 *     que guardan datos como cofres o carteles y, con {@code unsupportedOnly}, los que se sostienen): los bloques
 *     caen hasta el fondo de su tramo.</li>
 *     <li>Con {@code unsupportedOnly} solo caen los bloques que no se apoyan en nada: los que no están unidos por
 *     alguna cara, a través de otros bloques, a algo de fuera de la selección. Un saliente pegado a una pared se
 *     queda; una isla flotante cae.</li>
 *     <li>El agua (y la lava) no frena la caída: los bloques la atraviesan y el hueco que dejan por debajo del
 *     nivel del líquido de la columna se llena de él, así la superficie no baja. Un bloque que cae bajo ese nivel
 *     queda anegado si admite agua, y deja de estarlo si acaba por encima.</li>
 * </ul>
 */
public final class GravityEngine {
    /** Contenido nuevo de una posición: aire, agua o lava; cualquier otro valor es un id crudo de la foto. */
    public static final int AIR = -1;
    public static final int WATER = -2;
    public static final int LAVA = -3;
    /** Para {@link Sink#put}: no tocar la propiedad {@code waterlogged}. */
    public static final int KEEP_WATERLOG = -1;

    private GravityEngine() {
    }

    /** Destino de los cambios; {@code waterlogged} es 0, 1 o {@link #KEEP_WATERLOG}. */
    @FunctionalInterface
    public interface Sink {
        void put(int x, int y, int z, int content, int waterlogged);
    }

    /** Qué bloques pueden caer (la máscara); {@code null} acepta todos. */
    @FunctionalInterface
    public interface Movable {
        boolean test(int x, int y, int z);
    }

    /** Lo que sabe la gravedad de cada estado. */
    public interface Kinds {
        /** Agua o lava sin bloque (no un bloque anegado). */
        boolean fluid(int rawId);

        boolean lava(int rawId);

        /** Si el estado tiene la propiedad {@code waterlogged} (y si está a {@code true}). */
        boolean waterloggable(int rawId);

        boolean waterlogged(int rawId);

        /** Bloques con datos propios (cofres, carteles, cabezas…): no se mueven para no perder su contenido. */
        default boolean blockEntity(int rawId) {
            return false;
        }
    }

    /** Recuento: bloques que se han movido y posiciones escritas. */
    public record Result(int moved, int writes) {
    }

    public static Result apply(RegionSnapshot snapshot, Kinds kinds, Movable movable, boolean unsupportedOnly,
                               Sink sink, Runnable checkCancelled) {
        BitSet supported = unsupportedOnly ? supported(snapshot, kinds, checkCancelled) : null;
        int sizeY = snapshot.maximumY() - snapshot.minimumY() + 1;
        int[] column = new int[sizeY];
        int[] blocks = new int[sizeY];
        int[] blockY = new int[sizeY];
        int moved = 0;
        int writes = 0;
        for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
            for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                checkCancelled.run();
                int y = snapshot.minimumY();
                while (y <= snapshot.maximumY()) {
                    // Tramo: posiciones seleccionadas seguidas que no sean bloques fijos.
                    if (!snapshot.selected(x, y, z) || fixed(snapshot, kinds, movable, supported, x, y, z)) {
                        y++;
                        continue;
                    }
                    int start = y;
                    while (y <= snapshot.maximumY() && snapshot.selected(x, y, z)
                            && !fixed(snapshot, kinds, movable, supported, x, y, z)) {
                        y++;
                    }
                    int end = y - 1;
                    int[] counts = settle(snapshot, kinds, x, z, start, end, column, blocks, blockY);
                    moved += counts[0];
                    for (int level = start; level <= end; level++) {
                        int content = column[level - start];
                        int original = snapshot.rawId(x, level, z);
                        if (content == original || content < 0 && sameFluid(snapshot, kinds, x, level, z, content)) {
                            continue;
                        }
                        int waterlog = KEEP_WATERLOG;
                        if (content >= 0 && kinds.waterloggable(content)) {
                            boolean under = counts[2] == WATER && level <= counts[1];
                            if (kinds.waterlogged(content) != under) {
                                waterlog = under ? 1 : 0;
                            }
                        }
                        sink.put(x, level, z, content, waterlog);
                        writes++;
                    }
                }
            }
        }
        return new Result(moved, writes);
    }

    /**
     * Compacta un tramo. Deja en {@code column} el contenido nuevo de cada nivel y devuelve {@code {movidos,
     * nivel del líquido, líquido}} (nivel -1 si no hay líquido en el tramo).
     */
    private static int[] settle(RegionSnapshot snapshot, Kinds kinds, int x, int z, int start, int end,
                                int[] column, int[] blocks, int[] blockY) {
        int count = 0;
        int fluidTop = Integer.MIN_VALUE;
        int fluid = AIR;
        // Un bloque anegado marca el nivel del agua solo si no se va a mover (no tiene huecos debajo): si cae, se
        // lleva su agua.
        boolean resting = true;
        for (int y = start; y <= end; y++) {
            RegionSnapshot.StateInfo info = snapshot.info(x, y, z);
            int rawId = info.rawId();
            boolean pureFluid = kinds.fluid(rawId);
            if (info.liquid() && (pureFluid || resting)) {
                boolean lava = kinds.lava(rawId);
                if (!lava || fluid != WATER) {
                    fluid = lava ? LAVA : WATER;
                }
                fluidTop = Math.max(fluidTop, y);
            }
            if (info.air() || pureFluid) {
                resting = false;
                continue;
            }
            blocks[count] = rawId;
            blockY[count] = y;
            count++;
        }
        int moved = 0;
        for (int index = 0; index < count; index++) {
            if (blockY[index] != start + index) {
                moved++;
            }
        }
        for (int y = start; y <= end; y++) {
            int index = y - start;
            if (index < count) {
                column[index] = blocks[index];
            } else {
                column[index] = y <= fluidTop ? fluid : AIR;
            }
        }
        return new int[]{moved, fluidTop == Integer.MIN_VALUE ? -1 : fluidTop, fluid};
    }

    private static boolean fixed(RegionSnapshot snapshot, Kinds kinds, Movable movable, BitSet supported,
                                 int x, int y, int z) {
        RegionSnapshot.StateInfo info = snapshot.info(x, y, z);
        if (info.air() || kinds.fluid(info.rawId())) {
            return false;
        }
        if (kinds.blockEntity(info.rawId()) || movable != null && !movable.test(x, y, z)) {
            return true;
        }
        return supported != null && supported.get(index(snapshot, x, y, z));
    }

    /** Si la posición ya tiene ese contenido: aire sobre aire, o agua (lava) sin bloque sobre agua (lava). */
    private static boolean sameFluid(RegionSnapshot snapshot, Kinds kinds, int x, int y, int z, int content) {
        RegionSnapshot.StateInfo info = snapshot.info(x, y, z);
        if (content == AIR) {
            return info.air();
        }
        return kinds.fluid(info.rawId()) && kinds.lava(info.rawId()) == (content == LAVA);
    }

    /**
     * Bloques unidos por alguna cara, a través de otros bloques, a algo de fuera de la selección (el margen
     * capturado) o a la capa más baja de la foto.
     */
    static BitSet supported(RegionSnapshot snapshot, Kinds kinds, Runnable checkCancelled) {
        int sizeX = snapshot.maximumX() - snapshot.minimumX() + 1;
        int sizeZ = snapshot.maximumZ() - snapshot.minimumZ() + 1;
        int sizeY = snapshot.maximumY() - snapshot.minimumY() + 1;
        BitSet visited = new BitSet(sizeX * sizeY * sizeZ);
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    if (!solid(snapshot, kinds, x, y, z)) {
                        continue;
                    }
                    if (!snapshot.selected(x, y, z) || y == snapshot.minimumY()) {
                        int index = index(snapshot, x, y, z);
                        visited.set(index);
                        queue.enqueue(index);
                    }
                }
            }
        }
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int steps = 0;
        while (!queue.isEmpty()) {
            if ((steps++ & 4_095) == 0) {
                checkCancelled.run();
            }
            int index = queue.dequeueInt();
            int x = snapshot.minimumX() + index % sizeX;
            int z = snapshot.minimumZ() + (index / sizeX) % sizeZ;
            int y = snapshot.minimumY() + index / (sizeX * sizeZ);
            for (int[] offset : offsets) {
                int nx = x + offset[0];
                int ny = y + offset[1];
                int nz = z + offset[2];
                if (!snapshot.contains(nx, ny, nz) || !solid(snapshot, kinds, nx, ny, nz)) {
                    continue;
                }
                int next = index(snapshot, nx, ny, nz);
                if (!visited.get(next)) {
                    visited.set(next);
                    queue.enqueue(next);
                }
            }
        }
        return visited;
    }

    private static boolean solid(RegionSnapshot snapshot, Kinds kinds, int x, int y, int z) {
        RegionSnapshot.StateInfo info = snapshot.info(x, y, z);
        return !info.air() && !kinds.fluid(info.rawId());
    }

    private static int index(RegionSnapshot snapshot, int x, int y, int z) {
        int sizeX = snapshot.maximumX() - snapshot.minimumX() + 1;
        int sizeZ = snapshot.maximumZ() - snapshot.minimumZ() + 1;
        return ((y - snapshot.minimumY()) * sizeZ + (z - snapshot.minimumZ())) * sizeX + (x - snapshot.minimumX());
    }
}
