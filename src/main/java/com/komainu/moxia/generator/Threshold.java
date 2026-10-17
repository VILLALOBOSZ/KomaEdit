package com.komainu.moxia.generator;

import com.komainu.moxia.shape.tube.TubeFormula;

/**
 * Un número o una fórmula de KomaEdit ({@link TubeFormula}) que depende de la posición. Las variables son
 * {@code x}, {@code y} y {@code z} normalizadas de -1 a 1 dentro de la selección y {@code t}, la altura relativa
 * (0 abajo, 1 arriba). Ejemplo: {@code 0.3 + 0.2 * t} o {@code r < 0.5 ? 0.2 : 0.4} (con {@code r} la distancia al
 * eje en planta).
 *
 * <p>Se usa el compilador de KomaEdit y no el de WorldEdit por lo mismo que en {@code expr}: es cerrado (sin bucles
 * ni búferes globales) y unas diez veces más rápido, y aquí se evalúa una vez por bloque.</p>
 */
public final class Threshold {
    private final double constant;
    private final TubeFormula formula;

    private Threshold(double constant, TubeFormula formula) {
        this.constant = constant;
        this.formula = formula;
    }

    public static Threshold constant(double value) {
        return new Threshold(value, null);
    }

    public static Threshold parse(String text, String name) {
        String trimmed = text.trim();
        try {
            return constant(Double.parseDouble(trimmed));
        } catch (NumberFormatException ignored) {
            try {
                return new Threshold(Double.NaN, TubeFormula.compile(trimmed));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(name + ": " + exception.getMessage());
            }
        }
    }

    public boolean isConstant() {
        return formula == null;
    }

    public double constantValue() {
        return constant;
    }

    /** Evaluador para un hilo. */
    public Evaluator evaluator(long seed) {
        if (formula == null) {
            return (x, y, z, t) -> constant;
        }
        TubeFormula.Runner runner = formula.runner(seed, 1);
        return runner::evaluate;
    }

    @FunctionalInterface
    public interface Evaluator {
        double evaluate(double x, double y, double z, double t);
    }
}
