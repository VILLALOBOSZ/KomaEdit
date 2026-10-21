package com.komainu.moxia.studio.world;

import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los puntos de la trayectoria con el ratón: agarrar, meter uno en un tramo, añadir y arrastrar. */
class PathDragTest {
    private static final List<int[]> POINTS = List.of(new int[]{0, 64, 0}, new int[]{20, 64, 0},
            new int[]{20, 64, 20});
    private static final Vec3 EYE = new Vec3(10.5, 90.5, -20.5);

    private static GizmoMath.Ray at(double x, double y, double z) {
        return new GizmoMath.Ray(EYE, new Vec3(x, y, z).subtract(EYE));
    }

    @Test
    void pressingAPointGrabsItAndDraggingMovesItOverTheTerrain() {
        PathDrag drag = new PathDrag();
        PathDrag.Press press = drag.press(at(20.5, 64.5, 0.5), POINTS, false, new int[]{21, 65, 1}, 32);
        assertEquals(PathDrag.Grab.POINT, press.grab());
        assertEquals(1, press.index());
        assertTrue(drag.active());
        assertNull(drag.drag(new int[]{20, 64, 0}), "donde ya estaba: nada que mandar");
        assertArrayEquals(new int[]{24, 66, 3}, drag.drag(new int[]{24, 66, 3}));
        assertNull(drag.drag(new int[]{24, 66, 3}), "el mismo bloque otra vez: nada");
        assertNull(drag.drag(null), "el cursor al cielo: el punto se queda");
        assertTrue(drag.moved());
        drag.release();
        assertFalse(drag.active());
    }

    @Test
    void pressingTheMiddleOfASegmentInsertsAPointThere() {
        PathDrag drag = new PathDrag();
        PathDrag.Press press = drag.press(at(10.5, 64.5, 0.5), POINTS, false, new int[]{10, 64, -1}, 32);
        assertEquals(PathDrag.Grab.INSERT, press.grab());
        assertEquals(0, press.after(), "entre el primero y el segundo");
        assertEquals(1, press.index(), "el nuevo queda agarrado");
        assertArrayEquals(new int[]{11, 64, -2}, drag.drag(new int[]{11, 64, -2}));
        // Cerrada, el tramo del último al primero también vale.
        PathDrag.Press closing = new PathDrag().press(at(10.5, 64.5, 10.5), POINTS, true, null, 32);
        assertEquals(PathDrag.Grab.INSERT, closing.grab());
        assertEquals(2, closing.after());
        assertEquals(PathDrag.Grab.NONE, new PathDrag().press(at(10.5, 64.5, 10.5), POINTS, false, null, 32).grab(),
                "abierta, ahí no hay tramo");
    }

    @Test
    void pressingTheTerrainAddsAPointAtTheEnd() {
        PathDrag drag = new PathDrag();
        PathDrag.Press press = drag.press(at(40.5, 64.5, 40.5), POINTS, false, new int[]{40, 65, 40}, 32);
        assertEquals(PathDrag.Grab.ADD, press.grab());
        assertEquals(3, press.index());
        assertArrayEquals(new int[]{40, 65, 40}, press.place());
        assertNull(drag.drag(new int[]{40, 65, 40}), "soltarlo sin moverlo es un clic");
        assertFalse(drag.moved());
        assertArrayEquals(new int[]{42, 65, 40}, drag.drag(new int[]{42, 65, 40}), "y arrastrando, se lleva");
    }

    @Test
    void withTheMaximumOfPointsOnlyExistingOnesCanBeGrabbed() {
        PathDrag drag = new PathDrag();
        assertEquals(PathDrag.Grab.NONE, drag.press(at(40.5, 64.5, 40.5), POINTS, false, new int[]{40, 65, 40},
                3).grab());
        assertEquals(PathDrag.Grab.POINT, drag.press(at(0.5, 64.5, 0.5), POINTS, false, null, 3).grab());
    }

    @Test
    void farAwayPointsAreEasierToHit() {
        assertEquals(0.70, PathDrag.threshold(10.0), 1.0E-9);
        assertEquals(1.40, PathDrag.threshold(1_000.0), 1.0E-9);
        assertTrue(PathDrag.threshold(120.0) > PathDrag.threshold(80.0));
    }
}
