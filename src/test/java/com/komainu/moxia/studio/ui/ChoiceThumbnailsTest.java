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

    @Test
    void otherChoicesHaveNoThumbnail() {
        assertFalse(ChoiceThumbnails.supports("river", "kind"));
        assertNull(ChoiceThumbnails.get("river", "kind", "rio"));
    }
}
