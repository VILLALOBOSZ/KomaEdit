package com.komainu.moxia.gizmo;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Engancha el fantasma con gizmo en el servidor: paquetes, tick y limpieza. */
public final class GhostNetwork {
    private GhostNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(GhostPayloads.Open.ID, GhostPayloads.Open.CODEC);
        PayloadTypeRegistry.playS2C().register(GhostPayloads.Part.ID, GhostPayloads.Part.CODEC);
        PayloadTypeRegistry.playS2C().register(GhostPayloads.Result.ID, GhostPayloads.Result.CODEC);
        PayloadTypeRegistry.playC2S().register(GhostPayloads.Confirm.ID, GhostPayloads.Confirm.CODEC);
        PayloadTypeRegistry.playC2S().register(GhostPayloads.Cancel.ID, GhostPayloads.Cancel.CODEC);
        PayloadTypeRegistry.playC2S().register(GhostPayloads.Mode.ID, GhostPayloads.Mode.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GhostPayloads.Confirm.ID, (payload, context) ->
                context.server().execute(() -> GhostService.confirm(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(GhostPayloads.Cancel.ID, (payload, context) ->
                context.server().execute(() -> GhostService.cancel(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(GhostPayloads.Mode.ID, (payload, context) ->
                context.server().execute(() -> GhostService.mode(context.player(), payload.mode())));
        ServerTickEvents.END_SERVER_TICK.register(GhostService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GhostService.forget(handler.player.getUuid()));
        // Como la vista previa: solo en su dimensión y para quien lo pidió.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                GhostService.discard(player, "al cambiar de dimensión"));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                GhostService.discard(newPlayer, "al reaparecer"));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GhostService.clearAll());
    }
}
