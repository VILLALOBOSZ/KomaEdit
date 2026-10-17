package com.komainu.moxia.shape;

import java.util.Locale;
import java.util.Map;

public record ShapeOptions(Map<String, Double> values) {
    public static final ShapeOptions EMPTY = new ShapeOptions(Map.of());
    /** Máximo de parámetros avanzados por figura; lo comparten recetas y red. */
    public static final int MAX_ENTRIES = 32;

    public ShapeOptions {
        values = Map.copyOf(values);
        values.forEach((name, value) -> {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("El parámetro " + name + " debe ser un número finito");
            }
        });
    }

    public boolean has(String name) {
        return values.containsKey(normalize(name));
    }

    public double get(String name, double fallback) {
        return values.getOrDefault(normalize(name), fallback);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
