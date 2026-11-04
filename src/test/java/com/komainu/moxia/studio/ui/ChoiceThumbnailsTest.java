package com.komainu.moxia.studio.ui;

import com.komainu.moxia.nature.RockType;
import com.komainu.moxia.nature.TreeSpecies;
import com.komainu.moxia.roof.RoofType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las miniaturas de especies, tejados, rocas y sellos salen de sus generadores y no se parecen entre sí. */
class ChoiceThumbnailsTest {
    @BeforeAll
    static void blocking() {
        ChoiceThumbnails.setBlocking(true);
    }

    private static void check(String card, String param, List<String> options) {
        Set<Integer> signatures = new HashSet<>();
        for (String option : options) {
            StudioImage image = ChoiceThumbnails.get(card, param, option);
            assertNotNull(image, card + "/" + option);
            int[] pixels = image.pixels();
            assertEquals(ChoiceThumbnails.SIZE * ChoiceThumbnails.SIZE, pixels.length, option);
            long painted = Arrays.stream(pixels).filter(argb -> (argb >>> 24) > 0).count();
            assertTrue(painted > pixels.length / 12, card + "/" + option + ": casi vacía (" + painted + ")");
            if (!card.equals("stamp")) {
                // Los sellos son un mapa de alturas cuadrado; lo demás, un objeto sobre fondo transparente.
                assertTrue(painted < pixels.length, card + "/" + option + ": sin fondo transparente");
            }
            signatures.add(Arrays.hashCode(pixels));
        }
        assertEquals(options.size(), signatures.size(), card + ": dos opciones dan la misma miniatura");
    }

    @Test
    void everySpeciesRoofAndRockHasItsOwnThumbnail() {
        check("tree", "species", Arrays.stream(TreeSpecies.values()).map(TreeSpecies::id).toList());
        check("roof", "type", Arrays.stream(RoofType.values()).map(RoofType::id).toList());
        check("rock", "type", Arrays.stream(RockType.values()).map(RockType::id).toList());
        check("stamp", "stamp", List.of("montana", "crater", "colina"));
    }

    /**
     * Las de las variaciones van por color de material: un juego largo eligiendo materiales no puede quedarse con
     * todas en memoria. Las menos usadas salen y la que se sigue usando se queda.
     */
    @Test
    void theCacheHasALimitAndKeepsWhatIsInUse() {
        StudioImage kept = ChoiceThumbnails.cached("test/kept", () -> StudioImage.of("test/kept", 1, 1,
                () -> new int[1]));
        for (int index = 0; index < ChoiceThumbnails.MAX_READY + 200; index++) {
            String key = "test/material/" + index;
            ChoiceThumbnails.cached(key, () -> StudioImage.of(key, 1, 1, () -> new int[1]));
            ChoiceThumbnails.cached("test/kept", () -> {
                throw new AssertionError("la que se sigue usando no se vuelve a pintar");
            });
        }
        assertTrue(ChoiceThumbnails.readyCount() <= ChoiceThumbnails.MAX_READY, "como mucho el tope");
        assertEquals(kept, ChoiceThumbnails.cached("test/kept", () -> null));
    }

    @Test
    void otherChoicesHaveNoThumbnail() {
        assertFalse(ChoiceThumbnails.supports("river", "kind"));
        assertNull(ChoiceThumbnails.get("river", "kind", "rio"));
    }
}
