package com.komainu.moxia.display;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El historial de displays con un mundo de mentira: cada display es su UUID y un texto que hace de foto (en el juego,
 * su NBT). Es lo que corre dentro de {@code //undo} y {@code //redo}.
 */
class DisplayHistoryTest {
    /** El mundo: UUID → foto. La foto lleva el UUID delante, como el NBT de Minecraft. */
    private static final class FakeWorld implements DisplayHistory.World<String> {
        private final Map<UUID, String> displays = new LinkedHashMap<>();

        @Override
        public boolean exists(UUID id) {
            return displays.containsKey(id);
        }

        @Override
        public boolean spawn(String state) {
            UUID id = UUID.fromString(state.substring(0, 36));
            if (displays.containsKey(id)) {
                return false;
            }
            displays.put(id, state);
            return true;
        }

        @Override
        public boolean remove(UUID id) {
            return displays.remove(id) != null;
        }

        @Override
        public boolean apply(UUID id, String state) {
            if (!displays.containsKey(id)) {
                return false;
            }
            displays.put(id, state);
            return true;
        }
    }

    private static final UUID POT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SIGN = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static String state(UUID id, String pose) {
        return id + " " + pose;
    }

    @Test
    void undoingACreationRemovesItAndRedoBringsItBack() {
        FakeWorld world = new FakeWorld();
        world.spawn(state(POT, "en la mesa"));
        DisplayHistory<String> history = new DisplayHistory<String>().created(POT, state(POT, "en la mesa"));
        assertEquals(new DisplayHistory.Outcome(1, 0), history.undo(world));
        assertFalse(world.exists(POT));
        assertEquals(new DisplayHistory.Outcome(1, 0), history.redo(world));
        assertEquals(state(POT, "en la mesa"), world.displays.get(POT));
    }

    @Test
    void undoingADeletionBringsTheSameDisplayBack() {
        FakeWorld world = new FakeWorld();
        String before = state(SIGN, "en la pared");
        DisplayHistory<String> history = new DisplayHistory<String>().deleted(SIGN, before);
        history.undo(world);
        assertEquals(before, world.displays.get(SIGN), "con su UUID de siempre");
        assertEquals(new DisplayHistory.Outcome(0, 1), history.undo(world), "dos veces no lo duplica");
        assertEquals(1, world.displays.size());
        history.redo(world);
        assertFalse(world.exists(SIGN));
    }

    @Test
    void undoingAMoveRestoresEveryDisplayInReverseOrder() {
        FakeWorld world = new FakeWorld();
        world.spawn(state(POT, "x=1"));
        world.spawn(state(SIGN, "x=2"));
        DisplayHistory<String> move = new DisplayHistory<String>()
                .changed(POT, state(POT, "x=1"), state(POT, "x=1.0625"))
                .changed(SIGN, state(SIGN, "x=2"), state(SIGN, "x=2.0625"));
        world.apply(POT, state(POT, "x=1.0625"));
        world.apply(SIGN, state(SIGN, "x=2.0625"));
        assertEquals(new DisplayHistory.Outcome(2, 0), move.undo(world));
        assertEquals(state(POT, "x=1"), world.displays.get(POT));
        assertEquals(state(SIGN, "x=2"), world.displays.get(SIGN));
        move.redo(world);
        assertEquals(state(POT, "x=1.0625"), world.displays.get(POT));
    }

    @Test
    void aSequenceOfEditsUndoesStepByStep() {
        FakeWorld world = new FakeWorld();
        DisplayHistory<String> create = new DisplayHistory<String>().created(POT, state(POT, "a"));
        world.spawn(state(POT, "a"));
        DisplayHistory<String> move = new DisplayHistory<String>().changed(POT, state(POT, "a"), state(POT, "b"));
        world.apply(POT, state(POT, "b"));
        DisplayHistory<String> delete = new DisplayHistory<String>().deleted(POT, state(POT, "b"));
        world.remove(POT);
        // //undo tres veces, de la última a la primera.
        delete.undo(world);
        assertEquals(state(POT, "b"), world.displays.get(POT));
        move.undo(world);
        assertEquals(state(POT, "a"), world.displays.get(POT));
        create.undo(world);
        assertFalse(world.exists(POT));
        // Y //redo tres veces.
        create.redo(world);
        move.redo(world);
        delete.redo(world);
        assertFalse(world.exists(POT));
    }

    @Test
    void aDisplayKilledByOtherMeansIsNotRevived() {
        FakeWorld world = new FakeWorld();
        DisplayHistory<String> move = new DisplayHistory<String>().changed(POT, state(POT, "a"), state(POT, "b"));
        assertEquals(new DisplayHistory.Outcome(0, 1), move.undo(world), "lo quitó /kill: no vuelve");
        assertTrue(world.displays.isEmpty());
    }

    @Test
    void aRecordNeedsSomething() {
        assertThrows(IllegalArgumentException.class, () -> new DisplayHistory.Record<String>(POT, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DisplayHistory.Record<>(null, "a", "b"));
        DisplayHistory.Record<String> created = new DisplayHistory.Record<>(POT, null, "a");
        assertTrue(created.created());
        assertFalse(created.deleted());
    }
}
