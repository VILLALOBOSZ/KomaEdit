package com.komainu.moxia.feedback;

import com.komainu.moxia.brush.ez.AlignmentGuides;
import com.komainu.moxia.preview.PreviewPlanPayload;
import com.komainu.moxia.preview.PreviewPlanSender;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Engancha en el servidor lo que el cliente con KomaEdit dibuja y suena por su cuenta: el plan de la vista previa,
 * la selección, la simetría, el visualizador de la alineación de los pinceles de colocación y los avisos de sonido.
 * Los clientes sin el mod no reciben nada de esto (la alineación les llega con block displays).
 */
public final class WorldFeedback {
    private WorldFeedback() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(FeedbackEventPayload.ID, FeedbackEventPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PreviewPlanPayload.ID, PreviewPlanPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionPayload.ID, SelectionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SymmetryGuidePayload.ID, SymmetryGuidePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AlignmentGuidePayload.ID, AlignmentGuidePayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(PreviewPlanSender::tick);
        ServerTickEvents.END_SERVER_TICK.register(WorldOverlaySync::tick);
        ServerTickEvents.END_SERVER_TICK.register(AlignmentGuides::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PreviewPlanSender.forget(handler.player.getUuid());
            WorldOverlaySync.forget(handler.player.getUuid());
            AlignmentGuides.forget(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PreviewPlanSender.clearAll();
            WorldOverlaySync.clearAll();
            AlignmentGuides.clearAll();
        });
    }
}
