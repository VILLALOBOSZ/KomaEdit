package com.komainu.moxia.selection;

import com.komainu.moxia.performance.ServerWork;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Engancha la selección a mano en el servidor: paquetes, el tick del lazo y la limpieza. */
public final class SelectionNetwork {
    private SelectionNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(SelectionPayloads.Lasso.ID, SelectionPayloads.Lasso.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectionPayloads.Brush.ID, SelectionPayloads.Brush.CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionPayloads.Outline.ID, SelectionPayloads.Outline.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SelectionPayloads.Lasso.ID, (payload, context) ->
                context.server().execute(() -> SelectionToolService.lasso(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SelectionPayloads.Brush.ID, (payload, context) ->
                context.server().execute(() -> SelectionToolService.brush(context.player(), payload)));
        ServerWork.light(SelectionToolService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SelectionToolService.forget(handler.player.getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                SelectionToolService.discard(player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SelectionToolService.clearAll());
    }
}
