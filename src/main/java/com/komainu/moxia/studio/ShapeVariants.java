package com.komainu.moxia.studio;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeParameters;

import java.util.SplittableRandom;

/**
 * Presets de proporciones y variantes con semilla para explorar una figura sin tocar su generador:
 * solo proponen radio, espaciado, grosor, giro y altura dentro de los rangos del Studio,
 * partiendo de los valores de serie de la figura.
 */
public final class ShapeVariants {
    public static final NumericRange RADIUS = NumericRange.of(1, 128, 1);
    public static final NumericRange SPACING = NumericRange.of(2, 8, 1);
    public static final NumericRange THICKNESS = NumericRange.of(1, 4, 1);
    public static final NumericRange ROLL = new NumericRange(-180, 180, 5, 45);
    public static final NumericRange HEIGHT = NumericRange.of(3, 256, 1);
    /** Las semillas que se muestran tienen como mucho cinco cifras para poder dictarlas. */
    public static final int MAX_SEED = 99_999;

    private ShapeVariants() {
    }

    public record Proportions(int radius, int spacing, int thickness, int rollDegrees, int height) {
    }

    public enum Preset {
        COMPACT("gui.komaedit.preset.compact"),
        DEFAULT("gui.komaedit.preset.default"),
        WIDE("gui.komaedit.preset.wide"),
        BOLD("gui.komaedit.preset.bold");

        private final String translationKey;

        Preset(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public static Proportions defaults(PathShape shape) {
        ShapeParameters defaults = shape.defaults();
        return new Proportions(defaults.radius(), defaults.spacing(), defaults.thickness(),
                defaults.rollDegrees(), shape.defaultHeight());
    }

    public static Proportions preset(PathShape shape, Preset preset) {
        Proportions base = defaults(shape);
        return switch (preset) {
            case DEFAULT -> base;
            case COMPACT -> new Proportions(
                    RADIUS.clamp((int) Math.round(base.radius() * 0.6)),
                    SPACING.clamp(base.spacing() - 1),
                    base.thickness(),
                    base.rollDegrees(),
                    HEIGHT.clamp((int) Math.round(base.height() * 0.7)));
            case WIDE -> new Proportions(
                    RADIUS.clamp((int) Math.round(base.radius() * 1.5)),
                    SPACING.clamp(base.spacing() + 1),
                    base.thickness(),
                    base.rollDegrees(),
                    HEIGHT.clamp((int) Math.round(base.height() * 1.4)));
            case BOLD -> new Proportions(
                    RADIUS.clamp((int) Math.round(base.radius() * 1.2)),
                    base.spacing(),
                    THICKNESS.clamp(base.thickness() + 1),
                    base.rollDegrees(),
                    base.height());
        };
    }

    /**
     * Variante determinista: la misma figura y semilla dan siempre lo mismo, en cualquier equipo.
     * Se mueve alrededor de los valores de serie (radio de ×0,6 a ×1,6) para no salir de lo razonable.
     */
    public static Proportions variant(PathShape shape, int seed) {
        Proportions base = defaults(shape);
        SplittableRandom random = new SplittableRandom(seed * 0x9E3779B97F4A7C15L ^ shape.id().hashCode());
        double scale = 0.6 + random.nextDouble();
        int radius = RADIUS.clamp((int) Math.round(base.radius() * scale));
        int spacing = SPACING.clamp(base.spacing() + random.nextInt(-2, 3));
        int thickness = THICKNESS.clamp(base.thickness() + random.nextInt(-1, 2));
        int roll = ROLL.clamp(random.nextInt(-9, 10) * ROLL.step());
        int height = HEIGHT.clamp((int) Math.round(base.height() * (0.7 + random.nextDouble() * 0.8)));
        return new Proportions(radius, spacing, thickness, roll, height);
    }

    public static int randomSeed(SplittableRandom random) {
        return random.nextInt(1, MAX_SEED + 1);
    }
}
