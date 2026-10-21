package com.komainu.moxia.studio.world;

import com.komainu.moxia.selection.LassoPolygon;
import com.komainu.moxia.selection.SelectionCombine;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibujar el lazo con el Studio abierto: se pulsa en el mundo, se arrastra rodeando lo que se quiere y al soltar se
 * cierra solo. Las teclas que se mantienen al pulsar dicen cómo se junta con la selección ({@link SelectionCombine}).
 * Lógica pura: la pantalla le pasa el ratón y manda el contorno que sale.
 */
public final class LassoDraw {
    /** Lo que tiene que moverse el ratón para apuntar un punto nuevo (en unidades de pantalla). */
    public static final double MIN_STEP = 2.0;
    /** Puntos como mucho mientras se dibuja; al soltar se simplifican a {@link LassoPolygon#MAX_POINTS}. */
    public static final int MAX_RAW_POINTS = 8_192;

    private final List<float[]> points = new ArrayList<>();
    private SelectionCombine mode = SelectionCombine.REPLACE;
    private boolean active;

    public void begin(double x, double y, SelectionCombine combine) {
        points.clear();
        points.add(new float[]{(float) x, (float) y});
        mode = combine;
        active = true;
    }

    /** Arrastrar: apunta el punto si el ratón se ha movido lo bastante. */
    public boolean add(double x, double y) {
        if (!active || points.size() >= MAX_RAW_POINTS) {
            return false;
        }
        float[] last = points.getLast();
        if (Math.hypot(x - last[0], y - last[1]) < MIN_STEP) {
            return false;
        }
        points.add(new float[]{(float) x, (float) y});
        return true;
    }

    public boolean active() {
        return active;
    }

    public SelectionCombine mode() {
        return mode;
    }

    /** Los puntos dibujados hasta ahora (para pintar el trazo). */
    public List<float[]> points() {
        return List.copyOf(points);
    }

    public void cancel() {
        active = false;
        points.clear();
    }

    /**
     * Soltar: el contorno simplificado si rodea algo, o {@code null} si fue un clic o un garabato sin superficie.
     */
    public LassoPolygon finish() {
        if (!active) {
            return null;
        }
        active = false;
        List<float[]> drawn = new ArrayList<>(points);
        points.clear();
        if (drawn.size() < 3) {
            return null;
        }
        LassoPolygon polygon = LassoPolygon.of(LassoPolygon.simplified(drawn, LassoPolygon.MAX_POINTS));
        return polygon.area() < LassoPolygon.MIN_AREA ? null : polygon;
    }
}
