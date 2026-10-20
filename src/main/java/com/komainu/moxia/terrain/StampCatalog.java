package com.komainu.moxia.terrain;

import com.komainu.moxia.image.ImageFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los sellos de una carpeta ({@code config/komaedit/heightmaps/}): las imágenes que haya y los de serie. Una con el
 * nombre de uno de serie lo sustituye, así se pueden retocar. Los archivos leídos se guardan en memoria mientras no
 * cambien.
 */
public final class StampCatalog {
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    private final Path directory;
    private final Map<Path, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(long modified, long size, HeightStamp stamp) {
    }

    public StampCatalog(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    /** Escribe en la carpeta los sellos de serie que falten. Devuelve cuántos ha escrito. */
    public int writeDefaults() throws IOException {
        Files.createDirectories(directory);
        int written = 0;
        for (String name : StampLibrary.descriptions().keySet()) {
            Path file = directory.resolve(name + ".png");
            if (Files.exists(file)) {
                continue;
            }
            Path temporary = directory.resolve(name + ".png.tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                StampImages.write(StampLibrary.get(name), output);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            written++;
        }
        return written;
    }

    /** Nombres disponibles: los de serie y las imágenes de la carpeta, sin repetir y en orden alfabético. */
    public List<String> names() {
        TreeSet<String> names = new TreeSet<>(StampLibrary.descriptions().keySet());
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    String base = baseName(file);
                    if (base != null && base.matches("[a-z0-9_\\-]{1,64}")) {
                        names.add(base);
                    }
                }
            } catch (IOException ignored) {
                // Sin carpeta legible quedan los de serie.
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Carga un sello por nombre (sin extensión): primero la imagen de la carpeta y si no, el de serie. Valen los
     * mismos formatos que {@code /komaedit image} (PNG, JPG y GIF); el PNG de 16 bits conserva toda su precisión.
     * El nombre solo admite letras, números, guiones y subrayados, y un enlace que apunte fuera de la carpeta no se
     * sigue.
     */
    public HeightStamp load(String input) throws IOException {
        String name = normalize(input);
        Path file = findFile(name);
        if (file == null) {
            if (StampLibrary.has(name)) {
                return StampLibrary.get(name);
            }
            throw new IllegalArgumentException("No hay ningún sello «" + name + "»; mira /komaedit terreno sellos o"
                    + " deja un PNG en " + directory);
        }
        Path real = file.toRealPath();
        if (!real.startsWith(directory.toRealPath())) {
            throw new IllegalArgumentException("El sello " + name + " apunta fuera de " + directory);
        }
        long modified = Files.getLastModifiedTime(real).toMillis();
        long size = Files.size(real);
        if (size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("La imagen " + file.getFileName() + " pasa de 8 MB");
        }
        Cached cached = cache.get(real);
        if (cached != null && cached.modified == modified && cached.size == size) {
            return cached.stamp;
        }
        HeightStamp stamp;
        try (InputStream stream = Files.newInputStream(real)) {
            stamp = StampImages.read(name, stream);
        }
        cache.put(real, new Cached(modified, size, stamp));
        return stamp;
    }

    /** Nombre sin extensión en minúsculas si es una imagen admitida; si no, {@code null}. */
    private static String baseName(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || !ImageFiles.EXTENSIONS.contains(fileName.substring(dot + 1))) {
            return null;
        }
        return fileName.substring(0, dot);
    }

    static String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta el sello, por ejemplo montana, crater o duna");
        }
        String name = input.strip().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot > 0 && ImageFiles.EXTENSIONS.contains(name.substring(dot + 1))) {
            name = name.substring(0, dot);
        }
        name = name.replace("ñ", "n").replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o")
                .replace("ú", "u");
        if (!name.matches("[a-z0-9_\\-]{1,64}")) {
            throw new IllegalArgumentException("Nombre de sello no válido: " + input
                    + " (solo letras, números, - y _)");
        }
        return name;
    }

    private Path findFile(String name) {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        for (String extension : ImageFiles.EXTENSIONS) {
            Path exact = directory.resolve(name + "." + extension);
            if (Files.isRegularFile(exact)) {
                return exact;
            }
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (name.equals(baseName(file)) && Files.isRegularFile(file)) {
                    return file;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }
}
