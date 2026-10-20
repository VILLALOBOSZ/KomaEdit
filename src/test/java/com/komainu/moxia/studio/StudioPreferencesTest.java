package com.komainu.moxia.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioPreferencesTest {
    @TempDir
    Path directory;

    @Test
    void togglesFavorites() {
        StudioPreferences preferences = new StudioPreferences();
        assertTrue(preferences.toggleFavorite("coil"));
        assertTrue(preferences.isFavorite("coil"));
        assertFalse(preferences.toggleFavorite("coil"));
        assertFalse(preferences.isFavorite("coil"));
    }

    @Test
    void recentsAreMostRecentFirstWithoutDuplicatesAndBounded() {
        StudioPreferences preferences = new StudioPreferences();
        for (String id : List.of("a", "b", "c", "a", "d", "e", "f", "g")) {
            preferences.pushRecent(id);
        }
        assertEquals(List.of("g", "f", "e", "d", "a", "c"), preferences.recents());
        assertEquals(StudioPreferences.MAX_RECENTS, preferences.recents().size());
    }

    @Test
    void roundTripsThroughJsonAndDisk() throws Exception {
        StudioPreferences preferences = new StudioPreferences();
        preferences.toggleFavorite("sweep");
        preferences.toggleFavorite("rings");
        preferences.pushRecent("coil");
        preferences.pushRecent("web");
        Path file = directory.resolve("komaedit").resolve("studio.json");
        preferences.save(file);

        StudioPreferences loaded = StudioPreferences.load(file);
        assertEquals(List.of("sweep", "rings"), loaded.favorites());
        assertEquals(List.of("web", "coil"), loaded.recents());
    }

    @Test
    void toolsKeepTheirOwnFavoritesRecentsAndPreviewChoice() throws Exception {
        StudioPreferences preferences = new StudioPreferences();
        assertTrue(preferences.toggleToolFavorite("tree"));
        preferences.pushToolRecent("roof");
        preferences.pushToolRecent("tree");
        preferences.setToolPreview(true);
        assertFalse(preferences.isFavorite("tree"), "las figuras no comparten favoritas con las herramientas");
        assertTrue(preferences.recents().isEmpty());
        Path file = directory.resolve("tools.json");
        preferences.save(file);

        StudioPreferences loaded = StudioPreferences.load(file);
        assertTrue(loaded.isToolFavorite("tree"));
        assertEquals(List.of("tree", "roof"), loaded.toolRecents());
        assertTrue(loaded.toolPreview());
        assertFalse(StudioPreferences.fromJson("{\"favorites\": [\"coil\"]}").toolPreview(),
                "un archivo de antes empieza sin vista previa");
    }

    @Test
    void brokenOrMissingFilesGiveEmptyPreferences() throws Exception {
        assertTrue(StudioPreferences.load(directory.resolve("missing.json")).favorites().isEmpty());
        Path broken = directory.resolve("broken.json");
        Files.writeString(broken, "{\"favorites\": [\"coil\", 3, null, ");
        assertTrue(StudioPreferences.load(broken).favorites().isEmpty());
        StudioPreferences partial = StudioPreferences.fromJson("{\"favorites\": [\"coil\", 3, \"\"], \"recents\": 7}");
        assertEquals(List.of("coil"), partial.favorites());
        assertTrue(partial.recents().isEmpty());
    }
}
