package com.komainu.moxia.symmetry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymmetryModeTest {
    @Test
    void readsMirrorLettersInAnyOrder() {
        assertEquals(SymmetryMode.mirror(true, false, false, 4), SymmetryMode.parse("x", 4));
        assertEquals(SymmetryMode.mirror(true, true, false, 6), SymmetryMode.parse("ZX", 6));
        assertEquals(SymmetryMode.mirror(true, true, false, 4), SymmetryMode.parse("x+z", 4));
        assertEquals(SymmetryMode.mirror(true, true, true, 4), SymmetryMode.parse("x z y", 4));
        assertEquals("xzy", SymmetryMode.parse("yxz", 4).id());
        assertEquals("X+Z+Y", SymmetryMode.parse("yxz", 4).axes());
        assertEquals(8, SymmetryMode.parse("xzy", 4).images());
    }

    @Test
    void readsRadialWithArmsAndOptionalMirrorInY() {
        SymmetryMode kept = SymmetryMode.parse("radial", 7);
        assertTrue(kept.radial());
        assertEquals(7, kept.arms());
        assertFalse(kept.mirrorY());
        SymmetryMode eight = SymmetryMode.parse("radial 8 y", 4);
        assertEquals(8, eight.arms());
        assertTrue(eight.mirrorY());
        assertEquals("radial 8 y", eight.id());
        assertEquals(16, eight.images());
        assertEquals(eight, SymmetryMode.parse(eight.id(), 3));
        assertEquals(SymmetryMode.radial(5, false), SymmetryMode.parse("rotacional 5", 4));
        assertEquals(12, SymmetryMode.radial(5, true).withArms(12).arms());
        assertTrue(SymmetryMode.radial(5, true).withArms(12).mirrorY());
    }

    @Test
    void rejectsWhatDoesNotMakeSense() {
        for (String bad : List.of("", "w", "xx", "radial 1", "radial 17", "radial x", "xr")) {
            assertThrows(IllegalArgumentException.class, () -> SymmetryMode.parse(bad, 4), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> new SymmetryMode(true, true, false, false, 4));
        assertThrows(IllegalArgumentException.class, () -> SymmetryMode.mirror(false, false, false, 4));
        assertEquals(SymmetryMode.DEFAULT, SymmetryMode.parse(SymmetryMode.DEFAULT.id(), SymmetryMode.DEFAULT_ARMS));
    }
}
