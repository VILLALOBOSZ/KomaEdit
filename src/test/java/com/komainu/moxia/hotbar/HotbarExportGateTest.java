package com.komainu.moxia.hotbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El código de barras que manda el servidor solo va al portapapeles si el jugador acaba de pedir «Exportar». */
class HotbarExportGateTest {
    @Test
    void anUnaskedCodeNeverReachesTheClipboard() {
        HotbarExportGate gate = new HotbarExportGate();
        assertFalse(gate.accept(1_000L), "sin pedirlo");
        gate.expect(1_000L);
        assertTrue(gate.accept(2_000L), "lo que se pidió");
        assertFalse(gate.accept(2_100L), "y solo una vez por petición");
    }

    @Test
    void anOldRequestDoesNotOpenTheClipboardLater() {
        HotbarExportGate gate = new HotbarExportGate();
        gate.expect(0L);
        assertFalse(gate.accept(HotbarExportGate.WINDOW_MILLIS + 1L));
        gate.expect(100L);
        gate.clear();
        assertFalse(gate.accept(200L), "al salir del servidor se olvida");
    }
}
