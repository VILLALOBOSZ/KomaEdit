package com.komainu.moxia.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Definiciones de texto con nombre (paletas, ruidos, selecciones…) guardadas en un único JSON por jugador. Escribe con
 * {@link AtomicStorage}, así que un cierre a mitad deja el archivo anterior o el nuevo, nunca uno a medias. Los
 * nombres no distinguen mayúsculas y se guardan en minúsculas.
 */
public final class NamedDefinitions {
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_VALUE_LENGTH = 16_384;
    private static final int FORMAT_VERSION = 1;
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final String kind;

    /**
     * @param kind nombre de lo que se guarda en singular («paleta», «ruido»…), para los mensajes de error
     */
    public NamedDefinitions(Path file, String kind) {
        this.file = file;
        this.kind = kind;
    }

    public static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (!VALID_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("El nombre debe tener de 1 a 32 letras, números, _ o -");
        }
        return normalized;
    }

    public synchronized Map<String, String> all() {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            JsonObject root = parsed.getAsJsonObject();
            JsonObject entries = root.getAsJsonObject("entries");
            Map<String, String> result = new TreeMap<>();
            if (entries != null) {
                for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        result.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("No se pudo leer " + file.getFileName() + ": " + exception.getMessage(),
                    exception);
        }
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(all().get(normalizeName(name)));
    }

    /**
     * Guarda una definición.
     *
     * @return {@code true} si sustituyó una existente
     */
    public synchronized boolean put(String name, String value, boolean overwrite) {
        String key = normalizeName(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("No se puede guardar un valor vacío");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("El valor supera " + MAX_VALUE_LENGTH + " caracteres");
        }
        Map<String, String> entries = new TreeMap<>(all());
        boolean existed = entries.containsKey(key);
        if (existed && !overwrite) {
            throw new IllegalArgumentException("Ya existe " + key + " (" + kind + "); añade -f para sustituirlo");
        }
        if (!existed && entries.size() >= MAX_ENTRIES) {
            throw new IllegalArgumentException("Has llegado al máximo de " + MAX_ENTRIES + " guardados (" + kind + ")");
        }
        entries.put(key, value);
        write(entries);
        return existed;
    }

    public synchronized boolean delete(String name) {
        String key = normalizeName(name);
        Map<String, String> entries = new TreeMap<>(all());
        if (entries.remove(key) == null) {
            return false;
        }
        write(entries);
        return true;
    }

    private void write(Map<String, String> entries) {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonObject values = new JsonObject();
        entries.forEach(values::addProperty);
        root.add("entries", values);
        try {
            AtomicStorage.writeString(file, GSON.toJson(root));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar " + file.getFileName() + ": " + exception.getMessage(),
                    exception);
        }
    }
}
