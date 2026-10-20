package com.komainu.moxia.terrain.stamp;

import java.util.Locale;

/**
 * Categorías de los sellos de serie, en el orden de la galería. Cada una trae la paleta de superficie con la que se
 * pinta lo que cambia si el pincel no elige otra (hierba y nieve en las montañas, arena en las dunas…).
 */
public enum StampCategory {
    MOUNTAINS("montanas", "Montañas y picos", "alpino", 0.75),
    RANGES("cordilleras", "Cordilleras", "alpino", 0.75),
    HILLS("colinas", "Colinas suaves", "pradera", 0.25),
    PLATEAUS("mesetas", "Mesetas y mesas", "mesa", 0.45),
    CLIFFS("acantilados", "Acantilados y escarpes", "costa", 0.55),
    CANYONS("canones", "Cañones y gargantas", "mesa", 0.5),
    CRATERS("crateres", "Cráteres y calderas", "rocoso", 0.35),
    VOLCANOES("volcanes", "Volcanes", "ceniza", 0.7),
    DUNES("dunas", "Dunas", "desierto", 0.22),
    ISLANDS("islas", "Islas y atolones", "isla", 0.35),
    TERRACES("terrazas", "Terrazas y bancales", "bancal", 0.4),
    GLACIAL("glaciar", "Glaciares y morrenas", "glaciar", 0.45),
    ROCKS("rocas", "Rocas y peñascos", "rocoso", 0.7),
    DETAIL("detalle", "Detalle", "conservar", 0.12);

    private final String id;
    private final String displayName;
    private final String palette;
    private final double heightRatio;

    StampCategory(String id, String displayName, String palette, double heightRatio) {
        this.id = id;
        this.displayName = displayName;
        this.palette = palette;
        this.heightRatio = heightRatio;
    }

    /** Altura típica de sus sellos dividida por el radio, para los que no traen la suya. */
    double heightRatio() {
        return heightRatio;
    }

    /** Identificador corto y sin tildes: el de las órdenes, las claves de idioma y los filtros del Studio. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Preajuste de {@code SurfacePalette} que se usa con la paleta en automático. */
    public String palette() {
        return palette;
    }

    public static StampCategory byId(String input) {
        String wanted = input.strip().toLowerCase(Locale.ROOT).replace("ñ", "n").replace("á", "a")
                .replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
        for (StampCategory category : values()) {
            if (category.id.equals(wanted) || category.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Categoría de sellos desconocida: " + input);
    }
}
