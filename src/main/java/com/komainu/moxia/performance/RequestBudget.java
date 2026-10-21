package com.komainu.moxia.performance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno de lo que un cliente pide por paquete y cuesta en el servidor (escribir un archivo, avisar a todos, copiar una
 * selección grande). Cada jugador tiene {@code burst} peticiones seguidas y recupera {@code perSecond} por segundo;
 * las de más se ignoran. A mano no se llega; un cliente modificado ya no puede pedirlo sin parar. Java puro: la hora
 * llega de fuera.
 */
public final class RequestBudget {
    private final int burst;
    private final double perSecond;
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long at;

        Bucket(double tokens, long at) {
            this.tokens = tokens;
            this.at = at;
        }
    }

    public RequestBudget(int burst, double perSecond) {
        if (burst < 1 || !(perSecond > 0.0)) {
            throw new IllegalArgumentException("Freno no válido");
        }
        this.burst = burst;
        this.perSecond = perSecond;
    }

    /** ¿Se atiende esta petición del jugador? {@code nanos}: {@link System#nanoTime()}. */
    public boolean allow(UUID player, long nanos) {
        Bucket bucket = buckets.computeIfAbsent(player, ignored -> new Bucket(burst, nanos));
        synchronized (bucket) {
            long elapsed = Math.max(0L, nanos - bucket.at);
            bucket.tokens = Math.min(burst, bucket.tokens + elapsed * perSecond / 1_000_000_000.0);
            bucket.at = nanos;
            if (bucket.tokens < 1.0) {
                return false;
            }
            bucket.tokens -= 1.0;
            return true;
        }
    }

    public void forget(UUID player) {
        buckets.remove(player);
    }

    public void clear() {
        buckets.clear();
    }
}
