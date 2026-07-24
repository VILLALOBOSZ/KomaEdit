package com.komainu.moxia.recipe;

import java.util.Locale;

public enum CurveEasing {
    LINEAR("linear", "Lineal"),
    SMOOTH("smooth", "Suave"),
    EASE_IN("ease_in", "Entrada suave"),
    EASE_OUT("ease_out", "Salida suave"),
    EASE_IN_OUT("ease_in_out", "Entrada y salida");

    private final String id;
    private final String displayName;

    CurveEasing(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double apply(double value) {
        double clamped = Math.clamp(value, 0.0, 1.0);
        return switch (this) {
            case LINEAR -> clamped;
            case SMOOTH -> clamped * clamped * (3.0 - 2.0 * clamped);
            case EASE_IN -> clamped * clamped;
            case EASE_OUT -> 1.0 - (1.0 - clamped) * (1.0 - clamped);
            case EASE_IN_OUT -> clamped < 0.5
                    ? 2.0 * clamped * clamped
                    : 1.0 - Math.pow(-2.0 * clamped + 2.0, 2.0) * 0.5;
        };
    }

    public static CurveEasing byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        for (CurveEasing easing : values()) {
            if (easing.id.equals(normalized)) {
                return easing;
            }
        }
        throw new IllegalArgumentException("Curva desconocida: " + input);
    }
}
