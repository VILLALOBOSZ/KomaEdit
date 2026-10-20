package com.komainu.moxia.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.komainu.moxia.blueprint.BlueprintFixtures.ALICE;
import static com.komainu.moxia.blueprint.BlueprintFixtures.BRUNO;
import static com.komainu.moxia.blueprint.BlueprintFixtures.meta;
import static com.komainu.moxia.blueprint.BlueprintFixtures.schematic;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Planos por jugador, tamaño máximo, archivo y quién puede compartir. */
class BlueprintLimitsTest {
    private static final BlueprintLimits SMALL = new BlueprintLimits(2, 1, 1_000, 16, 2_048, true);

    @TempDir
    Path root;

    @Test
    void sizeChecksTheLongestSideAndTheVolume() {
        assertDoesNotThrow(() -> SMALL.checkSize(10, 10, 10, false));
        assertThrows(IllegalArgumentException.class, () -> SMALL.checkSize(17, 1, 1, false), "lado");
        assertThrows(IllegalArgumentException.class, () -> SMALL.checkSize(11, 10, 10, false), "volumen");
        assertDoesNotThrow(() -> SMALL.checkSize(400, 400, 400, true), "sin límites");
        assertThrows(IllegalArgumentException.class, () -> SMALL.checkFile(4_096));
    }

    @Test
    void thePerPlayerLimitCountsNewOnesNotReplacements() {
        BlueprintLibrary library = new BlueprintLibrary(root);
        library.save(meta("uno", ALICE, "A", List.of(), 2, 2, 2, 8), schematic("1"), new byte[]{1}, SMALL, false);
        library.save(meta("dos", ALICE, "A", List.of(), 2, 2, 2, 8), schematic("2"), new byte[]{1}, SMALL, false);
        IllegalArgumentException full = assertThrows(IllegalArgumentException.class, () -> library.save(
                meta("tres", ALICE, "A", List.of(), 2, 2, 2, 8), schematic("3"), new byte[]{1}, SMALL, false));
        assertTrue(full.getMessage().contains("máximo 2"), full.getMessage());
        assertDoesNotThrow(() -> library.save(meta("dos", ALICE, "A", List.of("nueva"), 2, 2, 2, 8),
                schematic("2b"), new byte[]{1}, SMALL, false), "reemplazar no suma");
        assertDoesNotThrow(() -> library.save(meta("tres", ALICE, "A", List.of(), 2, 2, 2, 8), schematic("3"),
                new byte[]{1}, SMALL, true), "komaedit.limit.blueprints");
        assertDoesNotThrow(() -> library.save(meta("uno", BRUNO, "B", List.of(), 2, 2, 2, 8), schematic("b"),
                new byte[]{1}, SMALL, false), "cada jugador tiene su cupo");
        assertThrows(IllegalArgumentException.class, () -> library.save(meta("gigante", BRUNO, "B", List.of(),
                16, 16, 16, 9), schematic("g"), new byte[]{1}, SMALL, false));
        assertThrows(IllegalArgumentException.class, () -> library.save(meta("pesado", BRUNO, "B", List.of(),
                2, 2, 2, 8), new byte[4_096], new byte[]{1}, SMALL, false));
        assertEquals(List.of("uno"), library.own(BRUNO).stream().map(entry -> entry.meta().name()).toList());
    }

    @Test
    void sharingHasItsOwnLimitAndCanBeSwitchedOff() {
        BlueprintLibrary library = new BlueprintLibrary(root);
        BlueprintLibrary.Entry first = library.save(meta("uno", ALICE, "A", List.of(), 2, 2, 2, 8), schematic("1"),
                new byte[]{1}, SMALL, false);
        BlueprintLibrary.Entry second = library.save(meta("dos", ALICE, "A", List.of(), 2, 2, 2, 8), schematic("2"),
                new byte[]{1}, SMALL, false);
        library.share(first, "A", 1L, SMALL, false, false);
        assertThrows(IllegalArgumentException.class, () -> library.share(second, "A", 2L, SMALL, false, false),
                "maxShared 1");
        assertDoesNotThrow(() -> library.share(first, "A", 3L, SMALL, false, false), "volver a compartir reemplaza");
        BlueprintLimits closed = new BlueprintLimits(2, 10, 1_000, 16, 2_048, false);
        IllegalArgumentException off = assertThrows(IllegalArgumentException.class,
                () -> library.share(second, "A", 4L, closed, true, true));
        assertTrue(off.getMessage().contains("no se pueden compartir"), off.getMessage());
    }

    @Test
    void configFileOverridesOnlyWhatItSays() {
        BlueprintLimits limits = BlueprintConfig.read("""
                {"format": 1, "maxPerPlayer": 10, "maxFileKilobytes": 512, "allowSharing": false}
                """);
        assertEquals(10, limits.maxPerPlayer());
        assertEquals(512 * 1024L, limits.maxFileBytes());
        assertFalse(limits.allowSharing());
        assertEquals(BlueprintLimits.DEFAULT.maxVolume(), limits.maxVolume());
        assertEquals(BlueprintLimits.DEFAULT.maxShared(), limits.maxShared());
        assertThrows(IllegalArgumentException.class, () -> BlueprintConfig.read("{\"format\": 2}"));
        assertThrows(IllegalArgumentException.class, () -> BlueprintConfig.read("{\"maxPerPlayer\": -1}"));
    }
}
