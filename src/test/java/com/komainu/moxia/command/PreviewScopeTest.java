package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewScopeTest {
    @Test
    void editingCommandsTakeTheFlagAndManagementDoesNot() {
        for (String family : List.of("texture", "deform", "smooth", "build", "place", "scatter", "shape", "noisegen",
                "ezspline", "ivy", "weather", "tweak", "bend", "roof", "loft", "tree", "rock", "terrain", "text", "image",
                "shade", "snow", "gravity", "drain", "waterlog")) {
            assertTrue(PreviewScope.eligible(List.of(family, "<arguments>")), family);
        }
        for (String family : List.of("help", "path", "ruta", "pincel", "sculpt", "job", "cancel", "confirm",
                "preview", "noise", "sel", "reload", "symmetry", "capability", "quiet", "wiki", "color", "analyze",
                "biome", "bioma", "gradient")) {
            assertFalse(PreviewScope.eligible(List.of(family)), family);
        }
        assertFalse(PreviewScope.eligible(List.of("color", "gradient", "<args>")), "color gradient -v es la variante");
        assertFalse(PreviewScope.eligible(List.of("tree", "save", "<preset>")), "guardar un preset no edita");
        assertFalse(PreviewScope.eligible(List.of("roca", "lista")));
        assertFalse(PreviewScope.eligible(List.of("text", "fonts")));
        assertTrue(PreviewScope.eligible(List.of("tree", "<ajustes>")));
        assertTrue(PreviewScope.eligible(List.of("palette", "swap", "<args>")));
        assertFalse(PreviewScope.eligible(List.of("palette", "print", "<args>")), "palette print -v es «detallado»");
        assertFalse(PreviewScope.eligible(List.of()));
    }

    @Test
    void ezEditsVerticalFlagIsKept() {
        assertTrue(PreviewScope.shortFlagTaken(List.of("deform", "noise", "perlin")));
        assertTrue(PreviewScope.shortFlagTaken(List.of("deformar", "voxelize")));
        assertFalse(PreviewScope.shortFlagTaken(List.of("deform", "rotate", "90")));
        assertTrue(PreviewScope.shortFlagTaken(List.of("envejecer", "85", "-v", "70")), "-v es la vida");
        assertTrue(PreviewScope.shortFlagTaken(List.of("shape", "polydome", "stone")), "-v es el patrón de aristas");
        assertTrue(PreviewScope.shortFlagTaken(List.of("textura", "mezclar")));
        assertTrue(PreviewScope.shortFlagTaken(List.of("text", "\"hola\"", "-v")), "-v es el texto de pie");
        assertFalse(PreviewScope.shortFlagTaken(List.of("roof", "gable")), "en los tejados -v es la vista previa");
        assertFalse(PreviewScope.shortFlagTaken(List.of("texture", "noise")));
        assertFalse(PreviewScope.shortFlagTaken(List.of("shape", "cone")));
        assertFalse(PreviewScope.shortFlagTaken(List.of()));
    }
}
