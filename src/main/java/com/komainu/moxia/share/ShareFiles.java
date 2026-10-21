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
 * {@code config/komaedit/imports} (lo que se deja para importar). Solo se lee y se escribe dentro de ellas: un nombre
 * con {@code ..}, una barra o un enlace que apunta fuera no vale.
 */
public final class ShareFiles {
    /** Lo que se puede escribir como nombre de archivo en la orden. */
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}");
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

    /**
     * Lee un archivo de {@code imports} por su nombre (con o sin {@code .kpack}).
     *
     * @param max bytes como mucho
     */
    public byte[] readImport(String name, long max) throws IOException {
        Path file = resolveImport(name);
        long size = Files.size(file);
        if (size > max) {
            throw new IllegalArgumentException("«" + file.getFileName() + "» ocupa " + size / 1024
                    + " KiB; el máximo es " + max / 1024 + " KiB");
        }
        return Files.readAllBytes(file);
    }

    /** El archivo de {@code imports} con ese nombre, comprobando que de verdad está dentro. */
    Path resolveImport(String name) throws IOException {
        String value = name == null ? "" : name.strip();
        if (!FILE_NAME.matcher(value).matches() || value.contains("..")) {
            throw new IllegalArgumentException("Nombre de archivo no válido: usa solo letras, números, . _ y -");
        }
        Path file = imports.resolve(value).normalize();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && !value.endsWith(ShareCodec.FILE_EXTENSION)) {
            file = imports.resolve(value + ShareCodec.FILE_EXTENSION).normalize();
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
