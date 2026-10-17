package com.komainu.moxia.generator;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseGeneratorTest {
    private static final PaletteTraits STRATA = PaletteTraits.of(List.of("stone", "dirt", "grass_block"));
    private static final Volume BOX = Volume.box(0, 10, 0, 31, 25, 31);

    private static GeneratedBlocks run(NoiseGenerator.Mode mode, String args, PaletteTraits palette, Volume volume,
                                       NoiseGenerator.Surface surface) {
        GeneratorRequests.NoiseRequest request = GeneratorRequests.noise(mode, args, () -> 42L);
        GeneratedBlocks blocks = new GeneratedBlocks(2_000_000);
        NoiseGenerator.generate(request.settings(), volume, surface, palette, -64, 319, blocks);
        return blocks;
    }

    private static Map<Long, int[]> columns(GeneratedBlocks blocks) {
        Map<Long, int[]> columns = new HashMap<>();
        for (int index = 0; index < blocks.size(); index++) {
            long key = (long) blocks.x(index) << 32 | (blocks.z(index) & 0xFFFFFFFFL);
            int[] range = columns.computeIfAbsent(key, ignored -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE, 0});
            range[0] = Math.min(range[0], blocks.y(index));
            range[1] = Math.max(range[1], blocks.y(index));
            range[2]++;
        }
        return columns;
    }

    @Test
    void heightmapColumnsAreSolidFromTheFloor() {
        GeneratedBlocks blocks = run(NoiseGenerator.Mode.HEIGHTMAP, "x Perlin(F:0.05)", STRATA, BOX, null);
        assertTrue(blocks.size() > 0);
        for (int[] range : columns(blocks).values()) {
            assertEquals(10, range[0], "cada columna empieza en el suelo de la selección");
            assertEquals(range[1] - range[0] + 1, range[2], "sin huecos");
            assertTrue(range[1] <= 25);
        }
        for (int index = 0; index < blocks.size(); index++) {
            int expected = Math.min(2, (int) Math.floor((blocks.y(index) - 10 + 0.5) / 16.0 * 3));
            assertEquals(expected, blocks.material(index), "la paleta va por alturas");
        }
    }

    @Test
    void sameSeedSameTerrainAndOtherSeedDiffers() {
        GeneratedBlocks first = run(NoiseGenerator.Mode.HEIGHTMAP, "x Perlin(F:0.05) -s 9", STRATA, BOX, null);
        GeneratedBlocks second = run(NoiseGenerator.Mode.HEIGHTMAP, "x Perlin(F:0.05) -s 9", STRATA, BOX, null);
        GeneratedBlocks other = run(NoiseGenerator.Mode.HEIGHTMAP, "x Perlin(F:0.05) -s 10", STRATA, BOX, null);
        assertEquals(first.size(), second.size());
        for (int index = 0; index < first.size(); index++) {
            assertEquals(first.packed(index), second.packed(index));
        }
        assertFalse(first.size() == other.size() && columns(first).equals(columns(other)));
    }

    @Test
    void heightCanGoAboveTheSelection() {
        GeneratedBlocks blocks = run(NoiseGenerator.Mode.HEIGHTMAP, "x Perlin(F:0.05) 60", STRATA, BOX, null);
        int highest = Integer.MIN_VALUE;
        for (int index = 0; index < blocks.size(); index++) {
            highest = Math.max(highest, blocks.y(index));
        }
        assertTrue(highest > 25, "con altura 60 sale por encima del techo de la selección");
    }

    @Test
    void maskSurfaceIsTheBaseAndClipsToTheSelection() {
        NoiseGenerator.Surface surface = (x, z) -> x < 16 ? 14 : NoiseGenerator.Surface.NONE;
        GeneratedBlocks blocks = run(NoiseGenerator.Mode.HEIGHTMAP, "x Perlin(F:0.05) 60", STRATA, BOX, surface);
        for (int index = 0; index < blocks.size(); index++) {
            assertTrue(blocks.x(index) < 16, "sin bloque de máscara no se genera");
            assertTrue(blocks.y(index) >= 15 && blocks.y(index) <= 25);
        }
    }

    @Test
    void smoothModeMakesFlatLakesAndSnowLayers() {
        PaletteTraits palette = PaletteTraits.of(List.of("water", "sand", "stone", "snow"));
        Volume volume = Volume.box(0, 0, 0, 47, 39, 47);
        GeneratedBlocks blocks = run(NoiseGenerator.Mode.HEIGHTMAP, "x Si(F:0.03,fT:F,fO:4) -t", palette, volume,
                null);
        Map<Long, Integer> topWater = new HashMap<>();
        boolean layered = false;
        for (int index = 0; index < blocks.size(); index++) {
            int material = blocks.material(index);
            if (material == 3) {
                assertTrue(blocks.layers(index) >= 1, "la nieve siempre lleva capas");
                layered |= blocks.layers(index) < 8;
            } else {
                assertEquals(0, blocks.layers(index));
            }
            if (material == 0) {
                long key = (long) blocks.x(index) << 32 | (blocks.z(index) & 0xFFFFFFFFL);
                topWater.merge(key, blocks.y(index), Math::max);
            }
        }
        assertTrue(layered, "hay capas sueltas en las cumbres");
        // El agua llega siempre al techo de su franja (40 / 4 = 10): superficie plana.
        for (int top : topWater.values()) {
            assertEquals(9, top);
        }
        // Bajo tierra no queda agua: en las columnas altas la franja de agua es arena.
        Map<Long, int[]> columns = columns(blocks);
        for (int index = 0; index < blocks.size(); index++) {
            long key = (long) blocks.x(index) << 32 | (blocks.z(index) & 0xFFFFFFFFL);
            if (columns.get(key)[1] > 12 && blocks.y(index) < 10) {
                assertTrue(blocks.material(index) != 0);
            }
        }
    }

    @Test
    void terrainDensityAndStrength() {
        assertEquals(1.0, NoiseGenerator.strength(new double[]{1.0, 0.5, 0.0}, 0.0), 1e-9);
        assertEquals(0.5, NoiseGenerator.strength(new double[]{1.0, 0.5, 0.0}, 0.5), 1e-9);
        assertEquals(0.25, NoiseGenerator.strength(new double[]{1.0, 0.5, 0.0}, 0.75), 1e-9);
        assertEquals(0.35, NoiseGenerator.strength(new double[]{0.7, 0.0}, 0.5), 1e-9);
        assertEquals(0.5, NoiseGenerator.strength(new double[]{0.5}, 0.9), 1e-9);
        // Sin fuerza, un llano a media altura.
        assertTrue(NoiseGenerator.density(0.9, 0.49, 0.0) > 0.5);
        assertFalse(NoiseGenerator.density(0.9, 0.51, 0.0) > 0.5);
        GeneratedBlocks flat = run(NoiseGenerator.Mode.TERRAIN, "x Perlin(F:0.05) 0 0", STRATA, BOX, null);
        for (int[] range : columns(flat).values()) {
            assertEquals(17, range[1], "con fuerza 0 todo el terreno acaba a media altura");
        }
    }

    @Test
    void terrainWithStrengthMakesOverhangs() {
        GeneratedBlocks blocks = run(NoiseGenerator.Mode.TERRAIN, "x Perlin(F:0.08) 0 1", STRATA,
                Volume.box(0, 0, 0, 31, 31, 31), null);
        int gaps = 0;
        for (int[] range : columns(blocks).values()) {
            if (range[1] - range[0] + 1 != range[2]) {
                gaps++;
            }
        }
        assertTrue(gaps > 0, "el ruido 3D abre cuevas o voladizos");
    }

    @Test
    void advancedRespectsThresholds() {
        GeneratedBlocks all = run(NoiseGenerator.Mode.ADVANCED, "x Perlin(F:0.05) 0 1", STRATA, BOX, null);
        assertEquals(32 * 16 * 32, all.size(), "de 0 a 1 se llena la selección");
        GeneratedBlocks band = run(NoiseGenerator.Mode.ADVANCED, "x Perlin(F:0.05) 0.45 0.55", STRATA, BOX, null);
        assertTrue(band.size() > 0 && band.size() < all.size());
        GeneratedBlocks formula = run(NoiseGenerator.Mode.ADVANCED, "x Perlin(F:0.05) 0 \"t < 0.5 ? 1 : 0\"",
                STRATA, BOX, null);
        for (int index = 0; index < formula.size(); index++) {
            assertTrue(formula.y(index) < 18, "la fórmula del umbral ve la altura relativa t");
        }
    }

    @Test
    void advancedHeightmapUsesThresholdsAsRange() {
        GeneratedBlocks blocks = run(NoiseGenerator.Mode.ADVANCED, "x Perlin(F:0.05) 0 1 -h", STRATA, BOX, null);
        for (int[] range : columns(blocks).values()) {
            assertEquals(10, range[0]);
            assertEquals(range[1] - range[0] + 1, range[2]);
        }
    }

    @Test
    void cylinderSelectionIsRespected() {
        Volume cylinder = new Volume() {
            @Override
            public boolean contains(int x, int y, int z) {
                return (x - 16) * (x - 16) + (z - 16) * (z - 16) <= 100 && y >= 0 && y <= 9;
            }

            @Override
            public int minimumX() {
                return 6;
            }

            @Override
            public int minimumY() {
                return 0;
            }

            @Override
            public int minimumZ() {
                return 6;
            }

            @Override
            public int maximumX() {
                return 26;
            }

            @Override
            public int maximumY() {
                return 9;
            }

            @Override
            public int maximumZ() {
                return 26;
            }
        };
        GeneratedBlocks blocks = run(NoiseGenerator.Mode.ADVANCED, "x Perlin(F:0.05) 0 1", STRATA, cylinder, null);
        for (int index = 0; index < blocks.size(); index++) {
            assertTrue(cylinder.contains(blocks.x(index), blocks.y(index), blocks.z(index)));
        }
    }

    @Test
    void requestValidation() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.noise(NoiseGenerator.Mode.ADVANCED,
                "x Perlin() 0.8 0.2", () -> 1L));
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.noise(NoiseGenerator.Mode.ADVANCED,
                "x Perlin() 2", () -> 1L));
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.noise(NoiseGenerator.Mode.HEIGHTMAP,
                "x Perlin() 10 0.5", () -> 1L));
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.noise(NoiseGenerator.Mode.HEIGHTMAP,
                "x Perlin() -l 2", () -> 1L));
        assertThrows(IllegalArgumentException.class, () -> GeneratorRequests.noise(NoiseGenerator.Mode.TERRAIN,
                "x Perlin() 0 1,2,3,4", () -> 1L));
        GeneratorRequests.NoiseRequest request = GeneratorRequests.noise(NoiseGenerator.Mode.HEIGHTMAP,
                "##Grayscale Si(F:0.1) 12 -z 4 -s -1 -o 1,2,3 -m grass_block -cty", () -> 77L);
        assertEquals("##Grayscale", request.palette());
        assertEquals("grass_block", request.mask());
        assertEquals(77L, request.settings().seed(), "-s -1 es semilla al azar");
        assertEquals(4.0, request.settings().zoom());
        assertTrue(request.settings().centred() && request.settings().smooth() && request.settings().force3d());
        assertEquals(GeneratorRequests.DEFAULT_NOISE,
                GeneratorRequests.noise(NoiseGenerator.Mode.TERRAIN, "stone", () -> 1L).settings().noise().toString());
    }
}
