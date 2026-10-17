package com.komainu.moxia.brush.ez;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetalPainterTest {
    @Test
    void amountGrowsAsTheNoiseDrops() {
        assertEquals(0, PetalPainter.amount(0.3, 0.3, 4), "en la densidad justa no hay follaje");
        assertEquals(0, PetalPainter.amount(0.3, 0.9, 4));
        assertEquals(4, PetalPainter.amount(0.3, 0.0, 4), "el valor más bajo da el máximo");
        assertEquals(1, PetalPainter.amount(0.3, 0.29, 4));
        assertEquals(0, PetalPainter.amount(0.0, 0.0, 4), "densidad cero: nada");
        assertEquals(1, PetalPainter.amount(1.0, 0.99, 1), "bloques de una sola pieza");
    }

    @Test
    void densityControlsCoverage() {
        double sparse = coverage(0.2);
        double dense = coverage(0.8);
        assertEquals(0.2, sparse, 0.05, "con ruido blanco, la densidad es la fracción cubierta");
        assertEquals(0.8, dense, 0.05);
    }

    @Test
    void onlyAirAboveTheMaskReceivesFoliage() {
        FakeWorld world = new FakeWorld(10);
        world.floor(0, "grass_block");
        world.set(1, 0, 0, "sand", BrushWorld.Cell.FULL);
        world.set(0, 1, 1, "stone", BrushWorld.Cell.FULL);
        world.set(-1, 0, 0, "oak_slab", BrushWorld.Cell.SHAPED);
        PetalPainter.Settings settings = new PetalPainter.Settings("pink_petals", 4, 1.0, (x, y, z) -> 0.0,
                BlockFilter.parse("#fullblock,!sand"), 7);
        List<BlockVector3> placed = new ArrayList<>();
        PetalPainter.paint(world, BlockVector3.at(0, 1, 0), 2, settings, false,
                (x, y, z, amount, facing) -> {
                    assertEquals(4, amount);
                    assertTrue(facing >= 0 && facing < 4);
                    placed.add(BlockVector3.at(x, y, z));
                });
        assertTrue(placed.contains(BlockVector3.at(0, 2, 1)), "también encima de la piedra suelta");
        assertFalse(placed.contains(BlockVector3.at(0, 2, 0)), "en el aire sin suelo, nada");
        assertFalse(placed.contains(BlockVector3.at(1, 1, 0)), "la arena está excluida");
        assertFalse(placed.contains(BlockVector3.at(-1, 1, 0)), "las losas no son cubo entero");
        assertFalse(placed.contains(BlockVector3.at(0, 1, 1)), "ocupado por piedra");
        assertTrue(placed.contains(BlockVector3.at(0, 1, 0)));
    }

    @Test
    void repaintingThickensAndErasingRemoves() {
        FakeWorld world = new FakeWorld(6);
        world.floor(0, "grass_block");
        world.set(0, 1, 0, "pink_petals", BrushWorld.Cell.OTHER).amount(0, 1, 0, 2);
        world.set(1, 1, 0, "pink_petals", BrushWorld.Cell.OTHER).amount(1, 1, 0, 4);
        PetalPainter.Settings settings = new PetalPainter.Settings("minecraft:pink_petals", 4, 1.0,
                (x, y, z) -> 0.5, BlockFilter.FULL_BLOCK, 1);
        Map<BlockVector3, int[]> result = new HashMap<>();
        PetalPainter.paint(world, BlockVector3.at(0, 1, 0), 1, settings, false,
                (x, y, z, amount, facing) -> result.put(BlockVector3.at(x, y, z), new int[]{amount, facing}));
        assertEquals(3, result.get(BlockVector3.at(0, 1, 0))[0], "2 piezas suben a 3");
        assertEquals(-1, result.get(BlockVector3.at(0, 1, 0))[1], "conserva la orientación");
        assertFalse(result.containsKey(BlockVector3.at(1, 1, 0)), "ya estaba al máximo");

        List<BlockVector3> removed = new ArrayList<>();
        PetalPainter.paint(world, BlockVector3.at(0, 1, 0), 1, settings, true, (x, y, z, amount, facing) -> {
            assertEquals(0, amount);
            removed.add(BlockVector3.at(x, y, z));
        });
        assertEquals(List.of(BlockVector3.at(0, 1, 0), BlockVector3.at(1, 1, 0)), removed);
    }

    @Test
    void settingsValidateDensity() {
        assertThrows(IllegalArgumentException.class, () -> new PetalPainter.Settings("pink_petals", 4, 1.5,
                BrushNoise.white(0), BlockFilter.FULL_BLOCK, 0));
    }

    @Test
    void blockFilterTerms() {
        BlockFilter filter = BlockFilter.parse("#solid,!oak_slab");
        assertTrue(filter.matches("stone", BrushWorld.Cell.FULL));
        assertTrue(filter.matches("minecraft:stone_slab", BrushWorld.Cell.SHAPED));
        assertFalse(filter.matches("oak_slab", BrushWorld.Cell.SHAPED));
        assertFalse(filter.matches("poppy", BrushWorld.Cell.OTHER));
        BlockFilter onlyExclusions = BlockFilter.parse("!sand");
        assertTrue(onlyExclusions.matches("poppy", BrushWorld.Cell.OTHER), "solo exclusiones parte de #existing");
        assertFalse(onlyExclusions.matches("air", BrushWorld.Cell.AIR));
        assertTrue(BlockFilter.parse("grass_block,moss_block").matches("minecraft:moss_block", BrushWorld.Cell.FULL));
        assertThrows(IllegalArgumentException.class, () -> BlockFilter.parse("#logs"));
    }

    private static double coverage(double density) {
        FakeWorld world = new FakeWorld(20);
        world.floor(0, "grass_block");
        PetalPainter.Settings settings = new PetalPainter.Settings("pink_petals", 4, density, BrushNoise.white(99),
                BlockFilter.FULL_BLOCK, 3);
        int[] placed = {0};
        PetalPainter.paint(world, BlockVector3.at(0, 1, 0), 15, settings, false,
                (x, y, z, amount, facing) -> placed[0]++);
        int total = 0;
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                if (x * x + z * z <= 15.5 * 15.5) {
                    total++;
                }
            }
        }
        return placed[0] / (double) total;
    }
}
