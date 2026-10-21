package com.komainu.moxia.share;

/**
 * Límites de los paquetes compartidos, en {@code config/komaedit/share.json}. Quien tenga
 * {@code komaedit.limit.share} se salta los de número por hora; los de tamaño protegen la memoria y el disco del
 * servidor y valen para todos.
 *
 * @param maxCodeChars      caracteres de un código de texto (los que se pegan en el Studio o en un {@code .txt}); en
 *                          el chat Minecraft no deja escribir más de 256
 * @param maxPackBytes      bytes del JSON del paquete ya descomprimido
 * @param maxFileBytes      bytes de un archivo {@code .kpack}
 * @param maxItems          elementos de un paquete
 * @param maxSchematicBytes bytes del NBT de un esquema ya descomprimido (el de un plano)
 * @param importsPerHour    importaciones por jugador y hora
 * @param exportsPerHour    exportaciones por jugador y hora
 * @param allowFiles        si se puede exportar a {@code config/komaedit/exports}; además hace falta
 *                          {@code komaedit.share.file}
 */
public record ShareLimits(
        int maxCodeChars,
        long maxPackBytes,
        long maxFileBytes,
        int maxItems,
        long maxSchematicBytes,
        int importsPerHour,
        int exportsPerHour,
        boolean allowFiles
) {
    public static final ShareLimits DEFAULT = new ShareLimits(32_000, 1024L * 1024, 16L * 1024 * 1024, 64,
            64L * 1024 * 1024, 30, 60, true);

    public ShareLimits {
        if (maxCodeChars < 256 || maxPackBytes < 4096 || maxFileBytes < 4096 || maxItems < 1
                || maxSchematicBytes < 4096 || importsPerHour < 0 || exportsPerHour < 0) {
            throw new IllegalArgumentException("Límites de paquetes no válidos");
        }
    }
}
