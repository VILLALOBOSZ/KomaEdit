package com.komainu.moxia.symmetry;

import com.komainu.moxia.symmetry.SymmetryGuides.Mark;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymmetryGuidesTest {
    @Test
    void mirrorPlanesAreDrawnOnThePlaneAroundThePlayer() {
        SymmetryCenter center = SymmetryCenter.of(10, 64, 0, true, false, false);
        List<Mark> marks = SymmetryGuides.marks(SymmetryMode.mirror(true, false, false, 4), center, 4.3, 70.0, -2.6);
        assertEquals(81, marks.size());
        for (Mark mark : marks) {
            assertEquals(11.0, mark.x(), 1.0E-9, "el plano X pasa entre el bloque 10 y el 11");
            assertEquals(SymmetryGuides.PLANE_X, mark.color());
            assertTrue(Math.abs(mark.z() - -2.5) <= SymmetryGuides.RADIUS);
            assertTrue(Math.abs(mark.y() - 70.5) <= SymmetryGuides.RADIUS);
        }
    }

    @Test
    void radialShowsTheAxisAndOneRayPerArm() {
        SymmetryCenter center = SymmetryCenter.block(0, 64, 0);
        List<Mark> marks = SymmetryGuides.marks(SymmetryMode.radial(6, false), center, 3.0, 64.0, 3.0);
        long axis = marks.stream().filter(mark -> mark.x() == 0.5 && mark.z() == 0.5).count();
        assertEquals(4 * SymmetryGuides.RADIUS + 1, axis);
        long firstArm = marks.stream().filter(mark -> mark.color() == SymmetryGuides.FIRST_ARM).count();
        assertEquals(SymmetryGuides.RADIUS, firstArm);
        assertEquals(axis + 6L * SymmetryGuides.RADIUS, marks.size());
    }

    @Test
    void farPlanesAreSkippedAndTheBudgetHolds() {
        SymmetryCenter center = SymmetryCenter.block(0, 64, 0);
        assertTrue(SymmetryGuides.marks(SymmetryMode.mirror(true, false, false, 4), center, 500.0, 64.0, 0.0).isEmpty());
        List<Mark> everything = SymmetryGuides.marks(SymmetryMode.mirror(true, true, true, 4), center, 2.0, 66.0, 2.0);
        assertEquals(3 * 81 + 4 * SymmetryGuides.RADIUS + 1, everything.size());
        assertTrue(SymmetryGuides.marks(SymmetryMode.radial(16, true), center, 1.0, 64.0, 1.0).size()
                <= SymmetryGuides.MAX_MARKS);
        everything.stream().filter(mark -> mark.color() == SymmetryGuides.PLANE_Y)
                .forEach(mark -> assertEquals(64.5, mark.y(), 1.0E-9));
    }
}
