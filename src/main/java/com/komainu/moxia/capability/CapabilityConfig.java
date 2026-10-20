package com.komainu.moxia.capability;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.persistence.AtomicStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Límites de las capacidades del servidor, en {@code config/komaedit/capabilities.json}. Si falta se crea con los
 * valores por defecto; si no se puede leer se usan los de por defecto y se avisa en el registro.
 */
public final class CapabilityConfig {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CapabilityLimits limits = CapabilityLimits.DEFAULT;

    private CapabilityConfig() {
    }

    public static CapabilityLimits limits() {
        return limits;
    }

    public static Path file() {
        return KomaEditConfig.directory().resolve("capabilities.json");
    }

    public static void load() {
        Path file = file();
        try {
            if (!Files.isRegularFile(file)) {
                save(CapabilityLimits.DEFAULT, file);
                limits = CapabilityLimits.DEFAULT;
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
            if (format != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported KomaEdit capabilities format: " + format);
            }
            CapabilityLimits fallback = CapabilityLimits.DEFAULT;
            limits = new CapabilityLimits(
                    integer(root, "maxReach", fallback.maxReach()),
                    integer(root, "maxLayerBlocks", fallback.maxLayerBlocks()),
                    integer(root, "maxSmearLength", fallback.maxSmearLength()),
                    integer(root, "maxFlySpeed", fallback.maxFlySpeed()),
                    integer(root, "actionsPerTick", fallback.actionsPerTick())
            );
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit capabilities config; safe defaults will be used",
                    exception);
            limits = CapabilityLimits.DEFAULT;
        }
    }

    private static void save(CapabilityLimits value, Path file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("maxReach", value.maxReach());
        root.addProperty("maxLayerBlocks", value.maxLayerBlocks());
        root.addProperty("maxSmearLength", value.maxSmearLength());
        root.addProperty("maxFlySpeed", value.maxFlySpeed());
        root.addProperty("actionsPerTick", value.actionsPerTick());
        Files.createDirectories(file.getParent());
        AtomicStorage.writeString(file, GSON.toJson(root));
    }

    private static int integer(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }
}
