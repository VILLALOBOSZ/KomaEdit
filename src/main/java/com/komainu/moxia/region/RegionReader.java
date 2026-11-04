package com.komainu.moxia.region;

import java.util.function.LongSupplier;

/**
 * Lectura por tramos de una caja del mundo hacia un {@link RegionSnapshot}: columna de chunk a columna de chunk, y dentro
 * de cada una por capas (Y, luego Z, luego X, el orden en que están los bloques dentro de una sección), con el reloj
 * mirado cada {@value #CHECK_EVERY} bloques y al pasar a otro chunk. Es el recorrido de la captura de
 * {@link RegionOperationService}; el mundo llega por {@link BlockSource}, así que se prueba y se mide sin Minecraft.
 *
 * <p>Antes iba por capas de la caja entera: cada capa cruzaba todos sus chunks, así que los que no estaban cargados
 * (una selección más grande que la distancia de visión) se cargaban en el hilo del servidor y se soltaban una vez por
 * capa, y entre dos miradas al reloj (1 024 bloques de una fila de 512 de ancho) podían cargarse 32.</p>
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
    private final int maximumX;
    private final int maximumY;
    private final int maximumZ;
    private final long total;
    private long index;
    /** La columna de chunk que se lee (sus bordes, dentro de la caja) y la posición siguiente. */
    private int columnX;
    private int columnZ;
    private int fromX;
    private int toX;
    private int fromZ;
    private int toZ;
    private int x;
    private int y;
    private int z;

    public RegionReader(BlockSource source, Selection selection, int minimumX, int minimumY, int minimumZ,
                        int sizeX, int sizeY, int sizeZ, boolean light) {
        this.source = source;
        this.selection = selection;
        this.builder = RegionSnapshot.builder(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ);
        this.light = light;
        this.minimumX = minimumX;
        this.minimumY = minimumY;
        this.minimumZ = minimumZ;
        this.maximumX = minimumX + sizeX - 1;
        this.maximumY = minimumY + sizeY - 1;
        this.maximumZ = minimumZ + sizeZ - 1;
        this.total = (long) sizeX * sizeZ * sizeY;
        enterColumn(Math.floorDiv(minimumX, 16), Math.floorDiv(minimumZ, 16));
    }

    private void enterColumn(int chunkX, int chunkZ) {
        columnX = chunkX;
        columnZ = chunkZ;
        fromX = Math.max(minimumX, chunkX * 16);
        toX = Math.min(maximumX, chunkX * 16 + 15);
        fromZ = Math.max(minimumZ, chunkZ * 16);
        toZ = Math.min(maximumZ, chunkZ * 16 + 15);
        x = fromX;
        y = minimumY;
        z = fromZ;
    }

    /** La posición siguiente: en la columna, o la primera de la columna siguiente (las de una fila, y luego en Z). */
    private boolean advance() {
        if (++x <= toX) {
            return false;
        }
        x = fromX;
        if (++z <= toZ) {
            return false;
        }
        z = fromZ;
        if (++y <= maximumY) {
            return false;
        }
        if (columnX < Math.floorDiv(maximumX, 16)) {
            enterColumn(columnX + 1, columnZ);
        } else {
            enterColumn(Math.floorDiv(minimumX, 16), columnZ + 1);
        }
        return true;
    }

    /**
     * Lee hasta {@code maxBlocks} bloques o hasta {@code deadline}.
     *
     * @return {@code true} cuando la caja está entera
     */
    public boolean step(int maxBlocks, long deadline, LongSupplier clock) {
        int processed = 0;
        boolean newColumn = true;
        while (index < total && processed < maxBlocks) {
            // Pasar a otro chunk puede cargarlo (si no lo está): se mira el reloj antes, no 1 024 bloques después.
            if ((newColumn || (processed & (CHECK_EVERY - 1)) == 0) && clock.getAsLong() >= deadline) {
                return false;
            }
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
            newColumn = advance();
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
