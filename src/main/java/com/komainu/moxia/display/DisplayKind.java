package com.komainu.moxia.display;

import java.util.Locale;

/** Los tres tipos de display de Minecraft. */
public enum DisplayKind {
    BLOCK("block", "bloque"),
    ITEM("item", "objeto"),
    TEXT("text", "texto");

    private final String id;
    private final String spanish;

    DisplayKind(String id, String spanish) {
        this.id = id;
        this.spanish = spanish;
    }

    public String id() {
        return id;
    }

    /** El nombre en español de la orden ({@code bloque}, {@code objeto}, {@code texto}). */
    public String spanish() {
        return spanish;
    }

    public static DisplayKind of(int ordinal) {
        DisplayKind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BLOCK;
    }

    public static DisplayKind parse(String text) {
        String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        for (DisplayKind kind : values()) {
            if (kind.id.equals(value) || kind.spanish.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Tipo de display desconocido: " + text + " (block, item o text)");
    }
}
