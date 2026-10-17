package com.komainu.moxia.shape;

import java.util.Locale;
import java.util.Map;

/**
 * Parámetros avanzados de una figura: números con nombre y, solo para la figura Expresión, el texto de la
 * fórmula del usuario (vacío si usa una fórmula de serie o si la figura no lleva fórmula).
 */
public record ShapeOptions(Map<String, Double> values, String formula) {
    public static final ShapeOptions EMPTY = new ShapeOptions(Map.of());
    /** Máximo de parámetros avanzados por figura; lo comparten recetas y red. */
    public static final int MAX_ENTRIES = 32;
    /** Longitud máxima del texto de fórmula; la misma que admite el compilador de fórmulas. */
    public static final int MAX_FORMULA_LENGTH = 256;

    public ShapeOptions(Map<String, Double> values) {
        this(values, "");
    }

    public ShapeOptions {
        values = Map.copyOf(values);
        values.forEach((name, value) -> {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("El parámetro " + name + " debe ser un número finito");
            }
        });
        formula = formula == null ? "" : formula.trim();
        if (formula.length() > MAX_FORMULA_LENGTH) {
            throw new IllegalArgumentException("La fórmula admite un máximo de " + MAX_FORMULA_LENGTH + " caracteres");
        }
    }

    public boolean has(String name) {
        return values.containsKey(normalize(name));
    }

    public double get(String name, double fallback) {
        return values.getOrDefault(normalize(name), fallback);
    }

    public boolean hasFormula() {
        return !formula.isEmpty();
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
