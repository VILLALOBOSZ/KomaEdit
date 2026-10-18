package com.komainu.moxia.image;

import java.util.List;
import java.util.Locale;

/** Cómo se reparte el error de color al elegir los bloques. */
public enum Dithering {
    /** Cada píxel, el bloque más parecido: zonas planas y bordes limpios (pixel art, logos). */
    NONE("none", "sin tramado", List.of("ninguno", "no", "off", "nada")),
    /** Floyd–Steinberg en serpentina: el error pasa a los vecinos; lo mejor para fotos. */
    FLOYD_STEINBERG("fs", "Floyd–Steinberg", List.of("floyd", "floydsteinberg", "floyd-steinberg", "difusion")),
    /**
     * Matriz de Bayer de 8×8 sobre una mezcla de Knoll de 16 bloques por color: trama regular y estable, sin los
     * «gusanos» de la difusión de error.
     */
    BAYER("bayer", "ordenado (Bayer)", List.of("ordenado", "ordered", "matriz"));

    private final String id;
    private final String displayName;
    private final List<String> aliases;

    Dithering(String id, String displayName, List<String> aliases) {
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

    public static Dithering parse(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("ó", "o").replace("–", "-");
        for (Dithering dithering : values()) {
            if (dithering.id.equals(key) || dithering.aliases.contains(key)) {
                return dithering;
            }
        }
        throw new IllegalArgumentException("Tramado desconocido: " + name + " (none, fs o bayer)");
    }
}
