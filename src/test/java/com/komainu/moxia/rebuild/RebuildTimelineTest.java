package com.komainu.moxia.rebuild;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cuándo le toca a cada bloque: bloques por tick o duración total, pausas entre capas y retrasos de Axiom. */
class RebuildTimelineTest {
    private static final long TICK = RebuildTimeline.UNIT;

    /** Una fila de bloques en x, en el orden de las capas (de x 0 en adelante). */
    private static RebuildFixtures.Fixture row(String... states) {
        RebuildFixtures.Build build = new RebuildFixtures.Build();
        for (int x = 0; x < states.length; x++) {
            build.set(x, 0, 0, states[x]);
        }
        return RebuildFixtures.selection(build);
    }

    private static RebuildTimeline timeline(RebuildFixtures.Fixture fixture, RebuildPace pace) {
        return RebuildTimeline.of(fixture.model(), RebuildSequence.of(fixture.model(), RebuildOrder.LAYERS,
                RebuildSequence.Options.DEFAULT), pace);
    }

    private static List<Long> dues(RebuildTimeline timeline) {
        List<Long> dues = new ArrayList<>();
        for (int step = 0; step < timeline.size(); step++) {
            dues.add(timeline.due(step));
        }
        return dues;
    }

    private static String[] stones(int count) {
        String[] states = new String[count];
        java.util.Arrays.fill(states, "stone");
        return states;
    }

    private static final RebuildPace PLAIN = RebuildPace.DEFAULT.withLeadIn(0);

    @Test
    void blocksPerTickSpaceTheBlocksEvenly() {
        RebuildTimeline four = timeline(row(stones(9)), PLAIN.withBlocksPerTick(4));
        assertEquals(List.of(0L, 250L, 500L, 750L, 1000L, 1250L, 1500L, 1750L, 2000L), dues(four));
        RebuildTimeline slow = timeline(row(stones(3)), PLAIN.withBlocksPerTick(0.25));
        assertEquals(List.of(0L, 4 * TICK, 8 * TICK), dues(slow), "un bloque cada cuatro ticks");
        RebuildTimeline waiting = timeline(row(stones(2)), PLAIN.withBlocksPerTick(1).withLeadIn(30));
        assertEquals(List.of(30 * TICK, 31 * TICK), dues(waiting), "la espera inicial va antes del primero");
    }

    @Test
    void aTotalDurationEndsExactlyOnTime() {
        RebuildTimeline even = timeline(row(stones(21)), PLAIN.withDuration(100));
        assertEquals(100 * TICK, even.end());
        assertEquals(5 * TICK, even.due(1), "100 ticks entre 20 huecos: uno cada 5");
        RebuildTimeline withWait = timeline(row(stones(21)), PLAIN.withDuration(100).withLeadIn(20));
        assertEquals(20 * TICK, withWait.due(0));
        assertEquals(100 * TICK, withWait.end());
        // Si las pausas no caben, todo se encoge en proporción y sigue acabando a tiempo.
        RebuildFixtures.Fixture layers = RebuildFixtures.selection(new RebuildFixtures.Build()
                .fill(0, 0, 0, 1, 3, 0, "stone"));
        RebuildTimeline squeezed = timeline(layers, PLAIN.withDuration(10).withLeadIn(20).withLayerPause(20));
        assertEquals(10 * TICK, squeezed.end());
        RebuildTimeline single = timeline(row("stone"), PLAIN.withDuration(10).withLeadIn(40));
        assertEquals(10 * TICK, single.end(), "un solo bloque: la espera se encoge a la duración");
    }

    @Test
    void thePauseGoesAtTheStartOfEachLayer() {
        RebuildFixtures.Fixture layers = RebuildFixtures.selection(new RebuildFixtures.Build()
                .fill(0, 0, 0, 2, 1, 0, "stone"));
        RebuildTimeline paused = timeline(layers, PLAIN.withBlocksPerTick(1).withLayerPause(10));
        assertEquals(List.of(0L, TICK, 2 * TICK, 13 * TICK, 14 * TICK, 15 * TICK), dues(paused));
    }

    @Test
    void sameAndDifferentBlocksAddTheirOwnDelay() {
        RebuildFixtures.Fixture blocks = row("stone", "stone", "dirt", "dirt", "stone");
        RebuildTimeline delayed = timeline(blocks, PLAIN.withBlocksPerTick(1).withDelays(2, 5));
        assertEquals(List.of(0L, 3 * TICK, 9 * TICK, 12 * TICK, 18 * TICK), dues(delayed));
        // El mismo bloque girado o con otras propiedades sigue siendo «igual».
        RebuildFixtures.Fixture stairs = row("oak_stairs[facing=north]", "oak_stairs[facing=east]", "oak_planks");
        assertEquals(List.of(0L, 3 * TICK, 9 * TICK),
                dues(timeline(stairs, PLAIN.withBlocksPerTick(1).withDelays(2, 5))));
    }

    @Test
    void dueUntilCountsTheStepsThatAlreadyFit() {
        RebuildTimeline four = timeline(row(stones(9)), PLAIN.withBlocksPerTick(4));
        assertEquals(1, four.dueUntil(0, 0));
        assertEquals(5, four.dueUntil(0, TICK));
        assertEquals(5, four.dueUntil(3, TICK));
        assertEquals(7, four.dueUntil(5, 1_500));
        assertEquals(9, four.dueUntil(0, 99 * TICK));
        assertEquals(9, four.dueUntil(9, 99 * TICK));
        assertEquals(4, four.dueUntil(4, -1), "antes de empezar, nada");
    }

    @Test
    void thePaceRejectsWhatCannotWork() {
        assertThrows(IllegalArgumentException.class, () -> RebuildPace.DEFAULT.withBlocksPerTick(0));
        assertThrows(IllegalArgumentException.class, () -> RebuildPace.DEFAULT.withBlocksPerTick(10_000));
        assertThrows(IllegalArgumentException.class, () -> RebuildPace.DEFAULT.withLayerPause(-1));
        assertThrows(IllegalArgumentException.class, () -> RebuildPace.DEFAULT.withDuration(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> RebuildPace.DEFAULT.withLeadIn(RebuildPace.MAX_TICKS + 1));
        assertTrue(RebuildPace.DEFAULT.withBlocksPerTick(RebuildPace.MIN_BLOCKS_PER_TICK).blocksPerTick() > 0);
    }
}
