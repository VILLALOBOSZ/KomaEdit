package com.komainu.moxia.shape.smooth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SmoothMaterialsTest {
    /** Muestra de identificadores que existen en Minecraft 1.21.1 (y uno de un mod). */
    private static final Set<String> REGISTRY = Set.of(
            "minecraft:stone_slab", "minecraft:stone_stairs",
            "minecraft:smooth_stone_slab",
            "minecraft:stone_brick_slab", "minecraft:stone_brick_stairs",
            "minecraft:oak_slab", "minecraft:oak_stairs",
            "minecraft:cut_sandstone_slab", "minecraft:sandstone_slab", "minecraft:sandstone_stairs",
            "minecraft:cobbled_deepslate_slab", "minecraft:cobbled_deepslate_stairs",
            "minecraft:deepslate_tile_slab", "minecraft:deepslate_tile_stairs",
            "minecraft:quartz_slab", "minecraft:quartz_stairs",
            "minecraft:smooth_quartz_slab", "minecraft:smooth_quartz_stairs",
            "minecraft:purpur_slab", "minecraft:purpur_stairs",
            "minecraft:brick_slab", "minecraft:brick_stairs",
            "minecraft:cut_copper_slab", "minecraft:waxed_oxidized_cut_copper_slab",
            "minecraft:crimson_slab", "minecraft:end_stone_brick_stairs",
            "mymod:marble_brick_slab"
    );

    @Test
    void conventionalNamesResolveDirectly() {
        assertEquals("minecraft:stone_slab", slab("minecraft:stone"));
        assertEquals("minecraft:stone_stairs", stairs("minecraft:stone"));
        assertEquals("minecraft:stone_brick_stairs", stairs("minecraft:stone_bricks"));
        assertEquals("minecraft:brick_slab", slab("minecraft:bricks"));
        assertEquals("minecraft:deepslate_tile_stairs", stairs("minecraft:cracked_deepslate_tiles"));
        assertEquals("minecraft:smooth_quartz_slab", slab("minecraft:smooth_quartz"));
        assertEquals("mymod:marble_brick_slab", slab("mymod:marble_bricks"), "también vale para mods");
        assertEquals("minecraft:stone_slab", slab("stone"), "sin espacio de nombres se asume minecraft");
    }

    @Test
    void woodLogsPillarsAndVariantsFindTheirFamily() {
        assertEquals("minecraft:oak_slab", slab("minecraft:oak_planks"));
        assertEquals("minecraft:oak_stairs", stairs("minecraft:stripped_oak_log"));
        assertEquals("minecraft:oak_slab", slab("minecraft:oak_wood"));
        assertEquals("minecraft:crimson_slab", slab("minecraft:crimson_hyphae"));
        assertEquals("minecraft:quartz_stairs", stairs("minecraft:chiseled_quartz_block"));
        assertEquals("minecraft:quartz_slab", slab("minecraft:quartz_pillar"));
        assertEquals("minecraft:purpur_stairs", stairs("minecraft:purpur_block"));
        assertEquals("minecraft:stone_brick_slab", slab("minecraft:chiseled_stone_bricks"));
    }

    @Test
    void tableCoversBlocksWithoutTheirOwnPieces() {
        // La piedra lisa tiene losa pero no escalera: la escalera sale de la piedra normal.
        assertEquals("minecraft:smooth_stone_slab", slab("minecraft:smooth_stone"));
        assertEquals("minecraft:stone_stairs", stairs("minecraft:smooth_stone"));
        assertEquals("minecraft:cut_sandstone_slab", slab("minecraft:cut_sandstone"));
        assertEquals("minecraft:sandstone_stairs", stairs("minecraft:cut_sandstone"));
        assertEquals("minecraft:cobbled_deepslate_stairs", stairs("minecraft:deepslate"));
        assertEquals("minecraft:end_stone_brick_stairs", stairs("minecraft:end_stone"));
        assertEquals("minecraft:cut_copper_slab", slab("minecraft:copper_block"));
        assertEquals("minecraft:waxed_oxidized_cut_copper_slab", slab("minecraft:waxed_oxidized_copper"));
    }

    @Test
    void blocksWithoutAnyFamilyAreLeftToTheColorFallback() {
        assertNull(slab("minecraft:white_concrete"));
        assertNull(stairs("minecraft:smooth_basalt"));
        assertEquals(List.of("smooth_basalt", "basalt"), SmoothMaterials.stems("smooth_basalt"));
    }

    private static String slab(String id) {
        return first(SmoothMaterials.slabIds(id));
    }

    private static String stairs(String id) {
        return first(SmoothMaterials.stairsIds(id));
    }

    private static String first(List<String> candidates) {
        return candidates.stream().filter(REGISTRY::contains).findFirst().orElse(null);
    }
}
