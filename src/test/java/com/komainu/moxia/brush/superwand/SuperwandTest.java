package com.komainu.moxia.brush.superwand;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuperwandTest {
    private static final BlockVector3 A = BlockVector3.at(0, 64, 0);
    private static final BlockVector3 B = BlockVector3.at(10, 64, 0);
    private static final BlockVector3 C = BlockVector3.at(10, 70, 10);

    @Test
    void modesCycleInOrder() {
        assertEquals(SuperwandMode.POSITION, SuperwandMode.TRACE.next());
        assertEquals(SuperwandMode.RELATIVE, SuperwandMode.POSITION.next());
        assertEquals(SuperwandMode.GRAB, SuperwandMode.RELATIVE.next());
        assertEquals(SuperwandMode.TRACE, SuperwandMode.GRAB.next(), "y vuelve a empezar");
        assertFalse(SuperwandMode.GRAB.selectsPoints());
        assertTrue(SuperwandMode.RELATIVE.selectsPoints());
        assertEquals(SuperwandMode.GRAB, SuperwandMode.byId("agarrar"));
        assertEquals(SuperwandMode.POSITION, SuperwandMode.byId("POSITION"));
        assertThrows(IllegalArgumentException.class, () -> SuperwandMode.byId("lasso"));
    }

    @Test
    void fullSelectionReplacesTheLastPoint() {
        SuperwandPoints points = new SuperwandPoints();
        points.add(A, 2);
        points.add(B, 2);
        points.add(C, 2);
        assertEquals(List.of(A, C), points.points(), "un cuboide lleno cambia su segundo punto");
        SuperwandPoints convex = new SuperwandPoints();
        convex.add(A, SuperwandPoints.UNLIMITED);
        convex.add(B, SuperwandPoints.UNLIMITED);
        convex.add(C, SuperwandPoints.UNLIMITED);
        assertEquals(3, convex.size());
        SuperwandPoints shrunk = new SuperwandPoints(List.of(A, B, C));
        shrunk.add(BlockVector3.ZERO, 2);
        assertEquals(List.of(A, BlockVector3.ZERO), shrunk.points(), "al pasar a cuboide se recorta");
    }

    @Test
    void removeReverseAndRotate() {
        SuperwandPoints points = new SuperwandPoints(List.of(A, B, C));
        points.reverse();
        assertEquals(List.of(C, B, A), points.points());
        points.rotate();
        assertEquals(List.of(B, A, C), points.points(), "el primero pasa al final");
        assertTrue(points.removeLast());
        assertEquals(A, points.last());
        points.clear();
        assertFalse(points.removeLast());
        assertNull(points.last());
    }

    @Test
    void grabFindsThePointUnderTheAim() {
        SuperwandPoints points = new SuperwandPoints(List.of(A, B, C));
        assertEquals(1, points.nearestToRay(2.5, 64.5, 0.5, 1, 0, 0, 1.5), "mirando al este, A queda detrás y se ve B");
        assertEquals(0, points.nearestToRay(-5.5, 64.5, 0.5, 1, 0, 0, 1.5), "el más cercano a lo largo del rayo");
        assertEquals(-1, points.nearestToRay(0.5, 80.5, 0.5, 1, 0, 0, 1.5), "nada cerca del rayo");
        assertEquals(-1, points.nearestToRay(20.5, 64.5, 0.5, 1, 0, 0, 1.5), "lo que queda detrás no cuenta");
    }

    @Test
    void translateMovesTheWholeSelection() {
        SuperwandPoints points = new SuperwandPoints(List.of(A, B));
        points.translate(1, -2, 3);
        assertEquals(List.of(BlockVector3.at(1, 62, 3), BlockVector3.at(11, 62, 3)), points.points());
    }

    @Test
    void relativeAndHeldPoints() {
        assertEquals(BlockVector3.at(0, 64, -5), SuperwandPoints.relative(A, 0, 0, -1, 5));
        assertEquals(BlockVector3.at(4, 68, 0), SuperwandPoints.relative(A, 1, 1, 0, Math.sqrt(32)),
                "en diagonal, a la distancia pedida");
        assertEquals(A, SuperwandPoints.relative(A, 0, 0, 0, 5), "sin dirección no se mueve");
        assertEquals(BlockVector3.at(3, 65, 0), SuperwandPoints.heldAt(0.5, 65.6, 0.5, 1, 0, 0, 3));
    }
}
