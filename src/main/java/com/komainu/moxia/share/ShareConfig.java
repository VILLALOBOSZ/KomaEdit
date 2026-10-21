package com.komainu.moxia.share;

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
 * Límites de los paquetes compartidos en {@code config/komaedit/share.json}. Si falta se crea con los de serie; si no
 * se puede leer se usan los de serie y se avisa en el registro. Se relee con {@code /komaedit reload}.
 */
public final class ShareConfig {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ShareLimits limits = ShareLimits.DEFAULT;

    private ShareConfig() {
    }

    public static ShareLimits limits() {
        return limits;
    }

    public static void load(Path directory) {
        Path file = directory.resolve("share.json");
        try {
            if (!Files.isRegularFile(file)) {
                save(ShareLimits.DEFAULT, file);
                limits = ShareLimits.DEFAULT;
                return;
            }
            limits = read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit share config; safe defaults will be used", exception);
            limits = ShareLimits.DEFAULT;
        }
    }

    static ShareLimits read(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported KomaEdit share format: " + format);
        }
        ShareLimits fallback = ShareLimits.DEFAULT;
        return new ShareLimits(
                root.has("maxCodeChars") ? root.get("maxCodeChars").getAsInt() : fallback.maxCodeChars(),
                root.has("maxPackKilobytes") ? root.get("maxPackKilobytes").getAsLong() * 1024
                        : fallback.maxPackBytes(),
                root.has("maxFileKilobytes") ? root.get("maxFileKilobytes").getAsLong() * 1024
                        : fallback.maxFileBytes(),
                root.has("maxItems") ? root.get("maxItems").getAsInt() : fallback.maxItems(),
                root.has("maxSchematicKilobytes") ? root.get("maxSchematicKilobytes").getAsLong() * 1024
                        : fallback.maxSchematicBytes(),
                root.has("importsPerHour") ? root.get("importsPerHour").getAsInt() : fallback.importsPerHour(),
                root.has("exportsPerHour") ? root.get("exportsPerHour").getAsInt() : fallback.exportsPerHour(),
                root.has("allowFiles") ? root.get("allowFiles").getAsBoolean() : fallback.allowFiles()
        );
    }

    private static void save(ShareLimits value, Path file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("maxCodeChars", value.maxCodeChars());
        root.addProperty("maxPackKilobytes", value.maxPackBytes() / 1024);
        root.addProperty("maxFileKilobytes", value.maxFileBytes() / 1024);
        root.addProperty("maxItems", value.maxItems());
        root.addProperty("maxSchematicKilobytes", value.maxSchematicBytes() / 1024);
        root.addProperty("importsPerHour", value.importsPerHour());
        root.addProperty("exportsPerHour", value.exportsPerHour());
        root.addProperty("allowFiles", value.allowFiles());
        Files.createDirectories(file.getParent());
        AtomicStorage.writeString(file, GSON.toJson(root));
    }
}
