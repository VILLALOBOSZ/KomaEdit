package com.komainu.moxia.performance;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La cola de colocación: sitios, puestos, pausa, cancelación y cuándo empieza cada una. */
class JobQueueTest {
    private final Set<String> paused = new HashSet<>();

    @Test
    void onlyTheSlotsPlaceAndTheRestWaitInOrder() {
        JobQueue<String> queue = new JobQueue<>();
        for (String owner : List.of("a", "b", "c", "d")) {
            queue.add(owner, 1_000_000L);
        }
        assertEquals(List.of("a", "b"), queue.admit(2, paused::contains));
        assertEquals(List.of("a", "b"), queue.activeOwners());
        assertEquals(Optional.empty(), queue.position("a"));
        assertEquals(Optional.of(1), queue.position("c"));
        assertEquals(Optional.of(2), queue.position("d"));
        assertEquals(2, queue.waiting());
        assertThrows(IllegalStateException.class, () -> queue.add("a", 5L), "una edición por jugador");
    }

    /**
     * Con los sitios llenos de ediciones enormes (minutos cada una), un retoque de otro jugador esperaba a que acabara
     * una. Las pequeñas no esperan ni ocupan sitio; las grandes siguen por orden de llegada.
     */
    @Test
    void aSmallEditDoesNotWaitBehindHugeOnes() {
        JobQueue<String> queue = new JobQueue<>();
        Set<String> paused = new HashSet<>();
        queue.add("enorme_1", 20_000_000L);
        queue.add("enorme_2", 16_000_000L);
        assertEquals(List.of("enorme_1", "enorme_2"), queue.admit(2, 50_000L, paused::contains));
        queue.add("grande", 3_000_000L);
        queue.add("retoque", 10L);
        queue.add("otra_grande", 2_000_000L);
        assertEquals(List.of("retoque"), queue.admit(2, 50_000L, paused::contains), "el retoque no espera");
        assertTrue(queue.active("retoque"));
        assertFalse(queue.active("grande"), "las grandes esperan su sitio");
        queue.remove("enorme_1");
        assertEquals(List.of("grande"), queue.admit(2, 50_000L, paused::contains), "por orden de llegada");
    }

    @Test
    void cancellingFromAnyPlaceLetsTheNextIn() {
        JobQueue<String> queue = new JobQueue<>();
        for (String owner : List.of("a", "b", "c", "d")) {
            queue.add(owner, 100L);
        }
        queue.admit(2, paused::contains);
        assertTrue(queue.remove("c"), "cancelar una que espera");
        assertEquals(Optional.of(1), queue.position("d"));
        assertTrue(queue.remove("a"), "cancelar una que coloca");
        assertEquals(List.of("d"), queue.admit(2, paused::contains));
        assertEquals(List.of("b", "d"), queue.activeOwners());
        assertFalse(queue.remove("zz"));
    }

    @Test
    void aPausedEditGivesUpItsSlotAndDoesNotJumpIn() {
        JobQueue<String> queue = new JobQueue<>();
        for (String owner : List.of("a", "b", "c")) {
            queue.add(owner, 100L);
        }
        queue.admit(1, paused::contains);
        paused.add("a");
        assertEquals(List.of("b"), queue.admit(1, paused::contains), "la pausada deja su sitio");
        paused.add("c");
        assertEquals(List.of(), queue.admit(2, paused::contains), "y una pausada que espera no entra");
        paused.remove("c");
        assertEquals(List.of("c"), queue.admit(2, paused::contains));
    }

    @Test
    void theWaitGrowsWithThePosition() {
        JobQueue<String> queue = new JobQueue<>();
        queue.add("grande", 2_000_000L);
        queue.add("mediana", 400_000L);
        queue.add("primera", 100_000L);
        queue.add("segunda", 100_000L);
        queue.admit(2, paused::contains);
        queue.progress("grande", 1_000_000L);
        double first = queue.secondsUntilStart("primera", 2, 20_000.0, paused::contains);
        double second = queue.secondsUntilStart("segunda", 2, 20_000.0, paused::contains);
        // La mediana acaba antes: 400 000 bloques a 10 000 bl/s (el ritmo se reparte entre dos) son 40 s.
        assertEquals(40.0, first, 0.001);
        assertTrue(second > first);
        assertEquals(0.0, queue.secondsUntilStart("grande", 2, 20_000.0, paused::contains));
    }

    @Test
    void theEstimateSimulatesTheSharedRate() {
        double[] starts = QueueEstimate.secondsUntilStart(new long[]{100, 300}, new long[]{50, 50, 50}, 2, 10.0);
        // 2 a la vez a 5 bl/s cada una: la de 100 acaba a los 20 s; la primera que espera entra entonces.
        assertEquals(20.0, starts[0], 1e-9);
        // Quedan 200 y 50 a 5 bl/s: a los 30 s acaba la de 50 y entra la segunda.
        assertEquals(30.0, starts[1], 1e-9);
        assertEquals(40.0, starts[2], 1e-9);
        assertEquals(0.0, QueueEstimate.secondsUntilStart(new long[0], new long[]{10}, 1, 0.0)[0]);
    }

    @Test
    void theRateMeterAveragesWholeSeconds() {
        RateMeter meter = new RateMeter();
        assertEquals(-1.0, meter.perSecond());
        for (int tick = 0; tick <= 20; tick++) {
            meter.record(1_000L, tick * 50_000_000L);
        }
        assertEquals(20_000.0, meter.perSecond(), 1_100.0);
    }
}
