package com.komainu.moxia.brush.superwand;

/** Modos de la supervarita; Mayús + clic izquierdo pasa al siguiente. */
public enum SuperwandMode {
    /** El bloque que miras. */
    TRACE("trace", "Apuntar", "apuntar", "mirar"),
    /** El bloque en el que estás. */
    POSITION("position", "Posición", "posicion", "pies"),
    /** A partir del último punto, en la dirección en la que miras. */
    RELATIVE("relative", "Relativo", "relativo"),
    /** Agarra un punto o la selección entera y la lleva contigo. */
    GRAB("grab", "Agarrar", "agarrar", "mover");

    private final String id;
    private final String displayName;
    private final String[] aliases;

    SuperwandMode(String id, String displayName, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean selectsPoints() {
        return this != GRAB;
    }

    public SuperwandMode next() {
        SuperwandMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static SuperwandMode byId(String input) {
        String name = input.trim().toLowerCase(java.util.Locale.ROOT);
        for (SuperwandMode mode : values()) {
            if (mode.id.equals(name) || mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
            for (String alias : mode.aliases) {
                if (alias.equals(name)) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("Modo de supervarita desconocido: " + input
                + " (trace, position, relative, grab)");
    }
}
