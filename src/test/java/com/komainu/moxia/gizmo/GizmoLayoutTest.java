package com.komainu.moxia.gizmo;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GizmoLayoutTest {
    private static final Vec3 CENTER = new Vec3(0.5, 64.5, 0.5);
    private static final GizmoLayout LAYOUT = new GizmoLayout(CENTER, 4.0, true, true, true);

    /** Un rayo desde {@code eye} hacia {@code target}. */
    private static GizmoMath.Ray toward(Vec3 eye, Vec3 target) {
        return new GizmoMath.Ray(eye, target.subtract(eye));
    }

    @Test
    void picksTheCubeTheArrowsAndTheRings() {
        Vec3 eye = new Vec3(8, 72, 12);
        assertEquals(GizmoHandle.MOVE_PLANE, LAYOUT.pick(toward(eye, CENTER)));
        assertEquals(GizmoHandle.MOVE_X, LAYOUT.pick(toward(eye, CENTER.add(new Vec3(2.0, 0, 0)))));
        assertEquals(GizmoHandle.MOVE_Y, LAYOUT.pick(toward(eye, CENTER.add(new Vec3(0, 2.5, 0)))));
        assertEquals(GizmoHandle.MOVE_Z, LAYOUT.pick(toward(eye, CENTER.add(new Vec3(0, 0, 1.8)))));
        // La punta, algo más ancha que el asta.
        assertEquals(GizmoHandle.MOVE_X, LAYOUT.pick(toward(eye, CENTER.add(new Vec3(3.6, 0.45, 0)))));
        double radius = GizmoLayout.RING_RADIUS * 4.0;
        double diagonal = radius / Math.sqrt(2.0);
        assertEquals(GizmoHandle.RING_Y, LAYOUT.pick(toward(eye, CENTER.add(new Vec3(-diagonal, 0, -diagonal)))));
        assertEquals(GizmoHandle.RING_X, LAYOUT.pick(toward(eye, CENTER.add(new Vec3(0, diagonal, -diagonal)))));
        assertEquals(GizmoHandle.RING_Z, LAYOUT.pick(toward(eye, CENTER.add(new Vec3(-diagonal, -diagonal, 0)))));
        assertEquals(GizmoHandle.NONE, LAYOUT.pick(toward(eye, CENTER.add(new Vec3(6, 6, 6)))));
    }

    @Test
    void ringsThatTheObjectCannotUseAreNotThere() {
        GizmoLayout upright = new GizmoLayout(CENTER, 4.0, false, true, false);
        Vec3 eye = new Vec3(8, 72, 12);
        double diagonal = GizmoLayout.RING_RADIUS * 4.0 / Math.sqrt(2.0);
        assertEquals(GizmoHandle.NONE, upright.pick(toward(eye, CENTER.add(new Vec3(0, diagonal, -diagonal)))));
        assertEquals(GizmoHandle.RING_Y, upright.pick(toward(eye, CENTER.add(new Vec3(-diagonal, 0, -diagonal)))));
    }

    @Test
    void theSameScreenPointPicksTheSameHandleNearAndFar() {
        Vec3 direction = new Vec3(-0.5, -0.45, -0.75).normalized();
        for (double distance : new double[]{6.0, 24.0, 120.0}) {
            Vec3 eye = CENTER.subtract(direction.multiply(distance));
            GizmoLayout layout = GizmoLayout.around(0, 64, 0, eye, 70.0, true);
            assertEquals(layout.size() / distance, GizmoMath.screenSize(1.0, 70.0, GizmoLayout.SCREEN_FRACTION),
                    1.0E-9);
            // Apuntar a la mitad de la flecha X da la flecha X, a cualquier distancia.
            Vec3 middle = layout.center().add(new Vec3(layout.size() * 0.5, 0, 0));
            assertEquals(GizmoHandle.MOVE_X, layout.pick(toward(eye, middle)), "a " + distance + " bloques");
            assertTrue(layout.size() > 0.0);
        }
    }
}
