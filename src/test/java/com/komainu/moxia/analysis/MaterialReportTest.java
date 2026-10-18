package com.komainu.moxia.analysis;

import com.komainu.moxia.region.RegionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialReportTest {
    /** Registro falso con los bloques y objetos de estas pruebas. */
    static final SurvivalCost.Registry REGISTRY = new SurvivalCost.Registry() {
        private final Set<String> blocks = Set.of("oak_planks", "spruce_planks", "stone", "stone_bricks", "bricks",
                "quartz_block", "smooth_quartz", "cobblestone", "bamboo_planks", "bamboo_mosaic", "deepslate_tiles",
                "purpur_block", "oak_door", "glass", "torch", "wall_torch", "flower_pot", "potted_poppy", "poppy",
                "snow", "sunflower", "red_bed", "oak_sign", "oak_wall_sign", "candle", "water", "redstone_wire",
                "sandstone", "white_banner", "white_wall_banner");
        private final Set<String> items = Set.of("oak_planks", "spruce_planks", "stone", "stone_bricks", "bricks",
                "quartz_block", "smooth_quartz", "cobblestone", "bamboo_planks", "bamboo_mosaic", "deepslate_tiles",
                "purpur_block", "oak_door", "glass", "torch", "flower_pot", "poppy", "snow", "sunflower", "red_bed",
                "oak_sign", "candle", "redstone", "sandstone", "white_banner");
        private final Map<String, Integer> stacks = Map.of("oak_door", 64, "red_bed", 1, "oak_sign", 16,
                "snow", 64, "white_banner", 16);

        @Override
        public boolean isBlock(String id) {
            return blocks.contains(id.substring(id.indexOf(':') + 1));
        }

        @Override
        public boolean isItem(String id) {
            return items.contains(id.substring(id.indexOf(':') + 1));
        }

        @Override
        public int maxStack(String itemId) {
            return stacks.getOrDefault(itemId.substring(itemId.indexOf(':') + 1), 64);
        }
    };

    private static double cost(String block, String properties, String item) {
        return SurvivalCost.of(block, properties, REGISTRY).stream().filter(cost -> cost.item().equals(item))
                .mapToDouble(SurvivalCost.Cost::amount).sum();
    }

    @Test
    void slabsStairsAndWallsBecomeTheirBaseBlock() {
        assertEquals(0.5, cost("minecraft:oak_slab", "type=bottom,waterlogged=false", "minecraft:oak_planks"));
        assertEquals(1.0, cost("minecraft:oak_slab", "type=double,waterlogged=false", "minecraft:oak_planks"));
        assertEquals(1.5, cost("minecraft:oak_stairs", "facing=north,half=bottom", "minecraft:oak_planks"),
                "las de madera salen de la mesa de trabajo: 6 tablones dan 4");
        assertEquals(1.5, cost("minecraft:bamboo_mosaic_stairs", "", "minecraft:bamboo_mosaic"));
        assertEquals(1.0, cost("minecraft:stone_brick_stairs", "", "minecraft:stone_bricks"),
                "las de piedra, del cortapiedras: 1 a 1");
        assertEquals(1.0, cost("minecraft:quartz_stairs", "", "minecraft:quartz_block"));
        assertEquals(0.5, cost("minecraft:smooth_quartz_slab", "type=top", "minecraft:smooth_quartz"));
        assertEquals(0.5, cost("minecraft:brick_slab", "type=bottom", "minecraft:bricks"));
        assertEquals(0.5, cost("minecraft:purpur_slab", "type=bottom", "minecraft:purpur_block"));
        assertEquals(1.0, cost("minecraft:deepslate_tile_wall", "", "minecraft:deepslate_tiles"));
        assertEquals(1.0, cost("minecraft:cobblestone_wall", "up=true", "minecraft:cobblestone"));
        assertEquals(1.0, cost("minecraft:petrified_oak_slab", "type=bottom", "minecraft:petrified_oak_slab"),
                "sin bloque base conocido cuenta la propia losa");
    }

    @Test
    void doubleBlocksUnitsAndPlacedItems() {
        assertEquals(1.0, cost("minecraft:oak_door", "half=lower,facing=north", "minecraft:oak_door"));
        assertTrue(SurvivalCost.of("minecraft:oak_door", "half=upper,facing=north", REGISTRY).isEmpty(),
                "la mitad de arriba no cuenta");
        assertTrue(SurvivalCost.of("minecraft:red_bed", "part=head", REGISTRY).isEmpty());
        assertTrue(SurvivalCost.of("minecraft:sunflower", "half=upper", REGISTRY).isEmpty());
        assertEquals(5.0, cost("minecraft:snow", "layers=5", "minecraft:snow"), "cada capa es una bola colocada");
        assertEquals(3.0, cost("minecraft:candle", "candles=3,lit=false", "minecraft:candle"));
        assertEquals(1.0, cost("minecraft:wall_torch", "facing=east", "minecraft:torch"));
        assertEquals(1.0, cost("minecraft:oak_wall_sign", "facing=east", "minecraft:oak_sign"));
        assertEquals(1.0, cost("minecraft:white_wall_banner", "facing=east", "minecraft:white_banner"));
        assertEquals(1.0, cost("minecraft:redstone_wire", "power=0", "minecraft:redstone"));
        assertEquals(1.0, cost("minecraft:potted_poppy", "", "minecraft:flower_pot"));
        assertEquals(1.0, cost("minecraft:potted_poppy", "", "minecraft:poppy"));
        assertTrue(SurvivalCost.of("minecraft:water", "level=0", REGISTRY).isEmpty());
        assertTrue(SurvivalCost.of("minecraft:fire", "age=0", REGISTRY).isEmpty());
    }

    @Test
    void reportCountsStacksAndChests() {
        List<MaterialReport.Counted> counted = List.of(
                new MaterialReport.Counted("minecraft:oak_stairs", "facing=north,half=bottom", 100),
                new MaterialReport.Counted("minecraft:oak_stairs", "facing=south,half=top", 20),
                new MaterialReport.Counted("minecraft:oak_slab", "type=bottom", 33),
                new MaterialReport.Counted("minecraft:oak_planks", "", 1000),
                new MaterialReport.Counted("minecraft:stone", "", 64 * 27 * 2),
                new MaterialReport.Counted("minecraft:oak_sign", "rotation=0", 20),
                new MaterialReport.Counted("minecraft:red_bed", "part=foot", 2),
                new MaterialReport.Counted("minecraft:red_bed", "part=head", 2),
                new MaterialReport.Counted("minecraft:water", "level=0", 500)
        );
        MaterialReport report = MaterialReport.build(counted, REGISTRY);
        assertEquals(100 + 20 + 33 + 1000 + 3456 + 20 + 4, report.blocks(), "el agua no cuenta");
        MaterialReport.Line planks = report.cost().stream()
                .filter(line -> line.item().equals("minecraft:oak_planks")).findFirst().orElseThrow();
        // 120 escaleras × 1,5 + 33 losas × 0,5 (16,5 → 17) + 1000 tablones.
        assertEquals(180 + 17 + 1000, planks.amount());
        assertEquals("18 pilas + 45", MaterialReport.stacks(planks));
        MaterialReport.Line signs = report.cost().stream()
                .filter(line -> line.item().equals("minecraft:oak_sign")).findFirst().orElseThrow();
        assertEquals("1 pila de 16 + 4", MaterialReport.stacks(signs));
        MaterialReport.Line beds = report.cost().stream()
                .filter(line -> line.item().equals("minecraft:red_bed")).findFirst().orElseThrow();
        assertEquals(2, beds.amount(), "una cama por pie");
        assertEquals(2, beds.slots());
        // Huecos: 54 de piedra + 19 de tablones + 2 de carteles + 2 camas = 77 → 3 cofres.
        assertEquals(54 + 19 + 2 + 2, report.totalSlots());
        assertEquals(3, report.chests());
        assertEquals("minecraft:stone", report.cost().getFirst().item(), "ordenado de más a menos");
        String csv = report.csv();
        assertTrue(csv.startsWith("seccion;id;cantidad;pila;pilas;sueltos;huecos\n"));
        assertTrue(csv.contains("coste;minecraft:oak_planks;1197;64;18;45;19\n"), csv);
        assertTrue(csv.contains("colocado;minecraft:oak_stairs;120;;;;\n"), csv);
        assertTrue(report.clipboard().contains("oak_planks: 1197 (18 pilas + 45)"), report.clipboard());
    }

    @Test
    void countsTheSelectionWithTheMask() {
        RegionSnapshot.Builder builder = RegionSnapshot.builder(0, 0, 0, 4, 2, 1)
                .state(new RegionSnapshot.StateInfo(0, null, "minecraft:air", true, false, false))
                .state(new RegionSnapshot.StateInfo(1, null, "minecraft:stone", false, false, true))
                .state(new RegionSnapshot.StateInfo(2, null, "minecraft:oak_slab", false, false, false,
                        "type=bottom,waterlogged=false"))
                .state(new RegionSnapshot.StateInfo(3, null, "minecraft:water", false, true, false, "level=0"));
        builder.set(0, 0, 0, 1, true).set(1, 0, 0, 1, true).set(2, 0, 0, 2, true).set(3, 0, 0, 3, true)
                .set(0, 1, 0, 1, false).set(1, 1, 0, 0, true).set(2, 1, 0, 2, true).set(3, 1, 0, 1, true);
        RegionSnapshot snapshot = builder.build();
        List<MaterialReport.Counted> all = MaterialReport.count(snapshot, null, () -> false);
        MaterialReport report = MaterialReport.build(all, REGISTRY);
        assertEquals(5, report.blocks(), "3 de piedra seleccionadas y 2 losas; el agua y el margen no");
        List<MaterialReport.Counted> masked = MaterialReport.count(snapshot, (x, y, z) -> y == 0, () -> false);
        assertEquals(3, MaterialReport.build(masked, REGISTRY).blocks());
    }
}
