package com.komainu.moxia.shape.smooth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PieceFitTest {
    @Test
    void masksFollowMinecraftGeometry() {
        assertEquals(0xFF, SmoothPiece.mask(SmoothPiece.FULL));
        assertEquals(0, SmoothPiece.mask(SmoothPiece.EMPTY));
        assertEquals(0x33, SmoothPiece.mask(SmoothPiece.slab(false)), "losa inferior: y = 0");
        assertEquals(0xCC, SmoothPiece.mask(SmoothPiece.slab(true)), "losa superior: y = 1");
        // Escalera hacia el norte: mitad inferior y los dos cuartos altos del lado norte (z = 0).
        assertEquals(0x33 | bit(0, 1, 0) | bit(1, 1, 0),
                SmoothPiece.mask(SmoothPiece.stairs(SmoothPiece.NORTH, false, SmoothPiece.STRAIGHT)));
        assertEquals(0x33 | bit(0, 1, 1) | bit(1, 1, 1),
                SmoothPiece.mask(SmoothPiece.stairs(SmoothPiece.SOUTH, false, SmoothPiece.STRAIGHT)));
        // Escalera colgada hacia el este: mitad superior y los cuartos bajos del lado este (x = 1).
        assertEquals(0xCC | bit(1, 0, 0) | bit(1, 0, 1),
                SmoothPiece.mask(SmoothPiece.stairs(SmoothPiece.EAST, true, SmoothPiece.STRAIGHT)));
        // Esquina exterior derecha mirando al norte: solo el cuarto noreste, como StairsBlock.SHAPE_INDICES.
        assertEquals(0x33 | bit(1, 1, 0),
                SmoothPiece.mask(SmoothPiece.stairs(SmoothPiece.NORTH, false, SmoothPiece.OUTER_RIGHT)));
        assertEquals(0x33 | bit(0, 1, 0) | bit(0, 1, 1) | bit(1, 1, 1),
                SmoothPiece.mask(SmoothPiece.stairs(SmoothPiece.SOUTH, false, SmoothPiece.INNER_RIGHT)));
    }

    @Test
    void everyStairRoundTripsThroughItsByte() {
        for (int facing = 0; facing < 4; facing++) {
            for (int half = 0; half < 2; half++) {
                for (int shape = SmoothPiece.STRAIGHT; shape <= SmoothPiece.OUTER_RIGHT; shape++) {
                    byte piece = SmoothPiece.stairs(facing, half == 1, shape);
                    byte added = SmoothPiece.added(piece);
                    for (byte code : new byte[]{piece, added}) {
                        assertTrue(code != SmoothPiece.FULL && code != SmoothPiece.EMPTY && SmoothPiece.isValid(code));
                        assertTrue(SmoothPiece.isStairs(code));
                        assertFalse(SmoothPiece.isSlab(code));
                        assertEquals(facing, SmoothPiece.facing(code));
                        assertEquals(half == 1, SmoothPiece.isTop(code));
                        assertEquals(shape, SmoothPiece.shape(code));
                        int octants = Integer.bitCount(SmoothPiece.mask(code));
                        assertEquals(shape == SmoothPiece.STRAIGHT ? 6 : shape <= SmoothPiece.INNER_RIGHT ? 7 : 5,
                                octants, SmoothPiece.describe(code));
                    }
                    assertFalse(SmoothPiece.isAdded(piece));
                    assertTrue(SmoothPiece.isAdded(added));
                    assertEquals(SmoothPiece.mask(piece), SmoothPiece.mask(added));
                }
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> SmoothPiece.stairs(4, false, SmoothPiece.STRAIGHT));
        assertThrows(IllegalArgumentException.class, () -> SmoothPiece.facing(SmoothPiece.slab(true)));
    }

    @Test
    void halfFilledBlockBecomesASlabAndHalfFilledGapGetsOne() {
        double[] lowerHalf = fill(0x33, 1.0);
        assertEquals(SmoothPiece.slab(false), choose(lowerHalf, true, SmoothMode.SLABS, 0.5));
        assertEquals(SmoothPiece.slab(true), choose(fill(0xCC, 0.9), true, SmoothMode.STAIRS, 0.5));
        byte added = choose(fill(0x33, 0.8), false, SmoothMode.SLABS, 0.5);
        assertTrue(SmoothPiece.isAdded(added));
        assertTrue(SmoothPiece.isSlab(added));
        assertFalse(SmoothPiece.isTop(added));
    }

    @Test
    void wholeBlocksAndEmptyAirStayAsTheyAre() {
        assertEquals(SmoothPiece.FULL, choose(fill(0xFF, 1.0), true, SmoothMode.STAIRS, 1.0));
        assertEquals(SmoothPiece.EMPTY, choose(fill(0xFF, 0.0), false, SmoothMode.STAIRS, 1.0));
        assertEquals(SmoothPiece.EMPTY, choose(fill(0x01, 0.3), false, SmoothMode.STAIRS, 1.0),
                "una esquina rozada no justifica una losa");
    }

    @Test
    void slopeBecomesAStraightStairFacingUphill() {
        // Mitad inferior llena y, arriba, solo el lado norte: el terreno sube hacia el norte.
        double[] slope = fill(0x33 | bit(0, 1, 0) | bit(1, 1, 0), 1.0);
        assertEquals(SmoothPiece.stairs(SmoothPiece.NORTH, false, SmoothPiece.STRAIGHT),
                choose(slope, true, SmoothMode.STAIRS, 0.5));
        // Techo que baja hacia el oeste: mitad superior y, abajo, solo el lado oeste.
        double[] overhang = fill(0xCC | bit(0, 0, 0) | bit(0, 0, 1), 0.95);
        assertEquals(SmoothPiece.stairs(SmoothPiece.WEST, true, SmoothPiece.STRAIGHT),
                choose(overhang, true, SmoothMode.STAIRS, 0.5));
        // Sin escaleras disponibles, la rampa no mejora al bloque entero con la cobertura por defecto.
        assertEquals(SmoothPiece.FULL, choose(slope, true, SmoothMode.SLABS, 0.5));
    }

    @Test
    void nearlyVerticalWallLeansTowardTheFullerHalf() {
        // Flanco de un tubo horizontal justo bajo el eje: la mitad sur está casi llena y la de arriba algo más.
        double[] flank = new double[8];
        flank[SmoothPiece.octant(0, 0, 1)] = 0.75;
        flank[SmoothPiece.octant(1, 0, 1)] = 0.75;
        flank[SmoothPiece.octant(0, 1, 1)] = 0.875;
        flank[SmoothPiece.octant(1, 1, 1)] = 0.875;
        assertEquals(SmoothPiece.stairs(SmoothPiece.SOUTH, true, SmoothPiece.STRAIGHT),
                choose(flank, true, SmoothMode.STAIRS, 0.5));
        // Reflejado en vertical, la escalera pasa a apoyarse abajo.
        double[] mirrored = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            mirrored[octant ^ 2] = flank[octant];
        }
        assertEquals(SmoothPiece.stairs(SmoothPiece.SOUTH, false, SmoothPiece.STRAIGHT),
                choose(mirrored, true, SmoothMode.STAIRS, 0.5));
    }

    @Test
    void coverageControlsHowEagerlyPiecesAreUsed() {
        double[] mostlyFull = fill(0xFF, 1.0);
        mostlyFull[SmoothPiece.octant(0, 1, 0)] = 0.6;
        mostlyFull[SmoothPiece.octant(1, 1, 0)] = 0.6;
        assertEquals(SmoothPiece.FULL, choose(mostlyFull, true, SmoothMode.STAIRS, 0.0));
        assertEquals(SmoothPiece.FULL, choose(mostlyFull, true, SmoothMode.STAIRS, 0.5),
                "60 % de ocupación en los cuartos: el bloque entero encaja mejor");
        assertTrue(SmoothPiece.isStairs(choose(mostlyFull, true, SmoothMode.STAIRS, 1.0)),
                "con cobertura 1 toda la superficie que no está entera lleva pieza");

        double[] thinLayer = fill(0x33, 0.35);
        assertEquals(SmoothPiece.EMPTY, choose(thinLayer, false, SmoothMode.SLABS, 0.5));
        assertTrue(SmoothPiece.isSlab(choose(thinLayer, false, SmoothMode.SLABS, 1.0)));
        assertEquals(SmoothPiece.EMPTY, choose(thinLayer, false, SmoothMode.NONE, 1.0));

        double previous = Double.POSITIVE_INFINITY;
        for (int step = 0; step <= 20; step++) {
            double threshold = PieceFit.threshold(step / 20.0);
            assertTrue(threshold < previous, "el umbral debe bajar al subir la cobertura");
            previous = threshold;
        }
        assertEquals(0.0, PieceFit.threshold(0.5), 1.0E-12);
        assertEquals(-2.0, PieceFit.threshold(1.0), 1.0E-12);
        assertTrue(PieceFit.threshold(0.0) >= 6.0, "con cobertura 0 ninguna pieza alcanza el umbral");
    }

    @Test
    void zeroCoverageNeverChangesAnything() {
        for (int mask = 0; mask < 256; mask++) {
            double[] occupancy = fill(mask, 1.0);
            assertEquals(SmoothPiece.FULL, choose(occupancy, true, SmoothMode.STAIRS, 0.0));
            assertEquals(SmoothPiece.EMPTY, choose(occupancy, false, SmoothMode.STAIRS, 0.0));
        }
    }

    private static byte choose(double[] occupancy, boolean existing, SmoothMode mode, double coverage) {
        return PieceFit.choose(occupancy, existing, mode, coverage);
    }

    private static double[] fill(int mask, double value) {
        double[] occupancy = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            occupancy[octant] = (mask & 1 << octant) != 0 ? value : 0.0;
        }
        return occupancy;
    }

    private static int bit(int x, int y, int z) {
        return 1 << SmoothPiece.octant(x, y, z);
    }
}
