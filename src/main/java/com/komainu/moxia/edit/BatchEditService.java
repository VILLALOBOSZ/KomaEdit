package com.komainu.moxia.edit;

import com.komainu.moxia.worldedit.EditHistory;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.feedback.FeedbackEventPayload;
import com.komainu.moxia.performance.JobQueue;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.performance.RateMeter;
import com.komainu.moxia.performance.ServerWork;
import com.komainu.moxia.persistence.OperationLedger;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.RunContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La cola de colocación de KomaEdit. Cada edición entra con su {@link EditSession} (un solo //undo aunque dure
 * cientos de ticks) y:
 *
 * <ul>
 *     <li>espera su turno: como mucho {@code maxConcurrentJobs} colocan a la vez, el resto espera por orden de llegada
 *     ({@link JobQueue}) y la barra dice el puesto y cuándo empezará;</li>
 *     <li>las que colocan se reparten el tiempo de cada tick por turnos con el resto del trabajo pesado
 *     ({@link ServerWork}): una grande no bloquea a las pequeñas de otros;</li>
 *     <li>escribe por ventanas ({@link PlacementRun}): cada ventana pasa de verdad al mundo con su luz, vecinos y envío
 *     a los clientes dentro del presupuesto, en vez de acumularse en el búfer de WorldEdit hasta el final.</li>
 * </ul>
 */
public final class BatchEditService {
    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();
    private static final JobQueue<UUID> QUEUE = new JobQueue<>();
    private static final RateMeter RATE = new RateMeter();

    private BatchEditService() {
    }

    /** Se apunta como trabajo pesado de {@link ServerWork}. */
    public static void register() {
        ServerWork.lane(new ServerWork.Lane() {
            @Override
            public void owners(Collection<UUID> into) {
                if (QUEUE.isEmpty()) {
                    RATE.idle();
                    return;
                }
                for (UUID admitted : QUEUE.admit(KomaEditConfig.settings().maxConcurrentJobs(),
                        ServerWork::paused)) {
                    Job job = JOBS.get(admitted);
                    if (job != null) {
                        job.placingSince = System.nanoTime();
                    }
                }
                for (UUID owner : QUEUE.activeOwners()) {
                    Job job = JOBS.get(owner);
                    if (job != null && (!ServerWork.paused(owner) || job.run.flushing())) {
                        into.add(owner);
                    }
                }
            }

            @Override
            public boolean step(MinecraftServer server, UUID owner, long deadline) {
                Job job = JOBS.get(owner);
                if (job == null || !QUEUE.active(owner)) {
                    return false;
                }
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
                if (player == null) {
                    remove(job);
                    finish(job, null, Outcome.CANCELLED, null);
                    return false;
                }
                return processSlice(job, player, deadline);
            }
        });
    }

    public static int submit(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            String patternInput,
            String description
    ) {
        return submit(player, plan, patternInput, description, null);
    }

    /**
     * Encola un plan con un patrón. Si la orden pidió vista previa ({@code -v}) o el jugador previsualiza siempre a
     * partir de N bloques, el plan queda en {@link PlanPreviews} hasta {@code /komaedit confirm} y no se coloca nada
     * aún (con {@code listener} nunca hay vista previa). Devuelve los bloques del plan en los dos casos.
     */
    public static int submit(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            String patternInput,
            String description,
            JobListener listener
    ) {
        OperationPolicy.validate(player, plan.size(), 0);
        if (plan.size() == 0) {
            throw new IllegalArgumentException("La operación no contiene bloques");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        if (listener == null && PlanPreviews.intercept(player, plan, blocks(plan, pattern, null), description,
                () -> submit(player, plan, patternInput, description))) {
            return plan.size();
        }
        requireFree(player, plan.size(), context);
        EditSession editSession = context.session().createEditSession(context.actor());
        enqueue(player, new Job(player, plan, pattern, null, null, null, null, plan.size(), description,
                editSession, context, listener, null));
        return plan.size();
    }

    public static int submitRegion(
            ServerPlayerEntity player,
            Region region,
            PositionPattern pattern,
            String description
    ) {
        long volume = region.getVolume();
        OperationPolicy.validate(player, volume, 0);
        if (volume <= 0) {
            throw new IllegalArgumentException("La selección no contiene bloques");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        requireFree(player, volume, context);
        EditSession editSession = context.session().createEditSession(context.actor());
        enqueue(player, new Job(player, null, null, region.iterator(), pattern, null, null, volume, description,
                editSession, context, null, null));
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
        OperationPolicy.validate(player, plan.size(), 0);
        if (plan.size() == 0) {
            throw new IllegalArgumentException("La operación no contiene bloques");
        }
        if (listener == null && PlanPreviews.intercept(player, plan, blocks(plan, null, pattern), description,
                () -> submitStyled(player, plan, pattern, description))) {
            return plan.size();
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        requireFree(player, plan.size(), context);
        EditSession editSession = context.session().createEditSession(context.actor());
        enqueue(player, new Job(player, plan, null, null, pattern, null, null, plan.size(), description,
                editSession, context, listener, null));
        return plan.size();
    }

    public static int submitCapturing(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            String patternInput,
            String description,
            CapturedJobListener listener
    ) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        requireCapture(plan, player, context);
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        EditSession editSession = context.session().createEditSession(context.actor());
        enqueue(player, new Job(player, plan, pattern, null, null, null, null, plan.size(), description,
                editSession, context, null, listener));
        return plan.size();
    }

    public static int submitStyledCapturing(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            PositionPattern pattern,
            String description,
            CapturedJobListener listener
    ) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        requireCapture(plan, player, context);
        EditSession editSession = context.session().createEditSession(context.actor());
        enqueue(player, new Job(player, plan, null, null, pattern, null, null, plan.size(), description,
                editSession, context, null, listener));
        return plan.size();
    }

    private static void requireCapture(PackedBlockPlan plan, ServerPlayerEntity player,
                                       WorldEditService.PlayerContext context) {
        OperationPolicy.validate(player, plan.size(), 0);
        if (plan.size() == 0 || plan.captureSize() == 0) {
            throw new IllegalArgumentException("El plan de instancia no contiene bloques capturables");
        }
        requireFree(player, plan.size(), context);
    }

    public static int submitClipboard(
            ServerPlayerEntity player,
            Clipboard clipboard,
            BlockPos destination,
            String description
    ) {
        long volume = clipboard.getRegion().getVolume();
        OperationPolicy.validate(player, volume, 0);
        if (volume <= 0) {
            throw new IllegalArgumentException("El portapapeles está vacío");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        requireFree(player, volume, context);
        EditSession editSession = context.session().createEditSession(context.actor());
        BlockVector3 target = BlockVector3.at(destination.getX(), destination.getY(), destination.getZ());
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 offset = target.subtract(origin);
        enqueue(player, new Job(player, null, null, clipboard.getRegion().iterator(), null, clipboard, offset,
                volume, description, editSession, context, null, null));
        return (int) Math.min(Integer.MAX_VALUE, volume);
    }

    /**
     * Una edición por jugador, y que quepa en su {@code //limit} de WorldEdit: si no, se dice antes de empezar en vez
     * de pararse a medias.
     */
    private static void requireFree(ServerPlayerEntity player, long blocks, WorldEditService.PlayerContext context) {
        if (JOBS.containsKey(player.getUuid())) {
            throw new IllegalStateException("Ya tienes una edición en curso; usa /komaedit job status");
        }
        EditLimits.check(EditLimits.Kind.WORLDEDIT, blocks, context.session().getBlockChangeLimit())
                .ifPresent(excess -> {
                    throw new IllegalArgumentException(excess.message("La edición", false));
                });
    }

    private static void enqueue(ServerPlayerEntity player, Job job) {
        JOBS.put(job.playerId, job);
        QUEUE.add(job.playerId, job.total);
        List<UUID> admitted = QUEUE.admit(KomaEditConfig.settings().maxConcurrentJobs(), ServerWork::paused);
        for (UUID owner : admitted) {
            Job started = JOBS.get(owner);
            if (started != null) {
                started.placingSince = System.nanoTime();
            }
        }
        QUEUE.position(job.playerId).ifPresent(position -> Feedback.info(player, Feedback.format(
                "%s espera su turno: puesto %s · empieza en ~%s · /komaedit cancel la quita de la cola",
                job.description, position, ProgressLine.duration(secondsUntilStart(job.playerId)))));
    }

    private static double secondsUntilStart(UUID owner) {
        return QUEUE.secondsUntilStart(owner, KomaEditConfig.settings().maxConcurrentJobs(), RATE.perSecond(),
                ServerWork::paused);
    }

    /** El estado de la edición del jugador (en cola, colocando o en pausa). */
    public static Optional<JobStatus> status(UUID playerId) {
        Job job = JOBS.get(playerId);
        if (job == null) {
            return Optional.empty();
        }
        int position = QUEUE.position(playerId).orElse(0);
        return Optional.of(new JobStatus(
                job.id, job.description, job.processed, job.total, job.editSession.size(),
                System.nanoTime() - job.startedAt, position,
                position > 0 ? secondsUntilStart(playerId) : 0.0, ServerWork.paused(playerId),
                job.plan == null ? 0L : job.plan.footprintBytes()
        ));
    }

    /** La línea de la barra de progreso: en cola (puesto y espera), en pausa o colocando. */
    public static Optional<ProgressLine> progress(UUID playerId) {
        Job job = JOBS.get(playerId);
        if (job == null) {
            return Optional.empty();
        }
        Optional<Integer> position = QUEUE.position(playerId);
        long since = job.placingSince == 0L ? job.startedAt : job.placingSince;
        if (ServerWork.paused(playerId)) {
            return Optional.of(new ProgressLine(job.description, ProgressLine.Phase.PAUSED, job.processed,
                    job.total, System.nanoTime() - since, position.isPresent()
                    ? "en pausa por un admin (aún en la cola)" : "en pausa por un admin"));
        }
        if (position.isPresent()) {
            return Optional.of(ProgressLine.queued(job.description, position.get(), QUEUE.waiting(),
                    secondsUntilStart(playerId)));
        }
        return Optional.of(new ProgressLine(job.description, ProgressLine.Phase.PLACING, job.processed, job.total,
                System.nanoTime() - since, "/komaedit cancel"));
    }

    /** Las ediciones de todos, para {@code /komaedit jobs}. */
    public static List<UUID> owners() {
        return new ArrayList<>(JOBS.keySet());
    }

    /** Bloques por segundo que coloca ahora la cola entera; -1 si no se ha medido. */
    public static double blocksPerSecond() {
        return RATE.perSecond();
    }

    public static boolean cancel(UUID playerId, boolean notify) {
        Job job = JOBS.get(playerId);
        if (job == null) {
            return false;
        }
        remove(job);
        finish(job, null, Outcome.CANCELLED, null);
        return true;
    }

    public static void shutdown() {
        for (Job job : List.copyOf(JOBS.values())) {
            remove(job);
            finish(job, null, Outcome.CANCELLED, null);
        }
        JOBS.clear();
    }

    private static void remove(Job job) {
        JOBS.remove(job.playerId);
        QUEUE.remove(job.playerId);
    }

    private static boolean processSlice(Job job, ServerPlayerEntity player, long deadline) {
        long before = job.processed;
        try {
            boolean more = job.run.step(deadline, !ServerWork.paused(job.playerId));
            job.syncProcessed();
            RATE.record(job.processed - before, System.nanoTime());
            QUEUE.progress(job.playerId, job.processed);
            if (job.run.finished()) {
                remove(job);
                finish(job, player, Outcome.DONE, null);
                return false;
            }
            job.persistProgress();
            return more;
        } catch (MaxChangedBlocksException exception) {
            remove(job);
            job.syncProcessed();
            finish(job, player, Outcome.LIMIT, null);
        } catch (RuntimeException exception) {
            remove(job);
            job.syncProcessed();
            KomaEditMod.LOGGER.error("Batch edit failed for {}", job.playerId, exception);
            finish(job, player, Outcome.FAILED, readableMessage(exception));
        }
        return false;
    }

    /**
     * Patrón que el plan pone en la entrada: el bloque fijo, el patrón por posición o el general. {@code null} si el
     * patrón por posición deja esa posición sin tocar.
     */
    static Pattern patternFor(
            PackedBlockPlan plan,
            int index,
            BlockVector3 position,
            Pattern defaultPattern,
            PositionPattern positionPattern
    ) {
        BlockState override = plan.override(index);
        if (override != null) {
            return override;
        }
        return positionPattern != null ? positionPattern.pattern(position) : defaultPattern;
    }

    /**
     * Bloque que se coloca con ese patrón: el del patrón o, si el plan pide una losa o escalera, la pieza de ese
     * mismo material. Si el material no tiene piezas, el bloque de la figura se queda entero y las piezas añadidas
     * fuera se omiten ({@code null}).
     */
    static BaseBlock shapeFor(PackedBlockPlan plan, int index, BlockVector3 position, Pattern pattern) {
        BaseBlock base = pattern.applyBlock(position);
        byte piece = plan.piece(index);
        if (piece == SmoothPiece.FULL) {
            return base;
        }
        BlockState shaped = SmoothBlockStates.shape(base, piece, plan.pieceMaterials());
        if (shaped != null) {
            return shaped.toBaseBlock();
        }
        return SmoothPiece.isAdded(piece) ? null : base;
    }

    /** Lo que colocaría cada entrada, para la vista previa y el fantasma: el mismo cálculo que al aplicar. */
    public static PlanPreviews.PlanBlocks blocks(
            PackedBlockPlan plan,
            Pattern defaultPattern,
            PositionPattern positionPattern
    ) {
        return (index, position) -> {
            Pattern pattern = patternFor(plan, index, position, defaultPattern, positionPattern);
            return pattern == null ? null : shapeFor(plan, index, position, pattern);
        };
    }

    /** El mundo de la edición y su {@link EditSession} como destino de {@link PlanApplication}. */
    private record WorldTarget(Job job) implements PlanApplication.Target {
        @Override
        public int rawIdAt(int x, int y, int z) {
            return job.rawIdAt(x, y, z);
        }

        @Override
        public boolean write(int index, int x, int y, int z) throws MaxChangedBlocksException {
            BlockVector3 position = BlockVector3.at(x, y, z);
            Pattern pattern = patternFor(job.plan, index, position, job.defaultPattern, job.positionPattern);
            boolean placed = true;
            if (pattern != null) {
                BaseBlock block = shapeFor(job.plan, index, position, pattern);
                if (block == null) {
                    placed = false;
                } else {
                    job.editSession.setBlock(position, job.withKeptData(index, position, block));
                }
            }
            int captureIndex = job.plan.captureIndex(index);
            if (placed && captureIndex != PackedBlockPlan.NO_CAPTURE) {
                // Lo escrito sigue en el búfer de WorldEdit: se lee cuando la ventana esté en el mundo.
                job.pendingCaptures.add(captureIndex);
                job.pendingCapturePositions.add(PackedBlockPlan.pack(x, y, z));
            }
            return placed;
        }
    }

    /** Lo que recorre un plan, para {@link PlacementRun}. */
    private record PlanSource(PlanApplication application, WorldTarget target) implements PlacementRun.Source {
        @Override
        public int place(int maxEntries, long deadline) throws MaxChangedBlocksException {
            return application.step(target, maxEntries, deadline, System::nanoTime);
        }

        @Override
        public boolean done() {
            return application.done();
        }
    }

    /** Una región con su patrón por posición, o un portapapeles, para {@link PlacementRun}. */
    private record IteratorSource(Job job) implements PlacementRun.Source {
        @Override
        public int place(int maxEntries, long deadline) throws MaxChangedBlocksException {
            int processed = 0;
            while (job.regionIterator.hasNext() && processed < maxEntries
                    && ((processed & 63) != 0 || System.nanoTime() < deadline)) {
                if (job.clipboard != null) {
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
            return processed;
        }

        @Override
        public boolean done() {
            return !job.regionIterator.hasNext();
        }
    }

    /**
     * El vaciado de la sesión de WorldEdit: {@code commit} pasa al mundo el búfer y luego aplica la luz, los vecinos
     * y el envío a los clientes posición a posición mientras quede tiempo. Lo usa también la reconstrucción animada.
     */
    public static PlacementRun.Session session(EditSession editSession) {
        return () -> {
            Operation operation = editSession.commit();
            if (operation == null) {
                return null;
            }
            Operation[] pending = {operation};
            return deadline -> {
                RunContext context = new RunContext() {
                    @Override
                    public boolean shouldContinue() {
                        return System.nanoTime() < deadline;
                    }
                };
                try {
                    while (pending[0] != null) {
                        pending[0] = pending[0].resume(context);
                        if (pending[0] != null && System.nanoTime() >= deadline) {
                            return false;
                        }
                    }
                } catch (WorldEditException exception) {
                    throw new IllegalStateException(exception.getMessage(), exception);
                }
                return true;
            };
        };
    }

    /** Cómo acabó una edición. */
    private enum Outcome {
        DONE,
        CANCELLED,
        /** Se paró en el límite de bloques de WorldEdit ({@code //limit}). */
        LIMIT,
        FAILED
    }

    /** Una edición que dura al menos esto suena al terminar (si el cliente tiene el mod y el sonido activado). */
    private static final long DONE_SOUND_AFTER_NANOS = 3_000_000_000L;

    private static void finish(Job job, ServerPlayerEntity player, Outcome outcome, String reason) {
        boolean cancelled = outcome != Outcome.DONE;
        if (job.application != null) {
            job.application.stop();
            job.protectedBlocks = job.application.protectedBlocks();
        }
        try {
            // Solo queda lo de la última ventana: cerrar ya no escribe la edición entera de golpe.
            job.editSession.close();
        } finally {
            if (job.editSession.size() > 0) {
                EditHistory.label(job.editSession, job.description);
                job.context.session().remember(job.editSession);
            }
        }
        job.resolveCaptures();
        if (!JOBS.containsKey(job.playerId)) {
            ServerWork.pause(job.playerId, false);
        }
        if (player != null) {
            announce(job, player, outcome, reason);
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

    /** Una sola línea por edición: cómo acabó, cuántos bloques y cómo deshacerla. */
    private static void announce(Job job, ServerPlayerEntity player, Outcome outcome, String reason) {
        int placed = job.editSession.size();
        Text kept = job.protectedBlocks > 0
                ? Feedback.format(" · %s cambios manuales conservados", job.protectedBlocks) : Text.empty();
        switch (outcome) {
            case DONE -> {
                Feedback.ok(player, Feedback.format("%s completada: %s bloques", job.description, placed)
                        .append(kept).append(" · //undo"));
                if (System.nanoTime() - job.startedAt >= DONE_SOUND_AFTER_NANOS) {
                    Feedback.sound(player, FeedbackEventPayload.Kind.DONE);
                }
            }
            case CANCELLED -> Feedback.warn(player, Feedback.format("%s cancelada: %s bloques colocados",
                    job.description, placed).append(kept).append(" · //undo"));
            case LIMIT -> Feedback.warn(player, Feedback.format(
                    "%s parada en el límite de WorldEdit: %s bloques colocados", job.description, placed)
                    .append(kept).append(" · //undo los quita, //limit lo sube"));
            case FAILED -> Feedback.error(player, Feedback.format("%s falló: %s", job.description, reason)
                    .append(placed > 0 ? Feedback.format(" · %s bloques colocados, //undo los quita", placed)
                            : Text.literal(" · no se colocó nada")));
        }
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

    /**
     * @param elapsedNanos     tiempo desde que la edición entró en la cola
     * @param queuePosition    puesto en la espera (1 la primera), o 0 si ya coloca
     * @param secondsUntilStart lo que le falta para empezar, si espera
     * @param paused           en pausa por un administrador
     * @param planBytes        memoria del plan (0 si no es un plan)
     */
    public record JobStatus(String id, String description, long processed, long total, int changed,
                            long elapsedNanos, int queuePosition, double secondsUntilStart, boolean paused,
                            long planBytes) {
        public int percentage() {
            return total == 0 ? 100 : (int) Math.min(100L, processed * 100L / total);
        }
    }

    private static final class Job {
        private static final long JOURNAL_INTERVAL_NANOS = 1_000_000_000L;
        private final String id;
        private final UUID playerId;
        private final ServerWorld world;
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
        private final PlanApplication application;
        private final PlacementRun run;
        private final IntArrayList pendingCaptures = new IntArrayList();
        private final LongArrayList pendingCapturePositions = new LongArrayList();
        private final BlockPos.Mutable cursor = new BlockPos.Mutable();
        private final long startedAt = System.nanoTime();
        private long placingSince;
        private long processed;
        private int protectedBlocks;
        private long lastJournalAt;
        private long lastJournalProcessed;

        private Job(
                ServerPlayerEntity player,
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
            this.playerId = player.getUuid();
            this.world = player.getServerWorld();
            this.id = OperationLedger.start(playerId, description, total);
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
            this.application = plan == null ? null : new PlanApplication(plan);
            PlacementRun.Source source = plan == null
                    ? new IteratorSource(this)
                    : new PlanSource(application, new WorldTarget(this));
            this.run = new PlacementRun(source, session(editSession), System::nanoTime, this::resolveCaptures);
        }

        private int rawIdAt(int x, int y, int z) {
            return Block.getRawIdFromState(world.getBlockState(cursor.set(x, y, z)));
        }

        /**
         * Un cambio de estado de lo que ya hay (el plan esperaba ese mismo bloque: anegar un cofre, girar un horno)
         * conserva lo que guarda, que WorldEdit vaciaría al escribir encima ({@link KeptData}). La entrada ya pasó
         * la comprobación de lo esperado, así que lo esperado es lo que hay.
         */
        private BaseBlock withKeptData(int index, BlockVector3 position, BaseBlock block) {
            int expected = plan.expectedStateId(index);
            if (expected == PackedBlockPlan.NO_EXPECTED_STATE) {
                return block;
            }
            net.minecraft.block.BlockState current = Block.getStateFromRawId(expected);
            if (!KeptData.keeps(block.getNbtReference() != null, true, current.hasBlockEntity(),
                    FabricAdapter.adapt(block.getBlockType()), current.getBlock())) {
                return block;
            }
            BaseBlock full = FabricAdapter.adapt(world).getFullBlock(position);
            return full.getNbtReference() == null ? block : block.toImmutableState().toBaseBlock(full.getNbtReference());
        }

        /** Con la ventana ya en el mundo: el estado que quedó en cada posición capturada (instancias). */
        private void resolveCaptures() {
            for (int index = 0; index < pendingCaptures.size(); index++) {
                long packed = pendingCapturePositions.getLong(index);
                capturedStateIds[pendingCaptures.getInt(index)] = rawIdAt(PackedBlockPlan.unpackX(packed),
                        PackedBlockPlan.unpackY(packed), PackedBlockPlan.unpackZ(packed));
            }
            pendingCaptures.clear();
            pendingCapturePositions.clear();
        }

        private void syncProcessed() {
            if (application != null) {
                processed = application.processed();
            }
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
    }
}
