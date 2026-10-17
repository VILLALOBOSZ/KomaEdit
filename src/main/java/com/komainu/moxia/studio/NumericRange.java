package com.komainu.moxia.studio;

import java.util.OptionalInt;

/**
 * Rango de un control numérico del Studio: límites, paso normal y paso grande (Mayús).
 * También traduce lo escrito en el campo y el arrastre del ratón a un valor válido.
 */
public record NumericRange(int min, int max, int step, int bigStep) {
    /** Píxeles de arrastre horizontal por paso. */
    public static final int PIXELS_PER_STEP = 6;

    public NumericRange {
        if (min > max || step < 1 || bigStep < step) {
            throw new IllegalArgumentException("invalid range " + min + ".." + max + " step " + step);
        }
    }

    public static NumericRange of(int min, int max, int step) {
        return new NumericRange(min, max, step, step * 5);
    }

    public int clamp(int value) {
        return Math.clamp(value, min, max);
    }

    /** Un paso hacia {@code direction} (signo), grande con Mayús. */
    public int stepped(int value, int direction, boolean big) {
        int amount = big ? bigStep : step;
        return clamp(value + Integer.signum(direction) * amount);
    }

    /** Valor tras arrastrar {@code pixels} desde {@code start}; redondea al paso del control. */
    public int dragged(int start, double pixels, boolean big) {
        int steps = (int) (pixels / PIXELS_PER_STEP);
        return clamp(start + steps * (big ? bigStep : step));
    }

    /** Lo escrito en el campo: entero con signo opcional, ajustado al rango; vacío o inválido no aplica. */
    public OptionalInt parse(String input) {
        String trimmed = input.trim().replace('−', '-');
        if (trimmed.isEmpty() || trimmed.equals("-") || trimmed.length() > 9) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(clamp(Integer.parseInt(trimmed)));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    /** Posición del valor en el rango, de 0 a 1, para la barra del control. */
    public double fraction(int value) {
        return max == min ? 1.0 : (clamp(value) - min) / (double) (max - min);
    }
}
