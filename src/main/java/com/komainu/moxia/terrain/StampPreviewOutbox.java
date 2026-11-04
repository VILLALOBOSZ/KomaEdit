package com.komainu.moxia.terrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Las miniaturas de los sellos propios que se le deben a un jugador: las pedidas que aún se leen y las leídas que
 * esperan su turno para salir. Una miniatura pedida otra vez mientras se lee o espera no se vuelve a leer, y hay como
 * mucho {@value #MAX_PENDING} a la vez: un cliente que las pide sin parar no llena la memoria del servidor (antes cada
 * petición leía otra vez todas las que nombraba y las dejaba en una cola sin fondo). Java puro.
 *
 * @param <P> la miniatura leída
 */
public final class StampPreviewOutbox<P> {
    /** Más que todos los sellos propios que caben en el servidor ({@code komaedit.limit.stamps} aparte). */
    public static final int MAX_PENDING = 512;

    private final Set<String> owed = new HashSet<>();
    private final Deque<Ready<P>> ready = new ArrayDeque<>();

    private record Ready<P>(String hash, P preview) {
    }

    /** ¿Hay que leerla? No si ya se lee o espera, ni si ya se deben {@value #MAX_PENDING}. */
    public synchronized boolean request(String hash) {
        if (hash == null || owed.contains(hash) || owed.size() >= MAX_PENDING) {
            return false;
        }
        owed.add(hash);
        return true;
    }

    /** Ya está leída: sale en su turno. */
    public synchronized void offer(String hash, P preview) {
        if (owed.contains(hash)) {
            ready.addLast(new Ready<>(hash, preview));
        }
    }

    /** No se pudo leer (ya no existe, o falló): deja de deberse. */
    public synchronized void failed(String hash) {
        owed.remove(hash);
        ready.removeIf(entry -> entry.hash().equals(hash));
    }

    /** Las que salen en este tick: hasta gastar {@code budget} (al menos una). */
    public synchronized List<P> poll(int budget, ToIntFunction<P> cost) {
        List<P> out = new ArrayList<>();
        int spent = 0;
        while (!ready.isEmpty() && (out.isEmpty() || spent < budget)) {
            Ready<P> next = ready.pollFirst();
            owed.remove(next.hash());
            spent += cost.applyAsInt(next.preview());
            out.add(next.preview());
        }
        return out;
    }

    /** Las que se deben ahora (leyéndose o esperando). */
    public synchronized int owed() {
        return owed.size();
    }

    public synchronized boolean isEmpty() {
        return owed.isEmpty();
    }
}
