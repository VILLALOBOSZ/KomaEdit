package com.komainu.moxia.biome;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.SnapshotJobs;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pinta biomas por lotes en el hilo del servidor con una {@link EditSession} de WorldEdit: cada celda es un
 * {@code setBiome}, el trabajo se reparte entre ticks y todo queda en una sola entrada de //undo. Al cerrar la
 * sesión, WorldEdit reenvía los biomas de los chunks tocados a los jugadores (también al deshacer), así que se ve
 * el cambio sin volver a entrar.
 */
public final class BiomeEditService {
    private static final long NANOS_PER_TICK = 4_000_000L;
    private static final int CELLS_PER_CHECK = 256;
    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();

    private BiomeEditService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BiomeEditService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> finish(handler.player.getUuid(), true));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (UUID playerId : List.copyOf(JOBS.keySet())) {
                finish(playerId, true);
            }
        });
    }

    public static boolean running(UUID playerId) {
        return JOBS.containsKey(playerId);
    }

    /** @return celdas en cola */
    public static int submit(ServerPlayerEntity player, long[] cells, BiomeType biome, String description) {
        if (cells.length == 0) {
            throw new IllegalArgumentException("La zona no toca ninguna celda de bioma");
        }
        // Cada celda es una sola escritura: el límite de bloques por operación cuenta celdas.
        OperationPolicy.validate(player, cells.length, 0);
        if (JOBS.containsKey(player.getUuid())) {
            throw new IllegalStateException("Ya estás pintando un bioma; espera a que termine");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        EditSession session = context.session().createEditSession(context.actor());
        JOBS.put(player.getUuid(), new Job(player.getUuid(), cells, biome, description, session, context));
        return cells.length;
    }

    public static boolean cancel(UUID playerId) {
        return finish(playerId, true);
    }

    /** Pintado en curso del jugador, para la barra de progreso. */
    public static Optional<ProgressLine> progress(UUID playerId) {
        Job job = JOBS.get(playerId);
        if (job == null) {
            return Optional.empty();
        }
        return Optional.of(new ProgressLine(job.description, ProgressLine.Phase.PLACING, job.index, job.cells.length,
                System.nanoTime() - job.startedAt, "/komaedit cancel"));
    }

    private static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + NANOS_PER_TICK;
        for (Job job : new ArrayList<>(JOBS.values())) {
            if (System.nanoTime() >= deadline) {
                return;
            }
            try {
                while (job.index < job.cells.length && System.nanoTime() < deadline) {
                    int end = Math.min(job.cells.length, job.index + CELLS_PER_CHECK);
                    for (; job.index < end; job.index++) {
                        long packed = job.cells[job.index];
                        job.session.setBiome(BlockVector3.at(PackedBlockPlan.unpackX(packed),
                                PackedBlockPlan.unpackY(packed), PackedBlockPlan.unpackZ(packed)), job.biome);
                    }
                }
                if (job.index >= job.cells.length) {
                    finish(job.playerId, false);
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.playerId);
                    if (player != null) {
                        player.sendMessage(SnapshotJobs.message(job.description + ": " + job.cells.length
                                + " celdas de 4×4×4 · //undo para deshacer", Formatting.GREEN), false);
                    }
                }
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.error("Biome edit failed for {}", job.playerId, exception);
                finish(job.playerId, true);
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.playerId);
                if (player != null) {
                    player.sendMessage(SnapshotJobs.message(job.description + ": " + SnapshotJobs.readable(exception)
                            + " · lo pintado se puede deshacer con //undo", Formatting.RED), false);
                }
            }
        }
    }

    /** Cierra la sesión (WorldEdit reenvía los biomas) y la guarda en el historial aunque esté a medias. */
    private static boolean finish(UUID playerId, boolean cancelled) {
        Job job = JOBS.remove(playerId);
        if (job == null) {
            return false;
        }
        try {
            job.session.close();
        } finally {
            if (job.session.size() > 0) {
                job.context.session().remember(job.session);
            }
        }
        if (cancelled) {
            KomaEditMod.LOGGER.info("Biome edit for {} stopped after {}/{} cells", playerId, job.index,
                    job.cells.length);
        }
        return true;
    }

    private static final class Job {
        private final UUID playerId;
        private final long[] cells;
        private final BiomeType biome;
        private final String description;
        private final EditSession session;
        private final WorldEditService.PlayerContext context;
        private final long startedAt = System.nanoTime();
        private int index;

        private Job(UUID playerId, long[] cells, BiomeType biome, String description, EditSession session,
                    WorldEditService.PlayerContext context) {
            this.playerId = playerId;
            this.cells = cells;
            this.biome = biome;
            this.description = description;
            this.session = session;
            this.context = context;
        }
    }
}
