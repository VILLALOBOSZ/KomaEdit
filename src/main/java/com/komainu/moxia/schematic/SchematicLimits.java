package com.komainu.moxia.schematic;

import com.komainu.moxia.progress.ProgressLine;

/**
 * Topes al leer un esquema que llega de fuera ({@code .litematic}, {@code .nbt} o {@code .schem}): se comprueban antes
 * de reservar memoria, así que un archivo hecho a mala idea se rechaza con un mensaje y no tumba el servidor.
 *
 * @param maxNbtBytes      bytes del NBT ya descomprimido (corta las bombas gzip)
 * @param maxDepth         anidamiento del NBT
 * @param maxVolume        bloques de caja del esquema entero (ancho × alto × fondo)
 * @param maxSide          lado más largo
 * @param maxPalette       estados distintos en una paleta
 * @param maxRegions       regiones de un {@code .litematic}
 * @param maxBlockEntities bloques con datos (cofres, carteles…)
 * @param maxEntities      entidades
 */
public record SchematicLimits(
        long maxNbtBytes,
        int maxDepth,
        long maxVolume,
        int maxSide,
        int maxPalette,
        int maxRegions,
        int maxBlockEntities,
        int maxEntities
) {
    /** Lado más largo que cabe en un esquema de Sponge (corto sin signo). */
    public static final int SPONGE_SIDE = 65_535;
    /** Lo más que admite la rejilla de índices (un array de Java). */
    public static final long MAX_VOLUME = 1L << 30;
    /**
     * De serie: 64 MiB de NBT, 16 millones de bloques de caja (la rejilla de índices ocupa 64 MiB) y lados de hasta
     * 4096. Los planos y los paquetes los recortan con sus propios límites.
     */
    public static final SchematicLimits DEFAULT = new SchematicLimits(64L * 1024 * 1024, 128, 16L * 1024 * 1024,
            4096, 65_536, 64, 65_536, 16_384);

    public SchematicLimits {
        if (maxNbtBytes < 1024 || maxDepth < 8 || maxVolume < 1 || maxVolume > MAX_VOLUME || maxSide < 1
                || maxSide > SPONGE_SIDE
                || maxPalette < 2 || maxRegions < 1 || maxBlockEntities < 0 || maxEntities < 0) {
            throw new IllegalArgumentException("Límites de esquema no válidos");
        }
    }

    /** Los mismos con otro tope de bloques de caja (el menor de los dos). */
    public SchematicLimits withVolume(long volume) {
        return new SchematicLimits(maxNbtBytes, maxDepth, Math.max(1, Math.min(maxVolume, volume)), maxSide,
                maxPalette, maxRegions, maxBlockEntities, maxEntities);
    }

    /** Los mismos con otro tope de NBT descomprimido (el menor de los dos). */
    public SchematicLimits withNbtBytes(long bytes) {
        return new SchematicLimits(Math.max(1024, Math.min(maxNbtBytes, bytes)), maxDepth, maxVolume, maxSide,
                maxPalette, maxRegions, maxBlockEntities, maxEntities);
    }

    /** Comprueba el tamaño de una caja antes de reservar su rejilla. */
    public void checkBox(long sizeX, long sizeY, long sizeZ) {
        long side = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("El esquema no tiene tamaño");
        }
        if (side > maxSide) {
            throw new IllegalArgumentException("El esquema mide " + ProgressLine.group(side)
                    + " bloques de lado; el máximo es " + ProgressLine.group(maxSide));
        }
        long volume = sizeX * sizeY * sizeZ;
        if (volume > maxVolume) {
            throw new IllegalArgumentException("El esquema ocupa " + ProgressLine.group(volume)
                    + " bloques de caja; el máximo es " + ProgressLine.group(maxVolume));
        }
    }
}
