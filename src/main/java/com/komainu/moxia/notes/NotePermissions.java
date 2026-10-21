package com.komainu.moxia.notes;

/** Nodos de permiso de las notas en el mundo; sus paquetes están en {@code PermissionNodes}. */
public final class NotePermissions {
    /** Poner notas privadas (solo las ve su autor), verlas, resolverlas y borrarlas. */
    public static final String PRIVATE = "komaedit.notes.private";
    /** Ver las notas del equipo y marcarlas como resueltas. */
    public static final String TEAM = "komaedit.notes.team";
    /** Poner notas para el equipo. */
    public static final String SHARE = "komaedit.notes.share";
    /** Borrar las notas de otros y saltarse los límites de número. */
    public static final String MANAGE = "komaedit.admin.notes";

    private NotePermissions() {
    }
}
