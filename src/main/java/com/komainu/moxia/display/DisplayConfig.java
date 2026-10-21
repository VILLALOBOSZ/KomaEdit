package com.komainu.moxia.display;

import com.google.gson.Gson;
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
 * Límites del editor de displays en {@code config/komaedit/displays.json}. Si falta se crea con los de serie; si no se
 * puede leer se usan los de serie y se avisa en el registro. Se relee con {@code /komaedit reload}.
 */
public final class DisplayConfig {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile DisplayLimits limits = DisplayLimits.DEFAULT;

    private DisplayConfig() {
    }

    public static DisplayLimits limits() {
        return limits;
    }

    public static void load(Path directory) {
        Path file = directory.resolve("displays.json");
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(directory);
                AtomicStorage.writeString(file, toJson(DisplayLimits.DEFAULT));
                limits = DisplayLimits.DEFAULT;
                return;
            }
            limits = read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit displays config; safe defaults will be used",
                    exception);
            limits = DisplayLimits.DEFAULT;
        }
    }

    static DisplayLimits read(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported KomaEdit displays format: " + format);
        }
        DisplayLimits fallback = DisplayLimits.DEFAULT;
        return new DisplayLimits(
                integer(root, "maxPerChunk", fallback.maxPerChunk()),
                integer(root, "maxPerPlayer", fallback.maxPerPlayer()),
                decimal(root, "maxDistance", fallback.maxDistance()),
                decimal(root, "maxScale", fallback.maxScale()),
                decimal(root, "maxOffset", fallback.maxOffset()),
                integer(root, "maxSelection", fallback.maxSelection()),
                integer(root, "maxTextLength", fallback.maxTextLength()),
                integer(root, "maxEditsPerSecond", fallback.maxEditsPerSecond()));
    }

    static String toJson(DisplayLimits value) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("maxPerChunk", value.maxPerChunk());
        root.addProperty("maxPerPlayer", value.maxPerPlayer());
        root.addProperty("maxDistance", value.maxDistance());
        root.addProperty("maxScale", value.maxScale());
        root.addProperty("maxOffset", value.maxOffset());
        root.addProperty("maxSelection", value.maxSelection());
        root.addProperty("maxTextLength", value.maxTextLength());
        root.addProperty("maxEditsPerSecond", value.maxEditsPerSecond());
        return GSON.toJson(root);
    }

    private static int integer(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }

    private static double decimal(JsonObject root, String key, double fallback) {
        return root.has(key) ? root.get(key).getAsDouble() : fallback;
    }
}
