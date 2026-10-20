package com.komainu.moxia.gizmo;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GizmoMathTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void rayAgainstAxisFindsTheClosestPoint() {
        Vec3 origin = new Vec3(10, 64, 10);
        // Mirando hacia abajo, justo encima del eje X a 3 bloques del origen.
        GizmoMath.Ray down = new GizmoMath.Ray(new Vec3(13, 80, 10), new Vec3(0, -1, 0));
        GizmoMath.AxisHit hit = GizmoMath.axis(down, origin, Orientation.X);
        assertNotNull(hit);
        assertEquals(3.0, hit.along(), EPSILON);
        assertEquals(16.0, hit.rayDistance(), EPSILON);
        assertEquals(0.0, hit.separation(), EPSILON);
        // Un bloque al lado del eje: la separación es ese bloque.
        GizmoMath.Ray beside = new GizmoMath.Ray(new Vec3(13, 80, 11), new Vec3(0, -1, 0));
        assertEquals(1.0, GizmoMath.axis(beside, origin, Orientation.X).separation(), EPSILON);
        // Un rayo oblicuo: lo que importa es dónde cae sobre el eje.
        GizmoMath.Ray slanted = new GizmoMath.Ray(new Vec3(0, 74, 20), new Vec3(1, -1, -1));
        assertEquals(0.0, GizmoMath.axis(slanted, origin, Orientation.X).along(), EPSILON);
    }

    @Test
    void lookingAlongAnAxisGivesNoPoint() {
        GizmoMath.Ray along = new GizmoMath.Ray(new Vec3(0, 64, 0), new Vec3(0, 0, 1));
        assertNull(GizmoMath.axis(along, new Vec3(0, 64, 5), Orientation.Z));
    }

    @Test
    void rayAgainstPlaneAndBox() {
        GizmoMath.Ray ray = new GizmoMath.Ray(new Vec3(0, 10, 0), new Vec3(1, -1, 0));
        double distance = GizmoMath.plane(ray, new Vec3(0, 4, 0), new Vec3(0, 1, 0));
        Vec3 point = ray.at(distance);
        assertEquals(6.0, point.x(), EPSILON);
        assertEquals(4.0, point.y(), EPSILON);
        // Detrás o paralelo: no hay corte.
        assertTrue(Double.isNaN(GizmoMath.plane(ray, new Vec3(0, 20, 0), new Vec3(0, 1, 0))));
        assertTrue(Double.isNaN(GizmoMath.plane(new GizmoMath.Ray(new Vec3(0, 10, 0), new Vec3(1, 0, 0)),
                new Vec3(0, 4, 0), new Vec3(0, 1, 0))));
        GizmoMath.Ray toward = new GizmoMath.Ray(new Vec3(-5, 0.5, 0.5), new Vec3(1, 0, 0));
        assertEquals(5.0, GizmoMath.box(toward, new Vec3(0, 0, 0), new Vec3(1, 1, 1)), EPSILON);
        assertTrue(Double.isNaN(GizmoMath.box(toward, new Vec3(0, 2, 0), new Vec3(1, 3, 1))));
    }

    @Test
    void movesInWholeBlocks() {
        assertEquals(0, GizmoMath.steps(0.49));
        assertEquals(1, GizmoMath.steps(0.5));
        assertEquals(3, GizmoMath.steps(3.2));
        assertEquals(0, GizmoMath.steps(-0.5));
        assertEquals(-1, GizmoMath.steps(-0.51));
        assertEquals(-4, GizmoMath.steps(-3.7));
    }

    @Test
    void theGizmoKeepsItsSizeOnScreen() {
        double near = GizmoMath.screenSize(10.0, 70.0, 0.16);
        double far = GizmoMath.screenSize(100.0, 70.0, 0.16);
        assertEquals(10.0, far / near, 1.0E-9);
        // A 10 bloques con 70° la pantalla mide 2·10·tan(35°) de alto: la flecha es el 16 % de eso.
        assertEquals(2.0 * 10.0 * Math.tan(Math.toRadians(35.0)) * 0.16, near, 1.0E-9);
        // Un zoom (menos campo de visión) la encoge en el mundo para que siga igual en pantalla.
        assertTrue(GizmoMath.screenSize(10.0, 30.0, 0.16) < near);
    }

    @Test
    void ringAnglesGrowWithAPositiveQuarterTurn() {
        Vec3 center = new Vec3(0.5, 0.5, 0.5);
        int[][] starts = {{0, 1, 0}, {1, 0, 0}, {1, 0, 0}};
        for (int axis = 0; axis < 3; axis++) {
            int[] start = starts[axis];
            int[] turned = Orientation.turn(axis, 1).apply(start[0], start[1], start[2]);
            double before = GizmoMath.angle(center.add(new Vec3(start[0], start[1], start[2])), center, axis);
            double after = GizmoMath.angle(center.add(new Vec3(turned[0], turned[1], turned[2])), center, axis);
            assertEquals(Math.PI / 2.0, GizmoMath.wrap(after - before), EPSILON, "eje " + axis);
        }
        assertEquals(1, GizmoMath.quarterTurns(Math.toRadians(50)));
        assertEquals(0, GizmoMath.quarterTurns(Math.toRadians(40)));
        assertEquals(-2, GizmoMath.quarterTurns(Math.toRadians(-170)));
        assertEquals(Math.toRadians(-10), GizmoMath.wrap(Math.toRadians(350)), EPSILON);
    }

    @Test
    void forwardIsTheClosestHorizontalDirection() {
        assertArrayEquals(new int[]{0, -1}, GizmoMath.forward(0.1, -0.9));
        assertArrayEquals(new int[]{1, 0}, GizmoMath.forward(0.8, 0.3));
        // Mirando al norte, la izquierda es el oeste.
        assertArrayEquals(new int[]{-1, 0}, GizmoMath.left(new int[]{0, -1}));
        assertArrayEquals(new int[]{0, -1}, GizmoMath.left(new int[]{1, 0}));
        assertEquals(Orientation.Y, GizmoMath.dominantAxis(new Vec3(0.2, -0.9, 0.3)));
    }
}
