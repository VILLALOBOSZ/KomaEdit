package com.komainu.moxia.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El presupuesto por tick con un reloj de mentira: lleno con el servidor holgado, menos si va justo o atrasado. */
class TickBudgetTest {
    private static final long MS = 1_000_000L;
    private static final TickBudget.Settings SETTINGS = new TickBudget.Settings(6 * MS, MS);

    /** Simula {@code ticks} ticks de {@code duration} ms (de los que KomaEdit gasta {@code own}) cada {@code every}. */
    private static long run(TickBudget budget, long start, int ticks, long duration, long own, long every) {
        long now = start;
        for (int tick = 0; tick < ticks; tick++) {
            budget.startTick(now);
            budget.spent(own);
            budget.endTick(now + duration);
            now += Math.max(every, duration);
        }
        return now;
    }

    @Test
    void withoutMeasurementsItGivesTheConfiguredBudget() {
        assertEquals(6 * MS, new TickBudget(SETTINGS).allowance());
    }

    @Test
    void aRelaxedServerGetsTheWholeBudget() {
        TickBudget budget = new TickBudget(SETTINGS);
        long now = run(budget, 0L, 60, 12 * MS, 4 * MS, 50 * MS);
        budget.startTick(now);
        assertEquals(6 * MS, budget.allowance());
        assertEquals(20.0, budget.tps(), 0.01);
        assertEquals(12.0, budget.mspt(), 0.01);
        assertEquals(4.0, budget.ownMillis(), 0.01);
    }

    @Test
    void aBusyServerOnlyGivesWhatIsLeftUnder45Milliseconds() {
        TickBudget budget = new TickBudget(SETTINGS);
        // El servidor solo ya gasta 41 ms: quedan 4 hasta 45.
        long now = run(budget, 0L, 80, 43 * MS, 2 * MS, 50 * MS);
        budget.startTick(now);
        assertEquals(4 * MS, budget.allowance(), MS / 10);
        // Con 45 ms propios del servidor, solo el mínimo.
        now = run(budget, now, 80, 47 * MS, 2 * MS, 50 * MS);
        budget.startTick(now);
        assertEquals(MS, budget.allowance());
    }

    @Test
    void belowTwentyTpsItDropsToTheMinimumAndRecovers() {
        TickBudget budget = new TickBudget(SETTINGS);
        // Ticks de 60 ms: 16,7 TPS.
        long now = run(budget, 0L, 60, 60 * MS, MS, 60 * MS);
        budget.startTick(now);
        assertEquals(MS, budget.allowance());
        assertTrue(budget.tps() < 17.0);
        budget.endTick(now + 20 * MS);
        // Vuelve a ir bien: en unos segundos el presupuesto vuelve entero.
        now = run(budget, now + 50 * MS, 100, 15 * MS, 2 * MS, 50 * MS);
        budget.startTick(now);
        assertEquals(6 * MS, budget.allowance());
    }

    @Test
    void settingsMustLeaveSomethingEachTick() {
        assertThrows(IllegalArgumentException.class, () -> new TickBudget.Settings(MS, 0L));
        assertThrows(IllegalArgumentException.class, () -> new TickBudget.Settings(MS, 2 * MS));
        assertEquals(new TickBudget.Settings(2_500_000L, 500_000L), TickBudget.Settings.ofMillis(2.5, 0.5));
    }
}
