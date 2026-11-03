package com.komainu.moxia.rebuild;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardar el historial de colocación: el de parar el servidor es siempre el último que queda escrito. */
class PlacementHistoryTest {
    @TempDir
    Path folder;

    /**
     * Un guardado periódico aún en el hilo de guardado cuando se para el servidor: antes, el de parar se escribía en el
     * acto y el periódico, con posiciones más viejas, acababa después y lo pisaba.
     */
    @Test
    void theSaveWhenStoppingIsWrittenAfterAnEarlierOneStillInTheQueue() throws Exception {
        Path file = folder.resolve("overworld.bin");
        long[] earlier = {1L};
        long[] latest = {1L, 2L, 3L};
        CountDownLatch busy = new CountDownLatch(1);
        PlacementHistory.IO.execute(() -> {
            try {
                busy.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        PlacementHistory.run(List.of(() -> PlacementHistory.write(file, earlier)), false);
        Thread stopping = new Thread(() -> PlacementHistory.run(List.of(() -> PlacementHistory.write(file, latest)),
                true));
        stopping.start();
        // Hasta que el de parar se queda esperando al hilo de guardado (lo que haya hecho antes, ya está hecho).
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (stopping.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(stopping.isAlive(), "espera al hilo de guardado");
        busy.countDown();
        stopping.join();
        try (InputStream input = Files.newInputStream(file)) {
            assertArrayEquals(latest, PlacementLog.read(input, 16).positions());
        }
    }
}
