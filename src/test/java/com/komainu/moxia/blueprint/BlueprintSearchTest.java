package com.komainu.moxia.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.komainu.moxia.blueprint.BlueprintFixtures.ALICE;
import static com.komainu.moxia.blueprint.BlueprintFixtures.BRUNO;
import static com.komainu.moxia.blueprint.BlueprintFixtures.meta;
import static com.komainu.moxia.blueprint.BlueprintFixtures.schematic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Búsqueda por nombre y etiqueta, y las listas de planos de {@code Bp(…)} y del pincel estampar. */
class BlueprintSearchTest {
    @TempDir
    Path root;

    private BlueprintLibrary library() {
        BlueprintLibrary library = new BlueprintLibrary(root);
        save(library, ALICE, "roca_musgo", "rocas musgo");
        save(library, ALICE, "roca_desnuda", "rocas");
        save(library, ALICE, "arbusto_bajo", "Arbustos bosque");
        save(library, ALICE, "ruina_torre", "ruinas piedra");
        BlueprintLibrary.Entry shared = save(library, BRUNO, "peñasco", "rocas");
        library.share(shared, "Bruno", 5L, BlueprintLimits.DEFAULT, false, false);
        save(library, BRUNO, "secreto", "rocas");
        return library;
    }

    private static BlueprintLibrary.Entry save(BlueprintLibrary library, java.util.UUID owner, String name,
                                               String tags) {
        String clean = BlueprintTags.fold(name).replace('ñ', 'n');
        return library.save(meta(clean, owner, "x", BlueprintTags.parse(tags), 3, 3, 3, 10), schematic(clean),
                new byte[]{1}, BlueprintLimits.DEFAULT, false);
    }

    @Test
    void tagsAreFoldedAndDeduplicated() {
        assertEquals(List.of("arboles", "rocas", "pena"), BlueprintTags.parse("#Árboles, rocas ROCAS  #peña"));
        assertTrue(BlueprintTags.parse("   ").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BlueprintTags.parse("a b c d e f g h i"));
        assertThrows(IllegalArgumentException.class, () -> BlueprintTags.normalize("con/barra"));
        assertThrows(IllegalArgumentException.class, () -> BlueprintTags.parse("rocas -v"), "-v no es etiqueta");
    }

    @Test
    void aTagFindsOwnAndSharedButNotOtherPlayersPrivateOnes() {
        BlueprintLibrary library = library();
        assertEquals(List.of("roca_desnuda", "roca_musgo", "servidor/penasco"), library.tagged(ALICE, "#Rocas")
                .stream().map(BlueprintLibrary.Entry::reference).sorted().toList());
        // Bruno ve su peñasco dos veces: el suyo y la copia que compartió.
        assertEquals(List.of("penasco", "secreto", "servidor/penasco"), library.tagged(BRUNO, "rocas").stream()
                .map(BlueprintLibrary.Entry::reference).sorted().toList());
        assertTrue(library.tagged(ALICE, "volcanes").isEmpty());
    }

    @Test
    void searchMatchesNamePiecesTagPrefixesAndExactHashTags() {
        BlueprintLibrary library = library();
        assertEquals(List.of("roca_desnuda", "roca_musgo", "penasco"), names(library.search(ALICE, "roca")),
                "«roca» casa con el nombre o con el principio de #rocas");
        assertEquals(List.of("roca_musgo"), names(library.search(ALICE, "#rocas musgo")));
        assertEquals(List.of("arbusto_bajo"), names(library.search(ALICE, "#ARBUSTOS")));
        assertEquals(List.of("arbusto_bajo"), names(library.search(ALICE, "bosq")), "principio de etiqueta");
        assertEquals(List.of("ruina_torre"), names(library.search(ALICE, "torre #ruinas")));
        assertTrue(library.search(ALICE, "#roca").isEmpty(), "#etiqueta pide la etiqueta exacta");
        assertEquals(5, library.search(ALICE, "").size());
    }

    @Test
    void exactNameComesFirst() {
        BlueprintLibrary library = library();
        save(library, ALICE, "roca", "otras");
        assertEquals("roca", names(library.search(ALICE, "roca")).getFirst());
    }

    @Test
    void selectionsMixNamesAndTagsWithWeights() {
        BlueprintLibrary library = library();
        List<BlueprintSelection.Weighted> chosen = BlueprintSelection.parse("roca_musgo:3, #rocas")
                .resolve(library, ALICE, 32);
        Map<String, Double> weights = chosen.stream().collect(Collectors.toMap(
                weighted -> weighted.entry().reference(), BlueprintSelection.Weighted::weight));
        // #rocas reparte su peso 1 entre sus tres planos; roca_musgo suma los dos.
        assertEquals(3 + 1 / 3.0, weights.get("roca_musgo"), 1e-9);
        assertEquals(1 / 3.0, weights.get("roca_desnuda"), 1e-9);
        assertEquals(1 / 3.0, weights.get("servidor/penasco"), 1e-9);
        assertEquals(3, chosen.size());
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintSelection.parse("#volcanes").resolve(library, ALICE, 32));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintSelection.parse("secreto").resolve(library, ALICE, 32), "el de Bruno es privado");
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintSelection.parse("#rocas").resolve(library, ALICE, 2), "límite de planos distintos");
    }

    private static List<String> names(List<BlueprintLibrary.Entry> entries) {
        return entries.stream().map(entry -> entry.meta().name()).toList();
    }
}
