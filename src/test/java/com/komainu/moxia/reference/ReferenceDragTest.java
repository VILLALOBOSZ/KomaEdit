package com.komainu.moxia.reference;

import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.GizmoHandle;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.gizmo.Orientation;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import static com.komainu.moxia.reference.ReferenceTransformTest.assertVector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mover, girar y escalar una referencia con el gizmo, con rayos a mano. */
class ReferenceDragTest {
    /** Una imagen de 32×16 píxeles a un píxel por bloque, de pie y de cara al sur. */
    private static final WorldFrame FRAME = new WorldFrame(new Vec3(0.5, 70.0, 0.5), ReferenceRotation.IDENTITY,
            1.0, false);

    private static GizmoMath.Ray down(double x, double z) {
        return new GizmoMath.Ray(new Vec3(x, 100.0, z), new Vec3(0, -1, 0));
    }

    private static GizmoMath.Ray toward(Vec3 eye, Vec3 target) {
        return new GizmoMath.Ray(eye, target.subtract(eye));
    }

    @Test
    void arrowsMoveWholeBlocksOrSixteenthsWithControl() {
        ReferenceDrag drag = ReferenceDrag.begin(ReferenceGizmo.Handle.of(GizmoHandle.MOVE_X), down(1.5, 0.5),
                FRAME, 32, 16);
        assertNotNull(drag);
        // El rayo baja 2,4 bloques más al este de donde se agarró.
        assertVector(new Vec3(2.5, 70.0, 0.5), drag.drag(down(3.9, 0.5), false).center());
        assertVector(new Vec3(2.875, 70.0, 0.5), drag.drag(down(3.9, 0.5), true).center());
        // Mirando a lo largo de la flecha no se puede arrastrar por ella.
        assertNull(ReferenceDrag.begin(ReferenceGizmo.Handle.of(GizmoHandle.MOVE_X),
                new GizmoMath.Ray(new Vec3(-10, 70, 0.5), new Vec3(1, 0, 0)), FRAME, 32, 16));
    }

    @Test
    void theCubeMovesTheImageAlongItsOwnPlane() {
        Vec3 eye = new Vec3(0.5, 70.0, 12.0);
        ReferenceDrag drag = ReferenceDrag.begin(ReferenceGizmo.Handle.of(GizmoHandle.MOVE_PLANE),
                toward(eye, FRAME.center()), FRAME, 32, 16);
        assertNotNull(drag);
        WorldFrame moved = drag.drag(toward(eye, FRAME.center().add(new Vec3(3.3, 1.6, 0))), false);
        assertVector(new Vec3(3.5, 72.0, 0.5), moved.center());
        // Mirando casi de canto, el cubo la lleva por el suelo.
        Vec3 side = new Vec3(20.0, 75.0, 0.6);
        ReferenceDrag flat = ReferenceDrag.begin(ReferenceGizmo.Handle.of(GizmoHandle.MOVE_PLANE),
                toward(side, FRAME.center()), FRAME, 32, 16);
        assertNotNull(flat);
        WorldFrame slid = flat.drag(toward(side, FRAME.center().add(new Vec3(-4.2, 0, 2.1))), false);
        assertEquals(70.0, slid.center().y(), 1.0E-9);
        assertEquals(-3.5, slid.center().x(), 1.0E-9);
        assertEquals(2.5, slid.center().z(), 1.0E-9);
    }

    @Test
    void ringsFollowTheMouseInFifteenDegreeSteps() {
        // Anillo Y desde arriba: se agarra al este del centro y se lleva al sur. Lo agarrado acompaña al ratón.
        ReferenceDrag drag = ReferenceDrag.begin(ReferenceGizmo.Handle.of(GizmoHandle.RING_Y),
                down(FRAME.center().x() + 1.0, FRAME.center().z()), FRAME, 32, 16);
        assertNotNull(drag);
        WorldFrame turned = drag.drag(down(FRAME.center().x(), FRAME.center().z() + 1.0), false);
        assertVector(new Vec3(0, 0, 1), turned.rotation().apply(new Vec3(1, 0, 0)));
        // A 20° se queda en 15°; con Ctrl, en 20°.
        ReferenceDrag again = ReferenceDrag.begin(ReferenceGizmo.Handle.of(GizmoHandle.RING_Y),
                down(FRAME.center().x() + 1.0, FRAME.center().z()), FRAME, 32, 16);
        double radians = Math.toRadians(20.0);
        GizmoMath.Ray twenty = down(FRAME.center().x() + Math.cos(radians), FRAME.center().z() + Math.sin(radians));
        Vec3 right = again.drag(twenty, false).rotation().right();
        assertEquals(15.0, Math.toDegrees(Math.atan2(right.z(), right.x())), 1.0E-6);
        right = again.drag(twenty, true).rotation().right();
        assertEquals(20.0, Math.toDegrees(Math.atan2(right.z(), right.x())), 1.0E-6);
    }

    @Test
    void ringsAddUpPastHalfATurn() {
        ReferenceDrag drag = ReferenceDrag.begin(ReferenceGizmo.Handle.of(GizmoHandle.RING_Y),
                down(FRAME.center().x() + 1.0, FRAME.center().z()), FRAME, 32, 16);
        WorldFrame frame = null;
        // Vuelta y cuarto en pasos pequeños: no salta al pasar por 180°.
        for (int step = 1; step <= 45; step++) {
            double radians = Math.toRadians(step * 10.0);
            frame = drag.drag(down(FRAME.center().x() + Math.cos(radians), FRAME.center().z() + Math.sin(radians)),
                    true);
        }
        assertNotNull(frame);
        assertTrue(frame.rotation().sameAs(ReferenceRotation.IDENTITY.turned(Orientation.Y, -450.0), 1.0E-9));
    }

    @Test
    void cornersScaleToWholeBlocksAroundTheCenter() {
        Vec3 eye = new Vec3(0.5, 70.0, 30.0);
        Vec3 corner = FRAME.corners(32, 16)[1];
        ReferenceDrag drag = ReferenceDrag.begin(ReferenceGizmo.Handle.corner(1), toward(eye, corner), FRAME, 32, 16);
        assertNotNull(drag);
        // La esquina de arriba a la derecha, la mitad más lejos del centro: la imagen pasa de 32 a 48 bloques.
        Vec3 farther = FRAME.center().add(corner.subtract(FRAME.center()).multiply(1.5));
        WorldFrame bigger = drag.drag(toward(eye, farther), false);
        assertEquals(48.0, bigger.widthBlocks(32), 1.0E-9);
        assertVector(FRAME.center(), bigger.center());
        // Un poco más: se queda en bloques enteros; con Ctrl, en dieciseisavos.
        Vec3 bit = FRAME.center().add(corner.subtract(FRAME.center()).multiply(1.51));
        assertEquals(48.0, drag.drag(toward(eye, bit), false).widthBlocks(32), 1.0E-9);
        assertEquals(48.3125, drag.drag(toward(eye, bit), true).widthBlocks(32), 1.0E-9);
        // Hacia el centro y más allá: nunca baja de un bloque.
        WorldFrame squashed = drag.drag(toward(eye, FRAME.center().subtract(new Vec3(5, 5, 0))), false);
        assertEquals(1.0, squashed.widthBlocks(32), 1.0E-9);
    }

    @Test
    void snappingRules() {
        assertEquals(2.0, ReferenceDrag.snap(2.4, false), 1.0E-12);
        assertEquals(-2.0, ReferenceDrag.snap(-2.4, false), 1.0E-12);
        assertEquals(3.0, ReferenceDrag.snap(2.5, false), 1.0E-12);
        assertEquals(2.375, ReferenceDrag.snap(2.4, true), 1.0E-12);
        assertNull(ReferenceDrag.begin(ReferenceGizmo.Handle.NONE, down(0, 0), FRAME, 32, 16));
    }

    @Test
    void pickingFindsTheGizmoFirstAndThenTheCorners() {
        Vec3 eye = new Vec3(0.5, 70.0, 20.0);
        ReferenceGizmo.Layout layout = ReferenceGizmo.layout(FRAME, 32, 16, eye, 70.0);
        assertEquals(GizmoHandle.MOVE_PLANE, layout.pick(toward(eye, FRAME.center())).gizmo());
        Vec3[] corners = FRAME.corners(32, 16);
        for (int index = 0; index < corners.length; index++) {
            ReferenceGizmo.Handle handle = layout.pick(toward(eye, corners[index]));
            assertEquals(index, handle.corner(), "esquina " + index);
        }
        assertTrue(layout.pick(toward(eye, new Vec3(40, 90, 0.5))).none());
        // Las esquinas se ven del mismo tamaño en pantalla: más lejos, más grandes en el mundo.
        assertTrue(layout.cornerHalf()[0] > 0.0);
    }

    @Test
    void theDrawingAddsOutlineAndCorners() {
        ReferenceGizmo.Layout layout = ReferenceGizmo.layout(FRAME, 32, 16, new Vec3(0.5, 70.0, 20.0), 70.0);
        GizmoGeometry.Shape base = GizmoGeometry.build(layout.gizmo(), GizmoHandle.NONE, GizmoHandle.NONE);
        GizmoGeometry.Shape shape = ReferenceGizmo.build(layout, ReferenceGizmo.Handle.corner(2),
                ReferenceGizmo.Handle.NONE);
        assertEquals(base.lines().size() + 4, shape.lines().size());
        assertEquals(base.triangles().size() + 4 * 12, shape.triangles().size());
        // La esquina bajo el puntero, en amarillo; las otras, blancas.
        int lit = shape.triangles().get(base.triangles().size() + 2 * 12 + 6).argb();
        int idle = shape.triangles().get(base.triangles().size() + 6).argb();
        assertEquals(GizmoGeometry.HIGHLIGHT & 0xFF000000, lit & 0xFF000000);
        assertEquals(GizmoGeometry.shade(GizmoGeometry.HIGHLIGHT, 1.0), lit);
        assertEquals(GizmoGeometry.shade(GizmoGeometry.CENTER, 1.0), idle);
        // Los tres anillos siempre (una imagen se puede tumbar).
        assertTrue(layout.gizmo().ring(Orientation.X) && layout.gizmo().ring(Orientation.Z));
    }
}
