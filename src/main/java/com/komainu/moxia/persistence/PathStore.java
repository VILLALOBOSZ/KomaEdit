package com.komainu.moxia.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.path.PathManager;
import com.sk89q.worldedit.math.BlockVector3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Recorridos guardados con nombre, por mundo y jugador, en {@code <mundo>/komaedit/players/<uuid>/paths/<nombre>.json}.
 * Se escriben con {@link AtomicStorage}, así que un corte a mitad deja el archivo anterior intacto.
 *
 * <p>Formato 1: {@code {"format":1,"name":"puente","dimension":"minecraft:overworld","closed":false,
 * "points":[[x,y,z],…]}}. Al leer se ignoran los campos desconocidos para que versiones futuras puedan añadir más.
 */
public final class PathStore {
    public static final int FORMAT_VERSION = 1;
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PathStore() {
    }

    public record Saved(String name, String dimension, boolean closed, List<BlockVector3> points) {
        public Saved {
            points = List.copyOf(points);
        }
    }

    public static void save(UUID playerId, Saved path) {
        try {
            AtomicStorage.writeString(file(playerId, path.name()), GSON.toJson(toJson(path)));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar el recorrido: " + exception.getMessage(), exception);
        }
    }

    public static Saved load(UUID playerId, String name) {
        Path file = file(playerId, name);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("No tienes ningún recorrido llamado " + normalize(name));
        }
        try {
            return fromJson(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el recorrido: " + exception.getMessage(), exception);
        }
    }

    public static boolean exists(UUID playerId, String name) {
        return Files.isRegularFile(file(playerId, name));
    }

    public static boolean delete(UUID playerId, String name) {
        try {
            return Files.deleteIfExists(file(playerId, name));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo borrar el recorrido: " + exception.getMessage(), exception);
        }
    }

    /** Nombres guardados, en orden alfabético. */
    public static List<String> names(UUID playerId) {
        Path directory = ProjectStorage.namespace(playerId, "paths");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .filter(name -> VALID_NAME.matcher(name).matches())
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron listar los recorridos: " + exception.getMessage(), exception);
        }
    }

    /** Pasa el nombre a minúsculas y comprueba que solo use letras, números, _ y -. */
    public static String normalize(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!VALID_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("El nombre debe tener de 1 a 32 letras sin tilde, números, _ o -");
        }
        return normalized;
    }

    public static JsonObject toJson(Saved path) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("name", path.name());
        root.addProperty("dimension", path.dimension());
        root.addProperty("closed", path.closed());
        JsonArray points = new JsonArray();
        for (BlockVector3 point : path.points()) {
            JsonArray entry = new JsonArray();
            entry.add(point.x());
            entry.add(point.y());
            entry.add(point.z());
            points.add(entry);
        }
        root.add("points", points);
        return root;
    }

    public static Saved fromJson(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("El recorrido guardado está dañado");
        }
        JsonObject root = element.getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : 0;
        if (format < 1 || format > FORMAT_VERSION) {
            throw new IllegalArgumentException("Formato de recorrido no compatible: " + format);
        }
        JsonArray array = root.getAsJsonArray("points");
        if (array == null) {
            throw new IllegalArgumentException("El recorrido guardado no tiene puntos");
        }
        if (array.size() > PathManager.MAX_POINTS) {
            throw new IllegalArgumentException("El recorrido guardado tiene más de " + PathManager.MAX_POINTS + " puntos");
        }
        List<BlockVector3> points = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            JsonArray coordinates = entry.getAsJsonArray();
            if (coordinates.size() != 3) {
                throw new IllegalArgumentException("Punto dañado en el recorrido guardado");
            }
            points.add(BlockVector3.at(
                    coordinates.get(0).getAsInt(),
                    coordinates.get(1).getAsInt(),
                    coordinates.get(2).getAsInt()
            ));
        }
        return new Saved(
                root.has("name") ? root.get("name").getAsString() : "",
                root.has("dimension") ? root.get("dimension").getAsString() : "",
                root.has("closed") && root.get("closed").getAsBoolean(),
                points
        );
    }

    private static Path file(UUID playerId, String name) {
        return ProjectStorage.namespace(playerId, "paths").resolve(normalize(name) + ".json");
    }
}
