package com.komainu.moxia.studio.live;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** La cola del servidor: una calculándose, detrás solo la última y nada mientras el jugador tiene algo en marcha. */
class LiveQueueTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void onlyTheLastOneWaitingCounts() {
        LiveQueue<String> queue = new LiveQueue<>();
        queue.offer(PLAYER, "a");
        assertEquals("a", queue.next(PLAYER, 0, false));
        queue.offer(PLAYER, "b");
        assertEquals("b", queue.offer(PLAYER, "c"), "b se descarta sin calcular");
        assertEquals("c", queue.offer(PLAYER, "d"), "y c también");
        assertEquals(2, queue.dropped(PLAYER));
        assertNull(queue.next(PLAYER, 1_000, false), "a aún no ha dado nada");
        assertEquals("a", queue.shown(PLAYER));
        assertEquals("d", queue.next(PLAYER, 1_000, false), "sale solo la última");
        assertNull(queue.next(PLAYER, 2_000, false));
    }

    @Test
    void waitsWhileThePlayerIsBusyAndKeepsAGap() {
        LiveQueue<String> queue = new LiveQueue<>();
        queue.offer(PLAYER, "a");
        assertNull(queue.next(PLAYER, 0, true), "lee o coloca otra cosa: espera");
        assertEquals("a", queue.next(PLAYER, 0, false));
        queue.failed(PLAYER);
        queue.offer(PLAYER, "b");
        assertNull(queue.next(PLAYER, LiveQueue.MIN_GAP_MILLIS - 1, false), "hueco mínimo entre dos");
        assertEquals("b", queue.next(PLAYER, LiveQueue.MIN_GAP_MILLIS, false));
    }

    @Test
    void oneThatGivesNothingEndsWhenTheServerStaysIdle() {
        LiveQueue<String> queue = new LiveQueue<>();
        queue.offer(PLAYER, "a");
        queue.next(PLAYER, 0, false);
        assertNull(queue.settle(PLAYER, 100, true), "calculando");
        assertNull(queue.settle(PLAYER, 200, false));
        assertNull(queue.settle(PLAYER, 200 + LiveQueue.SETTLE_MILLIS - 1, false));
        assertEquals("a", queue.settle(PLAYER, 200 + LiveQueue.SETTLE_MILLIS, false));
        assertNull(queue.running(PLAYER));
        assertNull(queue.shown(PLAYER), "lo que salga después ya no es de la vista en vivo");
    }

    @Test
    void playersDoNotWaitForEachOther() {
        LiveQueue<String> queue = new LiveQueue<>();
        queue.offer(PLAYER, "a");
        queue.offer(OTHER, "x");
        assertEquals("a", queue.next(PLAYER, 0, false));
        assertEquals("x", queue.next(OTHER, 0, false));
        queue.forget(PLAYER);
        assertNull(queue.running(PLAYER));
        assertEquals("x", queue.running(OTHER));
    }
}
