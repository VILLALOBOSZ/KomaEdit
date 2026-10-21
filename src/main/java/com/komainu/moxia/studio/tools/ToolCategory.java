package com.komainu.moxia.studio.tools;

/**
 * Los grupos de la cuadrícula de la etapa Herramientas, en el orden en que salen. Cada tarjeta se registra en uno
 * con {@link ToolCatalog}; el título del grupo es {@code gui.komaedit.tools.category.<id>}.
 */
public enum ToolCategory {
    /** Lo que crece o se pone en el paisaje: árboles y rocas. */
    NATURE("nature"),
    /** Lo que se construye sobre una obra o junto a ella: tejados, lonas, texto. */
    BUILDINGS("buildings"),
    /** Dar forma al terreno y pintarlo. */
    TERRAIN("terrain"),
    /** Retocar, medir y ayudas de obra. */
    UTILITIES("utilities"),
    /** Lo que se enciende y se queda: simetría y construir a mano. */
    MODES("modes");

    private final String id;

    ToolCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.komaedit.tools.category." + id;
    }
}
