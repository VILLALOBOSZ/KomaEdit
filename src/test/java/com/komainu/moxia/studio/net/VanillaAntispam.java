package com.komainu.moxia.studio.net;

/**
 * El antispam del chat de Minecraft 1.21.1 en un servidor de prueba ({@code ServerPlayNetworkHandler}): cada orden de
 * chat suma {@value #PER_COMMAND} ({@code checkForSpam}), cada tick resta 1 si hay algo ({@code tick}) y, al pasar de
 * {@value #KICK_ABOVE}, echa a quien no es OP ni el anfitrión ({@code disconnect.spam}). Los ticks van a
 * {@code tps} por segundo desde {@code start}.
 */
public final class VanillaAntispam {
    public static final int PER_COMMAND = 20;
    public static final int KICK_ABOVE = 200;

    private final long tickNanos;
    private long nextTick;
    private int counter;
    private int max;
    private int commands;
    /** En qué orden de chat echó al jugador (empezando en 1), o 0. */
    private int kickedAt;

    public VanillaAntispam(int tps, long start) {
        tickNanos = 1_000_000_000L / tps;
        nextTick = start + tickNanos;
    }

    /** Llega una orden de chat en {@code nanos}. */
    public void command(long nanos) {
        advance(nanos);
        commands++;
        counter += PER_COMMAND;
        max = Math.max(max, counter);
        if (counter > KICK_ABOVE && kickedAt == 0) {
            kickedAt = commands;
        }
    }

    public void advance(long nanos) {
        while (nextTick <= nanos) {
            if (counter > 0) {
                counter--;
            }
            nextTick += tickNanos;
        }
    }

    public boolean kicked() {
        return kickedAt > 0;
    }

    public int kickedAt() {
        return kickedAt;
    }

    /** Lo más alto que llegó el contador. */
    public int max() {
        return max;
    }

    /** Órdenes que llegaron por el chat. */
    public int commands() {
        return commands;
    }
}
