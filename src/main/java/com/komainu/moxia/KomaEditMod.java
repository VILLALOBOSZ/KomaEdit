package com.komainu.moxia;

import com.komainu.moxia.brush.BrushManager;
import com.komainu.moxia.brush.BrushToolInputs;
import com.komainu.moxia.capability.CapabilityConfig;
import com.komainu.moxia.capability.CapabilityService;
import com.komainu.moxia.command.MoxiaCommands;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.editor.EditorActionPayload;
import com.komainu.moxia.editor.EditorActionService;
import com.komainu.moxia.editor.FastPaintService;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.gradient.ServerPalettes;
import com.komainu.moxia.ivy.IvyService;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathHologramPayload;
import com.komainu.moxia.path.PathHologramSync;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.performance.ServerWork;
import com.komainu.moxia.persistence.ProjectStorage;
import com.komainu.moxia.persistence.OperationLedger;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.preview.PreviewDisplays;
import com.komainu.moxia.preview.PreviewHologramPayload;
import com.komainu.moxia.progress.ProgressBars;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.recipe.RecipeActionPayload;
import com.komainu.moxia.recipe.RecipeActionService;
import com.komainu.moxia.recipe.RecipeCatalogPayload;
import com.komainu.moxia.recipe.InstanceCatalogPayload;
import com.komainu.moxia.sculpt.SnapshotCaptureService;
import com.komainu.moxia.symmetry.SymmetryService;
import com.komainu.moxia.ui.QuickBuildPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
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
        ServerPalettes.load(KomaEditConfig.palettesFile());
        PayloadTypeRegistry.playS2C().register(PathHologramPayload.ID, PathHologramPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuickBuildPayload.ID, QuickBuildPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RecipeCatalogPayload.ID, RecipeCatalogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InstanceCatalogPayload.ID, InstanceCatalogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PreviewHologramPayload.ID, PreviewHologramPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.komainu.moxia.studio.net.StudioWelcomePayload.ID,
                com.komainu.moxia.studio.net.StudioWelcomePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EditorActionPayload.ID, EditorActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RecipeActionPayload.ID, RecipeActionPayload.CODEC);
        com.komainu.moxia.studio.net.StudioToolsNetwork.register();
        com.komainu.moxia.feedback.WorldFeedback.register();
        com.komainu.moxia.gizmo.GhostNetwork.register();
        ServerPlayNetworking.registerGlobalReceiver(EditorActionPayload.ID, (payload, context) ->
                context.server().execute(() -> EditorActionService.handle(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(RecipeActionPayload.ID, (payload, context) ->
                context.server().execute(() -> RecipeActionService.handle(context.player(), payload))
        );
        com.komainu.moxia.blueprint.BlueprintNetwork.register();
        MoxiaCommands.register();
        com.komainu.moxia.worldedit.EzExtensions.register();
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
                    serverPlayer.sendMessage(Feedback.line(Feedback.Level.INFO,
                            "Punto " + count + ": " + point.x() + ", " + point.y() + ", " + point.z()), true);
                    return ActionResult.SUCCESS;
                } catch (IllegalArgumentException exception) {
                    serverPlayer.sendMessage(Feedback.line(Feedback.Level.ERROR,
                            String.valueOf(exception.getMessage())), true);
                    return ActionResult.FAIL;
                }
            }
            if (BrushManager.settings(player.getUuid()).isEmpty()) {
                return ActionResult.PASS;
            }
            try {
                KomaPermissions.require(serverPlayer, KomaPermissions.BUILD);
                // Un estampado de pincel se aplica al momento: sin la vista previa universal.
                int changed = EditRequests.within(EditRequest.TOOL, () -> {
                    try {
                        return BrushManager.stamp(serverPlayer, hitResult);
                    } catch (RuntimeException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception.getMessage(), exception);
                    }
                });
                serverPlayer.sendMessage(Feedback.line(Feedback.Level.OK,
                        Feedback.format("Figura estampada: %s bloques · //undo", changed)), true);
                return ActionResult.SUCCESS;
            } catch (Exception exception) {
                serverPlayer.sendMessage(Feedback.line(Feedback.Level.ERROR,
                        exception.getMessage() == null ? "No se pudo estampar" : exception.getMessage()), true);
                return ActionResult.FAIL;
            }
        });
        BrushToolInputs.register();
        com.komainu.moxia.brush.paint.PaintStrokes.register();
        CapabilityConfig.load();
        CapabilityService.register();
        // Lo que se coloca a mano con las capacidades (reemplazar, extruir…) también se replica en simetría.
        com.komainu.moxia.capability.HandEditEvents.COLLECT.register((player, world, planned, extra) ->
                SymmetryService.mirrorHandEdits(player, world,
                        planned.stream().map(com.komainu.moxia.capability.HandEdit::pos).toList(),
                        planned.stream().map(com.komainu.moxia.capability.HandEdit::state).toList(),
                        (pos, state) -> extra.accept(new com.komainu.moxia.capability.HandEdit(pos, state))));
        SymmetryService.register();
        com.komainu.moxia.biome.BiomeEditService.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                ProjectStorage.bind(handler.player.getUuid(), server);
            } catch (RuntimeException exception) {
                ProjectStorage.unbind(handler.player.getUuid());
                LOGGER.error("Could not open KomaEdit project for {}", handler.player.getUuid(), exception);
                handler.player.sendMessage(Feedback.line(Feedback.Level.ERROR,
                        "El proyecto no pudo abrirse; KomaEdit queda desactivado en este mundo · revisa latest.log"),
                        false);
                return;
            }
            int interrupted = OperationLedger.markInterrupted(handler.player.getUuid());
            if (interrupted > 0) {
                handler.player.sendMessage(Feedback.line(Feedback.Level.WARN, Feedback.format(
                        "%s trabajo(s) anterior(es) quedaron interrumpidos · revisa /komaedit project status",
                        interrupted)), false);
            }
            try {
                // El proyecto ya está abierto: un catálogo que no se pueda enviar no debe desactivar KomaEdit.
                RecipeActionService.sync(handler.player);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not sync KomaEdit catalogs for {}", handler.player.getUuid(), exception);
                handler.player.sendMessage(Feedback.line(Feedback.Level.WARN,
                        "No se pudieron cargar las recetas o instancias guardadas · revisa latest.log"), false);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            BatchEditService.cancel(handler.player.getUuid(), false);
            com.komainu.moxia.rebuild.RebuildService.disconnected(handler.player.getUuid());
            // Lo que aún se lee o se calcula para él ya no tiene a quién entregarse.
            SnapshotCaptureService.cancel(handler.player.getUuid());
            AsyncEngine.cancel(handler.player.getUuid());
            FastPaintService.remove(handler.player.getUuid());
            IvyService.cancel(handler.player.getUuid());
            PathManager.remove(handler.player.getUuid());
            com.komainu.moxia.edit.PendingBuilds.remove(handler.player.getUuid());
            BrushManager.remove(handler.player.getUuid());
            ProjectStorage.unbind(handler.player.getUuid());
            RegionOperationService.cancel(handler.player.getUuid());
            ServerWork.forget(handler.player.getUuid());
            com.komainu.moxia.region.SelectionParts.forget(handler.player.getUuid());
            com.komainu.moxia.persistence.QuietMode.forget(handler.player.getUuid());
            PlanPreviews.forget(handler.player.getUuid());
            ProgressBars.remove(handler.player);
            com.komainu.moxia.studio.net.StudioToolsNetwork.forget(handler.player.getUuid());
            com.komainu.moxia.command.ColorCommands.forget(handler.player.getUuid());
        });
        // La vista previa es solo para quien edita y en su dimensión: se descarta al cambiar de mundo o reaparecer.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                PlanPreviews.discard(player, "al cambiar de dimensión"));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                PlanPreviews.discard(newPlayer, "al reaparecer"));
        // Los displays de vista previa solo existen en el cliente; si alguno llegara al mundo, se borra al cargar.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> PreviewDisplays.onEntityLoad(entity));
        // Todo lo que KomaEdit hace en el hilo del servidor sale de un mismo presupuesto por tick: lo ligero primero
        // y lo pesado (leer, colocar, pintar biomas) por turnos entre jugadores.
        ServerWork.light(FastPaintService::tick);
        ServerWork.light(SnapshotCaptureService::tick);
        ServerWork.light(PlanPreviews::tick);
        BatchEditService.register();
        RegionOperationService.register();
        // La reconstrucción animada va a ritmo de reloj, no de cola: su propio carril del mismo presupuesto.
        com.komainu.moxia.rebuild.RebuildService.register();
        com.komainu.moxia.rebuild.PlacementHistory.register();
        ServerWork.register();
        ServerTickEvents.END_SERVER_TICK.register(ProgressBars::tick);
        ServerTickEvents.END_SERVER_TICK.register(server -> PreviewDisplays.sweep());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            AsyncEngine.start();
            // Planes enormes volcados a disco por un servidor que se paró de golpe.
            int leftovers = com.komainu.moxia.edit.PackedBlockPlan.sweepSpills();
            if (leftovers > 0) {
                LOGGER.info("Removed {} leftover KomaEdit plan files", leftovers);
            }
            com.komainu.moxia.rebuild.PlacementHistory.start(server);
            // Las reconstrucciones que una caída dejó a medias se terminan ahora.
            com.komainu.moxia.rebuild.RebuildService.started(server);
        });
        // Antes de que se guarde el mundo: ninguna reconstrucción se queda a medias en disco.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            com.komainu.moxia.rebuild.RebuildService.stopping();
            com.komainu.moxia.rebuild.PlacementHistory.save(true);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BatchEditService.shutdown();
            com.komainu.moxia.rebuild.RebuildService.clearAll();
            com.komainu.moxia.rebuild.PlacementHistory.clearAll();
            FastPaintService.shutdown();
            SnapshotCaptureService.clearAll();
            AsyncEngine.shutdown();
            PathManager.clearAll();
            com.komainu.moxia.edit.PendingBuilds.clearAll();
            BrushManager.clearAll();
            ProjectStorage.clearBindings();
            RegionOperationService.clearAll();
            ServerWork.clearAll();
            com.komainu.moxia.region.SelectionParts.clearAll();
            com.komainu.moxia.persistence.QuietMode.clearAll();
            PlanPreviews.clearAll();
            ProgressBars.clearAll();
            PreviewDisplays.clearOrphans();
            com.komainu.moxia.studio.net.StudioToolsNetwork.clearAll();
            com.komainu.moxia.command.ColorCommands.clearAll();
        });
        LOGGER.info("KomaEdit WorldEdit add-on initialized");
    }
}
