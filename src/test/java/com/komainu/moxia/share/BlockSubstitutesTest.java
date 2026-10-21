package com.komainu.moxia.share;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockSubstitutesTest {
    private final BlockCatalog catalog = ShareFixtures.catalog();

    @Test
    void theSameNameInMinecraftWins() {
        assertEquals("minecraft:andesite", BlockSubstitutes.best("create:andesite", catalog));
    }

    @Test
    void theShapeMattersMoreThanTheMaterial() {
        assertEquals("minecraft:oak_stairs", BlockSubstitutes.best("biomesoplenty:fir_stairs", catalog));
        assertEquals("minecraft:spruce_stairs", BlockSubstitutes.best("mod:spruce_stairs", catalog));
        assertEquals("minecraft:andesite_slab", BlockSubstitutes.best("create:andesite_slab", catalog));
    }

    @Test
    void sharedWordsPickTheMaterial() {
        assertEquals("minecraft:andesite", BlockSubstitutes.best("create:andesite_casing", catalog));
        assertEquals("minecraft:moss_block", BlockSubstitutes.best("quark:thick_moss_block", catalog));
    }

    @Test
    void nothingAlikeIsAir() {
        assertEquals(BlockSubstitutes.AIR, BlockSubstitutes.best("mod:qwerty_zxcv", catalog));
    }

    @Test
    void adminBlocksAreNeverProposed() {
        assertEquals(BlockSubstitutes.AIR, BlockSubstitutes.best("mod:command_block", catalog));
        assertTrue(BlockSubstitutes.restricted("minecraft:command_block"));
        assertTrue(BlockSubstitutes.restricted("minecraft:structure_block"));
    }

    @Test
    void checkKeepsThePropertiesThatStillExist() {
        ShareCheck check = ShareFixtures.check();
        check.begin();
        assertEquals("minecraft:oak_stairs[facing=east,half=top]",
                check.block("biomesoplenty:fir_stairs[facing=east,half=top,mossy=true]"));
        assertEquals("minecraft:stone", check.block("stone[nonsense=1]"));
        assertEquals("minecraft:air", check.block("minecraft:command_block[facing=up]"));
        List<String> warnings = check.warnings();
        assertTrue(warnings.getFirst().contains("biomesoplenty:fir_stairs → oak_stairs"), warnings.toString());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("nonsense=1")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("administración")), warnings.toString());
    }
}
