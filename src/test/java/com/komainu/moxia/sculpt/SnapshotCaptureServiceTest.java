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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La foto del terreno de un pincel aprovecha todo el presupuesto del tick y no se queda a medias sin motivo. */
class SnapshotCaptureServiceTest {
    @AfterEach
    void clear() {
        SnapshotCaptureService.clearAll();
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

    /** Calienta la lectura, para que el presupuesto del tick no se lo coma el arranque de la JVM. */
    private static void warmUp() {
        World world = world(new AtomicInteger());
        for (int run = 0; run < 20; run++) {
            WorldSnapshot.capture(world, BlockVector3.at(0, 64, 0), 13, 13);
        }
    }

    @Test
    void aBrushOfRadiusTwelveIsCapturedInOneTick() {
        warmUp();
        AtomicInteger reads = new AtomicInteger();
        List<WorldSnapshot> done = new ArrayList<>();
        // Radio 12 + 1 de margen: 27 × 27 × 27 = 19 683 bloques, más que una porción (16 384).
        WorldSnapshot.Capture capture = WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 13, 13);
        assertEquals(19_683, capture.total());
        SnapshotCaptureService.submit(UUID.randomUUID(), capture, done::add, error -> {
            throw new AssertionError(error);
        });
        SnapshotCaptureService.tick(player -> true);
        assertTrue(capture.complete(), "en un tick: " + capture.captured() + " de " + capture.total());
        assertEquals(1, done.size());
        assertEquals(19_683, reads.get(), "cada bloque se lee una vez");
    }

    @Test
    void twoPlayersShareTheTickAndAnOfflineOneIsDropped() {
        warmUp();
        AtomicInteger reads = new AtomicInteger();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        List<UUID> done = new ArrayList<>();
        SnapshotCaptureService.submit(first, WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 10, 10),
                snapshot -> done.add(first), error -> { });
        SnapshotCaptureService.submit(second, WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 10, 10),
                snapshot -> done.add(second), error -> { });
        SnapshotCaptureService.submit(gone, WorldSnapshot.begin(world(reads), BlockVector3.at(0, 64, 0), 10, 10),
                snapshot -> done.add(gone), error -> { });
        SnapshotCaptureService.tick(player -> !player.equals(gone));
        assertTrue(done.contains(first) && done.contains(second), "las dos fotos salen en el mismo tick");
        assertFalse(done.contains(gone));
        assertFalse(SnapshotCaptureService.isRunning(gone), "el que se fue no sigue esperando");
    }
}
