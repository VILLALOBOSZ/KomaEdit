package com.komainu.moxia.path;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathEditingTest {
    private static final List<BlockVector3> LINE = List.of(at(0, 64, 0), at(10, 64, 0), at(10, 70, 20));

    @Test
    void insertsMidpointBetweenNeighbours() {
        List<BlockVector3> result = PathEditing.insertMidpoint(LINE, 0, false);
        assertEquals(List.of(at(0, 64, 0), at(5, 64, 0), at(10, 64, 0), at(10, 70, 20)), result);
    }

    @Test
    void insertAfterLastClosesTheLoopOrExtendsTheLastSegment() {
        assertEquals(at(5, 67, 10), PathEditing.insertMidpoint(LINE, 2, true).get(3));
        // Abierto: prolonga el último tramo media longitud.
        assertEquals(at(10, 73, 30), PathEditing.insertMidpoint(LINE, 2, false).get(3));
    }

    @Test
    void refusesToInsertBetweenTouchingPoints() {
        List<BlockVector3> touching = List.of(at(0, 0, 0), at(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PathEditing.insertMidpoint(touching, 0, false));
        assertThrows(IllegalArgumentException.class, () -> PathEditing.insertMidpoint(LINE, 5, false));
    }

    @Test
    void removesAndSetsPointsWithoutLeavingRepeats() {
        assertEquals(List.of(at(0, 64, 0), at(10, 70, 20)), PathEditing.remove(LINE, 1));
        assertEquals(List.of(at(10, 64, 0), at(10, 70, 20)), PathEditing.set(LINE, 0, at(10, 64, 0)));
    }

    @Test
    void reversesAndTranslates() {
        assertEquals(List.of(at(10, 70, 20), at(10, 64, 0), at(0, 64, 0)), PathEditing.reverse(LINE));
        assertEquals(at(-3, 66, 5), PathEditing.translate(LINE, -3, 2, 5).getFirst());
    }

    @Test
    void mirrorKeepsTheBoundingBox() {
        List<BlockVector3> mirrored = PathEditing.mirror(LINE, 'x', false);
        assertEquals(List.of(at(10, 64, 0), at(0, 64, 0), at(0, 70, 20)), mirrored);
    }

    @Test
    void mirrorAppendBuildsTheSymmetricHalfWithoutRepeatingTheJoint() {
        List<BlockVector3> half = List.of(at(0, 0, 0), at(4, 6, 0), at(8, 8, 0));
        List<BlockVector3> whole = PathEditing.mirror(half, 'x', true);
        assertEquals(List.of(at(0, 0, 0), at(4, 6, 0), at(8, 8, 0), at(12, 6, 0), at(16, 0, 0)), whole);
    }

    @Test
    void fourQuarterTurnsGiveTheSamePath() {
        List<BlockVector3> square = List.of(at(0, 5, 0), at(6, 5, 0), at(6, 5, 4));
        List<BlockVector3> turned = square;
        for (int step = 0; step < 4; step++) {
            turned = PathEditing.rotateY(turned, 90);
        }
        assertEquals(square, turned);
        assertThrows(IllegalArgumentException.class, () -> PathEditing.rotateY(square, 45));
    }

    @Test
    void quarterTurnStaysInsideTheSameCentre() {
        List<BlockVector3> line = List.of(at(-4, 0, 0), at(4, 0, 0));
        assertEquals(List.of(at(0, 0, -4), at(0, 0, 4)), PathEditing.rotateY(line, 90));
    }

    @Test
    void snapsToGridAndOptionallyHeight() {
        List<BlockVector3> points = List.of(at(3, 65, -7), at(9, 67, 13));
        assertEquals(List.of(at(4, 65, -8), at(8, 67, 12)), PathEditing.snap(points, 4, false));
        assertEquals(List.of(at(4, 64, -8), at(8, 68, 12)), PathEditing.snap(points, 4, true));
    }

    @Test
    void relaxKeepsCountAndOpenEndpoints() {
        List<BlockVector3> zigzag = List.of(at(0, 0, 0), at(4, 8, 0), at(8, 0, 0), at(12, 8, 0), at(16, 0, 0));
        List<BlockVector3> relaxed = PathEditing.relax(zigzag, false, 0.5, 2);
        assertEquals(zigzag.size(), relaxed.size());
        assertEquals(zigzag.getFirst(), relaxed.getFirst());
        assertEquals(zigzag.getLast(), relaxed.getLast());
        assertTrue(relaxed.get(1).y() < 8);
    }

    @Test
    void simplifyDropsAlignedPointsAndKeepsCorners() {
        List<BlockVector3> points = List.of(at(0, 0, 0), at(5, 0, 0), at(10, 0, 0), at(10, 0, 10));
        assertEquals(List.of(at(0, 0, 0), at(10, 0, 0), at(10, 0, 10)), PathEditing.simplify(points, 0.5));
    }

    @Test
    void subdivideRespectsTheLimit() {
        List<BlockVector3> result = PathEditing.subdivide(LINE, false, 32);
        assertEquals(5, result.size());
        assertThrows(IllegalArgumentException.class, () -> PathEditing.subdivide(LINE, true, 5));
    }

    @Test
    void measuresLengthWithTheClosingSegment() {
        List<BlockVector3> square = List.of(at(0, 0, 0), at(10, 0, 0), at(10, 0, 10), at(0, 0, 10));
        assertEquals(30.0, PathEditing.length(square, false), 1.0E-9);
        assertEquals(40.0, PathEditing.length(square, true), 1.0E-9);
    }

    private static BlockVector3 at(int x, int y, int z) {
        return BlockVector3.at(x, y, z);
    }
}
