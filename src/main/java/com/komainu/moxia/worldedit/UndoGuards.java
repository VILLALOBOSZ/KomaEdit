package com.komainu.moxia.worldedit;

import com.komainu.moxia.biome.BiomeEditService;
import com.komainu.moxia.brush.paint.PaintStrokes;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.editor.FastPaintService;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.rebuild.RebuildService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Lo que hace esperar a {@code //undo} y {@code //redo} en el juego ({@link UndoGuard}): la cola de edición, la
 * reconstrucción y los biomas, que solo entran en el historial al acabar; los trazos de pintar se cierran antes.
 */
public final class UndoGuards {
    /** Después de la fase de serie, que es donde WorldEdit registra sus órdenes. */
    private static final Identifier AFTER_WORLDEDIT = Identifier.of("komaedit", "after_worldedit");

    private UndoGuards() {
    }

    public static void register() {
        UndoGuard guard = UndoGuard.GAME;
        guard.blockWhile(id -> BatchEditService.status(id).map(BatchEditService.JobStatus::description).orElse(null));
        guard.blockWhile(RebuildService::placing);
        guard.blockWhile(BiomeEditService::placing);
        guard.closeFirst(PaintStrokes::close);
        guard.closeFirst(FastPaintService::remove);
        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_WORLDEDIT);
        CommandRegistrationCallback.EVENT.register(AFTER_WORLDEDIT, (dispatcher, registryAccess, environment) ->
                UndoGuard.install(dispatcher.getRoot(), UndoGuards::player, UndoGuards::refuse, guard));
    }

    private static UUID player(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        return player == null ? null : player.getUuid();
    }

    private static void refuse(ServerCommandSource source, String reason) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            Feedback.error(player, reason);
        }
    }
}
