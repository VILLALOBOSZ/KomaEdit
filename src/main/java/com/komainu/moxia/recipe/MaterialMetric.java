package com.komainu.moxia.recipe;

import java.util.Locale;

public enum MaterialMetric {
    AXIS("axis", "Eje"),
    EXPOSURE("exposure", "Exposición"),
    CAVITY("cavity", "Cavidades"),
    EDGES("edges", "Bordes"),
    CURVATURE("curvature", "Curvatura"),
    ORGANIC("organic", "Orgánico"),
    FIGURE("figure", "Patrón figura"),
    MOTIF("motif", "Frente / reverso"),
    RELIEF("relief", "Relieve"),
    ALTERNATE("alternate", "Filas alternas");

    private final String id;
    private final String displayName;

    MaterialMetric(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean usesFigureContext() {
        return this == FIGURE || this == MOTIF || this == RELIEF || this == ALTERNATE;
    }

    public boolean usesGeometryContext() {
        return this == EXPOSURE || this == CAVITY || this == EDGES || this == CURVATURE;
    }

    public static MaterialMetric byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        for (MaterialMetric metric : values()) {
            if (metric.id.equals(normalized)) {
                return metric;
            }
        }
        throw new IllegalArgumentException("Métrica de material desconocida: " + input);
    }
}
