package com.komainu.moxia.sculpt;

import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class SnapshotCaptureService {
    private static final int MAX_BLOCKS_PER_SLICE = 16_384;
    private static final long MAX_NANOS_PER_SLICE = 1_500_000L;
    private static final long MAX_NANOS_PER_TICK = 5_000_000L;
    private static final Map<UUID, CaptureJob> JOBS = new LinkedHashMap<>();
    private static int roundRobinOffset;

    private SnapshotCaptureService() {
    }

    public static void submit(
            ServerPlayerEntity player,
            World world,
            BlockVector3 center,
            int horizontalRadius,
            int verticalRadius,
            Consumer<WorldSnapshot> success,
            Consumer<Throwable> failure
    ) {
        UUID playerId = player.getUuid();
        JOBS.remove(playerId);
        JOBS.put(playerId, new CaptureJob(
                playerId,
                WorldSnapshot.begin(world, center, horizontalRadius, verticalRadius),
                success,
                failure,
                EditRequests.current()
        ));
    }

    public static boolean cancel(UUID playerId) {
        return JOBS.remove(playerId) != null;
    }

    public static boolean isRunning(UUID playerId) {
        return JOBS.containsKey(playerId);
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        java.util.List<CaptureJob> jobs = java.util.List.copyOf(JOBS.values());
        int start = Math.floorMod(roundRobinOffset++, jobs.size());
        long globalDeadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        for (int offset = 0; offset < jobs.size() && System.nanoTime() < globalDeadline; offset++) {
            CaptureJob job = jobs.get((start + offset) % jobs.size());
            if (!JOBS.containsKey(job.playerId)) {
                continue;
            }
            if (server.getPlayerManager().getPlayer(job.playerId) == null) {
                JOBS.remove(job.playerId);
                continue;
            }
            long deadline = Math.min(globalDeadline, System.nanoTime() + MAX_NANOS_PER_SLICE);
            try {
                job.capture.step(MAX_BLOCKS_PER_SLICE, deadline);
                if (job.capture.complete()) {
                    JOBS.remove(job.playerId);
                    // La petición de la orden (vista previa…) sigue activa para el cálculo que salga de la foto.
                    EditRequests.within(job.request, () -> job.success.accept(job.capture.finish()));
                }
            } catch (RuntimeException exception) {
                JOBS.remove(job.playerId);
                job.failure.accept(exception);
            }
        }
    }

    public static void clearAll() {
        JOBS.clear();
    }

    private record CaptureJob(
            UUID playerId,
            WorldSnapshot.Capture capture,
            Consumer<WorldSnapshot> success,
            Consumer<Throwable> failure,
            EditRequest request
    ) {
    }
}
