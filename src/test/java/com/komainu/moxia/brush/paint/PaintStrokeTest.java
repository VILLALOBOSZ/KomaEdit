package com.komainu.moxia.brush.paint;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El trazo al arrastrar: el espaciado, y la semilla libre o bloqueada (Axiom). */
class PaintStrokeTest {
    @Test
    void theFirstPointPaintsAtOnceAndTheRestEverySpacing() {
        PaintStroke stroke = new PaintStroke(10, 0.3, 1L, false, 0);
        assertEquals(3.0, stroke.spacing(), 1.0E-9);
        assertEquals(1, stroke.add(0.5, 10.5, 0.5).size(), "el clic, al momento");
        assertEquals(0, stroke.add(2.5, 10.5, 0.5).size(), "dos bloques aún no llegan al espaciado");
        List<PaintPainter.Dab> next = stroke.add(3.5, 10.5, 0.5);
        assertEquals(1, next.size());
        assertEquals(3.5, next.getFirst().x(), 1.0E-9, "el toque cae justo a 3 bloques del primero");
        List<PaintPainter.Dab> long_ = stroke.add(33.5, 10.5, 0.5);
        assertEquals(10, long_.size(), "30 bloques más: 10 toques");
        assertEquals(0, stroke.add(33.5, 10.5, 0.5).size(), "quieto no repite");
    }

    @Test
    void aTinyBrushStillLeavesADabEveryBlock() {
        PaintStroke stroke = new PaintStroke(1, 0.3, 1L, false, 0);
        assertEquals(1.0, stroke.spacing(), 1.0E-9);
        stroke.add(0.5, 0.5, 0.5);
        assertEquals(5, stroke.add(5.5, 0.5, 0.5).size());
    }

    @Test
    void aFreeSeedTurnsAndResizesTheFootprintOnEveryDab() {
        PaintStroke stroke = new PaintStroke(10, 0.3, 42L, false, 0);
        List<PaintPainter.Dab> dabs = new ArrayList<>(stroke.add(0.5, 0.5, 0.5));
        dabs.addAll(stroke.add(60.5, 0.5, 0.5));
        assertTrue(dabs.stream().map(PaintPainter.Dab::angle).distinct().count() > dabs.size() / 2, "giros");
        for (PaintPainter.Dab dab : dabs) {
            assertTrue(dab.radius() >= 10 * (1 - PaintStroke.SCALE_VARIATION) - 1.0E-9
                    && dab.radius() <= 10 * (1 + PaintStroke.SCALE_VARIATION) + 1.0E-9, "un poco de escala");
        }
        assertTrue(dabs.stream().map(PaintPainter.Dab::radius).distinct().count() > 1);
        // La cuenta sigue en el trazo siguiente: el clic de después tampoco se repite.
        PaintStroke after = new PaintStroke(10, 0.3, 42L, false, stroke.counter());
        assertNotEquals(dabs.getFirst(), after.add(0.5, 0.5, 0.5).getFirst());
    }

    @Test
    void aLockedSeedRepeatsTheSameDrawing() {
        List<PaintPainter.Dab> first = strokeOf(new PaintStroke(10, 0.3, 42L, true, 0));
        List<PaintPainter.Dab> second = strokeOf(new PaintStroke(10, 0.3, 42L, true, 1_234));
        assertEquals(first, second, "mismo trazo, mismos toques: la cuenta del pincel no cuenta");
        assertEquals(new PaintStroke(10, 0.3, 42L, true, 0).salt(), new PaintStroke(10, 0.3, 42L, true, 5).salt(),
                "y la misma trama");
        assertNotEquals(new PaintStroke(10, 0.3, 42L, false, 0).salt(), new PaintStroke(10, 0.3, 42L, false, 5).salt(),
                "libre, cada trazo su trama");
        assertEquals(1, first.stream().map(PaintPainter.Dab::angle).distinct().count(), "sin giro entre toques");
        assertEquals(1, first.stream().map(PaintPainter.Dab::radius).distinct().count(), "ni cambio de tamaño");
        List<PaintPainter.Dab> other = strokeOf(new PaintStroke(10, 0.3, 43L, true, 0));
        assertNotEquals(first.getFirst().angle(), other.getFirst().angle(), "otra semilla, otro giro");
    }

    @Test
    void aLockedSeedPaintsTheSameBlocksOnTheSameTerrain() {
        PaintFootprint mountain = FootprintLibrary.builtIn().get("montana");
        PaintPainter.Settings settings = PaintPainterTest.with(
                PaintPainterTest.settings(PaintGradient.SLOPE, 0.5, 0.3), mountain,
                PaintPainterTest.settings(PaintGradient.SLOPE, 0.5, 0.3).noise());
        Map<Long, Integer> once = painted(new PaintStroke(12, 0.3, 7L, true, 0), settings);
        Map<Long, Integer> twice = painted(new PaintStroke(12, 0.3, 7L, true, 99), settings);
        assertEquals(once, twice);
        Map<Long, Integer> free = painted(new PaintStroke(12, 0.3, 7L, false, 99), settings);
        assertNotEquals(once, free, "con la semilla libre cada clic sale distinto");
    }

    private static Map<Long, Integer> painted(PaintStroke stroke, PaintPainter.Settings settings) {
        return PaintPainterTest.paint(FakePaintWorld.hill(40, PaintPainterTest.PALETTE), strokeOf(stroke), settings,
                stroke.salt());
    }

    private static List<PaintPainter.Dab> strokeOf(PaintStroke stroke) {
        List<PaintPainter.Dab> dabs = new ArrayList<>();
        for (int x = -24; x <= 24; x += 6) {
            dabs.addAll(stroke.add(x + 0.5, FakePaintWorld.hillHeight(x, 4) + 0.5, 4.5));
        }
        return dabs;
    }
}
