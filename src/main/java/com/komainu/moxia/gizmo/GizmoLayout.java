package com.komainu.moxia.gizmo;

import com.komainu.moxia.shape.Vec3;

/**
 * Dónde y de qué tamaño está el gizmo, y qué pieza hay bajo un rayo. Las medidas son fracciones de {@code size}, que
 * el cliente calcula con {@link GizmoMath#screenSize} para que el gizmo ocupe siempre lo mismo en pantalla.
 *
 * <p>Las flechas salen del cubo central hacia el lado positivo de cada eje (rojo X, verde Y, azul Z); los anillos
 * rodean el centro en el plano perpendicular a su eje. Los de X y Z solo están si el objeto admite tumbarse.</p>
 *
 * @param center centro del gizmo (el centro del bloque del pivote)
 * @param size   largo de una flecha, en bloques
 */
public record GizmoLayout(Vec3 center, double size, boolean ringX, boolean ringY, boolean ringZ) {
    /** Donde empieza el asta de cada flecha, fuera del cubo. */
    public static final double ARROW_START = 0.16;
    /** Donde empieza la punta. */
    public static final double TIP_START = 0.78;
    public static final double TIP_RADIUS = 0.075;
    public static final double CUBE = 0.085;
    public static final double RING_RADIUS = 0.62;
    /** Holgura para acertar una pieza con el rayo. */
    public static final double PICK = 0.075;
    /** Parte de la altura de la pantalla que ocupa una flecha. */
    public static final double SCREEN_FRACTION = 0.16;

    public GizmoLayout {
        if (center == null || !(size > 0.0)) {
            throw new IllegalArgumentException("El gizmo necesita centro y tamaño");
        }
    }

    /** El gizmo de un objeto con pivote en el bloque ({@code x}, {@code y}, {@code z}), visto desde {@code camera}. */
    public static GizmoLayout around(int x, int y, int z, Vec3 camera, double fovDegrees, boolean tilts) {
        Vec3 center = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        double size = GizmoMath.screenSize(center.distance(camera), fovDegrees, SCREEN_FRACTION);
        return new GizmoLayout(center, size, tilts, true, tilts);
    }

    public boolean ring(int axis) {
        return switch (axis) {
            case Orientation.X -> ringX;
            case Orientation.Y -> ringY;
            default -> ringZ;
        };
    }

    /** La pieza bajo el rayo, o {@link GizmoHandle#NONE}. Gana el cubo; después, la que quede más cerca del rayo. */
    public GizmoHandle pick(GizmoMath.Ray ray) {
        double half = (CUBE + PICK * 0.5) * size;
        Vec3 extent = new Vec3(half, half, half);
        if (!Double.isNaN(GizmoMath.box(ray, center.subtract(extent), center.add(extent)))) {
            return GizmoHandle.MOVE_PLANE;
        }
        GizmoHandle best = GizmoHandle.NONE;
        double bestScore = Double.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            GizmoMath.AxisHit hit = GizmoMath.axis(ray, center, axis);
            if (hit != null && hit.rayDistance() > 0.0 && hit.along() >= ARROW_START * size
                    && hit.along() <= (1.0 + PICK) * size) {
                double tolerance = (hit.along() >= TIP_START * size ? TIP_RADIUS + PICK : PICK) * size;
                if (hit.separation() <= tolerance && hit.separation() / size < bestScore) {
                    best = GizmoHandle.arrow(axis);
                    bestScore = hit.separation() / size;
                }
            }
            if (!ring(axis)) {
                continue;
            }
            double distance = GizmoMath.plane(ray, center, GizmoMath.unit(axis));
            if (Double.isNaN(distance)) {
                continue;
            }
            double off = Math.abs(ray.at(distance).distance(center) - RING_RADIUS * size);
            if (off <= PICK * size && off / size < bestScore) {
                best = GizmoHandle.ring(axis);
                bestScore = off / size;
            }
        }
        return best;
    }
}
