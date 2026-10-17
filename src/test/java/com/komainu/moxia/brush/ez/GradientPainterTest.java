package com.komainu.moxia.brush.ez;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradientPainterTest {
    @Test
    void interpolationsBehaveAsDocumented() {
        assertEquals(0.3, GradientInterpolation.NONE.apply(0.3, 0.9), 1.0E-9, "NONE ignora el ruido");
        assertEquals(0.5, GradientInterpolation.LINEAR.apply(0.3, 0.2), 1.0E-9);
        assertEquals(0.1, GradientInterpolation.LINEAR.apply(0.0, 0.1), 1.0E-9, "LINEAR se sale por los extremos");
        assertEquals(0.0, GradientInterpolation.TAPERED.apply(0.0, 0.5), 1.0E-9, "TAPERED no mueve los extremos");
        assertEquals(1.0, GradientInterpolation.TAPERED.apply(1.0, -0.5), 1.0E-9);
        assertEquals(0.7, GradientInterpolation.TAPERED.apply(0.5, 0.2), 1.0E-9, "y empuja entero en el centro");
        assertEquals(0.0, GradientInterpolation.SIN.apply(0.0, 0.5), 1.0E-9);
        assertEquals(0.5, GradientInterpolation.BEZIER.apply(0.5, 0.0), 1.0E-9, "sin ruido, Bézier es la recta");
        assertEquals(1.0, GradientInterpolation.BEZIER.apply(1.0, 0.8), 1.0E-9);
    }

    @Test
    void bezierStaysMonotonicUpToBleedOne() {
        for (double offset : new double[]{-1.0, -0.5, 0.5, 1.0}) {
            double previous = -1.0;
            for (int step = 0; step <= 100; step++) {
                double value = GradientInterpolation.BEZIER.apply(step / 100.0, offset);
                assertTrue(value >= previous - 1.0E-12, "Bézier no retrocede con sangrado " + offset);
                previous = value;
            }
        }
    }

    @Test
    void namesAndIndices() {
        assertEquals(GradientInterpolation.TAPERED, GradientInterpolation.byName("Tapered"));
        assertEquals(GradientInterpolation.SIN, GradientInterpolation.byName("seno"));
        assertThrows(IllegalArgumentException.class, () -> GradientInterpolation.byName("cubic"));
        assertEquals(0, GradientInterpolation.paletteIndex(-0.2, 4));
        assertEquals(1, GradientInterpolation.paletteIndex(0.26, 4));
        assertEquals(3, GradientInterpolation.paletteIndex(1.0, 4), "el final es la última entrada");
        assertEquals(3, GradientInterpolation.paletteIndex(7.0, 4));
    }

    @Test
    void planeRampProjectsOnTheLine() {
        GradientPainter.Ramp ramp = GradientPainter.plane(BlockVector3.at(0, 0, 0), BlockVector3.at(10, 0, 0));
        assertEquals(0.0, ramp.t(0.5, 0.5, 0.5), 1.0E-9);
        assertEquals(1.0, ramp.t(10.5, 7.5, -3.5), 1.0E-9, "el plano del final es perpendicular a la recta");
        assertEquals(0.5, ramp.t(5.5, 40, 40), 1.0E-9);
        assertThrows(IllegalArgumentException.class,
                () -> GradientPainter.plane(BlockVector3.at(1, 2, 3), BlockVector3.at(1, 2, 3)));
    }

    @Test
    void distanceRampMeasuresFromTheStart() {
        BlockVector3 start = BlockVector3.at(0, 0, 0);
        BlockVector3 end = BlockVector3.at(4, 0, 0);
        assertEquals(1.0, GradientPainter.distance(start, end, DistanceFunction.EUCLIDEAN).t(0.5, 4.5, 0.5), 1.0E-9);
        assertEquals(1.5, GradientPainter.distance(start, end, DistanceFunction.MANHATTAN).t(3.5, 3.5, 0.5), 1.0E-9);
        assertEquals(0.75, GradientPainter.distance(start, end, DistanceFunction.CHEBYSHEV).t(3.5, 1.5, 2.5),
                1.0E-9);
        assertEquals(null, DistanceFunction.byName("none"));
    }

    @Test
    void sphereWithoutNoisePaintsCleanBands() {
        FakeWorld world = new FakeWorld(12);
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                world.set(x, 0, z, "stone", BrushWorld.Cell.FULL);
            }
        }
        world.set(0, 1, 0, "oak_stairs", BrushWorld.Cell.SHAPED);
        GradientPainter.Ramp ramp = GradientPainter.plane(BlockVector3.at(-8, 0, 0), BlockVector3.at(8, 0, 0));
        GradientPainter.Settings settings = new GradientPainter.Settings(4, GradientInterpolation.NONE, 0.5,
                BrushNoise.white(1), false, false);
        Map<Integer, Integer> byX = new HashMap<>();
        int count = GradientPainter.paintSphere(world, BlockVector3.at(0, 0, 0), 8, ramp, settings,
                (x, y, z, index) -> {
                    Integer previous = byX.put(x, index);
                    assertTrue(previous == null || previous == index, "sin ruido, una columna es un solo color");
                    assertEquals(0, y, "ni el aire ni la escalera se pintan sin -a / -w");
                });
        assertTrue(count > 150);
        assertEquals(0, byX.get(-8));
        assertEquals(3, byX.get(8));
        for (int x = -7; x <= 8; x++) {
            assertTrue(byX.get(x) >= byX.get(x - 1), "el degradado no retrocede");
        }
    }

    @Test
    void bleedMixesNeighbouringBandsOnly() {
        FakeWorld world = new FakeWorld(12);
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                world.set(x, 0, z, "stone", BrushWorld.Cell.FULL);
            }
        }
        GradientPainter.Ramp ramp = GradientPainter.plane(BlockVector3.at(-8, 0, 0), BlockVector3.at(8, 0, 0));
        GradientPainter.Settings settings = new GradientPainter.Settings(4, GradientInterpolation.TAPERED, 0.12,
                BrushNoise.white(42), false, false);
        Map<Integer, java.util.Set<Integer>> colours = new HashMap<>();
        GradientPainter.paintSphere(world, BlockVector3.at(0, 0, 0), 8, ramp, settings,
                (x, y, z, index) -> colours.computeIfAbsent(x, key -> new java.util.HashSet<>()).add(index));
        assertTrue(colours.values().stream().anyMatch(set -> set.size() > 1), "el sangrado mezcla bandas");
        assertEquals(java.util.Set.of(0), colours.get(-8), "TAPERED no ensucia el principio");
        colours.values().forEach(set -> assertTrue(set.stream().mapToInt(Integer::intValue).max().getAsInt()
                - set.stream().mapToInt(Integer::intValue).min().getAsInt() <= 1, "solo se mezclan vecinos"));
    }

    @Test
    void airAndShapesNeedTheirFlags() {
        assertFalse(GradientPainter.paintable(BrushWorld.Cell.AIR, settings(false, false)));
        assertTrue(GradientPainter.paintable(BrushWorld.Cell.AIR, settings(true, false)));
        assertFalse(GradientPainter.paintable(BrushWorld.Cell.SHAPED, settings(false, false)));
        assertTrue(GradientPainter.paintable(BrushWorld.Cell.SHAPED, settings(false, true)));
        assertFalse(GradientPainter.paintable(BrushWorld.Cell.OTHER, settings(true, true)), "flores y vallas nunca");
    }

    @Test
    void strokeFollowsThePathAndItsAxis() {
        StrokePath path = new StrokePath(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(10, 0, 0),
                BlockVector3.at(10, 0, 10)));
        assertEquals(20.0, path.length(), 1.0E-9);
        StrokePath.Closest middle = path.closest(10.5, 0.5, 0.5);
        assertEquals(0.5, middle.along(), 1.0E-9, "la esquina es la mitad del recorrido");
        assertEquals(2.0, path.closest(5.5, 2.5, 0.5).distance(), 1.0E-9);

        FakeWorld world = new FakeWorld(16);
        GradientPainter.Settings fill = new GradientPainter.Settings(2, GradientInterpolation.NONE, 0, BrushNoise.white(0),
                true, false);
        Map<Long, Integer> along = new HashMap<>();
        GradientPainter.paintStroke(world, path, 1, false, fill, (x, y, z, index) -> along.put(key(x, z, y), index));
        assertEquals(0, along.get(key(0, 0, 0)));
        assertEquals(1, along.get(key(10, 10, 0)));
        Map<Long, Integer> radial = new HashMap<>();
        GradientPainter.paintStroke(world, path, 2, true, fill, (x, y, z, index) -> radial.put(key(x, z, y), index));
        assertEquals(0, radial.get(key(5, 0, 0)), "el eje es el principio del degradado radial");
        assertEquals(1, radial.get(key(5, 0, 2)), "el borde del tubo, el final");
    }

    private static long key(int x, int z, int y) {
        return x * 1_000_000L + z * 1_000L + y;
    }

    private static GradientPainter.Settings settings(boolean air, boolean shaped) {
        return new GradientPainter.Settings(3, GradientInterpolation.LINEAR, 0.5, BrushNoise.white(0), air, shaped);
    }
}
