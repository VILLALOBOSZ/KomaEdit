package com.komainu.moxia.studio.live;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Dónde cae la vista previa en vivo de cada jugador. Lo que se coloca «donde miras» (árbol, roca, texto, agua…) busca
 * el sitio cada vez que se ejecuta su orden: donde hizo clic con el Studio abierto o, si no, el centro de la pantalla.
 * Para la vista previa en vivo eso no vale: al rehacerla el jugador ya puede estar mirando a otro lado, o haber
 * cerrado el Studio (y con él se olvida el clic), y el objeto nuevo saldría en otra parte.
 *
 * <p>Por eso el Construir que abre la sesión apunta el sitio que encontró ({@link #captured}) y las peticiones
 * siguientes de esa sesión lo usan tal cual ({@link #pinned}). Una sesión nueva (otro Construir) busca de nuevo.</p>
 *
 * <p>Lógica pura: {@code S} es el sitio (en el juego, el bloque, su cara y la dimensión).</p>
 */
public final class LiveAnchors<S> {
    private final Map<UUID, Anchor<S>> anchors = new ConcurrentHashMap<>();
    private final ThreadLocal<Run<S>> current = new ThreadLocal<>();

    private record Anchor<S>(int session, S spot) {
    }

    private static final class Run<S> {
        private final UUID player;
        private final S pinned;
        private S captured;

        private Run(UUID player, S pinned) {
            this.player = player;
            this.pinned = pinned;
        }
    }

    /**
     * Ejecuta {@code action} como la petición de la sesión {@code session}: si es la primera (o la sesión no se
     * conoce), lo que resuelva «donde miras» queda apuntado; si no, «donde miras» es lo apuntado.
     */
    public <T> T run(UUID player, int session, boolean first, Supplier<T> action) {
        Anchor<S> known = anchors.get(player);
        boolean fresh = first || known == null || known.session() != session;
        Run<S> run = new Run<>(player, fresh ? null : known.spot());
        Run<S> previous = current.get();
        current.set(run);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
            if (fresh && run.captured != null) {
                anchors.put(player, new Anchor<>(session, run.captured));
            } else if (fresh) {
                anchors.remove(player);
            }
        }
    }

    /** El sitio fijado de la petición en vivo que se ejecuta ahora para el jugador, o {@code null}. */
    public S pinned(UUID player) {
        Run<S> run = current.get();
        return run != null && run.player.equals(player) ? run.pinned : null;
    }

    /** «Donde miras» acaba de resolverse en {@code spot}: si es el Construir de una sesión, se apunta. */
    public void captured(UUID player, S spot) {
        Run<S> run = current.get();
        if (run != null && run.player.equals(player) && run.pinned == null && run.captured == null && spot != null) {
            run.captured = spot;
        }
    }

    /** El sitio apuntado de la sesión del jugador, o {@code null}. */
    public S anchor(UUID player, int session) {
        Anchor<S> known = anchors.get(player);
        return known != null && known.session() == session ? known.spot() : null;
    }

    public void forget(UUID player) {
        anchors.remove(player);
    }

    public void clear() {
        anchors.clear();
    }
}
