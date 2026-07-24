package com.komainu.moxia.shape;

public record ShapeParameters(
        PathShape shape,
        int radius,
        int spacing,
        int thickness,
        int rollDegrees,
        ShapeOptions options
) {
    public ShapeParameters(PathShape shape, int radius, int spacing, int thickness, int rollDegrees) {
        this(shape, radius, spacing, thickness, rollDegrees, ShapeOptions.EMPTY);
    }

    public ShapeParameters {
        if (options == null) {
            options = ShapeOptions.EMPTY;
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
    }

    public ShapeParameters withRadius(int value) {
        return new ShapeParameters(shape, value, spacing, thickness, rollDegrees, options);
    }

    public ShapeParameters withSpacing(int value) {
        return new ShapeParameters(shape, radius, value, thickness, rollDegrees, options);
    }

    public ShapeParameters withThickness(int value) {
        return new ShapeParameters(shape, radius, spacing, value, rollDegrees, options);
    }

    public ShapeParameters withRollDegrees(int value) {
        return new ShapeParameters(shape, radius, spacing, thickness, value, options);
    }

    public ShapeParameters withOptions(ShapeOptions value) {
        return new ShapeParameters(shape, radius, spacing, thickness, rollDegrees, value);
    }
}
