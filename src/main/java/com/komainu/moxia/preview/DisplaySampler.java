package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Elige qué cambios se enseñan con displays sin pasar del límite de entidades por jugador:
 *
 * <ol>
 *     <li>Si caben, todos.</li>
 *     <li>Si no, la superficie: los cambios con alguna cara hacia una posición que no cambia. Una esfera maciza se
 *     ve como su cáscara.</li>
 *     <li>Si la superficie tampoco cabe, una muestra repartida por el espacio: un cambio por celda de una rejilla que
 *     se hace más gruesa hasta caber, y el hueco que quede se rellena a saltos regulares.</li>
 * </ol>
 *
 * Es determinista: la misma lista de cambios da siempre la misma elección.
 */
public final class DisplaySampler {
    public enum Mode {
        ALL,
        SURFACE,
        SAMPLE
    }

    /** Índices elegidos (en orden creciente, sin repetir) y cómo se eligieron. */
    public record Choice(int[] indices, Mode mode, int total, int surface) {
        public int shown() {
            return indices.length;
        }
    }

    private static final int MIN_Y = -2048;
    private static final int MAX_Y = 2047;

    private DisplaySampler() {
    }

    /**
     * @param positions posiciones empaquetadas de los cambios ({@link PackedBlockPlan#pack}); solo se leen las
     *                  {@code count} primeras
     * @param limit     displays como mucho; 0 deja solo la caja
     */
    public static Choice choose(long[] positions, int count, int limit) {
        if (count < 0 || count > positions.length) {
            throw new IllegalArgumentException("Recuento fuera del array: " + count);
        }
        int budget = Math.max(0, limit);
        if (count <= budget) {
            return new Choice(range(count), Mode.ALL, count, count);
        }
        LongOpenHashSet all = new LongOpenHashSet(count);
        for (int index = 0; index < count; index++) {
            all.add(positions[index]);
        }
        IntArrayList surface = new IntArrayList();
        for (int index = 0; index < count; index++) {
            if (exposed(all, positions[index])) {
                surface.add(index);
            }
        }
        if (surface.size() <= budget) {
            return new Choice(surface.toIntArray(), Mode.SURFACE, count, surface.size());
        }
        return new Choice(sample(positions, surface, budget), Mode.SAMPLE, count, surface.size());
    }

    private static boolean exposed(LongOpenHashSet all, long packed) {
        int x = PackedBlockPlan.unpackX(packed);
        int y = PackedBlockPlan.unpackY(packed);
        int z = PackedBlockPlan.unpackZ(packed);
        return !all.contains(PackedBlockPlan.pack(x - 1, y, z))
                || !all.contains(PackedBlockPlan.pack(x + 1, y, z))
                || y == MIN_Y || !all.contains(PackedBlockPlan.pack(x, y - 1, z))
                || y == MAX_Y || !all.contains(PackedBlockPlan.pack(x, y + 1, z))
                || !all.contains(PackedBlockPlan.pack(x, y, z - 1))
                || !all.contains(PackedBlockPlan.pack(x, y, z + 1));
    }

    /**
     * Una muestra repartida por el espacio de {@code candidates} (índices de {@code positions}): un cambio por celda
     * de una rejilla que se hace más gruesa hasta caber y el resto a saltos regulares. Devuelve índices crecientes.
     */
    static int[] sample(long[] positions, IntArrayList candidates, int budget) {
        if (budget == 0) {
            return new int[0];
        }
        boolean[] chosen = new boolean[candidates.size()];
        int picked = 0;
        for (int shift = 1; shift < 26; shift++) {
            LongOpenHashSet cells = new LongOpenHashSet();
            int[] representatives = new int[Math.min(candidates.size(), budget + 1)];
            int found = 0;
            for (int slot = 0; slot < candidates.size() && found <= budget; slot++) {
                long packed = positions[candidates.getInt(slot)];
                long cell = cellKey(packed, shift);
                if (cells.add(cell)) {
                    if (found < representatives.length) {
                        representatives[found] = slot;
                    }
                    found++;
                }
            }
            if (found <= budget) {
                for (int index = 0; index < found; index++) {
                    chosen[representatives[index]] = true;
                }
                picked = found;
                break;
            }
        }
        // Rellena lo que sobra del presupuesto a saltos regulares entre los que no salieron en la rejilla.
        int remaining = budget - picked;
        int free = candidates.size() - picked;
        if (remaining > 0 && free > 0) {
            double stride = (double) free / remaining;
            double next = stride / 2.0;
            int seen = 0;
            for (int slot = 0; slot < candidates.size() && remaining > 0; slot++) {
                if (chosen[slot]) {
                    continue;
                }
                if (seen >= (int) next) {
                    chosen[slot] = true;
                    remaining--;
                    next += stride;
                }
                seen++;
            }
        }
        IntArrayList result = new IntArrayList(budget);
        for (int slot = 0; slot < candidates.size(); slot++) {
            if (chosen[slot]) {
                result.add(candidates.getInt(slot));
            }
        }
        return result.toIntArray();
    }

    private static long cellKey(long packed, int shift) {
        long x = PackedBlockPlan.unpackX(packed) >> shift;
        long y = PackedBlockPlan.unpackY(packed) >> shift;
        long z = PackedBlockPlan.unpackZ(packed) >> shift;
        return (x & 0x1FFFFFL) << 42 | (z & 0x1FFFFFL) << 21 | (y & 0x1FFFFFL);
    }

    private static int[] range(int count) {
        int[] indices = new int[count];
        for (int index = 0; index < count; index++) {
            indices[index] = index;
        }
        return indices;
    }
}
