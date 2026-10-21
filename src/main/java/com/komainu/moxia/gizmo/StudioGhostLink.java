package com.komainu.moxia.gizmo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Qué fantasma es del Studio, en el cliente. La vista previa en vivo rehace el fantasma que abrió Construir: el nuevo
 * tiene que sustituirlo y salir donde el jugador dejó el de antes, con su giro; uno que no pidió el Studio (un
 * {@code //paste} escrito en el chat) no se toca ni se rehace.
 *
 * <ul>
 *     <li>Con la red de la vista previa en vivo, el servidor dice de qué petición es cada fantasma justo antes de
 *     mandarlo ({@link #claim}): no hay que adivinar.</li>
 *     <li>Sin ella (un servidor de antes), cuenta lo que el Studio dijo que iba a pedir ({@link #expect}): cada aviso
 *     vale para un fantasma que llegue en {@value #EXPECT_MILLIS} ms. Antes era un solo aviso: si llegaban dos
 *     fantasmas seguidos del Studio, el segundo se tomaba por ajeno, salía en otro sitio y la vista en vivo se
 *     apagaba.</li>
 * </ul>
 *
 * <p>Lógica pura: {@code GhostClient} la usa tal cual y las pruebas de punta a punta también.</p>
 */
public final class StudioGhostLink {
    /** Lo que puede tardar en llegar el fantasma que pidió el Studio. */
    public static final long EXPECT_MILLIS = 20_000L;
    private static final int MAX_EXPECTED = 4;
    private static final int MAX_CLAIMS = 8;

    /** Qué es el fantasma que llega. */
    public enum Kind {
        /** El del Studio con otros ajustes: sustituye al de ahora y sale donde estaba. */
        REFRESH,
        /** Uno nuevo del Studio (su Construir). */
        STUDIO,
        /** Uno que no pidió el Studio. */
        FOREIGN
    }

    /** Lo que llega: el fantasma nuevo y, si sustituye al de antes, ese (su malla sigue a la vista hasta la nueva). */
    public record Arrival(Kind kind, GhostSession session, GhostSession previous) {
        /** Si se avisa en el chat: no al rehacerlo, que saldría una línea por cada ajuste tocado. */
        public boolean announce() {
            return kind != Kind.REFRESH;
        }
    }

    private record Claim(int ghost, boolean refresh) {
    }

    private final Deque<Claim> claims = new ArrayDeque<>();
    private int expected;
    private boolean refresh;
    private long until;
    private boolean fromStudio;

    /**
     * El Studio va a pedir un fantasma. Con {@code refresh} es la vista previa en vivo: el que llegue sustituye al de
     * ahora y sale donde el jugador lo dejó.
     */
    public void expect(boolean refresh, long now) {
        expected = Math.min(MAX_EXPECTED, (now < until ? expected : 0) + 1);
        this.refresh = refresh;
        until = now + EXPECT_MILLIS;
    }

    /** El servidor dice que el fantasma {@code ghost} es de la vista previa en vivo (con {@code refresh}: rehace). */
    public void claim(int ghost, boolean refresh) {
        claims.removeIf(claim -> claim.ghost() == ghost);
        claims.addLast(new Claim(ghost, refresh));
        while (claims.size() > MAX_CLAIMS) {
            claims.removeFirst();
        }
    }

    /**
     * Llega el fantasma {@code info}. Si es el del Studio con otros ajustes, sustituye a {@code current} y sale donde
     * el jugador dejó aquel ({@link GhostSession#follow}).
     */
    public Arrival arrive(GhostSession current, GhostSession.Info info, GhostSession.Rules rules, long now) {
        Kind kind = kind(info.id(), current != null && current.active(), now);
        GhostSession previous = kind == Kind.REFRESH ? current : null;
        GhostSession session = new GhostSession(info);
        session.follow(previous, rules);
        return new Arrival(kind, session, previous);
    }

    /** Qué es el fantasma {@code ghost}; {@code active}: hay uno a la vista que se puede mover. */
    public Kind kind(int ghost, boolean active, long now) {
        Claim claim = null;
        for (Claim candidate : claims) {
            if (candidate.ghost() == ghost) {
                claim = candidate;
            }
        }
        boolean waited = expected > 0 && now < until;
        Kind kind;
        if (claim != null) {
            claims.remove(claim);
            kind = claim.refresh() && fromStudio && active ? Kind.REFRESH : Kind.STUDIO;
        } else if (waited) {
            kind = refresh && fromStudio && active ? Kind.REFRESH : Kind.STUDIO;
        } else {
            kind = Kind.FOREIGN;
        }
        if (waited && kind != Kind.FOREIGN) {
            expected--;
        }
        if (expected == 0) {
            refresh = false;
        }
        fromStudio = kind != Kind.FOREIGN;
        return kind;
    }

    /** Si el fantasma de ahora es del Studio (la vista previa en vivo lo puede rehacer). */
    public boolean fromStudio() {
        return fromStudio;
    }

    /** El fantasma se colocó, se quitó o se cerró: ya no hay nada del Studio que rehacer. */
    public void closed() {
        fromStudio = false;
    }

    /** Fuera todo (al salir del servidor). */
    public void clear() {
        claims.clear();
        expected = 0;
        refresh = false;
        until = 0L;
        fromStudio = false;
    }
}
