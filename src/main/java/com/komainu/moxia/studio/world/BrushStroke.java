package com.komainu.moxia.studio.world;

import java.util.Arrays;

/**
 * Pintar con el pincel de la mano manteniendo el clic en el mundo, con el Studio abierto: cada pulsación es una
 * pasada del pincel donde apunta el cursor y, mientras se mantiene, otra cada {@value #INTERVAL_MILLIS} ms si el
 * cursor ya está en otro bloque (lo mismo que mantener el clic derecho con el pincel fuera del Studio). Lógica pura:
 * la pantalla dice dónde apunta el servidor y manda la pasada cuando esto lo pide.
 */
public final class BrushStroke {
    public static final long INTERVAL_MILLIS = 200L;

    private boolean down;
    private int[] last;
    private long lastAt = Long.MIN_VALUE / 2;
    private int strokes;

    /** Pulsar sobre {@code block}: hay que pintar ya (si apunta a un bloque). */
    public boolean press(int[] block, long now) {
        down = true;
        strokes = 0;
        return paint(block, now);
    }

    /** Arrastrar: si toca otra pasada (otro bloque y pasado el intervalo). */
    public boolean drag(int[] block, long now) {
        if (!down || block == null || Arrays.equals(block, last) || now - lastAt < INTERVAL_MILLIS) {
            return false;
        }
        return paint(block, now);
    }

    public void release() {
        down = false;
        last = null;
    }

    public boolean active() {
        return down;
    }

    /** Las pasadas de este trazo. */
    public int strokes() {
        return strokes;
    }

    private boolean paint(int[] block, long now) {
        if (block == null) {
            return false;
        }
        last = block.clone();
        lastAt = now;
        strokes++;
        return true;
    }
}
