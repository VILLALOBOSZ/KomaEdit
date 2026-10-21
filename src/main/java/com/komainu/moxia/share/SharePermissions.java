package com.komainu.moxia.share;

/** Nodos de permiso de los paquetes compartidos; sus paquetes de permisos están en {@code PermissionNodes}. */
public final class SharePermissions {
    /** Exportar lo propio a un código ({@code /komaedit share export}). */
    public static final String EXPORT = "komaedit.share.export";
    /**
     * Importar un código o un archivo. Es un permiso propio: no lo da {@code komaedit.use}. Además hace falta el de
     * guardar cada tipo (el de las paletas, los planos…).
     */
    public static final String IMPORT = "komaedit.share.import";
    /** Exportar a un archivo de {@code config/komaedit/exports} (escribe en el disco del servidor). */
    public static final String FILE = "komaedit.share.file";
    /** Sin el número de importaciones y exportaciones por hora de {@code share.json}. */
    public static final String UNLIMITED = "komaedit.limit.share";

    private SharePermissions() {
    }
}
