package com.komainu.moxia.biome;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Celdas de bioma que toca una zona, sin Minecraft. Minecraft guarda el bioma en celdas de 4×4×4 bloques: una celda
 * cambia entera si tiene al menos un bloque dentro de la zona. Cada celda se representa por su bloque de origen
 * (las coordenadas múltiplos de 4) empaquetado con {@link PackedBlockPlan#pack}.
 */
public final class BiomeCells {
    public static final int SIZE = 4;

    private BiomeCells() {
    }

    /** Si un bloque está dentro de la zona (selección y máscara). */
    @FunctionalInterface
    public interface Inside {
        boolean test(int x, int y, int z);
    }

    /**
     * @param box      caja a recorrer ({@code minX, minY, minZ, maxX, maxY, maxZ})
     * @param inside   {@code null}: toda la caja
     * @param vertical relleno vertical: cada columna de celdas tocada se pinta de {@code minY} a {@code maxY}
     */
    public static long[] cells(int[] box, Inside inside, boolean vertical, int minY, int maxY,
                               Runnable checkCancelled) {
        LongOpenHashSet cells = new LongOpenHashSet();
        if (inside == null) {
            for (int cy = cell(box[1]); cy <= cell(box[4]); cy++) {
                for (int cz = cell(box[2]); cz <= cell(box[5]); cz++) {
                    for (int cx = cell(box[0]); cx <= cell(box[3]); cx++) {
                        cells.add(PackedBlockPlan.pack(cx * SIZE, cy * SIZE, cz * SIZE));
                    }
                }
            }
        } else {
            for (int y = box[1]; y <= box[4]; y++) {
                for (int z = box[2]; z <= box[5]; z++) {
                    checkCancelled.run();
                    for (int x = box[0]; x <= box[3]; x++) {
                        if (inside.test(x, y, z)) {
                            cells.add(PackedBlockPlan.pack(cell(x) * SIZE, cell(y) * SIZE, cell(z) * SIZE));
                        }
                    }
                }
            }
        }
        if (vertical) {
            LongOpenHashSet columns = new LongOpenHashSet();
            for (long packed : cells) {
                columns.add(PackedBlockPlan.pack(PackedBlockPlan.unpackX(packed), 0, PackedBlockPlan.unpackZ(packed)));
            }
            cells.clear();
            for (long column : columns) {
                for (int cy = cell(minY); cy <= cell(maxY); cy++) {
                    cells.add(PackedBlockPlan.pack(PackedBlockPlan.unpackX(column), cy * SIZE,
                            PackedBlockPlan.unpackZ(column)));
                }
            }
        }
        long[] result = cells.toLongArray();
        // Por chunks y de abajo arriba: las escrituras van seguidas en la misma sección.
        LongArrays.quickSort(result, (LongComparator) (a, b) -> {
            int byChunk = Long.compare(chunk(a), chunk(b));
            return byChunk != 0 ? byChunk : Long.compare(order(a), order(b));
        });
        return result;
    }

    /** Celdas con algún bloque a menos de {@code radius} del centro (el pincel). */
    public static long[] sphere(int centerX, int centerY, int centerZ, int radius, boolean vertical, int minY,
                                int maxY, Inside extra) {
        int[] box = {centerX - radius, Math.max(minY, centerY - radius), centerZ - radius, centerX + radius,
                Math.min(maxY, centerY + radius), centerZ + radius};
        long radiusSquared = (long) radius * radius;
        return cells(box, (x, y, z) -> {
            long dx = x - centerX;
            long dy = y - centerY;
            long dz = z - centerZ;
            return dx * dx + dy * dy + dz * dz <= radiusSquared && (extra == null || extra.test(x, y, z));
        }, vertical, minY, maxY, () -> {
        });
    }

    static int cell(int block) {
        return Math.floorDiv(block, SIZE);
    }

    private static long chunk(long packed) {
        return ((long) (PackedBlockPlan.unpackX(packed) >> 4) << 32)
                ^ (PackedBlockPlan.unpackZ(packed) >> 4 & 0xFFFFFFFFL);
    }

    private static long order(long packed) {
        return ((long) PackedBlockPlan.unpackY(packed) << 40) ^ ((long) PackedBlockPlan.unpackZ(packed) << 20)
                ^ (PackedBlockPlan.unpackX(packed) & 0xFFFFF);
    }
}
