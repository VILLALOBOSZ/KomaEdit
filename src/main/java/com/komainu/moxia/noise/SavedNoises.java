package com.komainu.moxia.noise;

import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.persistence.ProjectStorage;

import java.util.UUID;
import java.util.function.Function;

/** Ruidos guardados por cada jugador en el proyecto del mundo ({@code komaedit/players/<uuid>/noises}). */
public final class SavedNoises {
    private SavedNoises() {
    }

    public static NamedDefinitions store(UUID playerId) {
        return new NamedDefinitions(ProjectStorage.namespace(playerId, "noises").resolve("noises.json"), "ruido");
    }

    /** Busca un ruido guardado; devuelve {@code null} si no existe o no hay proyecto abierto. */
    public static Function<String, String> resolver(UUID playerId) {
        return name -> {
            try {
                return store(playerId).get(name).orElse(null);
            } catch (RuntimeException exception) {
                return null;
            }
        };
    }

    /** Lee un ruido aceptando los ruidos de serie y los guardados del jugador. */
    public static NoiseSpec parse(UUID playerId, String input) {
        return NoiseSpec.parse(input, resolver(playerId));
    }
}
