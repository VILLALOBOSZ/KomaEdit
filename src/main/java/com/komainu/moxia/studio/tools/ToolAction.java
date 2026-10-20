package com.komainu.moxia.studio.tools;

import java.util.List;
import java.util.function.Function;

/**
 * Un botón más de la tarjeta además de Construir: llevarla al pincel, estimar, guardar como preset… Manda la orden
 * que devuelve {@code command}, igual que si se escribiera.
 *
 * @param permissions nodos que necesita esta acción
 * @param needsName   pide un nombre antes (guardar un preset): {@code command} recibe los valores y la pantalla
 *                    añade el nombre con {@link ToolCard#withName}
 */
public record ToolAction(String id, List<String> permissions, boolean needsName,
                         Function<ToolValues, String> command) {
    public ToolAction {
        permissions = List.copyOf(permissions);
    }

    public String labelKey() {
        return "gui.komaedit.tools.action." + id;
    }

    public String helpKey() {
        return labelKey() + ".help";
    }
}
