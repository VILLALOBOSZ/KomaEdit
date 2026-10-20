package com.komainu.moxia.symmetry;

import java.util.ArrayList;
import java.util.List;

/**
 * Marcas de partículas que enseñan la simetría alrededor del jugador sin el mod en el cliente (con él, los planos los
 * dibuja el cliente): en cada plano de espejo, un marco con una cruz que pasa por el jugador (rojo el de X, azul el de
 * Z, verde el de Y), en lugar de una rejilla entera; el eje vertical en amarillo y, en el radial, un rayo por brazo con
 * el primero en verde. Lógica pura: devuelve dónde y de qué color; quién las dibuja decide el resto.
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
            for (int[] point : frame()) {
                marks.add(new Mark(center.x(), baseY + point[1], baseZ + point[0], PLANE_X));
            }
        }
        if (mode.mirrorZ() && Math.abs(pz - center.z()) <= MAX_DISTANCE) {
            for (int[] point : frame()) {
                marks.add(new Mark(baseX + point[0], baseY + point[1], center.z(), PLANE_Z));
            }
        }
        if (mode.mirrorY() && Math.abs(py - center.y()) <= MAX_DISTANCE) {
            for (int[] point : frame()) {
                marks.add(new Mark(baseX + point[0], center.y(), baseZ + point[1], PLANE_Y));
            }
        }
        boolean axis = mode.radial() || mode.mirrorX() && mode.mirrorZ();
        if (axis && Math.hypot(px - center.x(), pz - center.z()) <= MAX_DISTANCE) {
            for (int step = -RADIUS; step <= RADIUS; step++) {
                marks.add(new Mark(center.x(), baseY + step, center.z(), AXIS));
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

    /**
     * Puntos del marco de un plano, en sus dos ejes: el borde del cuadrado de lado {@code 2·RADIUS} y la cruz por el
     * centro (donde está el jugador), a saltos de {@link #SPACING}. 45 puntos en vez de los 81 de la rejilla.
     */
    static List<int[]> frame() {
        List<int[]> points = new ArrayList<>();
        for (int u = -RADIUS; u <= RADIUS; u += SPACING) {
            for (int v = -RADIUS; v <= RADIUS; v += SPACING) {
                if (Math.abs(u) == RADIUS || Math.abs(v) == RADIUS || u == 0 || v == 0) {
                    points.add(new int[]{u, v});
                }
            }
        }
        return points;
    }
}
