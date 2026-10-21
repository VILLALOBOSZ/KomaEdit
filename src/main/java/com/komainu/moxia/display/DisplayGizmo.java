package com.komainu.moxia.display;

import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.GizmoLayout;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * El gizmo del editor de displays: como el del fantasma ({@link GizmoLayout}), pero en un marco que puede estar girado
 * (modo local: los ejes del objeto) y con tres herramientas. Flechas para mover, anillos para girar y cubos para
 * escalar, rojo X, verde Y, azul Z; el cubo del centro mueve por el plano del suelo (o escala por igual). Lo que se
 * dibuja sale de aquí ({@link #shape}) en el cliente y en los renders de las pruebas.
 *
 * @param center centro del gizmo (el del objeto activo)
 * @param size   largo de un brazo, en bloques (lo calcula {@link GizmoMath#screenSize})
 * @param frame  giro del marco: la identidad en modo global, el del objeto en local (al escalar, siempre el suyo)
 */
public record DisplayGizmo(Vec3 center, double size, Quat frame, Tool tool) {
    private static final int RING_SEGMENTS = 72;
    private static final int TIP_SEGMENTS = 12;
    private static final float SHAFT = 3.0F;
    private static final float RING = 2.5F;
    private static final float BOLD = 1.6F;
    private static final int DIMMED_ALPHA = 0x55;
    private static final int IDLE_ALPHA = 0xD8;
    /** Lado del cubo del extremo de cada brazo al escalar. */
    public static final double SCALE_CUBE = 0.07;

    public enum Tool {
        MOVE("move", "mover"),
        ROTATE("rotate", "girar"),
        SCALE("scale", "escalar");

        private final String id;
        private final String spanish;

        Tool(String id, String spanish) {
            this.id = id;
            this.spanish = spanish;
        }

        public String id() {
            return id;
        }

        public String spanish() {
            return spanish;
        }

        public Tool next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Tool parse(String text) {
            String value = text == null ? "" : text.strip().toLowerCase(java.util.Locale.ROOT);
            for (Tool tool : values()) {
                if (tool.id.equals(value) || tool.spanish.equals(value)) {
                    return tool;
                }
            }
            throw new IllegalArgumentException("Herramienta desconocida: " + text + " (mover, girar o escalar)");
        }
    }

    /** Las piezas que se agarran: un eje (flecha, anillo o cubo según la herramienta) o el centro. */
    public enum Handle {
        NONE(-1),
        X(0),
        Y(1),
        Z(2),
        CENTER(-1);

        private final int axis;

        Handle(int axis) {
            this.axis = axis;
        }

        public int axis() {
            return axis;
        }

        public static Handle of(int axis) {
            return switch (axis) {
                case 0 -> X;
                case 1 -> Y;
                default -> Z;
            };
        }
    }

    public DisplayGizmo {
        if (center == null || frame == null || tool == null || !(size > 0.0)) {
            throw new IllegalArgumentException("El gizmo necesita centro, tamaño, marco y herramienta");
        }
        frame = frame.normalized();
    }

    /** El gizmo en {@code center}, visto desde {@code camera}: ocupa siempre lo mismo en pantalla. */
    public static DisplayGizmo around(Vec3 center, Vec3 camera, double fovDegrees, Quat frame, Tool tool) {
        double size = GizmoMath.screenSize(center.distance(camera), fovDegrees, GizmoLayout.SCREEN_FRACTION);
        return new DisplayGizmo(center, size, frame, tool);
    }

    /** El eje {@code axis} del marco, en el mundo. */
    public Vec3 axis(int axis) {
        return frame.axisVector(axis);
    }

    // ---------------------------------------------------------------- elegir

    /** La pieza bajo el rayo, o {@link Handle#NONE}. Gana el centro; después, la que quede más cerca del rayo. */
    public Handle pick(GizmoMath.Ray ray) {
        double half = (GizmoLayout.CUBE + GizmoLayout.PICK * 0.5) * size;
        if (sphere(ray, center, half * 1.2)) {
            return Handle.CENTER;
        }
        Handle best = Handle.NONE;
        double bestScore = Double.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            Vec3 direction = axis(axis);
            if (tool == Tool.ROTATE) {
                double distance = plane(ray, center, direction);
                if (Double.isNaN(distance)) {
                    continue;
                }
                double off = Math.abs(ray.at(distance).distance(center) - GizmoLayout.RING_RADIUS * size);
                if (off <= GizmoLayout.PICK * size && off / size < bestScore) {
                    best = Handle.of(axis);
                    bestScore = off / size;
                }
                continue;
            }
            LineHit hit = line(ray, center, direction);
            if (hit == null || hit.rayDistance() <= 0.0 || hit.along() < GizmoLayout.ARROW_START * size
                    || hit.along() > (1.0 + GizmoLayout.PICK) * size) {
                continue;
            }
            double tip = tool == Tool.SCALE ? SCALE_CUBE : GizmoLayout.TIP_RADIUS;
            double tolerance = (hit.along() >= GizmoLayout.TIP_START * size ? tip + GizmoLayout.PICK
                    : GizmoLayout.PICK) * size;
            if (hit.separation() <= tolerance && hit.separation() / size < bestScore) {
                best = Handle.of(axis);
                bestScore = hit.separation() / size;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- dibujo

    /**
     * Lo que se dibuja: la pieza bajo el puntero (o la agarrada) en amarillo y, mientras se arrastra una, las demás
     * atenuadas.
     */
    public GizmoGeometry.Shape shape(Handle hovered, Handle dragged) {
        List<GizmoGeometry.Line> lines = new ArrayList<>();
        List<GizmoGeometry.Triangle> triangles = new ArrayList<>();
        for (int axis = 0; axis < 3; axis++) {
            Handle handle = Handle.of(axis);
            boolean lit = lit(handle, hovered, dragged);
            int color = color(handle, GizmoGeometry.axisColor(axis), hovered, dragged);
            Vec3 direction = axis(axis);
            Vec3[] basis = basis(axis);
            if (tool == Tool.ROTATE) {
                ring(lines, basis, GizmoLayout.RING_RADIUS * size, color, lit ? RING * BOLD : RING);
                continue;
            }
            Vec3 start = center.add(direction.multiply(GizmoLayout.ARROW_START * size));
            Vec3 base = center.add(direction.multiply(GizmoLayout.TIP_START * size));
            Vec3 tip = center.add(direction.multiply(size));
            float width = lit ? SHAFT * BOLD : SHAFT;
            if (tool == Tool.SCALE) {
                lines.add(new GizmoGeometry.Line(start, tip, color, width));
                cube(triangles, tip, SCALE_CUBE * size * (lit ? 1.25 : 1.0), color);
                continue;
            }
            lines.add(new GizmoGeometry.Line(start, base, color, width));
            double radius = GizmoLayout.TIP_RADIUS * size * (lit ? 1.25 : 1.0);
            for (int segment = 0; segment < TIP_SEGMENTS; segment++) {
                double first = 2.0 * Math.PI * segment / TIP_SEGMENTS;
                double second = 2.0 * Math.PI * (segment + 1) / TIP_SEGMENTS;
                Vec3 a = base.add(basis[0].multiply(Math.cos(first) * radius))
                        .add(basis[1].multiply(Math.sin(first) * radius));
                Vec3 b = base.add(basis[0].multiply(Math.cos(second) * radius))
                        .add(basis[1].multiply(Math.sin(second) * radius));
                triangles.add(new GizmoGeometry.Triangle(a, b, tip, color));
                triangles.add(new GizmoGeometry.Triangle(base, b, a, GizmoGeometry.shade(color, 0.7)));
            }
        }
        int middle = color(Handle.CENTER, GizmoGeometry.CENTER, hovered, dragged);
        double half = GizmoLayout.CUBE * size * (lit(Handle.CENTER, hovered, dragged) ? 1.2 : 1.0);
        cube(triangles, center, half, middle);
        return new GizmoGeometry.Shape(List.copyOf(lines), List.copyOf(triangles));
    }

    /** Las doce aristas de un volumen ({@link DisplayVolume#world}): el contorno de un display elegido o señalado. */
    public static List<GizmoGeometry.Line> outline(Affine unitToWorld, int argb, float width) {
        Vec3[] corners = unitToWorld.unitCorners();
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6},
                {3, 7}};
        List<GizmoGeometry.Line> lines = new ArrayList<>(edges.length);
        for (int[] edge : edges) {
            lines.add(new GizmoGeometry.Line(corners[edge[0]], corners[edge[1]], argb, width));
        }
        return lines;
    }

    /** Un anillo de ángulo (lo girado mientras se arrastra): del ángulo de partida al de ahora, en el anillo. */
    public List<GizmoGeometry.Line> arc(int axis, double fromRadians, double toRadians, int argb) {
        List<GizmoGeometry.Line> lines = new ArrayList<>();
        Vec3[] ring = ringBasis(axis);
        double radius = GizmoLayout.RING_RADIUS * size * 0.92;
        int segments = Math.max(2, (int) Math.ceil(Math.abs(toRadians - fromRadians) / (Math.PI / 36.0)));
        Vec3 previous = null;
        for (int segment = 0; segment <= segments; segment++) {
            double angle = fromRadians + (toRadians - fromRadians) * segment / segments;
            Vec3 point = center.add(ring[0].multiply(Math.cos(angle) * radius))
                    .add(ring[1].multiply(Math.sin(angle) * radius));
            if (previous != null) {
                lines.add(new GizmoGeometry.Line(previous, point, argb, RING * BOLD));
            }
            previous = point;
        }
        lines.add(new GizmoGeometry.Line(center, center.add(ring[0].multiply(Math.cos(fromRadians) * radius))
                .add(ring[1].multiply(Math.sin(fromRadians) * radius)), argb, 1.5F));
        lines.add(new GizmoGeometry.Line(center, previous, argb, 1.5F));
        return lines;
    }

    // ---------------------------------------------------------------- geometría

    /**
     * Ángulo de {@code point} en el plano del anillo {@code axis}, medido de forma que crece al girar en positivo
     * alrededor del eje (regla de la mano derecha): en Y, de Z hacia X.
     */
    public double angle(Vec3 point, int axis) {
        Vec3[] ring = ringBasis(axis);
        Vec3 offset = point.subtract(center);
        return Math.atan2(offset.dot(ring[1]), offset.dot(ring[0]));
    }

    /** Los dos ejes del plano del anillo en el orden del giro positivo: (Y, Z) para X, (Z, X) para Y, (X, Y) para Z. */
    private Vec3[] ringBasis(int axis) {
        return new Vec3[]{axis((axis + 1) % 3), axis((axis + 2) % 3)};
    }

    private Vec3[] basis(int axis) {
        return ringBasis(axis);
    }

    /** Punto de la recta más cercano al rayo. */
    public record LineHit(double along, double rayDistance, double separation) {
    }

    /** Rayo contra la recta de {@code origin} en {@code direction} (unitaria); nulo si van paralelos. */
    public static LineHit line(GizmoMath.Ray ray, Vec3 origin, Vec3 direction) {
        Vec3 offset = ray.origin().subtract(origin);
        double parallel = ray.direction().dot(direction);
        double denominator = 1.0 - parallel * parallel;
        if (denominator < 1.0E-6) {
            return null;
        }
        double rayOffset = ray.direction().dot(offset);
        double axisOffset = direction.dot(offset);
        double rayDistance = (parallel * axisOffset - rayOffset) / denominator;
        double along = (axisOffset - parallel * rayOffset) / denominator;
        Vec3 onRay = ray.at(rayDistance);
        Vec3 onAxis = origin.add(direction.multiply(along));
        return new LineHit(along, rayDistance, onRay.distance(onAxis));
    }

    /** Distancia a la que el rayo corta el plano, o {@code NaN} si va paralelo o queda detrás. */
    public static double plane(GizmoMath.Ray ray, Vec3 point, Vec3 normal) {
        return GizmoMath.plane(ray, point, normal);
    }

    private static boolean sphere(GizmoMath.Ray ray, Vec3 center, double radius) {
        Vec3 offset = center.subtract(ray.origin());
        double along = offset.dot(ray.direction());
        if (along < 0.0) {
            return false;
        }
        return ray.at(along).distance(center) <= radius;
    }

    private void ring(List<GizmoGeometry.Line> lines, Vec3[] basis, double radius, int color, float width) {
        Vec3 previous = null;
        for (int segment = 0; segment <= RING_SEGMENTS; segment++) {
            double angle = 2.0 * Math.PI * segment / RING_SEGMENTS;
            Vec3 point = center.add(basis[0].multiply(Math.cos(angle) * radius))
                    .add(basis[1].multiply(Math.sin(angle) * radius));
            if (previous != null) {
                lines.add(new GizmoGeometry.Line(previous, point, color, width));
            }
            previous = point;
        }
    }

    /** Un cubo con los ejes del marco y el brillo de Minecraft en cada cara. */
    private void cube(List<GizmoGeometry.Triangle> triangles, Vec3 middle, double half, int color) {
        Vec3 ax = axis(0).multiply(half);
        Vec3 ay = axis(1).multiply(half);
        Vec3 az = axis(2).multiply(half);
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < 8; index++) {
            corners[index] = middle.add(ax.multiply((index & 1) == 0 ? -1 : 1))
                    .add(ay.multiply((index & 2) == 0 ? -1 : 1)).add(az.multiply((index & 4) == 0 ? -1 : 1));
        }
        int[][] faces = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
        double[] shades = {0.6, 0.6, 0.5, 1.0, 0.8, 0.8};
        for (int face = 0; face < 6; face++) {
            int[] quad = faces[face];
            int shaded = GizmoGeometry.shade(color, shades[face]);
            triangles.add(new GizmoGeometry.Triangle(corners[quad[0]], corners[quad[1]], corners[quad[2]], shaded));
            triangles.add(new GizmoGeometry.Triangle(corners[quad[0]], corners[quad[2]], corners[quad[3]], shaded));
        }
    }

    private static boolean lit(Handle handle, Handle hovered, Handle dragged) {
        return dragged == handle || dragged == Handle.NONE && hovered == handle;
    }

    private static int color(Handle handle, int base, Handle hovered, Handle dragged) {
        if (lit(handle, hovered, dragged)) {
            return GizmoGeometry.HIGHLIGHT;
        }
        if (dragged != Handle.NONE) {
            return DIMMED_ALPHA << 24 | base & 0xFFFFFF;
        }
        return IDLE_ALPHA << 24 | base & 0xFFFFFF;
    }
}
