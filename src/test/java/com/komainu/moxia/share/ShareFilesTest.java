package com.komainu.moxia.share;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareFilesTest {
    @TempDir
    Path root;

    @Test
    void exportsGoToTheirFolderWithoutOverwriting() throws IOException {
        ShareFiles files = new ShareFiles(root);
        Instant now = Instant.parse("2026-10-21T15:30:00Z");
        Path first = files.writeExport("Casas de Villa!", new byte[]{1}, now);
        Path second = files.writeExport("Casas de Villa!", new byte[]{2}, now);
        assertEquals(root.resolve("exports").toAbsolutePath(), first.getParent());
        assertEquals("casas_de_villa-20261021-153000.kpack", first.getFileName().toString());
        assertNotEquals(first, second);
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(first));
        assertEquals("paquete", ShareFiles.slug("../../§§"));
    }

    @Test
    void importsOnlyReadInsideTheirFolder() throws IOException {
        ShareFiles files = new ShareFiles(root);
        Files.createDirectories(files.imports());
        Files.write(files.imports().resolve("casas.kpack"), new byte[]{7, 7});
        Files.writeString(root.resolve("secreto.txt"), "no");
        Files.createDirectories(files.imports().resolve("carpeta"));
        assertArrayEquals(new byte[]{7, 7}, files.readImport("casas", 1024));
        assertArrayEquals(new byte[]{7, 7}, files.readImport("casas.kpack", 1024));
        assertEquals(List.of("casas.kpack"), files.importable());
        for (String evil : List.of("../secreto.txt", "..", "carpeta/../../secreto.txt", "/etc/passwd",
                "C:\\Windows\\win.ini", "..\\secreto.txt", ".hidden", "carpeta", "")) {
            ShareCodecTest.assertMessage("", () -> files.readImport(evil, 1024));
        }
        ShareCodecTest.assertMessage("KiB", () -> files.readImport("casas", 1));
    }

    @Test
    void linksThatPointOutsideAreRefused() throws IOException {
        ShareFiles files = new ShareFiles(root);
        Files.createDirectories(files.imports());
        Path outside = Files.writeString(root.resolve("secreto.txt"), "no");
        try {
            Files.createSymbolicLink(files.imports().resolve("enlace.kpack"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            // Windows sin permiso de crear enlaces: no hay nada que probar.
            return;
        }
        ShareCodecTest.assertMessage("", () -> files.readImport("enlace.kpack", 1024));
        assertTrue(files.importable().isEmpty());
    }
}
