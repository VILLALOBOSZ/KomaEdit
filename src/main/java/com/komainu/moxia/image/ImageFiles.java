package com.komainu.moxia.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Imágenes de la carpeta del servidor ({@code config/komaedit/images/}). El nombre es relativo a esa carpeta (se
 * admiten subcarpetas con {@code /}) y nunca puede salir de ella: ni {@code ..}, ni rutas absolutas, ni enlaces que
 * apunten fuera.
 */
public final class ImageFiles {
    public static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg", "gif");
    private static final int MAX_LISTED = 100;

    private ImageFiles() {
    }

    /** Ruta del archivo dentro de la carpeta, comprobada. */
    public static Path resolve(Path directory, String name) {
        String text = name == null ? "" : name.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Falta el nombre de la imagen");
        }
        if (text.length() > 255 || text.contains("\\") || text.contains(":") || text.startsWith("/")
                || text.chars().anyMatch(character -> character < 0x20)) {
            throw new IllegalArgumentException("Nombre de imagen no válido: " + name + " (usa nombre.png o "
                    + "carpeta/nombre.png dentro de config/komaedit/images/)");
        }
        for (String part : text.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Nombre de imagen no válido: " + name);
            }
        }
        if (!EXTENSIONS.contains(extension(text))) {
            throw new IllegalArgumentException("La imagen tiene que ser .png, .jpg o .gif: " + name);
        }
        Path base = directory.toAbsolutePath().normalize();
        Path file = base.resolve(text).normalize();
        if (!file.startsWith(base)) {
            throw new IllegalArgumentException("Nombre de imagen no válido: " + name);
        }
        return file;
    }

    /** Lee el archivo con el límite de tamaño. */
    public static byte[] read(Path directory, String name, long maxBytes) {
        Path file = resolve(directory, name);
        try {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("No existe la imagen " + name + " en config/komaedit/images/");
            }
            Path base = directory.toRealPath();
            Path real = file.toRealPath();
            if (!real.startsWith(base)) {
                throw new IllegalArgumentException("La imagen " + name + " apunta fuera de config/komaedit/images/");
            }
            long size = Files.size(real);
            if (size > maxBytes) {
                throw ImageDownloader.tooLarge(maxBytes);
            }
            byte[] data = Files.readAllBytes(real);
            if (data.length > maxBytes) {
                throw ImageDownloader.tooLarge(maxBytes);
            }
            return data;
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer la imagen " + name + ": " + exception.getMessage());
        }
    }

    /** Imágenes disponibles (hasta 100, con subcarpetas de un nivel), para autocompletar. */
    public static List<String> list(Path directory) {
        List<String> names = new ArrayList<>();
        if (directory == null || !Files.isDirectory(directory)) {
            return names;
        }
        Path base = directory.toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(base, 2)) {
            files.filter(Files::isRegularFile)
                    .map(path -> base.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'))
                    .filter(path -> EXTENSIONS.contains(extension(path)) && !path.contains(" "))
                    .sorted()
                    .limit(MAX_LISTED)
                    .forEach(names::add);
        } catch (IOException | RuntimeException ignored) {
            // Sin lista: el jugador puede escribir el nombre igual.
        }
        return names;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
