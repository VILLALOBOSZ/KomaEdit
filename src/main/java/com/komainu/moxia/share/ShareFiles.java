package com.komainu.moxia.share;

import com.komainu.moxia.persistence.AtomicStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Las carpetas de los paquetes en archivo: {@code config/komaedit/exports} (lo que se exporta) y
 * {@code config/komaedit/imports} (lo que se deja para importar: paquetes {@code .kpack}, códigos en {@code .txt} y
 * esquemas sueltos, {@code .litematic}, {@code .nbt}, {@code .schem} o {@code .schematic}). Solo se lee y se escribe
 * dentro de ellas: un nombre con {@code ..}, una barra o un enlace que apunta fuera no vale.
 */
public final class ShareFiles {
    /**
     * Lo que se puede escribir como nombre de archivo en la orden: letras (también con tilde), números, espacios y
     * {@code . _ - ( ) [ ] + ' & , ! #}, sin barras ni dos puntos. Los de Litematica suelen llevar espacios.
     */
    private static final Pattern FILE_NAME = Pattern.compile("[\\p{L}\\p{N}_-][\\p{L}\\p{N} ._()\\[\\]+'&,!#-]{0,95}");
    /** Lo que se prueba detrás de un nombre escrito sin extensión. */
    private static final List<String> EXTENSIONS = List.of(ShareCodec.FILE_EXTENSION, ".litematic", ".nbt", ".schem",
            ".schematic", ".txt");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final int MAX_LISTED = 200;

    private final Path exports;
    private final Path imports;

    /** @param root {@code config/komaedit} */
    public ShareFiles(Path root) {
        this.exports = root.resolve("exports").toAbsolutePath().normalize();
        this.imports = root.resolve("imports").toAbsolutePath().normalize();
    }

    public Path exports() {
        return exports;
    }

    public Path imports() {
        return imports;
    }

    /**
     * Escribe un paquete en {@code exports} con un nombre que no pisa a otro: {@code rocas-20261021-153000.kpack}.
     */
    public Path writeExport(String packName, byte[] data, Instant now) throws IOException {
        Files.createDirectories(exports);
        String base = slug(packName) + "-" + STAMP.format(now);
        Path target = exports.resolve(base + ShareCodec.FILE_EXTENSION);
        for (int suffix = 2; Files.exists(target, LinkOption.NOFOLLOW_LINKS); suffix++) {
            target = exports.resolve(base + "-" + suffix + ShareCodec.FILE_EXTENSION);
        }
        Path temporary = AtomicStorage.temporary(target);
        Files.write(temporary, data);
        AtomicStorage.replace(temporary, target);
        return target;
    }

    /**
     * Escribe un esquema exportado en {@code exports} sin pisar otro: {@code torre.litematic}, {@code torre-2.litematic}…
     *
     * @param base      nombre sin extensión, ya limpio ({@code [a-z0-9_-]})
     * @param extension {@code .litematic}, {@code .nbt} o {@code .schem}
     */
    public Path writeSchematic(String base, String extension, byte[] data) throws IOException {
        Files.createDirectories(exports);
        Path target = exports.resolve(base + extension);
        for (int suffix = 2; Files.exists(target, LinkOption.NOFOLLOW_LINKS); suffix++) {
            target = exports.resolve(base + "-" + suffix + extension);
        }
        if (!target.normalize().startsWith(exports)) {
            throw new IllegalArgumentException("Nombre de archivo no válido");
        }
        AtomicStorage.requireWritableName(target);
        Path temporary = AtomicStorage.temporary(target);
        Files.write(temporary, data);
        AtomicStorage.replace(temporary, target);
        return target;
    }

    /** Los archivos que hay en {@code imports}, por nombre. */
    public List<String> importable() {
        if (!Files.isDirectory(imports, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(imports)) {
            return files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .map(file -> file.getFileName().toString())
                    .filter(name -> FILE_NAME.matcher(name).matches())
                    .sorted()
                    .limit(MAX_LISTED)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo listar " + imports.getFileName() + ": "
                    + exception.getMessage(), exception);
        }
    }

    /** Un archivo leído de {@code imports}: su nombre de verdad y sus bytes. */
    public record Read(String fileName, byte[] data) {
    }

    /**
     * Lee un archivo de {@code imports} por su nombre (con o sin {@code .kpack}).
     *
     * @param max bytes como mucho
     */
    public byte[] readImport(String name, long max) throws IOException {
        return read(name, max).data();
    }

    /** Como {@link #readImport}, con el nombre que tiene en la carpeta (el escrito puede no llevar extensión). */
    public Read read(String name, long max) throws IOException {
        Path file = resolveImport(name);
        long size = Files.size(file);
        if (size > max) {
            throw new IllegalArgumentException("«" + file.getFileName() + "» ocupa " + size / 1024
                    + " KiB; el máximo es " + max / 1024 + " KiB");
        }
        return new Read(file.getFileName().toString(), Files.readAllBytes(file));
    }

    /**
     * Separa el archivo de la región en {@code casa grande.litematic Unnamed 1}: si el texto entero no es un archivo,
     * el archivo es el trozo más largo del principio que exista y lo que sigue, la región.
     *
     * @return {@code [archivo, región]}; la región, vacía si no hay
     */
    public String[] fileAndRegion(String input) {
        String value = input == null ? "" : input.strip();
        if (exists(value)) {
            return new String[]{value, ""};
        }
        // Las regiones de Litematica también llevan espacios («Unnamed 1»): vale el archivo más largo que exista.
        for (int space = value.lastIndexOf(' '); space > 0; space = value.lastIndexOf(' ', space - 1)) {
            String file = value.substring(0, space).strip();
            if (exists(file)) {
                return new String[]{file, value.substring(space + 1).strip()};
            }
        }
        return new String[]{value, ""};
    }

    private boolean exists(String name) {
        try {
            resolveImport(name);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    /** El archivo de {@code imports} con ese nombre, comprobando que de verdad está dentro. */
    Path resolveImport(String name) throws IOException {
        String value = name == null ? "" : name.strip();
        if (!FILE_NAME.matcher(value).matches() || value.contains("..")) {
            throw new IllegalArgumentException("Nombre de archivo no válido: usa letras, números, espacios y . _ - ( )");
        }
        Path file = imports.resolve(value).normalize();
        for (int index = 0; index < EXTENSIONS.size() && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
             index++) {
            if (!value.toLowerCase(Locale.ROOT).endsWith(EXTENSIONS.get(index))) {
                file = imports.resolve(value + EXTENSIONS.get(index)).normalize();
            }
        }
        if (!file.startsWith(imports) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("No hay ningún «" + value + "» en config/komaedit/imports");
        }
        Path real = file.toRealPath();
        if (!real.startsWith(imports.toRealPath())) {
            throw new IllegalArgumentException("«" + value + "» apunta fuera de config/komaedit/imports");
        }
        return file;
    }

    /** {@code Casas de Villa} → {@code casas_de_villa}. */
    static String slug(String name) {
        StringBuilder slug = new StringBuilder();
        for (char character : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (slug.length() >= 40) {
                break;
            }
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '-') {
                slug.append(character);
            } else if (!slug.isEmpty() && slug.charAt(slug.length() - 1) != '_') {
                slug.append('_');
            }
        }
        while (!slug.isEmpty() && slug.charAt(slug.length() - 1) == '_') {
            slug.setLength(slug.length() - 1);
        }
        return slug.isEmpty() ? "paquete" : slug.toString();
    }
}
