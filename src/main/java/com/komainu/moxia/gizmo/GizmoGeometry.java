package com.komainu.moxia.gizmo;

import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se dibuja del gizmo, en el mundo: astas, puntas, anillos y el cubo central, con su color. Lo usa el cliente
 * para dibujarlo y las pruebas para los renders, así los dos enseñan lo mismo. Rojo X, verde Y, azul Z, algo
 * translúcidos para no tapar el objeto; la pieza bajo el punto de mira (o la agarrada) en amarillo opaco y, mientras se
 * arrastra una, las demás muy atenuadas.
 */
public final class GizmoGeometry {
    public static final int RED = 0xFFE5484D;
    public static final int GREEN = 0xFF46B358;
    public static final int BLUE = 0xFF3E8BFF;
    public static final int HIGHLIGHT = 0xFFFFD23F;
    public static final int CENTER = 0xFFF0F0F0;
    private static final int DIMMED_ALPHA = 0x55;
    /** En reposo, algo translúcido para no tapar el objeto; la pieza bajo el puntero, opaca. */
    private static final int IDLE_ALPHA = 0xD8;
    private static final int RING_SEGMENTS = 64;
    private static final int TIP_SEGMENTS = 12;
    private static final float SHAFT = 3.0F;
    private static final float RING = 2.5F;
    private static final float BOLD = 1.6F;

    private GizmoGeometry() {
    }

    /** Un segmento con su color y su grosor en píxeles. */
    public record Line(Vec3 from, Vec3 to, int argb, float width) {
    }

    public record Triangle(Vec3 a, Vec3 b, Vec3 c, int argb) {
    }

    public record Shape(List<Line> lines, List<Triangle> triangles) {
    }

    public static int axisColor(int axis) {
        return switch (axis) {
            case Orientation.X -> RED;
            case Orientation.Y -> GREEN;
            default -> BLUE;
        };
    }

    /**
     * @param hovered la pieza bajo el rayo
     * @param dragged la que se está arrastrando ({@link GizmoHandle#NONE} si ninguna)
     */
    public static Shape build(GizmoLayout layout, GizmoHandle hovered, GizmoHandle dragged) {
        List<Line> lines = new ArrayList<>();
        List<Triangle> triangles = new ArrayList<>();
        Vec3 center = layout.center();
        double size = layout.size();
        for (int axis = 0; axis < 3; axis++) {
            if (!layout.ring(axis)) {
                continue;
            }
            GizmoHandle handle = GizmoHandle.ring(axis);
            int color = color(handle, axisColor(axis), hovered, dragged);
            float width = lit(handle, hovered, dragged) ? RING * BOLD : RING;
            Vec3[] basis = basis(axis);
            double radius = GizmoLayout.RING_RADIUS * size;
            Vec3 previous = null;
            for (int segment = 0; segment <= RING_SEGMENTS; segment++) {
                double angle = 2.0 * Math.PI * segment / RING_SEGMENTS;
                Vec3 point = center.add(basis[0].multiply(Math.cos(angle) * radius))
                        .add(basis[1].multiply(Math.sin(angle) * radius));
                if (previous != null) {
                    lines.add(new Line(previous, point, color, width));
                }
                previous = point;
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            GizmoHandle handle = GizmoHandle.arrow(axis);
            int color = color(handle, axisColor(axis), hovered, dragged);
            float width = lit(handle, hovered, dragged) ? SHAFT * BOLD : SHAFT;
            Vec3 direction = GizmoMath.unit(axis);
            Vec3 start = center.add(direction.multiply(GizmoLayout.ARROW_START * size));
            Vec3 base = center.add(direction.multiply(GizmoLayout.TIP_START * size));
            Vec3 tip = center.add(direction.multiply(size));
            lines.add(new Line(start, base, color, width));
            Vec3[] basis = basis(axis);
            double radius = GizmoLayout.TIP_RADIUS * size * (lit(handle, hovered, dragged) ? 1.25 : 1.0);
            for (int segment = 0; segment < TIP_SEGMENTS; segment++) {
                double first = 2.0 * Math.PI * segment / TIP_SEGMENTS;
                double second = 2.0 * Math.PI * (segment + 1) / TIP_SEGMENTS;
                Vec3 a = base.add(basis[0].multiply(Math.cos(first) * radius))
                        .add(basis[1].multiply(Math.sin(first) * radius));
                Vec3 b = base.add(basis[0].multiply(Math.cos(second) * radius))
                        .add(basis[1].multiply(Math.sin(second) * radius));
                triangles.add(new Triangle(a, b, tip, color));
                triangles.add(new Triangle(base, b, a, shade(color, 0.7)));
            }
        }
        int cube = color(GizmoHandle.MOVE_PLANE, CENTER, hovered, dragged);
        double half = GizmoLayout.CUBE * size * (lit(GizmoHandle.MOVE_PLANE, hovered, dragged) ? 1.2 : 1.0);
        cube(triangles, center, half, cube);
        return new Shape(List.copyOf(lines), List.copyOf(triangles));
    }

    private static boolean lit(GizmoHandle handle, GizmoHandle hovered, GizmoHandle dragged) {
        return dragged == handle || dragged == GizmoHandle.NONE && hovered == handle;
    }

    private static int color(GizmoHandle handle, int base, GizmoHandle hovered, GizmoHandle dragged) {
        if (lit(handle, hovered, dragged)) {
            return HIGHLIGHT;
        }
        if (dragged != GizmoHandle.NONE) {
            return DIMMED_ALPHA << 24 | base & 0xFFFFFF;
        }
        return IDLE_ALPHA << 24 | base & 0xFFFFFF;
    }

    /** Dos vectores unidad perpendiculares al eje (y entre sí). */
    private static Vec3[] basis(int axis) {
        return switch (axis) {
            case Orientation.X -> new Vec3[]{new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
            case Orientation.Y -> new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
            default -> new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 1, 0)};
        };
    }

    private static void cube(List<Triangle> triangles, Vec3 center, double half, int color) {
        double[][] corners = new double[8][];
        for (int index = 0; index < 8; index++) {
            corners[index] = new double[]{center.x() + ((index & 1) == 0 ? -half : half),
                    center.y() + ((index & 2) == 0 ? -half : half), center.z() + ((index & 4) == 0 ? -half : half)};
        }
        // Caras: oeste, este, abajo, arriba, norte, sur (por esquinas), con el brillo de Minecraft.
        int[][] faces = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
        double[] shades = {0.6, 0.6, 0.5, 1.0, 0.8, 0.8};
        for (int face = 0; face < 6; face++) {
            int[] quad = faces[face];
            int shaded = shade(color, shades[face]);
            Vec3 a = vector(corners[quad[0]]);
            Vec3 b = vector(corners[quad[1]]);
            Vec3 c = vector(corners[quad[2]]);
            Vec3 d = vector(corners[quad[3]]);
            triangles.add(new Triangle(a, b, c, shaded));
            triangles.add(new Triangle(a, c, d, shaded));
        }
    }

    private static Vec3 vector(double[] values) {
        return new Vec3(values[0], values[1], values[2]);
    }

    /** El color con su brillo multiplicado (el alfa se queda). */
    public static int shade(int argb, double factor) {
        int red = (int) Math.round((argb >> 16 & 0xFF) * factor);
        int green = (int) Math.round((argb >> 8 & 0xFF) * factor);
        int blue = (int) Math.round((argb & 0xFF) * factor);
        return argb & 0xFF000000 | Math.min(255, red) << 16 | Math.min(255, green) << 8 | Math.min(255, blue);
    }
}
