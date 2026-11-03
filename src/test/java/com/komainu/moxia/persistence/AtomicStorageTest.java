package com.komainu.moxia.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicStorageTest {
    @TempDir
    Path directory;

    @Test
    void replacesFilesAndKeepsThePreviousVersion() throws Exception {
        Path target = directory.resolve("project.json");

        AtomicStorage.writeString(target, "first");
        AtomicStorage.writeString(target, "second");

        assertEquals("second", Files.readString(target));
        assertEquals("first", Files.readString(AtomicStorage.backup(target)));
        assertFalse(Files.exists(AtomicStorage.temporary(target)));
    }

    /**
     * Un plano, recorrido o receta llamado como un dispositivo de Windows ({@code con}, {@code nul}…) no se podía guardar
     * en un servidor o un mundo de un jugador en Windows, y el error no decía por qué. Ahora se dice antes de escribir
     * nada, en cualquier sistema.
     */
    @Test
    void aWindowsDeviceNameIsRefusedBeforeWritingAnything() throws Exception {
        for (String name : List.of("con.json", "NUL.schem", "aux", "com1.json", "lpt9.krecipe", "prn.meta.json")) {
            IOException error = assertThrows(IOException.class,
                    () -> AtomicStorage.writeString(directory.resolve(name), "{}"), name);
            assertTrue(error.getMessage().contains("reservado de Windows"), error.getMessage());
        }
        try (Stream<Path> written = Files.list(directory)) {
            assertEquals(List.of(), written.toList());
        }
        for (String name : List.of("console.json", "conejo.json", "com.json", "nulo.schem")) {
            AtomicStorage.writeString(directory.resolve(name), "{}");
        }
        try (Stream<Path> written = Files.list(directory)) {
            assertEquals(4, written.filter(file -> file.toString().endsWith(".json")
                    || file.toString().endsWith(".schem")).count());
        }
    }

    @Test
    void recoversInterruptedTemporaryWrites() throws Exception {
        Path root = directory.resolve("project");
        Path target = root.resolve("recipes").resolve("example.json");
        Path temporary = AtomicStorage.temporary(target);
        Files.createDirectories(temporary.getParent());
        Files.writeString(temporary, "recovered");

        AtomicStorage.recoverTree(root);

        assertTrue(Files.isRegularFile(target));
        assertEquals("recovered", Files.readString(target));
        assertFalse(Files.exists(temporary));
    }

    @Test
    void calculatesStableSha256Digests() throws Exception {
        Path target = directory.resolve("payload.bin");
        Files.writeString(target, "KomaEdit");

        assertEquals(
                "839abb02f1d6242ba5912d53d1363b3101e2644afffda87507e35f775bab39a7",
                AtomicStorage.sha256(target)
        );
    }
}
