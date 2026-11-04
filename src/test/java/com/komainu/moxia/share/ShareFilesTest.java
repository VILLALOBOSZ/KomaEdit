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
    void schematicsWithSpacesAndRegionsAreFound() throws IOException {
        ShareFiles files = new ShareFiles(root);
        Files.createDirectories(files.imports());
        Files.write(files.imports().resolve("Casa Pequeña (v2).litematic"), new byte[]{1});
        Files.write(files.imports().resolve("igloo.nbt"), new byte[]{2});
        Files.write(files.imports().resolve("torre.schem"), new byte[]{3});
        assertEquals("Casa Pequeña (v2).litematic", files.read("Casa Pequeña (v2)", 1024).fileName(),
                "sin extensión, se prueban las de los esquemas");
        assertArrayEquals(new byte[]{2}, files.readImport("igloo", 1024));
        assertArrayEquals(new byte[]{3}, files.readImport("torre.schem", 1024));
        assertArrayEquals(new String[]{"Casa Pequeña (v2).litematic", "tejado"},
                files.fileAndRegion("Casa Pequeña (v2).litematic tejado"));
        assertArrayEquals(new String[]{"Casa Pequeña (v2).litematic", ""},
                files.fileAndRegion(" Casa Pequeña (v2).litematic "));
        assertArrayEquals(new String[]{"igloo", "norte"}, files.fileAndRegion("igloo norte"));
        assertArrayEquals(new String[]{"Casa Pequeña (v2).litematic", "Unnamed 1"},
                files.fileAndRegion("Casa Pequeña (v2).litematic Unnamed 1"));
        Files.write(files.imports().resolve("torre [final] #2.litematic"), new byte[]{4});
        assertArrayEquals(new byte[]{4}, files.readImport("torre [final] #2", 1024));
        assertArrayEquals(new String[]{"no existe", ""}, files.fileAndRegion("no existe"));
        assertEquals(List.of("Casa Pequeña (v2).litematic", "igloo.nbt", "torre [final] #2.litematic",
                "torre.schem"), files.importable());
        for (String evil : List.of("../igloo.nbt", "igloo.nbt/..", "..\\igloo", "a/../../x.litematic")) {
            ShareCodecTest.assertMessage("", () -> files.readImport(evil, 1024));
        }
    }

    @Test
    void exportedSchematicsDoNotOverwrite() throws IOException {
        ShareFiles files = new ShareFiles(root);
        Path first = files.writeSchematic("torre", ".litematic", new byte[]{1});
        Path second = files.writeSchematic("torre", ".litematic", new byte[]{2});
        assertEquals("torre.litematic", first.getFileName().toString());
        assertEquals("torre-2.litematic", second.getFileName().toString());
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(first));
        assertEquals(files.exports(), second.getParent());
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
