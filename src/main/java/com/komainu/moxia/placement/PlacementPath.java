package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.tube.TubeSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Trayectoria de un array: curva de Kochanek–Bartels por los puntos (tensión, sesgo y continuidad de {@code -p}),
 * muestreada cada cuarto de bloque por longitud de arco y con un marco en cada muestra para {@code Tangential} y
 * {@code Orthogonal}.
 *
 * <ul>
 *     <li>{@code consistent}: el marco viaja sin girar sobre sí mismo (transporte paralelo); en un lazo se reparte
 *     el desfase del cierre.</li>
 *     <li>{@code horizontal}: la normal es siempre la perpendicular más hacia arriba, con el lado horizontal.</li>
 *     <li>{@code upright}: la normal es el +y del mundo.</li>
 * </ul>
 */
public final class PlacementPath {
    public static final double STEP = 0.25;
    public static final double MAX_LENGTH = 4_096.0;

    private final Vec3[] points;
    private final Vec3[] tangents;
    private final Vec3[] normals;
    private final double length;
    private final boolean closed;

    private PlacementPath(Vec3[] points, Vec3[] tangents, Vec3[] normals, double length, boolean closed) {
        this.points = points;
        this.tangents = tangents;
        this.normals = normals;
        this.length = length;
        this.closed = closed;
    }

    public static PlacementPath create(
            List<Vec3> controls,
            boolean closed,
            double tension,
            double bias,
            double continuity,
            TubeSettings.NormalMode mode
    ) {
        List<Vec3> distinct = new ArrayList<>();
        for (Vec3 control : controls) {
            if (distinct.isEmpty() || distinct.getLast().distance(control) > 1.0E-6) {
                distinct.add(control);
            }
        }
        if (closed && distinct.size() > 1 && distinct.getFirst().distance(distinct.getLast()) < 1.0E-6) {
            distinct.removeLast();
        }
        int minimum = closed ? 3 : 2;
        if (distinct.size() < minimum) {
            throw new IllegalArgumentException("La trayectoria necesita al menos " + minimum + " puntos distintos");
        }
        List<Vec3> dense = curve(distinct, closed, tension, bias, continuity);
        double[] cumulative = new double[dense.size()];
        for (int index = 1; index < dense.size(); index++) {
            cumulative[index] = cumulative[index - 1] + dense.get(index).distance(dense.get(index - 1));
        }
        double total = cumulative[cumulative.length - 1];
        if (total < 0.5) {
            throw new IllegalArgumentException("La trayectoria es demasiado corta");
        }
        if (total > MAX_LENGTH) {
            throw new IllegalArgumentException("La trayectoria supera el límite de 4096 bloques de longitud");
        }
        int intervals = Math.max(2, (int) Math.ceil(total / STEP));
        Vec3[] points = new Vec3[intervals + 1];
        int cursor = 0;
        for (int sample = 0; sample <= intervals; sample++) {
            double target = total * sample / intervals;
            while (cursor < cumulative.length - 2 && cumulative[cursor + 1] < target) {
                cursor++;
            }
            double span = cumulative[cursor + 1] - cumulative[cursor];
            double amount = span < 1.0E-12 ? 0.0 : Math.clamp((target - cumulative[cursor]) / span, 0.0, 1.0);
            points[sample] = dense.get(cursor).lerp(dense.get(cursor + 1), amount);
        }
        Vec3[] tangents = tangents(points, closed);
        Vec3[] normals = normals(tangents, closed, mode);
        return new PlacementPath(points, tangents, normals, total, closed);
    }

    private static List<Vec3> curve(List<Vec3> controls, boolean closed, double tension, double bias,
                                    double continuity) {
        int size = controls.size();
        int segments = closed ? size : size - 1;
        List<Vec3> dense = new ArrayList<>();
        for (int segment = 0; segment < segments; segment++) {
            Vec3 previous = control(controls, segment - 1, closed);
            Vec3 start = control(controls, segment, closed);
            Vec3 end = control(controls, segment + 1, closed);
            Vec3 following = control(controls, segment + 2, closed);
            // Tangentes de Kochanek–Bartels a la entrada y salida del tramo.
            Vec3 outgoing = start.subtract(previous).multiply((1 - tension) * (1 + bias) * (1 - continuity) * 0.5)
                    .add(end.subtract(start).multiply((1 - tension) * (1 - bias) * (1 + continuity) * 0.5));
            Vec3 incoming = end.subtract(start).multiply((1 - tension) * (1 + bias) * (1 + continuity) * 0.5)
                    .add(following.subtract(end).multiply((1 - tension) * (1 - bias) * (1 - continuity) * 0.5));
            double chord = start.distance(end);
            int steps = Math.clamp((int) Math.ceil(chord * 8.0), 8, 8_192);
            for (int step = 0; step < steps; step++) {
                double t = step / (double) steps;
                double t2 = t * t;
                double t3 = t2 * t;
                dense.add(start.multiply(2 * t3 - 3 * t2 + 1)
                        .add(outgoing.multiply(t3 - 2 * t2 + t))
                        .add(end.multiply(-2 * t3 + 3 * t2))
                        .add(incoming.multiply(t3 - t2)));
            }
        }
        dense.add(closed ? controls.getFirst() : controls.getLast());
        return dense;
    }

    private static Vec3 control(List<Vec3> controls, int index, boolean closed) {
        int size = controls.size();
        if (closed) {
            return controls.get(Math.floorMod(index, size));
        }
        if (index < 0) {
            return controls.getFirst().multiply(2.0).subtract(controls.get(1));
        }
        if (index >= size) {
            return controls.getLast().multiply(2.0).subtract(controls.get(size - 2));
        }
        return controls.get(index);
    }

    private static Vec3[] tangents(Vec3[] points, boolean closed) {
        int last = points.length - 1;
        Vec3[] tangents = new Vec3[points.length];
        for (int index = 0; index <= last; index++) {
            int before = closed ? (index == 0 ? last - 1 : index - 1) : Math.max(0, index - 1);
            int after = closed ? (index == last ? 1 : index + 1) : Math.min(last, index + 1);
            Vec3 difference = points[after].subtract(points[before]);
            tangents[index] = difference.length() < 1.0E-9
                    ? (index > 0 ? tangents[index - 1] : Alignment.EAST)
                    : difference.normalized();
        }
        return tangents;
    }

    private static Vec3[] normals(Vec3[] tangents, boolean closed, TubeSettings.NormalMode mode) {
        Vec3[] normals = new Vec3[tangents.length];
        if (mode == TubeSettings.NormalMode.UPRIGHT) {
            for (int index = 0; index < normals.length; index++) {
                normals[index] = Alignment.UP;
            }
            return normals;
        }
        normals[0] = upward(tangents[0], null);
        for (int index = 1; index < normals.length; index++) {
            Vec3 transported = transport(normals[index - 1], tangents[index - 1], tangents[index]);
            normals[index] = mode == TubeSettings.NormalMode.HORIZONTAL
                    ? upward(tangents[index], transported)
                    : transported;
        }
        if (closed && mode == TubeSettings.NormalMode.CONSISTENT) {
            // Lo que falta para que el último marco coincida con el primero se reparte por todo el lazo.
            int last = normals.length - 1;
            Vec3 tangent = tangents[0];
            double sine = normals[last].cross(normals[0]).dot(tangent);
            double cosine = normals[last].dot(normals[0]);
            double mismatch = Math.toDegrees(Math.atan2(sine, cosine));
            for (int index = 0; index <= last; index++) {
                normals[index] = Mat3.rotation(tangents[index], mismatch * index / last).apply(normals[index]);
            }
        }
        return normals;
    }

    /** La perpendicular a la tangente más parecida a +y; en tramos verticales se queda la de antes. */
    private static Vec3 upward(Vec3 tangent, Vec3 fallback) {
        Vec3 up = Alignment.UP.subtract(tangent.multiply(tangent.y()));
        if (up.length() < 1.0E-3) {
            if (fallback != null) {
                return fallback;
            }
            up = Alignment.EAST.subtract(tangent.multiply(tangent.x()));
        }
        return up.normalized();
    }

    /** Reflexión doble (Wang et al. 2008): lleva la normal de una tangente a la siguiente sin girarla. */
    private static Vec3 transport(Vec3 normal, Vec3 from, Vec3 to) {
        Vec3 axis = from.cross(to);
        double sine = axis.length();
        if (sine < 1.0E-12) {
            return normal;
        }
        double angle = Math.toDegrees(Math.atan2(sine, from.dot(to)));
        Vec3 rotated = Mat3.rotation(axis, angle).apply(normal);
        Vec3 projected = rotated.subtract(to.multiply(rotated.dot(to)));
        return projected.length() < 1.0E-9 ? normal : projected.normalized();
    }

    public double length() {
        return length;
    }

    public boolean closed() {
        return closed;
    }

    /** Punto, tangente y normal a {@code distance} bloques del principio. */
    public Frame at(double distance) {
        double clamped = Math.clamp(distance, 0.0, length);
        double position = clamped / length * (points.length - 1);
        int index = Math.min(points.length - 2, (int) Math.floor(position));
        double amount = position - index;
        Vec3 tangent = tangents[index].lerp(tangents[index + 1], amount);
        tangent = tangent.length() < 1.0E-9 ? tangents[index] : tangent.normalized();
        Vec3 normal = normals[index].lerp(normals[index + 1], amount);
        normal = normal.length() < 1.0E-9 ? normals[index] : normal.normalized();
        return new Frame(points[index].lerp(points[index + 1], amount), tangent, normal, clamped);
    }

    public record Frame(Vec3 position, Vec3 tangent, Vec3 normal, double distance) {
    }
}
