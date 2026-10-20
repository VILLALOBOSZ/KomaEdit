package com.komainu.moxia.brush.paint;

import java.text.Normalizer;
import java.util.Locale;

/** Cómo se reparte la paleta dentro de la pincelada: qué tono le toca a cada bloque. */
public enum PaintGradient {
    /** El centro de la huella, el primer tono; el borde, el último. */
    INTENSITY("huella", "por la huella", "intensity", "intensidad", "centro", "footprint"),
    /** Lo más bajo, el primer tono; lo más alto, el último. */
    HEIGHT("altura", "por altura", "height", "alto"),
    /** Lo llano, el primer tono; lo empinado, el último. */
    SLOPE("pendiente", "por pendiente", "slope", "inclinacion", "cuesta"),
    /** Manchas de ruido con la semilla del pincel. */
    NOISE("ruido", "por ruido", "noise", "manchas");

    private final String id;
    private final String description;
    private final String[] aliases;

    PaintGradient(String id, String description, String... aliases) {
        this.id = id;
        this.description = description;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    /** «por pendiente», para los mensajes. */
    public String description() {
        return description;
    }

    /** El modo con ese nombre o alias, o {@code null} si no es ninguno. */
    public static PaintGradient find(String input) {
        if (input == null) {
            return null;
        }
        String name = Normalizer.normalize(input.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        for (PaintGradient gradient : values()) {
            if (gradient.id.equals(name)) {
                return gradient;
            }
            for (String alias : gradient.aliases) {
                if (alias.equals(name)) {
                    return gradient;
                }
            }
        }
        return null;
    }

    public static PaintGradient byId(String input) {
        PaintGradient gradient = find(input);
        if (gradient == null) {
            throw new IllegalArgumentException("Degradado desconocido: " + input
                    + " (huella, altura, pendiente o ruido)");
        }
        return gradient;
    }
}
