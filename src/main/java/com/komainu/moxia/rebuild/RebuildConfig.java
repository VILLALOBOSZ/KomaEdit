package com.komainu.moxia.rebuild;

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
 * Ajustes de la reconstrucción animada en {@code config/komaedit/rebuild.json}. Si falta se crea con los de serie;
 * si no se puede leer se usan los de serie y se avisa en el registro. Se relee con {@code /komaedit reload}.
 */
public final class RebuildConfig {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Settings settings = Settings.DEFAULT;

    private RebuildConfig() {
    }

    /**
     * @param historyBlocks     colocaciones que se recuerdan por mundo para el orden «historial» (0 lo apaga)
     * @param historyEditBlocks una edición de WorldEdit o KomaEdit de más bloques no se apunta en el historial (un
     *                          relleno de terreno no es «cómo se construyó»)
     * @param effectsPerTick    partículas y sonidos como mucho por tick ({@code -f}, {@code -n})
     */
    public record Settings(int historyBlocks, int historyEditBlocks, int effectsPerTick) {
        public static final Settings DEFAULT = new Settings(500_000, 4_096, 3);

        public Settings {
            historyBlocks = Math.clamp(historyBlocks, 0, 20_000_000);
            historyEditBlocks = Math.clamp(historyEditBlocks, 0, 1_000_000);
            effectsPerTick = Math.clamp(effectsPerTick, 0, 64);
        }
    }

    public static Settings settings() {
        return settings;
    }

    public static void load(Path directory) {
        Path file = directory.resolve("rebuild.json");
        try {
            if (!Files.isRegularFile(file)) {
                save(Settings.DEFAULT, file);
                settings = Settings.DEFAULT;
                return;
            }
            settings = read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit rebuild config; safe defaults will be used", exception);
            settings = Settings.DEFAULT;
        }
    }

    static Settings read(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported KomaEdit rebuild format: " + format);
        }
        Settings fallback = Settings.DEFAULT;
        return new Settings(
                root.has("historyBlocksPerWorld") ? root.get("historyBlocksPerWorld").getAsInt()
                        : fallback.historyBlocks(),
                root.has("historyMaxEditBlocks") ? root.get("historyMaxEditBlocks").getAsInt()
                        : fallback.historyEditBlocks(),
                root.has("effectsPerTick") ? root.get("effectsPerTick").getAsInt() : fallback.effectsPerTick()
        );
    }

    static String json(Settings value) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("historyBlocksPerWorld", value.historyBlocks());
        root.addProperty("historyMaxEditBlocks", value.historyEditBlocks());
        root.addProperty("effectsPerTick", value.effectsPerTick());
        return GSON.toJson(root);
    }

    private static void save(Settings value, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        AtomicStorage.writeString(file, json(value));
    }
}
