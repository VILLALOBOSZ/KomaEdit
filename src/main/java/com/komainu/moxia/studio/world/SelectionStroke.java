package com.komainu.moxia.studio.world;

import com.komainu.moxia.selection.SelectionCombine;

import java.util.Arrays;

/**
 * Pintar la selección con el pincel de selección del Studio: cada bloque nuevo bajo el cursor es una pasada, como
 * mucho una cada {@value #INTERVAL_MILLIS} ms. El trazo empieza en la primera pasada que da en un bloque (el
 * servidor apunta entonces lo de antes para deshacerlo) y acaba al soltar. Lógica pura.
 */
public final class SelectionStroke {
    public static final long INTERVAL_MILLIS = 40L;

    /** Lo que hay que mandar al servidor. */
    public enum Send {
        NOTHING, START, DAB
    }

    private boolean down;
    private boolean started;
    private SelectionCombine mode = SelectionCombine.REPLACE;
    private int[] last;
    private long lastAt = Long.MIN_VALUE / 2;
    private int dabs;

    public Send press(int[] block, SelectionCombine combine, long now) {
        down = true;
        started = false;
        mode = combine;
        dabs = 0;
        last = null;
        return dab(block, now);
    }

    public Send drag(int[] block, long now) {
        if (!down || block == null || Arrays.equals(block, last) || now - lastAt < INTERVAL_MILLIS) {
            return Send.NOTHING;
        }
        return dab(block, now);
    }

    /** Soltar: si hay que avisar al servidor de que el trazo acabó. */
    public boolean release() {
        boolean end = down && started;
        down = false;
        started = false;
        last = null;
        return end;
    }

    public boolean active() {
        return down;
    }

    public SelectionCombine mode() {
        return mode;
    }

    public int dabs() {
        return dabs;
    }

    private Send dab(int[] block, long now) {
        if (block == null) {
            return Send.NOTHING;
        }
        last = block.clone();
        lastAt = now;
        dabs++;
        if (!started) {
            started = true;
            return Send.START;
        }
        return Send.DAB;
    }
}
