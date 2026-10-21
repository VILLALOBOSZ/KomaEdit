package com.komainu.moxia.studio.live;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Las peticiones de la vista previa en vivo en el servidor: por jugador, una calculándose como mucho y, detrás, solo
 * la última que llegó. Las de en medio se descartan sin calcular, así que tocar un ajuste veinte veces seguidas no
 * llena la cola ni rehace veinte veces.
 *
 * <ul>
 *     <li>Una petición sale cuando el jugador no tiene nada en marcha (la anterior ya se ve o no dio nada) y han pasado
 *     {@value #MIN_GAP_MILLIS} ms desde la anterior. Así nunca choca con la lectura de la selección de la anterior
 *     («ya hay una operación leyendo tu selección»).</li>
 *     <li>Se da por acabada cuando sale lo que pidió ({@link #shown}), cuando falla ({@link #failed}) o cuando el
 *     servidor lleva {@value #SETTLE_MILLIS} ms sin hacer nada para el jugador ({@link #settle}).</li>
 * </ul>
 *
 * <p>Lógica pura, con el reloj y el «está ocupado» que le pasa quien la usa.</p>
 *
 * @param <R> la petición
 */
public final class LiveQueue<R> {
    /** Lo mínimo entre dos peticiones del mismo jugador que salen a calcularse. */
    public static final long MIN_GAP_MILLIS = 100L;
    /** Lo que se espera, con el servidor libre, a que una petición lanzada dé algo antes de darla por acabada. */
    public static final long SETTLE_MILLIS = 1_500L;

    private final Map<UUID, Slot<R>> slots = new LinkedHashMap<>();

    private static final class Slot<R> {
        private R pending;
        private R running;
        private long lastRun = Long.MIN_VALUE / 2;
        private long idleSince = -1L;
        private int dropped;
    }

    /**
     * Llega una petición: espera detrás de la que se calcula y sustituye a la que ya esperaba.
     *
     * @return la que esperaba y ya no cuenta, o {@code null}
     */
    public R offer(UUID player, R request) {
        Slot<R> slot = slots.computeIfAbsent(player, ignored -> new Slot<>());
        R replaced = slot.pending;
        slot.pending = request;
        if (replaced != null) {
            slot.dropped++;
        }
        return replaced;
    }

    /**
     * La petición que hay que lanzar ahora, o {@code null}: sale la última que llegó si no hay otra calculándose, el
     * jugador no tiene nada en marcha y ha pasado el hueco mínimo.
     *
     * @param busy si el servidor está leyendo, calculando o colocando algo del jugador
     */
    public R next(UUID player, long now, boolean busy) {
        Slot<R> slot = slots.get(player);
        if (slot == null || slot.pending == null || slot.running != null || busy
                || now - slot.lastRun < MIN_GAP_MILLIS) {
            return null;
        }
        slot.running = slot.pending;
        slot.pending = null;
        slot.lastRun = now;
        slot.idleSince = -1L;
        return slot.running;
    }

    /**
     * Ya se ve lo que pidió la petición que se calculaba.
     *
     * @return esa petición (para contestarla), o {@code null} si el jugador no tenía ninguna: lo que salió no era de
     * la vista previa en vivo
     */
    public R shown(UUID player) {
        return finish(player);
    }

    /** La petición lanzada falló al ejecutarse: deja paso a la siguiente. */
    public R failed(UUID player) {
        return finish(player);
    }

    /**
     * La petición lanzada no dio nada: el servidor lleva {@value #SETTLE_MILLIS} ms sin trabajar para el jugador
     * desde que salió.
     *
     * @return la petición acabada así, o {@code null}
     */
    public R settle(UUID player, long now, boolean busy) {
        Slot<R> slot = slots.get(player);
        if (slot == null || slot.running == null) {
            return null;
        }
        if (busy) {
            slot.idleSince = -1L;
            return null;
        }
        if (slot.idleSince < 0L) {
            slot.idleSince = now;
        }
        return now - slot.idleSince >= SETTLE_MILLIS ? finish(player) : null;
    }

    /** La petición que se calcula ahora para el jugador, o {@code null}. */
    public R running(UUID player) {
        Slot<R> slot = slots.get(player);
        return slot == null ? null : slot.running;
    }

    /** La que espera detrás, o {@code null}. */
    public R pending(UUID player) {
        Slot<R> slot = slots.get(player);
        return slot == null ? null : slot.pending;
    }

    /** Cuántas peticiones del jugador se descartaron sin calcular porque llegó otra detrás. */
    public int dropped(UUID player) {
        Slot<R> slot = slots.get(player);
        return slot == null ? 0 : slot.dropped;
    }

    /** Los jugadores con algo en la cola. */
    public List<UUID> players() {
        return new ArrayList<>(slots.keySet());
    }

    public void forget(UUID player) {
        slots.remove(player);
    }

    public void clear() {
        slots.clear();
    }

    private R finish(UUID player) {
        Slot<R> slot = slots.get(player);
        if (slot == null || slot.running == null) {
            return null;
        }
        R done = slot.running;
        slot.running = null;
        slot.idleSince = -1L;
        return done;
    }
}
