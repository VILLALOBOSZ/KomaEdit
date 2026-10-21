package com.komainu.moxia.shape.tube;

import com.komainu.moxia.FullTests;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TubeFiguresTest {
    private static final int LIMIT = 1_500_000;

    @Test
    void solidCircleMatchesCylinderVolume() {
        SampledPath path = straight(60);
        Set<BlockVector3> blocks = TubeFigures.generate(path, sweep(8, Map.of("quality", 0.0)), LIMIT);
        double expected = Math.PI * 8 * 8 * 60;
        assertEquals(expected, blocks.size(), expected * 0.04, "volumen de un cilindro r=8, L=60");
    }

    @Test
    void solidCrossSectionsHaveNoHoles() {
        SampledPath path = diagonal(40);
        Set<BlockVector3> blocks = TubeFigures.generate(path, sweep(6, Map.of()), LIMIT);
        // En el eje del tubo diagonal no puede faltar ningún bloque: con estampados sí pasaba.
        for (int step = 4; step <= 36; step++) {
            BlockVector3 axis = BlockVector3.at(step, step / 2, step);
            assertTrue(blocks.contains(axis), "hueco en el eje en " + axis);
        }
    }

    @Test
    void hollowWallIsClosedAndOpenAtFlatEnds() {
        SampledPath path = straight(30);
        Set<BlockVector3> blocks = TubeFigures.generate(path, sweep(7, Map.of("hollow", 1.0)), LIMIT);
        // Corte transversal en x=15: anillo cerrado (6-conexo) y vacío dentro.
        Set<BlockVector3> ring = new java.util.HashSet<>();
        for (BlockVector3 block : blocks) {
            if (block.x() == 15) {
                ring.add(block);
                double radial = Math.hypot(block.y() + 0.5 - 0.5, block.z() + 0.5 - 0.5);
                assertTrue(radial > 4.5 && radial < 8.5, "bloque fuera de la pared: " + block);
            }
        }
        assertFalse(ring.isEmpty());
        assertTrue(isRingClosed(ring), "la pared hueca debe ser un anillo cerrado");
        assertFalse(blocks.contains(BlockVector3.at(0, 0, 0)), "con extremo plano el tubo queda abierto");
    }

    @Test
    void roundEndsExtendBeyondEndpointsAndFlatDoNot() {
        SampledPath path = straight(20);
        Set<BlockVector3> flat = TubeFigures.generate(path, sweep(5, Map.of()), LIMIT);
        Set<BlockVector3> round = TubeFigures.generate(path,
                sweep(5, Map.of("end", (double) TubeSettings.EndMode.ROUND.ordinal())), LIMIT);
        int flatMinimum = flat.stream().mapToInt(BlockVector3::x).min().orElseThrow();
        int roundMinimum = round.stream().mapToInt(BlockVector3::x).min().orElseThrow();
        assertEquals(0, flatMinimum);
        assertTrue(roundMinimum <= -4, "la cúpula debe sobresalir casi un radio: " + roundMinimum);
    }

    @Test
    void curveScaleShrinksTheEndWithoutGaps() {
        SampledPath path = straight(40);
        TubeCurve curve = new TubeCurve(1.0, 0.5, 1.0, 1.0, 0.0, value -> value);
        Set<BlockVector3> blocks = TubeFigures.generate(path, sweep(8, Map.of("quality", 0.0)), curve, LIMIT);
        int startHeight = column(blocks, 1);
        int endHeight = column(blocks, 39);
        assertEquals(16, startHeight, 1);
        assertEquals(8, endHeight, 1);
        for (int x = 0; x < 40; x++) {
            assertTrue(blocks.contains(BlockVector3.at(x, 0, 0)), "hueco en el eje en x=" + x);
        }
    }

    @Test
    void closedLoopsKeepTheirFrameAcrossTheSeam() {
        List<BlockVector3> points = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * 2.0 * index / 8.0;
            points.add(BlockVector3.at(Math.round(Math.cos(angle) * 30), Math.round(Math.sin(angle) * 12),
                    Math.round(Math.sin(angle) * 30)));
        }
        SampledPath path = SampledPath.createSmooth(points, true);
        TubeSettings settings = TubeSettings.from(sweep(4, Map.of()));
        TubeCenterline line = TubeCenterline.create(path, settings, TubeCurve.NONE);
        int last = line.count - 1;
        double dot = line.ux[0] * line.ux[last] + line.uy[0] * line.uy[last] + line.uz[0] * line.uz[last];
        assertEquals(1.0, dot, 1.0E-6, "el marco debe empalmar al cerrar el lazo");
    }

    @Test
    void kochanekBartelsPassesThroughEveryControlPoint() {
        List<com.komainu.moxia.shape.Vec3> controls = List.of(
                new com.komainu.moxia.shape.Vec3(0, 0, 0),
                new com.komainu.moxia.shape.Vec3(10, 4, 0),
                new com.komainu.moxia.shape.Vec3(14, 9, 6),
                new com.komainu.moxia.shape.Vec3(30, 9, 6)
        );
        for (double tension : new double[]{-0.5, 0.0, 0.8}) {
            List<com.komainu.moxia.shape.Vec3> dense = TubeCenterline.kochanekBartels(controls, false, tension,
                    0.3, -0.2);
            for (com.komainu.moxia.shape.Vec3 control : controls) {
                double nearest = dense.stream().mapToDouble(point -> point.distance(control)).min().orElseThrow();
                assertTrue(nearest < 1.0E-9, "la curva debe pasar por " + control);
            }
        }
    }

    @Test
    void radiusProgressionParsesKeyframesAndStaysMonotonic() {
        TubeSettings.RadiusProgression.Parsed parsed = TubeSettings.RadiusProgression.parse("2,0.25:12,2");
        assertEquals(12, parsed.baseRadius());
        ShapeOptions options = new ShapeOptions(parsed.options());
        TubeSettings.RadiusProgression progression = TubeSettings.RadiusProgression.fromOptions(options);
        assertEquals(2.0 / 12.0, progression.at(0.0), 1.0E-9);
        assertEquals(1.0, progression.at(0.25), 1.0E-9);
        assertEquals(2.0 / 12.0, progression.at(1.0), 1.0E-9);
        double previous = progression.at(0.25);
        for (int step = 26; step <= 100; step++) {
            double current = progression.at(step / 100.0);
            assertTrue(current <= previous + 1.0E-12, "sin rebotes tras el máximo");
            previous = current;
        }
        assertThrows(IllegalArgumentException.class, () -> TubeSettings.RadiusProgression.parse("0.5:3,4"));
        assertThrows(IllegalArgumentException.class, () -> TubeSettings.RadiusProgression.parse("3,0.6:4,0.4:5,3"));
    }

    @Test
    void everyProfileProducesConnectedGeometry() {
        SampledPath path = diagonal(30);
        for (TubeSettings.TubeProfile profile : TubeSettings.TubeProfile.values()) {
            Set<BlockVector3> blocks = TubeFigures.generate(path,
                    sweep(7, Map.of("profile", (double) profile.ordinal())), LIMIT);
            assertTrue(blocks.size() > 500, profile + " generó muy pocos bloques: " + blocks.size());
        }
    }

    @Test
    void braidAndNoiseTubeAreDeterministic() {
        SampledPath path = diagonal(36);
        for (PathShape shape : List.of(PathShape.BRAID, PathShape.NOISE_TUBE)) {
            Set<BlockVector3> first = TubeFigures.generate(path, shape.defaults(), LIMIT);
            Set<BlockVector3> second = TubeFigures.generate(path, shape.defaults(), LIMIT);
            assertFalse(first.isEmpty(), shape.id());
            assertEquals(first, second, shape.id() + " debe ser estable entre holograma y Build");
        }
    }

    @Test
    void largeTubesStayFast() {
        SampledPath path = diagonal(240);
        long start = System.nanoTime();
        Set<BlockVector3> blocks = TubeFigures.generate(path, sweep(14, Map.of()), LIMIT);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(blocks.size() > 100_000);
        FullTests.assertFasterThan(elapsed, 8_000, "demasiado lento: " + elapsed + " ms para " + blocks.size()
                + " bloques");
    }

    private static ShapeParameters sweep(int radius, Map<String, Double> options) {
        return new ShapeParameters(PathShape.SWEEP, radius, 6, 1, 0, new ShapeOptions(new HashMap<>(options)));
    }

    private static SampledPath straight(int length) {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(length, 0, 0)), false);
    }

    private static SampledPath diagonal(int length) {
        return SampledPath.createSmooth(List.of(
                BlockVector3.at(0, 0, 0),
                BlockVector3.at(length / 2, length / 4, length / 2),
                BlockVector3.at(length, length / 2, length)
        ), false);
    }

    private static int column(Set<BlockVector3> blocks, int x) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (BlockVector3 block : blocks) {
            if (block.x() == x && block.z() == 0) {
                minimum = Math.min(minimum, block.y());
                maximum = Math.max(maximum, block.y());
            }
        }
        return maximum - minimum + 1;
    }

    /** Un anillo es cerrado si su interior (el centro) no se conecta con el exterior en 4-vecindad. */
    private static boolean isRingClosed(Set<BlockVector3> ring) {
        int x = ring.iterator().next().x();
        Set<Long> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{0, 0});
        visited.add(0L);
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if (Math.abs(cell[0]) > 12 || Math.abs(cell[1]) > 12) {
                return false;
            }
            int[][] neighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : neighbors) {
                int y = cell[0] + offset[0];
                int z = cell[1] + offset[1];
                long key = ((long) y << 32) ^ (z & 0xFFFFFFFFL);
                if (ring.contains(BlockVector3.at(x, y, z)) || !visited.add(key)) {
                    continue;
                }
                queue.add(new int[]{y, z});
            }
        }
        return true;
    }
}
