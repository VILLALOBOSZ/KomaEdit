package com.komainu.moxia.terrain;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.PendingBuilds;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.RegionSnapshot;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * Lanza las herramientas de terreno sobre el motor común: lee la zona repartiendo la lectura entre ticks
 * ({@link RegionOperationService#capture}), calcula en un worker de {@link AsyncEngine}, enseña la estimación del
 * motor ({@link BuildEstimate}, con la confirmación de {@link PendingBuilds} si es grande) y escribe por lotes con
 * {@link BatchEditService}: una sola entrada de //undo y solo donde el bloque no haya cambiado mientras tanto. Con
 * {@code -v} (o «previsualizar siempre») el plan pasa por la vista previa del motor común antes de colocarse.
 */
public final class TerrainService {
    private static volatile StampCatalog catalog;

    private TerrainService() {
    }

    /** El trabajo en el worker: lee la vista y escribe en el destino. */
    @FunctionalInterface
    public interface Job {
        TerrainJobs.Summary run(TerrainView view, TerrainWriter.BlockSink sink, Runnable checkCancelled);
    }

    /** Corta un cálculo largo si el jugador cancela, se desconecta o lanza otro. */
    public static final Runnable CANCELLATION = () -> {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    };

    /** Los sellos de {@code config/komaedit/heightmaps}; la primera vez escribe allí los de serie que falten. */
    public static StampCatalog catalog() {
        StampCatalog current = catalog;
        if (current == null) {
            synchronized (TerrainService.class) {
                current = catalog;
                if (current == null) {
                    current = new StampCatalog(KomaEditConfig.directory().resolve("heightmaps"));
                    try {
                        int written = current.writeDefaults();
                        if (written > 0) {
                            KomaEditMod.LOGGER.info("KomaEdit: {} sellos de relieve de serie en {}", written,
                                    current.directory());
                        }
                    } catch (IOException exception) {
                        KomaEditMod.LOGGER.warn("KomaEdit: no se pudieron escribir los sellos de serie en {}: {}",
                                current.directory(), exception.getMessage());
                    }
                    catalog = current;
                }
            }
        }
        return current;
    }

    /**
     * @param region       zona a leer; sus posiciones son las que se pueden escribir
     * @param maskText     máscara {@code -m} de WorldEdit, o {@code null}
     * @param estimateOnly solo informa de lo que costaría
     */
    public static void submit(ServerPlayerEntity player, Region region, int padding, String description,
                              String maskText, TerrainBlocks blocks, boolean estimateOnly, Job job) {
        RegionMasks.Bound mask = maskText == null || maskText.isBlank() ? null : RegionMasks.parse(player, maskText);
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUuid();
        ServerWorld world = player.getServerWorld();
        Region frozen = region.clone();
        // Si al confirmar una vista previa la zona ha cambiado, se vuelve a leer y calcular entero.
        Runnable recompute = () -> {
            ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
            if (current != null) {
                submit(current, frozen, padding, description, maskText, blocks, false, job);
            }
        };
        RegionOperationService.capture(player, frozen, padding, description,
                snapshot -> compute(server, playerId, world, description, snapshot, mask, blocks, estimateOnly, job,
                        recompute));
    }

    private record Outcome(RegionEdits edits, TerrainJobs.Summary summary) {
    }

    private static void compute(MinecraftServer server, UUID playerId, ServerWorld world, String description,
                                RegionSnapshot snapshot, RegionMasks.Bound mask, TerrainBlocks blocks,
                                boolean estimateOnly, Job job, Runnable recompute) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }
        QuietMode.status(player, message(description + ": calculando fuera del hilo del servidor…", Formatting.GRAY));
        AsyncEngine.submit(
                playerId,
                description,
                () -> {
                    if (mask != null) {
                        mask.bind(snapshot);
                    }
                    RegionEdits edits = new RegionEdits(snapshot, RegionOperationService.MAX_CHANGES);
                    TerrainView view = new RegionTerrainView(snapshot, mask);
                    TerrainJobs.Summary summary = job.run(view, blocks.region(edits, snapshot), CANCELLATION);
                    return new Outcome(edits, summary);
                },
                outcome -> server.execute(() -> finish(server, playerId, world, description, estimateOnly, outcome,
                        recompute)),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null && !(throwable instanceof CancellationException)) {
                        current.sendMessage(message(description + ": " + readable(throwable), Formatting.RED), false);
                    }
                })
        );
    }

    private static void finish(MinecraftServer server, UUID playerId, ServerWorld world, String description,
                               boolean estimateOnly, Outcome outcome, Runnable recompute) {
        ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
        if (current == null) {
            return;
        }
        if (current.getServerWorld() != world) {
            current.sendMessage(message(description + ": " + RegionOperationService.OTHER_WORLD, Formatting.YELLOW),
                    false);
            return;
        }
        String detail = outcome.summary().detail();
        if (outcome.edits().changes() == 0) {
            current.sendMessage(message(description + ": no había nada que cambiar"
                    + (detail.isBlank() ? "" : " (" + detail + ")"), Formatting.YELLOW), false);
            return;
        }
        PackedBlockPlan plan = outcome.edits().plan();
        BuildEstimate estimate = BuildEstimate.of(plan);
        if (estimateOnly) {
            current.sendMessage(message("Estimación de " + description + ": " + estimate.summary()
                    + verdict(current, estimate) + " · " + outcome.summary().columns() + " columnas"
                    + (detail.isBlank() ? "" : " · " + detail), Formatting.AQUA), false);
            return;
        }
        Runnable submit = () -> {
            try {
                // Con -v o «previsualizar siempre», BatchEditService deja el plan en vista previa en lugar de
                // encolarlo; las vigilancias de la zona son las de las posiciones que cambian.
                int queued = PlanPreviews.withExtras(new PlanPreviews.Extras(recompute, null),
                        () -> BatchEditService.submit(current, plan, "air", description));
                if (!PlanPreviews.holds(playerId, plan)) {
                    QuietMode.status(current, message(description + ": " + ProgressLine.group(queued) + " bloques en cola · "
                            + outcome.summary().columns() + " columnas" + (detail.isBlank() ? "" : " · " + detail)
                            + " · //undo para deshacer", Formatting.GREEN));
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                current.sendMessage(message(description + ": " + readable(exception), Formatting.RED), false);
            }
        };
        PendingBuilds.submitOrHold(current, description, estimate, submit, true);
    }

    /** Lo mismo que avisa {@code /komaedit estimate}: si pasa del límite o si pedirá confirmación. */
    static String verdict(ServerPlayerEntity player, BuildEstimate estimate) {
        int threshold = KomaEditConfig.settings().confirmBuildAboveBlocks();
        int limit = KomaEditConfig.settings().maxBlocksPerOperation();
        if (estimate.blocks() > limit && !KomaPermissions.can(player, KomaPermissions.LIMIT_UNRESTRICTED)) {
            return " · supera tu límite de " + ProgressLine.group(limit) + " bloques";
        }
        return BuildEstimate.needsConfirmation(estimate.blocks(), threshold) ? " · pedirá confirmación" : "";
    }

    static Text message(String text, Formatting color) {
        return Feedback.prefix().append(Text.literal(text).formatted(color));
    }

    static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
