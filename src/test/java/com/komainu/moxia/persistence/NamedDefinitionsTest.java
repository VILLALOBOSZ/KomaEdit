package com.komainu.moxia.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedDefinitionsTest {
    @TempDir
    Path directory;

    @Test
    void savesListsAndDeletesAcrossInstances() {
        Path file = directory.resolve("palettes").resolve("palettes.json");
        NamedDefinitions store = new NamedDefinitions(file, "paleta");

        assertFalse(store.put("Hielo", "blue_ice;packed_ice", false));
        assertFalse(store.put("#roca", "stone;andesite", false));

        NamedDefinitions reopened = new NamedDefinitions(file, "paleta");
        assertEquals(List.of("hielo", "roca"), List.copyOf(reopened.all().keySet()));
        assertEquals(Optional.of("blue_ice;packed_ice"), reopened.get("HIELO"));
        assertTrue(reopened.delete("hielo"));
        assertFalse(reopened.delete("hielo"));
        assertEquals(Optional.empty(), store.get("hielo"));
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void refusesToOverwriteWithoutForce() {
        NamedDefinitions store = new NamedDefinitions(directory.resolve("noises.json"), "ruido");
        store.put("nubes", "Pe(f:0.02)", false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> store.put("nubes", "Si()", false));
        assertTrue(error.getMessage().contains("-f"));
        assertTrue(store.put("nubes", "Si()", true));
        assertEquals(Optional.of("Si()"), store.get("nubes"));
    }

    @Test
    void validatesNamesAndValues() {
        NamedDefinitions store = new NamedDefinitions(directory.resolve("x.json"), "paleta");
        assertThrows(IllegalArgumentException.class, () -> store.put("con espacio", "stone", false));
        assertThrows(IllegalArgumentException.class, () -> store.put("../fuera", "stone", false));
        assertThrows(IllegalArgumentException.class, () -> store.put("", "stone", false));
        assertThrows(IllegalArgumentException.class, () -> store.put("vacia", " ", false));
        assertEquals("roca_2", NamedDefinitions.normalizeName(" #Roca_2 "));
    }

    @Test
    void reportsUnreadableFiles() throws Exception {
        Path file = directory.resolve("broken.json");
        Files.writeString(file, "{not json");
        NamedDefinitions store = new NamedDefinitions(file, "paleta");

        assertThrows(IllegalStateException.class, store::all);
    }
}
