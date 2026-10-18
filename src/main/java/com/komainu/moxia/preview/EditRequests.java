package com.komainu.moxia.preview;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Lleva la {@link EditRequest} de una orden hasta el momento de aplicar su plan, que puede llegar muchos ticks
 * después y desde otro hilo:
 *
 * <ol>
 *     <li>Mientras se ejecuta la orden (o la confirmación, o el clic de una herramienta) la petición está en
 *     {@link #current()}, solo para ese hilo.</li>
 *     <li>{@code RegionOperationService} y {@code AsyncEngine} la recogen al empezar y la devuelven al terminar:
 *     la captura la restaura con {@link #within}, y el worker la deja en un relevo por jugador
 *     ({@link #handOff}) justo antes de su resultado.</li>
 *     <li>Si una cadena la pierde por el camino, la última orden con {@code -v} queda armada unos minutos y se usa
 *     una sola vez.</li>
 * </ol>
 *
 * {@link #resolve} la consume en ese orden: hilo, relevo, orden armada; sin nada, {@link EditRequest#DEFAULT}.
 */
public final class EditRequests {
    static final long HANDOFF_NANOS = 10_000_000_000L;
    static final long ARMED_NANOS = 180_000_000_000L;
    private static final ThreadLocal<EditRequest> CURRENT = new ThreadLocal<>();
    private static final Map<UUID, Stamped> HANDOFF = new ConcurrentHashMap<>();
    private static final Map<UUID, Stamped> ARMED = new ConcurrentHashMap<>();
    static volatile LongSupplier clock = System::nanoTime;

    private EditRequests() {
    }

    private record Stamped(EditRequest request, long at) {
    }

    @FunctionalInterface
    public interface CommandBody {
        int run() throws Exception;
    }

    /** La petición del hilo actual, o {@code null} si no hay ninguna activa. */
    public static EditRequest current() {
        return CURRENT.get();
    }

    /** Ejecuta {@code action} con {@code request} como petición del hilo (una petición ya activa se respeta). */
    public static <T> T within(EditRequest request, Supplier<T> action) {
        EditRequest previous = CURRENT.get();
        if (request == null || previous != null) {
            return action.get();
        }
        CURRENT.set(request);
        try {
            return action.get();
        } finally {
            CURRENT.remove();
        }
    }

    public static void within(EditRequest request, Runnable action) {
        within(request, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Como {@link #within}, para la ejecución de una orden: además deja armada la petición con vista previa (o
     * desarma la anterior) por si la operación la pierde por el camino.
     */
    public static int withinCommand(EditRequest request, UUID player, CommandBody body) throws Exception {
        if (CURRENT.get() != null) {
            return body.run();
        }
        if (player != null) {
            if (request.preview()) {
                ARMED.put(player, new Stamped(request, clock.getAsLong()));
            } else {
                ARMED.remove(player);
            }
        }
        CURRENT.set(request);
        try {
            return body.run();
        } finally {
            CURRENT.remove();
        }
    }

    /** El worker de {@code owner} termina: su petición queda a la espera del plan que va a encolar. */
    public static void handOff(UUID owner, EditRequest request) {
        if (owner != null && request != null) {
            HANDOFF.put(owner, new Stamped(request, clock.getAsLong()));
        }
    }

    /** Consume la petición que corresponde a un plan de {@code player} (ver la clase). */
    public static EditRequest resolve(UUID player) {
        return find(player, true);
    }

    /** Igual que {@link #resolve}, sin consumir nada. */
    public static EditRequest peek(UUID player) {
        return find(player, false);
    }

    public static void forget(UUID player) {
        HANDOFF.remove(player);
        ARMED.remove(player);
    }

    public static void clearAll() {
        HANDOFF.clear();
        ARMED.clear();
    }

    private static EditRequest find(UUID player, boolean consume) {
        long now = clock.getAsLong();
        EditRequest request = CURRENT.get();
        if (request == null && player != null) {
            Stamped handed = consume ? HANDOFF.remove(player) : HANDOFF.get(player);
            if (handed != null && now - handed.at() <= HANDOFF_NANOS) {
                request = handed.request();
            }
        }
        if (player != null) {
            Stamped armed = ARMED.get(player);
            if (armed != null && now - armed.at() > ARMED_NANOS) {
                ARMED.remove(player, armed);
                armed = null;
            }
            if (request == null && armed != null) {
                request = armed.request();
            }
            // La petición armada se usa una sola vez, llegue por donde llegue.
            if (consume && armed != null && armed.request() == request) {
                ARMED.remove(player, armed);
            }
        }
        return request == null ? EditRequest.DEFAULT : request;
    }
}
