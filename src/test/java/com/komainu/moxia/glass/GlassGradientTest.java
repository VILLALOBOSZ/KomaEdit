package com.komainu.moxia.glass;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlassGradientTest {
    private static final List<GlassColors.Swatch> BLACK = List.of(new GlassColors.Swatch("", 0, 0, 0, 1.0));
    private static final List<GlassColors.Swatch> WHITE = List.of(new GlassColors.Swatch("", 255, 255, 255, 1.0));

    @Test
    void hexAndOklabRoundTrip() {
        assertArrayEquals(new double[]{0x3A, 0x7F, 0xD2}, GlassColors.hex("#3A7FD2"));
        assertArrayEquals(new double[]{255, 0, 0}, GlassColors.hex("f00"));
        assertThrows(IllegalArgumentException.class, () -> GlassColors.hex("#12345"));
        assertEquals("#3A7FD2", GlassColors.toHex(GlassColors.rgb(GlassColors.oklab(GlassColors.hex("#3A7FD2")))));
        assertEquals(0.0, GlassColors.distance(new double[]{10, 20, 30}, new double[]{10, 20, 30}), 1.0E-12);
    }

    @Test
    void oneLayerOverWhiteFindsTheDye() {
        GlassGradientSolver solver = GlassGradientSolver.standard();
        // Rojo a través de una capa sobre blanco: 0,46·rojo + 0,54·blanco.
        double[] seen = {0.46 * 153 + 0.54 * 255, 0.46 * 51 + 0.54 * 255, 0.46 * 51 + 0.54 * 255};
        GlassGradientSolver.Stack stack = solver.solve(seen, 1, 6, WHITE, false);
        assertEquals(List.of("minecraft:red_stained_glass"), stack.layers());
        assertEquals(0.0, stack.error(), 1.0E-6);
    }

    @Test
    void moreLayersGetCloser() {
        GlassGradientSolver solver = GlassGradientSolver.standard();
        double[] target = GlassColors.hex("#2E7D6A");
        double one = solver.solve(target, 1, 6, BLACK, false).error();
        double three = solver.solve(target, 3, 6, BLACK, false).error();
        double five = solver.solve(target, 5, 6, BLACK, false).error();
        assertTrue(three <= one && five <= three, one + " ≥ " + three + " ≥ " + five);
    }

    @Test
    void beamMatchesExhaustiveSearchOnTwoLayers() {
        GlassGradientSolver solver = GlassGradientSolver.standard();
        double[] target = GlassColors.hex("#B04A8C");
        double best = Double.MAX_VALUE;
        for (GlassColors.Swatch front : GlassColors.GLASS) {
            for (GlassColors.Swatch back : GlassColors.GLASS) {
                double[] color = new double[3];
                double transmission = 1.0;
                for (GlassColors.Swatch layer : List.of(front, back)) {
                    double[] rgb = layer.rgb();
                    for (int channel = 0; channel < 3; channel++) {
                        color[channel] += transmission * layer.alpha() * rgb[channel];
                    }
                    transmission *= 1.0 - layer.alpha();
                }
                for (int channel = 0; channel < 3; channel++) {
                    color[channel] += transmission * 255;
                }
                best = Math.min(best, GlassColors.distance(color, target));
            }
        }
        assertEquals(best, solver.solve(target, 2, 6, WHITE, false).error(), 1.0E-9);
    }

    @Test
    void unorderedSearchUsesSortedStacks() {
        GlassGradientSolver.Stack stack = GlassGradientSolver.standard().solve(GlassColors.hex("#7A5BB0"), 4, 6,
                WHITE, true);
        List<String> order = GlassColors.GLASS.stream().map(GlassColors.Swatch::blockId).toList();
        for (int layer = 1; layer < stack.layers().size(); layer++) {
            assertTrue(order.indexOf(stack.layers().get(layer - 1)) <= order.indexOf(stack.layers().get(layer)));
        }
    }

    @Test
    void gradientGoesFromStartToEndAndPicksBackgrounds() {
        GlassGradientSolver solver = GlassGradientSolver.standard();
        double[] start = GlassColors.hex("#E8D44D");
        double[] end = GlassColors.hex("#3A4FD2");
        List<GlassGradientSolver.Stack> stacks = solver.gradient(start, end, 3, 8, 5, WHITE, false, () -> false);
        assertEquals(8, stacks.size());
        assertTrue(GlassColors.distance(stacks.getFirst().color(), start)
                < GlassColors.distance(stacks.getFirst().color(), end));
        assertTrue(GlassColors.distance(stacks.getLast().color(), end)
                < GlassColors.distance(stacks.getLast().color(), start));
        List<GlassColors.Swatch> backgrounds = List.of(
                new GlassColors.Swatch("minecraft:black_concrete", 8, 10, 15, 1.0),
                new GlassColors.Swatch("minecraft:white_concrete", 207, 213, 214, 1.0),
                new GlassColors.Swatch("minecraft:yellow_concrete", 241, 175, 21, 1.0));
        GlassGradientSolver.Stack chosen = solver.solve(start, 2, 5, backgrounds, false);
        assertTrue(backgrounds.stream().anyMatch(swatch -> swatch.blockId().equals(chosen.background())));
        assertTrue(chosen.error() <= solver.solve(start, 2, 5, backgrounds.subList(0, 1), false).error(),
                "elegir el fondo nunca empeora el color");
        assertTrue(chosen.error() < 0.06, "un amarillo claro sale bien con fondo: " + chosen.error());
        assertThrows(IllegalArgumentException.class,
                () -> solver.gradient(start, end, 0, 8, 5, WHITE, false, () -> false));
    }

    @Test
    void backgroundTableIsBundled() {
        List<GlassColors.Swatch> backgrounds = GlassColors.backgrounds();
        assertTrue(backgrounds.size() > 100);
        assertTrue(backgrounds.stream().anyMatch(swatch -> swatch.blockId().equals("minecraft:white_concrete")));
        assertFalse(backgrounds.stream().anyMatch(swatch -> swatch.blockId().contains("glass")));
    }

    @Test
    void requestReadsEzEditsArguments() {
        GlassGradientRequest full = GlassGradientRequest.parse("#E8D44D #3A4FD2 4 16 7 right -c #202020 -bs");
        assertArrayEquals(GlassColors.hex("#3A4FD2"), full.end());
        assertEquals(4, full.layers());
        assertEquals(16, full.length());
        assertEquals(7, full.quality());
        assertEquals("right", full.direction());
        assertArrayEquals(new double[]{32, 32, 32}, full.background());
        assertTrue(full.findBackground());
        assertTrue(full.unordered());

        GlassGradientRequest single = GlassGradientRequest.parse("ff8800 3");
        assertArrayEquals(single.start(), single.end(), "sin color final, el inicial en todo el degradado");
        assertEquals(1, single.length());
        assertEquals(GlassGradientRequest.DEFAULT_QUALITY, single.quality());
        assertEquals("up", single.direction());
        assertFalse(single.findBackground());

        assertThrows(IllegalArgumentException.class, () -> GlassGradientRequest.parse("#ff8800"));
        assertThrows(IllegalArgumentException.class, () -> GlassGradientRequest.parse("#ff8800 40"));
        assertThrows(IllegalArgumentException.class, () -> GlassGradientRequest.parse("#ff8800 3 4 5 sideways"));
        assertThrows(IllegalArgumentException.class, () -> GlassGradientRequest.parse("#ff8800 3 -x"));
    }
}
