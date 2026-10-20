package com.komainu.moxia.blueprint;

/** De quién es un plano: de un jugador o compartido con todo el servidor. */
public enum BlueprintScope {
    PLAYER("jugador"),
    SHARED("servidor");

    private final String id;

    BlueprintScope(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static BlueprintScope byId(String value) {
        for (BlueprintScope scope : values()) {
            if (scope.id.equalsIgnoreCase(value) || scope.name().equalsIgnoreCase(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Ámbito de plano desconocido: " + value);
    }
}
