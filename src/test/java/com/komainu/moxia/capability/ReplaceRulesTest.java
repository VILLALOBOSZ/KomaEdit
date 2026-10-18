package com.komainu.moxia.capability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaceRulesTest {
    private static final Map<String, List<String>> STAIRS = Map.of(
            "facing", List.of("north", "south", "west", "east"),
            "half", List.of("top", "bottom"),
            "shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"),
            "waterlogged", List.of("true", "false")
    );
    private static final Map<String, List<String>> SLAB = Map.of(
            "type", List.of("top", "bottom", "double"),
            "waterlogged", List.of("true", "false")
    );
    private static final Map<String, List<String>> CHEST = Map.of(
            "facing", List.of("north", "south", "west", "east"),
            "type", List.of("single", "left", "right"),
            "waterlogged", List.of("true", "false")
    );

    @Test
    void stairsKeepEveryPropertyWhenReplacedByStairs() {
        Map<String, String> old = Map.of("facing", "west", "half", "top", "shape", "outer_left",
                "waterlogged", "true");
        Map<String, String> placed = Map.of("facing", "north", "half", "bottom", "shape", "straight",
                "waterlogged", "false");
        assertTrue(StateTransfer.sameShape(old, STAIRS));
        assertEquals(old, StateTransfer.transfer(old, STAIRS, placed));
    }

    @Test
    void differentShapesKeepOnlyWhatTheyShare() {
        Map<String, String> stairs = Map.of("facing", "west", "half", "top", "shape", "straight",
                "waterlogged", "true");
        Map<String, String> placedSlab = Map.of("type", "bottom", "waterlogged", "false");
        assertFalse(StateTransfer.sameShape(stairs, SLAB));
        assertEquals(Map.of("type", "bottom", "waterlogged", "true"),
                StateTransfer.transfer(stairs, SLAB, placedSlab));
    }

    @Test
    void valuesTheNewBlockDoesNotAcceptAreIgnored() {
        Map<String, String> slab = Map.of("type", "double", "waterlogged", "false");
        Map<String, String> placedChest = Map.of("facing", "south", "type", "single", "waterlogged", "false");
        assertEquals(placedChest, StateTransfer.transfer(slab, CHEST, placedChest));
    }

    @Test
    void twoPartBlocksAreDetected() {
        assertTrue(StateTransfer.multiBlock(Map.of("half", List.of("upper", "lower"),
                "facing", List.of("north"))));
        assertTrue(StateTransfer.multiBlock(Map.of("part", List.of("head", "foot"))));
        assertFalse(StateTransfer.multiBlock(STAIRS));
        assertFalse(StateTransfer.multiBlock(Map.of()));
    }

    @Test
    void familySwapKeepsTheShapeAndTakesTheMaterial() {
        BlockFamilyIndex index = new BlockFamilyIndex(List.of(
                new BlockFamilyIndex.Family("oak", "minecraft:oak_planks", Map.of(
                        "stairs", "minecraft:oak_stairs", "slab", "minecraft:oak_slab",
                        "fence", "minecraft:oak_fence")),
                new BlockFamilyIndex.Family("spruce", "minecraft:spruce_planks", Map.of(
                        "stairs", "minecraft:spruce_stairs", "slab", "minecraft:spruce_slab")),
                new BlockFamilyIndex.Family("andesite", "minecraft:andesite", Map.of(
                        "polished", "minecraft:polished_andesite", "stairs", "minecraft:andesite_stairs")),
                new BlockFamilyIndex.Family("polished_andesite", "minecraft:polished_andesite", Map.of(
                        "stairs", "minecraft:polished_andesite_stairs")),
                new BlockFamilyIndex.Family("granite", "minecraft:granite", Map.of(
                        "polished", "minecraft:polished_granite", "stairs", "minecraft:granite_stairs")),
                new BlockFamilyIndex.Family("polished_granite", "minecraft:polished_granite", Map.of(
                        "stairs", "minecraft:polished_granite_stairs"))
        ));
        assertEquals("minecraft:spruce_stairs",
                index.swap("minecraft:oak_stairs", "minecraft:spruce_planks").orElseThrow());
        assertEquals("minecraft:spruce_slab",
                index.swap("minecraft:oak_slab", "minecraft:spruce_stairs").orElseThrow());
        assertEquals("minecraft:spruce_planks",
                index.swap("minecraft:oak_planks", "minecraft:spruce_slab").orElseThrow());
        // La familia de la mano no tiene valla: no hay equivalente y se reemplaza tal cual.
        assertTrue(index.swap("minecraft:oak_fence", "minecraft:spruce_planks").isEmpty());
        // Un bloque fuera de las familias tampoco.
        assertTrue(index.swap("minecraft:oak_stairs", "minecraft:dirt").isEmpty());
        // Andesita pulida apuntada es la variante «pulida» de la andesita: con granito sale granito pulido.
        assertEquals("polished", index.variantOf("minecraft:polished_andesite"));
        assertEquals("minecraft:polished_granite",
                index.swap("minecraft:polished_andesite", "minecraft:granite").orElseThrow());
        // Con granito pulido en la mano manda su propia familia: sus escaleras.
        assertEquals("minecraft:polished_granite_stairs",
                index.swap("minecraft:andesite_stairs", "minecraft:polished_granite").orElseThrow());
    }
}
