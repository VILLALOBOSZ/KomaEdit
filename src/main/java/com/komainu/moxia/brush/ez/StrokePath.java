package com.komainu.moxia.brush.ez;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.List;

/** Polilínea del trazo de degradado, sobre los centros de los bloques marcados. */
public final class StrokePath {
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final double[] cumulative;
    private final double length;

    public StrokePath(List<BlockVector3> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("El trazo necesita al menos dos puntos");
        }
        int count = points.size();
        xs = new double[count];
        ys = new double[count];
        zs = new double[count];
        cumulative = new double[count];
        for (int index = 0; index < count; index++) {
            BlockVector3 point = points.get(index);
            xs[index] = point.x() + 0.5;
            ys[index] = point.y() + 0.5;
            zs[index] = point.z() + 0.5;
            if (index > 0) {
                cumulative[index] = cumulative[index - 1] + Math.sqrt(square(xs[index] - xs[index - 1])
                        + square(ys[index] - ys[index - 1]) + square(zs[index] - zs[index - 1]));
            }
        }
        length = cumulative[count - 1];
    }

    public double length() {
        return length;
    }

    public int pointCount() {
        return xs.length;
    }

    /** Punto más cercano del trazo a {@code (x, y, z)}: fracción de longitud recorrida (0–1) y distancia. */
    public Closest closest(double x, double y, double z) {
        double best = Double.POSITIVE_INFINITY;
        double along = 0.0;
        for (int index = 1; index < xs.length; index++) {
            double sx = xs[index] - xs[index - 1];
            double sy = ys[index] - ys[index - 1];
            double sz = zs[index] - zs[index - 1];
            double segment = sx * sx + sy * sy + sz * sz;
            double amount = segment < 1.0E-9 ? 0.0 : Math.clamp(((x - xs[index - 1]) * sx
                    + (y - ys[index - 1]) * sy + (z - zs[index - 1]) * sz) / segment, 0.0, 1.0);
            double distance = square(xs[index - 1] + sx * amount - x) + square(ys[index - 1] + sy * amount - y)
                    + square(zs[index - 1] + sz * amount - z);
            if (distance < best) {
                best = distance;
                double travelled = cumulative[index - 1] + Math.sqrt(segment) * amount;
                along = length < 1.0E-9 ? 0.0 : travelled / length;
            }
        }
        return new Closest(along, Math.sqrt(best));
    }

    public int minimumX() {
        return (int) Math.floor(min(xs));
    }

    public int minimumY() {
        return (int) Math.floor(min(ys));
    }

    public int minimumZ() {
        return (int) Math.floor(min(zs));
    }

    public int maximumX() {
        return (int) Math.floor(max(xs));
    }

    public int maximumY() {
        return (int) Math.floor(max(ys));
    }

    public int maximumZ() {
        return (int) Math.floor(max(zs));
    }

    private static double min(double[] values) {
        double result = Double.POSITIVE_INFINITY;
        for (double value : values) {
            result = Math.min(result, value);
        }
        return result;
    }

    private static double max(double[] values) {
        double result = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    private static double square(double value) {
        return value * value;
    }

    public record Closest(double along, double distance) {
    }
}
