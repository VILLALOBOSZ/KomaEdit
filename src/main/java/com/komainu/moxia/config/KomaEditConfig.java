package com.komainu.moxia.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.image.ImageConfig;
import com.komainu.moxia.persistence.AtomicStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class KomaEditConfig {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("komaedit")
            .resolve("config.json");
    private static volatile Settings settings = Settings.DEFAULT;

    private KomaEditConfig() {
    }

    public static void load() {
        ImageConfig.load(directory());
        try {
            if (!Files.isRegularFile(FILE)) {
                save(Settings.DEFAULT);
                settings = Settings.DEFAULT;
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            int format = root.has("format") ? root.get("format").getAsInt() : 1;
            if (format != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported KomaEdit config format: " + format);
            }
            settings = new Settings(
                    integer(root, "maxBlocksPerOperation", Settings.DEFAULT.maxBlocksPerOperation()),
                    integer(root, "maxConcurrentJobs", Settings.DEFAULT.maxConcurrentJobs()),
                    bool(root, "rootPermissionGrantsAll", Settings.DEFAULT.rootPermissionGrantsAll()),
                    integer(root, "backupRetention", Settings.DEFAULT.backupRetention()),
                    integer(root, "confirmBuildAboveBlocks", Settings.DEFAULT.confirmBuildAboveBlocks()),
                    integer(root, "arraySnapRadius", Settings.DEFAULT.arraySnapRadius())
            );
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit config; safe defaults will be used", exception);
            settings = Settings.DEFAULT;
        }
    }

    /** Carpeta de la configuración de KomaEdit ({@code config/komaedit}). */
    public static Path directory() {
        return FILE.getParent();
    }

    /** Archivo de las paletas de serie del servidor. */
    public static Path palettesFile() {
        return directory().resolve("palettes.json");
    }

    public static Settings settings() {
        return settings;
    }

    private static void save(Settings value) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("maxBlocksPerOperation", value.maxBlocksPerOperation());
        root.addProperty("maxConcurrentJobs", value.maxConcurrentJobs());
        root.addProperty("rootPermissionGrantsAll", value.rootPermissionGrantsAll());
        root.addProperty("backupRetention", value.backupRetention());
        root.addProperty("confirmBuildAboveBlocks", value.confirmBuildAboveBlocks());
        root.addProperty("arraySnapRadius", value.arraySnapRadius());
        AtomicStorage.writeString(FILE, GSON.toJson(root));
    }

    private static int integer(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    /**
     * @param confirmBuildAboveBlocks por encima de estos bloques, un Build pide confirmación antes de encolarse; 0 no
     *                                pregunta nunca
     * @param arraySnapRadius         hasta dónde busca superficie la bandera {@code -b} de {@code array}, en bloques
     */
    public record Settings(
            int maxBlocksPerOperation,
            int maxConcurrentJobs,
            boolean rootPermissionGrantsAll,
            int backupRetention,
            int confirmBuildAboveBlocks,
            int arraySnapRadius
    ) {
        public static final int DEFAULT_ARRAY_SNAP_RADIUS = 96;
        public static final int MAX_ARRAY_SNAP_RADIUS = 256;
        public static final Settings DEFAULT = new Settings(1_500_000, 4, false, 5, 250_000,
                DEFAULT_ARRAY_SNAP_RADIUS);

        public Settings(int maxBlocksPerOperation, int maxConcurrentJobs, boolean rootPermissionGrantsAll,
                        int backupRetention) {
            this(maxBlocksPerOperation, maxConcurrentJobs, rootPermissionGrantsAll, backupRetention, 250_000);
        }

        public Settings(int maxBlocksPerOperation, int maxConcurrentJobs, boolean rootPermissionGrantsAll,
                        int backupRetention, int confirmBuildAboveBlocks) {
            this(maxBlocksPerOperation, maxConcurrentJobs, rootPermissionGrantsAll, backupRetention,
                    confirmBuildAboveBlocks, DEFAULT_ARRAY_SNAP_RADIUS);
        }

        public Settings {
            if (maxBlocksPerOperation < 10_000 || maxBlocksPerOperation > 10_000_000) {
                throw new IllegalArgumentException("maxBlocksPerOperation must be between 10000 and 10000000");
            }
            if (maxConcurrentJobs < 1 || maxConcurrentJobs > 64) {
                throw new IllegalArgumentException("maxConcurrentJobs must be between 1 and 64");
            }
            if (backupRetention < 1 || backupRetention > 50) {
                throw new IllegalArgumentException("backupRetention must be between 1 and 50");
            }
            if (confirmBuildAboveBlocks < 0 || confirmBuildAboveBlocks > 10_000_000) {
                throw new IllegalArgumentException("confirmBuildAboveBlocks must be between 0 and 10000000");
            }
            if (arraySnapRadius < 1 || arraySnapRadius > MAX_ARRAY_SNAP_RADIUS) {
                throw new IllegalArgumentException("arraySnapRadius must be between 1 and " + MAX_ARRAY_SNAP_RADIUS);
            }
        }
    }
}
