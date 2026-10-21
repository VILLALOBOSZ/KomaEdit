package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.studio.live.LivePreviewRequestPayload;
import com.komainu.moxia.studio.live.LivePreviewResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * La red de la vista previa en vivo en el cliente: manda las peticiones del Studio por su paquete (sin antispam) y
 * guarda lo que contesta el servidor para que el Studio lo lea. Si la respuesta es un fantasma, se lo dice a
 * {@link GhostClient} antes de que llegue: así sabe que sustituye al del Studio.
 */
public final class LiveClient {
    private static final int MAX_RESULTS = 16;
    private static final Deque<LivePreviewResultPayload> RESULTS = new ArrayDeque<>();

    private LiveClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LivePreviewResultPayload.ID, (payload, context) ->
                context.client().execute(() -> receive(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(RESULTS::clear));
    }

    private static void receive(LivePreviewResultPayload payload) {
        if (payload.outcomeValue() == LivePreviewResultPayload.Outcome.GHOST) {
            GhostClient.claimStudio(payload.id(), payload.refresh());
        }
        RESULTS.addLast(payload);
        while (RESULTS.size() > MAX_RESULTS) {
            RESULTS.removeFirst();
        }
    }

    /** Si el servidor tiene la red de la vista previa en vivo (uno anterior no: se usa el chat). */
    public static boolean supported() {
        return ClientPlayNetworking.canSend(LivePreviewRequestPayload.ID);
    }

    /** @return si se pudo mandar por la red de la vista previa en vivo */
    public static boolean send(LivePreviewRequestPayload request) {
        if (!supported()) {
            return false;
        }
        ClientPlayNetworking.send(request);
        return true;
    }

    /** La respuesta más antigua sin leer, una sola vez. */
    public static Optional<LivePreviewResultPayload> take() {
        return Optional.ofNullable(RESULTS.pollFirst());
    }
}
