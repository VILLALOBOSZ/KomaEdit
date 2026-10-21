package com.komainu.moxia.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.notes.NoteLimits;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.views.SharedViews;
import com.komainu.moxia.views.ViewTeleport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reglas de las vistas guardadas y de las notas en {@code config/komaedit/utilities.json}. Si falta se crea con las
 * de serie; si no se puede leer se usan las de serie y se avisa en el registro. Se relee con {@code /komaedit reload}.
 */
public final class UtilitiesConfig {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Settings settings = Settings.DEFAULT;

    /** Todo lo de este archivo. */
    public record Settings(ViewTeleport.Rules teleport, SharedViews.Limits views, NoteLimits notes) {
        public static final Settings DEFAULT = new Settings(ViewTeleport.Rules.DEFAULT, SharedViews.Limits.DEFAULT,
                NoteLimits.DEFAULT);
    }

    private UtilitiesConfig() {
    }

    public static Settings settings() {
        return settings;
    }

    public static void load(Path directory) {
        Path file = directory.resolve("utilities.json");
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(directory);
                AtomicStorage.writeString(file, toJson(Settings.DEFAULT));
                settings = Settings.DEFAULT;
                return;
            }
            settings = parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit utilities config; safe defaults will be used",
                    exception);
            settings = Settings.DEFAULT;
        }
    }

    /** Lee el JSON; lo que falte toma su valor de serie. Lanza si el formato no es este o un valor no vale. */
    static Settings parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported KomaEdit utilities format: " + format);
        }
        JsonObject views = root.has("views") ? root.getAsJsonObject("views") : new JsonObject();
        JsonObject notes = root.has("notes") ? root.getAsJsonObject("notes") : new JsonObject();
        ViewTeleport.Rules teleport = ViewTeleport.Rules.DEFAULT;
        SharedViews.Limits shared = SharedViews.Limits.DEFAULT;
        NoteLimits limits = NoteLimits.DEFAULT;
        return new Settings(
                new ViewTeleport.Rules(
                        bool(views, "teleport", teleport.enabled()),
                        bool(views, "creativeOnly", teleport.creativeOnly()),
                        views.has("maxTeleportDistance") ? views.get("maxTeleportDistance").getAsDouble()
                                : teleport.maxDistance(),
                        views.has("cooldownMillis") ? views.get("cooldownMillis").getAsLong()
                                : teleport.cooldownMillis()),
                new SharedViews.Limits(
                        bool(views, "allowSharing", shared.allowSharing()),
                        integer(views, "maxSharedPerPlayer", shared.maxPerPlayer()),
                        integer(views, "maxShared", shared.maxTotal())),
                new NoteLimits(
                        integer(notes, "maxPerPlayer", limits.maxPerPlayer()),
                        integer(notes, "maxTeam", limits.maxTeam()),
                        integer(notes, "maxLength", limits.maxLength()),
                        integer(notes, "maxDistance", limits.maxDistance()),
                        bool(notes, "allowTeam", limits.allowTeam())));
    }

    static String toJson(Settings value) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        JsonObject views = new JsonObject();
        views.addProperty("teleport", value.teleport().enabled());
        views.addProperty("creativeOnly", value.teleport().creativeOnly());
        views.addProperty("maxTeleportDistance", value.teleport().maxDistance());
        views.addProperty("cooldownMillis", value.teleport().cooldownMillis());
        views.addProperty("allowSharing", value.views().allowSharing());
        views.addProperty("maxSharedPerPlayer", value.views().maxPerPlayer());
        views.addProperty("maxShared", value.views().maxTotal());
        root.add("views", views);
        JsonObject notes = new JsonObject();
        notes.addProperty("maxPerPlayer", value.notes().maxPerPlayer());
        notes.addProperty("maxTeam", value.notes().maxTeam());
        notes.addProperty("maxLength", value.notes().maxLength());
        notes.addProperty("maxDistance", value.notes().maxDistance());
        notes.addProperty("allowTeam", value.notes().allowTeam());
        root.add("notes", notes);
        return GSON.toJson(root);
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }
}
