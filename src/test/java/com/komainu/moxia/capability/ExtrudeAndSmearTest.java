package com.komainu.moxia.capability;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtrudeAndSmearTest {
    private static final Cell UP = new Cell(0, 1, 0);
    private static final Cell EAST = new Cell(1, 0, 0);

    /** Mundo de prueba: bloques por posición; lo que no está es aire. */
    private static final class FakeWorld {
        private final Map<Cell, String> blocks = new HashMap<>();

        FakeWorld fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        blocks.put(new Cell(x, y, z), block);
                    }
                }
            }
            return this;
        }

        LayerFinder.View viewMatching(String block) {
            return new LayerFinder.View() {
                @Override
                public boolean matches(int x, int y, int z) {
                    return block.equals(blocks.get(new Cell(x, y, z)));
                }

                @Override
                public boolean free(int x, int y, int z) {
                    return !blocks.containsKey(new Cell(x, y, z));
                }
            };
        }
    }

    @Test
    void layerFollowsTheExposedFaceOfTheSameBlock() {
        FakeWorld world = new FakeWorld()
                .fill(0, 0, 0, 4, 0, 2, "stone")
                .fill(5, 0, 0, 5, 0, 2, "dirt")
                .fill(2, 1, 1, 2, 1, 1, "glass");
        LayerFinder.Layer layer = LayerFinder.find(world.viewMatching("stone"), new Cell(0, 0, 0), UP, 100);
        // 15 de piedra, menos la que tiene el cristal encima; la tierra no entra.
        assertEquals(14, layer.cells().size());
        assertFalse(layer.truncated());
        assertFalse(layer.cells().contains(new Cell(2, 0, 1)));
        Set<Cell> targets = new HashSet<>(LayerFinder.extruded(layer, UP));
        assertTrue(targets.contains(new Cell(0, 1, 0)));
        assertFalse(targets.contains(new Cell(2, 1, 1)));
    }

    @Test
    void layerStopsAtTheLimitAndSaysSo() {
        FakeWorld world = new FakeWorld().fill(0, 0, 0, 9, 0, 9, "stone");
        LayerFinder.Layer layer = LayerFinder.find(world.viewMatching("stone"), new Cell(5, 0, 5), UP, 30);
        assertEquals(30, layer.cells().size());
        assertTrue(layer.truncated());
        assertEquals(new Cell(5, 0, 5), layer.cells().getFirst());
        LayerFinder.Layer exact = LayerFinder.find(world.viewMatching("stone"), new Cell(0, 0, 0), UP, 100);
        assertEquals(100, exact.cells().size());
        assertFalse(exact.truncated());
    }

    @Test
    void layerOnASideFaceStaysInThatPlane() {
        FakeWorld world = new FakeWorld().fill(0, 0, 0, 0, 3, 3, "bricks");
        LayerFinder.Layer layer = LayerFinder.find(world.viewMatching("bricks"), new Cell(0, 1, 1), EAST, 100);
        assertEquals(16, layer.cells().size());
        for (Cell cell : layer.cells()) {
            assertEquals(0, cell.x());
        }
    }

    @Test
    void coveredOrDifferentStartGivesNothing() {
        FakeWorld world = new FakeWorld().fill(0, 0, 0, 0, 1, 0, "stone");
        assertTrue(LayerFinder.find(world.viewMatching("stone"), new Cell(0, 0, 0), UP, 10).isEmpty());
        assertTrue(LayerFinder.find(world.viewMatching("dirt"), new Cell(0, 1, 0), UP, 10).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> LayerFinder.find(world.viewMatching("stone"), new Cell(0, 1, 0), new Cell(1, 1, 0), 10));
    }

    @Test
    void rowTakesTheLongestStraightRunInTheFacePlane() {
        FakeWorld world = new FakeWorld()
                .fill(0, 0, 0, 6, 0, 0, "fence")
                .fill(3, 0, 1, 3, 0, 2, "fence");
        List<Cell> row = SmearPlanner.row(world.viewMatching("fence"), new Cell(3, 0, 0), UP, 64);
        assertEquals(7, row.size());
        assertTrue(row.contains(new Cell(0, 0, 0)));
        assertTrue(row.contains(new Cell(6, 0, 0)));
        assertEquals(3, SmearPlanner.row(world.viewMatching("fence"), new Cell(3, 0, 0), UP, 3).size());
    }

    @Test
    void lookingUpAlongsideAPillarStretchesItUp() {
        // Ojos a 4 bloques al este del pilar, mirando a 45° hacia arriba y al oeste: el rayo cruza el eje a y≈4.
        double[] eye = {4.5, 0.5, 0.5};
        double[] look = {-1.0, 1.0, 0.0};
        assertEquals(4, SmearPlanner.length(eye, look, new Cell(0, 0, 0), UP, 6.0, 64));
        // Mirando hacia abajo, al otro lado: estira hacia abajo.
        assertEquals(-4, SmearPlanner.length(eye, new double[]{-1.0, -1.0, 0.0}, new Cell(0, 0, 0), UP, 6.0, 64));
        // El tope del servidor.
        assertEquals(2, SmearPlanner.length(eye, look, new Cell(0, 0, 0), UP, 6.0, 2));
    }

    @Test
    void lookingAlongTheAxisUsesTheDistanceInstead() {
        double[] eye = {0.5, 1.5, 0.5};
        double[] look = {1.0, 0.0, 0.0};
        // Desde encima del origen y mirando por el eje X: se proyecta el punto a 6 bloques.
        assertEquals(6, SmearPlanner.length(eye, look, new Cell(0, 1, 0), EAST, 6.0, 64));
    }

    @Test
    void rayPointingAwayFromTheAxisDoesNotStretch() {
        double[] eye = {4.5, 0.5, 0.5};
        double[] look = {1.0, 1.0, 0.0};
        assertEquals(0, SmearPlanner.length(eye, look, new Cell(0, 0, 0), UP, 6.0, 64));
    }

    @Test
    void endsSentByTheClientAreChecked() {
        Cell anchor = new Cell(10, 64, 10);
        assertEquals(5, SmearPlanner.validateEnd(anchor, UP, new Cell(10, 69, 10), 64));
        assertEquals(-3, SmearPlanner.validateEnd(anchor, new Cell(0, -1, 0), new Cell(10, 67, 10), 64));
        assertThrows(IllegalArgumentException.class,
                () -> SmearPlanner.validateEnd(anchor, UP, new Cell(11, 69, 10), 64));
        assertThrows(IllegalArgumentException.class,
                () -> SmearPlanner.validateEnd(anchor, UP, anchor, 64));
        assertThrows(IllegalArgumentException.class,
                () -> SmearPlanner.validateEnd(anchor, UP, new Cell(10, 164, 10), 64));
    }

    @Test
    void copiesRepeatTheWholeRowStepByStep() {
        List<Cell> row = List.of(new Cell(0, 0, 0), new Cell(1, 0, 0));
        List<SmearPlanner.Copy> copies = SmearPlanner.copies(row, UP, -2);
        assertEquals(4, copies.size());
        assertEquals(new SmearPlanner.Copy(new Cell(0, 0, 0), new Cell(0, -1, 0)), copies.get(0));
        assertEquals(new SmearPlanner.Copy(new Cell(1, 0, 0), new Cell(1, -2, 0)), copies.get(3));
    }
}
