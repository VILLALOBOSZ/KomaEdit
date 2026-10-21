package com.komainu.moxia.edit;

import com.sk89q.worldedit.MaxChangedBlocksException;

import java.util.function.LongSupplier;

/**
 * Colocación de una edición por ventanas, con el tiempo de cada tick como límite.
 *
 * <p>WorldEdit no escribe en el mundo al llamar a {@code EditSession.setBlock}: lo guarda en un búfer hasta el
 * {@code commit} y, en su modo rápido de serie, deja la luz, los vecinos y el envío a los clientes para después de
 * escribir. Si nadie vacía el búfer, toda la edición cae en el tick en que se cierra la sesión. Aquí cada ventana:</p>
 *
 * <ol>
 *     <li>escribe unas cuantas entradas en la sesión (el historial de //undo se apunta ya);</li>
 *     <li>vacía el búfer: los bloques pasan al mundo agrupados por chunk y sección;</li>
 *     <li>aplica luz, vecinos y envío a los clientes de esa ventana, otra vez agrupados por sección, repartido entre
 *     ticks si hace falta. Hasta que no termina, no se escribe la ventana siguiente.</li>
 * </ol>
 *
 * El tamaño de la ventana se ajusta a lo que cuesta de verdad cada bloque ({@link Pacer}): lo que no se puede cortar
 * (pasar el búfer al mundo) cabe en lo que queda del tick. Java puro: la sesión y el reloj llegan por parámetro.
 */
public final class PlacementRun {
    /** Lo que se coloca: un plan, una región con un patrón, un portapapeles… */
    public interface Source {
        /**
         * Escribe en la sesión hasta {@code maxEntries} entradas o hasta {@code deadline}.
         *
         * @return entradas recorridas
         */
        int place(int maxEntries, long deadline) throws MaxChangedBlocksException;

        boolean done();
    }

    /** El vaciado de una ventana. */
    public interface Flush {
        /**
         * Sigue vaciando hasta {@code deadline}. La primera llamada pasa al mundo todo el búfer (no se puede
         * cortar); el resto (luz, vecinos, envío) sí respeta el plazo.
         *
         * @return {@code true} si ha terminado
         */
        boolean resume(long deadline);
    }

    /** La sesión donde se escribe. */
    public interface Session {
        /** Empieza a vaciar lo escrito desde el último vaciado; {@code null} si no hay nada pendiente. */
        Flush flush();
    }

    private final Source source;
    private final Session session;
    private final LongSupplier clock;
    private final Runnable onWindowFlushed;
    private final Pacer pacer = new Pacer();
    private Flush flushing;
    private int windowEntries;
    private long windowFlushNanos;
    private long windows;

    /**
     * @param onWindowFlushed se llama cuando una ventana está entera en el mundo (para leer lo colocado)
     */
    public PlacementRun(Source source, Session session, LongSupplier clock, Runnable onWindowFlushed) {
        this.source = source;
        this.session = session;
        this.clock = clock;
        this.onWindowFlushed = onWindowFlushed == null ? () -> { } : onWindowFlushed;
    }

    /**
     * Trabaja hasta {@code deadline}: termina de vaciar la ventana anterior y, si {@code placeMore} y queda tiempo,
     * escribe y vacía otra.
     *
     * @param placeMore {@code false} para no empezar ventanas nuevas (edición en pausa): solo se acaba de vaciar
     * @return {@code true} si aún queda trabajo (entradas o vaciado)
     */
    public boolean step(long deadline, boolean placeMore) throws MaxChangedBlocksException {
        if (flushing != null && !drain(deadline)) {
            return true;
        }
        if (!placeMore || source.done()) {
            return !source.done();
        }
        long now = clock.getAsLong();
        if (now >= deadline) {
            return true;
        }
        long available = deadline - now;
        int limit = pacer.entries(available);
        long placeUntil = now + Math.max(1L, (long) (available * pacer.placeShare()));
        int placed = source.place(limit, placeUntil);
        long placedAt = clock.getAsLong();
        if (placed == 0) {
            return !source.done();
        }
        pacer.placed(placed, placedAt - now);
        windowEntries = placed;
        windowFlushNanos = 0L;
        windows++;
        flushing = session.flush();
        if (flushing == null) {
            onWindowFlushed.run();
        } else {
            drain(deadline);
        }
        return !source.done() || flushing != null;
    }

    private boolean drain(long deadline) {
        long start = clock.getAsLong();
        boolean finished = flushing.resume(deadline);
        // Un vaciado puede volver tras cada fase (búfer al mundo, luego efectos): se sigue mientras quede plazo.
        while (!finished && clock.getAsLong() < deadline) {
            finished = flushing.resume(deadline);
        }
        windowFlushNanos += Math.max(0L, clock.getAsLong() - start);
        if (finished) {
            flushing = null;
            pacer.flushed(windowEntries, windowFlushNanos);
            onWindowFlushed.run();
        }
        return finished;
    }

    /** Si hay una ventana a medio vaciar (sus bloques ya están en el mundo, falta luz, vecinos o envío). */
    public boolean flushing() {
        return flushing != null;
    }

    /** Si ya no queda nada: todas las entradas escritas y la última ventana vaciada. */
    public boolean finished() {
        return source.done() && flushing == null;
    }

    /** Ventanas empezadas hasta ahora. */
    public long windows() {
        return windows;
    }

    Pacer pacer() {
        return pacer;
    }

    /**
     * Cuántas entradas caben en una ventana. Mide lo que cuesta escribir cada entrada en la sesión y vaciarla después
     * (media móvil) y llena con eso el tiempo disponible. La primera ventana es pequeña, para medir.
     */
    static final class Pacer {
        static final int FIRST_WINDOW = 512;
        static final int MIN_WINDOW = 64;
        static final int MAX_WINDOW = 65_536;
        private static final double SMOOTHING = 0.25;
        private double placeNanos = -1.0;
        private double flushNanos = -1.0;

        int entries(long availableNanos) {
            if (placeNanos < 0.0) {
                return FIRST_WINDOW;
            }
            double perEntry = placeNanos + Math.max(0.0, flushNanos);
            long fits = perEntry <= 0.0 ? MAX_WINDOW : (long) (availableNanos / perEntry);
            return (int) Math.clamp(fits, MIN_WINDOW, MAX_WINDOW);
        }

        /** Parte del tiempo para escribir; el resto queda para vaciar. */
        double placeShare() {
            if (placeNanos <= 0.0 || flushNanos < 0.0) {
                return 0.5;
            }
            return Math.clamp(placeNanos / (placeNanos + flushNanos), 0.1, 0.9);
        }

        void placed(int entries, long nanos) {
            if (entries > 0) {
                placeNanos = smooth(placeNanos, (double) nanos / entries);
            }
        }

        void flushed(int entries, long nanos) {
            if (entries > 0) {
                flushNanos = smooth(flushNanos, (double) nanos / entries);
            }
        }

        private static double smooth(double average, double sample) {
            return average < 0.0 ? sample : average + SMOOTHING * (sample - average);
        }
    }
}
