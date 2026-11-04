package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampCategory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StampCaptureTest {
    private static final Runnable NO_CANCEL = () -> {
    };

    private interface Height {
        int at(int x, int z);
    }

    private static StampCapture.Columns columns(int sizeX, int sizeZ, Height height, String block) {
        StampCapture.Columns columns = new StampCapture.Columns(sizeX, sizeZ);
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                columns.set(x, z, height.at(x, z), false, block);
            }
        }
        return columns;
    }

    /** La altura que da el sello en la columna (x, z) a tamaño real, como la vería el pincel. */
    private static double height(StampCapture.Result result, int x, int z, int sizeX, int sizeZ) {
        HeightStamp stamp = result.stamp();
        int side = stamp.width();
        int column = x + (side - sizeX) / 2;
        int row = z + (side - sizeZ) / 2;
        double span = Math.max(result.above(), result.below());
        double relief = stamp.reliefAt(column / (double) (side - 1), row / (double) (side - 1));
        return result.base() + relief * span;
    }

    @Test
    void aMountainOnFlatGroundOnlyRaisesWithItsRealHeight() {
        int size = 80;
        StampCapture.Columns columns = columns(size, size, (x, z) -> {
            double r = Math.hypot(x - 40, z - 40);
            return (int) Math.round(30 + Math.max(0.0, 36 - r) * 0.9);
        }, "minecraft:stone");
        StampCapture.Result result = StampCapture.build("monte", columns, null, NO_CANCEL);
        assertEquals(30.0, result.base(), 0.2, "el borde llano es el nivel base");
        assertEquals(0.0, result.stamp().neutral(), "solo sube: gris neutro negro");
        assertEquals(0.0, result.below(), 1e-9);
        assertEquals(32.4, result.above(), 1.5, "la cumbre sube lo que subía");
        assertEquals(result.above() / 40.0, result.heightRatio(), 1e-9);
        assertEquals(StampCategory.MOUNTAINS, result.category(), "piedra y alto: montaña");
    }

    @Test
    void aValleyOnlySinksAndOneWithBothUsesHalfTheGreysOnEachSide() {
        int size = 64;
        StampCapture.Result valley = StampCapture.build("hoya", columns(size, size,
                (x, z) -> (int) Math.round(50 - Math.max(0.0, 24 - Math.hypot(x - 32, z - 32)) * 0.5),
                "minecraft:grass_block"), null, NO_CANCEL);
        assertEquals(1.0, valley.stamp().neutral(), "solo hunde: gris neutro blanco");
        assertTrue(valley.below() > 10.0 && valley.above() == 0.0);

        StampCapture.Result both = StampCapture.build("las_dos", columns(size, size, (x, z) -> {
            double hill = Math.max(0.0, 14 - Math.hypot(x - 22, z - 32)) * 1.5;
            double hole = Math.max(0.0, 12 - Math.hypot(x - 44, z - 32)) * 0.8;
            return (int) Math.round(40 + hill - hole);
        }, "minecraft:grass_block"), null, NO_CANCEL);
        assertEquals(StampCapture.NEUTRAL_BOTH, both.stamp().neutral());
        // Proporciones de verdad: lo que sube y lo que hunde, cada uno con su altura.
        assertEquals(40.0 + both.above(), height(both, 22, 32, size, size), 1.0);
        assertEquals(40.0 - both.below(), height(both, 44, 32, size, size), 1.0);
        assertTrue(both.above() > both.below() * 1.5, "la colina es más alta que hondo el hoyo");
    }

    @Test
    void stairsBecomeASmoothRampThatStaysWithinHalfABlock() {
        int sizeX = 120;
        int sizeZ = 40;
        // Una cuesta suave de un bloque cada seis: en bloques, escalones.
        StampCapture.Columns columns = columns(sizeX, sizeZ, (x, z) -> 20 + x / 6, "minecraft:grass_block");
        StampCapture.Result result = StampCapture.build("cuesta", columns, null, NO_CANCEL);
        int steps = 0;
        double previous = Double.NaN;
        double previousSlope = Double.NaN;
        for (int x = 20; x < 100; x++) {
            double value = height(result, x, 20, sizeX, sizeZ);
            // Fuera del fundido del borde, nunca más de medio bloque de lo que había.
            if (x > 30 && x < 90) {
                assertEquals(20 + x / 6, value, 0.55, "se aparta de lo leído en x " + x);
            }
            if (!Double.isNaN(previous)) {
                double slope = value - previous;
                if (!Double.isNaN(previousSlope) && Math.abs(slope - previousSlope) > 0.12 && x > 30 && x < 90) {
                    steps++;
                }
                previousSlope = slope;
            }
            previous = value;
        }
        assertTrue(steps < 6, "la rampa sigue a escalones (" + steps + " quiebros)");
    }

    @Test
    void theEdgeFadesToTheBaseAndALongSelectionIsCentredInASquare() {
        StampCapture.Columns columns = columns(100, 30, (x, z) -> 25 + (int) Math.round(10
                * Math.sin(x * 0.09) * Math.sin(z * 0.2)), "minecraft:grass_block");
        StampCapture.Result result = StampCapture.build("franja", columns, null, NO_CANCEL);
        HeightStamp stamp = result.stamp();
        assertEquals(100, stamp.width());
        assertEquals(100, stamp.height());
        double neutral = stamp.neutral();
        assertEquals(neutral, stamp.pixel(50, 0), 1e-6, "el margen de arriba es neutro");
        assertEquals(neutral, stamp.pixel(50, 99), 1e-6, "el de abajo también");
        // La primera fila de lo leído ya está en el nivel base.
        assertEquals(neutral, stamp.pixel(50, 35), 0.01);
    }

    @Test
    void columnsOutsideTheShapeStayNeutralAndAlmostEmptyIsRefused() {
        StampCapture.Columns round = columns(40, 40, (x, z) -> Math.hypot(x - 20, z - 20) > 18 ? StampCapture.NONE
                : 30 + (int) Math.round(Math.max(0, 12 - Math.hypot(x - 20, z - 20))), "minecraft:stone");
        StampCapture.Result result = StampCapture.build("redondo", round, null, NO_CANCEL);
        assertEquals(result.stamp().neutral(), result.stamp().pixel(0, 0), 1e-6);
        StampCapture.Columns empty = columns(40, 40, (x, z) -> x < 5 ? 30 : StampCapture.NONE, null);
        assertThrows(IllegalArgumentException.class, () -> StampCapture.build("nada", empty, null, NO_CANCEL));
        assertThrows(IllegalArgumentException.class, () -> new StampCapture.Columns(4, 40));
        assertThrows(IllegalArgumentException.class, () -> new StampCapture.Columns(40, StampCapture.MAX_SIDE + 1));
    }

    @Test
    void theCategoryComesFromWhatIsOnTop() {
        StampCapture.Columns dunes = columns(40, 40, (x, z) -> 30 + (x + z) % 5 / 2, "minecraft:sand");
        assertEquals(StampCategory.DUNES, StampCapture.build("arena", dunes, null, NO_CANCEL).category());
        StampCapture.Columns lake = new StampCapture.Columns(40, 40);
        for (int z = 0; z < 40; z++) {
            for (int x = 0; x < 40; x++) {
                lake.set(x, z, 20 + (Math.hypot(x - 20, z - 20) > 12 ? 6 : 0), Math.hypot(x - 20, z - 20) <= 12,
                        "minecraft:sand");
            }
        }
        StampCapture.Result island = StampCapture.build("lago", lake, null, NO_CANCEL);
        assertEquals(StampCategory.ISLANDS, island.category());
        assertTrue(island.hadWater());
        assertEquals(StampCategory.VOLCANOES, StampCapture.build("pedido", dunes, StampCategory.VOLCANOES,
                NO_CANCEL).category(), "la que se pide manda");
    }

    @Test
    void theSixteenBitPngKeepsTheFineRelief() throws IOException {
        StampCapture.Result result = StampCapture.build("fino", columns(64, 64, (x, z) -> 10 + (int) Math.round(
                120 * Math.exp(-(Math.pow(x - 32, 2) + Math.pow(z - 32, 2)) / 300.0)), "minecraft:stone"), null,
                NO_CANCEL);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StampImages.write(result.stamp(), output, 16);
        HeightStamp read = StampImages.read("fino", new ByteArrayInputStream(output.toByteArray()));
        double span = Math.max(result.above(), result.below());
        for (int row = 0; row < 64; row += 3) {
            for (int column = 0; column < 64; column += 3) {
                double difference = Math.abs(read.pixel(column, row) - result.stamp().pixel(column, row)) * span;
                assertTrue(difference < 0.01, "el PNG pierde " + difference + " bloques");
            }
        }
        assertEquals(result.stamp().neutral(), read.neutral(), 1e-4);
    }
}
