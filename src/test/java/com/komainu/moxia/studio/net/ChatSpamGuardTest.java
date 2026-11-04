package com.komainu.moxia.studio.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El freno del cliente para un servidor sin el paquete de los botones, contra el antispam de Minecraft
 * ({@link VanillaAntispam}): con él, por mucho que se pulse, nadie sale expulsado; sin él, a la docena de clics sí.
 */
class ChatSpamGuardTest {
    private static final long START = 3_000_000_000L;
    private static final long MILLIS = 1_000_000L;

    @Test
    void withoutTheGuardADozenClicksInARowGetABuilderKicked() {
        VanillaAntispam server = new VanillaAntispam(20, START);
        long now = START;
        for (int click = 0; click < 30; click++) {
            server.command(now);
            now += 100 * MILLIS;
        }
        // Diez por segundo: 20 de más por clic y 2 ticks de bajada entre uno y otro.
        assertEquals(12, server.kickedAt(), "el fallo de antes, que la prueba sabe ver");
    }

    @Test
    void withTheGuardNoClickingGetsABuilderKicked() {
        // Desde todo de golpe hasta uno cada segundo, con el servidor a su ritmo o algo lento.
        for (int tps : new int[]{20, 18, 15}) {
            for (long gap : new long[]{0, 10, 50, 100, 250, 500, 1_000, 1_500}) {
                ChatSpamGuard guard = new ChatSpamGuard();
                VanillaAntispam server = new VanillaAntispam(tps, START);
                long now = START;
                for (int click = 0; click < 300; click++) {
                    if (guard.allow(now)) {
                        server.command(now);
                    }
                    now += gap * MILLIS;
                }
                assertFalse(server.kicked(), tps + " ticks/s, un clic cada " + gap + " ms: " + server.max());
                assertTrue(server.commands() >= 8, "algo sale: " + server.commands());
            }
        }
    }

    @Test
    void aShortBurstGoesThroughAndThenItSaysHowLongToWait() {
        ChatSpamGuard guard = new ChatSpamGuard();
        for (int click = 0; click < 8; click++) {
            assertTrue(guard.allow(START), "clic " + (click + 1));
        }
        assertFalse(guard.allow(START));
        int wait = guard.waitSeconds(START);
        assertEquals(2, wait, "20 de más a 15 ticks por segundo");
        assertFalse(guard.allow(START + (wait - 1) * 1_000 * MILLIS));
        assertTrue(guard.allow(START + wait * 1_000 * MILLIS));
    }

    @Test
    void joiningAServerStartsFromZero() {
        ChatSpamGuard guard = new ChatSpamGuard();
        while (guard.allow(START)) {
            // Hasta el tope.
        }
        guard.reset();
        assertTrue(guard.allow(START));
    }
}
