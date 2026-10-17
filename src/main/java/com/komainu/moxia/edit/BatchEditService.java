package com.komainu.moxia.edit;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.persistence.OperationLedger;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BatchEditService {
    private static final int MAX_BLOCKS_PER_SLICE = 32_768;
    private static final long MAX_NANOS_PER_SLICE = 10_000_000L;
    private static final long MAX_NANOS_PER_TICK = 12_000_000L;
    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();
    private static int roundRobinOffset;

    private BatchEditService() {
    }

    public static int submit(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            String patternInput,
            String description
    ) {
        return submit(player, plan, patternInput, description, null);
    }

    public static int submit(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            String patternInput,
            String description,
            JobListener listener
    ) {
        OperationPolicy.validate(player, plan.size(), JOBS.size());
        if (plan.size() == 0) {
            throw new IllegalArgumentException("La operación no contiene bloques");
        }
        UUID playerId = player.getUuid();
        if (JOBS.containsKey(playerId)) {
            throw new IllegalStateException("Ya tienes una edición en curso; usa /komaedit job status");
        }

        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        EditSession editSession = context.session().createEditSession(context.actor());
        JOBS.put(playerId, new Job(
                playerId,
                plan,
                pattern,
                null,
                null,
                null,
                null,
                plan.size(),
                description,
                editSession,
                context,
                listener,
                null
        ));
        return plan.size();
    }

    public static int submitRegion(
            ServerPlayerEntity player,
            Region region,
            PositionPattern pattern,
            String description
    ) {
        long volume = region.getVolume();
        OperationPolicy.validate(player, volume, JOBS.size());
        if (volume <= 0) {
            throw new IllegalArgumentException("La selección no contiene bloques");
        }
        UUID playerId = player.getUuid();
        if (JOBS.containsKey(playerId)) {
            throw new IllegalStateException("Ya tienes una edición en curso; usa /komaedit job status");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        EditSession editSession = context.session().createEditSession(context.actor());
        JOBS.put(playerId, new Job(
                playerId,
                null,
                null,
                region.iterator(),
                pattern,
                null,
                null,
                volume,
                description,
                editSession,
                context,
                null,
                null
        ));
        return (int) Math.min(Integer.MAX_VALUE, volume);
    }

    public static int submitStyled(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            PositionPattern pattern,
            String description
    ) {
        return submitStyled(player, plan, pattern, description, null);
    }

    public static int submitStyled(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            PositionPattern pattern,
            String description,
            JobListener listener
    ) {
        OperationPolicy.validate(player, plan.size(), JOBS.size());
        if (plan.size() == 0) {
            throw new IllegalArgumentException("La operación no contiene bloques");
        }
        UUID playerId = player.getUuid();
        if (JOBS.containsKey(playerId)) {
            throw new IllegalStateException("Ya tienes una edición en curso; usa /komaedit job status");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        EditSession editSession = context.session().createEditSession(context.actor());
        JOBS.put(playerId, new Job(
                playerId,
                plan,
                null,
                null,
                pattern,
                null,
                null,
                plan.size(),
                description,
                editSession,
                context,
                listener,
                null
        ));
        return plan.size();
    }

    public static int submitCapturing(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            String patternInput,
            String description,
            CapturedJobListener listener
    ) {
        requireCapture(plan, player);
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        EditSession editSession = context.session().createEditSession(context.actor());
        JOBS.put(player.getUuid(), new Job(
                player.getUuid(),
                plan,
                pattern,
                null,
                null,
                null,
                null,
                plan.size(),
                description,
                editSession,
                context,
                null,
                listener
        ));
        return plan.size();
    }

    public static int submitStyledCapturing(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            PositionPattern pattern,
            String description,
            CapturedJobListener listener
    ) {
        requireCapture(plan, player);
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        EditSession editSession = context.session().createEditSession(context.actor());
        JOBS.put(player.getUuid(), new Job(
                player.getUuid(),
                plan,
                null,
                null,
                pattern,
                null,
                null,
                plan.size(),
                description,
                editSession,
                context,
                null,
                listener
        ));
        return plan.size();
    }

    private static void requireCapture(PackedBlockPlan plan, ServerPlayerEntity player) {
        OperationPolicy.validate(player, plan.size(), JOBS.size());
        if (plan.size() == 0 || plan.captureSize() == 0) {
            throw new IllegalArgumentException("El plan de instancia no contiene bloques capturables");
        }
        if (JOBS.containsKey(player.getUuid())) {
            throw new IllegalStateException("Ya tienes una edición en curso; usa /komaedit job status");
        }
    }

    public static int submitClipboard(
            ServerPlayerEntity player,
            Clipboard clipboard,
            BlockPos destination,
            String description
    ) {
        long volume = clipboard.getRegion().getVolume();
        OperationPolicy.validate(player, volume, JOBS.size());
        if (volume <= 0) {
            throw new IllegalArgumentException("El portapapeles está vacío");
        }
        UUID playerId = player.getUuid();
        if (JOBS.containsKey(playerId)) {
            throw new IllegalStateException("Ya tienes una edición en curso; usa /komaedit job status");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        EditSession editSession = context.session().createEditSession(context.actor());
        BlockVector3 target = BlockVector3.at(destination.getX(), destination.getY(), destination.getZ());
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 offset = target.subtract(origin);
        JOBS.put(playerId, new Job(
                playerId,
                null,
                null,
                clipboard.getRegion().iterator(),
                null,
                clipboard,
                offset,
                volume,
                description,
                editSession,
                context,
                null,
                null
        ));
        return (int) Math.min(Integer.MAX_VALUE, volume);
    }

    public static Optional<JobStatus> status(UUID playerId) {
        Job job = JOBS.get(playerId);
        if (job == null) {
            return Optional.empty();
        }
        return Optional.of(new JobStatus(
                job.id, job.description, job.processed, job.total, job.editSession.size()
        ));
    }

    public static boolean cancel(UUID playerId, boolean notify) {
        Job job = JOBS.remove(playerId);
        if (job == null) {
            return false;
        }
        finish(job, null, true);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        java.util.List<Job> jobs = java.util.List.copyOf(JOBS.values());
        int start = Math.floorMod(roundRobinOffset++, jobs.size());
        long globalDeadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        for (int offset = 0; offset < jobs.size() && System.nanoTime() < globalDeadline; offset++) {
            Job job = jobs.get((start + offset) % jobs.size());
            if (!JOBS.containsKey(job.playerId)) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.playerId);
            if (player == null) {
                JOBS.remove(job.playerId);
                finish(job, null, true);
                continue;
            }
            processSlice(job, player, globalDeadline);
        }
    }

    public static void shutdown() {
        for (Job job : java.util.List.copyOf(JOBS.values())) {
            finish(job, null, true);
        }
        JOBS.clear();
    }

    private static void processSlice(Job job, ServerPlayerEntity player, long globalDeadline) {
        long deadline = Math.min(globalDeadline, System.nanoTime() + MAX_NANOS_PER_SLICE);
        int processed = 0;
        try {
            while (job.hasNext()
                    && processed < MAX_BLOCKS_PER_SLICE
                    && System.nanoTime() < deadline) {
                if (job.plan != null) {
                    int planIndex = (int) job.processed;
                    BlockVector3 position = job.plan.position(planIndex);
                    BlockState override = job.plan.override(planIndex);
                    BlockPos minecraftPosition = new BlockPos(position.x(), position.y(), position.z());
                    int expectedStateId = job.plan.expectedStateId(planIndex);
                    boolean allowed = expectedStateId == PackedBlockPlan.NO_EXPECTED_STATE
                            || Block.getRawIdFromState(player.getServerWorld().getBlockState(minecraftPosition))
                            == expectedStateId;
                    if (!allowed) {
                        job.protectedBlocks++;
                    } else {
                        boolean placed = true;
                        if (override == null && job.positionPattern != null) {
                            Pattern resolved = job.positionPattern.pattern(position);
                            if (resolved != null) {
                                placed = place(job, position, resolved, job.plan.piece(planIndex));
                            }
                        } else if (override == null) {
                            placed = place(job, position, job.defaultPattern, job.plan.piece(planIndex));
                        } else {
                            job.editSession.setBlock(position, override);
                        }
                        int captureIndex = job.plan.captureIndex(planIndex);
                        if (placed && captureIndex != PackedBlockPlan.NO_CAPTURE) {
                            job.capturedStateIds[captureIndex] = Block.getRawIdFromState(
                                    player.getServerWorld().getBlockState(minecraftPosition)
                            );
                        }
                    }
                } else if (job.clipboard != null) {
                    BlockVector3 source = job.regionIterator.next();
                    BlockVector3 destination = source.add(
                            job.clipboardOffset.x(),
                            job.clipboardOffset.y(),
                            job.clipboardOffset.z()
                    );
                    job.editSession.setBlock(destination, job.clipboard.getFullBlock(source));
                } else {
                    BlockVector3 position = job.regionIterator.next();
                    Pattern pattern = job.positionPattern.pattern(position);
                    if (pattern != null) {
                        job.editSession.setBlock(position, pattern);
                    }
                }
                job.processed++;
                processed++;
            }
            if (!job.hasNext()) {
                JOBS.remove(job.playerId);
                finish(job, player, false);
            } else {
                job.persistProgress();
            }
        } catch (MaxChangedBlocksException exception) {
            JOBS.remove(job.playerId);
            finish(job, player, true);
            player.sendMessage(message("Se alcanzó el límite de WorldEdit; el cambio parcial puede deshacerse"), false);
        } catch (RuntimeException exception) {
            JOBS.remove(job.playerId);
            finish(job, player, true);
            KomaEditMod.LOGGER.error("Batch edit failed for {}", job.playerId, exception);
            player.sendMessage(message("La edición falló: " + readableMessage(exception)), false);
        }
    }

    /**
     * Coloca el bloque del patrón o, si el plan pide una losa o escalera, la pieza de ese mismo material. Si el
     * material no tiene piezas, el bloque de la figura se queda entero y las piezas añadidas fuera se omiten.
     *
     * @return {@code false} si la posición se dejó sin tocar
     */
    private static boolean place(Job job, BlockVector3 position, Pattern pattern, byte piece)
            throws MaxChangedBlocksException {
        if (piece == SmoothPiece.FULL) {
            job.editSession.setBlock(position, pattern);
            return true;
        }
        BaseBlock base = pattern.applyBlock(position);
        BlockState shaped = SmoothBlockStates.shape(base, piece);
        if (shaped != null) {
            job.editSession.setBlock(position, shaped);
            return true;
        }
        if (SmoothPiece.isAdded(piece)) {
            return false;
        }
        job.editSession.setBlock(position, base);
        return true;
    }

    private static void finish(Job job, ServerPlayerEntity player, boolean cancelled) {
        try {
            job.editSession.close();
        } finally {
            if (job.editSession.size() > 0) {
                job.context.session().remember(job.editSession);
            }
        }
        if (player != null) {
            String state = cancelled ? "cancelada" : "completada";
            player.sendMessage(message(
                    job.description + " " + state + ": " + job.editSession.size() + " bloques · //undo"
            ), false);
            if (job.protectedBlocks > 0) {
                player.sendMessage(message(
                        job.protectedBlocks + " cambios manuales protegidos y conservados"
                ), false);
            }
        }
        OperationLedger.finish(
                job.playerId,
                job.id,
                job.description,
                job.processed,
                job.total,
                job.editSession.size(),
                job.protectedBlocks,
                cancelled
        );
        if (job.listener != null) {
            try {
                job.listener.finished(cancelled, job.editSession.size());
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.error("Batch completion listener failed for {}", job.playerId, exception);
            }
        }
        if (job.capturedListener != null) {
            try {
                job.capturedListener.finished(
                        cancelled,
                        job.editSession.size(),
                        Arrays.copyOf(job.capturedStateIds, job.capturedStateIds.length),
                        job.protectedBlocks
                );
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.error("Batch capture listener failed for {}", job.playerId, exception);
            }
        }
    }

    private static Text message(String value) {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD)
                .append(Text.literal(value).formatted(Formatting.GRAY));
    }

    private static String readableMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    @FunctionalInterface
    public interface PositionPattern {
        Pattern pattern(BlockVector3 position);
    }

    @FunctionalInterface
    public interface JobListener {
        void finished(boolean cancelled, int changedBlocks);
    }

    @FunctionalInterface
    public interface CapturedJobListener {
        void finished(boolean cancelled, int changedBlocks, int[] capturedStateIds, int protectedBlocks);
    }

    public record JobStatus(String id, String description, long processed, long total, int changed) {
        public int percentage() {
            return total == 0 ? 100 : (int) Math.min(100L, processed * 100L / total);
        }
    }

    private static final class Job {
        private static final long JOURNAL_INTERVAL_NANOS = 1_000_000_000L;
        private final String id;
        private final UUID playerId;
        private final PackedBlockPlan plan;
        private final Pattern defaultPattern;
        private final java.util.Iterator<BlockVector3> regionIterator;
        private final PositionPattern positionPattern;
        private final Clipboard clipboard;
        private final BlockVector3 clipboardOffset;
        private final long total;
        private final String description;
        private final EditSession editSession;
        private final WorldEditService.PlayerContext context;
        private final JobListener listener;
        private final CapturedJobListener capturedListener;
        private final int[] capturedStateIds;
        private long processed;
        private int protectedBlocks;
        private long lastJournalAt;
        private long lastJournalProcessed;

        private Job(
                UUID playerId,
                PackedBlockPlan plan,
                Pattern defaultPattern,
                java.util.Iterator<BlockVector3> regionIterator,
                PositionPattern positionPattern,
                Clipboard clipboard,
                BlockVector3 clipboardOffset,
                long total,
                String description,
                EditSession editSession,
                WorldEditService.PlayerContext context,
                JobListener listener,
                CapturedJobListener capturedListener
        ) {
            this.id = OperationLedger.start(playerId, description, total);
            this.playerId = playerId;
            this.plan = plan;
            this.defaultPattern = defaultPattern;
            this.regionIterator = regionIterator;
            this.positionPattern = positionPattern;
            this.clipboard = clipboard;
            this.clipboardOffset = clipboardOffset;
            this.total = total;
            this.description = description;
            this.editSession = editSession;
            this.context = context;
            this.listener = listener;
            this.capturedListener = capturedListener;
            this.capturedStateIds = plan == null || plan.captureSize() == 0
                    ? new int[0]
                    : new int[plan.captureSize()];
            Arrays.fill(this.capturedStateIds, PackedBlockPlan.NO_EXPECTED_STATE);
        }

        private void persistProgress() {
            long now = System.nanoTime();
            if (processed == lastJournalProcessed || now - lastJournalAt < JOURNAL_INTERVAL_NANOS) {
                return;
            }
            lastJournalAt = now;
            lastJournalProcessed = processed;
            OperationLedger.progress(playerId, id, description, processed, total, editSession.size());
        }

        private boolean hasNext() {
            return plan != null ? processed < plan.size() : regionIterator.hasNext();
        }
    }
}
