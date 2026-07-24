package com.komainu.moxia;

import com.komainu.moxia.brush.BrushManager;
import com.komainu.moxia.command.MoxiaCommands;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.editor.EditorActionPayload;
import com.komainu.moxia.editor.EditorActionService;
import com.komainu.moxia.editor.FastPaintService;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathHologramPayload;
import com.komainu.moxia.path.PathHologramSync;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.recipe.RecipeActionPayload;
import com.komainu.moxia.recipe.RecipeActionService;
import com.komainu.moxia.recipe.RecipeCatalogPayload;
import com.komainu.moxia.recipe.InstanceCatalogPayload;
import com.komainu.moxia.recipe.ProceduralInstanceStore;
import com.komainu.moxia.recipe.RecipeStore;
import com.komainu.moxia.ui.QuickBuildPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class KomaEditMod implements ModInitializer {
    public static final String MOD_ID = "komaedit";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(PathHologramPayload.ID, PathHologramPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuickBuildPayload.ID, QuickBuildPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RecipeCatalogPayload.ID, RecipeCatalogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InstanceCatalogPayload.ID, InstanceCatalogPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EditorActionPayload.ID, EditorActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RecipeActionPayload.ID, RecipeActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EditorActionPayload.ID, (payload, context) ->
                context.server().execute(() -> EditorActionService.handle(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(RecipeActionPayload.ID, (payload, context) ->
                context.server().execute(() -> RecipeActionService.handle(context.player(), payload))
        );
        MoxiaCommands.register();
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            if (hand == Hand.MAIN_HAND && PathManager.isCaptureMode(player.getUuid())) {
                try {
                    var placement = hitResult.getBlockPos().offset(hitResult.getSide());
                    var point = PathManager.add(player.getUuid(), placement);
                    PathHologramSync.sync(serverPlayer);
                    int count = PathManager.saved(player.getUuid()).points().size();
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal(
                            "KomaEdit · Punto " + count + ": " + point.x() + ", " + point.y() + ", " + point.z()
                    ), true);
                    return ActionResult.SUCCESS;
                } catch (IllegalArgumentException exception) {
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal(
                            "KomaEdit · " + exception.getMessage()
                    ), true);
                    return ActionResult.FAIL;
                }
            }
            if (BrushManager.settings(player.getUuid()).isEmpty()) {
                return ActionResult.PASS;
            }
            try {
                int changed = BrushManager.stamp(serverPlayer, hitResult);
                serverPlayer.sendMessage(net.minecraft.text.Text.literal(
                        "KomaEdit · Figura estampada: " + changed + " bloques · //undo"
                ), true);
                return ActionResult.SUCCESS;
            } catch (Exception exception) {
                serverPlayer.sendMessage(net.minecraft.text.Text.literal(
                        "KomaEdit · " + (exception.getMessage() == null ? "No se pudo estampar" : exception.getMessage())
                ), true);
                return ActionResult.FAIL;
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                clearProceduralSession(handler.player.getUuid())
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            BatchEditService.cancel(handler.player.getUuid(), false);
            FastPaintService.remove(handler.player.getUuid());
            PathManager.remove(handler.player.getUuid());
            BrushManager.remove(handler.player.getUuid());
            clearProceduralSession(handler.player.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(BatchEditService::tick);
        ServerTickEvents.END_SERVER_TICK.register(FastPaintService::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> AsyncEngine.start());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BatchEditService.shutdown();
            FastPaintService.shutdown();
            AsyncEngine.shutdown();
            PathManager.clearAll();
            BrushManager.clearAll();
        });
        LOGGER.info("KomaEdit WorldEdit add-on initialized");
    }

    private static void clearProceduralSession(UUID playerId) {
        try {
            RecipeStore.clear(playerId);
            ProceduralInstanceStore.clear(playerId);
        } catch (IllegalStateException exception) {
            LOGGER.warn("Could not clear procedural session data for {}", playerId, exception);
        }
    }
}
