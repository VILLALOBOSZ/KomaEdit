package com.komainu.moxia.terrain;

import java.util.Locale;

/**
 * Qué es cada bloque para las herramientas de terreno. El suelo es lo que forma el mapa de alturas; la vegetación
 * (hojas, troncos, plantas, nieve en capa, antorchas…) se apoya en él y se quita si el suelo de debajo desaparece.
 */
public enum TerrainCell {
    AIR,
    GROUND,
    WATER,
    LAVA,
    /** Hojas, troncos, plantas y todo lo suelto que no es suelo. */
    PLANT;

    private static final String[] VEGETATION = {"_leaves", "_log", "_wood", "_stem", "_hyphae", "mushroom_block",
            "vine", "bamboo", "cactus", "sugar_cane", "pumpkin", "melon", "azalea", "mangrove_roots", "bee_nest",
            "dripleaf", "chorus", "moss_carpet", "_wart_block", "shroomlight"};

    public boolean liquid() {
        return this == WATER || this == LAVA;
    }

    /**
     * Clasifica un bloque por su id y lo que dice Minecraft de él.
     *
     * @param solid bloque macizo (cubo opaco); lo que no lo es y no es aire ni líquido cuenta como suelto
     */
    public static TerrainCell classify(String blockId, boolean air, boolean liquid, boolean solid) {
        if (air) {
            return AIR;
        }
        String id = blockId == null ? "" : blockId.toLowerCase(Locale.ROOT);
        if (liquid) {
            return id.contains("lava") ? LAVA : WATER;
        }
        for (String vegetation : VEGETATION) {
            if (id.contains(vegetation)) {
                return PLANT;
            }
        }
        // Suelos que no llegan a cubo entero pero son terreno.
        if (id.endsWith(":mud") || id.endsWith(":dirt_path") || id.endsWith(":farmland") || id.endsWith(":soul_sand")) {
            return GROUND;
        }
        return solid ? GROUND : PLANT;
    }
}
