package com.komainu.moxia.performance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reparto por turnos de un tick entre jugadores, con un reloj de mentira. */
class FairShareTest {
    private static final long MS = 1_000_000L;

    /** Reloj que solo avanza cuando el trabajo gasta. */
    private static final class Clock {
        private long now;

        long now() {
            return now;
        }

        void spend(long nanos) {
            now += nanos;
        }
    }

    /** Trabajo de mentira: gasta {@code cost} por unidad hasta el plazo o hasta acabar. */
    private static final class Job {
        private final Clock clock;
        private final long cost;
        private long left;

        Job(Clock clock, long units, long cost) {
            this.clock = clock;
            this.left = units;
            this.cost = cost;
        }

        boolean step(long deadline) {
            while (left > 0 && clock.now() + cost <= deadline) {
                clock.spend(cost);
                left--;
            }
            return left > 0;
        }
    }

    @Test
    void equalJobsGetEqualShares() {
        Clock clock = new Clock();
        Map<String, Job> jobs = new HashMap<>();
        for (String owner : List.of("ana", "bea", "carla")) {
            jobs.put(owner, new Job(clock, 1_000_000, 10_000L));
        }
        Map<String, Long> used = FairShare.run(List.of("ana", "bea", "carla"), 0, 6 * MS, clock::now,
                (owner, deadline) -> jobs.get(owner).step(deadline));
        assertTrue(clock.now() <= 6 * MS);
        for (long nanos : used.values()) {
            assertEquals(2 * MS, nanos, 20_000L);
        }
    }

    @Test
    void aSmallEditFinishesWhileAHugeOneKeepsGoing() {
        Clock clock = new Clock();
        Job huge = new Job(clock, 50_000_000, 5_000L);
        Job small = new Job(clock, 1_500, 5_000L);
        Map<String, Job> jobs = Map.of("grande", huge, "pequeña", small);
        List<String> owners = new ArrayList<>(List.of("grande", "pequeña"));
        int ticks = 0;
        long start = clock.now();
        while (small.left > 0) {
            long tickStart = clock.now();
            FairShare.run(owners, ticks, tickStart + 6 * MS, clock::now,
                    (owner, deadline) -> jobs.get(owner).step(deadline));
            assertTrue(clock.now() - tickStart <= 6 * MS, "nunca pasa del presupuesto");
            clock.now = tickStart + 50 * MS;
            ticks++;
        }
        // 1 500 unidades a 5 µs son 7,5 ms: con la mitad del tick cada una, 3 ticks.
        assertTrue(ticks <= 3, "la pequeña no espera a la grande: " + ticks + " ticks");
        assertTrue(huge.left < 50_000_000, "y la grande también avanza");
        assertTrue(clock.now() - start > 0);
    }

    @Test
    void timeAJobDoesNotUseGoesToTheOthers() {
        Clock clock = new Clock();
        Job idle = new Job(clock, 10, 1_000L);
        Job busy = new Job(clock, 1_000_000, 1_000L);
        Map<String, Job> jobs = Map.of("idle", idle, "busy", busy);
        Map<String, Long> used = FairShare.run(List.of("idle", "busy"), 0, 6 * MS, clock::now,
                (owner, deadline) -> jobs.get(owner).step(deadline));
        assertEquals(10_000L, used.get("idle"));
        assertEquals(6 * MS - 10_000L, used.get("busy"), 2_000L);
    }

    @Test
    void theFirstTurnRotatesEachTick() {
        Clock clock = new Clock();
        List<String> first = new ArrayList<>();
        for (int tick = 0; tick < 3; tick++) {
            List<String> order = new ArrayList<>();
            FairShare.run(List.of("a", "b", "c"), tick, clock.now() + MS, clock::now, (owner, deadline) -> {
                order.add(owner);
                clock.spend(deadline - clock.now());
                return true;
            });
            first.add(order.getFirst());
        }
        assertEquals(List.of("a", "b", "c"), first);
    }

    @Test
    void workThatWaitsOnAnotherThreadIsNotSpunOn() {
        Clock clock = new Clock();
        int[] calls = {0};
        FairShare.run(List.of("esperando"), 0, 6 * MS, clock::now, (owner, deadline) -> {
            calls[0]++;
            return true;
        });
        assertEquals(1, calls[0], "dice que le queda pero no gasta nada: una sola vuelta");
    }
}
