package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.persistence.NamedDefinitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareImportTest {
    private static final ShareTarget BRUNO = ShareFixtures.player(ShareFixtures.BRUNO, "Bruno");

    @TempDir
    Path root;

    private ShareKinds.Registry kinds;
    private NamedDefinitions palettes;

    @BeforeEach
    void setUp() {
        kinds = ShareFixtures.kinds(root);
        palettes = ShareFixtures.store(root, BRUNO, "palettes");
        palettes.put("rocas", "dirt", false);
    }

    private static SharePack.Item palette(String name, String entries) {
        JsonObject data = new JsonObject();
        data.addProperty("e", entries);
        return new SharePack.Item("palette", name, 1, data);
    }

    private ShareImport prepare(SharePack.Item... items) {
        return ShareImport.prepare(SharePack.of("prueba", "Alicia", "1.9.1", 0L, List.of(items)), BRUNO,
                kinds::find, ShareFixtures.check());
    }

    @Test
    void conflictsAreShownBeforeAnythingIsSaved() {
        ShareImport plan = prepare(palette("rocas", "stone;andesite"), palette("arena", "sand"));
        assertEquals(ShareImport.Status.CONFLICT, plan.entries().get(0).status());
        assertTrue(plan.entries().get(0).problem().contains("ya tienes"));
        assertEquals(ShareImport.Status.NEW, plan.entries().get(1).status());
        // Preparar no guarda nada.
        assertEquals("dirt", palettes.get("rocas").orElseThrow());
        assertTrue(palettes.get("arena").isEmpty());
    }

    @Test
    void renameKeepsBoth() {
        List<ShareImport.Outcome> outcomes = prepare(palette("rocas", "stone;andesite"))
                .apply(BRUNO, ShareImport.Policy.RENAME);
        assertEquals("rocas_2", outcomes.getFirst().savedAs());
        assertEquals("dirt", palettes.get("rocas").orElseThrow());
        assertEquals("stone;andesite", palettes.get("rocas_2").orElseThrow());
        // Y la siguiente vez, el siguiente libre.
        prepare(palette("rocas", "tuff")).apply(BRUNO, ShareImport.Policy.RENAME);
        assertEquals("tuff", palettes.get("rocas_3").orElseThrow());
    }

    @Test
    void replaceOverwrites() {
        prepare(palette("rocas", "stone;andesite")).apply(BRUNO, ShareImport.Policy.REPLACE);
        assertEquals("stone;andesite", palettes.get("rocas").orElseThrow());
    }

    @Test
    void skipLeavesWhatWasThere() {
        List<ShareImport.Outcome> outcomes = prepare(palette("rocas", "stone"), palette("arena", "sand"))
                .apply(BRUNO, ShareImport.Policy.SKIP);
        assertFalse(outcomes.get(0).saved());
        assertTrue(outcomes.get(1).saved());
        assertEquals("dirt", palettes.get("rocas").orElseThrow());
    }

    @Test
    void longNamesStillFindAFreeName() {
        String name = "a".repeat(32);
        palettes.put(name, "dirt", false);
        List<ShareImport.Outcome> outcomes = prepare(palette(name, "stone")).apply(BRUNO, ShareImport.Policy.RENAME);
        assertEquals("a".repeat(30) + "_2", outcomes.getFirst().savedAs());
    }

    @Test
    void thePackCannotOverwriteItself() {
        ShareImport plan = prepare(palette("nueva", "stone"), palette("NUEVA", "andesite"));
        assertEquals(ShareImport.Status.NEW, plan.entries().get(0).status());
        assertEquals(ShareImport.Status.CONFLICT, plan.entries().get(1).status());
        List<ShareImport.Outcome> outcomes = plan.apply(BRUNO, ShareImport.Policy.REPLACE);
        assertEquals("nueva", outcomes.get(0).savedAs());
        assertEquals("nueva_2", outcomes.get(1).savedAs());
        assertEquals("stone", palettes.get("nueva").orElseThrow());
    }

    @Test
    void unknownKindsFromNewerVersionsAreSkippedNotFatal() {
        ShareImport plan = prepare(new SharePack.Item("hotbar", "constructor", 1, new JsonObject()),
                palette("arena", "sand"), new SharePack.Item("noise", "nuevo", 7, new JsonObject()));
        assertEquals(ShareImport.Status.UNKNOWN, plan.entries().get(0).status());
        assertEquals(ShareImport.Status.NEW, plan.entries().get(1).status());
        assertEquals(ShareImport.Status.INVALID, plan.entries().get(2).status());
        assertTrue(plan.entries().get(2).problem().contains("más nueva"));
        List<ShareImport.Outcome> outcomes = plan.apply(BRUNO, ShareImport.Policy.RENAME);
        assertEquals(1, outcomes.stream().filter(ShareImport.Outcome::saved).count());
    }

    @Test
    void missingBlocksAreReplacedAndReported() {
        ShareImport plan = prepare(palette("mod", "create:andesite_casing;biomesoplenty:fir_stairs[facing=east];"
                + "command_block;stone"));
        ShareImport.Entry entry = plan.entries().getFirst();
        assertEquals(ShareImport.Status.NEW, entry.status());
        assertEquals("andesite;oak_stairs[facing=east];air;stone", entry.item().data().get("e").getAsString());
        assertTrue(entry.warnings().getFirst().contains("create:andesite_casing → andesite"), entry.warnings()
                .toString());
        assertTrue(entry.warnings().stream().anyMatch(warning -> warning.contains("administración")));
    }

    @Test
    void anEmptyOrInvalidPackHasNothingToImport() {
        ShareImport plan = prepare(palette("../../x", "stone"), palette("ok", ""));
        assertFalse(plan.importable());
        assertEquals(2, plan.count(ShareImport.Status.INVALID));
    }

    @Test
    void policiesAcceptSpanishAndEnglish() {
        assertEquals(ShareImport.Policy.RENAME, ShareImport.Policy.byName("renombrar"));
        assertEquals(ShareImport.Policy.REPLACE, ShareImport.Policy.byName("Replace"));
        assertEquals(ShareImport.Policy.SKIP, ShareImport.Policy.byName("saltar"));
        ShareCodecTest.assertMessage("renombrar", () -> ShareImport.Policy.byName("borrar"));
    }
}
