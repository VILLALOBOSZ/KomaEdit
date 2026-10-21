package com.komainu.moxia.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class AtomicStorage {
    private static final String TEMPORARY_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".bak";

    private AtomicStorage() {
    }

    public static void writeString(Path target, String value) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = temporary(target);
        Files.writeString(temporary, value, StandardCharsets.UTF_8);
        replace(temporary, target);
    }

    public static void replace(Path temporary, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path backup = backup(target);
        if (Files.isRegularFile(target)) {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            move(temporary, target);
        } catch (IOException exception) {
            if (!Files.exists(target) && Files.isRegularFile(backup)) {
                Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            }
            throw exception;
        }
    }

    public static void recoverTree(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files;
        try (var paths = Files.walk(root, 5)) {
            files = paths.filter(Files::isRegularFile).toList();
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (name.endsWith(TEMPORARY_SUFFIX)) {
                Path target = file.resolveSibling(name.substring(0, name.length() - TEMPORARY_SUFFIX.length()));
                if (Files.exists(target)) {
                    Files.deleteIfExists(file);
                } else {
                    move(file, target);
                }
            }
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (!name.endsWith(BACKUP_SUFFIX)) {
                continue;
            }
            Path target = file.resolveSibling(name.substring(0, name.length() - BACKUP_SUFFIX.length()));
            if (!Files.exists(target)) {
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static Path temporary(Path target) {
        return target.resolveSibling(target.getFileName() + TEMPORARY_SUFFIX);
    }

    public static Path backup(Path target) {
        return target.resolveSibling(target.getFileName() + BACKUP_SUFFIX);
    }

    /**
     * Aparta un archivo que no se puede leer ({@code notes.json} → {@code notes.broken-1.json}) para que lo que se
     * guarde después no lo pise y se pueda recuperar a mano. Devuelve dónde quedó.
     */
    public static Path setAside(Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int number = 1; ; number++) {
            Path candidate = file.resolveSibling(stem + ".broken-" + number + extension);
            if (!Files.exists(candidate)) {
                Files.move(file, candidate);
                return candidate;
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
