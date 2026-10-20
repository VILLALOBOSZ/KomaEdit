package com.komainu.moxia.studio.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El servidor solo acepta un clic del Studio al alcance de la mirada del jugador. */
class StudioAimTest {
    @Test
    void onlyClicksWithinReachCount() {
        assertTrue(StudioAim.within(0.5, 65.6, 0.5, 100, 64, 0, 160.0));
        assertTrue(StudioAim.within(0.5, 65.6, 0.5, 160, 64, 0, 160.0), "medio bloque de margen");
        assertFalse(StudioAim.within(0.5, 65.6, 0.5, 170, 64, 0, 160.0), "más lejos de lo que se ve");
        assertFalse(StudioAim.within(0.5, 65.6, 0.5, 0, 64, 300, 160.0));
    }
}
