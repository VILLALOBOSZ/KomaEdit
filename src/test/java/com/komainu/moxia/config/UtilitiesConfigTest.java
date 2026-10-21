package com.komainu.moxia.config;

import com.komainu.moxia.notes.NoteLimits;
import com.komainu.moxia.views.SharedViews;
import com.komainu.moxia.views.ViewTeleport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code config/komaedit/utilities.json}: de serie, de una versión sin algún campo, y roto. */
class UtilitiesConfigTest {
    @TempDir
    Path folder;

    @Test
    void theDefaultsGoBackAndForth() {
        UtilitiesConfig.Settings read = UtilitiesConfig.parse(UtilitiesConfig.toJson(UtilitiesConfig.Settings.DEFAULT));
        assertEquals(UtilitiesConfig.Settings.DEFAULT, read);
        assertTrue(read.teleport().creativeOnly(), "de serie, saltar solo en creativo o espectador");
    }

    @Test
    void missingFieldsTakeTheirDefault() {
        UtilitiesConfig.Settings read = UtilitiesConfig.parse("""
                {"format": 1, "views": {"maxTeleportDistance": 500, "allowSharing": false},
                 "notes": {"maxLength": 120}}""");
        assertEquals(500.0, read.teleport().maxDistance());
        assertEquals(ViewTeleport.Rules.DEFAULT.cooldownMillis(), read.teleport().cooldownMillis());
        assertFalse(read.views().allowSharing());
        assertEquals(SharedViews.Limits.DEFAULT.maxPerPlayer(), read.views().maxPerPlayer());
        assertEquals(120, read.notes().maxLength());
        assertEquals(NoteLimits.DEFAULT.maxPerPlayer(), read.notes().maxPerPlayer());
        assertEquals(UtilitiesConfig.Settings.DEFAULT, UtilitiesConfig.parse("{}"));
    }

    @Test
    void badValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> UtilitiesConfig.parse("{\"format\": 2}"));
        assertThrows(IllegalArgumentException.class,
                () -> UtilitiesConfig.parse("{\"notes\": {\"maxLength\": 9999}}"), "más de lo que cabe");
        assertThrows(IllegalArgumentException.class,
                () -> UtilitiesConfig.parse("{\"views\": {\"cooldownMillis\": -1}}"));
    }

    @Test
    void aMissingFileIsCreatedAndABrokenOneFallsBackToTheDefaults() throws Exception {
        UtilitiesConfig.load(folder);
        Path file = folder.resolve("utilities.json");
        assertTrue(Files.isRegularFile(file));
        assertEquals(UtilitiesConfig.Settings.DEFAULT, UtilitiesConfig.settings());
        Files.writeString(file, "{\"notes\": {\"maxLength\": 50}}");
        UtilitiesConfig.load(folder);
        assertEquals(50, UtilitiesConfig.settings().notes().maxLength());
        Files.writeString(file, "{ esto no es json");
        UtilitiesConfig.load(folder);
        assertEquals(UtilitiesConfig.Settings.DEFAULT, UtilitiesConfig.settings());
    }
}
