package com.komainu.moxia.studio.net;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada petición del estado de herramientas lee la carpeta de fuentes, los presets y el historial y contesta con un
 * paquete grande; antes no tenía freno y un cliente modificado la podía pedir sin parar.
 */
class ToolStateRequestBudgetTest {
    @Test
    void aFloodOfStateRequestsIsCut() {
        UUID player = new UUID(9L, 1L);
        int served = 0;
        for (int packet = 0; packet < 10_000; packet++) {
            if (StudioToolsNetwork.STATE_REQUESTS.allow(player, 2_000_000_000L)) {
                served++;
            }
        }
        assertEquals(10, served);
        StudioToolsNetwork.forget(player);
    }

    @Test
    void theStudioRefreshPlusAHistoryJumpIsAlwaysServed() {
        UUID player = new UUID(9L, 2L);
        long now = 5_000_000_000L;
        for (int second = 0; second < 120; second++) {
            // El refresco de cada segundo con el historial abierto y un salto por el historial.
            assertTrue(StudioToolsNetwork.STATE_REQUESTS.allow(player, now), "refresco " + second);
            assertTrue(StudioToolsNetwork.STATE_REQUESTS.allow(player, now + 1_000_000L), "salto " + second);
            now += 1_000_000_000L;
        }
        StudioToolsNetwork.forget(player);
    }
}
