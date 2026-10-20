package com.komainu.moxia.studio.world;

import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;

import java.util.Arrays;
import java.util.List;

/**
 * Los puntos de la trayectoria con el ratón, sin flechas: pulsar en un punto lo agarra y arrastrando lo lleva por el
 * terreno; pulsar en un tramo mete un punto en medio y lo agarra; pulsar en el terreno añade uno al final (y también
 * se puede arrastrar antes de soltarlo). Las flechas del punto elegido siguen sirviendo para moverlo en un solo eje
 * (subirlo, por ejemplo). Lógica pura: la pantalla le da el rayo del cursor y el sitio del terreno al que apunta, y
 * manda lo que salga ({@code PATH_ADD}, {@code PATH_INSERT}, {@code PATH_MOVE}).
 */
public final class PathDrag {
    /** Lo que agarró la pulsación. */
    public enum Grab {
        NONE,
        /** Un punto que ya estaba. */
        POINT,
        /** Uno nuevo en medio de un tramo, tras el punto {@link Press#after}. */
        INSERT,
        /** Uno nuevo al final. */
        ADD
    }

    /**
     * Lo que hay que mandar al pulsar.
     *
     * @param grab   qué se agarró
     * @param index  el punto que queda agarrado (el nuevo, al insertar o añadir)
     * @param after  al insertar, tras qué punto
     * @param place  al añadir, dónde
     */
    public record Press(Grab grab, int index, int after, int[] place) {
        static final Press NONE = new Press(Grab.NONE, -1, -1, null);
    }

    private int index = -1;
    private int[] last;
    private boolean moved;

    public boolean active() {
        return index >= 0;
    }

    /** El punto agarrado, o -1. */
    public int index() {
        return index;
    }

    /** Si el punto agarrado ya se movió (si no, soltar es un clic sin más). */
    public boolean moved() {
        return moved;
    }

    /**
     * Pulsar en el mundo.
     *
     * @param points los puntos de ahora, {x, y, z}
     * @param closed si la trayectoria está cerrada (tiene un tramo del último al primero)
     * @param place  el bloque de aire delante de la cara a la que apunta el cursor, o {@code null}
     * @param limit  cuántos puntos caben como mucho
     */
    public Press press(GizmoMath.Ray ray, List<int[]> points, boolean closed, int[] place, int limit) {
        moved = false;
        int point = pickPoint(ray, points);
        if (point >= 0) {
            index = point;
            last = points.get(point).clone();
            return new Press(Grab.POINT, point, -1, null);
        }
        if (points.size() < limit) {
            int segment = pickSegment(ray, points, closed);
            if (segment >= 0) {
                index = segment + 1;
                last = null;
                return new Press(Grab.INSERT, segment + 1, segment, null);
            }
            if (place != null) {
                index = points.size();
                last = place.clone();
                return new Press(Grab.ADD, points.size(), -1, place.clone());
            }
        }
        index = -1;
        return Press.NONE;
    }

    /**
     * Arrastrar: el punto agarrado va al sitio del terreno al que apunta el cursor.
     *
     * @return dónde tiene que ir ahora, o {@code null} si no ha cambiado de bloque
     */
    public int[] drag(int[] place) {
        if (index < 0 || place == null || Arrays.equals(place, last)) {
            return null;
        }
        last = place.clone();
        moved = true;
        return place.clone();
    }

    public void release() {
        index = -1;
        last = null;
    }

    /** El punto más cercano al rayo a menos de un bloque y poco (más de lejos, algo más), o -1. */
    public static int pickPoint(GizmoMath.Ray ray, List<int[]> points) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < points.size(); index++) {
            Vec3 center = center(points.get(index));
            double along = center.subtract(ray.origin()).dot(ray.direction());
            if (along < 0.0) {
                continue;
            }
            double distance = center.distance(ray.at(along));
            if (distance <= threshold(along) && distance < bestDistance) {
                best = index;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * El tramo que pasa más cerca del rayo (el de {@code i} a {@code i + 1}; cerrado, también del último al primero),
     * sin contar sus puntas, que ya son de los puntos; o -1.
     */
    public static int pickSegment(GizmoMath.Ray ray, List<int[]> points, boolean closed) {
        int segments = closed && points.size() >= 3 ? points.size() : points.size() - 1;
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < segments; index++) {
            Vec3 start = center(points.get(index));
            Vec3 end = center(points.get((index + 1) % points.size()));
            double[] closest = closest(ray, start, end);
            if (closest == null || closest[0] < 0.0 || closest[1] < 0.15 || closest[1] > 0.85) {
                continue;
            }
            if (closest[2] <= threshold(closest[0]) && closest[2] < bestDistance) {
                best = index;
                bestDistance = closest[2];
            }
        }
        return best;
    }

    /** Hasta dónde vale un clic, según lo lejos que esté: más margen de lejos, donde un bloque se ve pequeño. */
    static double threshold(double distance) {
        return Math.clamp(distance * 0.009, 0.70, 1.40);
    }

    /**
     * Lo más cerca que pasan el rayo y el segmento: {distancia por el rayo, dónde cae en el segmento (0–1),
     * separación}; o {@code null} si van paralelos.
     */
    static double[] closest(GizmoMath.Ray ray, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double length = segment.dot(segment);
        if (length < 1.0E-9) {
            return null;
        }
        Vec3 offset = ray.origin().subtract(start);
        double a = 1.0;
        double b = ray.direction().dot(segment);
        double c = length;
        double d = ray.direction().dot(offset);
        double e = segment.dot(offset);
        double denominator = a * c - b * b;
        if (Math.abs(denominator) < 1.0E-9) {
            return null;
        }
        double rayDistance = (b * e - c * d) / denominator;
        double along = Math.clamp((a * e - b * d) / denominator, 0.0, 1.0);
        Vec3 onSegment = start.add(segment.multiply(along));
        double onRay = Math.max(0.0, onSegment.subtract(ray.origin()).dot(ray.direction()));
        return new double[]{rayDistance, along, onSegment.distance(ray.at(onRay))};
    }

    private static Vec3 center(int[] point) {
        return new Vec3(point[0] + 0.5, point[1] + 0.5, point[2] + 0.5);
    }
}
