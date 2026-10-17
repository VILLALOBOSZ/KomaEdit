package com.komainu.moxia.shape.smooth;

import java.util.List;
import java.util.Locale;

/** Qué piezas pueden sustituir a los bloques de superficie de una figura de tubo. */
public enum SmoothMode {
    NONE("none", "Sin piezas", "No", "off", "no", "ninguno", "nada", "full", "bloques"),
    SLABS("slabs", "Losas", "Losas", "slab", "losa", "losas"),
    STAIRS("stairs", "Losas y escaleras", "Escaleras", "slabsandstairs", "slabsstairs", "sas", "both", "escaleras",
            "escalera", "losasyescaleras", "ambas");

    private final String id;
    private final String displayName;
    private final String shortName;
    private final String[] aliases;

    SmoothMode(String id, String displayName, String shortName, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Para controles estrechos como las filas del Studio. */
    public String shortName() {
        return shortName;
    }

    public static SmoothMode byIndex(int index) {
        SmoothMode[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    public static SmoothMode byId(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (SmoothMode mode : values()) {
            if (mode.id.equals(normalized) || List.of(mode.aliases).contains(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Modo de piezas desconocido: " + input + " (none, slabs, stairs)");
    }
}
