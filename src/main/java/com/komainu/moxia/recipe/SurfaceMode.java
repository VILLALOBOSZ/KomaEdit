package com.komainu.moxia.recipe;

import java.util.Locale;

public enum SurfaceMode {
    NONE("none", "Libre"),
    FLOOR("floor", "Suelo"),
    CEILING("ceiling", "Techo"),
    NEAREST("nearest", "Superficie cercana");

    private final String id;
    private final String displayName;

    SurfaceMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static SurfaceMode byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        for (SurfaceMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Modo de superficie desconocido: " + input);
    }
}
