package com.komainu.moxia.selection;

import java.util.ArrayList;
import java.util.List;

/**
 * El contorno del lazo en la pantalla: una lista de puntos que se cierra sola del último al primero. Dentro es lo que
 * deja la regla par-impar, así que un contorno que se cruza consigo mismo deja huecos donde se solapa, como en los
 * programas de dibujo.
 *
 * @param xs coordenadas X de los puntos (en unidades de pantalla)
 * @param ys coordenadas Y
 */
public record LassoPolygon(float[] xs, float[] ys) {
    /** Puntos como mucho que viajan al servidor; uno más largo se simplifica antes ({@link #simplified}). */
    public static final int MAX_POINTS = 512;
    /** Lo que tiene que encerrar un lazo (en unidades de pantalla al cuadrado) para no ser un simple clic. */
    public static final double MIN_AREA = 16.0;

    public LassoPolygon {
        if (xs == null || ys == null || xs.length != ys.length) {
            throw new IllegalArgumentException("Contorno del lazo no válido");
        }
        if (xs.length < 3) {
            throw new IllegalArgumentException("El lazo necesita al menos tres puntos");
        }
        if (xs.length > MAX_POINTS) {
            throw new IllegalArgumentException("El lazo tiene demasiados puntos: " + xs.length + " (máximo "
                    + MAX_POINTS + ")");
        }
        for (int index = 0; index < xs.length; index++) {
            if (!Float.isFinite(xs[index]) || !Float.isFinite(ys[index])) {
                throw new IllegalArgumentException("Contorno del lazo no válido");
            }
        }
    }

    public static LassoPolygon of(List<float[]> points) {
        float[] xs = new float[points.size()];
        float[] ys = new float[points.size()];
        for (int index = 0; index < points.size(); index++) {
            xs[index] = points.get(index)[0];
            ys[index] = points.get(index)[1];
        }
        return new LassoPolygon(xs, ys);
    }

    public int size() {
        return xs.length;
    }

    /** Si el punto queda dentro (regla par-impar). */
    public boolean contains(double x, double y) {
        boolean inside = false;
        int count = xs.length;
        for (int index = 0, previous = count - 1; index < count; previous = index++) {
            double yi = ys[index];
            double yj = ys[previous];
            if (yi > y != yj > y) {
                double crossing = (xs[previous] - xs[index]) * (y - yi) / (yj - yi) + xs[index];
                if (x < crossing) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /** La superficie que encierra (sin signo; con cruces, la suma con signo de sus partes). */
    public double area() {
        double twice = 0.0;
        int count = xs.length;
        for (int index = 0, previous = count - 1; index < count; previous = index++) {
            twice += (double) xs[previous] * ys[index] - (double) xs[index] * ys[previous];
        }
        return Math.abs(twice) * 0.5;
    }

    /** {@code {minX, minY, maxX, maxY}}. */
    public double[] bounds() {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < xs.length; index++) {
            minX = Math.min(minX, xs[index]);
            minY = Math.min(minY, ys[index]);
            maxX = Math.max(maxX, xs[index]);
            maxY = Math.max(maxY, ys[index]);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * El contorno con {@code maxPoints} puntos como mucho, quitando los que menos cambian la forma (Douglas-Peucker
     * con la tolerancia justa). Con menos puntos que el máximo, sale tal cual.
     */
    public static List<float[]> simplified(List<float[]> points, int maxPoints) {
        if (points.size() <= maxPoints) {
            return List.copyOf(points);
        }
        double low = 0.0;
        double high = 64.0;
        List<float[]> best = null;
        // La tolerancia más pequeña que deja el contorno por debajo del máximo, buscada a mitades.
        for (int round = 0; round < 24; round++) {
            double tolerance = (low + high) * 0.5;
            List<float[]> attempt = douglasPeucker(points, tolerance);
            if (attempt.size() <= maxPoints) {
                best = attempt;
                high = tolerance;
            } else {
                low = tolerance;
            }
        }
        if (best == null) {
            best = douglasPeucker(points, high);
        }
        if (best.size() > maxPoints) {
            // Por si acaso: uno de cada n.
            List<float[]> thinned = new ArrayList<>(maxPoints);
            double step = best.size() / (double) maxPoints;
            for (int index = 0; index < maxPoints; index++) {
                thinned.add(best.get((int) (index * step)));
            }
            best = thinned;
        }
        return best;
    }

    private static List<float[]> douglasPeucker(List<float[]> points, double tolerance) {
        int count = points.size();
        boolean[] keep = new boolean[count];
        keep[0] = true;
        keep[count - 1] = true;
        ArrayList<int[]> pending = new ArrayList<>();
        pending.add(new int[]{0, count - 1});
        while (!pending.isEmpty()) {
            int[] span = pending.removeLast();
            int first = span[0];
            int last = span[1];
            double farthest = -1.0;
            int index = -1;
            for (int middle = first + 1; middle < last; middle++) {
                double distance = distance(points.get(middle), points.get(first), points.get(last));
                if (distance > farthest) {
                    farthest = distance;
                    index = middle;
                }
            }
            if (index >= 0 && farthest > tolerance) {
                keep[index] = true;
                pending.add(new int[]{first, index});
                pending.add(new int[]{index, last});
            }
        }
        List<float[]> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            if (keep[index]) {
                result.add(points.get(index));
            }
        }
        return result;
    }

    private static double distance(float[] point, float[] start, float[] end) {
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double length = dx * dx + dy * dy;
        if (length < 1.0E-12) {
            return Math.hypot(point[0] - start[0], point[1] - start[1]);
        }
        double along = Math.clamp(((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / length, 0.0, 1.0);
        return Math.hypot(point[0] - (start[0] + along * dx), point[1] - (start[1] + along * dy));
    }
}
