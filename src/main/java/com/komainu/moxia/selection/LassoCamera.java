package com.komainu.moxia.selection;

import com.komainu.moxia.shape.Vec3;

/**
 * La cámara con la que se dibujó el lazo: dónde estaba, hacia dónde miraba, su campo de visión vertical y el tamaño
 * de la pantalla. Con ella se sabe por dónde pasa el rayo de cada punto de la pantalla, igual que el cursor del
 * Studio, y dónde cae en la pantalla un punto del mundo.
 *
 * @param fov    campo de visión vertical, en grados
 * @param width  ancho de la pantalla (en las mismas unidades que los puntos del lazo)
 * @param height alto de la pantalla
 */
public record LassoCamera(Vec3 position, Vec3 forward, Vec3 up, Vec3 right, double fov, double width, double height) {
    public static final double MIN_FOV = 1.0;
    public static final double MAX_FOV = 179.0;
    public static final double MAX_SCREEN = 16_384.0;

    public LassoCamera {
        if (!finite(position) || !finite(forward) || !finite(up)) {
            throw new IllegalArgumentException("Cámara del lazo no válida");
        }
        if (!(fov >= MIN_FOV && fov <= MAX_FOV) || !(width >= 1.0 && width <= MAX_SCREEN)
                || !(height >= 1.0 && height <= MAX_SCREEN)) {
            throw new IllegalArgumentException("Pantalla del lazo no válida");
        }
    }

    /**
     * La cámara desde el ojo y dos direcciones (adelante y arriba, como las da Minecraft). Se enderezan: arriba pasa
     * a ser perpendicular a adelante, y la derecha sale de las dos.
     */
    public static LassoCamera of(Vec3 position, Vec3 forward, Vec3 up, double fov, double width, double height) {
        Vec3 front = unit(forward);
        Vec3 right = unit(front.cross(up));
        Vec3 straightUp = right.cross(front);
        return new LassoCamera(position, front, straightUp, right, fov, width, height);
    }

    /** La cámara de un jugador que mira con esta guiñada y cabeceo (grados, como Minecraft), sin alabeo. */
    public static LassoCamera looking(Vec3 position, double yaw, double pitch, double fov, double width,
                                      double height) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 forward = new Vec3(-Math.sin(yawRadians) * Math.cos(pitchRadians), -Math.sin(pitchRadians),
                Math.cos(yawRadians) * Math.cos(pitchRadians));
        // Arriba es hacia delante con el cabeceo 90° más alto.
        Vec3 up = new Vec3(-Math.sin(yawRadians) * Math.sin(pitchRadians), Math.cos(pitchRadians),
                Math.cos(yawRadians) * Math.sin(pitchRadians));
        return of(position, forward, up, fov, width, height);
    }

    /** {@code tan(fov / 2)}: lo que se abre la vista por cada unidad hacia delante. */
    public double tangent() {
        return Math.tan(Math.toRadians(fov * 0.5));
    }

    /** Puntos de pantalla por bloque a {@code distance} bloques de la cámara (en el centro de la vista). */
    public double pixelsPerBlock(double distance) {
        return height / (2.0 * tangent()) / Math.max(1.0E-6, distance);
    }

    /** La dirección (unitaria) del rayo que pasa por el punto {@code (screenX, screenY)} de la pantalla. */
    public Vec3 ray(double screenX, double screenY) {
        double normalizedX = screenX / width * 2.0 - 1.0;
        double normalizedY = screenY / height * 2.0 - 1.0;
        double tangent = tangent();
        double aspect = width / height;
        return unit(forward.add(right.multiply(normalizedX * tangent * aspect)).add(up.multiply(-normalizedY * tangent)));
    }

    /**
     * Dónde cae en la pantalla el punto {@code (x, y, z)}: {@code {screenX, screenY, profundidad}}, o {@code null}
     * si está detrás de la cámara.
     */
    public double[] project(double x, double y, double z) {
        Vec3 offset = new Vec3(x - position.x(), y - position.y(), z - position.z());
        double depth = offset.dot(forward);
        if (depth <= 1.0E-6) {
            return null;
        }
        double tangent = tangent();
        double aspect = width / height;
        double normalizedX = offset.dot(right) / (depth * tangent * aspect);
        double normalizedY = -offset.dot(up) / (depth * tangent);
        return new double[]{(normalizedX + 1.0) * 0.5 * width, (normalizedY + 1.0) * 0.5 * height, depth};
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private static Vec3 unit(Vec3 vector) {
        double length = Math.sqrt(vector.dot(vector));
        if (!(length > 1.0E-9)) {
            throw new IllegalArgumentException("Cámara del lazo no válida");
        }
        return vector.multiply(1.0 / length);
    }
}
