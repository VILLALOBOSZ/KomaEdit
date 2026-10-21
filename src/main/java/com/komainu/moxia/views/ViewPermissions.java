package com.komainu.moxia.views;

/** Nodos de permiso de las vistas guardadas; sus paquetes están en {@code PermissionNodes}. */
public final class ViewPermissions {
    /** Saltar a una vista (propia o compartida) en la misma dimensión. */
    public static final String TELEPORT = "komaedit.view.teleport";
    /** Saltar a una vista de otra dimensión. */
    public static final String DIMENSION = "komaedit.view.dimension";
    /** Saltar también en supervivencia o aventura, si el servidor solo deja en creativo. */
    public static final String ANY_MODE = "komaedit.view.anymode";
    /** Ver las vistas que comparte el equipo. */
    public static final String SEE_SHARED = "komaedit.view.shared";
    /** Compartir vistas propias con el equipo. */
    public static final String SHARE = "komaedit.view.share";
    /** Quitar o reemplazar las vistas compartidas de otros, sin los límites de número. */
    public static final String MANAGE = "komaedit.admin.views";

    private ViewPermissions() {
    }
}
