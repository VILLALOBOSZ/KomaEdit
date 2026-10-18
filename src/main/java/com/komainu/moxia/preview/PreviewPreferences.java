package com.komainu.moxia.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.persistence.ProjectStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Previsualizar siempre a partir de N bloques», por jugador y mundo ({@code komaedit/players/<uuid>/preview}).
 * 0 la apaga: entonces solo hay vista previa con {@code -v}.
 */
public final class PreviewPreferences {
    public static final int MAX_THRESHOLD = 10_000_000;
    private static final int FORMAT_VERSION = 1;
    private static final Map<UUID, Integer> CACHE = new ConcurrentHashMap<>();

    private PreviewPreferences() {
    }

    /** Umbral del jugador; 0 si no previsualiza siempre o si su proyecto no está abierto. */
    public static int alwaysAbove(UUID player) {
        Integer cached = CACHE.get(player);
        if (cached != null) {
            return cached;
        }
        int value = 0;
        try {
            Path file = file(player);
            if (Files.isRegularFile(file)) {
                value = decode(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not read KomaEdit preview preference for {}", player, exception);
        }
        CACHE.put(player, value);
        return value;
    }

    public static void setAlwaysAbove(UUID player, int blocks) {
        int value = validate(blocks);
        try {
            AtomicStorage.writeString(file(player), encode(value));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar la preferencia: " + exception.getMessage(), exception);
        }
        CACHE.put(player, value);
    }

    public static void forget(UUID player) {
        CACHE.remove(player);
    }

    public static void clearAll() {
        CACHE.clear();
    }

    static int validate(int blocks) {
        if (blocks < 0 || blocks > MAX_THRESHOLD) {
            throw new IllegalArgumentException("El umbral va de 0 (apagado) a " + MAX_THRESHOLD + " bloques");
        }
        return blocks;
    }

    static String encode(int blocks) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("alwaysPreviewAboveBlocks", validate(blocks));
        return root.toString();
    }

    static int decode(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Formato de preferencia desconocido: " + format);
        }
        return root.has("alwaysPreviewAboveBlocks")
                ? validate(root.get("alwaysPreviewAboveBlocks").getAsInt())
                : 0;
    }

    private static Path file(UUID player) {
        return ProjectStorage.namespace(player, "preview").resolve("preview.json");
    }
}
