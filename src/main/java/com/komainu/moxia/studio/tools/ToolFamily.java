package com.komainu.moxia.studio.tools;

/** Familias de herramientas de la etapa Herramientas, en el orden de sus pastillas. */
public enum ToolFamily {
    NATURE("nature"),
    ROOFS("roofs"),
    TERRAIN("terrain"),
    UTILITIES("utilities"),
    /** Decorar con displays (bloques, objetos y textos sueltos, girados y escalados). */
    DECORATE("decorate"),
    SYMMETRY("symmetry"),
    CAPABILITIES("capabilities");

    private final String id;

    ToolFamily(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.komaedit.tools.family." + id;
    }
}
