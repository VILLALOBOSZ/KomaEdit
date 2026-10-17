package com.komainu.moxia.shape.smooth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Familia de losas y escaleras de un bloque, deducida por su identificador.
 *
 * <p>Casi todos los bloques siguen la convención de Minecraft ({@code stone} → {@code stone_slab},
 * {@code stone_bricks} → {@code stone_brick_stairs}, {@code oak_planks} → {@code oak_slab}). Esta clase genera,
 * por orden de preferencia, las "raíces" posibles de un bloque y la tabla resuelve los casos sin convención
 * (troncos, pilares, cobre, piedra pulida sin escaleras…). Quien la usa comprueba qué identificadores existen;
 * si ninguno existe, el servidor recurre a la losa o escalera de color más parecido.</p>
 */
public final class SmoothMaterials {
    private static final int MAX_STEMS = 12;
    /** Bloques cuya familia tiene otro nombre. */
    private static final Map<String, String> TABLE = Map.ofEntries(
            Map.entry("deepslate", "cobbled_deepslate"),
            Map.entry("chiseled_deepslate", "deepslate_tile"),
            Map.entry("end_stone", "end_stone_brick"),
            Map.entry("netherrack", "nether_brick"),
            Map.entry("packed_mud", "mud_brick"),
            Map.entry("mud", "mud_brick"),
            Map.entry("quartz_bricks", "quartz"),
            Map.entry("sand", "sandstone"),
            Map.entry("red_sand", "red_sandstone"),
            Map.entry("gilded_blackstone", "blackstone"),
            Map.entry("crying_obsidian", "obsidian"),
            Map.entry("magma_block", "nether_brick")
    );
    private static final String[] PREFIXES = {
            "chiseled_", "cracked_", "infested_", "stripped_", "smooth_", "polished_", "cut_", "gilded_"
    };
    private static final String[] WOOD_SUFFIXES = {"_planks", "_log", "_wood", "_stem", "_hyphae", "_block",
            "_pillar"};

    private SmoothMaterials() {
    }

    public static List<String> slabIds(String blockId) {
        return withSuffix(blockId, "_slab");
    }

    public static List<String> stairsIds(String blockId) {
        return withSuffix(blockId, "_stairs");
    }

    /**
     * Candidatos a la pieza de un bloque con cualquier sufijo de forma ({@code _wall}, {@code _fence}…), por orden
     * de preferencia; quien la usa se queda con el primero que exista.
     */
    public static List<String> shapeIds(String blockId, String suffix) {
        return withSuffix(blockId, suffix);
    }

    /** Raíces por orden de preferencia: primero el propio bloque, después sus parientes más cercanos. */
    static List<String> stems(String path) {
        Set<String> found = new LinkedHashSet<>();
        List<String> frontier = new ArrayList<>(List.of(path));
        while (!frontier.isEmpty() && found.size() < MAX_STEMS) {
            List<String> next = new ArrayList<>();
            for (String stem : frontier) {
                if (stem.isEmpty() || !found.add(stem)) {
                    continue;
                }
                next.addAll(relatives(stem));
            }
            frontier = next;
        }
        return List.copyOf(found);
    }

    private static List<String> relatives(String stem) {
        List<String> relatives = new ArrayList<>();
        String mapped = TABLE.get(stem);
        if (mapped != null) {
            relatives.add(mapped);
        }
        // stone_bricks → stone_brick, deepslate_tiles → deepslate_tile, bricks → brick.
        if (stem.endsWith("bricks") || stem.endsWith("tiles")) {
            relatives.add(stem.substring(0, stem.length() - 1));
        }
        for (String suffix : WOOD_SUFFIXES) {
            if (stem.endsWith(suffix) && stem.length() > suffix.length()) {
                relatives.add(stem.substring(0, stem.length() - suffix.length()));
            }
        }
        // El cobre entero no tiene losas; sí su versión cortada con el mismo grado de oxidación.
        if (stem.endsWith("copper") && !stem.endsWith("cut_copper")) {
            relatives.add(stem.substring(0, stem.length() - "copper".length()) + "cut_copper");
        }
        for (String prefix : PREFIXES) {
            if (stem.startsWith(prefix) && stem.length() > prefix.length()) {
                relatives.add(stem.substring(prefix.length()));
            }
        }
        return relatives;
    }

    private static List<String> withSuffix(String blockId, String suffix) {
        int colon = blockId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : blockId.substring(0, colon);
        String path = colon < 0 ? blockId : blockId.substring(colon + 1);
        List<String> ids = new ArrayList<>();
        for (String stem : stems(path)) {
            ids.add(namespace + ":" + stem + suffix);
        }
        return ids;
    }
}
