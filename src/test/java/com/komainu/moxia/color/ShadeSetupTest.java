package com.komainu.moxia.color;

import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.texture.TextureContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Luz, familias de piezas y paleta de la selección de {@code /komaedit shade}. */
class ShadeSetupTest {
    private static final ShadeLight.Scene SCENE = new ShadeLight.Scene(0.1, new double[]{10, 20, 0},
            new double[]{0, -1, 0}, new double[]{0, 0, 0});

    @Test
    void sunFollowsTheSkyAngleWithinItsLimits() {
        // Mediodía: el sol estaría a plomo; se baja a 65° (por el oeste, como justo después de mediodía).
        ShadeLight noon = ShadeLight.sun(0.0);
        assertEquals(Math.sin(Math.toRadians(65)), noon.towards()[1], 1.0E-9);
        assertTrue(noon.towards()[0] < 0, "por el oeste");
        // Por la mañana (0,8) el sol está al este y bajo; al atardecer (0,2), al oeste.
        ShadeLight morning = ShadeLight.sun(0.8);
        assertTrue(morning.towards()[0] > 0.5, "al este");
        assertTrue(morning.label().startsWith("sol"));
        ShadeLight evening = ShadeLight.sun(0.2);
        assertTrue(evening.towards()[0] < -0.5, "al oeste");
        // Al amanecer, rasante: se sube a 20°.
        assertEquals(Math.sin(Math.toRadians(20)), ShadeLight.sun(0.75).towards()[1], 1.0E-9);
        // De noche alumbra la luna, al otro lado.
        ShadeLight night = ShadeLight.sun(0.45);
        assertTrue(night.label().startsWith("luna"));
        assertTrue(night.towards()[1] > 0);
        for (double angle = 0.0; angle < 1.0; angle += 0.05) {
            double[] towards = ShadeLight.sun(angle).towards();
            assertEquals(1.0, Math.sqrt(towards[0] * towards[0] + towards[1] * towards[1] + towards[2] * towards[2]),
                    1.0E-9);
            assertTrue(towards[1] >= Math.sin(Math.toRadians(20)) - 1.0E-9);
        }
    }

    @Test
    void lightCanComeFromThePlayerTheViewACompassPointOrAVector() {
        ShadeLight player = ShadeLight.resolve("jugador", SCENE);
        assertArrayEquals(new double[]{10 / Math.sqrt(500), 20 / Math.sqrt(500), 0}, player.towards(), 1.0E-9);
        // Mirando hacia abajo, la luz va hacia abajo: viene de arriba.
        assertArrayEquals(new double[]{0, 1, 0}, ShadeLight.resolve("mirada", SCENE).towards(), 1.0E-9);
        ShadeLight spot = ShadeLight.resolve("foco", SCENE);
        assertNull(spot.towards());
        assertArrayEquals(new double[]{10, 20, 0}, spot.point(), 1.0E-9);
        double[] northEast = ShadeLight.resolve("ne", SCENE).towards();
        assertTrue(northEast[0] > 0 && northEast[2] < 0, "el noreste es x+ y z-");
        assertEquals(Math.sin(Math.toRadians(45)), northEast[1], 1.0E-9);
        assertArrayEquals(ShadeLight.resolve("so", SCENE).towards(), ShadeLight.resolve("sw", SCENE).towards(), 0.0);
        // Un vector dice hacia dónde va la luz, como en texture sunlight.
        assertArrayEquals(new double[]{0, 1, 0}, ShadeLight.resolve("0;-1;0", SCENE).towards(), 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> ShadeLight.resolve("de lado", SCENE));
        assertThrows(IllegalArgumentException.class, () -> ShadeLight.resolve("jugador",
                new ShadeLight.Scene(0, new double[3], new double[]{0, 0, 1}, new double[3])));
    }

    @Test
    void piecesBelongToTheirFullBlock() {
        Set<String> blocks = Set.of("minecraft:stone", "minecraft:stone_bricks", "minecraft:oak_planks",
                "minecraft:quartz_block", "minecraft:purpur_block", "minecraft:deepslate_tiles",
                "minecraft:nether_bricks", "minecraft:bamboo_planks", "minecraft:smooth_stone");
        java.util.function.Predicate<String> full = blocks::contains;
        assertEquals("minecraft:stone", ShapeFamilies.base("minecraft:stone_slab", full));
        assertEquals("minecraft:stone_bricks", ShapeFamilies.base("minecraft:stone_brick_stairs", full));
        assertEquals("minecraft:oak_planks", ShapeFamilies.base("minecraft:oak_fence_gate", full));
        assertEquals("minecraft:quartz_block", ShapeFamilies.base("minecraft:quartz_stairs", full));
        assertEquals("minecraft:purpur_block", ShapeFamilies.base("minecraft:purpur_slab", full));
        assertEquals("minecraft:deepslate_tiles", ShapeFamilies.base("minecraft:deepslate_tile_wall", full));
        assertEquals("minecraft:nether_bricks", ShapeFamilies.base("minecraft:nether_brick_fence", full));
        // La caña de bambú no es un bloque entero: la losa de bambú es de tablones.
        assertEquals("minecraft:bamboo_planks", ShapeFamilies.base("minecraft:bamboo_slab", full));
        assertEquals("minecraft:smooth_stone", ShapeFamilies.base("minecraft:smooth_stone_slab", full));
        assertEquals("minecraft:andesite", ShapeFamilies.base("minecraft:andesite", full));
        assertEquals("minecraft:mystery_slab", ShapeFamilies.base("minecraft:mystery_slab", full));
        assertTrue(ShapeFamilies.shaped("minecraft:oak_stairs"));
        assertFalse(ShapeFamilies.shaped("minecraft:oak_planks"));
    }

    @Test
    void selectionPaletteIsDarkToLightAndLeavesAccentsAlone() {
        BlockColorCatalog catalog = TestColors.VANILLA;
        Map<String, Integer> counts = Map.of(
                "minecraft:stone", 500,
                "minecraft:andesite", 200,
                "minecraft:cobblestone", 150,
                "minecraft:calcite", 120,
                "minecraft:deepslate", 90,
                "minecraft:gold_block", 6);
        ShadePalette palette = ShadePalette.fromCounts(counts, catalog, 7, false);
        assertFalse(palette.ramp());
        assertEquals(5, palette.blocks().size(), palette.describe());
        assertFalse(palette.targets().contains("minecraft:gold_block"), "el oro (menos del 2 %) se queda");
        for (int index = 1; index < palette.blocks().size(); index++) {
            assertTrue(palette.blocks().get(index).l() >= palette.blocks().get(index - 1).l(), palette.describe());
        }
        assertEquals("minecraft:deepslate", palette.ids().getFirst());
        assertEquals("minecraft:calcite", palette.ids().getLast());
    }

    @Test
    void singleMaterialGivesARampAroundIt() {
        ShadePalette palette = ShadePalette.fromCounts(Map.of("minecraft:sandstone", 800, "minecraft:torchflower", 3),
                TestColors.VANILLA, 6, false);
        assertTrue(palette.ramp());
        assertEquals(6, palette.blocks().size());
        assertTrue(palette.ids().contains("minecraft:sandstone"), palette.describe());
        assertEquals(Set.of("minecraft:sandstone"), palette.targets());
        ShadePalette forced = ShadePalette.fromCounts(Map.of("minecraft:stone", 10, "minecraft:andesite", 10),
                TestColors.VANILLA, 5, true);
        assertTrue(forced.ramp());
        assertThrows(IllegalArgumentException.class, () -> ShadePalette.fromCounts(Map.of(), TestColors.VANILLA, 5,
                false));
    }

    @Test
    void countsSurfaceMaterialsWithPiecesAsTheirBlock() {
        RegionSnapshot.Builder builder = RegionSnapshot.builder(0, 0, 0, 5, 4, 5)
                .state(new RegionSnapshot.StateInfo(0, null, "minecraft:air", true, false, false))
                .state(new RegionSnapshot.StateInfo(1, null, "minecraft:stone", false, false, true))
                .state(new RegionSnapshot.StateInfo(2, null, "minecraft:stone_bricks", false, false, true))
                .state(new RegionSnapshot.StateInfo(3, null, "minecraft:stone_brick_stairs", false, false, false))
                .state(new RegionSnapshot.StateInfo(4, null, "minecraft:glass", false, false, false));
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 5; z++) {
                    int id;
                    if (y == 0) {
                        id = 1;
                    } else if (y == 1 && x == 2 && z == 2) {
                        id = 2;
                    } else if (y == 1 && x == 1 && z == 2) {
                        id = 3;
                    } else if (y == 1 && x == 3 && z == 2) {
                        id = 4;
                    } else {
                        id = 0;
                    }
                    builder.set(x, y, z, id, true);
                }
            }
        }
        RegionSnapshot snapshot = builder.build();
        Map<String, Integer> counts = ShadeOperation.count(snapshot, TestColors.VANILLA, TextureContext.Filter.ALL,
                () -> { });
        assertEquals(25, counts.get("minecraft:stone"), "todo el suelo da al aire");
        assertEquals(2, counts.get("minecraft:stone_bricks"), "el bloque y la escalera cuentan como ladrillo");
        assertFalse(counts.containsKey("minecraft:glass"), "el cristal no se sombrea");
        assertTrue(ShadeOperation.recolorable(snapshot, 1, 1, 2));
        assertFalse(ShadeOperation.recolorable(snapshot, 3, 1, 2));
        Map<String, Integer> masked = ShadeOperation.count(snapshot, TestColors.VANILLA, (x, y, z) -> y > 0,
                () -> { });
        assertEquals(List.of("minecraft:stone_bricks"), List.copyOf(masked.keySet()));
    }
}
