package com.komainu.moxia.studio.tools;

import java.util.List;

/**
 * Algo que impide construir con la tarjeta o que conviene avisar. La pantalla lo traduce con
 * {@code gui.komaedit.tools.problem.<id>} y sus argumentos.
 *
 * @param blocking si el botón Construir queda apagado (un aviso deja construir: el servidor decide)
 */
public record ToolProblem(String id, List<Object> arguments, boolean blocking) {
    public ToolProblem {
        arguments = List.copyOf(arguments);
    }

    public static ToolProblem error(String id, Object... arguments) {
        return new ToolProblem(id, List.of(arguments), true);
    }

    public static ToolProblem warning(String id, Object... arguments) {
        return new ToolProblem(id, List.of(arguments), false);
    }

    public String translationKey() {
        return "gui.komaedit.tools.problem." + id;
    }
}
