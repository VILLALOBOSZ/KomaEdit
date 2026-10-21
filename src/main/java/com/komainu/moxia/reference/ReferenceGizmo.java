package com.komainu.moxia.reference;

import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.GizmoHandle;
import com.komainu.moxia.gizmo.GizmoLayout;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * El gizmo de una imagen de referencia en el mundo: el mismo del fantasma (flechas, anillos y cubo,
 * {@link GizmoGeometry}) en el centro de la imagen, con los tres anillos siempre, y además un cuadradito blanco en
 * cada esquina para agrandarla o encogerla, y su contorno. Lógica pura: el cliente lo dibuja y las maquetas también.
 *
 * <p>Diferencias con el fantasma: las flechas y el cubo no saltan de bloque en bloque sino lo que se elija
 * ({@link ReferenceDrag}); el cubo arrastra la imagen por su propio plano; los anillos giran de 15° en 15° (con Ctrl,
 * de grado en grado).</p>
 */
public final class ReferenceGizmo {
    /** Lado de los cuadraditos de las esquinas, en fracción del tamaño del gizmo a esa distancia. */
    public static final double CORNER = 0.1;
    public static final int OUTLINE = 0xF0FFD23F;
    public static final int OUTLINE_IDLE = 0xC0FFFFFF;
    private static final float OUTLINE_WIDTH = 2.0F;

    private ReferenceGizmo() {
    }

    /** Una pieza agarrable: una del gizmo de siempre o una esquina (0 a 3, como {@link WorldFrame#corners}). */
    public record Handle(GizmoHandle gizmo, int corner) {
        public static final Handle NONE = new Handle(GizmoHandle.NONE, -1);

        public Handle {
            gizmo = gizmo == null ? GizmoHandle.NONE : gizmo;
            corner = gizmo == GizmoHandle.NONE ? Math.clamp(corner, -1, 3) : -1;
        }

        public static Handle of(GizmoHandle handle) {
            return new Handle(handle, -1);
        }

        public static Handle corner(int index) {
            return new Handle(GizmoHandle.NONE, index);
        }

        public boolean none() {
            return gizmo == GizmoHandle.NONE && corner < 0;
        }

        public boolean isCorner() {
            return corner >= 0;
        }
    }

    /**
     * Dónde y de qué tamaño está el gizmo de la imagen vista desde {@code camera}, con los cuadraditos de las esquinas
     * medidos a la distancia de cada una (así se ven igual cerca y lejos).
     */
    public record Layout(GizmoLayout gizmo, Vec3[] corners, double[] cornerHalf) {
        public Handle pick(GizmoMath.Ray ray) {
            GizmoHandle handle = gizmo.pick(ray);
            if (handle != GizmoHandle.NONE) {
                return Handle.of(handle);
            }
            int best = -1;
            double nearest = Double.MAX_VALUE;
            for (int index = 0; index < corners.length; index++) {
                double half = cornerHalf[index] * 1.6;
                Vec3 extent = new Vec3(half, half, half);
                double distance = GizmoMath.box(ray, corners[index].subtract(extent), corners[index].add(extent));
                if (!Double.isNaN(distance) && distance < nearest) {
                    nearest = distance;
                    best = index;
                }
            }
            return best < 0 ? Handle.NONE : Handle.corner(best);
        }
    }

    public static Layout layout(WorldFrame frame, int pixelWidth, int pixelHeight, Vec3 camera, double fovDegrees) {
        Vec3 center = frame.center();
        double size = GizmoMath.screenSize(center.distance(camera), fovDegrees, GizmoLayout.SCREEN_FRACTION);
        Vec3[] corners = frame.corners(pixelWidth, pixelHeight);
        double[] half = new double[corners.length];
        for (int index = 0; index < corners.length; index++) {
            half[index] = GizmoMath.screenSize(corners[index].distance(camera), fovDegrees,
                    GizmoLayout.SCREEN_FRACTION) * CORNER * 0.5;
        }
        return new Layout(new GizmoLayout(center, size, true, true, true), corners, half);
    }

    /** Lo que se dibuja: el gizmo, el contorno de la imagen y las esquinas, con la pieza resaltada en amarillo. */
    public static GizmoGeometry.Shape build(Layout layout, Handle hovered, Handle dragged) {
        GizmoGeometry.Shape base = GizmoGeometry.build(layout.gizmo(), hovered.gizmo(), dragged.gizmo());
        List<GizmoGeometry.Line> lines = new ArrayList<>(base.lines());
        List<GizmoGeometry.Triangle> triangles = new ArrayList<>(base.triangles());
        Vec3[] corners = layout.corners();
        boolean scaling = dragged.isCorner();
        int outline = scaling ? OUTLINE : OUTLINE_IDLE;
        for (int index = 0; index < corners.length; index++) {
            lines.add(new GizmoGeometry.Line(corners[index], corners[(index + 1) % corners.length], outline,
                    OUTLINE_WIDTH));
        }
        for (int index = 0; index < corners.length; index++) {
            boolean lit = dragged.corner() == index || dragged.none() && hovered.corner() == index;
            int color = lit ? GizmoGeometry.HIGHLIGHT
                    : !dragged.none() ? 0x55 << 24 | GizmoGeometry.CENTER & 0xFFFFFF : GizmoGeometry.CENTER;
            double half = layout.cornerHalf()[index] * (lit ? 1.3 : 1.0);
            cube(triangles, corners[index], half, color);
        }
        return new GizmoGeometry.Shape(List.copyOf(lines), List.copyOf(triangles));
    }

    private static void cube(List<GizmoGeometry.Triangle> triangles, Vec3 center, double half, int color) {
        Vec3[] corner = new Vec3[8];
        for (int index = 0; index < 8; index++) {
            corner[index] = new Vec3(center.x() + ((index & 1) == 0 ? -half : half),
                    center.y() + ((index & 2) == 0 ? -half : half), center.z() + ((index & 4) == 0 ? -half : half));
        }
        int[][] faces = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
        double[] shades = {0.6, 0.6, 0.5, 1.0, 0.8, 0.8};
        for (int face = 0; face < faces.length; face++) {
            int shaded = GizmoGeometry.shade(color, shades[face]);
            int[] quad = faces[face];
            triangles.add(new GizmoGeometry.Triangle(corner[quad[0]], corner[quad[1]], corner[quad[2]], shaded));
            triangles.add(new GizmoGeometry.Triangle(corner[quad[0]], corner[quad[2]], corner[quad[3]], shaded));
        }
    }
}
