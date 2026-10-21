package com.komainu.moxia.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Una zona que hace de selección mientras se ejecuta una orden, solo en este hilo y solo para un jugador: es lo que
 * deja usar cualquier orden de la selección como pincel ({@code /komaedit pincel zona}). Cada clic del pincel
 * ejecuta su orden con la zona alrededor del bloque pulsado y {@link WorldEditService#selection} la devuelve en vez
 * de la selección de WorldEdit, que no se toca. Las operaciones copian la región al empezar, así que lo que siga en
 * otros hilos o ticks ya tiene la suya.
 */
public final class SelectionOverride {
    private static final ThreadLocal<Zone> CURRENT = new ThreadLocal<>();

    /**
     * @param anchor el bloque pulsado: lo que en la selección sería «lo alto» (el nivel de Inundar) es aquí el clic
     */
    public record Zone(UUID player, Region region, BlockVector3 anchor) {
    }

    private SelectionOverride() {
    }

    /** Ejecuta {@code action} con {@code region} como selección de {@code player}; al acabar vuelve la de antes. */
    public static <T> T within(UUID player, Region region, BlockVector3 anchor, Supplier<T> action) {
        Zone previous = CURRENT.get();
        CURRENT.set(new Zone(player, region.clone(), anchor));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** La zona de {@code player} en este hilo, si una orden se está ejecutando como pincel. */
    public static Optional<Zone> current(UUID player) {
        Zone zone = CURRENT.get();
        return zone != null && zone.player().equals(player) ? Optional.of(zone) : Optional.empty();
    }
}
