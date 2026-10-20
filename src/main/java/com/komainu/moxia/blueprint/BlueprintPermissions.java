package com.komainu.moxia.blueprint;

import com.komainu.moxia.permission.KomaPermissions;

/** Nodos de permiso de la biblioteca de planos; sus paquetes están en {@code PermissionNodes}. */
public final class BlueprintPermissions {
    /** Guardar planos propios, cambiarles las etiquetas y borrarlos. */
    public static final String SAVE = "komaedit.blueprint.save";
    /** Ver la biblioteca (lista, búsqueda, Studio) y cargar planos al portapapeles. */
    public static final String LOAD = "komaedit.blueprint.load";
    /** Compartir planos propios con todo el servidor. */
    public static final String SHARE = "komaedit.blueprint.share";
    /** Colocar planos ({@code /komaedit blueprint place}). */
    public static final String PLACE = KomaPermissions.BUILD + ".blueprint";
    /** El pincel «estampar». */
    public static final String STAMP = KomaPermissions.BUILD + ".brush.stamp";
    /** Borrar o reemplazar los planos compartidos de otros. */
    public static final String MANAGE = "komaedit.admin.blueprints";
    /** Sin los límites de número y tamaño de {@code blueprints.json}. */
    public static final String UNLIMITED = "komaedit.limit.blueprints";

    private BlueprintPermissions() {
    }
}
