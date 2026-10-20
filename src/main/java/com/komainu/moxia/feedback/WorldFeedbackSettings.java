package com.komainu.moxia.feedback;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/**
 * Ajustes de lo que KomaEdit dibuja y suena en el mundo, por jugador y en su propio equipo
 * ({@code config/komaedit-client.json}). Lógica pura: el cliente los lee, los guarda y los enseña en su pantalla.
 * Un archivo roto o a medias no impide jugar: lo que falte o no se entienda toma el valor de serie.
 *
 * @param previewStyle   cómo se ve la vista previa: bloques translúcidos con contorno o solo el contorno
 * @param previewOpacity opacidad de los bloques de la vista previa, de 0.15 a 0.9
 * @param fadeNear       atenuar la vista previa al acercarse, para ver lo que hay detrás
 * @param selection      contorno de la selección de WorldEdit (de serie apagado si está WorldEditCUI)
 * @param dimensions     medidas (ancho×alto×largo) en la selección y en la caja de la vista previa
 * @param pathNumbers    números en los puntos de la trayectoria
 * @param brushReticle   radio del pincel en la superficie que se mira
 * @param symmetryGuides planos y eje de la simetría
 * @param sounds         sonidos al confirmar, al terminar una edición larga y al fallar
 * @param soundVolume    volumen de esos sonidos, de 0 a 1
 */
public record WorldFeedbackSettings(
        PreviewStyle previewStyle,
        double previewOpacity,
        boolean fadeNear,
        boolean selection,
        boolean dimensions,
        boolean pathNumbers,
        boolean brushReticle,
        boolean symmetryGuides,
        boolean sounds,
        double soundVolume
) {
    public static final int FORMAT = 1;
    public static final double MIN_OPACITY = 0.15;
    public static final double MAX_OPACITY = 0.9;

    public enum PreviewStyle {
        /** El bloque que quedará, translúcido, con el contorno de la figura. */
        GHOST("ghost"),
        /** Solo el contorno: no tapa nada. */
        OUTLINE("outline");

        private final String id;

        PreviewStyle(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static PreviewStyle byId(String id, PreviewStyle fallback) {
            for (PreviewStyle style : values()) {
                if (style.id.equalsIgnoreCase(id)) {
                    return style;
                }
            }
            return fallback;
        }
    }

    public WorldFeedbackSettings {
        previewStyle = previewStyle == null ? PreviewStyle.GHOST : previewStyle;
        previewOpacity = clamp(previewOpacity, MIN_OPACITY, MAX_OPACITY, 0.5);
        soundVolume = clamp(soundVolume, 0.0, 1.0, 0.35);
    }

    /** Los de serie: discretos. Sin WorldEditCUI, con contorno de la selección. */
    public static WorldFeedbackSettings defaults(boolean worldEditCui) {
        return new WorldFeedbackSettings(PreviewStyle.GHOST, 0.5, true, !worldEditCui, true, true, true, true,
                true, 0.35);
    }

    public WorldFeedbackSettings withPreviewStyle(PreviewStyle value) {
        return new WorldFeedbackSettings(value, previewOpacity, fadeNear, selection, dimensions, pathNumbers,
                brushReticle, symmetryGuides, sounds, soundVolume);
    }

    public WorldFeedbackSettings withPreviewOpacity(double value) {
        return new WorldFeedbackSettings(previewStyle, value, fadeNear, selection, dimensions, pathNumbers,
                brushReticle, symmetryGuides, sounds, soundVolume);
    }

    public WorldFeedbackSettings withFadeNear(boolean value) {
        return new WorldFeedbackSettings(previewStyle, previewOpacity, value, selection, dimensions, pathNumbers,
                brushReticle, symmetryGuides, sounds, soundVolume);
    }

    public WorldFeedbackSettings withSelection(boolean value) {
        return new WorldFeedbackSettings(previewStyle, previewOpacity, fadeNear, value, dimensions, pathNumbers,
                brushReticle, symmetryGuides, sounds, soundVolume);
    }

    public WorldFeedbackSettings withDimensions(boolean value) {
        return new WorldFeedbackSettings(previewStyle, previewOpacity, fadeNear, selection, value, pathNumbers,
                brushReticle, symmetryGuides, sounds, soundVolume);
    }

    public WorldFeedbackSettings withPathNumbers(boolean value) {
        return new WorldFeedbackSettings(previewStyle, previewOpacity, fadeNear, selection, dimensions, value,
                brushReticle, symmetryGuides, sounds, soundVolume);
    }

    public WorldFeedbackSettings withBrushReticle(boolean value) {
        return new WorldFeedbackSettings(previewStyle, previewOpacity, fadeNear, selection, dimensions, pathNumbers,
                value, symmetryGuides, sounds, soundVolume);
    }

    public WorldFeedbackSettings withSymmetryGuides(boolean value) {
        return new WorldFeedbackSettings(previewStyle, previewOpacity, fadeNear, selection, dimensions, pathNumbers,
                brushReticle, value, sounds, soundVolume);
    }

    public WorldFeedbackSettings withSounds(boolean value) {
        return new WorldFeedbackSettings(previewStyle, previewOpacity, fadeNear, selection, dimensions, pathNumbers,
                brushReticle, symmetryGuides, value, soundVolume);
    }

    public WorldFeedbackSettings withSoundVolume(double value) {
        return new WorldFeedbackSettings(previewStyle, previewOpacity, fadeNear, selection, dimensions, pathNumbers,
                brushReticle, symmetryGuides, sounds, value);
    }

    /**
     * Opacidad de la vista previa a {@code distance} bloques de la cámara: con {@link #fadeNear}, baja al acercarse
     * (a la cuarta parte encima de los bloques) para no tapar lo que hay detrás.
     */
    public double opacityAt(double distance) {
        if (!fadeNear) {
            return previewOpacity;
        }
        double near = 1.5;
        double far = 7.0;
        double amount = Math.clamp((distance - near) / (far - near), 0.0, 1.0);
        double smooth = amount * amount * (3.0 - 2.0 * amount);
        return previewOpacity * (0.25 + 0.75 * smooth);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonObject preview = new JsonObject();
        preview.addProperty("style", previewStyle.id());
        preview.addProperty("opacity", round(previewOpacity));
        preview.addProperty("fadeNear", fadeNear);
        root.add("preview", preview);
        root.addProperty("selectionOutline", selection);
        root.addProperty("dimensions", dimensions);
        root.addProperty("pathNumbers", pathNumbers);
        root.addProperty("brushReticle", brushReticle);
        root.addProperty("symmetryGuides", symmetryGuides);
        JsonObject sound = new JsonObject();
        sound.addProperty("enabled", sounds);
        sound.addProperty("volume", round(soundVolume));
        root.add("sounds", sound);
        return root.toString();
    }

    /** Lee el archivo; lo que falte o no se entienda toma el valor de {@code defaults}. */
    public static WorldFeedbackSettings parse(String json, WorldFeedbackSettings defaults) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json == null ? "" : json);
            if (!element.isJsonObject()) {
                return defaults;
            }
            root = element.getAsJsonObject();
        } catch (RuntimeException exception) {
            return defaults;
        }
        JsonObject preview = object(root, "preview");
        JsonObject sound = object(root, "sounds");
        return new WorldFeedbackSettings(
                PreviewStyle.byId(string(preview, "style", defaults.previewStyle().id()), defaults.previewStyle()),
                number(preview, "opacity", defaults.previewOpacity()),
                bool(preview, "fadeNear", defaults.fadeNear()),
                bool(root, "selectionOutline", defaults.selection()),
                bool(root, "dimensions", defaults.dimensions()),
                bool(root, "pathNumbers", defaults.pathNumbers()),
                bool(root, "brushReticle", defaults.brushReticle()),
                bool(root, "symmetryGuides", defaults.symmetryGuides()),
                bool(sound, "enabled", defaults.sounds()),
                number(sound, "volume", defaults.soundVolume())
        );
    }

    /** «45 %». */
    public static String percent(double value) {
        return String.format(Locale.ROOT, "%d %%", Math.round(value * 100.0));
    }

    private static double clamp(double value, double minimum, double maximum, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static double number(JsonObject root, String key, double fallback) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsDouble();
    }

    private static String string(JsonObject root, String key, String fallback) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        return element.getAsString();
    }
}
