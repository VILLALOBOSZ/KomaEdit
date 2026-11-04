package com.komainu.moxia.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gson lee sin recursión, así que un paquete puede traer unos ajustes con cien mil niveles. Copiarlos sí la usa: en el
 * hilo de los paquetes eso era un {@code StackOverflowError} que mataba la tarea sin decirle nada al jugador.
 */
class ShareDeepDataTest {
    @Test
    void deeplyNestedSettingsAreRefusedWithAMessage() throws InterruptedException {
        JsonObject data = new JsonObject();
        JsonObject current = data;
        for (int level = 0; level < 100_000; level++) {
            JsonObject next = new JsonObject();
            current.add("x", next);
            current = next;
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("k", "palette");
        entry.addProperty("n", "honda");
        entry.add("d", data);
        JsonArray items = new JsonArray();
        items.add(entry);
        JsonObject root = new JsonObject();
        root.addProperty("f", SharePack.FORMAT);
        root.add("i", items);
        Throwable[] thrown = {null};
        // Con la pila de un hilo normal, fijada para que no dependa de la JVM.
        Thread worker = new Thread(null, () -> {
            try {
                ShareCodec.fromJson(root, hash -> null, ShareLimits.DEFAULT);
            } catch (Throwable throwable) {
                thrown[0] = throwable;
            }
        }, "paquetes de prueba", 1024 * 1024);
        worker.start();
        worker.join();
        assertEquals(IllegalArgumentException.class, thrown[0] == null ? null : thrown[0].getClass(),
                String.valueOf(thrown[0]));
        assertTrue(thrown[0].getMessage().contains("anidados"), thrown[0].getMessage());
    }
}
