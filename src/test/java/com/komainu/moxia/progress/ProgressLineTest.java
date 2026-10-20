package com.komainu.moxia.progress;

import com.komainu.moxia.edit.BuildEstimate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressLineTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void measuredPhaseShowsPercentRateAndRemainingTime() {
        ProgressLine line = new ProgressLine("Texturizar", ProgressLine.Phase.PLACING, 45_000, 100_000, 3 * SECOND,
                "/komaedit cancel");
        assertEquals(0.45f, line.fraction(), 1e-6);
        assertEquals(15_000.0, line.rate(), 1e-6);
        assertEquals(55_000 / 15_000.0, line.remainingSeconds(), 1e-6);
        assertEquals("Texturizar · colocando 45 % · 15 000 bl/s · ~4 s · /komaedit cancel", line.text());
    }

    @Test
    void unmeasuredPhaseCountsTime() {
        ProgressLine line = new ProgressLine(null, ProgressLine.Phase.CALCULATING, 0, 0, 75 * SECOND, null);
        assertFalse(line.measured());
        assertEquals(0.0f, line.fraction());
        assertEquals(-1.0, line.remainingSeconds());
        assertEquals("Operación · calculando · 1 min 15 s", line.text());
    }

    @Test
    void waitingPreviewShowsItsCountdown() {
        ProgressLine line = new ProgressLine("Suavizar", ProgressLine.Phase.WAITING, 30_000, 90_000, 0,
                "1 200 cambios · /komaedit confirm · caduca en 30 s");
        assertEquals(1.0f / 3.0f, line.fraction(), 1e-6);
        assertEquals("Suavizar · vista previa · 1 200 cambios · /komaedit confirm · caduca en 30 s", line.text());
    }

    @Test
    void justStartedHasNoRateYet() {
        ProgressLine line = new ProgressLine("Leer", ProgressLine.Phase.READING, 10, 1_000, 10_000_000L, null);
        assertEquals(0.0, line.rate());
        assertEquals("Leer · leyendo 1 %", line.text());
        assertEquals(1.0f, new ProgressLine("x", ProgressLine.Phase.CHECKING, 5, 2, 0, null).fraction());
    }

    @Test
    void estimatesBeforeStartingAndWorldEditLimit() {
        assertEquals(0.0, BuildEstimate.placeSeconds(0));
        assertEquals(0.05, BuildEstimate.placeSeconds(1), 1e-9);
        assertEquals(20.0, BuildEstimate.placeSeconds(BuildEstimate.BLOCKS_PER_TICK * 400L), 1e-9);
        assertEquals(1.0, BuildEstimate.readSeconds(BuildEstimate.READ_BLOCKS_PER_TICK * 20L), 1e-9);
        assertTrue(BuildEstimate.limitWarning(1_000, -1).isEmpty(), "sin //limit no avisa");
        assertTrue(BuildEstimate.limitWarning(1_000, 1_000).isEmpty());
        String warning = BuildEstimate.limitWarning(1_001, 1_000).orElseThrow();
        assertTrue(warning.contains("1 000") && warning.contains("//undo"), warning);
        assertEquals(BuildEstimate.placeSeconds(250_000), BuildEstimate.of(250_000, 0, 1, null).seconds());
    }
}
