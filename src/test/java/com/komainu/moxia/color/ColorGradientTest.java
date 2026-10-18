package com.komainu.moxia.color;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorGradientTest {
    private static final BlockColorCatalog CATALOG = TestColors.VANILLA;
    private static final ColorFilter SOLID_CUBES = ColorFilter.of(true, true, true, false);

    private static List<BlockColor> gradient(String from, String to, int steps, ColorFilter filter, int variant) {
        return ColorGradient.between(CATALOG, ColorGradient.Anchor.of(TestColors.block(from)),
                ColorGradient.Anchor.of(TestColors.block(to)), steps, filter, variant);
    }

    private static void assertNoRepeats(List<BlockColor> blocks) {
        Set<String> seen = new HashSet<>();
        for (BlockColor block : blocks) {
            assertTrue(seen.add(block.id()), "repetido: " + block.id() + " en " + names(blocks));
        }
    }

    private static void assertLightnessRises(List<BlockColor> blocks) {
        for (int index = 1; index < blocks.size(); index++) {
            assertTrue(blocks.get(index).l() >= blocks.get(index - 1).l() - 1.0E-9,
                    "la luminosidad baja en el paso " + index + ": " + names(blocks));
        }
    }

    private static String names(List<BlockColor> blocks) {
        return String.join(", ", blocks.stream().map(BlockColor::shortName).toList());
    }

    @Test
    void darkToLightGradientRisesInLightnessWithoutRepeats() {
        for (int steps : new int[]{3, 5, 7, 9, 12, 16}) {
            List<BlockColor> blocks = gradient("deepslate", "calcite", steps, ColorFilter.ANY, 0);
            assertEquals(steps, blocks.size());
            assertEquals("minecraft:deepslate", blocks.getFirst().id());
            assertEquals("minecraft:calcite", blocks.getLast().id());
            assertNoRepeats(blocks);
            assertLightnessRises(blocks);
        }
    }

    @Test
    void lightToDarkGradientFallsInLightness() {
        List<BlockColor> blocks = gradient("white_concrete", "black_concrete", 9, SOLID_CUBES, 0);
        for (int index = 1; index < blocks.size(); index++) {
            assertTrue(blocks.get(index).l() <= blocks.get(index - 1).l() + 1.0E-9, names(blocks));
        }
        assertNoRepeats(blocks);
    }

    @Test
    void stepsStayCloseToTheStraightLineInOklab() {
        List<BlockColor> blocks = gradient("dark_oak_planks", "birch_planks", 7, SOLID_CUBES, 0);
        double[] from = blocks.getFirst().lab();
        double[] to = blocks.getLast().lab();
        for (int index = 1; index < blocks.size() - 1; index++) {
            double[] target = OkLab.lerp(from, to, index / 6.0);
            assertTrue(OkLab.distance(blocks.get(index).lab(), target) < 0.06,
                    "el paso " + index + " se aleja: " + names(blocks));
        }
    }

    @Test
    void filterAppliesToEveryChosenStep() {
        ColorFilter uniform = ColorFilter.of(true, true, true, true);
        List<BlockColor> blocks = gradient("red_concrete", "yellow_concrete", 8, uniform, 0);
        for (BlockColor block : blocks.subList(1, blocks.size() - 1)) {
            assertTrue(uniform.test(block), block.id() + " no pasa el filtro");
        }
        assertNoRepeats(blocks);
    }

    @Test
    void hexEndpointsChooseTheirNearestBlocks() {
        List<BlockColor> blocks = ColorGradient.between(CATALOG, ColorGradient.Anchor.of(0x1B1B1F),
                ColorGradient.Anchor.of(0xF4F4F0), 6, SOLID_CUBES, 0);
        assertEquals(6, blocks.size());
        assertTrue(blocks.getFirst().l() < 0.3, names(blocks));
        assertTrue(blocks.getLast().l() > 0.85, names(blocks));
        assertNoRepeats(blocks);
        assertLightnessRises(blocks);
    }

    @Test
    void sameLightnessEndpointsStillGiveDistinctBlocks() {
        // Rojo y azul de lana con luminosidad parecida: no se exige subir, pero no se repite nada.
        List<BlockColor> blocks = gradient("red_wool", "blue_wool", 7, SOLID_CUBES, 0);
        assertEquals(7, blocks.size());
        assertNoRepeats(blocks);
    }

    @Test
    void variantsTryOtherBlocksAndKeepTheRules() {
        List<BlockColor> first = gradient("deepslate", "calcite", 9, SOLID_CUBES, 0);
        List<BlockColor> second = gradient("deepslate", "calcite", 9, SOLID_CUBES, 1);
        List<BlockColor> third = gradient("deepslate", "calcite", 9, SOLID_CUBES, 2);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
        for (List<BlockColor> blocks : List.of(second, third)) {
            assertEquals("minecraft:deepslate", blocks.getFirst().id());
            assertEquals("minecraft:calcite", blocks.getLast().id());
            assertNoRepeats(blocks);
            assertLightnessRises(blocks);
        }
        // Es determinista: la misma variante da lo mismo.
        assertEquals(second, gradient("deepslate", "calcite", 9, SOLID_CUBES, 1));
    }

    @Test
    void rampKeepsTheBaseAndClimbsInLightness() {
        for (String base : List.of("stone", "sandstone", "red_terracotta", "white_concrete", "black_wool")) {
            BlockColor block = TestColors.block(base);
            List<BlockColor> ramp = ColorGradient.ramp(CATALOG, block, 7, ColorFilter.BUILDING);
            assertEquals(7, ramp.size());
            assertTrue(ramp.contains(block), base + ": " + names(ramp));
            assertNoRepeats(ramp);
            assertLightnessRises(ramp);
            assertTrue(ramp.getLast().l() - ramp.getFirst().l() > 0.15, base + " apenas cambia: " + names(ramp));
            for (BlockColor step : ramp) {
                assertTrue(step == block || ColorFilter.BUILDING.test(step), step.id());
            }
        }
    }

    @Test
    void solveKeepsFixedStepsAndRejectsImpossibleRequests() {
        BlockColor stone = TestColors.block("stone");
        List<double[]> targets = List.of(OkLab.of(0x202020), stone.lab(), OkLab.of(0xE0E0E0));
        List<BlockColor> blocks = ColorGradient.solve(CATALOG.candidates(SOLID_CUBES), targets, Map.of(1, stone),
                Map.of());
        assertEquals(stone, blocks.get(1));
        assertThrows(IllegalArgumentException.class, () -> ColorGradient.between(CATALOG,
                ColorGradient.Anchor.of(stone), ColorGradient.Anchor.of(stone), 1, SOLID_CUBES, 0));
        assertThrows(IllegalArgumentException.class, () -> ColorGradient.between(CATALOG,
                ColorGradient.Anchor.of(stone), ColorGradient.Anchor.of(stone), 33, SOLID_CUBES, 0));
    }
}
