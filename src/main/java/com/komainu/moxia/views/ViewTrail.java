package com.komainu.moxia.views;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Por dónde ha ido el jugador al saltar, en el cliente: los sitios de antes de cada salto (para «volver») y la tecla
 * de la vista fija. Java puro.
 *
 * <p>La vista fija va y vuelve: la primera pulsación lleva a la vista y apunta dónde estabas; si sigues cerca de la
 * vista ({@link #NEAR} bloques), la siguiente te devuelve allí. Si te has movido lejos, vuelve a llevarte a la vista
 * (y apunta el sitio nuevo): la vista fija siempre está a una tecla aunque te muevas.</p>
 */
public final class ViewTrail {
    /** Hasta aquí se sigue «en la vista fija». */
    public static final double NEAR = 3.0;
    public static final int MAX_BACK = 16;
    private final Deque<ViewTeleport.Target> back = new ArrayDeque<>();
    private ViewTeleport.Target pinnedReturn;

    /** Antes de cada salto: dónde estaba. */
    public void left(ViewTeleport.Target from) {
        back.push(from);
        while (back.size() > MAX_BACK) {
            back.removeLast();
        }
    }

    /** El sitio de antes del último salto, que se quita de la lista. */
    public Optional<ViewTeleport.Target> back() {
        return Optional.ofNullable(back.poll());
    }

    public boolean canGoBack() {
        return !back.isEmpty();
    }

    /** Adónde lleva la tecla de la vista fija estando en {@code current}. */
    public ViewTeleport.Target pinned(ViewTeleport.Target current, SavedView pinned) {
        ViewTeleport.Target view = ViewTeleport.Target.of(pinned);
        if (pinnedReturn != null && near(current, view)) {
            ViewTeleport.Target target = pinnedReturn;
            pinnedReturn = null;
            return target;
        }
        pinnedReturn = current;
        return view;
    }

    /** Al cambiar de mundo o de servidor. */
    public void clear() {
        back.clear();
        pinnedReturn = null;
    }

    static boolean near(ViewTeleport.Target a, ViewTeleport.Target b) {
        if (!a.dimension().equals(b.dimension())) {
            return false;
        }
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz <= NEAR * NEAR;
    }
}
