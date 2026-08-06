package com.komainu.moxia.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public final class OperationLedger {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private OperationLedger() {
    }

    public static String start(UUID playerId, String description, long total) {
        String id = UUID.randomUUID().toString();
        JsonObject root = base(id, description, total);
        root.addProperty("status", "RUNNING");
        root.addProperty("processed", 0L);
        root.addProperty("changed", 0);
        root.addProperty("protected", 0);
        root.addProperty("startedAt", Instant.now().toString());
        write(playerId, id, root);
        return id;
    }

    public static void progress(UUID playerId, String id, String description, long processed, long total, int changed) {
        JsonObject root = readOrBase(playerId, id, description, total);
        root.addProperty("status", "RUNNING");
        root.addProperty("processed", processed);
        root.addProperty("changed", changed);
        root.addProperty("updatedAt", Instant.now().toString());
        write(playerId, id, root);
    }

    public static void finish(
            UUID playerId,
            String id,
            String description,
            long processed,
            long total,
            int changed,
            int protectedBlocks,
            boolean cancelled
    ) {
        JsonObject root = readOrBase(playerId, id, description, total);
        root.addProperty("status", cancelled ? "CANCELLED" : "COMPLETED");
        root.addProperty("processed", processed);
        root.addProperty("changed", changed);
        root.addProperty("protected", protectedBlocks);
        root.addProperty("finishedAt", Instant.now().toString());
        write(playerId, id, root);
    }

    public static int markInterrupted(UUID playerId) {
        Path directory = ProjectStorage.namespace(playerId, "jobs");
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        int interrupted = 0;
        try (var paths = Files.list(directory)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (!"RUNNING".equals(root.get("status").getAsString())) {
                    continue;
                }
                root.addProperty("status", "INTERRUPTED");
                root.addProperty("interruptedAt", Instant.now().toString());
                AtomicStorage.writeString(file, GSON.toJson(root));
                interrupted++;
            }
            return interrupted;
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not recover interrupted KomaEdit jobs for {}", playerId, exception);
            return interrupted;
        }
    }

    private static JsonObject base(String id, String description, long total) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("id", id);
        root.addProperty("description", description);
        root.addProperty("total", total);
        return root;
    }

    private static JsonObject readOrBase(UUID playerId, String id, String description, long total) {
        Path file = path(playerId, id);
        if (!Files.isRegularFile(file)) {
            return base(id, description, total);
        }
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not read operation ledger {}; recreating it", id, exception);
            return base(id, description, total);
        }
    }

    private static void write(UUID playerId, String id, JsonObject root) {
        try {
            AtomicStorage.writeString(path(playerId, id), GSON.toJson(root));
        } catch (IOException exception) {
            KomaEditMod.LOGGER.warn("Could not persist operation ledger {}", id, exception);
        }
    }

    private static Path path(UUID playerId, String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) {
            throw new IllegalArgumentException("Invalid operation id");
        }
        return ProjectStorage.namespace(playerId, "jobs").resolve(id + ".json");
    }
}
