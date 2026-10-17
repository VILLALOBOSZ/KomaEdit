package com.komainu.moxia.region;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/**
 * Motor común de las operaciones sobre la selección de WorldEdit (texturizar, deformar, suavizar, retocar…).
 *
 * <ol>
 *     <li>Lee la selección y un margen alrededor repartiendo el trabajo entre ticks, sin congelar el servidor.</li>
 *     <li>Calcula la operación en un worker de {@link AsyncEngine} sobre una foto inmutable.</li>
 *     <li>Escribe con {@link BatchEditService} por lotes, solo donde el bloque no haya cambiado, y deja el
 *     historial de WorldEdit para //undo.</li>
 * </ol>
 */
public final class RegionOperationService {
    public static final long MAX_CAPTURE_BLOCKS = 16_000_000L;
    public static final int MAX_CHANGES = 1_500_000;
    private static final int BLOCKS_PER_SLICE = 32_768;
    private static final long NANOS_PER_TICK = 4_000_000L;
    private static final Map<UUID, CaptureJob> JOBS = new LinkedHashMap<>();

    private RegionOperationService() {
    }

    /**
     * Lanza una operación sobre la selección actual del jugador.
     *
     * @param padding bloques extra capturados alrededor de la selección para que la operación vea a los vecinos
     */
    public static void submit(
            ServerPlayerEntity player,
            int padding,
            String description,
            RegionOperation operation
    ) throws IncompleteRegionException {
        start(player, WorldEditService.selection(player).clone(), padding, description, operation, null);
    }

    /**
     * Lee una región cualquiera (no tiene por qué ser la selección) y entrega la foto en el hilo del servidor, sin
     * calcular nada: para operaciones que montan su propio plan, como la dispersión de estructuras, que escribe
     * también fuera de la región.
     *
     * @param region     las posiciones de la región quedan marcadas como seleccionadas en la foto
     * @param onCaptured recibe la foto terminada en el hilo del servidor
     */
    public static void capture(
            ServerPlayerEntity player,
            Region region,
            int padding,
            String description,
            Consumer<RegionSnapshot> onCaptured
    ) {
        start(player, region.clone(), padding, description, null, onCaptured);
    }

    private static void start(
            ServerPlayerEntity player,
            Region selection,
            int padding,
            String description,
            RegionOperation operation,
            Consumer<RegionSnapshot> onCaptured
    ) {
        ServerWorld world = player.getServerWorld();
        BlockVector3 minimum = selection.getMinimumPoint();
        BlockVector3 maximum = selection.getMaximumPoint();
        int margin = Math.clamp(padding, 0, 32);
        int minimumY = Math.max(world.getBottomY(), minimum.y() - margin);
        int maximumY = Math.min(world.getTopY() - 1, maximum.y() + margin);
        int minimumX = minimum.x() - margin;
        int minimumZ = minimum.z() - margin;
        int sizeX = maximum.x() + margin - minimumX + 1;
        int sizeY = maximumY - minimumY + 1;
        int sizeZ = maximum.z() + margin - minimumZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (sizeY <= 0) {
            throw new IllegalArgumentException("La selección está fuera de la altura del mundo");
        }
        if (volume > MAX_CAPTURE_BLOCKS) {
            throw new IllegalArgumentException("La selección es demasiado grande para esta operación ("
                    + volume + " bloques con su margen; máximo " + MAX_CAPTURE_BLOCKS + ")");
        }
        OperationPolicy.validate(player, selection.getVolume(), 0);
        UUID playerId = player.getUuid();
        if (JOBS.containsKey(playerId)) {
            throw new IllegalStateException("Ya hay una operación leyendo tu selección; espera a que termine");
        }
        JOBS.put(playerId, new CaptureJob(playerId, world, selection, description, operation, onCaptured,
                RegionSnapshot.builder(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ), sizeX, sizeY, sizeZ,
                minimumX, minimumY, minimumZ));
        player.sendMessage(message(description + ": leyendo " + volume + " bloques…", Formatting.GRAY), false);
    }

    public static boolean cancel(UUID playerId) {
        return JOBS.remove(playerId) != null;
    }

    public static void clearAll() {
        JOBS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + NANOS_PER_TICK;
        List<CaptureJob> jobs = new ArrayList<>(JOBS.values());
        for (CaptureJob job : jobs) {
            if (System.nanoTime() >= deadline) {
                return;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.playerId);
            if (player == null) {
                JOBS.remove(job.playerId);
                continue;
            }
            try {
                if (job.step(deadline)) {
                    JOBS.remove(job.playerId);
                    if (job.onCaptured != null) {
                        job.onCaptured.accept(job.builder.build());
                    } else {
                        compute(server, player, job.description, job.operation, job.builder.build());
                    }
                }
            } catch (RuntimeException exception) {
                JOBS.remove(job.playerId);
                player.sendMessage(message(job.description + ": " + readable(exception), Formatting.RED), false);
            }
        }
    }

    private static void compute(
            MinecraftServer server,
            ServerPlayerEntity player,
            String description,
            RegionOperation operation,
            RegionSnapshot snapshot
    ) {
        UUID playerId = player.getUuid();
        player.sendMessage(message(description + ": calculando fuera del hilo del servidor…", Formatting.GRAY), false);
        AsyncEngine.submit(
                playerId,
                () -> {
                    RegionEdits edits = new RegionEdits(snapshot, MAX_CHANGES);
                    operation.apply(snapshot, edits);
                    return edits;
                },
                edits -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    if (edits.changes() == 0) {
                        current.sendMessage(message(description + ": no había nada que cambiar", Formatting.YELLOW),
                                false);
                        return;
                    }
                    try {
                        int queued = BatchEditService.submit(current, edits.plan(), "air", description);
                        current.sendMessage(message(description + ": " + queued
                                + " bloques en cola · //undo para deshacer", Formatting.GREEN), false);
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        current.sendMessage(message(description + ": " + readable(exception), Formatting.RED), false);
                    }
                }),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null && !(throwable instanceof CancellationException)) {
                        current.sendMessage(message(description + ": " + readable(throwable), Formatting.RED), false);
                    }
                })
        );
    }

    private static Text message(String text, Formatting color) {
        return Text.literal("KomaEdit · ").formatted(Formatting.DARK_AQUA)
                .append(Text.literal(text).formatted(color));
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** Lectura por tramos de la región; cada tick avanza hasta agotar su presupuesto de tiempo. */
    private static final class CaptureJob {
        private final UUID playerId;
        private final ServerWorld world;
        private final Region selection;
        private final String description;
        private final RegionOperation operation;
        private final Consumer<RegionSnapshot> onCaptured;
        private final RegionSnapshot.Builder builder;
        private final int sizeX;
        private final int sizeZ;
        private final int minimumX;
        private final int minimumY;
        private final int minimumZ;
        private final long total;
        private final BlockPos.Mutable position = new BlockPos.Mutable();
        private long index;

        private CaptureJob(
                UUID playerId,
                ServerWorld world,
                Region selection,
                String description,
                RegionOperation operation,
                Consumer<RegionSnapshot> onCaptured,
                RegionSnapshot.Builder builder,
                int sizeX,
                int sizeY,
                int sizeZ,
                int minimumX,
                int minimumY,
                int minimumZ
        ) {
            this.playerId = playerId;
            this.world = world;
            this.selection = selection;
            this.description = description;
            this.operation = operation;
            this.onCaptured = onCaptured;
            this.builder = builder;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.minimumX = minimumX;
            this.minimumY = minimumY;
            this.minimumZ = minimumZ;
            this.total = (long) sizeX * sizeY * sizeZ;
        }

        /** @return {@code true} cuando la lectura ha terminado */
        boolean step(long deadline) {
            int processed = 0;
            long layer = (long) sizeX * sizeZ;
            while (index < total && processed < BLOCKS_PER_SLICE) {
                if ((processed & 1_023) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
                int localY = (int) (index / layer);
                long remainder = index - localY * layer;
                int localZ = (int) (remainder / sizeX);
                int localX = (int) (remainder - (long) localZ * sizeX);
                int x = minimumX + localX;
                int y = minimumY + localY;
                int z = minimumZ + localZ;
                position.set(x, y, z);
                BlockState state = world.getBlockState(position);
                int rawId = Block.getRawIdFromState(state);
                if (!builder.knows(rawId)) {
                    builder.state(describe(rawId, state));
                }
                builder.set(x, y, z, rawId, selection.contains(BlockVector3.at(x, y, z)));
                index++;
                processed++;
            }
            return index >= total;
        }

        private RegionSnapshot.StateInfo describe(int rawId, BlockState state) {
            com.sk89q.worldedit.world.block.BlockState adapted = FabricAdapter.adapt(state);
            String text = adapted.getAsString();
            int bracket = text.indexOf('[');
            String properties = bracket < 0 ? "" : text.substring(bracket + 1, text.length() - 1);
            return new RegionSnapshot.StateInfo(
                    rawId,
                    adapted,
                    Registries.BLOCK.getId(state.getBlock()).toString(),
                    state.isAir(),
                    !state.getFluidState().isEmpty(),
                    state.isSolidBlock(world, position),
                    properties
            );
        }
    }
}
