package com.komainu.moxia.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.persistence.ProjectStorage;
import com.komainu.moxia.persistence.TolerantListing;
import com.sk89q.worldedit.math.BlockVector3;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ProceduralInstanceStore {
    private static final int FORMAT_VERSION = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProceduralInstanceStore() {
    }

    public static void save(
            UUID playerId,
            String id,
            ProceduralRecipe recipe,
            PathSnapshot path,
            PackedBlockPlan geometry,
            int[] stateIds
    ) {
        if (stateIds.length != geometry.size()) {
            throw new IllegalArgumentException("No se pueden guardar estados incompletos de la instancia");
        }
        if (geometry.size() > 1_500_000) {
            throw new IllegalArgumentException("La instancia supera el límite de bloques");
        }
        Path directory = playerDirectory(playerId);
        Path metadata = metadataPath(playerId, id);
        Path blocks = blocksPath(playerId, id);
        try {
            Files.createDirectories(directory);
            Path temporaryBlocks = AtomicStorage.temporary(blocks);
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporaryBlocks))
            ))) {
                output.writeInt(geometry.size());
                for (int index = 0; index < geometry.size(); index++) {
                    output.writeLong(geometry.packedPosition(index));
                    output.writeInt(stateIds[index]);
                }
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", FORMAT_VERSION);
            root.addProperty("id", id);
            root.addProperty("updatedAt", System.currentTimeMillis());
            root.addProperty("blockCount", geometry.size());
            root.addProperty("blocksSha256", AtomicStorage.sha256(temporaryBlocks));
            root.add("recipe", RecipeStore.toJson(recipe));
            JsonObject pathJson = new JsonObject();
            pathJson.addProperty("closed", path.closed());
            pathJson.addProperty("source", path.source());
            JsonArray points = new JsonArray();
            for (BlockVector3 point : path.points()) {
                JsonArray coordinates = new JsonArray();
                coordinates.add(point.x());
                coordinates.add(point.y());
                coordinates.add(point.z());
                points.add(coordinates);
            }
            pathJson.add("points", points);
            root.add("path", pathJson);
            Path temporaryMetadata = AtomicStorage.temporary(metadata);
            Files.writeString(temporaryMetadata, GSON.toJson(root), StandardCharsets.UTF_8);
            AtomicStorage.replace(temporaryBlocks, blocks);
            AtomicStorage.replace(temporaryMetadata, metadata);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar la instancia: " + exception.getMessage(), exception);
        }
    }

    public static ProceduralInstance load(UUID playerId, String id) {
        Path metadata = metadataPath(playerId, id);
        Path blocks = blocksPath(playerId, id);
        if (!Files.isRegularFile(metadata) || !Files.isRegularFile(blocks)) {
            throw new IllegalArgumentException("No existe la instancia seleccionada");
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
            int version = root.get("version").getAsInt();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IllegalArgumentException("Versión de instancia no compatible");
            }
            if (version >= 3) {
                root = recoverConsistentPair(metadata, blocks, root);
            }
            JsonObject recipeJson = root.getAsJsonObject("recipe");
            ProceduralRecipe recipe = RecipeStore.fromJson(recipeJson, recipeJson.get("version").getAsInt());
            JsonObject pathJson = root.getAsJsonObject("path");
            List<BlockVector3> points = new ArrayList<>();
            pathJson.getAsJsonArray("points").forEach(element -> {
                JsonArray coordinates = element.getAsJsonArray();
                points.add(BlockVector3.at(
                        coordinates.get(0).getAsInt(),
                        coordinates.get(1).getAsInt(),
                        coordinates.get(2).getAsInt()
                ));
            });
            long[] positions;
            int[] stateIds;
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(blocks))
            ))) {
                int size = input.readInt();
                if (size < 0 || size > 1_500_000) {
                    throw new IllegalArgumentException("Cantidad de bloques de instancia no válida");
                }
                positions = new long[size];
                stateIds = new int[size];
                for (int index = 0; index < size; index++) {
                    positions[index] = input.readLong();
                    stateIds[index] = version >= 2 ? input.readInt() : -1;
                }
            }
            return new ProceduralInstance(
                    root.get("id").getAsString(),
                    recipe,
                    new PathSnapshot(points, pathJson.get("closed").getAsBoolean(), pathJson.get("source").getAsString()),
                    positions,
                    stateIds,
                    root.get("updatedAt").getAsLong()
            );
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("No se pudo cargar la instancia: " + exception.getMessage(), exception);
        }
    }

    public static List<ProceduralInstanceSummary> list(UUID playerId) {
        Path directory = playerDirectory(playerId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            List<Path> paths = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(ProceduralInstanceStore::modified).reversed())
                    .limit(128)
                    .toList();
            return TolerantListing.readAll(paths, path -> summary(playerId, path), "la instancia");
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron listar las instancias: " + exception.getMessage(), exception);
        }
    }

    private static ProceduralInstanceSummary summary(UUID playerId, Path metadata) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(metadata, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            int version = root.get("version").getAsInt();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IllegalArgumentException("Versión de instancia no compatible");
            }
            JsonObject recipeJson = root.getAsJsonObject("recipe");
            ProceduralRecipe recipe = RecipeStore.fromJson(recipeJson, recipeJson.get("version").getAsInt());
            String id = root.get("id").getAsString();
            int blockCount = root.has("blockCount")
                    ? root.get("blockCount").getAsInt()
                    : legacyBlockCount(playerId, id);
            int pathPoints = root.getAsJsonObject("path").getAsJsonArray("points").size();
            return new ProceduralInstanceSummary(
                    id,
                    recipe,
                    pathPoints,
                    blockCount,
                    root.get("updatedAt").getAsLong()
            );
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "No se pudo leer el resumen " + metadata.getFileName() + ": " + exception.getMessage(),
                    exception
            );
        }
    }

    private static int legacyBlockCount(UUID playerId, String id) {
        Path blocks = blocksPath(playerId, id);
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(blocks))
        ))) {
            int size = input.readInt();
            if (size < 0 || size > 1_500_000) {
                throw new IllegalArgumentException("Cantidad de bloques de instancia no válida");
            }
            return size;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer la geometría antigua: " + exception.getMessage(), exception);
        }
    }

    public static void delete(UUID playerId, String id) {
        Path metadata = metadataPath(playerId, id);
        Path blocks = blocksPath(playerId, id);
        try {
            deleteWithRecoveryFiles(metadata);
            deleteWithRecoveryFiles(blocks);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo eliminar la instancia: " + exception.getMessage(), exception);
        }
    }

    private static JsonObject recoverConsistentPair(Path metadata, Path blocks, JsonObject root) throws IOException {
        String expected = root.get("blocksSha256").getAsString();
        if (AtomicStorage.sha256(blocks).equals(expected)) {
            return root;
        }

        Path previousBlocks = AtomicStorage.backup(blocks);
        if (Files.isRegularFile(previousBlocks) && AtomicStorage.sha256(previousBlocks).equals(expected)) {
            Files.copy(previousBlocks, blocks, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return root;
        }

        Path previousMetadata = AtomicStorage.backup(metadata);
        if (Files.isRegularFile(previousMetadata)) {
            JsonObject previous = JsonParser.parseString(
                    Files.readString(previousMetadata, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            if (previous.has("blocksSha256")
                    && AtomicStorage.sha256(blocks).equals(previous.get("blocksSha256").getAsString())) {
                Files.copy(previousMetadata, metadata, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return previous;
            }
        }
        throw new IllegalArgumentException("The instance geometry checksum does not match");
    }

    private static void deleteWithRecoveryFiles(Path target) throws IOException {
        Files.deleteIfExists(target);
        Files.deleteIfExists(AtomicStorage.temporary(target));
        Files.deleteIfExists(AtomicStorage.backup(target));
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
            throw new IllegalStateException("No se pudieron limpiar las instancias de la sesión", exception);
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static Path metadataPath(UUID playerId, String id) {
        validateId(id);
        return playerDirectory(playerId).resolve(id + ".json");
    }

    private static Path blocksPath(UUID playerId, String id) {
        validateId(id);
        return playerDirectory(playerId).resolve(id + ".blocks.gz");
    }

    private static Path playerDirectory(UUID playerId) {
        return ProjectStorage.namespace(playerId, "instances");
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) {
            throw new IllegalArgumentException("Identificador de instancia no válido");
        }
    }
}
