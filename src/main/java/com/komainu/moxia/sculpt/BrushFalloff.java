package com.komainu.moxia.sculpt;

import java.util.Locale;

public enum BrushFalloff {
    CONSTANT("constant", "Constante") {
        @Override
        public double apply(double distance) {
            return distance <= 1.0 ? 1.0 : 0.0;
        }
    },
    LINEAR("linear", "Lineal") {
        @Override
        public double apply(double distance) {
            return Math.max(0.0, 1.0 - distance);
        }
    },
    SMOOTH("smooth", "Suave") {
        @Override
        public double apply(double distance) {
            double amount = Math.clamp(1.0 - distance, 0.0, 1.0);
            return amount * amount * (3.0 - 2.0 * amount);
        }
    },
    SPHERICAL("spherical", "Esférica") {
        @Override
        public double apply(double distance) {
            double normalized = Math.clamp(distance, 0.0, 1.0);
            double squared = 1.0 - normalized * normalized;
            return Math.sqrt(Math.max(0.0, squared));
        }
    };

    private final String id;
    private final String displayName;

    BrushFalloff(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public abstract double apply(double distance);

    public static BrushFalloff byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        for (BrushFalloff value : values()) {
            if (value.id.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Falloff desconocido: " + input);
    }
}
