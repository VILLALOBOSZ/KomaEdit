package com.komainu.moxia.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TolerantListingTest {
    @Test
    void skipsUnreadableFilesAndKeepsTheRest() {
        List<Path> files = List.of(Path.of("buena.json"), Path.of("antigua.json"), Path.of("otra.json"));
        List<String> read = TolerantListing.readAll(files, path -> {
            if (path.toString().startsWith("antigua")) {
                throw new IllegalStateException("Forma 3D desconocida: epic_tree");
            }
            return path.toString();
        }, "la receta");
        assertEquals(List.of("buena.json", "otra.json"), read);
    }
}
