package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@code roof gable bamboo_stairs}: el bloque entero salía {@code bamboo}, la planta, y el tejado era de cañas. */
class RoofFullBlockTest {
    private static final Set<String> BLOCKS = Set.of("bamboo", "bamboo_planks", "bamboo_block", "bamboo_mosaic",
            "stone", "oak_planks", "quartz_block", "stone_bricks", "nether_bricks");

    @Test
    void piecesFindTheirFullBlock() {
        assertEquals("bamboo_planks", RoofCommands.fullBlockPath("bamboo_stairs", BLOCKS::contains));
        assertEquals("bamboo_planks", RoofCommands.fullBlockPath("bamboo_slab", BLOCKS::contains));
        assertEquals("bamboo_mosaic", RoofCommands.fullBlockPath("bamboo_mosaic_stairs", BLOCKS::contains));
        assertEquals("stone", RoofCommands.fullBlockPath("stone_stairs", BLOCKS::contains));
        assertEquals("oak_planks", RoofCommands.fullBlockPath("oak_stairs", BLOCKS::contains));
        assertEquals("quartz_block", RoofCommands.fullBlockPath("quartz_slab", BLOCKS::contains));
        assertEquals("stone_bricks", RoofCommands.fullBlockPath("stone_brick_stairs", BLOCKS::contains));
        assertEquals("nether_bricks", RoofCommands.fullBlockPath("nether_brick_slab", BLOCKS::contains));
        assertNull(RoofCommands.fullBlockPath("mystery_stairs", BLOCKS::contains));
    }
}
