package com.komainu.moxia.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cancelar en la fase de cálculo: el worker se corta (las operaciones miran la interrupción, como
 * {@code RegionEdits.checkCancelled}) y no entrega nada, ni resultado ni error. Las demás fases se prueban con sus
 * piezas: la lectura en {@code RegionReaderTest}, la cola en {@link JobQueueTest} y la colocación (también a medio
 * vaciar una ventana) en {@code PlacementRunTest}.
 */
class AsyncEngineCancelTest {
    @AfterEach
    void stop() {
        AsyncEngine.shutdown();
    }

    @Test
    void cancellingACalculationDeliversNothing() throws InterruptedException {
        UUID owner = UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicInteger loops = new AtomicInteger();
        AsyncEngine.submit(owner, "Calcular", () -> {
            started.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    loops.incrementAndGet();
                    AsyncEngine.progress(loops.get(), 0);
                }
                return "nunca";
            } finally {
                stopped.countDown();
            }
        }, result -> delivered.set(true), failure -> delivered.set(true));
        assertTrue(started.await(30, TimeUnit.SECONDS));
        assertTrue(AsyncEngine.status(owner).isPresent(), "la barra lo ve calculando");
        assertTrue(AsyncEngine.cancel(owner));
        assertTrue(stopped.await(30, TimeUnit.SECONDS), "el worker se corta");
        assertFalse(AsyncEngine.isRunning(owner));
        assertTrue(AsyncEngine.status(owner).isEmpty());
        Thread.sleep(50);
        assertFalse(delivered.get(), "ni resultado ni error tras cancelar");
    }

    @Test
    void aNewCalculationOfTheSamePlayerReplacesTheOld() throws InterruptedException {
        UUID owner = UUID.randomUUID();
        CountDownLatch firstStarted = new CountDownLatch(1);
        AtomicBoolean firstDelivered = new AtomicBoolean();
        CountDownLatch second = new CountDownLatch(1);
        AsyncEngine.submit(owner, () -> {
            firstStarted.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }
            return 1;
        }, result -> firstDelivered.set(true), failure -> firstDelivered.set(true));
        assertTrue(firstStarted.await(30, TimeUnit.SECONDS));
        AsyncEngine.submit(owner, () -> 2, result -> second.countDown(), failure -> { });
        assertTrue(second.await(30, TimeUnit.SECONDS));
        assertFalse(firstDelivered.get());
    }
}
