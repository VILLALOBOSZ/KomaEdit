package com.komainu.moxia.color;

import com.komainu.moxia.shape.smooth.SmoothMaterials;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockColorCatalogTest {
    private static final BlockColorCatalog CATALOG = TestColors.VANILLA;

    @Test
    void tableCoversEveryVanillaBlockAndKeepsTheSmoothColors() {
        Map<String, BlockColorCatalog.Measured> measured = BlockColorCatalog.measured();
        assertTrue(measured.size() > 1_000, "faltan bloques: " + measured.size());
        Map<String, Integer> smooth = SmoothMaterials.textureColors();
        assertEquals(smooth.keySet(), measured.keySet(), "smoothblocks y color leen la misma tabla");
        smooth.forEach((id, rgb) -> assertEquals(rgb, measured.get(id).rgb(), id));
    }

    @Test
    void uniformFlagMarksCubesWithOneTexture() {
        Map<String, BlockColorCatalog.Measured> measured = BlockColorCatalog.measured();
        for (String id : List.of("stone", "white_wool", "red_concrete", "oak_planks", "glass", "andesite")) {
            assertTrue(measured.get("minecraft:" + id).uniform(), id);
        }
        for (String id : List.of("oak_log", "sandstone", "grass_block", "crafting_table", "stone_stairs", "deepslate",
                "quartz_pillar", "torch")) {
            assertFalse(measured.get("minecraft:" + id).uniform(), id);
        }
    }

    @Test
    void readsFourOrFiveColumns() throws IOException {
        String text = "# cabecera\nminecraft:a 1 2 3\nminecraft:b 255 0 16 u\nminecraft:c 9 9 9 p\n";
        Map<String, BlockColorCatalog.Measured> table = BlockColorCatalog.read(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(new BlockColorCatalog.Measured(0x010203, false, false), table.get("minecraft:a"));
        assertEquals(new BlockColorCatalog.Measured(0xFF0010, true, false), table.get("minecraft:b"));
        assertEquals(new BlockColorCatalog.Measured(0x090909, false, true), table.get("minecraft:c"));
    }

    @Test
    void similarIsSortedByDistanceAndSkipsTheReference() {
        BlockColor stone = TestColors.block("stone");
        List<BlockColorCatalog.Match> matches = CATALOG.similar(stone.lab(), 12, ColorFilter.ANY, Set.of(stone.id()));
        assertEquals(12, matches.size());
        for (int index = 1; index < matches.size(); index++) {
            assertTrue(matches.get(index - 1).distance() <= matches.get(index).distance());
        }
        Set<String> ids = new HashSet<>();
        matches.forEach(match -> ids.add(match.block().id()));
        assertFalse(ids.contains("minecraft:stone"));
        // La losa y la escalera de piedra tienen el mismo color: no salen como «parecidas».
        assertFalse(ids.contains("minecraft:stone_slab"));
        assertFalse(ids.contains("minecraft:stone_stairs"));
        assertTrue(matches.getFirst().distance() < 0.03, "hay bloques casi iguales a la piedra");
        assertTrue(ids.contains("minecraft:andesite") || ids.contains("minecraft:cobblestone")
                || ids.contains("minecraft:stone_bricks"), ids.toString());
    }

    @Test
    void similarCollapsesBlocksWithTheSameColorIntoTheFullBlock() {
        BlockColor planks = TestColors.block("oak_planks");
        List<BlockColorCatalog.Match> matches = CATALOG.similar(planks.lab(), 5, ColorFilter.ANY, Set.of());
        assertEquals("minecraft:oak_planks", matches.getFirst().block().id());
        assertEquals(0.0, matches.getFirst().distance(), 1.0E-12);
        assertTrue(matches.stream().noneMatch(match -> match.block().id().startsWith("minecraft:oak_")
                && !match.block().id().equals("minecraft:oak_planks") && match.distance() == 0.0));
    }

    @Test
    void filtersKeepOnlyBlocksWithTheRequiredFlags() {
        double[] white = OkLab.of(0xFFFFFF);
        ColorFilter opaqueUniform = ColorFilter.of(true, true, true, true);
        List<BlockColorCatalog.Match> matches = CATALOG.similar(white, 20, opaqueUniform, Set.of());
        assertEquals(20, matches.size());
        for (BlockColorCatalog.Match match : matches) {
            BlockColor block = match.block();
            assertTrue(block.has(BlockColor.CUBE | BlockColor.SOLID | BlockColor.OPAQUE | BlockColor.UNIFORM),
                    block.id());
        }
        assertTrue(matches.stream().noneMatch(match -> match.block().id().contains("glass")));
        List<BlockColorCatalog.Match> any = CATALOG.similar(OkLab.of(0xE8F0F8), 40, ColorFilter.ANY, Set.of());
        assertTrue(any.stream().anyMatch(match -> !match.block().has(BlockColor.CUBE)),
                "sin filtro también salen piezas y plantas");
    }

    @Test
    void oresMachinesAndPatternsAreNotPlain() {
        Map<String, BlockColorCatalog.Measured> measured = BlockColorCatalog.measured();
        for (String id : List.of("gold_ore", "deepslate_lapis_ore", "orange_glazed_terracotta", "furnace", "dropper",
                "observer", "crafting_table", "bookshelf", "chiseled_bookshelf", "tnt", "glowstone", "note_block",
                "exposed_copper_bulb", "bee_nest")) {
            assertTrue(measured.get("minecraft:" + id).patterned(), id);
        }
        for (String id : List.of("stone", "diorite", "cobblestone", "oak_log", "oak_planks", "sandstone", "white_wool",
                "red_terracotta", "deepslate", "calcite", "bricks", "mud_bricks", "smooth_basalt")) {
            assertFalse(measured.get("minecraft:" + id).patterned(), id);
        }
        // Por defecto los degradados y los parecidos solo usan materiales lisos; -x los deja entrar.
        double[] gold = TestColors.block("gold_ore").lab();
        assertTrue(CATALOG.similar(gold, 30, ColorFilter.PLAIN, Set.of()).stream()
                .allMatch(match -> match.block().has(BlockColor.PLAIN)));
        assertTrue(CATALOG.similar(gold, 30, ColorFilter.ANY, Set.of()).stream()
                .anyMatch(match -> !match.block().has(BlockColor.PLAIN)));
        assertEquals("-c -x", ColorFilter.of(true, false, false, false, true).flags());
        assertEquals("-c -o", ColorFilter.of(true, false, true, false).flags());
        assertEquals("lisos, cubo entero", ColorFilter.of(true, false, false, false).describe());
    }

    @Test
    void technicalBlocksAndTrapsAreNeverProposed() {
        assertFalse(BlockColorCatalog.pickable("minecraft:infested_stone"));
        assertFalse(BlockColorCatalog.pickable("minecraft:barrier"));
        assertFalse(BlockColorCatalog.pickable("minecraft:petrified_oak_slab"));
        assertTrue(BlockColorCatalog.pickable("minecraft:stone"));
        for (BlockColor block : CATALOG.candidates(ColorFilter.ANY)) {
            assertTrue(BlockColorCatalog.pickable(block.id()), block.id());
        }
    }

    @Test
    void modBlocksUseTheirMapColor() {
        BlockColor modded = BlockColor.of("mimod:marmol", 0xF0F0E8, BlockColor.ITEM | BlockColor.CUBE, false);
        BlockColorCatalog catalog = BlockColorCatalog.of(List.of(modded, TestColors.block("stone")));
        assertEquals(modded, catalog.get("mimod:marmol"));
        assertFalse(catalog.get("mimod:marmol").measured());
        assertEquals("mimod:marmol", catalog.similar(OkLab.of(0xFFFFFF), 1, ColorFilter.ANY, Set.of()).getFirst()
                .block().id());
        assertNull(catalog.get("mimod:nada"));
        assertThrows(IllegalArgumentException.class, () -> catalog.require("mimod:nada"));
    }

    @Test
    void namesWithoutNamespaceAndPiecesResolveToTheirBlock() {
        assertNotNull(CATALOG.get("stone"));
        assertEquals("minecraft:stone_bricks", CATALOG.base("stone_brick_stairs"));
        assertEquals("minecraft:oak_planks", CATALOG.base("minecraft:oak_slab"));
        assertEquals("minecraft:stone", CATALOG.base("stone"));
    }

    @Test
    void lightnessOrderIsDarkToLight() {
        List<BlockColor> sorted = BlockColorCatalog.byLightness(List.of(TestColors.block("calcite"),
                TestColors.block("blackstone"), TestColors.block("stone")));
        assertEquals(List.of("minecraft:blackstone", "minecraft:stone", "minecraft:calcite"),
                sorted.stream().map(BlockColor::id).toList());
    }
}
