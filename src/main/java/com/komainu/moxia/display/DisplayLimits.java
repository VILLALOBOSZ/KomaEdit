package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;

import java.util.Optional;

/**
 * Lo que el servidor deja hacer con los displays ({@code config/komaedit/displays.json}). Lógica pura: dice si una
 * pose, un número de displays o un texto pasan, con el motivo si no.
 *
 * @param maxPerChunk       displays de cualquiera en un chunk (contando los de vanilla y otros mods)
 * @param maxPerPlayer      displays creados por un jugador con KomaEdit, en todo el servidor
 * @param maxDistance       hasta dónde del jugador se puede crear, elegir o llevar un display, en bloques
 * @param maxScale          escala máxima de un eje
 * @param maxOffset         lo más lejos que puede quedar el modelo de su entidad (la traslación), en bloques
 * @param maxSelection      displays elegidos a la vez (un grupo cuenta entero)
 * @param maxTextLength     caracteres de un texto
 * @param maxEditsPerSecond paquetes de edición por segundo y jugador (los de más se ignoran)
 */
public record DisplayLimits(int maxPerChunk, int maxPerPlayer, double maxDistance, double maxScale, double maxOffset,
                            int maxSelection, int maxTextLength, int maxEditsPerSecond) {
    public static final DisplayLimits DEFAULT = new DisplayLimits(64, 2_000, 48.0, 16.0, 8.0, 256, 256, 20);

    public DisplayLimits {
        if (maxPerChunk < 1 || maxPerChunk > 4_096) {
            throw new IllegalArgumentException("maxPerChunk must be between 1 and 4096");
        }
        if (maxPerPlayer < 0 || maxPerPlayer > 1_000_000) {
            throw new IllegalArgumentException("maxPerPlayer must be between 0 and 1000000 (0: no limit)");
        }
        if (!(maxDistance >= 4.0 && maxDistance <= 512.0)) {
            throw new IllegalArgumentException("maxDistance must be between 4 and 512");
        }
        if (!(maxScale >= 1.0 && maxScale <= 256.0)) {
            throw new IllegalArgumentException("maxScale must be between 1 and 256");
        }
        if (!(maxOffset >= 1.0 && maxOffset <= 256.0)) {
            throw new IllegalArgumentException("maxOffset must be between 1 and 256");
        }
        if (maxSelection < 1 || maxSelection > 4_096) {
            throw new IllegalArgumentException("maxSelection must be between 1 and 4096");
        }
        if (maxTextLength < 1 || maxTextLength > 4_096) {
            throw new IllegalArgumentException("maxTextLength must be between 1 and 4096");
        }
        if (maxEditsPerSecond < 1 || maxEditsPerSecond > 200) {
            throw new IllegalArgumentException("maxEditsPerSecond must be between 1 and 200");
        }
    }

    /**
     * Problema de una pose que quiere poner el jugador, o vacío si vale: números raros, escala, traslación y distancia
     * del jugador a la entidad y al centro del modelo.
     *
     * @param center el centro del modelo en el mundo con esa pose
     */
    public Optional<String> checkPose(DisplayPose pose, Vec3 center, Vec3 player) {
        if (!pose.finite() || !finite(center)) {
            return Optional.of("valores no válidos");
        }
        Vec3 scale = pose.scale();
        double smallest = Math.min(scale.x(), Math.min(scale.y(), scale.z()));
        double largest = Math.max(scale.x(), Math.max(scale.y(), scale.z()));
        if (smallest < DisplayDrag.MIN_SCALE - 1.0E-9) {
            return Optional.of("escala demasiado pequeña (como poco 1/64)");
        }
        if (largest > maxScale + 1.0E-9) {
            return Optional.of("escala de más de " + format(maxScale));
        }
        if (pose.translation().length() > maxOffset + 1.0E-9) {
            return Optional.of("el modelo queda a más de " + format(maxOffset) + " bloques de su entidad");
        }
        if (pose.position().distance(player) > maxDistance || center.distance(player) > maxDistance) {
            return Optional.of("a más de " + format(maxDistance) + " bloques de ti");
        }
        return Optional.empty();
    }

    /** Si cabe uno más en el chunk que ya tiene {@code inChunk}. */
    public Optional<String> checkChunk(int inChunk, int adding) {
        return inChunk + adding > maxPerChunk
                ? Optional.of("ese chunk ya tiene " + inChunk + " displays (como mucho " + maxPerChunk + ")")
                : Optional.empty();
    }

    /** Si el jugador puede crear {@code adding} más; {@code maxPerPlayer} 0 es sin límite. */
    public Optional<String> checkPlayer(int owned, int adding) {
        return maxPerPlayer > 0 && owned + adding > maxPerPlayer
                ? Optional.of("ya tienes " + owned + " displays (como mucho " + maxPerPlayer + ")")
                : Optional.empty();
    }

    public Optional<String> checkSelection(int size) {
        return size > maxSelection ? Optional.of("como mucho " + maxSelection + " displays a la vez")
                : Optional.empty();
    }

    public Optional<String> checkText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.of("el texto está vacío");
        }
        return text.length() > maxTextLength ? Optional.of("el texto tiene más de " + maxTextLength + " caracteres")
                : Optional.empty();
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.valueOf(value);
    }
}
