package com.komainu.moxia.gizmo;

import com.komainu.moxia.shape.Vec3;

/**
 * La geometría del gizmo: rayo contra eje, rayo contra plano, el ángulo de un anillo y los pasos de bloque. Lógica
 * pura: el cliente le pasa el rayo del punto de mira (o del ratón en el Studio) y las pruebas, rayos a mano.
 */
public final class GizmoMath {
    private static final double PARALLEL = 1.0E-6;

    private GizmoMath() {
    }

    /** Un rayo con su dirección normalizada. */
    public record Ray(Vec3 origin, Vec3 direction) {
        public Ray {
            if (origin == null || direction == null || direction.length() < 1.0E-9) {
                throw new IllegalArgumentException("El rayo necesita origen y dirección");
            }
            direction = direction.normalized();
        }

        public Vec3 at(double distance) {
            return origin.add(direction.multiply(distance));
        }
    }

    /**
     * El punto del eje más cercano al rayo.
     *
     * @param along       dónde cae sobre el eje, medido desde su origen
     * @param rayDistance a qué distancia del origen del rayo
     * @param separation  distancia entre el rayo y el eje en ese punto
     */
    public record AxisHit(double along, double rayDistance, double separation) {
    }

    /**
     * Rayo contra la recta que pasa por {@code origin} en la dirección del eje {@code axis}. Nulo si el rayo va
     * paralelo al eje (no hay un punto más cercano: mirando a lo largo de la flecha no se puede arrastrar por ella).
     */
    public static AxisHit axis(Ray ray, Vec3 origin, int axis) {
        Vec3 direction = unit(axis);
        Vec3 offset = ray.origin().subtract(origin);
        double parallel = ray.direction().dot(direction);
        double denominator = 1.0 - parallel * parallel;
        if (denominator < PARALLEL) {
            return null;
        }
        double rayOffset = ray.direction().dot(offset);
        double axisOffset = direction.dot(offset);
        double rayDistance = (parallel * axisOffset - rayOffset) / denominator;
        double along = (axisOffset - parallel * rayOffset) / denominator;
        Vec3 onRay = ray.at(rayDistance);
        Vec3 onAxis = origin.add(direction.multiply(along));
        return new AxisHit(along, rayDistance, onRay.distance(onAxis));
    }

    /**
     * Distancia a la que el rayo corta el plano de {@code point} y {@code normal}, o {@code NaN} si va paralelo o el
     * plano queda detrás.
     */
    public static double plane(Ray ray, Vec3 point, Vec3 normal) {
        double facing = ray.direction().dot(normal);
        if (Math.abs(facing) < PARALLEL) {
            return Double.NaN;
        }
        double distance = point.subtract(ray.origin()).dot(normal) / facing;
        return distance < 0.0 ? Double.NaN : distance;
    }

    /** Distancia a la que el rayo entra en la caja, o {@code NaN} si no la toca. */
    public static double box(Ray ray, Vec3 minimum, Vec3 maximum) {
        double near = 0.0;
        double far = Double.POSITIVE_INFINITY;
        double[] origin = {ray.origin().x(), ray.origin().y(), ray.origin().z()};
        double[] direction = {ray.direction().x(), ray.direction().y(), ray.direction().z()};
        double[] low = {minimum.x(), minimum.y(), minimum.z()};
        double[] high = {maximum.x(), maximum.y(), maximum.z()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < PARALLEL) {
                if (origin[axis] < low[axis] || origin[axis] > high[axis]) {
                    return Double.NaN;
                }
                continue;
            }
            double first = (low[axis] - origin[axis]) / direction[axis];
            double second = (high[axis] - origin[axis]) / direction[axis];
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
            if (near > far) {
                return Double.NaN;
            }
        }
        return near;
    }

    /** Bloques enteros de un desplazamiento: se redondea al más cercano (medio bloque ya cuenta). */
    public static int steps(double delta) {
        return (int) Math.floor(delta + 0.5);
    }

    /**
     * Tamaño en el mundo que ocupa {@code fraction} de la altura de la pantalla a {@code distance} bloques de la
     * cámara con un campo de visión vertical de {@code fovDegrees}: así el gizmo se ve igual de grande cerca y lejos.
     */
    public static double screenSize(double distance, double fovDegrees, double fraction) {
        double tangent = Math.tan(Math.toRadians(Math.clamp(fovDegrees, 1.0, 179.0) * 0.5));
        return Math.max(0.05, distance) * 2.0 * tangent * fraction;
    }

    /**
     * Ángulo de {@code point} alrededor de {@code center} en el plano perpendicular al eje, medido de forma que un
     * cuarto de vuelta positivo de {@link Orientation#turn} lo sube 90°: en Y de este a sur, en X de arriba a sur y en
     * Z de este a arriba.
     */
    public static double angle(Vec3 point, Vec3 center, int axis) {
        Vec3 offset = point.subtract(center);
        return switch (axis) {
            case Orientation.X -> Math.atan2(offset.z(), offset.y());
            case Orientation.Y -> Math.atan2(offset.z(), offset.x());
            default -> Math.atan2(offset.y(), offset.x());
        };
    }

    /** Diferencia entre dos ángulos llevada a (−π, π]: lo que se ha girado entre dos lecturas seguidas. */
    public static double wrap(double radians) {
        double wrapped = Math.IEEEremainder(radians, 2.0 * Math.PI);
        return wrapped <= -Math.PI ? wrapped + 2.0 * Math.PI : wrapped;
    }

    /** Cuartos de vuelta enteros de un ángulo acumulado (se redondea al más cercano). */
    public static int quarterTurns(double radians) {
        return (int) Math.floor(radians / (Math.PI / 2.0) + 0.5);
    }

    public static Vec3 unit(int axis) {
        return switch (axis) {
            case Orientation.X -> new Vec3(1, 0, 0);
            case Orientation.Y -> new Vec3(0, 1, 0);
            case Orientation.Z -> new Vec3(0, 0, 1);
            default -> throw new IllegalArgumentException("Eje desconocido: " + axis);
        };
    }

    /** Eje en el que más avanza el vector. */
    public static int dominantAxis(Vec3 vector) {
        double x = Math.abs(vector.x());
        double y = Math.abs(vector.y());
        double z = Math.abs(vector.z());
        return x >= y && x >= z ? Orientation.X : y >= z ? Orientation.Y : Orientation.Z;
    }

    /**
     * La dirección horizontal de bloque (norte, sur, este u oeste) más parecida a la mirada, como {dx, dz}. Es el
     * «adelante» de las flechas del teclado.
     */
    public static int[] forward(double lookX, double lookZ) {
        if (Math.abs(lookX) > Math.abs(lookZ)) {
            return new int[]{lookX > 0 ? 1 : -1, 0};
        }
        return new int[]{0, lookZ > 0 || lookZ == 0.0 && lookX == 0.0 ? 1 : -1};
    }

    /** La izquierda de un «adelante» horizontal (mirando al norte, el oeste). */
    public static int[] left(int[] forward) {
        return new int[]{forward[1], -forward[0]};
    }
}
