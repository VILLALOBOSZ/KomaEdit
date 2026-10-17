package com.komainu.moxia.studio;

import com.komainu.moxia.shape.PathShape;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeSearchTest {
    private static final List<PathShape> ALL = Arrays.asList(PathShape.values());

    @Test
    void normalizesCaseAccentsAndSeparators() {
        assertEquals("expresion", ShapeSearch.normalize("Expresión"));
        assertEquals("telarana", ShapeSearch.normalize("TELARAÑA"));
        assertEquals("hex armor", ShapeSearch.normalize("  hex_armor-"));
    }

    @Test
    void emptyQueryKeepsTheOriginalOrder() {
        assertEquals(ALL, ShapeSearch.search(ALL, "   "));
    }

    @Test
    void findsByNameWithoutAccents() {
        assertEquals(PathShape.EXPRESSION, ShapeSearch.search(ALL, "expresion").getFirst());
        assertEquals(PathShape.WEB, ShapeSearch.search(ALL, "telarana").getFirst());
    }

    @Test
    void findsByCommandAlias() {
        assertEquals(PathShape.CHAINLINK, ShapeSearch.search(ALL, "chain").getFirst());
        assertEquals(PathShape.NOISE_TUBE, ShapeSearch.search(ALL, "roca").getFirst());
    }

    @Test
    void namePrefixBeatsDescriptionMatches() {
        List<PathShape> results = ShapeSearch.search(ALL, "esca");
        assertEquals(PathShape.SCALES, results.getFirst());
        assertTrue(results.contains(PathShape.SPIKED_SCALES));
    }

    @Test
    void everyWordMustMatch() {
        List<PathShape> results = ShapeSearch.search(ALL, "escamas espina");
        assertEquals(List.of(PathShape.SPIKED_SCALES), results);
        assertTrue(ShapeSearch.search(ALL, "zzzz").isEmpty());
    }

    @Test
    void descriptionCountsButWeighsLess() {
        assertTrue(ShapeSearch.score(PathShape.SWEEP, "estanco") > 0);
        assertTrue(ShapeSearch.score(PathShape.SWEEP, "perfil") > ShapeSearch.score(PathShape.SWEEP, "estanco"));
    }

    @Test
    void everyFigureHasAFamily() {
        for (PathShape shape : PathShape.values()) {
            ShapeCategory category = ShapeCategory.of(shape);
            assertFalse(category == ShapeCategory.ALL || category == ShapeCategory.FAVORITES, shape.name());
        }
        StudioPreferences preferences = new StudioPreferences();
        preferences.toggleFavorite(PathShape.COIL.id());
        assertTrue(ShapeCategory.FAVORITES.contains(PathShape.COIL, preferences));
        assertFalse(ShapeCategory.FAVORITES.contains(PathShape.RINGS, preferences));
        assertTrue(ShapeCategory.TUBE.contains(PathShape.EXPRESSION, preferences));
    }
}
