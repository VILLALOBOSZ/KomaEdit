package com.komainu.moxia.gradient;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Paletas guardadas, de serie, códec, bloques y cambio de paleta. */
class PaletteLibraryTest {
    @Test
    void everyDefaultPaletteIsMadeOfPlainBlocks() {
        for (GradientPalette palette : GradientPalette.values()) {
            List<String> entries = PaletteSpec.parse("##" + palette.displayName());
            assertTrue(entries.size() >= 5, palette + " tiene muy pocos bloques");
            for (String entry : entries) {
                BlockSpec.parse(entry);
            }
        }
    }

    @Test
    void newDefaultPalettesResolveByEzEditsName() {
        for (String name : new String[]{"Beige", "Brown", "DustyBrown", "EnchantedDark", "EnchantedWarm",
                "EnchantedBright", "LegacyWool", "Light", "Moss", "Ice", "Rocks", "Leaf", "LeavesAll", "Sand",
                "ShiningGold"}) {
            assertFalse(PaletteSpec.parse("##" + name).isEmpty(), name);
        }
        assertEquals(16, PaletteSpec.parse("##LegacyWool").size());
        assertTrue(PaletteSpec.parse("##LeavesAll").stream().allMatch(entry -> entry.endsWith("[persistent=true]")));
    }

    @Test
    void userPalettesExpandThroughTheHook() {
        Map<String, String> saved = Map.of("hielo", "blue_ice;packed_ice;ice", "mezcla", "#hielo,stone");
        List<String> entries = PaletteSpec.parse("-#mezcla", saved::get);

        assertEquals(List.of("stone", "ice", "packed_ice", "blue_ice"), entries);
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("#nada", saved::get));
    }

    @Test
    void codecRoundTripsAndDropsTheVanillaNamespace() {
        List<String> entries = List.of("minecraft:stone", "oak_stairs[facing=east,half=top]", "create:andesite_casing");
        String code = PaletteCodec.encode(entries);

        assertTrue(code.startsWith(PaletteCodec.PREFIX));
        assertFalse(code.contains("minecraft"));
        assertEquals(List.of("stone", "oak_stairs[facing=east,half=top]", "create:andesite_casing"),
                PaletteCodec.decode(code));
    }

    @Test
    void codecRejectsDamagedCodes() {
        assertThrows(IllegalArgumentException.class, () -> PaletteCodec.decode("stone"));
        assertThrows(IllegalArgumentException.class, () -> PaletteCodec.decode("kp1:!!!"));
        assertThrows(IllegalArgumentException.class, () -> PaletteCodec.decode("kp1:AAAA"));
        assertThrows(IllegalArgumentException.class, () -> PaletteCodec.encode(List.of()));
    }

    @Test
    void blockSpecsMatchStrictlyOrByType() {
        BlockSpec stairs = BlockSpec.parse("Oak_Stairs[facing=east]");
        assertEquals("minecraft:oak_stairs", stairs.id());
        assertEquals("minecraft:oak_stairs[facing=east]", stairs.toString());
        assertEquals("oak_stairs", stairs.shortName());

        Map<String, String> west = Map.of("facing", "west", "half", "bottom");
        Map<String, String> east = Map.of("facing", "east", "half", "top");
        assertFalse(stairs.matches("minecraft:oak_stairs", west, true));
        assertTrue(stairs.matches("minecraft:oak_stairs", west, false));
        assertTrue(stairs.matches("minecraft:oak_stairs", east, true));
        assertFalse(stairs.matches("minecraft:spruce_stairs", east, false));
        assertTrue(BlockSpec.parse("cave_air").isAir());

        assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse("50%stone"));
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse("oak_stairs[facing]"));
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse("oak_stairs[facing=east"));
    }

    @Test
    void swapPairsByPositionAndCanStretch() {
        List<String> source = List.of("stone", "air", "dirt", "sand");
        List<String> target = List.of("andesite", "glass");

        PaletteSwap plain = PaletteSwap.of(source, target, false, false, false, false);
        assertEquals(2, plain.rules().size());
        assertEquals("minecraft:andesite", plain.ruleFor("minecraft:stone", Map.of()).target().id());
        assertEquals("minecraft:glass", plain.ruleFor("minecraft:air", Map.of()).target().id());
        assertNull(plain.ruleFor("minecraft:dirt", Map.of()));

        PaletteSwap stretched = PaletteSwap.of(source, target, true, true, false, false);
        assertNull(stretched.ruleFor("minecraft:air", Map.of()));
        assertEquals("minecraft:andesite", stretched.ruleFor("minecraft:stone", Map.of()).target().id());
        assertEquals("minecraft:glass", stretched.ruleFor("minecraft:dirt", Map.of()).target().id());
        assertEquals("minecraft:glass", stretched.ruleFor("minecraft:sand", Map.of()).target().id());
    }

    @Test
    void swapHonoursFuzzyMatchingAndCopiesStates() {
        List<String> source = List.of("oak_stairs[facing=east]");
        List<String> target = List.of("stone_brick_stairs[half=top]");
        Map<String, String> west = Map.of("facing", "west", "half", "bottom");

        assertNull(PaletteSwap.of(source, target, false, false, false, false).ruleFor("minecraft:oak_stairs", west));
        PaletteSwap fuzzy = PaletteSwap.of(source, target, false, false, true, true);
        PaletteSwap.Rule rule = fuzzy.ruleFor("minecraft:oak_stairs", west);
        assertEquals(Map.of("facing", "west", "half", "top"), fuzzy.targetProperties(rule, west));

        PaletteSwap noCopy = PaletteSwap.of(source, target, false, false, true, false);
        assertEquals(Map.of("half", "top"), noCopy.targetProperties(noCopy.ruleFor("minecraft:oak_stairs", west),
                west));
    }

    @Test
    void swapNeedsAtLeastOnePair() {
        assertThrows(IllegalArgumentException.class,
                () -> PaletteSwap.of(List.of("air"), List.of("stone"), true, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> PaletteSwap.of(List.of(), List.of("stone"), false, false, false, false));
    }
}
