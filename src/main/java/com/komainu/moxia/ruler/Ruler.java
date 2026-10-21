package com.komainu.moxia.ruler;

import java.util.ArrayList;
import java.util.List;

/**
 * La regla de un jugador: el modo y los puntos puestos con clics. Es solo del cliente y no guarda nada en disco. En
 * el recorrido cada clic añade un punto (hasta {@link #MAX_POINTS}); en el círculo y la caja, el tercer clic empieza
 * otra medida desde ese punto. Repetir el último punto no cuenta.
 */
public final class Ruler {
    public static final int MAX_POINTS = 32;
    private RulerMode mode = RulerMode.LINE;
    private final List<RulerPoint> points = new ArrayList<>();

    public RulerMode mode() {
        return mode;
    }

    /** Cambia el modo y conserva los puntos que quepan en él (los primeros). */
    public void mode(RulerMode value) {
        mode = value;
        while (points.size() > mode.capacity()) {
            points.removeLast();
        }
    }

    public RulerMode cycleMode() {
        mode(mode.next());
        return mode;
    }

    public List<RulerPoint> points() {
        return List.copyOf(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /** Añade un punto; devuelve {@code false} si era el mismo que el último o el recorrido ya está lleno. */
    public boolean add(RulerPoint point) {
        if (!points.isEmpty() && points.getLast().equals(point)) {
            return false;
        }
        if (points.size() >= mode.capacity()) {
            if (mode == RulerMode.LINE) {
                return false;
            }
            points.clear();
        }
        points.add(point);
        return true;
    }

    /** Quita el último punto; {@code false} si no había. */
    public boolean removeLast() {
        if (points.isEmpty()) {
            return false;
        }
        points.removeLast();
        return true;
    }

    public void clear() {
        points.clear();
    }

    /** Lo que mide con los puntos de ahora. */
    public RulerMeasure measure() {
        if (points.size() < 2) {
            return new RulerMeasure.Pending(mode, points);
        }
        return switch (mode) {
            case LINE -> RulerMeasure.Line.of(points);
            case CIRCLE -> RulerMeasure.Circle.of(points.get(0), points.get(1));
            case BOX -> RulerMeasure.Box.of(points.get(0), points.get(1));
        };
    }
}
