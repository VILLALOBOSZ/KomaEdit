package com.komainu.moxia.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.komainu.moxia.blueprint.BlueprintFixtures.ALICE;
import static com.komainu.moxia.blueprint.BlueprintFixtures.BRUNO;
import static com.komainu.moxia.blueprint.BlueprintFixtures.meta;
import static com.komainu.moxia.blueprint.BlueprintFixtures.schematic;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardar y volver a leer planos con sus metadatos, compartirlos y borrarlos, sin Minecraft. */
class BlueprintLibraryTest {
    @TempDir
    Path root;

    @Test
    void saveAndLoadKeepTheSchematicTheThumbnailAndEveryField() {
        byte[] schem = schematic("roca_grande");
        byte[] png = BlueprintThumbnail.png(BlueprintFixtures.rock(3));
        BlueprintLibrary library = new BlueprintLibrary(root);
        BlueprintMeta meta = meta("Roca_Grande", ALICE, "Alicia", BlueprintTags.parse("#Rocas, musgo  bosque"),
                7, 5, 6, 118);
        library.save(meta, schem, png, BlueprintLimits.DEFAULT, false);

        // Otra biblioteca sobre la misma carpeta: lo que se lee viene del disco, no de la memoria.
        BlueprintLibrary reopened = new BlueprintLibrary(root);
        BlueprintLibrary.Entry entry = reopened.require(ALICE, BlueprintRef.parse("roca_grande"));
        BlueprintMeta read = entry.meta();
        assertEquals("roca_grande", read.name());
        assertEquals(BlueprintScope.PLAYER, read.scope());
        assertEquals(ALICE, read.owner());
        assertEquals("Alicia", read.author());
        assertEquals(1_700_000_000_000L, read.created());
        assertEquals(List.of(7, 5, 6), List.of(read.sizeX(), read.sizeY(), read.sizeZ()));
        assertEquals(118, read.blocks());
        assertEquals(List.of("rocas", "musgo", "bosque"), read.tags());
        assertEquals("sponge.3", read.schematicFormat());
        assertEquals(BlueprintLibrary.sha256(png), read.thumbnail(), "la huella es la del PNG guardado");
        assertArrayEquals(schem, reopened.readSchematic(entry));
        assertArrayEquals(png, reopened.readThumbnail(entry));
        assertTrue(Files.isRegularFile(root.resolve("players").resolve(ALICE.toString()).resolve("roca_grande.schem")));
    }

    @Test
    void metadataSurvivesAJsonRoundTrip() {
        BlueprintMeta meta = meta("pozo", BRUNO, "Bruno", List.of("agua", "aldea"), 5, 6, 5, 61)
                .asShared("Bruno", 1_800_000_000_000L);
        assertEquals(meta, BlueprintMeta.fromJson(meta.toJson()));
    }

    @Test
    void overwritingKeepsTheCreationDateAndTheCount() {
        BlueprintLibrary library = new BlueprintLibrary(root);
        library.save(meta("casa", ALICE, "Alicia", List.of(), 9, 9, 7, 300), schematic("a"), new byte[]{1, 2},
                BlueprintLimits.DEFAULT, false);
        BlueprintMeta second = new BlueprintMeta("casa", BlueprintScope.PLAYER, ALICE, "Alicia", 1_700_000_000_000L,
                1_750_000_000_000L, 9, 10, 7, 320, List.of("casas"), "", "sponge.3", "", 0L);
        library.save(second, schematic("b"), new byte[]{3}, BlueprintLimits.DEFAULT, false);
        assertEquals(1, library.own(ALICE).size());
        BlueprintMeta read = library.own(ALICE).getFirst().meta();
        assertEquals(320, read.blocks());
        assertEquals(1_750_000_000_000L, read.updated());
        assertArrayEquals(schematic("b"), library.readSchematic(library.own(ALICE).getFirst()));
    }

    @Test
    void ownBlueprintsComeBeforeSharedOnesAndServerPrefixGoesToTheShared() {
        BlueprintLibrary library = new BlueprintLibrary(root);
        BlueprintLibrary.Entry brunos = library.save(meta("roca", BRUNO, "Bruno", List.of("rocas"), 3, 2, 3, 12),
                schematic("bruno"), new byte[]{9}, BlueprintLimits.DEFAULT, false);
        library.share(brunos, "Bruno", 2L, BlueprintLimits.DEFAULT, false, false);
        library.save(meta("roca", ALICE, "Alicia", List.of(), 4, 4, 4, 30), schematic("alice"), new byte[]{8},
                BlueprintLimits.DEFAULT, false);

        assertEquals(ALICE, library.require(ALICE, BlueprintRef.parse("roca")).meta().owner());
        BlueprintLibrary.Entry shared = library.require(ALICE, BlueprintRef.parse("servidor/roca"));
        assertEquals(BlueprintScope.SHARED, shared.meta().scope());
        assertEquals("servidor/roca", shared.reference());
        assertEquals("Bruno", shared.meta().sharedBy());
        assertArrayEquals(schematic("bruno"), library.readSchematic(shared));
        // Quien no tiene uno propio llega al compartido sin prefijo.
        assertEquals(BlueprintScope.SHARED, library.require(BlueprintFixtures.BRUNO,
                BlueprintRef.parse("server/roca")).meta().scope());
        assertEquals(List.of("roca", "servidor/roca"), library.visible(ALICE).stream()
                .map(BlueprintLibrary.Entry::reference).toList());
    }

    @Test
    void sharingSomeoneElsesNameNeedsTheManagePermission() {
        BlueprintLibrary library = new BlueprintLibrary(root);
        BlueprintLibrary.Entry brunos = library.save(meta("torre", BRUNO, "Bruno", List.of(), 3, 9, 3, 40),
                schematic("b"), new byte[]{1}, BlueprintLimits.DEFAULT, false);
        library.share(brunos, "Bruno", 1L, BlueprintLimits.DEFAULT, false, false);
        BlueprintLibrary.Entry alices = library.save(meta("torre", ALICE, "Alicia", List.of(), 3, 12, 3, 50),
                schematic("a"), new byte[]{2}, BlueprintLimits.DEFAULT, false);
        assertThrows(IllegalArgumentException.class,
                () -> library.share(alices, "Alicia", 2L, BlueprintLimits.DEFAULT, false, false));
        library.share(alices, "Alicia", 3L, BlueprintLimits.DEFAULT, false, true);
        assertEquals(ALICE, library.require(BRUNO, BlueprintRef.parse("servidor/torre")).meta().owner());
        assertThrows(IllegalArgumentException.class, () -> library.share(
                library.require(ALICE, BlueprintRef.parse("servidor/torre")), "Alicia", 4L, BlueprintLimits.DEFAULT,
                false, true), "un compartido no se vuelve a compartir");
    }

    @Test
    void deleteRemovesEveryFileAndRetagOnlyTouchesTheJson() throws IOException {
        BlueprintLibrary library = new BlueprintLibrary(root);
        BlueprintLibrary.Entry entry = library.save(meta("arco", ALICE, "Alicia", List.of("a"), 5, 5, 1, 13),
                schematic("arco"), new byte[]{4, 5}, BlueprintLimits.DEFAULT, false);
        BlueprintLibrary.Entry retagged = library.retag(entry, List.of("ruinas", "piedra"), 99L);
        assertEquals(List.of("ruinas", "piedra"), library.require(ALICE, BlueprintRef.parse("arco")).meta().tags());
        assertArrayEquals(schematic("arco"), library.readSchematic(retagged));
        assertTrue(library.delete(retagged));
        assertTrue(library.own(ALICE).isEmpty());
        try (var files = Files.list(library.playerDirectory(ALICE))) {
            assertEquals(List.of(), files.toList(), "ni .schem, ni .png, ni .bak");
        }
        assertFalse(library.delete(retagged));
    }

    @Test
    void aDamagedJsonIsSkippedAndTheRestStillLoads() throws IOException {
        BlueprintLibrary library = new BlueprintLibrary(root);
        library.save(meta("bueno", ALICE, "Alicia", List.of(), 2, 2, 2, 8), schematic("x"), new byte[]{1},
                BlueprintLimits.DEFAULT, false);
        Path directory = library.playerDirectory(ALICE);
        Files.writeString(directory.resolve("roto.json"), "{ no es json", StandardCharsets.UTF_8);
        Files.write(directory.resolve("roto.schem"), new byte[]{1});
        Files.writeString(directory.resolve("huerfano.json"), meta("huerfano", ALICE, "Alicia", List.of(), 1, 1, 1, 1)
                .toJson().toString(), StandardCharsets.UTF_8);
        assertEquals(List.of("bueno"), new BlueprintLibrary(root).own(ALICE).stream()
                .map(entry -> entry.meta().name()).toList(), "sin esquema, el plano no cuenta");
    }

    @Test
    void namesCannotEscapeTheFolder() {
        for (String name : new String[]{"../fuera", "a/b", "con espacio", "", "#rocas", "x".repeat(33), "-v"}) {
            assertThrows(IllegalArgumentException.class, () -> BlueprintRef.parse(name), name);
        }
        assertEquals(new BlueprintRef("casa_1", false), BlueprintRef.parse("Casa_1"));
        assertEquals(new BlueprintRef("casa_1", true), BlueprintRef.parse("Servidor/Casa_1"));
    }

    @Test
    void thumbnailsAreFoundByTheirHashOnlyAmongVisibleBlueprints() {
        BlueprintLibrary library = new BlueprintLibrary(root);
        byte[] png = BlueprintThumbnail.png(BlueprintFixtures.bush());
        BlueprintLibrary.Entry entry = library.save(meta("arbusto", ALICE, "Alicia", List.of(), 5, 4, 5, 40),
                schematic("arbusto"), png, BlueprintLimits.DEFAULT, false);
        String hash = entry.meta().thumbnail();
        assertTrue(library.thumbnail(ALICE, hash).isPresent());
        assertTrue(library.thumbnail(BRUNO, hash).isEmpty(), "Bruno no ve los planos privados de Alicia");
        library.share(entry, "Alicia", 1L, BlueprintLimits.DEFAULT, false, false);
        assertTrue(library.thumbnail(BRUNO, hash).isPresent());
    }
}
