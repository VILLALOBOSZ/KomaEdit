package com.komainu.moxia.brush.paint;

import java.util.Set;

/**
 * Qué cuenta como terreno natural para el pincel de pintura cuando no lleva máscara: tierras, piedras, arenas,
 * terracotas, nieve, hielo, menas y lo del Nether y el End. Así pintar una ladera junto a una casa no repinta la casa.
 * Con {@code -b}, {@code -m} o {@code -x} decide quien pinta. Los bloques de la propia paleta siempre se pueden
 * repintar.
 */
public final class PaintTerrain {
    private static final Set<String> BLOCKS = Set.of(
            "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "moss_block", "clay",
            "gravel", "suspicious_gravel", "sand", "suspicious_sand", "red_sand", "sandstone", "red_sandstone",
            "smooth_sandstone", "smooth_red_sandstone", "stone", "cobblestone", "mossy_cobblestone", "granite",
            "diorite", "andesite", "deepslate", "cobbled_deepslate", "tuff", "calcite", "dripstone_block",
            "terracotta", "snow_block", "ice", "packed_ice", "blue_ice", "obsidian", "netherrack", "soul_soil",
            "basalt", "smooth_basalt", "blackstone", "end_stone", "magma_block", "crimson_nylium", "warped_nylium",
            "sculk", "bedrock", "amethyst_block", "budding_amethyst", "raw_iron_block", "raw_copper_block",
            "raw_gold_block", "pale_moss_block", "mud", "packed_mud");

    private PaintTerrain() {
    }

    /** Si el id ({@code minecraft:stone} o {@code stone}) es terreno natural. */
    public static boolean natural(String blockId) {
        if (blockId == null) {
            return false;
        }
        String id = blockId.startsWith("minecraft:") ? blockId.substring("minecraft:".length()) : blockId;
        if (BLOCKS.contains(id)) {
            return true;
        }
        if (id.endsWith("_terracotta")) {
            return !id.endsWith("_glazed_terracotta");
        }
        return id.endsWith("_ore");
    }
}
