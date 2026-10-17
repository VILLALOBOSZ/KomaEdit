package com.komainu.moxia.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterHistoryTest {
    @Test
    void undoesAndRedoesInOrder() {
        ParameterHistory<Integer> history = new ParameterHistory<>(10);
        history.record(1, "a", 0);
        history.record(2, "b", 10_000);
        assertEquals(2, history.undo(3));
        assertEquals(1, history.undo(2));
        assertNull(history.undo(1));
        assertEquals(2, history.redo(1));
        assertEquals(3, history.redo(2));
        assertNull(history.redo(3));
    }

    @Test
    void coalescesQuickChangesOfTheSameControl() {
        ParameterHistory<Integer> history = new ParameterHistory<>(10);
        history.record(5, "radius", 1_000);
        history.record(6, "radius", 1_300);
        history.record(7, "radius", 1_900);
        assertEquals(1, history.undoSize());
        assertEquals(5, history.undo(8));
    }

    @Test
    void doesNotCoalesceOtherControlsSlowChangesOrUngroupedOnes() {
        ParameterHistory<Integer> history = new ParameterHistory<>(10);
        history.record(1, "radius", 0);
        history.record(2, "spacing", 100);
        history.record(3, "spacing", 100 + ParameterHistory.COALESCE_MILLIS + 1);
        history.record(4, null, 2_000);
        history.record(5, null, 2_001);
        assertEquals(5, history.undoSize());
    }

    @Test
    void newChangeClearsRedoAndUndoBreaksCoalescing() {
        ParameterHistory<Integer> history = new ParameterHistory<>(10);
        history.record(1, "radius", 0);
        assertEquals(1, history.undo(2));
        assertTrue(history.canRedo());
        history.record(1, "radius", 10);
        assertFalse(history.canRedo());
        history.record(2, "radius", 20);
        assertEquals(1, history.undoSize());
    }

    @Test
    void dropsTheOldestBeyondCapacity() {
        ParameterHistory<Integer> history = new ParameterHistory<>(3);
        for (int value = 0; value < 5; value++) {
            history.record(value, null, value);
        }
        assertEquals(3, history.undoSize());
        assertEquals(4, history.undo(5));
        assertEquals(3, history.undo(4));
        assertEquals(2, history.undo(3));
        assertNull(history.undo(2));
    }
}
