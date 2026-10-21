package com.komainu.moxia.performance;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El freno de las peticiones caras que llegan por paquete: un cliente modificado no las puede pedir sin parar. */
class RequestBudgetTest {
    private static final long SECOND = 1_000_000_000L;
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);

    @Test
    void aFloodOfRequestsInTheSameInstantOnlyGetsTheBurst() {
        RequestBudget budget = new RequestBudget(8, 2.0);
        int allowed = 0;
        for (int packet = 0; packet < 10_000; packet++) {
            if (budget.allow(ALICE, 5 * SECOND)) {
                allowed++;
            }
        }
        assertEquals(8, allowed);
    }

    @Test
    void theBudgetRefillsWithTimeButNeverAboveTheBurst() {
        RequestBudget budget = new RequestBudget(4, 2.0);
        long now = 0L;
        for (int request = 0; request < 4; request++) {
            assertTrue(budget.allow(ALICE, now));
        }
        assertFalse(budget.allow(ALICE, now));
        now += SECOND / 2;
        assertTrue(budget.allow(ALICE, now), "medio segundo da para una");
        assertFalse(budget.allow(ALICE, now));
        now += 60 * SECOND;
        int allowed = 0;
        while (budget.allow(ALICE, now)) {
            allowed++;
        }
        assertEquals(4, allowed, "tras un rato parado vuelve a tener la ráfaga, no más");
    }

    @Test
    void aNormalPaceIsNeverStopped() {
        RequestBudget budget = new RequestBudget(8, 2.0);
        for (int click = 0; click < 1_000; click++) {
            assertTrue(budget.allow(ALICE, click * (SECOND / 2)), "un clic cada medio segundo");
        }
    }

    @Test
    void eachPlayerHasItsOwnBudgetAndForgettingResetsIt() {
        RequestBudget budget = new RequestBudget(1, 1.0);
        assertTrue(budget.allow(ALICE, 0L));
        assertFalse(budget.allow(ALICE, 0L));
        assertTrue(budget.allow(BOB, 0L), "lo de Alice no frena a Bob");
        budget.forget(ALICE);
        assertTrue(budget.allow(ALICE, 0L));
        assertThrows(IllegalArgumentException.class, () -> new RequestBudget(0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new RequestBudget(1, Double.NaN));
    }
}
