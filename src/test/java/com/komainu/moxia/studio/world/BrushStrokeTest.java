package com.komainu.moxia.studio.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pintar manteniendo el clic: una pasada al pulsar y otra cada poco mientras el cursor cambia de bloque. */
class BrushStrokeTest {
    @Test
    void holdingTheClickKeepsPaintingAtTheRhythmOfARightClickHeldDown() {
        BrushStroke stroke = new BrushStroke();
        assertTrue(stroke.press(new int[]{0, 64, 0}, 1_000), "al pulsar, ya");
        assertFalse(stroke.drag(new int[]{1, 64, 0}, 1_000 + BrushStroke.INTERVAL_MILLIS - 1), "aún no");
        assertTrue(stroke.drag(new int[]{1, 64, 0}, 1_000 + BrushStroke.INTERVAL_MILLIS));
        assertFalse(stroke.drag(new int[]{1, 64, 0}, 2_000), "en el mismo bloque no se repite");
        assertFalse(stroke.drag(null, 3_000), "al cielo, nada");
        assertTrue(stroke.drag(new int[]{2, 64, 0}, 3_000));
        assertEquals(3, stroke.strokes());
        stroke.release();
        assertFalse(stroke.active());
        assertFalse(stroke.drag(new int[]{5, 64, 0}, 9_000), "soltado, ya no pinta");
    }

    @Test
    void pressingTheAirPaintsNothing() {
        BrushStroke stroke = new BrushStroke();
        assertFalse(stroke.press(null, 0));
        assertEquals(0, stroke.strokes());
    }
}
