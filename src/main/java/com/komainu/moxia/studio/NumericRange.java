package com.komainu.moxia.studio;

import java.util.OptionalInt;

/**
 * Rango de un control numérico del Studio: límites, paso normal y paso grande (Mayús).
 * También traduce lo escrito en el campo y el arrastre del ratón a un valor válido.
 */
public record NumericRange(int min, int max, int step, int bigStep) {
    /** Píxeles de arrastre horizontal por paso, como mucho (los rangos largos van más deprisa). */
    public static final int PIXELS_PER_STEP = 6;
    /** Con Mayús, el arrastre fino: un paso cada tantos píxeles, sea cual sea el rango. */
    public static final int FINE_PIXELS_PER_STEP = 12;
    /** Arrastrar esta distancia recorre el rango entero (si así va más deprisa que un paso cada 6 píxeles). */
    public static final int DRAG_SPAN = 240;

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

    /**
     * Valor tras arrastrar {@code pixels} desde {@code start}, en pasos enteros del control. Normal: un paso cada
     * {@value #PIXELS_PER_STEP} píxeles o, en rangos largos, el rango entero en {@value #DRAG_SPAN}. Fino (Mayús):
     * un paso cada {@value #FINE_PIXELS_PER_STEP} píxeles.
     */
    public int dragged(int start, double pixels, boolean fine) {
        double stepsPerPixel = fine ? 1.0 / FINE_PIXELS_PER_STEP
                : Math.max(1.0 / PIXELS_PER_STEP, steps() / (double) DRAG_SPAN);
        long steps = (long) (pixels * stepsPerPixel);
        return clamp((int) Math.clamp(start + steps * step, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    /** Cuántos pasos normales caben en el rango. */
    public long steps() {
        return ((long) max - min) / step;
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
