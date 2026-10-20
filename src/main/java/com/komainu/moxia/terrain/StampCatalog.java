package com.komainu.moxia.terrain;

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
 * Los sellos de una carpeta ({@code config/komaedit/heightmaps/}): los PNG que haya y los de serie. Un PNG con el
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

    /** Nombres disponibles: los de serie y los PNG de la carpeta, sin repetir y en orden alfabético. */
    public List<String> names() {
        TreeSet<String> names = new TreeSet<>(StampLibrary.descriptions().keySet());
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.png")) {
                for (Path file : files) {
                    String fileName = file.getFileName().toString();
                    names.add(fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT));
                }
            } catch (IOException ignored) {
                // Sin carpeta legible quedan los de serie.
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Carga un sello por nombre (sin {@code .png}): primero el archivo de la carpeta y si no, el de serie. El nombre
     * solo admite letras, números, guiones y subrayados, para no salir de la carpeta.
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
        long modified = Files.getLastModifiedTime(file).toMillis();
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("El PNG " + file.getFileName() + " pasa de 8 MB");
        }
        Cached cached = cache.get(file);
        if (cached != null && cached.modified == modified && cached.size == size) {
            return cached.stamp;
        }
        HeightStamp stamp;
        try (InputStream stream = Files.newInputStream(file)) {
            stamp = StampImages.read(name, stream);
        }
        cache.put(file, new Cached(modified, size, stamp));
        return stamp;
    }

    static String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta el sello, por ejemplo montana, crater o duna");
        }
        String name = input.strip().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
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
        Path exact = directory.resolve(name + ".png");
        if (Files.isRegularFile(exact)) {
            return exact;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.png")) {
            for (Path file : files) {
                if (file.getFileName().toString().equalsIgnoreCase(name + ".png")) {
                    return file;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }
}
