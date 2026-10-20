package com.komainu.moxia.image;

import java.util.List;
import java.util.Locale;

/**
 * Qué punto de la obra cae en el bloque elegido (o, ajustada a la selección, hacia dónde se arrima si sobra sitio).
 * {@code horizontal}: 0 izquierda, 0,5 centro, 1 derecha; {@code vertical}: 0 abajo, 0,5 centro, 1 arriba.
 */
public enum Anchor {
    CENTER("center", 0.5, 0.5, List.of("centro", "c", "middle", "medio")),
    BOTTOM("bottom", 0.5, 0.0, List.of("abajo", "b", "base")),
    TOP("top", 0.5, 1.0, List.of("arriba", "t")),
    LEFT("left", 0.0, 0.5, List.of("izquierda", "l")),
    RIGHT("right", 1.0, 0.5, List.of("derecha", "r")),
    BOTTOM_LEFT("bottomleft", 0.0, 0.0, List.of("abajoizquierda", "bl")),
    BOTTOM_RIGHT("bottomright", 1.0, 0.0, List.of("abajoderecha", "br")),
    TOP_LEFT("topleft", 0.0, 1.0, List.of("arribaizquierda", "tl")),
    TOP_RIGHT("topright", 1.0, 1.0, List.of("arribaderecha", "tr"));

    private final String id;
    private final double horizontal;
    private final double vertical;
    private final List<String> aliases;

    Anchor(String id, double horizontal, double vertical, List<String> aliases) {
        this.id = id;
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public double horizontal() {
        return horizontal;
    }

    public double vertical() {
        return vertical;
    }

    public static Anchor parse(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        for (Anchor anchor : values()) {
            if (anchor.id.equals(key) || anchor.aliases.contains(key)) {
                return anchor;
            }
        }
        throw new IllegalArgumentException("Anclaje desconocido: " + name
                + " (center, bottom, top, left, right, bottomleft, bottomright, topleft o topright)");
    }
}
