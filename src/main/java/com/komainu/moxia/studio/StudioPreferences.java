package com.komainu.moxia.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.persistence.AtomicStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Favoritas y recientes del Studio, por jugador y en su propio equipo (no viajan con las recetas).
 * Se guardan por id de figura; un id que ya no existe se conserva por si vuelve en otra versión.
 */
public final class StudioPreferences {
    public static final int MAX_RECENTS = 6;
    public static final int MAX_FAVORITES = 64;
    private static final int FORMAT = 1;

    private final Set<String> favorites = new LinkedHashSet<>();
    private final List<String> recents = new ArrayList<>();

    public boolean isFavorite(String id) {
        return favorites.contains(id);
    }

    /** Marca o desmarca; devuelve si queda marcada. */
    public boolean toggleFavorite(String id) {
        if (favorites.remove(id)) {
            return false;
        }
        if (favorites.size() >= MAX_FAVORITES) {
            return false;
        }
        favorites.add(id);
        return true;
    }

    public List<String> favorites() {
        return List.copyOf(favorites);
    }

    /** La más reciente primero, sin repetidas. */
    public void pushRecent(String id) {
        recents.remove(id);
        recents.addFirst(id);
        while (recents.size() > MAX_RECENTS) {
            recents.removeLast();
        }
    }

    public List<String> recents() {
        return List.copyOf(recents);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonArray favoriteArray = new JsonArray();
        favorites.forEach(favoriteArray::add);
        root.add("favorites", favoriteArray);
        JsonArray recentArray = new JsonArray();
        recents.forEach(recentArray::add);
        root.add("recents", recentArray);
        return root.toString();
    }

    /** Lee lo que pueda: un archivo roto o a medias da preferencias vacías, nunca un error. */
    public static StudioPreferences fromJson(String json) {
        StudioPreferences preferences = new StudioPreferences();
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return preferences;
            }
            JsonObject root = parsed.getAsJsonObject();
            for (String id : strings(root, "favorites")) {
                if (preferences.favorites.size() < MAX_FAVORITES) {
                    preferences.favorites.add(id);
                }
            }
            List<String> stored = strings(root, "recents");
            for (int index = stored.size() - 1; index >= 0; index--) {
                preferences.pushRecent(stored.get(index));
            }
        } catch (RuntimeException ignored) {
            return new StudioPreferences();
        }
        return preferences;
    }

    public static StudioPreferences load(Path file) {
        try {
            return Files.isRegularFile(file)
                    ? fromJson(Files.readString(file, StandardCharsets.UTF_8))
                    : new StudioPreferences();
        } catch (IOException exception) {
            return new StudioPreferences();
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        AtomicStorage.writeString(file, toJson());
    }

    private static List<String> strings(JsonObject root, String key) {
        List<String> values = new ArrayList<>();
        if (root.has(key) && root.get(key).isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray(key)) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    String value = element.getAsString().trim();
                    if (!value.isEmpty() && value.length() <= 64) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }
}
