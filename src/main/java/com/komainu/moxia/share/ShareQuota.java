package com.komainu.moxia.share;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Cuántas veces ha importado o exportado cada jugador en la última hora. Leer un paquete grande cuesta (descomprimir y
 * revisar un plano entero), así que no se deja repetir sin parar. Recibe el reloj para probarlo sin esperar.
 */
public final class ShareQuota {
    private static final long HOUR = 60L * 60L * 1000L;

    private final LongSupplier clock;
    private final Map<UUID, Deque<Long>> imports = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> exports = new ConcurrentHashMap<>();

    /** @param clock milisegundos de un reloj que solo avanza */
    public ShareQuota(LongSupplier clock) {
        this.clock = clock;
    }

    /** Apunta una importación o lanza si ya hizo {@code perHour} en la última hora. */
    public void takeImport(UUID player, int perHour) {
        take(imports, player, perHour, "importaciones");
    }

    public void takeExport(UUID player, int perHour) {
        take(exports, player, perHour, "exportaciones");
    }

    public void forget(UUID player) {
        imports.remove(player);
        exports.remove(player);
    }

    private void take(Map<UUID, Deque<Long>> map, UUID player, int perHour, String what) {
        long now = clock.getAsLong();
        Deque<Long> times = map.computeIfAbsent(player, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= HOUR) {
                times.pollFirst();
            }
            if (perHour <= 0) {
                throw new IllegalArgumentException("En este servidor no se permiten " + what);
            }
            if (times.size() >= perHour) {
                long wait = HOUR - (now - times.peekFirst());
                long minutes = Math.max(1L, (wait + 59_999L) / 60_000L);
                throw new IllegalArgumentException("Has llegado a " + perHour + " " + what + " por hora: espera "
                        + minutes + " min");
            }
            times.addLast(now);
        }
    }
}
