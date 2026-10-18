package com.komainu.moxia.path;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathGeneratorsTest {
    private static final BlockVector3 CENTER = BlockVector3.at(100, 64, -50);

    @Test
    void circleHasDistinctPointsAtTheRadius() {
        List<BlockVector3> circle = PathGenerators.circle(CENTER, 20, 16);
        assertEquals(16, circle.size());
        for (BlockVector3 point : circle) {
            double distance = Math.hypot(point.x() - CENTER.x(), point.z() - CENTER.z());
            assertEquals(20.0, distance, 0.75);
            assertEquals(CENTER.y(), point.y());
        }
    }

    @Test
    void polygonRejectsTooFewSides() {
        assertThrows(IllegalArgumentException.class, () -> PathGenerators.polygon(CENTER, 10, 2, 0));
        assertEquals(6, PathGenerators.polygon(CENTER, 10, 6, 30).size());
    }

    @Test
    void spiralGoesFromInnerToOuterRadiusAndClimbs() {
        List<BlockVector3> spiral = PathGenerators.spiral(CENTER, 4, 16, 2, 10, 25);
        assertEquals(BlockVector3.at(104, 64, -50), spiral.getFirst());
        assertEquals(BlockVector3.at(116, 74, -50), spiral.getLast());
    }

    @Test
    void helixKeepsItsRadius() {
        List<BlockVector3> helix = PathGenerators.spiral(CENTER, 8, 8, 3, 30, 32);
        for (BlockVector3 point : helix) {
            assertEquals(8.0, Math.hypot(point.x() - CENTER.x(), point.z() - CENTER.z()), 0.75);
        }
        assertEquals(94, helix.getLast().y());
    }

    @Test
    void suggestedPointsStayInsideTheLimit() {
        assertEquals(8, PathGenerators.suggestedPoints(0.25, 32));
        assertEquals(25, PathGenerators.suggestedPoints(2, 32));
        assertEquals(32, PathGenerators.suggestedPoints(10, 32));
    }

    @Test
    void arcStartsAndEndsAtTheChosenPointsWithItsHeight() {
        BlockVector3 start = BlockVector3.at(0, 64, 0);
        BlockVector3 end = BlockVector3.at(40, 64, 0);
        List<BlockVector3> arc = PathGenerators.arc(start, end, 10, PathGenerators.Bulge.UP, 17);
        assertEquals(start, arc.getFirst());
        assertEquals(end, arc.getLast());
        assertEquals(BlockVector3.at(20, 74, 0), arc.get(8));
    }

    @Test
    void sidewaysArcStaysLevel() {
        BlockVector3 start = BlockVector3.at(0, 64, 0);
        BlockVector3 end = BlockVector3.at(40, 64, 0);
        List<BlockVector3> left = PathGenerators.arc(start, end, 8, PathGenerators.Bulge.LEFT, 9);
        // Hacia el este, la izquierda es el norte (z negativa).
        assertEquals(BlockVector3.at(20, 64, -8), left.get(4));
        left.forEach(point -> assertEquals(64, point.y()));
    }

    @Test
    void levelCatenaryHangsByTheRequestedSag() {
        BlockVector3 start = BlockVector3.at(0, 80, 0);
        BlockVector3 end = BlockVector3.at(0, 80, 60);
        List<BlockVector3> chain = PathGenerators.catenary(start, end, 12, PathGenerators.Bulge.DOWN, 21);
        assertEquals(start, chain.getFirst());
        assertEquals(end, chain.getLast());
        assertEquals(BlockVector3.at(0, 68, 30), chain.get(10));
        int lowest = chain.stream().mapToInt(BlockVector3::y).min().orElseThrow();
        assertEquals(68, lowest);
    }

    @Test
    void catenaryWithOneHighEndHasItsLowestPointNearTheLowerEnd() {
        BlockVector3 start = BlockVector3.at(0, 60, 0);
        BlockVector3 end = BlockVector3.at(60, 80, 0);
        List<BlockVector3> chain = PathGenerators.catenary(start, end, 10, PathGenerators.Bulge.DOWN, 31);
        BlockVector3 lowest = chain.stream().min((a, b) -> Integer.compare(a.y(), b.y())).orElseThrow();
        assertTrue(lowest.x() < 30, "el punto más bajo debe quedar hacia el extremo bajo: " + lowest);
        assertTrue(lowest.y() < 60, "con 10 de comba la cadena baja del extremo inferior: " + lowest);
    }

    @Test
    void catenarySolverMatchesTheSagExactly() {
        PathGenerators.Catenary curve = PathGenerators.Catenary.solve(60, 20, 10);
        assertEquals(10.0, curve.sag(), 1.0E-6);
        assertEquals(0.0, curve.y(0), 1.0E-6);
        assertEquals(20.0, curve.y(60), 1.0E-6);
    }

    @Test
    void invertedCatenaryRisesLikeAnArch() {
        BlockVector3 start = BlockVector3.at(0, 64, 0);
        BlockVector3 end = BlockVector3.at(30, 64, 0);
        List<BlockVector3> arch = PathGenerators.catenary(start, end, 9, PathGenerators.Bulge.UP, 11);
        assertEquals(73, arch.get(5).y());
    }

    @Test
    void sidewaysCatenaryStaysLevel() {
        BlockVector3 start = BlockVector3.at(0, 64, 0);
        BlockVector3 end = BlockVector3.at(40, 64, 0);
        List<BlockVector3> chain = PathGenerators.catenary(start, end, 6, PathGenerators.Bulge.RIGHT, 9);
        assertEquals(BlockVector3.at(20, 64, 6), chain.get(4));
    }

    @Test
    void catenaryNeedsHorizontalSeparation() {
        BlockVector3 start = BlockVector3.at(0, 64, 0);
        BlockVector3 above = BlockVector3.at(0, 90, 0);
        assertThrows(IllegalArgumentException.class,
                () -> PathGenerators.catenary(start, above, 4, PathGenerators.Bulge.DOWN, 16));
        assertThrows(IllegalArgumentException.class,
                () -> PathGenerators.catenary(start, BlockVector3.at(40, 64, 0), 0, PathGenerators.Bulge.DOWN, 16));
    }

    @Test
    void bulgeAcceptsSpanishNames() {
        assertEquals(PathGenerators.Bulge.DOWN, PathGenerators.Bulge.byId("abajo"));
        assertEquals(PathGenerators.Bulge.LEFT, PathGenerators.Bulge.byId("LEFT"));
        assertThrows(IllegalArgumentException.class, () -> PathGenerators.Bulge.byId("sideways"));
    }

    @Test
    void catenarySelectionUsesARelativeSagAndAStraightLineAtZero() {
        BlockVector3 start = BlockVector3.at(0, 80, 0);
        BlockVector3 end = BlockVector3.at(40, 80, 0);
        List<BlockVector3> line = PathGenerators.catenarySelection(start, end, 0.0, PathGenerators.Bulge.DOWN, 5);
        assertEquals(List.of(start, BlockVector3.at(10, 80, 0), BlockVector3.at(20, 80, 0),
                BlockVector3.at(30, 80, 0), end), line);
        List<BlockVector3> hanging = PathGenerators.catenarySelection(start, end, 0.25, PathGenerators.Bulge.DOWN, 9);
        assertEquals(start, hanging.getFirst());
        assertEquals(end, hanging.getLast());
        int lowest = hanging.stream().mapToInt(BlockVector3::y).min().orElseThrow();
        assertEquals(70, lowest, 1);
        assertThrows(IllegalArgumentException.class,
                () -> PathGenerators.catenarySelection(start, end, 6.0, PathGenerators.Bulge.DOWN, 9));
        assertThrows(IllegalArgumentException.class,
                () -> PathGenerators.catenarySelection(start, start, 0.1, PathGenerators.Bulge.DOWN, 9));
    }
}
