package com.komainu.moxia.blueprint;

import com.komainu.moxia.progress.ProgressLine;
/**
 * Límites de la biblioteca de planos, en {@code config/komaedit/blueprints.json}. Quien tenga
 * {@code komaedit.blueprint.unlimited} se salta los de número y tamaño (no el del archivo, que protege el disco).
 *
 * @param maxPerPlayer   planos propios por jugador
 * @param maxShared      planos compartidos en todo el servidor
 * @param maxVolume      bloques de caja (ancho × alto × fondo) de un plano
 * @param maxSide        lado más largo de un plano
 * @param maxFileBytes   tamaño del esquema ya comprimido
 * @param allowSharing   si se puede compartir; además hace falta {@code komaedit.blueprint.share}
 */
public record BlueprintLimits(
        int maxPerPlayer,
        int maxShared,
        long maxVolume,
        int maxSide,
        long maxFileBytes,
        boolean allowSharing
) {
    public static final BlueprintLimits DEFAULT = new BlueprintLimits(64, 512, 1_000_000L, 512, 8L * 1024 * 1024,
            true);

    public BlueprintLimits {
        if (maxPerPlayer < 0 || maxShared < 0 || maxVolume < 1 || maxSide < 1 || maxFileBytes < 1024) {
            throw new IllegalArgumentException("Límites de planos no válidos");
        }
    }

    /** ¿Cabe un plano de este tamaño? */
    public void checkSize(int sizeX, int sizeY, int sizeZ, boolean unlimited) {
        if (unlimited) {
            return;
        }
        int side = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (side > maxSide) {
            throw new IllegalArgumentException("El plano mide " + ProgressLine.group(side) + " bloques de lado; el máximo es " + maxSide);
        }
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > maxVolume) {
            throw new IllegalArgumentException("El plano ocupa " + ProgressLine.group(volume) + " bloques de caja; el máximo es "
                    + maxVolume);
        }
    }

    /**
     * ¿Puede guardar uno más quien ya tiene {@code existing}? Reemplazar uno que ya existe no cuenta como nuevo.
     */
    public void checkCount(int existing, boolean replacing, boolean unlimited) {
        if (!unlimited && !replacing && existing >= maxPerPlayer) {
            throw new IllegalArgumentException("Ya tienes " + existing + " planos (máximo " + maxPerPlayer
                    + "): borra alguno con /komaedit blueprint delete");
        }
    }

    public void checkShared(int existing, boolean replacing, boolean unlimited) {
        if (!allowSharing) {
            throw new IllegalArgumentException("En este servidor no se pueden compartir planos");
        }
        if (!unlimited && !replacing && existing >= maxShared) {
            throw new IllegalArgumentException("El servidor ya tiene " + existing + " planos compartidos (máximo "
                    + maxShared + ")");
        }
    }

    public void checkFile(long bytes) {
        if (bytes > maxFileBytes) {
            throw new IllegalArgumentException("El esquema ocupa " + (bytes / 1024) + " KiB; el máximo es "
                    + (maxFileBytes / 1024) + " KiB");
        }
    }
}
