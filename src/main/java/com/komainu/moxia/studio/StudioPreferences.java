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
 * Las herramientas tienen sus propias favoritas y recientes (por id de tarjeta) y la preferencia de construir con
 * vista previa.
 */
public final class StudioPreferences {
    public static final int MAX_RECENTS = 6;
    public static final int MAX_FAVORITES = 64;
    private static final int FORMAT = 1;

    private final Set<String> favorites = new LinkedHashSet<>();
    private final List<String> recents = new ArrayList<>();
    private final Set<String> toolFavorites = new LinkedHashSet<>();
    private final List<String> toolRecents = new ArrayList<>();
    private boolean toolPreview;

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

    public boolean isToolFavorite(String id) {
        return toolFavorites.contains(id);
    }

    /** Marca o desmarca una herramienta; devuelve si queda marcada. */
    public boolean toggleToolFavorite(String id) {
        if (toolFavorites.remove(id)) {
            return false;
        }
        if (toolFavorites.size() >= MAX_FAVORITES) {
            return false;
        }
        toolFavorites.add(id);
        return true;
    }

    public List<String> toolFavorites() {
        return List.copyOf(toolFavorites);
    }

    public void pushToolRecent(String id) {
        toolRecents.remove(id);
        toolRecents.addFirst(id);
        while (toolRecents.size() > MAX_RECENTS) {
            toolRecents.removeLast();
        }
    }

    public List<String> toolRecents() {
        return List.copyOf(toolRecents);
    }

    /** Si Construir de las herramientas pide la vista previa ({@code -v}) antes de tocar el mundo. */
    public boolean toolPreview() {
        return toolPreview;
    }

    public void setToolPreview(boolean value) {
        toolPreview = value;
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
        JsonArray toolFavoriteArray = new JsonArray();
        toolFavorites.forEach(toolFavoriteArray::add);
        root.add("toolFavorites", toolFavoriteArray);
        JsonArray toolRecentArray = new JsonArray();
        toolRecents.forEach(toolRecentArray::add);
        root.add("toolRecents", toolRecentArray);
        root.addProperty("toolPreview", toolPreview);
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
            for (String id : strings(root, "toolFavorites")) {
                if (preferences.toolFavorites.size() < MAX_FAVORITES) {
                    preferences.toolFavorites.add(id);
                }
            }
            List<String> tools = strings(root, "toolRecents");
            for (int index = tools.size() - 1; index >= 0; index--) {
                preferences.pushToolRecent(tools.get(index));
            }
            preferences.toolPreview = root.has("toolPreview") && root.get("toolPreview").isJsonPrimitive()
                    && root.getAsJsonPrimitive("toolPreview").isBoolean() && root.get("toolPreview").getAsBoolean();
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
