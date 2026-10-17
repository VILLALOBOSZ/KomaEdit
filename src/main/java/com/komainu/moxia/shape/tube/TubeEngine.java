package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.ShapeOptions;

import java.util.List;
import java.util.Locale;

/**
 * Motor con el que se generan las figuras clásicas (cadena, anillos, cuentas, oscilar, red, panal, nudos y
 * espiral). El clásico estampa pinceles a lo largo de la curva; el exacto evalúa la misma figura como función
 * implícita en espacio de tubo. Se guarda como la opción numérica {@code engine} de la receta.
 */
public enum TubeEngine {
    CLASSIC("classic", "Clásico", "clasico", "stamp", "pinceles", "brush"),
    EXACT("exact", "Exacto", "exacto", "tube", "tubo", "implicit", "implicito");

    public static final String OPTION = "engine";

    private final String id;
    private final String displayName;
    private final String[] aliases;

    TubeEngine(String id, String displayName, String... aliases) {
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

    public static TubeEngine of(ShapeOptions options) {
        return options.get(OPTION, 0.0) >= 0.5 ? EXACT : CLASSIC;
    }

    public static TubeEngine byIndex(int index) {
        TubeEngine[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    public static TubeEngine byId(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")
                .replace("á", "a").replace("í", "i");
        for (TubeEngine engine : values()) {
            if (engine.id.equals(normalized) || List.of(engine.aliases).contains(normalized)) {
                return engine;
            }
        }
        throw new IllegalArgumentException("Motor desconocido: " + input + " (classic, exact)");
    }
}
