package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Carga un schematic de la carpeta de schematics de WorldEdit por su nombre, sin salir de esa carpeta. */
final class Schematics {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_./-]{1,128}");
    private static final String[] EXTENSIONS = {"", ".schem", ".schematic"};

    private Schematics() {
    }

    static Clipboard load(String name) {
        if (!VALID.matcher(name).matches() || name.contains("..") || name.startsWith("/")) {
            throw new IllegalArgumentException("Nombre de schematic inválido: " + name);
        }
        WorldEdit worldEdit = WorldEdit.getInstance();
        Path folder = worldEdit.getWorkingDirectoryPath(worldEdit.getConfiguration().saveDir).toAbsolutePath()
                .normalize();
        for (String extension : EXTENSIONS) {
            Path file = folder.resolve(name + extension).normalize();
            if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
                continue;
            }
            ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
            if (format == null) {
                continue;
            }
            try (InputStream input = Files.newInputStream(file); ClipboardReader reader = format.getReader(input)) {
                return reader.read();
            } catch (IOException exception) {
                throw new IllegalArgumentException("No se pudo leer el schematic " + name + ": "
                        + exception.getMessage());
            }
        }
        throw new IllegalArgumentException("No existe el schematic " + name);
    }
}
