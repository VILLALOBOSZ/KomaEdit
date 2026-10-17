package com.komainu.moxia.recipe;

import java.util.Locale;

public enum SocketPlacement {
    ENDPOINTS("endpoints", "Extremos"),
    NODES("nodes", "Cada nodo"),
    INTERVAL("interval", "Intervalos"),
    BEND("bend", "Doblar"),
    STRETCH("stretch", "Estirar");

    private final String id;
    private final String displayName;

    SocketPlacement(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Doblar y Estirar curvan el módulo por todo el recorrido en vez de pegarlo en puntos sueltos. */
    public boolean bends() {
        return this == BEND || this == STRETCH;
    }

    public static SocketPlacement byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        for (SocketPlacement placement : values()) {
            if (placement.id.equals(normalized)) {
                return placement;
            }
        }
        throw new IllegalArgumentException("Posición de socket desconocida: " + input);
    }
}
