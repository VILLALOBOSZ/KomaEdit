package com.komainu.moxia.symmetry;

import java.util.ArrayList;
import java.util.List;

/**
 * Marcas de partículas que enseñan la simetría alrededor del jugador: una rejilla de puntos en cada plano de espejo
 * (rojo el de X, azul el de Z, verde el de Y), el eje vertical en amarillo y, en el radial, un rayo por brazo con el
 * primero en verde. Lógica pura: devuelve dónde y de qué color; quién las dibuja decide el resto.
 */
public final class SymmetryGuides {
    public static final int PLANE_X = 0xEF5350;
    public static final int PLANE_Z = 0x42A5F5;
    public static final int PLANE_Y = 0x66BB6A;
    public static final int AXIS = 0xFFCA28;
    public static final int FIRST_ARM = 0x66BB6A;
    /** Medio lado de la rejilla de cada plano, en bloques. */
    public static final int RADIUS = 8;
    /** Separación entre puntos de la rejilla. */
    public static final int SPACING = 2;
    /** Un plano o eje más lejos que esto del jugador no se dibuja. */
    public static final double MAX_DISTANCE = 48.0;
    /** Tope de marcas por refresco (tres planos, eje y 16 brazos caben con holgura). */
    public static final int MAX_MARKS = 400;

    private SymmetryGuides() {
    }

    public record Mark(double x, double y, double z, int color) {
    }

    /** Marcas para un jugador en {@code (px, py, pz)}. */
    public static List<Mark> marks(SymmetryMode mode, SymmetryCenter center, double px, double py, double pz) {
        List<Mark> marks = new ArrayList<>();
        double baseX = Math.floor(px) + 0.5;
        double baseY = Math.floor(py) + 0.5;
        double baseZ = Math.floor(pz) + 0.5;
        if (mode.mirrorX() && Math.abs(px - center.x()) <= MAX_DISTANCE) {
            for (int u = -RADIUS; u <= RADIUS; u += SPACING) {
                for (int v = -RADIUS; v <= RADIUS; v += SPACING) {
                    marks.add(new Mark(center.x(), baseY + v, baseZ + u, PLANE_X));
                }
            }
        }
        if (mode.mirrorZ() && Math.abs(pz - center.z()) <= MAX_DISTANCE) {
            for (int u = -RADIUS; u <= RADIUS; u += SPACING) {
                for (int v = -RADIUS; v <= RADIUS; v += SPACING) {
                    marks.add(new Mark(baseX + u, baseY + v, center.z(), PLANE_Z));
                }
            }
        }
        if (mode.mirrorY() && Math.abs(py - center.y()) <= MAX_DISTANCE) {
            for (int u = -RADIUS; u <= RADIUS; u += SPACING) {
                for (int v = -RADIUS; v <= RADIUS; v += SPACING) {
                    marks.add(new Mark(baseX + u, center.y(), baseZ + v, PLANE_Y));
                }
            }
        }
        boolean axis = mode.radial() || mode.mirrorX() && mode.mirrorZ();
        if (axis && Math.hypot(px - center.x(), pz - center.z()) <= MAX_DISTANCE) {
            for (int step = -2 * RADIUS; step <= 2 * RADIUS; step++) {
                marks.add(new Mark(center.x(), baseY + step * 0.5, center.z(), AXIS));
            }
            if (mode.radial()) {
                for (int arm = 0; arm < mode.arms(); arm++) {
                    double angle = 2.0 * Math.PI * arm / mode.arms();
                    for (int distance = 1; distance <= RADIUS; distance++) {
                        marks.add(new Mark(center.x() + Math.cos(angle) * distance, baseY,
                                center.z() + Math.sin(angle) * distance, arm == 0 ? FIRST_ARM : AXIS));
                    }
                }
            }
        }
        return marks.size() <= MAX_MARKS ? marks : List.copyOf(marks.subList(0, MAX_MARKS));
    }
}
