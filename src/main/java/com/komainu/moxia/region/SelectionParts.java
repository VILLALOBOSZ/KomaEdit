package com.komainu.moxia.region;

import com.komainu.moxia.edit.BlockBounds;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trozos propuestos para una selección que pasaba del límite: {@code /komaedit trozo <n>} selecciona cada uno sin
 * volver a medir la selección original (que ya habrá cambiado al seleccionar el primero). Se guarda la última
 * propuesta de cada jugador.
 */
public final class SelectionParts {
    private static final Map<UUID, Proposal> PROPOSALS = new ConcurrentHashMap<>();

    private SelectionParts() {
    }

    /**
     * @param world   mundo de la selección (su clave de registro)
     * @param subject la orden que no cupo, para el aviso
     */
    public record Proposal(String world, String subject, List<BlockBounds> parts, long createdAt) {
        public Proposal {
            parts = List.copyOf(parts);
        }
    }

    public static void propose(UUID player, String world, String subject, List<BlockBounds> parts) {
        PROPOSALS.put(player, new Proposal(world, subject, parts, System.nanoTime()));
    }

    public static Optional<Proposal> get(UUID player) {
        return Optional.ofNullable(PROPOSALS.get(player));
    }

    /** La propuesta si se acaba de hacer (para añadir los botones al aviso). */
    public static Optional<Proposal> fresh(UUID player, long withinNanos) {
        return get(player).filter(proposal -> System.nanoTime() - proposal.createdAt() <= withinNanos);
    }

    public static void forget(UUID player) {
        PROPOSALS.remove(player);
    }

    public static void clearAll() {
        PROPOSALS.clear();
    }
}
