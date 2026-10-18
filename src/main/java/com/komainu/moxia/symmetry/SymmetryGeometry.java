package com.komainu.moxia.symmetry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dónde caen las copias de un bloque. Lógica pura: los espejos y los cuartos de vuelta son aritmética entera sobre
 * el centro en medios bloques ({@link SymmetryCenter}); el resto de ángulos del radial gira el centro del bloque y se
 * queda con el bloque que lo contiene.
 */
public final class SymmetryGeometry {
    /** Margen para que un centro que cae justo en un borde (60° de un bloque a 1 del eje) no dependa del redondeo. */
    private static final double EPSILON = 1.0E-7;

    private SymmetryGeometry() {
    }

    public record Point(int x, int y, int z) {
    }

    /** Una copia de un grupo de bloques (uno, o las dos mitades de una puerta): la transformación y dónde cae cada uno. */
    public record Copy(SymmetryTransform transform, List<Point> points) {
        public Copy {
            points = List.copyOf(points);
        }
    }

    /** Todas las transformaciones del modo, la identidad primero. */
    public static List<SymmetryTransform> transforms(SymmetryMode mode) {
        List<SymmetryTransform> transforms = new ArrayList<>();
        for (int vertical = 0; vertical < (mode.mirrorY() ? 2 : 1); vertical++) {
            boolean y = vertical == 1;
            if (mode.radial()) {
                for (int turn = 0; turn < mode.arms(); turn++) {
                    transforms.add(new SymmetryTransform(false, false, y, turn, mode.arms()));
                }
                continue;
            }
            for (int xz = 0; xz < 4; xz++) {
                boolean x = (xz & 1) != 0;
                boolean z = (xz & 2) != 0;
                if (x && !mode.mirrorX() || z && !mode.mirrorZ()) {
                    continue;
                }
                transforms.add(new SymmetryTransform(x, z, y, 0, 1));
            }
        }
        return List.copyOf(transforms);
    }

    /** Lleva el bloque {@code (x, y, z)} con la transformación alrededor del centro. */
    public static Point apply(SymmetryTransform transform, SymmetryCenter center, int x, int y, int z) {
        int newY = transform.mirrorY() ? center.y2() - 1 - y : y;
        // Desplazamiento del centro del bloque respecto al de la simetría, en medios bloques.
        long dx = 2L * x + 1 - center.x2();
        long dz = 2L * z + 1 - center.z2();
        if (transform.mirrorX()) {
            dx = -dx;
        }
        if (transform.mirrorZ()) {
            dz = -dz;
        }
        if (transform.exactQuarter()) {
            for (int step = 0; step < transform.quarterTurns(); step++) {
                long previous = dx;
                dx = -dz;
                dz = previous;
            }
            return new Point(
                    (int) Math.floorDiv(center.x2() + dx - 1, 2),
                    newY,
                    (int) Math.floorDiv(center.z2() + dz - 1, 2)
            );
        }
        double angle = transform.angle();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = dx * cos - dz * sin;
        double rz = dx * sin + dz * cos;
        return new Point(
                (int) Math.floor((center.x2() + rx) / 2.0 + EPSILON),
                newY,
                (int) Math.floor((center.z2() + rz) / 2.0 + EPSILON)
        );
    }

    /**
     * Las copias de un grupo de bloques que se colocan o rompen juntos. Una copia se descarta entera si alguno de sus
     * bloques cae sobre el grupo original o sobre otra copia anterior: un bloque en el propio espejo no se pisa a sí
     * mismo con su estado reflejado.
     */
    public static List<Copy> copies(SymmetryMode mode, SymmetryCenter center, List<Point> parts) {
        Set<Point> taken = new HashSet<>(parts);
        List<Copy> copies = new ArrayList<>();
        for (SymmetryTransform transform : transforms(mode)) {
            if (transform.identity()) {
                continue;
            }
            List<Point> points = new ArrayList<>(parts.size());
            boolean free = true;
            for (Point part : parts) {
                Point point = apply(transform, center, part.x(), part.y(), part.z());
                if (taken.contains(point) || points.contains(point)) {
                    free = false;
                    break;
                }
                points.add(point);
            }
            if (free) {
                taken.addAll(points);
                copies.add(new Copy(transform, points));
            }
        }
        return copies;
    }
}
