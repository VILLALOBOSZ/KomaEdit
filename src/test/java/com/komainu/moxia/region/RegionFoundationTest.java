package com.komainu.moxia.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionFoundationTest {
    @Test
    void snapshotKnowsSelectionNeighboursAndExposure() {
        RegionSnapshot.Builder builder = RegionSnapshot.builder(10, 60, -5, 3, 3, 3)
                .state(new RegionSnapshot.StateInfo(0, null, "minecraft:air", true, false, false))
                .state(new RegionSnapshot.StateInfo(1, null, "minecraft:stone", false, false, true));
        for (int x = 10; x <= 12; x++) {
            for (int y = 60; y <= 62; y++) {
                for (int z = -5; z <= -3; z++) {
                    boolean core = x == 11 && z == -4;
                    builder.set(x, y, z, core || y == 60 ? 1 : 0, x == 11);
                }
            }
        }
        RegionSnapshot snapshot = builder.build();
        assertTrue(snapshot.selected(11, 61, -4));
        assertFalse(snapshot.selected(10, 61, -4), "el margen no es selección");
        assertEquals("minecraft:stone", snapshot.blockId(11, 61, -4));
        assertTrue(snapshot.isAir(9, 61, -4), "fuera de la foto todo es aire");
        assertEquals(4, snapshot.exposedFaces(11, 61, -4), "columna: cuatro caras laterales al aire");
        assertEquals(5, snapshot.exposedFaces(11, 62, -4), "la cima suma la cara de arriba");
        assertEquals(27, snapshot.volume());
    }

    @Test
    void tweakRulesMapBlockIds() {
        assertEquals("minecraft:stone_bricks", TweakRules.deinfested("minecraft:infested_stone_bricks"));
        assertEquals("minecraft:deepslate", TweakRules.deinfested("infested_deepslate"));
        assertNull(TweakRules.deinfested("minecraft:stone"));

        assertEquals("minecraft:waxed_exposed_cut_copper_stairs",
                TweakRules.waxed("minecraft:exposed_cut_copper_stairs", true));
        assertEquals("minecraft:copper_bulb", TweakRules.waxed("minecraft:waxed_copper_bulb", false));
        assertNull(TweakRules.waxed("minecraft:waxed_copper_block", true), "ya encerado");
        assertNull(TweakRules.waxed("minecraft:copper_ore", true), "el mineral no se encera");
        assertNull(TweakRules.waxed("minecraft:stone", true));

        assertTrue(TweakRules.isLeaves("minecraft:cherry_leaves"));
        assertFalse(TweakRules.isLeaves("minecraft:oak_log"));

        assertEquals(TweakRules.Tweak.WAX, TweakRules.Tweak.byId("encerar"));
        assertTrue(TweakRules.enabled(null));
        assertFalse(TweakRules.enabled("off"));
        assertThrows(IllegalArgumentException.class, () -> TweakRules.enabled("tal vez"));
        assertThrows(IllegalArgumentException.class, () -> TweakRules.Tweak.byId("pintar"));
    }
}
