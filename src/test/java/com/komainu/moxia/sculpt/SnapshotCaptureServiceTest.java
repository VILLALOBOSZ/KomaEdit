package com.komainu.moxia.sculpt;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La foto del terreno de un pincel aprovecha todo el presupuesto del tick y no se queda a medias sin motivo. El
 * presupuesto se mide con un reloj falso que avanza lo mismo en cada lectura (lo que cuesta leer un bloque), así que
 * el resultado es el mismo en cualquier máquina, por cargada que esté.
 */
class SnapshotCaptureServiceTest {
    @AfterEach
    void clear() {
        SnapshotCaptureService.clearAll();
    }

    /** Un reloj que avanza {@code nanos} cada vez que se lee: leer un bloque cuesta eso. */
    private static LongSupplier clock(long nanos) {
        long[] now = {0L};
        return () -> now[0] += nanos;
    }

    /** Un mundo de mentira de -64 a 319 que cuenta las lecturas (los bloques salen vacíos: aquí no importan). */
    private static World world(AtomicInteger reads) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMinY" -> -64;
                    case "getMaxY" -> 319;
                    case "getBlock" -> {
                        reads.incrementAndGet();
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void aBrushOfRadiusTwelveIsCapturedInOneTick() {
        AtomicInteger reads = new AtomicInteger();
        List<WorldSnapshot> done = new ArrayList<>();
        // Radio 12 + 1 de margen: 27 × 27 × 27 = 19 683 bloques, más que una porción (16 384). A 0,1 µs por bloque
        // son unos 2 ms: caben en el tick (5 ms) aunque no en una porción (1,5 ms).
        WorldSnapshot.Capture capture = WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 13, 13);
        assertEquals(19_683, capture.total());
        SnapshotCaptureService.submit(UUID.randomUUID(), capture, done::add, error -> {
            throw new AssertionError(error);
        });
        SnapshotCaptureService.tick(player -> true, clock(100L));
        assertTrue(capture.complete(), "en un tick: " + capture.captured() + " de " + capture.total());
        assertEquals(1, done.size());
        assertEquals(19_683, reads.get(), "cada bloque se lee una vez");
    }

    @Test
    void aSlowWorldStopsAtTheTickBudgetAndGoesOnTheNextTick() {
        // A 1 µs por bloque, en los 5 ms del tick caben unos 5 000: la misma foto tarda cuatro ticks.
        AtomicInteger reads = new AtomicInteger();
        List<WorldSnapshot> done = new ArrayList<>();
        WorldSnapshot.Capture capture = WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 13, 13);
        SnapshotCaptureService.submit(UUID.randomUUID(), capture, done::add, error -> {
            throw new AssertionError(error);
        });
        LongSupplier clock = clock(1_000L);
        SnapshotCaptureService.tick(player -> true, clock);
        int first = capture.captured();
        long budget = SnapshotCaptureService.MAX_NANOS_PER_TICK / 1_000L;
        assertTrue(first > budget * 9 / 10 && first <= budget, "en el primer tick: " + first + " de " + budget);
        assertFalse(capture.complete());
        assertTrue(done.isEmpty(), "aún no hay foto");
        int ticks = 1;
        while (!capture.complete() && ticks < 10) {
            SnapshotCaptureService.tick(player -> true, clock);
            ticks++;
        }
        assertEquals(4, ticks, "19 683 bloques a unos 5 000 por tick");
        assertEquals(1, done.size());
        assertEquals(19_683, reads.get(), "cada bloque se lee una vez, aunque la foto se reparta entre ticks");
    }

    @Test
    void twoPlayersShareTheTickAndAnOfflineOneIsDropped() {
        AtomicInteger reads = new AtomicInteger();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        List<UUID> done = new ArrayList<>();
        // Tres fotos de 21 × 21 × 21 = 9 261 bloques; a 0,1 µs por bloque, las dos de quien sigue conectado caben en
        // el mismo tick.
        SnapshotCaptureService.submit(first, WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 10, 10),
                snapshot -> done.add(first), error -> { });
        SnapshotCaptureService.submit(second, WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 10, 10),
                snapshot -> done.add(second), error -> { });
        SnapshotCaptureService.submit(gone, WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 10, 10),
                snapshot -> done.add(gone), error -> { });
        SnapshotCaptureService.tick(player -> !player.equals(gone), clock(100L));
        assertTrue(done.contains(first) && done.contains(second), "las dos fotos salen en el mismo tick");
        assertFalse(done.contains(gone));
        assertFalse(SnapshotCaptureService.isRunning(gone), "el que se fue no sigue esperando");
        assertEquals(2 * 9_261, reads.get(), "del que se fue no se lee nada");
    }
}
