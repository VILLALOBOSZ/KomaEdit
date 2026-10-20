package com.komainu.moxia.nature;

import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.persistence.ProjectStorage;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Presets de árboles y rocas guardados por jugador y mundo ({@code komaedit/players/<uuid>/nature}), como los ruidos
 * guardados. Cada preset es el texto de los ajustes; se usa con {@code #nombre} y lo que se escriba después manda.
 */
public final class NaturePresets {
    private NaturePresets() {
    }

    public static NamedDefinitions trees(UUID playerId) {
        return new NamedDefinitions(ProjectStorage.namespace(playerId, "nature").resolve("trees.json"),
                "preset de árbol");
    }

    public static NamedDefinitions rocks(UUID playerId) {
        return new NamedDefinitions(ProjectStorage.namespace(playerId, "nature").resolve("rocks.json"),
                "preset de roca");
    }

    /** Busca un preset; {@code null} si no existe o no hay proyecto abierto. */
    public static Function<String, String> resolver(Supplier<NamedDefinitions> store) {
        return name -> {
            try {
                return store.get().get(name).orElse(null);
            } catch (RuntimeException exception) {
                return null;
            }
        };
    }

    /** Ajustes de árbol aceptando los presets del jugador. */
    public static TreeSettings tree(UUID playerId, String input) {
        return TreeSettings.parse(input, resolver(() -> trees(playerId)));
    }

    /** Ajustes de roca aceptando los presets del jugador. */
    public static RockSettings rock(UUID playerId, String input) {
        return RockSettings.parse(input, resolver(() -> rocks(playerId)));
    }
}
