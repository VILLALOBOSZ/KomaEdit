package com.komainu.moxia;

import com.komainu.moxia.brush.BrushManager;
import com.komainu.moxia.command.MoxiaCommands;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.editor.EditorActionPayload;
import com.komainu.moxia.editor.EditorActionService;
import com.komainu.moxia.editor.FastPaintService;
import com.komainu.moxia.ivy.IvyService;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathHologramPayload;
import com.komainu.moxia.path.PathHologramSync;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.persistence.ProjectStorage;
import com.komainu.moxia.persistence.OperationLedger;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.recipe.RecipeActionPayload;
import com.komainu.moxia.recipe.RecipeActionService;
import com.komainu.moxia.recipe.RecipeCatalogPayload;
import com.komainu.moxia.recipe.InstanceCatalogPayload;
import com.komainu.moxia.sculpt.SnapshotCaptureService;
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

public final class KomaEditMod implements ModInitializer {
    public static final String MOD_ID = "komaedit";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        KomaEditConfig.load();
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
                    KomaPermissions.require(serverPlayer, KomaPermissions.PATH);
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
                KomaPermissions.require(serverPlayer, KomaPermissions.BUILD);
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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                ProjectStorage.bind(handler.player.getUuid(), server);
            } catch (RuntimeException exception) {
                ProjectStorage.unbind(handler.player.getUuid());
                LOGGER.error("Could not open KomaEdit project for {}", handler.player.getUuid(), exception);
                handler.player.sendMessage(net.minecraft.text.Text.literal(
                        "KomaEdit · El proyecto no pudo abrirse; KomaEdit queda desactivado en este mundo. "
                                + "Revisa latest.log"
                ), false);
                return;
            }
            int interrupted = OperationLedger.markInterrupted(handler.player.getUuid());
            if (interrupted > 0) {
                handler.player.sendMessage(net.minecraft.text.Text.literal(
                        "KomaEdit · " + interrupted
                                + " trabajo(s) anterior(es) quedaron interrumpidos; revisa /komaedit project status"
                ), false);
            }
            try {
                // El proyecto ya está abierto: un catálogo que no se pueda enviar no debe desactivar KomaEdit.
                RecipeActionService.sync(handler.player);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not sync KomaEdit catalogs for {}", handler.player.getUuid(), exception);
                handler.player.sendMessage(net.minecraft.text.Text.literal(
                        "KomaEdit · No se pudieron cargar las recetas o instancias guardadas; revisa latest.log"
                ), false);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            BatchEditService.cancel(handler.player.getUuid(), false);
            FastPaintService.remove(handler.player.getUuid());
            IvyService.cancel(handler.player.getUuid());
            PathManager.remove(handler.player.getUuid());
            BrushManager.remove(handler.player.getUuid());
            ProjectStorage.unbind(handler.player.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(BatchEditService::tick);
        ServerTickEvents.END_SERVER_TICK.register(FastPaintService::tick);
        ServerTickEvents.END_SERVER_TICK.register(SnapshotCaptureService::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> AsyncEngine.start());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BatchEditService.shutdown();
            FastPaintService.shutdown();
            SnapshotCaptureService.clearAll();
            AsyncEngine.shutdown();
            PathManager.clearAll();
            BrushManager.clearAll();
            ProjectStorage.clearBindings();
        });
        LOGGER.info("KomaEdit WorldEdit add-on initialized");
    }
}
