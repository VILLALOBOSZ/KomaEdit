package com.komainu.moxia.region;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionRulesTest {
    private static final Set<String> BLOCKS = Set.of("minecraft:stone", "minecraft:stone_bricks",
            "minecraft:oak_planks", "minecraft:quartz_block", "minecraft:smooth_stone", "minecraft:bricks",
            "minecraft:deepslate_tiles", "minecraft:cut_copper", "minecraft:waxed_cut_copper", "minecraft:purpur_block",
            "minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:bamboo_planks", "minecraft:bamboo_mosaic");

    @Test
    void slabsBecomeTheirFullBlock() {
        assertEquals("minecraft:stone", RegionRules.fullBlock("minecraft:stone_slab", BLOCKS::contains));
        assertEquals("minecraft:stone_bricks", RegionRules.fullBlock("minecraft:stone_brick_slab", BLOCKS::contains));
        assertEquals("minecraft:oak_planks", RegionRules.fullBlock("minecraft:oak_slab", BLOCKS::contains));
        assertEquals("minecraft:oak_planks", RegionRules.fullBlock("minecraft:petrified_oak_slab", BLOCKS::contains));
        assertEquals("minecraft:quartz_block", RegionRules.fullBlock("minecraft:quartz_slab", BLOCKS::contains));
        assertEquals("minecraft:smooth_stone", RegionRules.fullBlock("minecraft:smooth_stone_slab", BLOCKS::contains));
        assertEquals("minecraft:bricks", RegionRules.fullBlock("minecraft:brick_slab", BLOCKS::contains));
        assertEquals("minecraft:deepslate_tiles", RegionRules.fullBlock("minecraft:deepslate_tile_slab",
                BLOCKS::contains));
        assertEquals("minecraft:waxed_cut_copper", RegionRules.fullBlock("minecraft:waxed_cut_copper_slab",
                BLOCKS::contains));
        assertEquals("minecraft:purpur_block", RegionRules.fullBlock("minecraft:purpur_slab", BLOCKS::contains));
        assertEquals("minecraft:cut_sandstone", RegionRules.fullBlock("minecraft:cut_sandstone_slab", BLOCKS::contains));
        assertEquals("minecraft:bamboo_planks", RegionRules.fullBlock("minecraft:bamboo_slab", BLOCKS::contains));
        assertEquals("minecraft:bamboo_mosaic", RegionRules.fullBlock("minecraft:bamboo_mosaic_slab", BLOCKS::contains));
        assertNull(RegionRules.fullBlock("minecraft:stone", BLOCKS::contains));
        assertNull(RegionRules.fullBlock("minecraft:unknown_slab", BLOCKS::contains));
    }

    @Test
    void cycleWrapsBothWays() {
        assertEquals(1, RegionRules.cycle(4, 0, 1));
        assertEquals(0, RegionRules.cycle(4, 3, 1));
        assertEquals(3, RegionRules.cycle(4, 0, -1));
        assertEquals(2, RegionRules.cycle(4, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> RegionRules.cycle(0, 0, 1));
    }

    @Test
    void percentagesAndRadii() {
        assertEquals(0.1, RegionRules.percentage("10"), 1.0E-9);
        assertEquals(0.255, RegionRules.percentage("25.5%"), 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> RegionRules.percentage("120"));
        assertArrayEquals(new int[]{2, 2, 2}, RegionRules.radii("2"));
        assertArrayEquals(new int[]{3, 1, 2}, RegionRules.radii("3,1,2"));
        assertThrows(IllegalArgumentException.class, () -> RegionRules.radii("1,2"));
        assertThrows(IllegalArgumentException.class, () -> RegionRules.radii("9"));
    }

    @Test
    void hangingPlantsHaveStems() {
        assertEquals("minecraft:weeping_vines_plant", RegionRules.hangingPlant("minecraft:weeping_vines"));
        assertEquals("minecraft:cave_vines_plant", RegionRules.hangingPlant("minecraft:cave_vines"));
        assertNull(RegionRules.hangingPlant("minecraft:vine"));
    }

    @Test
    void mossCoversTheRequestedShareInBlobs() {
        List<int[]> floor = new ArrayList<>();
        for (int x = 0; x < 60; x++) {
            for (int z = 0; z < 60; z++) {
                floor.add(new int[]{x, 64, z});
            }
        }
        int[][] positions = floor.toArray(int[][]::new);
        boolean[] moss = RegionRules.moss(positions, 2.0, new int[]{1, 1, 1}, 5, 4L, () -> { });
        int covered = count(moss);
        assertEquals(0.5, covered / (double) positions.length, 0.02, "cantidad 2: la mitad");
        assertEquals(RegionRules.coverage(6.0), count(RegionRules.moss(positions, 6.0, new int[]{1, 1, 1}, 5, 4L,
                () -> { })) / (double) positions.length, 0.02);
        // Las manchas son grandes: la mayoría de los vecinos de un bloque con musgo también lo tienen.
        assertTrue(sameNeighbours(moss) > 0.75, "manchas, no sal y pimienta: " + sameNeighbours(moss));
        boolean[] noisy = RegionRules.moss(positions, 2.0, new int[]{1, 1, 1}, 0, 4L, () -> { });
        assertTrue(sameNeighbours(noisy) < 0.6, "sin suavizado queda ruido: " + sameNeighbours(noisy));
        assertEquals(0, count(RegionRules.moss(positions, 0.0, new int[]{1, 1, 1}, 5, 4L, () -> { })));
    }

    private static int count(boolean[] values) {
        int count = 0;
        for (boolean value : values) {
            count += value ? 1 : 0;
        }
        return count;
    }

    /** En el suelo de 60×60 del test: parte de vecinos (4) con el mismo valor. */
    private static double sameNeighbours(boolean[] values) {
        int same = 0;
        int total = 0;
        for (int x = 0; x < 60; x++) {
            for (int z = 0; z < 59; z++) {
                same += values[x * 60 + z] == values[x * 60 + z + 1] ? 1 : 0;
                total++;
            }
        }
        return same / (double) total;
    }
}
