package com.komainu.moxia.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.blueprint.BlueprintConfig;
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
    private static volatile Settings settings = Settings.DEFAULT;

    private KomaEditConfig() {
    }

    public static void load() {
        ImageConfig.load(directory());
        BlueprintConfig.load(directory());
        settings = load(file());
    }

    /**
     * Lee la configuración de {@code file}. Si no existe la crea con los valores por defecto; si no se puede leer
     * avisa en el registro y usa los de por defecto. Los campos que falten (una configuración de una versión
     * anterior) toman su valor por defecto.
     */
    static Settings load(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                AtomicStorage.writeString(file, toJson(Settings.DEFAULT));
                return Settings.DEFAULT;
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit config; safe defaults will be used", exception);
            return Settings.DEFAULT;
        }
    }

    /** Interpreta el JSON de {@code config.json}; lanza si el formato no es el de esta versión o un valor no vale. */
    static Settings parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : 1;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported KomaEdit config format: " + format);
        }
        return new Settings(
                integer(root, "maxBlocksPerOperation", Settings.DEFAULT.maxBlocksPerOperation()),
                integer(root, "maxConcurrentJobs", Settings.DEFAULT.maxConcurrentJobs()),
                bool(root, "rootPermissionGrantsAll", Settings.DEFAULT.rootPermissionGrantsAll()),
                integer(root, "backupRetention", Settings.DEFAULT.backupRetention()),
                integer(root, "confirmBuildAboveBlocks", Settings.DEFAULT.confirmBuildAboveBlocks()),
                integer(root, "previewTimeoutSeconds", Settings.DEFAULT.previewTimeoutSeconds()),
                integer(root, "previewMaxDisplays", Settings.DEFAULT.previewMaxDisplays()),
                integer(root, "arraySnapRadius", Settings.DEFAULT.arraySnapRadius())
        );
    }

    /** El archivo se busca al usarlo: así la clase se puede cargar fuera del juego (en las pruebas). */
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("komaedit").resolve("config.json");
    }

    /** Carpeta de la configuración de KomaEdit ({@code config/komaedit}). */
    public static Path directory() {
        return file().getParent();
    }

    /** Archivo de las paletas de serie del servidor. */
    public static Path palettesFile() {
        return directory().resolve("palettes.json");
    }

    public static Settings settings() {
        return settings;
    }

    static String toJson(Settings value) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("maxBlocksPerOperation", value.maxBlocksPerOperation());
        root.addProperty("maxConcurrentJobs", value.maxConcurrentJobs());
        root.addProperty("rootPermissionGrantsAll", value.rootPermissionGrantsAll());
        root.addProperty("backupRetention", value.backupRetention());
        root.addProperty("confirmBuildAboveBlocks", value.confirmBuildAboveBlocks());
        root.addProperty("previewTimeoutSeconds", value.previewTimeoutSeconds());
        root.addProperty("previewMaxDisplays", value.previewMaxDisplays());
        root.addProperty("arraySnapRadius", value.arraySnapRadius());
        return GSON.toJson(root);
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
            int previewTimeoutSeconds,
            int previewMaxDisplays,
            int arraySnapRadius
    ) {
        public static final int DEFAULT_ARRAY_SNAP_RADIUS = 96;
        public static final int MAX_ARRAY_SNAP_RADIUS = 256;
        /**
         * Por encima de {@code confirmBuildAboveBlocks} bloques, un Build pide confirmación antes de encolarse (0 no
         * pregunta nunca). Una vista previa caduca a los {@code previewTimeoutSeconds} segundos y enseña como mucho
         * {@code previewMaxDisplays} block displays (0: solo la caja).
         */
        public static final Settings DEFAULT = new Settings(1_500_000, 4, false, 5, 250_000, 90, 1_500,
                DEFAULT_ARRAY_SNAP_RADIUS);

        public Settings(int maxBlocksPerOperation, int maxConcurrentJobs, boolean rootPermissionGrantsAll,
                        int backupRetention) {
            this(maxBlocksPerOperation, maxConcurrentJobs, rootPermissionGrantsAll, backupRetention, 250_000);
        }

        public Settings(int maxBlocksPerOperation, int maxConcurrentJobs, boolean rootPermissionGrantsAll,
                        int backupRetention, int confirmBuildAboveBlocks) {
            this(maxBlocksPerOperation, maxConcurrentJobs, rootPermissionGrantsAll, backupRetention,
                    confirmBuildAboveBlocks, 90, 1_500, DEFAULT_ARRAY_SNAP_RADIUS);
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
            if (previewTimeoutSeconds < 10 || previewTimeoutSeconds > 3_600) {
                throw new IllegalArgumentException("previewTimeoutSeconds must be between 10 and 3600");
            }
            if (previewMaxDisplays < 0 || previewMaxDisplays > 10_000) {
                throw new IllegalArgumentException("previewMaxDisplays must be between 0 and 10000");
            }
            if (arraySnapRadius < 1 || arraySnapRadius > MAX_ARRAY_SNAP_RADIUS) {
                throw new IllegalArgumentException("arraySnapRadius must be between 1 and " + MAX_ARRAY_SNAP_RADIUS);
            }
        }
    }
}
