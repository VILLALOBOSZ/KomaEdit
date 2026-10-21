package com.komainu.moxia.region;

import java.util.function.LongSupplier;

/**
 * Lectura por tramos de una caja del mundo hacia un {@link RegionSnapshot}: por capas (Y, luego Z, luego X, el orden
 * en que están los bloques dentro de una sección), con el reloj mirado cada {@value #CHECK_EVERY} bloques. Es el
 * recorrido de la captura de {@link RegionOperationService}; el mundo llega por {@link BlockSource}, así que se
 * prueba y se mide sin Minecraft.
 */
public final class RegionReader {
    static final int CHECK_EVERY = 1_024;

    /** El mundo, visto desde la lectura. */
    public interface BlockSource {
        int rawId(int x, int y, int z);

        /** Lo que hace falta saber de un estado la primera vez que aparece. */
        RegionSnapshot.StateInfo describe(int rawId, int x, int y, int z);

        /** Luz de bloque en los 4 bits altos y del cielo en los bajos (solo si la operación la pide). */
        default int light(int x, int y, int z) {
            return 0;
        }
    }

    /** Qué posiciones de la caja están dentro de la selección (el resto es margen). */
    @FunctionalInterface
    public interface Selection {
        boolean contains(int x, int y, int z);
    }

    private final BlockSource source;
    private final Selection selection;
    private final RegionSnapshot.Builder builder;
    private final boolean light;
    private final int minimumX;
    private final int minimumY;
    private final int minimumZ;
    private final int sizeX;
    private final long layer;
    private final long total;
    private long index;

    public RegionReader(BlockSource source, Selection selection, int minimumX, int minimumY, int minimumZ,
                        int sizeX, int sizeY, int sizeZ, boolean light) {
        this.source = source;
        this.selection = selection;
        this.builder = RegionSnapshot.builder(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ);
        this.light = light;
        this.minimumX = minimumX;
        this.minimumY = minimumY;
        this.minimumZ = minimumZ;
        this.sizeX = sizeX;
        this.layer = (long) sizeX * sizeZ;
        this.total = layer * sizeY;
    }

    /**
     * Lee hasta {@code maxBlocks} bloques o hasta {@code deadline}.
     *
     * @return {@code true} cuando la caja está entera
     */
    public boolean step(int maxBlocks, long deadline, LongSupplier clock) {
        int processed = 0;
        while (index < total && processed < maxBlocks) {
            if ((processed & (CHECK_EVERY - 1)) == 0 && clock.getAsLong() >= deadline) {
                return false;
            }
            int localY = (int) (index / layer);
            long remainder = index - localY * layer;
            int localZ = (int) (remainder / sizeX);
            int localX = (int) (remainder - (long) localZ * sizeX);
            int x = minimumX + localX;
            int y = minimumY + localY;
            int z = minimumZ + localZ;
            int rawId = source.rawId(x, y, z);
            if (!builder.knows(rawId)) {
                builder.state(source.describe(rawId, x, y, z));
            }
            builder.set(x, y, z, rawId, selection.contains(x, y, z));
            if (light) {
                int levels = source.light(x, y, z);
                builder.light(x, y, z, (levels >> 4) & 15, levels & 15);
            }
            index++;
            processed++;
        }
        return index >= total;
    }

    public boolean done() {
        return index >= total;
    }

    public long read() {
        return index;
    }

    public long total() {
        return total;
    }

    public RegionSnapshot build() {
        if (!done()) {
            throw new IllegalStateException("La lectura no ha terminado");
        }
        return builder.build();
    }
}
