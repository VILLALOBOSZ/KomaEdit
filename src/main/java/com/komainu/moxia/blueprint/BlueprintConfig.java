package com.komainu.moxia.blueprint;

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
 * Límites de la biblioteca de planos en {@code config/komaedit/blueprints.json}. Si falta se crea con los de serie;
 * si no se puede leer se usan los de serie y se avisa en el registro. Se relee con {@code /komaedit reload}.
 */
public final class BlueprintConfig {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile BlueprintLimits limits = BlueprintLimits.DEFAULT;

    private BlueprintConfig() {
    }

    public static BlueprintLimits limits() {
        return limits;
    }

    public static void load(Path directory) {
        Path file = directory.resolve("blueprints.json");
        try {
            if (!Files.isRegularFile(file)) {
                save(BlueprintLimits.DEFAULT, file);
                limits = BlueprintLimits.DEFAULT;
                return;
            }
            limits = read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit blueprints config; safe defaults will be used",
                    exception);
            limits = BlueprintLimits.DEFAULT;
        }
    }

    static BlueprintLimits read(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported KomaEdit blueprints format: " + format);
        }
        BlueprintLimits fallback = BlueprintLimits.DEFAULT;
        return new BlueprintLimits(
                root.has("maxPerPlayer") ? root.get("maxPerPlayer").getAsInt() : fallback.maxPerPlayer(),
                root.has("maxShared") ? root.get("maxShared").getAsInt() : fallback.maxShared(),
                root.has("maxVolume") ? root.get("maxVolume").getAsLong() : fallback.maxVolume(),
                root.has("maxSide") ? root.get("maxSide").getAsInt() : fallback.maxSide(),
                root.has("maxFileKilobytes") ? root.get("maxFileKilobytes").getAsLong() * 1024
                        : fallback.maxFileBytes(),
                root.has("allowSharing") ? root.get("allowSharing").getAsBoolean() : fallback.allowSharing()
        );
    }

    private static void save(BlueprintLimits value, Path file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("maxPerPlayer", value.maxPerPlayer());
        root.addProperty("maxShared", value.maxShared());
        root.addProperty("maxVolume", value.maxVolume());
        root.addProperty("maxSide", value.maxSide());
        root.addProperty("maxFileKilobytes", value.maxFileBytes() / 1024);
        root.addProperty("allowSharing", value.allowSharing());
        Files.createDirectories(file.getParent());
        AtomicStorage.writeString(file, GSON.toJson(root));
    }
}
