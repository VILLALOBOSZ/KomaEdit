package com.komainu.moxia.texture;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteRemapTest {
    private static final Set<String> BLOCKS = Set.of(
            "minecraft:stone", "minecraft:stone_slab", "minecraft:stone_stairs",
            "minecraft:cobblestone", "minecraft:cobblestone_slab", "minecraft:cobblestone_stairs",
            "minecraft:cobblestone_wall",
            "minecraft:dirt",
            "minecraft:stone_bricks", "minecraft:stone_brick_slab", "minecraft:stone_brick_stairs",
            "minecraft:stone_brick_wall",
            "minecraft:oak_planks", "minecraft:oak_slab", "minecraft:oak_fence", "minecraft:oak_fence_gate"
    );

    private static PaletteRemap remap(String... materials) {
        return new PaletteRemap(List.of(materials), BLOCKS::contains);
    }

    @Test
    void findsShapeVariantsByName() {
        PaletteRemap remap = remap("minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:dirt");
        assertEquals("minecraft:stone_brick_stairs", remap.variant(0, "_stairs"));
        assertEquals("minecraft:stone_brick_wall", remap.variant(0, "_wall"));
        assertEquals("minecraft:oak_fence_gate", remap.variant(1, "_fence_gate"));
        assertEquals("minecraft:oak_fence", remap.variant(1, "_fence"));
        assertNull(remap.variant(2, "_stairs"), "la tierra no tiene escaleras");
        assertEquals("_fence_gate", PaletteRemap.shapeOf("minecraft:oak_fence_gate"));
        assertEquals("_slab", PaletteRemap.shapeOf("minecraft:stone_brick_slab"));
        assertNull(PaletteRemap.shapeOf("minecraft:stone"));
    }

    @Test
    void nearestMaterialWithTheShape() {
        PaletteRemap remap = remap("minecraft:stone", "minecraft:dirt", "minecraft:dirt", "minecraft:cobblestone");
        assertEquals(0, remap.nearestWithShape(1, "_stairs", -1));
        assertEquals(3, remap.nearestWithShape(2, "_stairs", 1));
        assertEquals(3, remap.nearestWithShape(1, "_wall", 1), "solo el empedrado tiene muro");
        assertEquals(-1, remap(("minecraft:dirt")).nearestWithShape(0, "_stairs", 1));
        assertEquals(2, remap.nearestWithShape(2, null, 1));
    }

    @Test
    void indexOfKnowsBlocksAndShapes() {
        PaletteRemap remap = remap("minecraft:stone", "minecraft:stone", "minecraft:stone", "minecraft:cobblestone");
        assertEquals(0, remap.indexOf("minecraft:stone", -1));
        assertEquals(1, remap.indexOf("minecraft:stone", 0));
        assertEquals(2, remap.indexOf("minecraft:stone", 1));
        assertEquals(3, remap.indexOf("minecraft:cobblestone_stairs", 1), "las piezas cuentan como su material");
        assertEquals(-1, remap.indexOf("minecraft:dirt", 0));
    }

    @Test
    void paletteIndexRoundsOrDithers() {
        assertEquals(0, PaletteRemap.index(0.0, 5, 0.0, 0, 0, 0));
        assertEquals(4, PaletteRemap.index(1.0, 5, 0.0, 0, 0, 0));
        assertEquals(2, PaletteRemap.index(0.5, 5, 0.0, 0, 0, 0));
        assertEquals(1, PaletteRemap.index(0.3, 5, 0.0, 0, 0, 0));
        assertEquals(2, PaletteRemap.index(0.4, 5, 0.0, 0, 0, 0));
        int upper = 0;
        for (int x = 0; x < 1_000; x++) {
            int index = PaletteRemap.index(0.375, 5, 1.0, x, 3, 7);
            assertTrue(index == 1 || index == 2);
            upper += index == 2 ? 1 : 0;
        }
        assertTrue(upper > 400 && upper < 600, "con tramado completo el reparto sigue a la fracción: " + upper);
    }
}
