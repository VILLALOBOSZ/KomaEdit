package com.komainu.moxia.image;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.persistence.AtomicStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Carga {@code config/komaedit/images.json} y crea la carpeta {@code config/komaedit/images/} donde el admin deja
 * las imágenes. Se recarga con {@code /komaedit reload}, junto con el resto de la configuración.
 */
public final class ImageConfig {
    private static volatile ImageSettings settings = ImageSettings.DEFAULT;
    private static volatile Path directory;

    private ImageConfig() {
    }

    public static void load(Path configDirectory) {
        Path file = configDirectory.resolve("images.json");
        directory = configDirectory.resolve("images");
        try {
            Files.createDirectories(directory);
            if (!Files.isRegularFile(file)) {
                AtomicStorage.writeString(file, ImageSettings.DEFAULT.toJson());
                settings = ImageSettings.DEFAULT;
                return;
            }
            settings = ImageSettings.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit images.json; URLs stay disabled", exception);
            settings = ImageSettings.DEFAULT;
        }
    }

    public static ImageSettings settings() {
        return settings;
    }

    /** Carpeta de las imágenes del servidor ({@code config/komaedit/images}). */
    public static Path directory() {
        Path current = directory;
        if (current == null) {
            throw new IllegalStateException("La configuración de imágenes aún no se ha cargado");
        }
        return current;
    }
}
