package com.komainu.moxia.gradient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPalettesTest {
    @AfterEach
    void clear() {
        ServerPalettes.set(Map.of());
    }

    @Test
    void serverPalettesExpandAndChainInFileOrder() {
        Map<String, List<String>> loaded = ServerPalettes.parse("""
                {"format": 1, "palettes": {
                  "Murallas": "stone_bricks,cracked_stone_bricks,mossy_stone_bricks",
                  "murallas_rev": "-##murallas,gold_block"
                }}""");
        assertEquals(List.of("stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"), loaded.get("murallas"));
        assertEquals(List.of("mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "gold_block"),
                loaded.get("murallas_rev"));
        ServerPalettes.set(loaded);
        assertEquals(List.of("stone_bricks", "gold_block"), PaletteSpec.parse("##Murallas_Rev(3:4)"));
    }

    @Test
    void builtInPalettesWinAndUnknownNamesStillFail() {
        ServerPalettes.set(Map.of("grayscale", List.of("dirt", "stone")));
        assertFalse(PaletteSpec.parse("##Grayscale").contains("dirt"));
        assertThrows(IllegalArgumentException.class, () -> PaletteSpec.parse("##noexiste"));
    }

    @Test
    void emptyOrMissingPalettesSectionLoadsNothing() {
        assertTrue(ServerPalettes.parse("{\"format\": 1}").isEmpty());
        assertTrue(ServerPalettes.parse("{\"format\": 1, \"palettes\": {}}").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ServerPalettes.parse("{\"format\": 9}"));
    }
}
