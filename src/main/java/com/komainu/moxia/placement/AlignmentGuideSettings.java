package com.komainu.moxia.placement;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.persistence.AtomicStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/komaedit/visualisations.json}: el visualizador de la alineación de los pinceles de colocación, como
 * la sección {@code visualisations} de la configuración de ezEdits. Se recarga con {@code /komaedit reload}.
 * Cada jugador con KomaEdit en su equipo puede apagarlo además para sí en {@code /kegui ajustes}.
 *
 * @param enabled         si se enseña
 * @param updateTicks     cada cuántos ticks se recalcula (1–40; 4 de serie, cinco veces por segundo)
 * @param vanillaDisplays si los clientes sin KomaEdit lo ven con block displays (solo los ve quien tiene el pincel)
 */
public record AlignmentGuideSettings(boolean enabled, int updateTicks, boolean vanillaDisplays) {
    public static final AlignmentGuideSettings DEFAULT = new AlignmentGuideSettings(true, 4, true);
    private static final int FORMAT = 1;
    private static volatile AlignmentGuideSettings current = DEFAULT;

    public AlignmentGuideSettings {
        if (updateTicks < 1 || updateTicks > 40) {
            throw new IllegalArgumentException("updateTicks must be between 1 and 40");
        }
    }

    public static AlignmentGuideSettings get() {
        return current;
    }

    /** Lee el archivo de {@code configDirectory}; si no existe lo crea y, si está roto, avisa y usa los de serie. */
    public static void load(Path configDirectory) {
        Path file = configDirectory.resolve("visualisations.json");
        try {
            if (!Files.isRegularFile(file)) {
                AtomicStorage.writeString(file, DEFAULT.toJson());
                current = DEFAULT;
                return;
            }
            current = parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit visualisations.json; defaults will be used", exception);
            current = DEFAULT;
        }
    }

    /** Lo que falte toma el valor de serie. */
    public static AlignmentGuideSettings parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject guide = root.has("alignment") && root.get("alignment").isJsonObject()
                ? root.getAsJsonObject("alignment") : new JsonObject();
        return new AlignmentGuideSettings(
                guide.has("enabled") ? guide.get("enabled").getAsBoolean() : DEFAULT.enabled(),
                guide.has("updateTicks") ? guide.get("updateTicks").getAsInt() : DEFAULT.updateTicks(),
                guide.has("vanillaDisplays") ? guide.get("vanillaDisplays").getAsBoolean() : DEFAULT.vanillaDisplays()
        );
    }

    public String toJson() {
        JsonObject guide = new JsonObject();
        guide.addProperty("enabled", enabled);
        guide.addProperty("updateTicks", updateTicks);
        guide.addProperty("vanillaDisplays", vanillaDisplays);
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.add("alignment", guide);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
}
