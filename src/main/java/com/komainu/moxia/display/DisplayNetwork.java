package com.komainu.moxia.display;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Engancha el editor de displays en el servidor: paquetes, guardado del registro y limpieza. */
public final class DisplayNetwork {
    private DisplayNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(DisplayPayloads.Selection.ID, DisplayPayloads.Selection.CODEC);
        PayloadTypeRegistry.playC2S().register(DisplayPayloads.Pick.ID, DisplayPayloads.Pick.CODEC);
        PayloadTypeRegistry.playC2S().register(DisplayPayloads.Edit.ID, DisplayPayloads.Edit.CODEC);
        PayloadTypeRegistry.playC2S().register(DisplayPayloads.Act.ID, DisplayPayloads.Act.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DisplayPayloads.Pick.ID, (payload, context) ->
                context.server().execute(() -> DisplayService.pick(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(DisplayPayloads.Edit.ID, (payload, context) ->
                context.server().execute(() -> DisplayService.edit(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(DisplayPayloads.Act.ID, (payload, context) ->
                context.server().execute(() -> DisplayService.act(context.player(), payload)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DisplayService.forget(handler.player.getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                DisplayService.changedWorld(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                DisplayService.changedWorld(newPlayer));
        ServerLifecycleEvents.SERVER_STARTED.register(DisplayService::started);
        ServerTickEvents.END_SERVER_TICK.register(DisplayService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DisplayService.stopped());
    }
}
