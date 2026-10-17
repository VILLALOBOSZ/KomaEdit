package com.komainu.moxia.recipe;

import com.komainu.moxia.gradient.GradientAxis;
import com.komainu.moxia.gradient.GradientPalette;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.tube.TubeFormula;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public record ProceduralRecipe(
        String name,
        PathShape shape,
        String pattern,
        String accentPattern,
        int radius,
        int spacing,
        int thickness,
        int rollDegrees,
        int height,
        String paletteId,
        GradientAxis gradientAxis,
        int dither,
        boolean reverse,
        Map<String, Double> options,
        RecipeExtensions extensions,
        String formula
) {
    /**
     * 4: fórmula de la figura Expresión ({@code formula}, texto). Las versiones 1–3 se leen con la fórmula vacía,
     * es decir, con la fórmula de serie que indique la opción {@code preset}.
     */
    public static final int FORMAT_VERSION = 4;
    public static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");

    public ProceduralRecipe {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("El nombre debe usar 1–32 letras, números, _ o -");
        }
        if (shape == null) {
            throw new IllegalArgumentException("La figura de la receta es obligatoria");
        }
        if (pattern == null || pattern.isBlank() || pattern.length() > 256) {
            throw new IllegalArgumentException("El material base no es válido");
        }
        if (accentPattern == null || accentPattern.isBlank() || accentPattern.length() > 256) {
            throw new IllegalArgumentException("El material secundario no es válido");
        }
        if (radius < 1 || radius > 128) {
            throw new IllegalArgumentException("La escala debe estar entre 1 y 128");
        }
        if (spacing < 2 || spacing > 8) {
            throw new IllegalArgumentException("El espaciado debe estar entre 2 y 8");
        }
        if (thickness < 1 || thickness > 4) {
            throw new IllegalArgumentException("El grosor debe estar entre 1 y 4");
        }
        if (rollDegrees < -180 || rollDegrees > 180) {
            throw new IllegalArgumentException("El giro debe estar entre -180 y 180 grados");
        }
        if (height < 3 || height > 256) {
            throw new IllegalArgumentException("La altura debe estar entre 3 y 256");
        }
        paletteId = paletteId == null ? "" : paletteId.trim();
        gradientAxis = gradientAxis == null ? GradientAxis.Y : gradientAxis;
        if (!paletteId.isEmpty()) {
            GradientPalette.byId(paletteId);
        }
        if (dither < 0 || dither > 100) {
            throw new IllegalArgumentException("La mezcla debe estar entre 0 y 100");
        }
        options = options == null ? Map.of() : Map.copyOf(options);
        if (options.size() > ShapeOptions.MAX_ENTRIES) {
            throw new IllegalArgumentException("La receta admite un máximo de " + ShapeOptions.MAX_ENTRIES
                    + " parámetros avanzados");
        }
        new ShapeOptions(options);
        extensions = extensions == null ? RecipeExtensions.DEFAULT : extensions;
        // Solo la figura Expresión lleva texto; en el resto se descarta para no arrastrar estado invisible.
        formula = formula == null || shape != PathShape.EXPRESSION ? "" : formula.trim();
        if (!formula.isEmpty()) {
            TubeFormula.compile(formula);
        }
    }

    public ProceduralRecipe(
            String name,
            PathShape shape,
            String pattern,
            String accentPattern,
            int radius,
            int spacing,
            int thickness,
            int rollDegrees,
            int height,
            String paletteId,
            GradientAxis gradientAxis,
            int dither,
            boolean reverse,
            Map<String, Double> options,
            RecipeExtensions extensions
    ) {
        this(name, shape, pattern, accentPattern, radius, spacing, thickness, rollDegrees, height, paletteId,
                gradientAxis, dither, reverse, options, extensions, "");
    }

    public ProceduralRecipe(
            String name,
            PathShape shape,
            String pattern,
            int radius,
            int spacing,
            int thickness,
            int rollDegrees,
            int height,
            String paletteId,
            GradientAxis gradientAxis,
            int dither,
            boolean reverse,
            Map<String, Double> options,
            RecipeExtensions extensions
    ) {
        this(name, shape, pattern, defaultAccentPattern(shape), radius, spacing, thickness, rollDegrees, height,
                paletteId, gradientAxis, dither, reverse, options, extensions);
    }

    public ProceduralRecipe(
            String name,
            PathShape shape,
            String pattern,
            int radius,
            int spacing,
            int thickness,
            int rollDegrees,
            int height,
            String paletteId,
            GradientAxis gradientAxis,
            int dither,
            boolean reverse,
            Map<String, Double> options
    ) {
        this(name, shape, pattern, defaultAccentPattern(shape), radius, spacing, thickness, rollDegrees, height, paletteId,
                gradientAxis, dither, reverse, options, RecipeExtensions.DEFAULT);
    }

    public static ProceduralRecipe defaults() {
        ShapeParameters defaults = PathShape.SCALES.defaults();
        return new ProceduralRecipe(
                "mi_receta",
                defaults.shape(),
                "stone",
                "oak_leaves",
                defaults.radius(),
                defaults.spacing(),
                defaults.thickness(),
                defaults.rollDegrees(),
                18,
                "",
                GradientAxis.Y,
                35,
                false,
                Map.of(),
                RecipeExtensions.DEFAULT
        );
    }

    public boolean usesGradient() {
        return !paletteId.isEmpty();
    }

    public GradientPalette palette() {
        if (!usesGradient()) {
            throw new IllegalStateException("La receta usa un material sólido");
        }
        return GradientPalette.byId(paletteId);
    }

    public ShapeParameters shapeParameters() {
        Map<String, Double> resolved = new LinkedHashMap<>(options);
        if (shape.usesHeight()) {
            resolved.put("height", (double) height);
        }
        if (shape == PathShape.CLOUDS) {
            resolved.put("gap", (double) height);
        }
        return new ShapeParameters(
                shape,
                radius,
                spacing,
                thickness,
                rollDegrees,
                new ShapeOptions(resolved, formula)
        );
    }

    public ProceduralRecipe withName(String value) {
        return copy(value, shape, pattern, radius, spacing, thickness, rollDegrees, height,
                paletteId, gradientAxis, dither, reverse);
    }

    public ProceduralRecipe copy(
            String nextName,
            PathShape nextShape,
            String nextPattern,
            int nextRadius,
            int nextSpacing,
            int nextThickness,
            int nextRoll,
            int nextHeight,
            String nextPalette,
            GradientAxis nextAxis,
            int nextDither,
            boolean nextReverse
    ) {
        return new ProceduralRecipe(
                nextName,
                nextShape,
                nextPattern,
                accentPattern,
                nextRadius,
                nextSpacing,
                nextThickness,
                nextRoll,
                nextHeight,
                nextPalette,
                nextAxis,
                nextDither,
                nextReverse,
                options,
                extensions,
                formula
        );
    }

    public ProceduralRecipe withExtensions(RecipeExtensions value) {
        return new ProceduralRecipe(
                name,
                shape,
                pattern,
                accentPattern,
                radius,
                spacing,
                thickness,
                rollDegrees,
                height,
                paletteId,
                gradientAxis,
                dither,
                reverse,
                options,
                value,
                formula
        );
    }

    public ProceduralRecipe withShapeDefaults(PathShape nextShape) {
        ShapeParameters defaults = nextShape.defaults();
        return new ProceduralRecipe(
                name,
                nextShape,
                pattern,
                accentPattern,
                defaults.radius(),
                defaults.spacing(),
                defaults.thickness(),
                defaults.rollDegrees(),
                nextShape.defaultHeight(),
                paletteId,
                gradientAxis,
                dither,
                reverse,
                defaults.options().values(),
                RecipeExtensions.DEFAULT
        );
    }

    public ProceduralRecipe withOptions(Map<String, Double> nextOptions) {
        return new ProceduralRecipe(
                name,
                shape,
                pattern,
                accentPattern,
                radius,
                spacing,
                thickness,
                rollDegrees,
                height,
                paletteId,
                gradientAxis,
                dither,
                reverse,
                nextOptions,
                extensions,
                formula
        );
    }

    /** Fórmula propia de la figura Expresión; vacía vuelve a la fórmula de serie. */
    public ProceduralRecipe withFormula(String value) {
        return new ProceduralRecipe(
                name,
                shape,
                pattern,
                accentPattern,
                radius,
                spacing,
                thickness,
                rollDegrees,
                height,
                paletteId,
                gradientAxis,
                dither,
                reverse,
                options,
                extensions,
                value
        );
    }

    private static String defaultAccentPattern(PathShape shape) {
        return "oak_leaves";
    }
}
