package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Qué cambios de una vista previa se mandan al cliente que la dibuja él mismo, y qué caras de cada uno se ven.
 *
 * <ol>
 *     <li>Una posición repetida cuenta una vez, con su última entrada (la que gana al aplicar).</li>
 *     <li>Solo se manda la superficie: un cambio rodeado de cambios no se ve desde ningún lado. Cada uno lleva
 *     sus caras al descubierto ({@link #faces}), calculadas con todos los cambios, así el cliente no dibuja caras
 *     hacia dentro aunque no tenga el interior.</li>
 *     <li>Si la superficie no cabe en el límite, una muestra repartida por el espacio ({@link DisplaySampler}).</li>
 * </ol>
 *
 * Las caras van en el orden oeste, este, abajo, arriba, norte, sur (−X, +X, −Y, +Y, −Z, +Z): el bit {@code d} está
 * puesto si el vecino en esa dirección no cambia. Es determinista.
 */
public final class PreviewSurface {
    public static final int[] OFFSET_X = {-1, 1, 0, 0, 0, 0};
    public static final int[] OFFSET_Y = {0, 0, -1, 1, 0, 0};
    public static final int[] OFFSET_Z = {0, 0, 0, 0, -1, 1};
    public static final int ALL_FACES = 0b111111;
    private static final int MIN_Y = -2048;
    private static final int MAX_Y = 2047;

    private PreviewSurface() {
    }

    /**
     * Lo elegido: índices crecientes del plan, sus caras al descubierto y cómo se eligieron.
     *
     * @param unique  posiciones distintas del plan
     * @param surface cuántas de ellas tienen alguna cara al descubierto
     */
    public record Selection(int[] indices, byte[] faces, DisplaySampler.Mode mode, int total, int unique,
                            int surface) {
        public int shown() {
            return indices.length;
        }
    }

    /**
     * @param positions posiciones empaquetadas ({@link PackedBlockPlan#pack}); se leen las {@code count} primeras
     * @param limit     cambios como mucho
     */
    public static Selection select(long[] positions, int count, int limit) {
        if (count < 0 || count > positions.length) {
            throw new IllegalArgumentException("Recuento fuera del array: " + count);
        }
        int budget = Math.max(0, limit);
        LongOpenHashSet all = new LongOpenHashSet(count);
        boolean[] last = new boolean[count];
        int unique = 0;
        for (int index = count - 1; index >= 0; index--) {
            if (all.add(positions[index])) {
                last[index] = true;
                unique++;
            }
        }
        IntArrayList surface = new IntArrayList();
        IntArrayList masks = new IntArrayList();
        for (int index = 0; index < count; index++) {
            if (!last[index]) {
                continue;
            }
            int mask = faces(all, positions[index]);
            if (mask != 0) {
                surface.add(index);
                masks.add(mask);
            }
        }
        if (surface.size() <= budget) {
            DisplaySampler.Mode mode = surface.size() == unique ? DisplaySampler.Mode.ALL
                    : DisplaySampler.Mode.SURFACE;
            return new Selection(surface.toIntArray(), bytes(masks), mode, count, unique, surface.size());
        }
        int[] sampled = DisplaySampler.sample(positions, surface, budget);
        byte[] sampledMasks = new byte[sampled.length];
        int cursor = 0;
        for (int slot = 0; slot < surface.size() && cursor < sampled.length; slot++) {
            if (surface.getInt(slot) == sampled[cursor]) {
                sampledMasks[cursor++] = (byte) masks.getInt(slot);
            }
        }
        return new Selection(sampled, sampledMasks, DisplaySampler.Mode.SAMPLE, count, unique, surface.size());
    }

    /** Caras de {@code packed} cuyo vecino no está en {@code all}. */
    public static int faces(LongOpenHashSet all, long packed) {
        int x = PackedBlockPlan.unpackX(packed);
        int y = PackedBlockPlan.unpackY(packed);
        int z = PackedBlockPlan.unpackZ(packed);
        int mask = 0;
        for (int direction = 0; direction < 6; direction++) {
            int neighbourY = y + OFFSET_Y[direction];
            if (neighbourY < MIN_Y || neighbourY > MAX_Y
                    || !all.contains(PackedBlockPlan.pack(x + OFFSET_X[direction], neighbourY,
                    z + OFFSET_Z[direction]))) {
                mask |= 1 << direction;
            }
        }
        return mask;
    }

    private static byte[] bytes(IntArrayList values) {
        byte[] result = new byte[values.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) values.getInt(index);
        }
        return result;
    }
}
