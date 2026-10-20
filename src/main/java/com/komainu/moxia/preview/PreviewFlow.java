package com.komainu.moxia.preview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Ciclo de vida de las vistas previas, sin Minecraft: una por jugador, que espera confirmación hasta caducar.
 *
 * <ul>
 *     <li>{@link #hold}: el plan ya calculado se guarda tal cual; una vista previa anterior del mismo jugador se
 *     retira.</li>
 *     <li>{@link #confirm}: empieza a comprobar, repartido entre ticks, que la zona vigilada no ha cambiado.</li>
 *     <li>{@link #tick}: si la zona está igual aplica el mismo plan, sin recalcularlo; si cambió avisa para
 *     recalcular; si pasa el plazo sin confirmar, caduca.</li>
 * </ul>
 *
 * Cada vista previa que sale de aquí, por cualquier camino, pasa una sola vez por {@link Hooks#hide}: así no
 * quedan displays sin borrar.
 */
public final class PreviewFlow<P> {
    public interface Hooks<P> {
        /** Retira lo que se enseñó (displays, caja). Se llama una vez por vista previa. */
        void hide(UUID player, P plan);

        /** La zona sigue igual: aplicar el plan guardado. */
        void apply(UUID player, P plan);

        /** La zona cambió desde la vista previa ({@code mismatch} es la primera posición distinta, empaquetada). */
        void changed(UUID player, P plan, long mismatch);

        /** Pasó el plazo sin confirmar. */
        void expired(UUID player, P plan);
    }

    public enum Phase {
        WAITING,
        CHECKING
    }

    public record Status(Phase phase, long remainingNanos, long ttlNanos, int checked, int total) {
    }

    private static final class Entry<P> {
        private final P plan;
        private final ZoneCheck.Watch watch;
        private final long ttl;
        private final long expiresAt;
        private Phase phase = Phase.WAITING;
        private ZoneCheck check;

        private Entry(P plan, ZoneCheck.Watch watch, long now, long ttl) {
            this.plan = plan;
            this.watch = watch;
            this.ttl = ttl;
            this.expiresAt = now + ttl;
        }
    }

    private final Map<UUID, Entry<P>> entries = new LinkedHashMap<>();
    private final Hooks<P> hooks;
    private final long ttlNanos;

    public PreviewFlow(Hooks<P> hooks, long ttlNanos) {
        if (ttlNanos <= 0) {
            throw new IllegalArgumentException("El plazo de la vista previa tiene que ser positivo");
        }
        this.hooks = hooks;
        this.ttlNanos = ttlNanos;
    }

    public long ttlNanos() {
        return ttlNanos;
    }

    /** Guarda la vista previa de {@code player}; retira la anterior si la había. */
    public void hold(UUID player, P plan, ZoneCheck.Watch watch, long now) {
        hold(player, plan, watch, now, ttlNanos);
    }

    /** Igual, con un plazo propio en vez del de la clase. */
    public void hold(UUID player, P plan, ZoneCheck.Watch watch, long now, long ttl) {
        if (ttl <= 0) {
            throw new IllegalArgumentException("El plazo de la vista previa tiene que ser positivo");
        }
        Entry<P> previous = entries.put(player, new Entry<>(plan, watch, now, ttl));
        if (previous != null) {
            hooks.hide(player, previous.plan);
        }
    }

    /**
     * Empieza la confirmación.
     *
     * @return {@code false} si no había vista previa, si ya se estaba confirmando o si acaba de caducar
     */
    public boolean confirm(UUID player, long now) {
        Entry<P> entry = entries.get(player);
        if (entry == null) {
            return false;
        }
        if (entry.phase == Phase.WAITING && now >= entry.expiresAt) {
            entries.remove(player);
            hooks.hide(player, entry.plan);
            hooks.expired(player, entry.plan);
            return false;
        }
        if (entry.phase == Phase.CHECKING) {
            return false;
        }
        entry.phase = Phase.CHECKING;
        entry.check = new ZoneCheck(entry.watch);
        return true;
    }

    /** Descarta la vista previa sin aplicar nada. */
    public boolean discard(UUID player) {
        Entry<P> entry = entries.remove(player);
        if (entry == null) {
            return false;
        }
        hooks.hide(player, entry.plan);
        return true;
    }

    public boolean has(UUID player) {
        return entries.containsKey(player);
    }

    public Optional<P> plan(UUID player) {
        Entry<P> entry = entries.get(player);
        return entry == null ? Optional.empty() : Optional.of(entry.plan);
    }

    public Optional<Status> status(UUID player, long now) {
        Entry<P> entry = entries.get(player);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new Status(entry.phase, Math.max(0L, entry.expiresAt - now), entry.ttl,
                entry.check == null ? 0 : entry.check.checked(), entry.watch == null ? 0 : entry.watch.size()));
    }

    /**
     * Avanza caducidades y confirmaciones.
     *
     * @param worlds el mundo de cada jugador para comprobar la zona, o {@code null} si ya no está (se descarta)
     * @param budget comprobaciones como mucho por vista previa en este tick
     */
    public void tick(Function<UUID, ZoneCheck.World> worlds, long now, int budget) {
        if (entries.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, Entry<P>>> snapshot = new ArrayList<>(entries.entrySet());
        for (Map.Entry<UUID, Entry<P>> item : snapshot) {
            UUID player = item.getKey();
            Entry<P> entry = item.getValue();
            if (entries.get(player) != entry) {
                continue;
            }
            if (entry.phase == Phase.WAITING) {
                if (now >= entry.expiresAt) {
                    entries.remove(player);
                    hooks.hide(player, entry.plan);
                    hooks.expired(player, entry.plan);
                }
                continue;
            }
            ZoneCheck.World world = worlds.apply(player);
            if (world == null) {
                entries.remove(player);
                hooks.hide(player, entry.plan);
                continue;
            }
            if (!entry.check.step(world, budget)) {
                continue;
            }
            entries.remove(player);
            hooks.hide(player, entry.plan);
            if (entry.check.changed()) {
                hooks.changed(player, entry.plan, entry.check.mismatch());
            } else {
                hooks.apply(player, entry.plan);
            }
        }
    }

    /** Retira todas (al parar el servidor). */
    public void clear() {
        List<Map.Entry<UUID, Entry<P>>> snapshot = new ArrayList<>(entries.entrySet());
        entries.clear();
        for (Map.Entry<UUID, Entry<P>> item : snapshot) {
            hooks.hide(item.getKey(), item.getValue().plan);
        }
    }

    public int size() {
        return entries.size();
    }

    /** Las vistas previas guardadas ahora mismo. */
    public List<P> plans() {
        List<P> plans = new ArrayList<>(entries.size());
        for (Entry<P> entry : entries.values()) {
            plans.add(entry.plan);
        }
        return plans;
    }
}
