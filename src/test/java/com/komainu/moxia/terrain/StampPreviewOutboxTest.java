package com.komainu.moxia.terrain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las miniaturas de los sellos propios se piden por paquete: un cliente modificado puede pedir las mismas sin parar o
 * miles distintas. Antes cada petición las leía otra vez del disco y las dejaba en una cola sin fondo.
 */
class StampPreviewOutboxTest {
    @Test
    void askingForTheSamePreviewAgainDoesNotReadItAgain() {
        StampPreviewOutbox<String> outbox = new StampPreviewOutbox<>();
        int reads = 0;
        for (int packet = 0; packet < 100_000; packet++) {
            if (outbox.request("a".repeat(64))) {
                reads++;
            }
        }
        assertEquals(1, reads, "se lee una vez mientras se debe");
        outbox.offer("a".repeat(64), "miniatura");
        assertFalse(outbox.request("a".repeat(64)), "ni mientras espera su turno");
        assertEquals(List.of("miniatura"), outbox.poll(1, preview -> 8_192));
        assertTrue(outbox.request("a".repeat(64)), "ya salió: se puede volver a pedir");
    }

    @Test
    void thousandsOfDifferentRequestsStopAtTheCap() {
        StampPreviewOutbox<String> outbox = new StampPreviewOutbox<>();
        int reads = 0;
        for (int hash = 0; hash < 100_000; hash++) {
            String id = String.format("%064x", hash);
            if (outbox.request(id)) {
                reads++;
                outbox.offer(id, id);
            }
        }
        assertEquals(StampPreviewOutbox.MAX_PENDING, reads);
        assertEquals(StampPreviewOutbox.MAX_PENDING, outbox.owed());
    }

    @Test
    void eachTickSendsWithinItsBudgetAndAtLeastOne() {
        StampPreviewOutbox<String> outbox = new StampPreviewOutbox<>();
        for (String id : List.of("1", "2", "3", "4")) {
            outbox.request(id);
            outbox.offer(id, id);
        }
        assertEquals(List.of("1", "2"), outbox.poll(16_384, preview -> 8_192));
        assertEquals(List.of("3"), outbox.poll(1, preview -> 8_192), "al menos una aunque no quepa");
        outbox.failed("4");
        assertTrue(outbox.poll(16_384, preview -> 8_192).isEmpty(), "la que falló ya no sale");
        assertTrue(outbox.isEmpty());
    }
}
