package com.komainu.moxia.brush.ez;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrushOptionsTest {
    @Test
    void parsesGradientLikeEzEdits() {
        BrushOptions options = EzBrushType.GRADIENT.options(
                "\"##Magma, stone\" 12 tapered 0.7 -avw -n Perlin(Freq:0.25, Seed:3) -z 2 -d manhattan");
        assertEquals("##Magma, stone", options.positional(0));
        assertEquals(12, options.integer(1, 8, 1, 32, "El radio"));
        assertEquals("tapered", options.positional(2));
        assertEquals(0.7, options.decimal(3, 0.5, 0, 4, "El sangrado"), 1.0E-9);
        assertTrue(options.has('a') && options.has('v') && options.has('w'));
        assertEquals("Perlin(Freq:0.25, Seed:3)", options.flag('n'), "los espacios dentro de paréntesis no cortan");
        assertEquals(2.0, options.flagDecimal('z', 1, 0, 100, "La escala"), 1.0E-9);
        assertEquals("manhattan", options.flag('d'));
    }

    @Test
    void invertedPaletteIsNotAFlag() {
        BrushOptions options = EzBrushType.PALETTE_SHIFT.options("-##Grayscale(1:8) 6 -2 -r");
        assertEquals("-##Grayscale(1:8)", options.positional(0));
        assertEquals(-2, options.integer(2, 1, -255, 255, "El desplazamiento"), "un número negativo es posicional");
        assertTrue(options.has('r'));
    }

    @Test
    void defaultsAndErrors() {
        BrushOptions options = EzBrushType.PETAL_PAINTER.options("petals");
        assertEquals(8, options.integer(1, 8, 1, 32, "El radio"));
        assertEquals(0.3, options.decimal(2, 0.3, 0, 1, "La densidad"), 1.0E-9);
        assertNull(options.flag('m'));
        assertThrows(IllegalArgumentException.class, () -> EzBrushType.PETAL_PAINTER.options("petals -m"));
        assertThrows(IllegalArgumentException.class,
                () -> EzBrushType.PETAL_PAINTER.options("petals 40").integer(1, 8, 1, 32, "El radio"));
        assertThrows(IllegalArgumentException.class, () -> EzBrushType.GRADIENT.options("##Magma -an Perlin()"),
                "una bandera con valor no se junta con otras");
        assertThrows(IllegalArgumentException.class, () -> BrushOptions.tokenize("\"abierto"));
    }

    @Test
    void tokenizerKeepsGroups() {
        assertEquals(List.of("Cl(O:C, R:90)", "C(D:(-1,2,-1))", "-x"),
                BrushOptions.tokenize("  Cl(O:C, R:90)   C(D:(-1,2,-1)) -x "));
        assertEquals(List.of("[stone, dirt]*2"), BrushOptions.tokenize("[stone, dirt]*2"));
    }

    @Test
    void typesByNameAndAlias() {
        assertEquals(EzBrushType.GRADIENT_STROKE, EzBrushType.byId("gradientstroke"));
        assertEquals(EzBrushType.PETAL_PAINTER, EzBrushType.byId("petalos"));
        assertEquals(EzBrushType.PLACE, EzBrushType.byId("pl"));
        assertThrows(IllegalArgumentException.class, () -> EzBrushType.byId("scatter"));
        assertFalse(BrushOptions.isNumber("tapered"));
        assertTrue(BrushOptions.isNumber("0,5"));
    }

    @Test
    void unknownLettersStayPositional() {
        BrushOptions options = BrushOptions.parse("-stone 4", Set.of('a'), Set.of());
        assertEquals("-stone", options.positional(0));
    }
}
