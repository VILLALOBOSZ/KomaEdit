package com.komainu.moxia.studio.tools;

/** Sobre qué trabaja una herramienta: la pantalla lo cuenta y avisa si falta antes de construir. */
public enum ToolTarget {
    /** El bloque que se mira (el centro de la pantalla). */
    LOOK("look"),
    /** Un punto de la trayectoria en cada uno o, sin puntos, el bloque que se mira. */
    PATH_OR_LOOK("path_or_look"),
    /** La trayectoria o, sin ella, la selección de WorldEdit (sus puntos o de pos1 a pos2). */
    PATH_OR_SELECTION("path_or_selection"),
    /** La selección de WorldEdit. */
    SELECTION("selection"),
    /** Queda en un pincel en la mano: se usa con clic derecho en el mundo. */
    HAND("hand"),
    /** Un modo que se activa y se queda (simetría, capacidades, la regla). */
    MODE("mode"),
    /** Donde está el jugador y hacia dónde mira (guardar una vista). */
    SELF("self");

    private final String id;

    ToolTarget(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.komaedit.tools.target." + id;
    }
}
