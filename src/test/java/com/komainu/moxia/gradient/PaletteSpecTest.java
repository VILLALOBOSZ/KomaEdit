package com.komainu.moxia.gradient;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaletteSpecTest {
    private static final List<String> GRAYSCALE = List.of(GradientPalette.GRAYSCALE.patterns().split(";"));

    @Test
    void resolvesPresetsByIdOrDisplayNameIgnoringCase() {
        assertEquals(GRAYSCALE, PaletteSpec.parse("##Grayscale"));
        assertEquals(GRAYSCALE, PaletteSpec.parse("##GRAYSCALE="));
        assertEquals(List.of(GradientPalette.GLOW_BLUE.patterns().split(";")), PaletteSpec.parse("##GlowBlue"));
    }

    @Test
    void supportsInvertSliceRepeatAndGroups() {
        assertEquals(GRAYSCALE.reversed(), PaletteSpec.parse("-##Grayscale"));
        assertEquals(GRAYSCALE.subList(0, 8), PaletteSpec.parse("##Grayscale(1:8)"));
        assertEquals(GRAYSCALE.subList(2, 11).reversed(), PaletteSpec.parse("-##Grayscale(3:11)"));
        assertEquals(List.of("gold_block", "gold_block", "gold_block", "diamond_block"),
                PaletteSpec.parse("gold_block*3,diamond_block"));
        List<String> grouped = PaletteSpec.parse("-[##Grayscale,gold_block]");
        assertEquals("gold_block", grouped.getFirst());
        assertEquals(GRAYSCALE.getLast(), grouped.get(1));
        List<String> ungrouped = PaletteSpec.parse("-##Grayscale,gold_block");
        assertEquals("gold_block", ungrouped.getLast());
    }

    @Test
    void keepsLegacySemicolonListsAndBlockStates() {
        assertEquals(List.of("stone", "andesite", "oak_stairs[facing=east,half=top]"),
                PaletteSpec.parse("stone;andesite , oak_stairs[facing=east,half=top]"));
        assertEquals(List.of("50%stone"), PaletteSpec.parse("50%stone"));
    }

    @Test
    void resolvesUserPalettesRecursivelyWithALimit() {
        Map<String, String> saved = Map.of("muro", "stone_bricks*2,##Grayscale(1:2)", "bucle", "#bucle");
        assertEquals(List.of("stone_bricks", "stone_bricks", GRAYSCALE.get(0), GRAYSCALE.get(1)),
                PaletteSpec.parse("#Muro", saved::get));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("#bucle", saved::get));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("#muro"));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("#nada", saved::get));
    }

    @Test
    void rejectsMalformedPalettes() {
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse(""));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("##NoExiste"));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("##Grayscale(0:3)"));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("##Grayscale(5:99)"));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("[stone,dirt"));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("stone,,dirt"));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("stone*999"));
    }
}
