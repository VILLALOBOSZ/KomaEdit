package com.komainu.moxia.feedback;

import com.komainu.moxia.preview.PreviewPlanPayload;
import com.komainu.moxia.preview.PreviewPlanSender;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Engancha en el servidor lo que el cliente con KomaEdit dibuja y suena por su cuenta: el plan de la vista previa,
 * la selección, la simetría y los avisos de sonido. Los clientes sin el mod no reciben nada de esto.
 */
public final class WorldFeedback {
    private WorldFeedback() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(FeedbackEventPayload.ID, FeedbackEventPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PreviewPlanPayload.ID, PreviewPlanPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionPayload.ID, SelectionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SymmetryGuidePayload.ID, SymmetryGuidePayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(PreviewPlanSender::tick);
        ServerTickEvents.END_SERVER_TICK.register(WorldOverlaySync::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PreviewPlanSender.forget(handler.player.getUuid());
            WorldOverlaySync.forget(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PreviewPlanSender.clearAll();
            WorldOverlaySync.clearAll();
        });
    }
}
