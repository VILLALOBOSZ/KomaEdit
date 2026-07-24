package com.komainu.moxia.recipe;

import java.util.Locale;

public enum SocketPlacement {
    ENDPOINTS("endpoints", "Extremos"),
    NODES("nodes", "Cada nodo"),
    INTERVAL("interval", "Intervalos");

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
