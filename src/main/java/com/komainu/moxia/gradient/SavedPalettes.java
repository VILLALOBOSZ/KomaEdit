package com.komainu.moxia.gradient;

import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.persistence.ProjectStorage;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Paletas guardadas por cada jugador en el proyecto del mundo ({@code komaedit/players/<uuid>/palettes}). Se guardan ya
 * desplegadas ({@code a;b;c}), así que borrar o cambiar otra paleta no rompe las que se hicieron a partir de ella.
 */
public final class SavedPalettes {
    private SavedPalettes() {
    }

    public static NamedDefinitions store(UUID playerId) {
        return new NamedDefinitions(ProjectStorage.namespace(playerId, "palettes").resolve("palettes.json"), "paleta");
    }

    /** El gancho {@code #nombre} de {@link PaletteSpec}; devuelve {@code null} si no existe o no hay proyecto abierto. */
    public static Function<String, String> resolver(UUID playerId) {
        return name -> {
            try {
                return store(playerId).get(name).orElse(null);
            } catch (RuntimeException exception) {
                return null;
            }
        };
    }

    /** Lee una paleta aceptando las paletas guardadas del jugador. */
    public static List<String> parse(UUID playerId, String input) {
        return PaletteSpec.parse(input, resolver(playerId));
    }
}
