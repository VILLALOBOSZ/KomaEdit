package com.komainu.moxia.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.sculpt.BrushFalloff;
import com.komainu.moxia.sculpt.BrushMask;
import com.komainu.moxia.sculpt.SculptBrushType;
import com.komainu.moxia.sculpt.SculptProfile;
import com.komainu.moxia.sculpt.SculptSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class SculptPresetStore {
    private static final int FORMAT_VERSION = 1;
    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir()
            .resolve("komaedit")
            .resolve("presets");

    private SculptPresetStore() {
    }

    public static void save(UUID playerId, String name, SculptProfile profile) {
        Path target = presetPath(playerId, name);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(toJson(profile)), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar el preset: " + exception.getMessage(), exception);
        }
    }

    public static SculptProfile load(UUID playerId, String name) {
        Path target = presetPath(playerId, name);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("No existe el preset " + name);
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Versión de preset no compatible");
            }
            SculptBrushType type = SculptBrushType.byId(root.get("type").getAsString());
            BrushFalloff falloff = BrushFalloff.byId(root.get("falloff").getAsString());
            BrushMask mask = new BrushMask(
                    root.get("ignoreAir").getAsBoolean(),
                    root.get("ignoreLiquids").getAsBoolean(),
                    strings(root.getAsJsonArray("whitelist")),
                    strings(root.getAsJsonArray("blacklist")),
                    strings(root.getAsJsonArray("replaceOnly"))
            );
            SculptSettings settings = new SculptSettings(
                    type,
                    root.get("radius").getAsInt(),
                    root.get("height").getAsInt(),
                    root.get("intensity").getAsInt(),
                    falloff,
                    root.get("noise").getAsInt(),
                    root.get("randomness").getAsInt(),
                    root.get("hollow").getAsBoolean(),
                    mask
            );
            return new SculptProfile(settings, root.get("pattern").getAsString());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("No se pudo cargar el preset: " + exception.getMessage(), exception);
        }
    }

    public static List<String> list(UUID playerId) {
        Path directory = playerDirectory(playerId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron listar los presets: " + exception.getMessage(), exception);
        }
    }

    public static boolean delete(UUID playerId, String name) {
        try {
            return Files.deleteIfExists(presetPath(playerId, name));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo eliminar el preset: " + exception.getMessage(), exception);
        }
    }

    private static JsonObject toJson(SculptProfile profile) {
        SculptSettings settings = profile.settings();
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("type", settings.type().id());
        root.addProperty("pattern", profile.pattern());
        root.addProperty("radius", settings.radius());
        root.addProperty("height", settings.height());
        root.addProperty("intensity", settings.intensity());
        root.addProperty("falloff", settings.falloff().id());
        root.addProperty("noise", settings.noise());
        root.addProperty("randomness", settings.randomness());
        root.addProperty("hollow", settings.hollow());
        root.addProperty("ignoreAir", settings.mask().ignoreAir());
        root.addProperty("ignoreLiquids", settings.mask().ignoreLiquids());
        root.add("whitelist", array(settings.mask().whitelist()));
        root.add("blacklist", array(settings.mask().blacklist()));
        root.add("replaceOnly", array(settings.mask().replaceOnly()));
        return root;
    }

    private static JsonArray array(Set<String> values) {
        JsonArray array = new JsonArray();
        values.stream().sorted().forEach(array::add);
        return array;
    }

    private static Set<String> strings(JsonArray array) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        array.forEach(element -> values.add(element.getAsString()));
        return values;
    }

    private static Path presetPath(UUID playerId, String name) {
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("El nombre debe usar 1–32 letras, números, _ o -");
        }
        return playerDirectory(playerId).resolve(name + ".json");
    }

    private static Path playerDirectory(UUID playerId) {
        return ROOT.resolve(playerId.toString());
    }
}
