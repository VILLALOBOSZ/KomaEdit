package com.komainu.moxia.studio.net;

/**
 * El antispam de Minecraft contado desde el cliente, para un servidor sin el paquete de los botones (un KomaEdit
 * anterior). Allí los botones siguen yendo por el chat: cada orden suma {@value #PER_COMMAND} a un contador que baja 1
 * por tick y, al pasar de {@value #KICK_ABOVE}, Minecraft echa a quien no es OP ({@code checkForSpam}). El cliente se
 * frena antes y avisa en lugar de mandar la orden.
 *
 * <p>Con margen, por si el servidor va lento (con menos ticks baja menos) o el jugador escribió alguna orden a mano:
 * se queda en {@value #LIMIT} y cuenta la bajada como si el servidor fuera a {@value #ASSUMED_TPS} ticks por segundo.
 * Salen así ocho seguidas y luego una cada segundo y pico. Java puro: la hora llega de fuera.</p>
 */
public final class ChatSpamGuard {
    static final int PER_COMMAND = 20;
    static final int KICK_ABOVE = 200;
    static final int LIMIT = 160;
    static final int ASSUMED_TPS = 15;
    private static final long TICK_NANOS = 1_000_000_000L / ASSUMED_TPS;

    private int counter;
    private long at;
    private boolean started;

    /** ¿Se puede mandar otra orden por el chat sin acercarse a la expulsión? Si sí, cuenta. */
    public synchronized boolean allow(long nanos) {
        decay(nanos);
        if (counter + PER_COMMAND > LIMIT) {
            return false;
        }
        counter += PER_COMMAND;
        return true;
    }

    /** Los segundos (redondeados hacia arriba, al menos 1) que faltan para que quepa otra. */
    public synchronized int waitSeconds(long nanos) {
        decay(nanos);
        int ticks = Math.max(0, counter + PER_COMMAND - LIMIT);
        return Math.max(1, (ticks + ASSUMED_TPS - 1) / ASSUMED_TPS);
    }

    /** Al entrar en un servidor el contador empieza de cero, como el suyo. */
    public synchronized void reset() {
        counter = 0;
        started = false;
    }

    private void decay(long nanos) {
        if (!started) {
            at = nanos;
            started = true;
            return;
        }
        long ticks = Math.max(0L, (nanos - at) / TICK_NANOS);
        if (ticks > 0) {
            counter = (int) Math.max(0L, counter - ticks);
            at += ticks * TICK_NANOS;
        }
    }
}
