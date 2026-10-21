package com.komainu.moxia.hotbar;

import com.komainu.moxia.capability.BlockFamilyIndex;
import com.komainu.moxia.gradient.PaletteSpec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crear barras desde una paleta, un degradado, la selección o la familia de un material. */
class HotbarSourcesTest {
    /** Como el registro de Minecraft: casi todo bloque es su objeto; la antorcha de pared da antorcha; el agua, nada. */
    static final Function<String, HotbarSources.ItemRef> ITEMS = id -> switch (id) {
        case "minecraft:water", "minecraft:fire" -> null;
        case "minecraft:wall_torch" -> new HotbarSources.ItemRef("minecraft:torch", false);
        case "minecraft:oak_wall_sign" -> new HotbarSources.ItemRef("minecraft:oak_sign", false);
        default -> new HotbarSources.ItemRef(id, true);
    };

    private static List<String> ids(HotbarSources.Built built) {
        return built.slots().stream().map(item -> item.isEmpty() ? "-" : item.shortId()).toList();
    }

    @Test
    void aShortPaletteFillsFromTheLeft() {
        HotbarSources.Built built = HotbarSources.fromBlocks(
                PaletteSpec.parse("stone,andesite;cobblestone"), ITEMS, true);
        assertEquals(List.of("stone", "andesite", "cobblestone", "-", "-", "-", "-", "-", "-"), ids(built));
        assertTrue(built.skipped().isEmpty());
        assertEquals(1, built.slots().getFirst().count());
    }

    @Test
    void aLongPaletteIsSpreadFromFirstToLast() {
        HotbarSources.Built built = HotbarSources.fromBlocks(PaletteSpec.parse("##Magma"), ITEMS, true);
        List<String> magma = PaletteSpec.parse("##Magma").stream().distinct().toList();
        assertTrue(magma.size() > 9, "##Magma tiene más de nueve bloques");
        assertEquals(9, built.slots().stream().filter(item -> !item.isEmpty()).count());
        assertEquals(HotbarItem.normalize(magma.getFirst()), built.slots().getFirst().id(), "con el primero");
        assertEquals(HotbarItem.normalize(magma.getLast()), built.slots().getLast().id(), "y el último");
    }

    @Test
    void theMostUsedTakeTheFirstNine() {
        Map<String, Long> counts = new LinkedHashMap<>();
        String[] blocks = {"stone", "dirt", "oak_planks", "glass", "sand", "gravel", "andesite", "diorite", "granite",
                "tuff", "calcite"};
        for (int index = 0; index < blocks.length; index++) {
            counts.put("minecraft:" + blocks[index], 1_000L - index * 10L);
        }
        counts.put("minecraft:calcite", 5_000L);
        counts.put("minecraft:water", 9_999L);
        List<String> ordered = HotbarSources.mostUsed(counts);
        assertEquals("minecraft:water", ordered.getFirst());
        HotbarSources.Built built = HotbarSources.fromBlocks(ordered, ITEMS, false);
        assertEquals(List.of("calcite", "stone", "dirt", "oak_planks", "glass", "sand", "gravel", "andesite",
                "diorite"), ids(built), "sin el agua (no tiene objeto) y de más a menos usado");
        assertEquals(List.of("water"), built.skipped());
    }

    @Test
    void blockStatesBecomeTheBlockStateComponent() {
        HotbarSources.Built built = HotbarSources.fromBlocks(
                List.of("oak_log[axis=x]", "oak_log", "wall_torch[facing=east]", "torch", "snow[layers=3]"),
                ITEMS, true);
        assertEquals("{\"minecraft:block_state\":{\"axis\":\"x\"}}", built.slots().get(0).components());
        assertEquals("", built.slots().get(1).components(), "el tronco sin estado es otro objeto");
        assertEquals(HotbarItem.of("torch", 1), built.slots().get(2),
                "la antorcha de pared da una antorcha sin estado (y no se repite)");
        assertEquals("{\"minecraft:block_state\":{\"layers\":\"3\"}}", built.slots().get(3).components());
        assertTrue(built.slots().get(4).isEmpty());
    }

    @Test
    void patternsAndBlocksWithoutItemAreSkipped() {
        HotbarSources.Built built = HotbarSources.fromBlocks(List.of("50%stone", "water", "air", "fire", "dirt"),
                ITEMS, true);
        assertEquals(List.of("dirt", "-", "-", "-", "-", "-", "-", "-", "-"), ids(built));
        assertEquals(List.of("50%stone", "water", "fire"), built.skipped(), "el aire no cuenta como fallo");
        assertTrue(HotbarSources.fromBlocks(List.of("water"), ITEMS, true).isEmpty());
    }

    @Test
    void aRoofTakesTheMaterialWithItsStairsAndSlabs() {
        BlockFamilyIndex.Family oak = new BlockFamilyIndex.Family("minecraft:oak_planks", "minecraft:oak_planks",
                Map.of("door", "minecraft:oak_door", "slab", "minecraft:oak_slab", "stairs", "minecraft:oak_stairs",
                        "fence", "minecraft:oak_fence", "sign", "minecraft:oak_sign",
                        "wall_sign", "minecraft:oak_wall_sign", "trapdoor", "minecraft:oak_trapdoor",
                        "fence_gate", "minecraft:oak_fence_gate", "button", "minecraft:oak_button",
                        "pressure_plate", "minecraft:oak_pressure_plate"));
        List<String> blocks = HotbarSources.family("minecraft:oak_planks", oak);
        assertEquals(List.of("minecraft:oak_planks", "minecraft:oak_stairs", "minecraft:oak_slab",
                "minecraft:oak_fence", "minecraft:oak_fence_gate", "minecraft:oak_trapdoor", "minecraft:oak_door",
                "minecraft:oak_button", "minecraft:oak_pressure_plate", "minecraft:oak_sign",
                "minecraft:oak_wall_sign"), blocks);
        HotbarSources.Built built = HotbarSources.fromBlocks(blocks, ITEMS, false);
        assertEquals(List.of("oak_planks", "oak_stairs", "oak_slab", "oak_fence", "oak_fence_gate", "oak_trapdoor",
                "oak_door", "oak_button", "oak_pressure_plate"), ids(built));

        assertEquals(List.of("minecraft:oak_stairs", "minecraft:oak_planks", "minecraft:oak_slab"),
                HotbarSources.family("minecraft:oak_stairs", new BlockFamilyIndex.Family("minecraft:oak_planks",
                        "minecraft:oak_planks", Map.of("stairs", "minecraft:oak_stairs",
                        "slab", "minecraft:oak_slab"))), "lo que se pidió va primero");
        assertEquals(List.of("minecraft:mud"), HotbarSources.family("minecraft:mud", null));
    }
}
