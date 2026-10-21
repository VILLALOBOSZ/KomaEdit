package com.komainu.moxia.sculpt;

import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.performance.ServerWork;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Captura entre ticks la foto del terreno que leen los pinceles y las herramientas antes de calcular. Cada tick
 * reparte por turnos porciones de {@value #MAX_BLOCKS_PER_SLICE} bloques entre los que esperan hasta gastar
 * {@value #MAX_NANOS_PER_TICK} ns: con un solo jugador pintando, la foto de un pincel de radio 8 a 12 (de 5 000 a
 * 20 000 bloques) sale en un tick en vez de en dos o tres, y el clic se nota antes.
 */
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
        submit(player.getUuid(), WorldSnapshot.begin(world, center, horizontalRadius, verticalRadius), success,
                failure);
    }

    static void submit(UUID playerId, WorldSnapshot.Capture capture, Consumer<WorldSnapshot> success,
                       Consumer<Throwable> failure) {
        JOBS.remove(playerId);
        JOBS.put(playerId, new CaptureJob(playerId, capture, success, failure, EditRequests.current()));
    }

    public static boolean cancel(UUID playerId) {
        return JOBS.remove(playerId) != null;
    }

    public static boolean isRunning(UUID playerId) {
        return JOBS.containsKey(playerId);
    }

    public static void tick(MinecraftServer server) {
        tick(playerId -> server.getPlayerManager().getPlayer(playerId) != null);
    }

    /** Un tick: porciones por turnos hasta acabar o gastar el presupuesto del tick. */
    static void tick(Predicate<UUID> online) {
        if (JOBS.isEmpty()) {
            return;
        }
        long globalDeadline = ServerWork.deadline(MAX_NANOS_PER_TICK);
        boolean first = true;
        while (!JOBS.isEmpty() && System.nanoTime() < globalDeadline) {
            java.util.List<CaptureJob> jobs = java.util.List.copyOf(JOBS.values());
            // El turno empieza en otro trabajo cada tick (no en cada vuelta), para que nadie espere siempre.
            int start = Math.floorMod(first ? roundRobinOffset++ : roundRobinOffset - 1, jobs.size());
            first = false;
            for (int offset = 0; offset < jobs.size() && System.nanoTime() < globalDeadline; offset++) {
                CaptureJob job = jobs.get((start + offset) % jobs.size());
                if (!JOBS.containsKey(job.playerId)) {
                    continue;
                }
                if (!online.test(job.playerId)) {
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
