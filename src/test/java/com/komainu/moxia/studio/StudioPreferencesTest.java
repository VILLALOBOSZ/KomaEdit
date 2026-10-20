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
        assertEquals(StudioPreferences.PreviewMode.LIVE,
                StudioPreferences.fromJson("{\"favorites\": [\"coil\"]}").previewMode(),
                "un archivo de antes, sin elegir nada, empieza con la vista previa en vivo");
    }

    @Test
    void theLivePreviewIsOnByDefaultAndAChosenOffSticks() {
        assertEquals(StudioPreferences.PreviewMode.LIVE, new StudioPreferences().previewMode());
        // La 1.7.0 guardaba «off» aunque nadie lo eligiera (era lo de serie): pasa a en vivo una vez.
        assertEquals(StudioPreferences.PreviewMode.LIVE,
                StudioPreferences.fromJson("{\"format\": 2, \"toolPreview\": false, \"preview\": \"off\"}")
                        .previewMode());
        assertEquals(StudioPreferences.PreviewMode.BUILD,
                StudioPreferences.fromJson("{\"format\": 2, \"preview\": \"build\"}").previewMode());
        // Apagada a mano con esta versión, se queda apagada.
        StudioPreferences off = new StudioPreferences();
        off.setPreviewMode(StudioPreferences.PreviewMode.OFF);
        assertEquals(StudioPreferences.PreviewMode.OFF, StudioPreferences.fromJson(off.toJson()).previewMode());
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

    @Test
    void theStudioRemembersStageToolSettingsScrollBlocksAndFoldedSections() throws Exception {
        StudioPreferences preferences = new StudioPreferences();
        preferences.setStage("tools");
        preferences.setTool("roof");
        preferences.setToolValues("roof", java.util.Map.of("type", "hip", "overhang", 2, "open", true));
        preferences.setScroll("tool:roof", 120);
        preferences.pushBlockRecent("minecraft:deepslate_tiles");
        preferences.pushBlockRecent("minecraft:spruce_planks");
        preferences.setExpanded("tools:more:roof", true);
        preferences.setPreviewMode(StudioPreferences.PreviewMode.LIVE);
        Path file = directory.resolve("all.json");
        preferences.save(file);

        StudioPreferences loaded = StudioPreferences.load(file);
        assertEquals("tools", loaded.stage());
        assertEquals("roof", loaded.tool());
        assertEquals(java.util.Map.of("type", "hip", "overhang", 2, "open", true), loaded.toolValues("roof"));
        assertEquals(120, loaded.scroll("tool:roof"));
        assertEquals(0, loaded.scroll("tool:tree"));
        assertEquals(List.of("minecraft:spruce_planks", "minecraft:deepslate_tiles"), loaded.blockRecents());
        assertTrue(loaded.expanded("tools:more:roof"));
        assertEquals(StudioPreferences.PreviewMode.LIVE, loaded.previewMode());
    }

    @Test
    void previewModesCycleAndOldFilesKeepTheirChoice() {
        assertEquals(StudioPreferences.PreviewMode.BUILD, StudioPreferences.PreviewMode.OFF.next());
        assertEquals(StudioPreferences.PreviewMode.LIVE, StudioPreferences.PreviewMode.BUILD.next());
        assertEquals(StudioPreferences.PreviewMode.OFF, StudioPreferences.PreviewMode.LIVE.next());
        assertEquals(StudioPreferences.PreviewMode.BUILD,
                StudioPreferences.fromJson("{\"toolPreview\": true}").previewMode(), "el interruptor de antes");
        StudioPreferences live = new StudioPreferences();
        live.setPreviewMode(StudioPreferences.PreviewMode.LIVE);
        live.setToolPreview(true);
        assertEquals(StudioPreferences.PreviewMode.LIVE, live.previewMode(), "encender no quita el en vivo");
    }

    @Test
    void blockRecentsAreBoundedAndWithoutDuplicates() {
        StudioPreferences preferences = new StudioPreferences();
        for (int index = 0; index < 20; index++) {
            preferences.pushBlockRecent("minecraft:block_" + index % 12);
        }
        assertEquals(StudioPreferences.MAX_BLOCK_RECENTS, preferences.blockRecents().size());
        assertEquals("minecraft:block_7", preferences.blockRecents().getFirst());
    }
}
