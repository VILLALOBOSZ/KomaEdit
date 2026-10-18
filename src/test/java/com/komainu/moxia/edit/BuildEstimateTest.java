package com.komainu.moxia.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildEstimateTest {
    @Test
    void countsChunksOnBothSidesOfZero() {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(4);
        builder.addPattern(-1, 64, -1);
        builder.addPattern(0, 64, 0);
        builder.addPattern(15, 70, 15);
        builder.addPattern(16, 64, 0);
        BuildEstimate estimate = BuildEstimate.of(builder.build());
        assertEquals(4, estimate.blocks());
        assertEquals(3, estimate.chunks());
        assertEquals(18, estimate.sizeX());
        assertEquals(7, estimate.sizeY());
        assertEquals(17, estimate.sizeZ());
    }

    @Test
    void estimatesTimeFromTheQueueRate() {
        BuildEstimate estimate = BuildEstimate.of(BuildEstimate.BLOCKS_PER_TICK * 20 * 90, 0, 120, null);
        assertEquals(90.0, estimate.seconds(), 1.0E-9);
        assertEquals("1 min 30 s", BuildEstimate.duration(estimate.seconds()));
        assertEquals("1 s", BuildEstimate.duration(0.2));
    }

    @Test
    void summaryReadsNaturally() {
        BuildEstimate estimate = BuildEstimate.of(1_234_567, 890, 42,
                new BlockBounds(0, 60, 0, 59, 83, 87));
        assertEquals("1 234 567 bloques (890 piezas) · 42 chunks · caja 60×24×88 · ~47 MB · ~21 s",
                estimate.summary());
    }

    @Test
    void asksForConfirmationOnlyAboveAPositiveThreshold() {
        assertTrue(BuildEstimate.needsConfirmation(250_001, 250_000));
        assertFalse(BuildEstimate.needsConfirmation(250_000, 250_000));
        assertFalse(BuildEstimate.needsConfirmation(9_000_000, 0));
    }
}
