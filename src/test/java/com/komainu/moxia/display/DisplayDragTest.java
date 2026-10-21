package com.komainu.moxia.display;

import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.komainu.moxia.display.QuatTest.assertVec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El arrastre del gizmo con rayos a mano: pasos de 1, 1/4 y 1/16, giros de 15° y 5°, escalas y grupos. */
class DisplayDragTest {
    private static final Vec3 CENTER = new Vec3(0.5, 64.5, 0.5);
    private static final DisplaySteps PIXEL = new DisplaySteps(DisplaySteps.Move.SIXTEENTH, DisplaySteps.Angle.DEG15);

    private static DisplayDrag.Item block(int id, Vec3 center, Quat left, Vec3 scale) {
        return new DisplayDrag.Item(id, DisplayPose.centered(center, left, scale, new Vec3(0.5, 0.5, 0.5)),
                DisplayVolume.block());
    }

    private static DisplayGizmo gizmo(Vec3 center, Quat frame, DisplayGizmo.Tool tool) {
        return new DisplayGizmo(center, 1.0, frame, tool);
    }

    /** Un rayo hacia +Z que pasa por (x, y) en el plano de z = centro. */
    private static GizmoMath.Ray towardsZ(double x, double y) {
        return new GizmoMath.Ray(new Vec3(x, y, CENTER.z() - 6), new Vec3(0, 0, 1));
    }

    /** Un rayo hacia abajo que pasa por (x, z). */
    private static GizmoMath.Ray down(double x, double z) {
        return new GizmoMath.Ray(new Vec3(x, CENTER.y() + 6, z), new Vec3(0, -1, 0));
    }

    @Test
    void movingAlongXSnapsTheCornerToTheSixteenthGrid() {
        DisplayDrag.Item item = block(1, CENTER, Quat.IDENTITY, new Vec3(0.5, 0.5, 0.5));
        DisplayGizmo gizmo = gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.MOVE);
        assertEquals(DisplayGizmo.Handle.X, gizmo.pick(towardsZ(CENTER.x() + 0.6, CENTER.y())));
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.X, towardsZ(CENTER.x() + 0.6, CENTER.y()),
                List.of(item), 0, PIXEL, true);
        assertNotNull(drag);
        assertTrue(drag.update(towardsZ(CENTER.x() + 0.6 + 0.30, CENTER.y()), false));
        DisplayDrag.Item moved = drag.current().getFirst();
        // La esquina estaba en x = 0.25; 0.25 + 0.30 = 0.55 → 0.5625 (9/16).
        assertEquals(0.5625, moved.minimum().x(), 1.0E-12);
        assertEquals(CENTER.y(), moved.center().y(), 1.0E-12, "solo se mueve por X");
        assertEquals(CENTER.z(), moved.center().z(), 1.0E-12);
        // Libre (Alt): exactamente lo arrastrado.
        drag.update(towardsZ(CENTER.x() + 0.6 + 0.30, CENTER.y()), true);
        assertEquals(0.55, drag.current().getFirst().minimum().x(), 1.0E-12);
        assertTrue(drag.moved());
    }

    @Test
    void aWholeBlockSnapsToTheBlockGridWithStepOne() {
        DisplayDrag.Item item = block(1, CENTER, Quat.IDENTITY, DisplayPose.ONE);
        DisplaySteps steps = PIXEL.withMove(DisplaySteps.Move.ONE);
        DisplayDrag drag = DisplayDrag.begin(gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.MOVE),
                DisplayGizmo.Handle.Y, new GizmoMath.Ray(new Vec3(CENTER.x() - 6, CENTER.y() + 0.5, CENTER.z()),
                        new Vec3(1, 0, 0)), List.of(item), 0, steps, true);
        assertNotNull(drag);
        drag.update(new GizmoMath.Ray(new Vec3(CENTER.x() - 6, CENTER.y() + 0.5 + 1.4, CENTER.z()),
                new Vec3(1, 0, 0)), false);
        DisplayDrag.Item moved = drag.current().getFirst();
        assertEquals(65.0, moved.minimum().y(), 1.0E-12, "sube un bloque entero: queda alineado con los bloques");
        assertEquals(65.5, moved.center().y(), 1.0E-12);
    }

    @Test
    void theCenterCubeMovesOverTheGround() {
        DisplayDrag.Item item = block(1, CENTER, Quat.IDENTITY, DisplayPose.ONE);
        DisplayGizmo gizmo = gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.MOVE);
        assertEquals(DisplayGizmo.Handle.CENTER, gizmo.pick(down(CENTER.x(), CENTER.z())));
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.CENTER, down(CENTER.x(), CENTER.z()),
                List.of(item), 0, PIXEL.withMove(DisplaySteps.Move.QUARTER), true);
        assertNotNull(drag);
        drag.update(down(CENTER.x() + 0.6, CENTER.z() - 1.1), false);
        Vec3 corner = drag.current().getFirst().minimum();
        assertEquals(0.5, corner.x(), 1.0E-12, "0 + 0.6 → 0.5 en cuartos");
        assertEquals(-1.0, corner.z(), 1.0E-12, "0 − 1.1 → −1 en cuartos");
        assertEquals(64.0, corner.y(), 1.0E-12);
    }

    @Test
    void inLocalModeItMovesAlongItsOwnTurnedAxis() {
        Quat turned = Quat.axis(1, Math.toRadians(45));
        DisplayDrag.Item item = block(1, CENTER, turned, DisplayPose.ONE);
        DisplayGizmo gizmo = gizmo(CENTER, turned, DisplayGizmo.Tool.MOVE);
        Vec3 axis = gizmo.axis(0);
        Vec3 grab = CENTER.add(axis.multiply(0.6));
        GizmoMath.Ray start = new GizmoMath.Ray(grab.add(new Vec3(0, 6, 0)), new Vec3(0, -1, 0));
        assertEquals(DisplayGizmo.Handle.X, gizmo.pick(start));
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.X, start, List.of(item), 0,
                PIXEL.withMove(DisplaySteps.Move.QUARTER), false);
        assertNotNull(drag);
        Vec3 there = CENTER.add(axis.multiply(0.6 + 0.8));
        drag.update(new GizmoMath.Ray(there.add(new Vec3(0, 6, 0)), new Vec3(0, -1, 0)), false);
        Vec3 delta = drag.current().getFirst().center().subtract(CENTER);
        assertVec(axis.multiply(0.75), delta, 1.0E-9);
    }

    @Test
    void ringsTurnInFifteenDegreeSteps() {
        DisplayDrag.Item item = block(1, CENTER, Quat.IDENTITY, DisplayPose.ONE);
        DisplayGizmo gizmo = gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.ROTATE);
        double radius = 0.62;
        // El anillo Y está en el plano horizontal; se agarra en +Z (ángulo 0) y se lleva a 50° hacia +X.
        GizmoMath.Ray grab = down(CENTER.x(), CENTER.z() + radius);
        assertEquals(DisplayGizmo.Handle.Y, gizmo.pick(grab));
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.Y, grab, List.of(item), 0, PIXEL, true);
        assertNotNull(drag);
        double angle = Math.toRadians(50);
        drag.update(down(CENTER.x() + Math.sin(angle) * radius, CENTER.z() + Math.cos(angle) * radius), false);
        assertEquals(45.0, Math.toDegrees(drag.turned()), 1.0E-9);
        Quat left = drag.current().getFirst().pose().left();
        assertEquals(0.0, left.angleTo(Quat.axis(1, Math.toRadians(45))), 1.0E-9);
        assertVec(CENTER, drag.current().getFirst().center(), 1.0E-12);
        drag.update(down(CENTER.x() + Math.sin(angle) * radius, CENTER.z() + Math.cos(angle) * radius), true);
        assertEquals(50.0, Math.toDegrees(drag.turned()), 1.0E-9, "libre: lo girado");
        DisplayDrag fine = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.Y, grab, List.of(item), 0,
                PIXEL.withAngle(DisplaySteps.Angle.DEG5), true);
        assertNotNull(fine);
        double little = Math.toRadians(12);
        fine.update(down(CENTER.x() + Math.sin(little) * radius, CENTER.z() + Math.cos(little) * radius), false);
        assertEquals(10.0, Math.toDegrees(fine.turned()), 1.0E-9);
    }

    @Test
    void aRingTurnsPastHalfAVoltWithoutJumping() {
        DisplayDrag.Item item = block(1, CENTER, Quat.IDENTITY, DisplayPose.ONE);
        DisplayGizmo gizmo = gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.ROTATE);
        double radius = 0.62;
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.Y, down(CENTER.x(), CENTER.z() + radius),
                List.of(item), 0, PIXEL, true);
        assertNotNull(drag);
        for (int degrees = 10; degrees <= 270; degrees += 10) {
            double angle = Math.toRadians(degrees);
            drag.update(down(CENTER.x() + Math.sin(angle) * radius, CENTER.z() + Math.cos(angle) * radius), false);
        }
        assertEquals(270.0, Math.toDegrees(drag.turned()), 1.0E-9, "las vueltas se acumulan");
    }

    @Test
    void scaleCubesScaleOneAxisAroundTheCenter() {
        DisplayDrag.Item item = block(1, CENTER, Quat.IDENTITY, DisplayPose.ONE);
        DisplayGizmo gizmo = gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.SCALE);
        GizmoMath.Ray grab = towardsZ(CENTER.x() + 1.0, CENTER.y());
        assertEquals(DisplayGizmo.Handle.X, gizmo.pick(grab), "el cubo del extremo del eje X");
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.X, grab, List.of(item), 0, PIXEL, true);
        assertNotNull(drag);
        drag.update(towardsZ(CENTER.x() + 0.5, CENTER.y()), false);
        DisplayPose pose = drag.current().getFirst().pose();
        assertEquals(0.5, pose.scale().x(), 1.0E-12);
        assertEquals(1.0, pose.scale().y(), 1.0E-12);
        assertVec(CENTER, drag.current().getFirst().center(), 1.0E-12);
        drag.update(towardsZ(CENTER.x() - 3.0, CENTER.y()), false);
        assertEquals(1.0 / 16.0, drag.current().getFirst().pose().scale().x(), 1.0E-12,
                "nunca por debajo de un paso");
    }

    @Test
    void theCenterCubeScalesAGroupTogether() {
        DisplayDrag.Item pot = block(1, CENTER, Quat.IDENTITY, DisplayPose.ONE);
        DisplayDrag.Item plant = block(2, CENTER.add(new Vec3(0, 1, 0)), Quat.IDENTITY, DisplayPose.ONE);
        DisplayGizmo gizmo = gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.SCALE);
        GizmoMath.Ray grab = towardsZ(CENTER.x(), CENTER.y());
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.CENTER, grab, List.of(pot, plant), 0,
                PIXEL.withMove(DisplaySteps.Move.QUARTER), true);
        assertNotNull(drag);
        drag.update(towardsZ(CENTER.x(), CENTER.y() + 1.0), false);
        List<DisplayDrag.Item> scaled = drag.current();
        assertEquals(2.0, scaled.get(0).pose().scale().x(), 1.0E-12);
        assertVec(CENTER, scaled.get(0).center(), 1.0E-12);
        assertVec(CENTER.add(new Vec3(0, 2, 0)), scaled.get(1).center(), 1.0E-12, "la planta sube con el tiesto");
    }

    @Test
    void aGroupTurnsAroundTheActiveOne() {
        DisplayDrag.Item pot = block(1, CENTER, Quat.IDENTITY, new Vec3(0.5, 0.5, 0.5));
        DisplayDrag.Item lamp = block(2, CENTER.add(new Vec3(0, 0, 1)), Quat.IDENTITY, new Vec3(0.5, 0.5, 0.5));
        DisplayGizmo gizmo = gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.ROTATE);
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.Y, down(CENTER.x(), CENTER.z() + 0.62),
                List.of(pot, lamp), 0, PIXEL, true);
        assertNotNull(drag);
        drag.update(down(CENTER.x() + 0.62, CENTER.z()), false);
        assertEquals(90.0, Math.toDegrees(drag.turned()), 1.0E-9);
        assertVec(CENTER.add(new Vec3(1, 0, 0)), drag.current().get(1).center(), 1.0E-12);
        assertVec(CENTER, drag.current().get(0).center(), 1.0E-12);
    }

    @Test
    void lookingAlongAnArrowCannotGrabIt() {
        DisplayDrag.Item item = block(1, CENTER, Quat.IDENTITY, DisplayPose.ONE);
        DisplayGizmo gizmo = gizmo(CENTER, Quat.IDENTITY, DisplayGizmo.Tool.MOVE);
        GizmoMath.Ray along = new GizmoMath.Ray(CENTER.add(new Vec3(-5, 0, 0)), new Vec3(1, 0, 0));
        assertNull(DisplayDrag.begin(gizmo, DisplayGizmo.Handle.X, along, List.of(item), 0, PIXEL, true));
        assertNull(DisplayDrag.begin(gizmo, DisplayGizmo.Handle.NONE, towardsZ(1, 64), List.of(item), 0, PIXEL,
                true));
        DisplayDrag drag = DisplayDrag.begin(gizmo, DisplayGizmo.Handle.X, towardsZ(CENTER.x() + 0.6, CENTER.y()),
                List.of(item), 0, PIXEL, true);
        assertNotNull(drag);
        assertFalse(drag.update(towardsZ(CENTER.x() + 0.61, CENTER.y()), false), "menos de medio paso: nada");
        assertFalse(drag.moved());
    }

    @Test
    void theGizmoTakesTheObjectAxesInLocalAndScaleMode() {
        Quat object = Quat.axis(1, 0.8);
        DisplayEditorSettings global = DisplayEditorSettings.DEFAULT;
        assertEquals(Quat.IDENTITY, global.frame(object));
        assertEquals(object, global.withTool(DisplayGizmo.Tool.SCALE).frame(object), "escalar: siempre local");
        assertEquals(object, global.withLocal(true).frame(object));
        assertTrue(global.globalGrid());
        assertFalse(global.withLocal(true).globalGrid());
    }
}
