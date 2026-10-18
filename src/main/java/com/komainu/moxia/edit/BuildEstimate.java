package com.komainu.moxia.edit;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Locale;

/**
 * Lo que costará colocar un plan: bloques, chunks que toca, caja, memoria y tiempo aproximado. El tiempo supone
 * el ritmo de la cola de KomaEdit (como mucho 32 768 bloques y 10 ms por tick) con un coste medio por bloque
 * prudente; en servidores cargados puede tardar más.
 */
public record BuildEstimate(
        int blocks,
        int pieces,
        int chunks,
        BlockBounds bounds,
        long memoryBytes,
        double seconds
) {
    /** Bloques por tick que la cola coloca de media: unos 10 ms por tick a ~3 µs por bloque. */
    public static final int BLOCKS_PER_TICK = 3_000;
    /** Memoria aproximada por bloque del plan durante la construcción (posición, estado esperado, pieza, cola). */
    public static final int BYTES_PER_BLOCK = 40;

    public static BuildEstimate of(PackedBlockPlan plan) {
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (int index = 0; index < plan.size(); index++) {
            long packed = plan.packedPosition(index);
            int chunkX = PackedBlockPlan.unpackX(packed) >> 4;
            int chunkZ = PackedBlockPlan.unpackZ(packed) >> 4;
            chunks.add(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
        }
        return of(plan.size(), plan.pieceCount(), chunks.size(), plan.size() == 0 ? null : plan.bounds());
    }

    public static BuildEstimate of(int blocks, int pieces, int chunks, BlockBounds bounds) {
        double ticks = Math.ceil((double) blocks / BLOCKS_PER_TICK);
        return new BuildEstimate(blocks, pieces, chunks, bounds, (long) blocks * BYTES_PER_BLOCK, ticks / 20.0);
    }

    public int sizeX() {
        return bounds == null ? 0 : bounds.maximumX() - bounds.minimumX() + 1;
    }

    public int sizeY() {
        return bounds == null ? 0 : bounds.maximumY() - bounds.minimumY() + 1;
    }

    public int sizeZ() {
        return bounds == null ? 0 : bounds.maximumZ() - bounds.minimumZ() + 1;
    }

    /** «120 345 bloques · 42 chunks · caja 60×24×88 · ~3 MB · ~2 s». */
    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append(group(blocks)).append(" bloques");
        if (pieces > 0) {
            builder.append(" (").append(group(pieces)).append(" piezas)");
        }
        builder.append(" · ").append(group(chunks)).append(chunks == 1 ? " chunk" : " chunks");
        if (bounds != null) {
            builder.append(" · caja ").append(sizeX()).append('×').append(sizeY()).append('×').append(sizeZ());
        }
        builder.append(" · ~").append(memory(memoryBytes));
        builder.append(" · ~").append(duration(seconds));
        return builder.toString();
    }

    /** Si hay que pedir confirmación antes de construir: umbral 0 = nunca. */
    public static boolean needsConfirmation(int blocks, int threshold) {
        return threshold > 0 && blocks > threshold;
    }

    static String group(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < digits.length(); index++) {
            if (index > 0 && (digits.length() - index) % 3 == 0) {
                builder.append(' ');
            }
            builder.append(digits.charAt(index));
        }
        return (value < 0 ? "-" : "") + builder;
    }

    static String memory(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }
        return Math.round(bytes / (1024.0 * 1024.0)) + " MB";
    }

    static String duration(double seconds) {
        if (seconds < 1.0) {
            return "1 s";
        }
        if (seconds < 60.0) {
            return Math.round(seconds) + " s";
        }
        long total = Math.round(seconds);
        return String.format(Locale.ROOT, "%d min %02d s", total / 60, total % 60);
    }
}
