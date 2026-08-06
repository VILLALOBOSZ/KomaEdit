package com.komainu.moxia.persistence;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.KomaEditConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ProjectStorage {
    private static final int FORMAT_VERSION = 1;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);
    private static final Path LEGACY_ROOT = FabricLoader.getInstance().getConfigDir().resolve("komaedit");
    private static final Map<UUID, Path> PLAYER_ROOTS = new ConcurrentHashMap<>();

    private ProjectStorage() {
    }

    public static void bind(UUID playerId, MinecraftServer server) {
        Path projectRoot = server.getSavePath(WorldSavePath.ROOT).resolve("komaedit");
        Path playerRoot = projectRoot.resolve("players").resolve(playerId.toString());
        try {
            Files.createDirectories(playerRoot);
            writeManifest(projectRoot);
            AtomicStorage.recoverTree(playerRoot);
            migrateLegacy(playerId, playerRoot);
            PLAYER_ROOTS.put(playerId, playerRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize the KomaEdit project: " + exception.getMessage(), exception);
        }
    }

    public static void unbind(UUID playerId) {
        PLAYER_ROOTS.remove(playerId);
    }

    public static void clearBindings() {
        PLAYER_ROOTS.clear();
    }

    public static Path namespace(UUID playerId, String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("Invalid project namespace");
        }
        return playerRoot(playerId).resolve(name);
    }

    public static ProjectStatus status(UUID playerId) {
        Path playerRoot = playerRoot(playerId);
        try {
            long recipes = count(playerRoot.resolve("recipes"), ".json");
            long instances = count(playerRoot.resolve("instances"), ".json");
            long jobs = count(playerRoot.resolve("jobs"), ".json");
            long interruptedJobs = countText(playerRoot.resolve("jobs"), "\"status\": \"INTERRUPTED\"");
            long bytes;
            try (var paths = Files.walk(playerRoot, 5)) {
                bytes = paths.filter(Files::isRegularFile).mapToLong(ProjectStorage::size).sum();
            }
            return new ProjectStatus(playerRoot, recipes, instances, jobs, interruptedJobs, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect the KomaEdit project", exception);
        }
    }

    public static Path backup(UUID playerId) {
        Path playerRoot = playerRoot(playerId);
        Path projectRoot = playerRoot.getParent().getParent();
        Path backups = projectRoot.resolve("backups");
        Path target = backups.resolve(BACKUP_TIME.format(Instant.now()) + "-" + playerId + ".zip");
        try {
            Files.createDirectories(backups);
            Path temporary = AtomicStorage.temporary(target);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8);
                 var paths = Files.walk(playerRoot, 5)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    String relative = playerRoot.relativize(file).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(relative));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            AtomicStorage.replace(temporary, target);
            pruneBackups(backups, playerId, KomaEditConfig.settings().backupRetention());
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the KomaEdit project backup", exception);
        }
    }

    private static Path playerRoot(UUID playerId) {
        Path root = PLAYER_ROOTS.get(playerId);
        if (root == null) {
            throw new IllegalStateException("The KomaEdit project is not initialized for this player");
        }
        return root;
    }

    private static void writeManifest(Path projectRoot) throws IOException {
        Path manifest = projectRoot.resolve("project.json");
        if (Files.isRegularFile(manifest)) {
            return;
        }
        String json = "{\n"
                + "  \"format\": " + FORMAT_VERSION + ",\n"
                + "  \"createdAt\": \"" + Instant.now() + "\"\n"
                + "}\n";
        AtomicStorage.writeString(manifest, json);
    }

    private static void migrateLegacy(UUID playerId, Path playerRoot) throws IOException {
        Path marker = playerRoot.resolve(".legacy-migration-v1");
        if (Files.exists(marker)) {
            return;
        }
        migrateDirectory(LEGACY_ROOT.resolve("recipes").resolve(playerId.toString()), playerRoot.resolve("recipes"));
        migrateDirectory(LEGACY_ROOT.resolve("instances").resolve(playerId.toString()), playerRoot.resolve("instances"));
        Files.writeString(marker, Instant.now().toString(), StandardCharsets.UTF_8);
    }

    private static void migrateDirectory(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        Files.createDirectories(target);
        try (var paths = Files.list(source)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                Path destination = target.resolve(file.getFileName().toString());
                if (!Files.exists(destination)) {
                    Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static long count(Path directory, String suffix) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .count();
        }
    }

    private static long countText(Path directory, String value) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var paths = Files.list(directory)) {
            long count = 0;
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (Files.readString(path, StandardCharsets.UTF_8).contains(value)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static void pruneBackups(Path directory, UUID playerId, int retained) throws IOException {
        try (var paths = Files.list(directory)) {
            var backups = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("-" + playerId + ".zip"))
                    .sorted(Comparator.comparingLong(ProjectStorage::modified).reversed())
                    .toList();
            for (int index = retained; index < backups.size(); index++) {
                Files.deleteIfExists(backups.get(index));
            }
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            KomaEditMod.LOGGER.debug("Could not inspect backup timestamp for {}", path, exception);
            return 0L;
        }
    }

    public record ProjectStatus(
            Path root,
            long recipes,
            long instances,
            long jobs,
            long interruptedJobs,
            long bytes
    ) {
    }
}
