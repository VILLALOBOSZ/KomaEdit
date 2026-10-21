package com.komainu.moxia.rebuild;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los órdenes de aparición: cada uno pone cada bloque de la obra una sola vez y en el orden que promete. */
class RebuildSequenceTest {
    private static final RebuildFixtures.Fixture HOUSE = RebuildFixtures.selection(RebuildFixtures.house());

    private static RebuildSequence sequence(RebuildOrder order) {
        return sequence(HOUSE, order, RebuildSequence.Options.DEFAULT);
    }

    private static RebuildSequence sequence(RebuildFixtures.Fixture fixture, RebuildOrder order,
                                            RebuildSequence.Options options) {
        return RebuildSequence.of(fixture.model(), order, options);
    }

    /** Todos los bloques de la obra (sin el aire) salen una vez y solo una. */
    private static void assertEveryBlockOnce(RebuildFixtures.Fixture fixture, RebuildSequence sequence) {
        RebuildModel model = fixture.model();
        assertEquals(model.buildable(), sequence.size(), sequence.order() + ": pasos");
        BitSet seen = new BitSet(model.size());
        for (int step = 0; step < sequence.size(); step++) {
            int entry = sequence.entry(step);
            assertFalse(seen.get(entry), sequence.order() + ": la entrada " + entry + " sale dos veces");
            assertNotEquals(RebuildModel.AIR, model.target(entry), sequence.order() + ": el aire no es un paso");
            seen.set(entry);
        }
        for (int entry = 0; entry < model.size(); entry++) {
            assertEquals(model.target(entry) != RebuildModel.AIR, seen.get(entry),
                    sequence.order() + ": la entrada " + entry);
        }
    }

    @Test
    void everyOrderPlacesEveryBlockExactlyOnce() {
        RebuildFixtures.Fixture paste = RebuildFixtures.paste(RebuildFixtures.house(),
                new RebuildFixtures.Build().fill(-2, -1, -2, 16, 2, 16, "dirt"));
        for (RebuildFixtures.Fixture fixture : List.of(HOUSE, paste)) {
            for (RebuildOrder order : RebuildOrder.values()) {
                for (RebuildAxis axis : RebuildAxis.values()) {
                    RebuildSequence.Options options = new RebuildSequence.Options(axis, new int[]{7, 3, 7}, 42L);
                    assertEveryBlockOnce(fixture, sequence(fixture, order, options));
                    if (order != RebuildOrder.AXIS) {
                        break;
                    }
                }
            }
        }
    }

    @Test
    void anEmptyBuildHasNoSteps() {
        RebuildFixtures.Fixture empty = RebuildFixtures.selection(new RebuildFixtures.Build());
        for (RebuildOrder order : RebuildOrder.values()) {
            assertEquals(0, sequence(empty, order, RebuildSequence.Options.DEFAULT).size(), order.id());
        }
    }

    @Test
    void layersGoBottomUpAndZigzagInsideEachLayer() {
        RebuildFixtures.Fixture slab = RebuildFixtures.selection(new RebuildFixtures.Build()
                .fill(0, 0, 0, 3, 1, 2, "stone"));
        RebuildSequence sequence = sequence(slab, RebuildOrder.LAYERS, RebuildSequence.Options.DEFAULT);
        RebuildModel model = slab.model();
        for (int step = 1; step < sequence.size(); step++) {
            assertTrue(model.y(sequence.entry(step)) >= model.y(sequence.entry(step - 1)), "nunca baja");
        }
        // La primera fila va de x 0 a 3 y la siguiente vuelve de 3 a 0: el barrido no salta de un lado a otro.
        int[] firstRows = new int[8];
        for (int step = 0; step < 8; step++) {
            firstRows[step] = model.x(sequence.entry(step));
            assertEquals(step < 4 ? 0 : 1, model.z(sequence.entry(step)), "fila del paso " + step);
        }
        assertArrayEquals(new int[]{0, 1, 2, 3, 3, 2, 1, 0}, firstRows);
        for (int step = 1; step < sequence.size(); step++) {
            int previous = sequence.entry(step - 1);
            int current = sequence.entry(step);
            int distance = Math.abs(model.x(current) - model.x(previous)) + Math.abs(model.z(current)
                    - model.z(previous)) + Math.abs(model.y(current) - model.y(previous));
            assertTrue(distance <= 1, "paso " + step + " salta " + distance + " bloques");
        }
        assertEquals("capa 0", sequence.groupLabel(0));
        assertTrue(sequence.startsGroup(12), "la capa 1 empieza en el paso 12");
        assertFalse(sequence.startsGroup(11));
    }

    @Test
    void axisSweepsInTheChosenDirection() {
        for (RebuildAxis axis : RebuildAxis.values()) {
            RebuildSequence sequence = sequence(HOUSE, RebuildOrder.AXIS,
                    new RebuildSequence.Options(axis, null, 0L));
            RebuildModel model = HOUSE.model();
            for (int step = 1; step < sequence.size(); step++) {
                int before = coordinate(model, sequence.entry(step - 1), axis.axis());
                int now = coordinate(model, sequence.entry(step), axis.axis());
                assertTrue(axis.negative() ? now <= before : now >= before, axis.id() + " paso " + step);
                assertEquals(now, sequence.group(step), "el grupo es la rodaja");
            }
        }
    }

    private static int coordinate(RebuildModel model, int entry, int axis) {
        return axis == 0 ? model.x(entry) : axis == 1 ? model.y(entry) : model.z(entry);
    }

    @Test
    void wavesGrowFromTheCenter() {
        int[] center = {7, 2, 7};
        RebuildSequence sequence = sequence(HOUSE, RebuildOrder.WAVES, new RebuildSequence.Options(null, center, 0L));
        RebuildModel model = HOUSE.model();
        long previous = -1;
        for (int step = 0; step < sequence.size(); step++) {
            int entry = sequence.entry(step);
            long dx = model.x(entry) - center[0];
            long dy = model.y(entry) - center[1];
            long dz = model.z(entry) - center[2];
            long distance = dx * dx + dy * dy + dz * dz;
            assertTrue(distance >= previous, "la onda no vuelve hacia dentro en el paso " + step);
            assertEquals((int) Math.floor(Math.sqrt(distance)), sequence.group(step));
            previous = distance;
        }
        // Sin centro: el de la base de la obra.
        RebuildSequence fromBase = sequence(HOUSE, RebuildOrder.WAVES, RebuildSequence.Options.DEFAULT);
        int first = fromBase.entry(0);
        assertEquals(model.minY(), model.y(first));
        assertEquals(Math.floorDiv(model.minX() + model.maxX(), 2), model.x(first));
        assertEquals(Math.floorDiv(model.minZ() + model.maxZ(), 2), model.z(first));
    }

    @Test
    void randomIsTheSameWithTheSameSeedAndDifferentWithAnother() {
        RebuildSequence first = sequence(HOUSE, RebuildOrder.RANDOM, new RebuildSequence.Options(null, null, 7L));
        RebuildSequence again = sequence(HOUSE, RebuildOrder.RANDOM, new RebuildSequence.Options(null, null, 7L));
        RebuildSequence other = sequence(HOUSE, RebuildOrder.RANDOM, new RebuildSequence.Options(null, null, 8L));
        boolean differs = false;
        boolean shuffled = false;
        for (int step = 0; step < first.size(); step++) {
            assertEquals(first.entry(step), again.entry(step));
            differs |= first.entry(step) != other.entry(step);
            shuffled |= step > 0 && first.entry(step) < first.entry(step - 1);
        }
        assertTrue(differs, "otra semilla, otro orden");
        assertTrue(shuffled, "no es el orden de lectura");
        assertFalse(first.startsGroup(10), "al azar no hay capas");
    }

    @Test
    void theBuilderGoesFromFoundationsToPlants() {
        RebuildSequence sequence = sequence(RebuildOrder.BUILDER);
        RebuildModel model = HOUSE.model();
        BuildPhase[] phases = BuilderPhases.classify(model);
        int previous = -1;
        for (int step = 0; step < sequence.size(); step++) {
            int phase = phases[sequence.entry(step)].ordinal();
            int last = previous;
            assertTrue(phase >= previous, () -> "una fase anterior después de " + BuildPhase.values()[last]);
            assertEquals(phase, sequence.group(step));
            previous = phase;
        }
        Map<String, BuildPhase> expected = new HashMap<>();
        expected.put("minecraft:grass_block", BuildPhase.FOUNDATION);
        expected.put("minecraft:cobblestone@0", BuildPhase.FOUNDATION);
        expected.put("minecraft:oak_log[axis=y]", BuildPhase.STRUCTURE);
        expected.put("minecraft:stripped_spruce_log[axis=x]", BuildPhase.STRUCTURE);
        expected.put("minecraft:oak_planks", BuildPhase.WALLS);
        expected.put("minecraft:glass_pane[east=true,west=true]", BuildPhase.WALLS);
        expected.put("minecraft:cobblestone@5", BuildPhase.WALLS);
        expected.put("minecraft:spruce_stairs[facing=south]", BuildPhase.ROOF);
        expected.put("minecraft:spruce_slab[type=bottom]", BuildPhase.ROOF);
        expected.put("minecraft:oak_door[facing=south,half=lower]", BuildPhase.DETAILS);
        expected.put("minecraft:wall_torch[facing=south]", BuildPhase.DETAILS);
        expected.put("minecraft:oak_fence", BuildPhase.DETAILS);
        expected.put("minecraft:chest[facing=south]", BuildPhase.DETAILS);
        expected.put("minecraft:red_carpet", BuildPhase.DETAILS);
        expected.put("minecraft:poppy", BuildPhase.VEGETATION);
        expected.put("minecraft:oak_leaves[persistent=true]", BuildPhase.VEGETATION);
        Map<String, BuildPhase> found = new HashMap<>();
        for (int entry = 0; entry < model.size(); entry++) {
            String state = HOUSE.state(entry);
            if (state.equals("minecraft:cobblestone")) {
                state += model.y(entry) == 0 ? "@0" : model.y(entry) == 5 ? "@5" : "@other";
            }
            BuildPhase phase = phases[entry];
            BuildPhase seen = found.putIfAbsent(state, phase);
            if (expected.containsKey(state)) {
                assertEquals(expected.get(state), phase, state + " en " + model.x(entry) + "," + model.y(entry)
                        + "," + model.z(entry));
            } else if (seen != null && !state.endsWith("@other")) {
                assertEquals(seen, phase, state + " cae en dos fases");
            }
        }
        assertTrue(found.keySet().containsAll(expected.keySet()), "faltan bloques en la casa: " + expected.keySet());
        assertEquals("cimientos", sequence.groupLabel(0));
        assertEquals("vegetación", sequence.groupLabel(sequence.size() - 1));
    }

    @Test
    void stairsUnderARoofAreDetailsAndTheFirstLayerIsAlwaysFoundation() {
        RebuildFixtures.Build build = new RebuildFixtures.Build()
                .fill(0, 0, 0, 4, 0, 4, "stone_bricks")
                .fill(0, 1, 0, 0, 3, 0, "oak_log")
                .set(2, 1, 2, "oak_stairs[facing=east]")
                .fill(0, 4, 0, 4, 4, 4, "oak_planks")
                .set(2, 5, 2, "oak_stairs[facing=west]");
        RebuildFixtures.Fixture fixture = RebuildFixtures.selection(build);
        BuildPhase[] phases = BuilderPhases.classify(fixture.model());
        for (int entry = 0; entry < fixture.model().size(); entry++) {
            int y = fixture.model().y(entry);
            String state = fixture.state(entry);
            BuildPhase expected = y == 0 ? BuildPhase.FOUNDATION
                    : state.contains("log") ? BuildPhase.STRUCTURE
                    : state.contains("planks") ? BuildPhase.WALLS
                    : y == 1 ? BuildPhase.DETAILS : BuildPhase.ROOF;
            assertEquals(expected, phases[entry], state + " y=" + y);
        }
        // Unos postes sueltos no son un suelo, pero la primera capa sigue siendo cimientos.
        RebuildFixtures.Fixture posts = RebuildFixtures.selection(new RebuildFixtures.Build()
                .set(0, 0, 0, "stone").set(5, 0, 5, "stone").fill(0, 1, 0, 5, 1, 5, "oak_planks"));
        assertEquals(0, BuilderPhases.groundLevel(posts.model()));
    }

    @Test
    void historyFollowsThePlacementOrderAfterWhatWasAlreadyThere() {
        RebuildFixtures.Build build = new RebuildFixtures.Build().fill(0, 0, 0, 4, 2, 0, "stone");
        Map<Long, Long> history = new HashMap<>();
        // Lo apuntado: de arriba abajo y de derecha a izquierda, al revés de las capas.
        long rank = 100;
        for (int y = 2; y >= 1; y--) {
            for (int x = 4; x >= 0; x--) {
                history.put(PackedBlockPlan.pack(x, y, 0), rank++);
            }
        }
        RebuildFixtures.Fixture fixture = RebuildFixtures.selection(build, history);
        RebuildSequence sequence = sequence(fixture, RebuildOrder.HISTORY, RebuildSequence.Options.DEFAULT);
        assertEquals(RebuildOrder.HISTORY, sequence.order());
        assertFalse(sequence.fellBack());
        RebuildModel model = fixture.model();
        for (int step = 0; step < 5; step++) {
            assertEquals(0, model.y(sequence.entry(step)), "primero lo de antes del historial (la capa 0)");
            assertEquals(0, sequence.group(step));
        }
        for (int step = 5; step < sequence.size(); step++) {
            assertEquals(100 + step - 5, model.history(sequence.entry(step)), "después, en el orden apuntado");
            assertEquals(1, sequence.group(step));
        }
        assertEquals("historial", sequence.groupLabel(5));
    }

    @Test
    void withoutHistoryItFallsBackToLayers() {
        RebuildSequence sequence = sequence(RebuildOrder.HISTORY);
        assertTrue(sequence.fellBack());
        assertEquals(RebuildOrder.LAYERS, sequence.order());
        RebuildSequence layers = sequence(RebuildOrder.LAYERS);
        for (int step = 0; step < sequence.size(); step++) {
            assertEquals(layers.entry(step), sequence.entry(step));
        }
    }
}
