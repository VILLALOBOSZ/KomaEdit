package com.komainu.moxia.worldedit.ez;

import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.persistence.ProjectStorage;

import java.util.UUID;

/** Selecciones guardadas por cada jugador en el proyecto del mundo ({@code komaedit/players/<uuid>/selections}). */
public final class SavedSelections {
    private SavedSelections() {
    }

    public static NamedDefinitions store(UUID playerId) {
        return new NamedDefinitions(ProjectStorage.namespace(playerId, "selections").resolve("selections.json"),
                "selección");
    }

    public static SelectionShape load(UUID playerId, String name) {
        String json = store(playerId).get(name)
                .orElseThrow(() -> new IllegalArgumentException("No tienes ninguna selección guardada llamada "
                        + NamedDefinitions.normalizeName(name)));
        return SelectionShape.fromJson(json);
    }

    public static boolean save(UUID playerId, String name, SelectionShape shape, boolean overwrite) {
        return store(playerId).put(name, shape.toJson().toString(), overwrite);
    }
}
