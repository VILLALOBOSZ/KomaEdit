package com.komainu.moxia.deform;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceAndSmoothingTest {
    /** Cubo de 12 en una selección de 20 con margen de 4. */
    private static Volume cube() {
        return Volume.of(Shapes.snapshot(20, 20, 20, 4, (x, y, z) -> x >= 4 && y >= 4 && z >= 4 && x < 16 && y < 16
                && z < 16 ? (y == 15 ? Shapes.GRASS : Shapes.STONE) : Shapes.AIR));
    }

    @Test
    void fuzzifyOnlyTouchesTheBandAroundTheSurface() {
        Volume volume = cube();
        SurfaceOps.Settings settings = new SurfaceOps.Settings(2, SurfaceOps.Displacement.white(7), 0, 0,
                false, false, null, false);
        Shapes.Result result = Shapes.apply(volume, SurfaceOps.apply(volume, settings, Shapes.NO_CANCEL));
        assertTrue(result.stats.total() > 100, "la superficie cambia: " + result.stats.total());
        for (int index = 0; index < result.ids.length; index++) {
            if (result.ids[index] == volume.material[index]) {
                continue;
            }
            int x = volume.x(index);
            int y = volume.y(index);
            int z = volume.z(index);
            double inside = Math.min(Math.min(Math.min(x - 4, 15 - x), Math.min(y - 4, 15 - y)), Math.min(z - 4, 15 - z));
            assertTrue(inside < 3, "nada cambia a más del radio dentro: " + x + " " + y + " " + z);
            assertTrue(x >= 1 && y >= 1 && z >= 1 && x <= 18 && y <= 18 && z <= 18, "ni lejos por fuera");
        }
    }

    @Test
    void carveOnlyNeverAddsAndExpandOnlyNeverRemoves() {
        Volume volume = cube();
        Shapes.Result carved = Shapes.apply(volume, SurfaceOps.apply(volume, new SurfaceOps.Settings(3,
                SurfaceOps.Displacement.perlin(6, 2, 3), 1, 2, true, false, null, false), Shapes.NO_CANCEL));
        Shapes.Result grown = Shapes.apply(volume, SurfaceOps.apply(volume, new SurfaceOps.Settings(3,
                SurfaceOps.Displacement.perlin(6, 2, 3), 1, 2, false, true, null, false), Shapes.NO_CANCEL));
        assertTrue(carved.stats.total() > 0 && grown.stats.total() > 0);
        for (int index = 0; index < volume.material.length; index++) {
            if (volume.material[index] == Shapes.AIR) {
                assertEquals(Shapes.AIR, carved.ids[index], "-c no añade");
            } else {
                assertEquals(volume.material[index], grown.ids[index], "-e no quita");
            }
        }
        // Lo que crece toma el material más cercano: encima de la hierba, hierba.
        for (int index = 0; index < grown.ids.length; index++) {
            if (volume.material[index] == Shapes.AIR && grown.ids[index] != Shapes.AIR && volume.y(index) > 16) {
                assertEquals(Shapes.GRASS, grown.ids[index]);
            }
        }
    }

    @Test
    void maskLimitsWhatCanBeCarved() {
        Volume volume = cube();
        SurfaceOps.Settings onlyGrass = new SurfaceOps.Settings(3, SurfaceOps.Displacement.white(1), 0, 0,
                true, false, rawId -> rawId == Shapes.GRASS, false);
        Shapes.Result result = Shapes.apply(volume, SurfaceOps.apply(volume, onlyGrass, Shapes.NO_CANCEL));
        assertTrue(result.stats.total() > 0);
        for (int index = 0; index < result.ids.length; index++) {
            if (volume.material[index] == Shapes.STONE) {
                assertEquals(Shapes.STONE, result.ids[index], "la piedra no casa con la máscara");
            }
        }
    }

    @Test
    void topologyFlagKeepsOnePieceWithoutHoles() {
        Volume volume = cube();
        SurfaceOps.Settings wild = new SurfaceOps.Settings(4, SurfaceOps.Displacement.white(11), 0, 0,
                false, false, null, true);
        Shapes.Result result = Shapes.apply(volume, SurfaceOps.apply(volume, wild, Shapes.NO_CANCEL));
        assertTrue(result.stats.total() > 0);
        assertEquals(1, components(result, true), "sigue siendo una sola pieza");
        assertEquals(1, components(result, false), "y el aire no queda encerrado dentro");
    }

    @Test
    void surfaceSettingsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new SurfaceOps.Settings(2,
                SurfaceOps.Displacement.white(1), 0, 0, true, true, null, false));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceOps.Settings(0,
                SurfaceOps.Displacement.white(1), 0, 0, false, false, null, false));
        assertEquals(SurfaceOps.Mode.NOISIFY, SurfaceOps.Mode.byId("noiseify"));
        assertThrows(IllegalArgumentException.class, () -> SurfaceOps.Mode.byId("melt"));
    }

    @Test
    void smoothRoundsCornersAndRemovesSpikes() {
        Volume volume = Volume.of(Shapes.snapshot(20, 20, 20, 4, (x, y, z) -> {
            boolean cube = x >= 4 && y >= 4 && z >= 4 && x < 16 && y < 16 && z < 16;
            boolean spike = x == 10 && z == 10 && y >= 16 && y < 19;
            return cube || spike ? Shapes.STONE : Shapes.AIR;
        }));
        Shapes.Result smooth = Shapes.apply(volume, SmoothingOps.smooth(volume, new double[]{2, 2, 2}, 1, 0,
                Shapes.NO_CANCEL));
        assertEquals(Shapes.AIR, smooth.at(10, 18, 10), "la púa desaparece");
        assertEquals(Shapes.AIR, smooth.at(4, 4, 4), "las esquinas se redondean");
        assertEquals(Shapes.STONE, smooth.at(10, 10, 10));
        Shapes.Result fat = Shapes.apply(volume, SmoothingOps.smooth(volume, new double[]{2, 2, 2}, 1, 0.8,
                Shapes.NO_CANCEL));
        Shapes.Result thin = Shapes.apply(volume, SmoothingOps.smooth(volume, new double[]{2, 2, 2}, 1, -0.8,
                Shapes.NO_CANCEL));
        assertTrue(fat.occupied() > smooth.occupied() && smooth.occupied() > thin.occupied(),
                "el sesgo engorda o adelgaza");
    }

    @Test
    void inflateAndDeflateAreExactEllipsoids() {
        Volume single = Volume.of(Shapes.snapshot(9, 9, 9, 0, (x, y, z) -> x == 4 && y == 4 && z == 4
                ? Shapes.STONE : Shapes.AIR));
        Shapes.Result ball = Shapes.apply(single, SmoothingOps.inflate(single, new double[]{2, 2, 2}, Shapes.NO_CANCEL));
        assertEquals(33, ball.occupied(), "puntos de la red a distancia ≤ 2");
        Shapes.Result flat = Shapes.apply(single, SmoothingOps.inflate(single, new double[]{3, 0, 3}, Shapes.NO_CANCEL));
        for (int index = 0; index < flat.ids.length; index++) {
            if (flat.ids[index] != Shapes.AIR) {
                assertEquals(4, single.y(index), "radio 0 en Y: solo crece en horizontal");
            }
        }

        Volume block = Volume.of(Shapes.snapshot(7, 7, 7, 2, (x, y, z) -> x >= 1 && y >= 1 && z >= 1 && x < 6
                && y < 6 && z < 6 ? Shapes.STONE : Shapes.AIR));
        Shapes.Result shrunk = Shapes.apply(block, SmoothingOps.deflate(block, new double[]{1, 1, 1}, Shapes.NO_CANCEL));
        assertEquals(27, shrunk.occupied(), "un cubo de 5 encoge a uno de 3");
        assertThrows(IllegalArgumentException.class, () -> SmoothingOps.parseRadii("1,2", 10));
        assertThrows(IllegalArgumentException.class, () -> SmoothingOps.parseRadii("0", 10));
        double[] radii = SmoothingOps.parseRadii("2,1,3", 10);
        assertEquals(1.0, radii[1]);
    }

    @Test
    void smoothblocksPutsSlabsAndStairsOnTheSmoothedSurface() {
        Volume volume = Volume.of(Shapes.snapshot(24, 16, 24, 4, Shapes.sphere(12, 0, 12, 12)));
        Shapes.Result pieces = Shapes.apply(volume, SmoothingOps.smooth(volume, new double[]{2, 2, 2}, 1, 0,
                Shapes.NO_CANCEL), volume.selectionBounds(), SmoothProfile.parse("SlabsAndStairs"));
        assertTrue(pieces.stats.slabs() > 0 && pieces.stats.stairs() > 0,
                "una cúpula suavizada lleva losas y escaleras: " + pieces.stats);
    }

    /** Componentes conexas de lo ocupado o del aire, en toda la captura. */
    private static int components(Shapes.Result result, boolean occupied) {
        Volume volume = result.volume;
        boolean[] seen = new boolean[result.ids.length];
        int components = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        // Lo ocupado con conectividad 26 y el aire con 6, como la prueba de punto simple.
        java.util.List<int[]> steps = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int reach = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (reach != 0 && (occupied || reach == 1)) {
                        steps.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        for (int start = 0; start < result.ids.length; start++) {
            if (seen[start] || (result.ids[start] != Shapes.AIR) != occupied) {
                continue;
            }
            components++;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int index = queue.poll();
                for (int[] step : steps) {
                    int x = volume.x(index) + step[0];
                    int y = volume.y(index) + step[1];
                    int z = volume.z(index) + step[2];
                    if (!volume.contains(x, y, z)) {
                        continue;
                    }
                    int next = volume.index(x, y, z);
                    if (!seen[next] && (result.ids[next] != Shapes.AIR) == occupied) {
                        seen[next] = true;
                        queue.add(next);
                    }
                }
            }
        }
        return components;
    }
}
