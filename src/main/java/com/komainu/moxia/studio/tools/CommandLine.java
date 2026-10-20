package com.komainu.moxia.studio.tools;

/**
 * Arma una orden de {@code /komaedit} palabra a palabra, sin la barra (como la manda el cliente con
 * {@code sendChatCommand}). Lo vacío o {@code null} no se escribe.
 */
final class CommandLine {
    private final StringBuilder text;

    CommandLine(String start) {
        text = new StringBuilder(start);
    }

    CommandLine word(String value) {
        if (value != null && !value.isBlank()) {
            text.append(' ').append(value.strip());
        }
        return this;
    }

    CommandLine flag(String flag, String value) {
        if (value != null && !value.isBlank()) {
            text.append(' ').append(flag).append(' ').append(value.strip());
        }
        return this;
    }

    CommandLine flag(String flag, boolean on) {
        if (on) {
            text.append(' ').append(flag);
        }
        return this;
    }

    /** La bandera con el número de {@code id}, solo si no es el de serie (ni automático). */
    CommandLine changed(ToolValues values, String id, String flag) {
        if (!values.isDefault(id)) {
            flag(flag, values.formatted(id));
        }
        return this;
    }

    String build() {
        return text.toString();
    }

    @Override
    public String toString() {
        return build();
    }
}
