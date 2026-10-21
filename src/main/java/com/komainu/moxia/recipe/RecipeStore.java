package com.komainu.moxia.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.gradient.GradientAxis;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.persistence.ProjectStorage;
import com.komainu.moxia.persistence.TolerantListing;
import com.komainu.moxia.shape.PathShape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecipeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Recetas guardadas por jugador como mucho: las que enseña el Studio ({@link #list}). */
    public static final int MAX_RECIPES = 64;

    private RecipeStore() {
    }

    public static void save(UUID playerId, ProceduralRecipe recipe) {
        save(playerDirectory(playerId), recipe);
    }

    static void save(Path directory, ProceduralRecipe recipe) {
        Path target = recipePath(directory, recipe.name());
        try {
            if (!Files.isRegularFile(target) && count(directory) >= MAX_RECIPES) {
                throw new IllegalArgumentException("Ya tienes " + MAX_RECIPES
                        + " recetas guardadas, el máximo: borra alguna para guardar otra");
            }
            Files.createDirectories(target.getParent());
            AtomicStorage.writeString(target, GSON.toJson(toJson(recipe)));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar la receta: " + exception.getMessage(), exception);
        }
    }

    private static long count(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .count();
        }
    }

    public static List<ProceduralRecipe> list(UUID playerId) {
        Path directory = playerDirectory(playerId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            List<Path> paths = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .limit(MAX_RECIPES)
                    .toList();
            return TolerantListing.readAll(paths, RecipeStore::read, "la receta");
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron listar las recetas: " + exception.getMessage(), exception);
        }
    }

    public static ProceduralRecipe load(UUID playerId, String name) {
        Path path = recipePath(playerId, name);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("No existe la receta " + name);
        }
        return read(path);
    }

    public static boolean exists(UUID playerId, String name) {
        return Files.isRegularFile(recipePath(playerId, name));
    }

    public static boolean delete(UUID playerId, String name) {
        Path target = recipePath(playerId, name);
        try {
            boolean deleted = Files.deleteIfExists(target);
            Files.deleteIfExists(AtomicStorage.temporary(target));
            Files.deleteIfExists(AtomicStorage.backup(target));
            return deleted;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo eliminar la receta: " + exception.getMessage(), exception);
        }
    }

    public static void clear(UUID playerId) {
        Path directory = playerDirectory(playerId);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try {
            List<Path> storedFiles;
            try (var files = Files.list(directory)) {
                storedFiles = files.toList();
            }
            for (Path file : storedFiles) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron limpiar las recetas de la sesión", exception);
        }
    }

    /** Una receta del disco; si no se puede leer (una figura que ya no existe), lanza con el motivo. */
    static ProceduralRecipe read(Path path) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            int version = root.get("version").getAsInt();
            if (version < 1 || version > ProceduralRecipe.FORMAT_VERSION) {
                throw new IllegalArgumentException("Versión de receta no compatible: " + path.getFileName());
            }
            return fromJson(root, version);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("No se pudo leer " + path.getFileName() + ": " + exception.getMessage(), exception);
        }
    }

    /** Una receta en el JSON que se guarda (lo usan también los paquetes compartidos). */
    public static ProceduralRecipe fromJson(JsonObject root, int version) {
        try {
            Map<String, Double> options = new LinkedHashMap<>();
            if (root.has("options")) {
                root.getAsJsonObject("options").entrySet()
                        .forEach(entry -> options.put(entry.getKey(), entry.getValue().getAsDouble()));
            }
            RecipeExtensions extensions = version >= 2 && root.has("extensions")
                    ? extensionsFromJson(root.getAsJsonObject("extensions"))
                    : RecipeExtensions.DEFAULT;
            PathShape shape = PathShape.byId(root.get("shape").getAsString());
            String accentPattern = version >= 3 && root.has("accentPattern")
                    ? root.get("accentPattern").getAsString()
                    : defaultAccentPattern(shape);
            String formula = version >= 4 && root.has("formula") ? root.get("formula").getAsString() : "";
            return new ProceduralRecipe(
                    root.get("name").getAsString(),
                    shape,
                    root.get("pattern").getAsString(),
                    accentPattern,
                    root.get("radius").getAsInt(),
                    root.get("spacing").getAsInt(),
                    root.get("thickness").getAsInt(),
                    root.get("roll").getAsInt(),
                    root.get("height").getAsInt(),
                    root.get("palette").getAsString(),
                    GradientAxis.byId(root.get("axis").getAsString()),
                    root.get("dither").getAsInt(),
                    root.get("reverse").getAsBoolean(),
                    options,
                    extensions,
                    formula
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("La receta guardada no es válida: " + exception.getMessage(), exception);
        }
    }

    public static JsonObject toJson(ProceduralRecipe recipe) {
        JsonObject root = new JsonObject();
        root.addProperty("version", ProceduralRecipe.FORMAT_VERSION);
        root.addProperty("name", recipe.name());
        root.addProperty("shape", recipe.shape().id());
        root.addProperty("pattern", recipe.pattern());
        root.addProperty("accentPattern", recipe.accentPattern());
        root.addProperty("radius", recipe.radius());
        root.addProperty("spacing", recipe.spacing());
        root.addProperty("thickness", recipe.thickness());
        root.addProperty("roll", recipe.rollDegrees());
        root.addProperty("height", recipe.height());
        root.addProperty("palette", recipe.paletteId());
        root.addProperty("axis", recipe.gradientAxis().id());
        root.addProperty("dither", recipe.dither());
        root.addProperty("reverse", recipe.reverse());
        JsonObject options = new JsonObject();
        recipe.options().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> options.addProperty(entry.getKey(), entry.getValue()));
        root.add("options", options);
        root.add("extensions", extensionsToJson(recipe.extensions()));
        if (!recipe.formula().isEmpty()) {
            root.addProperty("formula", recipe.formula());
        }
        return root;
    }

    private static JsonObject extensionsToJson(RecipeExtensions extensions) {
        JsonObject root = new JsonObject();
        root.addProperty("startScale", extensions.startScalePercent());
        root.addProperty("endScale", extensions.endScalePercent());
        root.addProperty("startSpacing", extensions.startSpacingPercent());
        root.addProperty("endSpacing", extensions.endSpacingPercent());
        root.addProperty("twist", extensions.twistDegrees());
        root.addProperty("easing", extensions.easing().id());
        root.addProperty("rhythmEnabled", extensions.rhythmEnabled());
        root.addProperty("accentShape", extensions.accentShape().id());
        root.addProperty("rhythmInterval", extensions.rhythmInterval());
        root.addProperty("surfaceMode", extensions.surfaceMode().id());
        root.addProperty("conformRadius", extensions.conformRadius());
        root.addProperty("surfaceOffset", extensions.surfaceOffset());
        root.addProperty("materialMetric", extensions.materialMetric().id());
        root.addProperty("socketsEnabled", extensions.socketsEnabled());
        root.addProperty("socketPlacement", extensions.socketPlacement().id());
        root.addProperty("socketInterval", extensions.socketInterval());
        root.addProperty("closeMicroGaps", extensions.closeMicroGaps());
        root.addProperty("removeIsolated", extensions.removeIsolated());
        return root;
    }

    private static RecipeExtensions extensionsFromJson(JsonObject root) {
        return new RecipeExtensions(
                root.get("startScale").getAsInt(),
                root.get("endScale").getAsInt(),
                root.get("startSpacing").getAsInt(),
                root.get("endSpacing").getAsInt(),
                root.get("twist").getAsInt(),
                CurveEasing.byId(root.get("easing").getAsString()),
                root.get("rhythmEnabled").getAsBoolean(),
                PathShape.byId(root.get("accentShape").getAsString()),
                root.get("rhythmInterval").getAsInt(),
                SurfaceMode.byId(root.get("surfaceMode").getAsString()),
                root.get("conformRadius").getAsInt(),
                root.get("surfaceOffset").getAsInt(),
                MaterialMetric.byId(root.get("materialMetric").getAsString()),
                root.get("socketsEnabled").getAsBoolean(),
                SocketPlacement.byId(root.get("socketPlacement").getAsString()),
                root.get("socketInterval").getAsInt(),
                root.get("closeMicroGaps").getAsBoolean(),
                root.get("removeIsolated").getAsBoolean()
        );
    }

    private static String defaultAccentPattern(PathShape shape) {
        return "oak_leaves";
    }

    private static Path recipePath(UUID playerId, String name) {
        return recipePath(playerDirectory(playerId), name);
    }

    private static Path recipePath(Path directory, String name) {
        if (!ProceduralRecipe.VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("El nombre debe usar 1–32 letras, números, _ o -");
        }
        return directory.resolve(name + ".json");
    }

    private static Path playerDirectory(UUID playerId) {
        return ProjectStorage.namespace(playerId, "recipes");
    }
}
