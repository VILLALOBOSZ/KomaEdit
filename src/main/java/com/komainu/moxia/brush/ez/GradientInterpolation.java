package com.komainu.moxia.brush.ez;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cómo mezcla el ruido los colores vecinos de un degradado. {@code t} es la posición en el degradado (0 al inicio,
 * 1 al final) y {@code offset} el empujón del ruido, ya multiplicado por el sangrado: de -sangrado a +sangrado.
 */
public enum GradientInterpolation {
    /** Bandas limpias: el ruido no se usa. */
    NONE,
    /** El mismo empujón en todo el degradado; cerca de los extremos se sale de los dos puntos. */
    LINEAR,
    /** Fuerte en el centro y nulo en los extremos (4·t·(1−t)): no se sale del tramo. */
    TAPERED,
    /** Bézier cuadrática con el punto de control movido por el ruido: más suave; con sangrado > 1 se rompe. */
    BEZIER,
    /** Como TAPERED pero con un seno (sin(π·t)): reparte el sangrado más ancho por el centro. */
    SIN;

    public double apply(double t, double offset) {
        double clamped = Math.clamp(t, 0.0, 1.0);
        return switch (this) {
            case NONE -> t;
            case LINEAR -> t + offset;
            case TAPERED -> t + offset * 4.0 * clamped * (1.0 - clamped);
            case BEZIER -> {
                if (t < 0.0 || t > 1.0) {
                    yield t;
                }
                double control = 0.5 + 0.5 * offset;
                yield 2.0 * t * (1.0 - t) * control + t * t;
            }
            case SIN -> t + offset * Math.sin(Math.PI * clamped);
        };
    }

    public static GradientInterpolation byName(String input) {
        String name = BrushOptions.normalize(input);
        for (GradientInterpolation value : values()) {
            if (BrushOptions.normalize(value.name()).equals(name)) {
                return value;
            }
        }
        return switch (name) {
            case "ninguna", "no" -> NONE;
            case "lineal", "lin" -> LINEAR;
            case "afilada", "tap" -> TAPERED;
            case "bez" -> BEZIER;
            case "seno", "sine" -> SIN;
            default -> throw new IllegalArgumentException("Interpolación desconocida: " + input + " ("
                    + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")) + ")");
        };
    }

    public static boolean isName(String input) {
        try {
            byName(input);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Posición de la paleta para un valor ya interpolado. */
    public static int paletteIndex(double t, int size) {
        if (size <= 1) {
            return 0;
        }
        double clamped = Math.clamp(t, 0.0, 1.0);
        return Math.min(size - 1, (int) Math.floor(clamped * size));
    }
}
