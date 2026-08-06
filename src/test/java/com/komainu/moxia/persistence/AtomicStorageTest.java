package com.komainu.moxia.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
