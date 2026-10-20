package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewScopeTest {
    @Test
    void editingCommandsTakeTheFlagAndManagementDoesNot() {
        for (String family : List.of("texture", "deform", "smooth", "build", "place", "scatter", "shape", "noisegen",
                "ezspline", "ivy", "weather", "tweak", "gradient", "bend")) {
            assertTrue(PreviewScope.eligible(List.of(family, "<arguments>")), family);
        }
        for (String family : List.of("help", "path", "ruta", "pincel", "sculpt", "job", "cancel", "confirm",
                "preview", "noise", "sel", "reload")) {
            assertFalse(PreviewScope.eligible(List.of(family)), family);
        }
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
        assertFalse(PreviewScope.shortFlagTaken(List.of("texture", "noise")));
        assertFalse(PreviewScope.shortFlagTaken(List.of("shape", "cone")));
        assertFalse(PreviewScope.shortFlagTaken(List.of()));
    }

    @Test
    void textAndBiomeKeepTheirOwnVerticalFlag() {
        // En el texto -v es «de pie» y en los biomas el relleno vertical: la vista previa se los comía.
        for (String family : List.of("text", "texto", "biome", "bioma")) {
            assertTrue(PreviewScope.shortFlagTaken(List.of(family, "<arguments>")), family);
        }
    }
}
