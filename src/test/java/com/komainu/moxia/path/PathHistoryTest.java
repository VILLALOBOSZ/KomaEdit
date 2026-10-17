package com.komainu.moxia.path;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathHistoryTest {
    private static final PathHistory.State EMPTY = new PathHistory.State(List.of(), false);
    private static final PathHistory.State ONE = new PathHistory.State(List.of(BlockVector3.at(1, 2, 3)), false);
    private static final PathHistory.State TWO = new PathHistory.State(
            List.of(BlockVector3.at(1, 2, 3), BlockVector3.at(4, 5, 6)), false);

    @Test
    void undoesAndRedoesInOrder() {
        PathHistory history = new PathHistory();
        history.record(EMPTY);
        history.record(ONE);
        assertEquals(ONE, history.undo(TWO).orElseThrow());
        assertEquals(EMPTY, history.undo(ONE).orElseThrow());
        assertTrue(history.undo(EMPTY).isEmpty());
        assertEquals(ONE, history.redo(EMPTY).orElseThrow());
        assertEquals(TWO, history.redo(ONE).orElseThrow());
        assertTrue(history.redo(TWO).isEmpty());
    }

    @Test
    void aNewChangeForgetsTheRedoBranch() {
        PathHistory history = new PathHistory();
        history.record(EMPTY);
        history.undo(ONE);
        assertEquals(1, history.redoSteps());
        history.record(EMPTY);
        assertEquals(0, history.redoSteps());
    }

    @Test
    void draggingTheSamePointIsOneStep() {
        PathHistory history = new PathHistory();
        long start = 1_000_000_000L;
        history.recordMove(ONE, 0, start);
        history.recordMove(TWO, 0, start + 100_000_000L);
        history.recordMove(TWO, 0, start + 200_000_000L);
        assertEquals(1, history.undoSteps());
        // Otro punto, o el mismo tras una pausa, abre un paso nuevo.
        history.recordMove(TWO, 1, start + 300_000_000L);
        history.recordMove(ONE, 1, start + 5_000_000_000L);
        assertEquals(3, history.undoSteps());
    }

    @Test
    void keepsAtMostTheConfiguredSteps() {
        PathHistory history = new PathHistory();
        for (int step = 0; step < PathHistory.MAX_STEPS + 10; step++) {
            history.record(new PathHistory.State(List.of(BlockVector3.at(step, 0, 0)), false));
        }
        assertEquals(PathHistory.MAX_STEPS, history.undoSteps());
    }
}
