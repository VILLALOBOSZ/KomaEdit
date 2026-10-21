package com.komainu.moxia.display;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Los displays elegidos por un jugador, en el servidor: en orden y con el activo (el último pulsado, sobre el que gira
 * el gizmo). Elegir uno de un grupo elige el grupo entero, salvo que se pida solo ese. Lógica pura.
 */
public final class DisplaySelection {
    /** Cómo se suma un clic a lo elegido. */
    public enum Mode {
        /** Solo esto. */
        REPLACE,
        /** Añadir (Mayús+clic); si ya estaba, quitarlo. */
        TOGGLE,
        /** Añadir siempre. */
        ADD
    }

    private final LinkedHashSet<UUID> selected = new LinkedHashSet<>();
    private UUID primary;

    public List<UUID> ids() {
        return List.copyOf(selected);
    }

    public UUID primary() {
        return primary;
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public int size() {
        return selected.size();
    }

    public boolean contains(UUID id) {
        return selected.contains(id);
    }

    /**
     * Elige {@code clicked} con los de su grupo ({@code members}, que lo incluye; solo él si no tiene grupo).
     *
     * @return si cambió algo
     */
    public boolean pick(UUID clicked, Collection<UUID> members, Mode mode) {
        List<UUID> group = new ArrayList<>(members);
        if (!group.contains(clicked)) {
            group.add(clicked);
        }
        List<UUID> before = ids();
        UUID previous = primary;
        switch (mode) {
            case REPLACE -> {
                selected.clear();
                selected.addAll(group);
                primary = clicked;
            }
            case ADD -> {
                selected.addAll(group);
                primary = clicked;
            }
            case TOGGLE -> {
                if (selected.contains(clicked)) {
                    group.forEach(selected::remove);
                    if (group.contains(primary)) {
                        primary = selected.isEmpty() ? null : last();
                    }
                } else {
                    selected.addAll(group);
                    primary = clicked;
                }
            }
        }
        return !before.equals(ids()) || previous != primary;
    }

    /** Sustituye todo (al duplicar, se eligen las copias). */
    public void set(Collection<UUID> ids, UUID primaryId) {
        selected.clear();
        selected.addAll(ids);
        primary = primaryId != null && selected.contains(primaryId) ? primaryId : selected.isEmpty() ? null : last();
    }

    /** Quita los que ya no existen o no se pueden tocar. */
    public void retain(Collection<UUID> alive) {
        selected.retainAll(alive);
        if (primary != null && !selected.contains(primary)) {
            primary = selected.isEmpty() ? null : last();
        }
    }

    public void remove(UUID id) {
        selected.remove(id);
        if (id.equals(primary)) {
            primary = selected.isEmpty() ? null : last();
        }
    }

    public void clear() {
        selected.clear();
        primary = null;
    }

    private UUID last() {
        UUID last = null;
        for (UUID id : selected) {
            last = id;
        }
        return last;
    }
}
