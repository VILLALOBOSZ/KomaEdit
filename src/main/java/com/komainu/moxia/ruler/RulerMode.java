package com.komainu.moxia.ruler;

import java.util.List;
import java.util.Locale;

/** Qué mide la regla: un recorrido por varios puntos, un círculo (centro y borde) o una caja (dos esquinas). */
public enum RulerMode {
    LINE("linea", List.of("line", "línea", "recorrido")),
    CIRCLE("circulo", List.of("circle", "círculo", "radio")),
    BOX("caja", List.of("box", "cuboid", "cubo"));

    private final String id;
    private final List<String> aliases;

    RulerMode(String id, List<String> aliases) {
        this.id = id;
        this.aliases = List.copyOf(aliases);
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.komaedit.ruler.mode." + id;
    }

    /** Puntos que caben: el recorrido, hasta {@link Ruler#MAX_POINTS}; el círculo y la caja, dos. */
    public int capacity() {
        return this == LINE ? Ruler.MAX_POINTS : 2;
    }

    public RulerMode next() {
        RulerMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** El modo por su nombre en español, en inglés o un alias; {@code null} si no es ninguno. */
    public static RulerMode byName(String value) {
        String key = value.strip().toLowerCase(Locale.ROOT);
        for (RulerMode mode : values()) {
            if (mode.id.equals(key) || mode.name().equalsIgnoreCase(key) || mode.aliases.contains(key)) {
                return mode;
            }
        }
        return null;
    }
}
