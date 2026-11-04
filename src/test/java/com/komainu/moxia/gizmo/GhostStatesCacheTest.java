package com.komainu.moxia.gizmo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los ids de estado del fantasma los manda el servidor. Uno malicioso que mandaba paletas de 65536 ids inventados (que
 * se leen como aire) dejaba cada uno, por cada giro, en los cachés de {@link GhostStates}, que nunca se vacían.
 */
class GhostStatesCacheTest {
    private static final int REGISTERED = 27_000;

    @Test
    void onlyRealStatesAreKept() {
        assertTrue(GhostStates.cacheable(0, REGISTERED));
        assertTrue(GhostStates.cacheable(REGISTERED - 1, REGISTERED));
        assertFalse(GhostStates.cacheable(REGISTERED, REGISTERED));
        assertFalse(GhostStates.cacheable(0x10000000, REGISTERED), "un id inventado");
        assertFalse(GhostStates.cacheable(-1, REGISTERED));
    }
}
