package com.komainu.moxia.preview;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.feedback.FeedbackEventPayload;
import com.komainu.moxia.gizmo.GhostService;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Vista previa universal de los planes de bloques. {@link BatchEditService} llama a {@link #intercept} antes de
 * encolar un plan; si la orden pidió {@code -v} (o el jugador previsualiza siempre a partir de N bloques), el plan
 * no se coloca:
 *
 * <ol>
 *     <li><b>Preparar</b> (repartido entre ticks): se resuelve el bloque final de cada entrada con su patrón y se
 *     lee el estado actual del mundo. El resultado es un plan «congelado» de escrituras condicionales: lo que se ve
 *     es exactamente lo que se colocará, también con patrones aleatorios.</li>
 *     <li><b>Enseñar</b>: displays solo para quien edita ({@link PreviewDisplays}), con el límite de entidades de la
 *     configuración ({@link DisplaySampler}) y la caja.</li>
 *     <li><b>Confirmar</b> ({@code /komaedit confirm}): se comprueba que la zona no ha cambiado y se encola el mismo
 *     plan congelado, sin recalcular. Si cambió, se avisa y se recalcula la operación, que vuelve a enseñarse.</li>
 * </ol>
 *
 * Una operación puede añadir cómo recalcularse y qué más vigilar con {@link #withExtras} (lo hace
 * {@code RegionOperationService}); sin eso, se recalcula volviendo a ejecutar la orden, y si no venía de una orden,
 * volviendo a preparar el mismo plan.
 */
public final class PlanPreviews {
    private static final long PREPARE_NANOS_PER_TICK = 4_000_000L;
    private static final int CHECKS_PER_TICK = 60_000;
    private static final int SPAWNS_PER_TICK = 128;
    private static final int ANNOUNCE_ABOVE_SECONDS = 3;
    private static final int ANNOUNCE_PREPARING_ABOVE = 100_000;
    /** Cambios como mucho en el plan que dibuja el cliente (su superficie o una muestra): unos 1,2 MB. */
    private static final int CLIENT_ENTRIES = 250_000;
    private static final ThreadLocal<Extras> EXTRAS = new ThreadLocal<>();
    private static final Map<UUID, Preparation> PREPARING = new LinkedHashMap<>();
    private static final ExecutorService SAMPLER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit-Preview");
        thread.setDaemon(true);
        return thread;
    });
    private static final PreviewFlow<Held> FLOW = new PreviewFlow<>(new Hooks(), 90_000_000_000L);
    private static volatile MinecraftServer server;

    private PlanPreviews() {
    }

    /** Lo que una operación puede añadir a su vista previa. */
    public record Extras(Runnable recompute, ZoneCheck.Watch watch) {
    }

    /** Bloque final de una entrada del plan, o {@code null} si esa entrada no cambia nada. */
    @FunctionalInterface
    public interface PlanBlocks {
        BaseBlock block(int index, BlockVector3 position);
    }

    public enum Confirmation {
        /** No hay vista previa. */
        NONE,
        /** Aún se está preparando. */
        PREPARING,
        /** Ya se estaba confirmando. */
        CHECKING,
        /** Hay otra edición colocándose; la vista previa sigue esperando. */
        BUSY,
        /** Caducó justo ahora; ya se avisó. */
        EXPIRED,
        /** Comprobando la zona; se aplicará en unos ticks. */
        STARTED
    }

    /** Ejecuta {@code action} con {@code extras} para la vista previa que pueda salir de ella. */
    public static <T> T withExtras(Extras extras, Supplier<T> action) {
        Extras previous = EXTRAS.get();
        EXTRAS.set(extras);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                EXTRAS.remove();
            } else {
                EXTRAS.set(previous);
            }
        }
    }

    /** Si un plan de {@code blocks} bloques de este jugador pasaría ahora por la vista previa. */
    public static boolean wouldPreview(ServerPlayerEntity player, int blocks) {
        UUID id = player.getUuid();
        EditRequest request = EditRequests.peek(id);
        return GhostService.wouldTake(player, request) || PreviewPolicy.shouldPreview(request, blocks,
                request.kind() == EditRequest.Kind.DEFAULT ? PreviewPreferences.alwaysAbove(id) : 0);
    }

    /**
     * Decide si el plan pasa por la vista previa. Si no, avisa de la estimación cuando es largo o supera el límite
     * de WorldEdit y devuelve {@code false}: el llamador lo encola.
     *
     * @param applyOriginal encola el plan original tal cual (se usa si no se puede congelar, por ejemplo con
     *                      bloques con NBT)
     * @return {@code true} si el plan queda en vista previa y no hay que encolarlo
     */
    public static boolean intercept(
            ServerPlayerEntity player,
            PackedBlockPlan plan,
            PlanBlocks blocks,
            String description,
            Runnable applyOriginal
    ) {
        UUID id = player.getUuid();
        Extras extras = EXTRAS.get();
        EditRequest request = EditRequests.resolve(id);
        // Un objeto que se puede mover sale como fantasma con gizmo si el jugador lo tiene activado.
        if (GhostService.offer(player, plan, blocks, description, request)) {
            LastEdits.remember(id, request);
            return true;
        }
        int threshold = request.kind() == EditRequest.Kind.DEFAULT ? PreviewPreferences.alwaysAbove(id) : 0;
        if (!PreviewPolicy.shouldPreview(request, plan.size(), threshold)) {
            // Lo confirmado ya enseñó su estimación y su aviso de //limit.
            if (request.kind() != EditRequest.Kind.CONFIRMED) {
                announce(player, plan.size(), description);
            }
            LastEdits.remember(id, request);
            return false;
        }
        server = player.getServer();
        boolean replaced = discardQuietly(id);
        Preparation preparation = new Preparation(id, player.getServerWorld().getRegistryKey(), plan, blocks,
                description, request, extras, applyOriginal);
        PREPARING.put(id, preparation);
        if (plan.size() > ANNOUNCE_PREPARING_ABOVE) {
            Feedback.status(player, Feedback.format("Preparando la vista previa de %s (%s bloques)…", description,
                    plan.size()).append(replaced ? " · la anterior se descartó" : ""));
        } else if (replaced) {
            Feedback.status(player, "La vista previa anterior se descartó");
        }
        LastEdits.remember(id, request);
        return true;
    }

    /** Empieza a aplicar la vista previa del jugador. */
    public static Confirmation confirm(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (PREPARING.containsKey(id)) {
            return Confirmation.PREPARING;
        }
        Optional<Held> held = FLOW.plan(id);
        if (held.isEmpty()) {
            return Confirmation.NONE;
        }
        Optional<PreviewFlow.Status> status = FLOW.status(id, System.nanoTime());
        if (status.isPresent() && status.get().phase() == PreviewFlow.Phase.CHECKING) {
            return Confirmation.CHECKING;
        }
        if (BatchEditService.status(id).isPresent()) {
            return Confirmation.BUSY;
        }
        held.get().confirmedAt = System.nanoTime();
        return FLOW.confirm(id, System.nanoTime()) ? Confirmation.STARTED : Confirmation.EXPIRED;
    }

    /**
     * Descarta la vista previa (o su preparación) sin aplicar nada.
     *
     * @return la descripción de la operación descartada
     */
    public static Optional<String> cancel(UUID player) {
        Preparation preparation = PREPARING.remove(player);
        if (preparation != null) {
            return Optional.of(preparation.description);
        }
        Optional<Held> held = FLOW.plan(player);
        if (held.isPresent() && FLOW.discard(player)) {
            return Optional.of(held.get().description);
        }
        return Optional.empty();
    }

    /** Descarta con un aviso (al cambiar de dimensión o reaparecer). */
    public static void discard(ServerPlayerEntity player, String reason) {
        cancel(player.getUuid()).ifPresent(description -> Feedback.warn(player,
                "Vista previa de " + description + " descartada " + reason + ": no se ha cambiado nada"));
    }

    /** El jugador se fue: se olvida todo lo suyo (el cliente ya no tiene los displays). */
    public static void forget(UUID player) {
        PREPARING.remove(player);
        FLOW.discard(player);
        EditRequests.forget(player);
        PreviewPreferences.forget(player);
        PreviewPlanSender.forget(player);
        LastEdits.forget(player);
    }

    public static boolean hasPending(UUID player) {
        return PREPARING.containsKey(player) || FLOW.has(player);
    }

    /** Cambios de la vista previa que espera confirmación del jugador, o 0 (el pie del Studio los cuenta). */
    public static long pendingChanges(UUID player) {
        return FLOW.plan(player).map(held -> (long) held.frozen.size()).orElse(0L);
    }

    /** Si {@code plan} es el que el jugador tiene ahora en vista previa (o preparándose). */
    public static boolean holds(UUID player, PackedBlockPlan plan) {
        if (GhostService.holds(player, plan)) {
            return true;
        }
        Preparation preparation = PREPARING.get(player);
        if (preparation != null) {
            return preparation.plan == plan;
        }
        return FLOW.plan(player).map(held -> held.original == plan).orElse(false);
    }

    public static void clearAll() {
        PREPARING.clear();
        FLOW.clear();
        EditRequests.clearAll();
        PreviewPreferences.clearAll();
        PreviewPlanSender.clearAll();
        LastEdits.clearAll();
        server = null;
    }

    /** Fase de la vista previa del jugador para la barra de progreso. */
    public static Optional<ProgressLine> progress(UUID player) {
        long now = System.nanoTime();
        Preparation preparation = PREPARING.get(player);
        if (preparation != null) {
            return Optional.of(new ProgressLine(preparation.description, ProgressLine.Phase.PREPARING,
                    preparation.sampling ? preparation.plan.size() : preparation.cursor, preparation.plan.size(),
                    now - preparation.startedAt, null));
        }
        Optional<Held> held = FLOW.plan(player);
        Optional<PreviewFlow.Status> status = FLOW.status(player, now);
        if (held.isEmpty() || status.isEmpty()) {
            return Optional.empty();
        }
        PreviewFlow.Status current = status.get();
        if (current.phase() == PreviewFlow.Phase.CHECKING) {
            return Optional.of(new ProgressLine(held.get().description, ProgressLine.Phase.CHECKING,
                    current.checked(), current.total(), now - held.get().confirmedAt, null));
        }
        long remaining = current.remainingNanos() / 1_000_000L;
        return Optional.of(new ProgressLine(held.get().description, ProgressLine.Phase.WAITING, remaining,
                Math.max(1L, current.ttlNanos() / 1_000_000L), 0L,
                ProgressLine.group(held.get().frozen.size()) + " cambios · /komaedit confirm · caduca en "
                        + ProgressLine.duration(remaining / 1_000.0)));
    }

    public static void tick(MinecraftServer current) {
        server = current;
        if (PREPARING.isEmpty() && FLOW.size() == 0) {
            return;
        }
        long deadline = System.nanoTime() + PREPARE_NANOS_PER_TICK;
        prepare(current, deadline);
        FLOW.tick(id -> {
            ServerPlayerEntity player = current.getPlayerManager().getPlayer(id);
            Held held = FLOW.plan(id).orElse(null);
            if (player == null || held == null || !player.getServerWorld().getRegistryKey().equals(held.world)) {
                return null;
            }
            ServerWorld world = player.getServerWorld();
            BlockPos.Mutable position = new BlockPos.Mutable();
            return (x, y, z) -> Block.getRawIdFromState(world.getBlockState(position.set(x, y, z)));
        }, System.nanoTime(), CHECKS_PER_TICK);
        for (Held held : FLOW.plans()) {
            ServerPlayerEntity player = current.getPlayerManager().getPlayer(held.player);
            if (player != null) {
                held.spawnSome(player, SPAWNS_PER_TICK);
            }
        }
    }

    // ---------------------------------------------------------------- preparación

    private static void prepare(MinecraftServer current, long deadline) {
        for (Preparation preparation : List.copyOf(PREPARING.values())) {
            if (System.nanoTime() >= deadline) {
                return;
            }
            if (preparation.sampling) {
                continue;
            }
            ServerPlayerEntity player = current.getPlayerManager().getPlayer(preparation.player);
            if (player == null || !player.getServerWorld().getRegistryKey().equals(preparation.world)) {
                PREPARING.remove(preparation.player);
                continue;
            }
            try {
                if (preparation.step(player.getServerWorld(), deadline)) {
                    sample(current, player, preparation);
                }
            } catch (RuntimeException exception) {
                PREPARING.remove(preparation.player);
                KomaEditMod.LOGGER.warn("KomaEdit preview preparation failed for {}", preparation.player, exception);
                Feedback.error(player, "No se pudo preparar la vista previa de " + preparation.description + ": "
                        + readable(exception));
            }
        }
    }

    private static void sample(MinecraftServer current, ServerPlayerEntity player, Preparation preparation) {
        PackedBlockPlan frozen = preparation.frozen.build();
        preparation.builtFrozen = frozen;
        if (frozen.size() == 0) {
            PREPARING.remove(preparation.player);
            Feedback.warn(player, preparation.description + ": no había nada que cambiar");
            return;
        }
        preparation.sampling = true;
        long[] positions = new long[frozen.size()];
        for (int index = 0; index < positions.length; index++) {
            positions[index] = frozen.packedPosition(index);
        }
        boolean client = PreviewPlanSender.supported(player);
        int limit = KomaEditConfig.settings().previewMaxDisplays();
        SAMPLER.execute(() -> {
            try {
                if (client) {
                    ClientPlan plan = clientPlan(preparation, frozen, positions);
                    current.execute(() -> show(current, preparation, plan.choice(), plan));
                } else {
                    DisplaySampler.Choice choice = DisplaySampler.choose(positions, positions.length, limit);
                    current.execute(() -> show(current, preparation, choice, null));
                }
            } catch (RuntimeException exception) {
                current.execute(() -> {
                    if (PREPARING.remove(preparation.player, preparation)) {
                        ServerPlayerEntity online = current.getPlayerManager().getPlayer(preparation.player);
                        if (online != null) {
                            Feedback.error(online, "No se pudo preparar la vista previa: " + readable(exception));
                        }
                    }
                });
            }
        });
    }

    /** El plan troceado para un cliente que dibuja él mismo la vista previa. */
    private record ClientPlan(int id, List<byte[]> parts, DisplaySampler.Choice choice) {
    }

    /** En el hilo de la vista previa: superficie, tipos y estados de lo que se enseña, en partes. */
    private static ClientPlan clientPlan(Preparation preparation, PackedBlockPlan frozen, long[] positions) {
        PreviewSurface.Selection selection = PreviewSurface.select(positions, positions.length, CLIENT_ENTRIES);
        int count = selection.shown();
        long[] shown = new long[count];
        int[] states = new int[count];
        byte[] kinds = new byte[count];
        for (int slot = 0; slot < count; slot++) {
            int index = selection.indices()[slot];
            shown[slot] = positions[index];
            states[slot] = preparation.stateIds.getInt(index);
            kinds[slot] = (byte) preparation.kind(index).ordinal();
        }
        BlockBounds bounds = frozen.bounds();
        int id = PreviewPlanSender.nextId();
        PreviewPlanCodec.Header header = new PreviewPlanCodec.Header(id, bounds.minimumX(), bounds.minimumY(),
                bounds.minimumZ(), bounds.maximumX(), bounds.maximumY(), bounds.maximumZ(), frozen.size(),
                preparation.added, preparation.removed, selection.mode(), count);
        List<byte[]> parts = PreviewPlanCodec.encode(header, shown, states, kinds, selection.faces(),
                PreviewPlanCodec.DEFAULT_PART_BYTES);
        DisplaySampler.Choice choice = new DisplaySampler.Choice(selection.indices(), selection.mode(),
                frozen.size(), selection.surface());
        return new ClientPlan(id, parts, choice);
    }

    private static void show(MinecraftServer current, Preparation preparation, DisplaySampler.Choice choice,
                             ClientPlan clientPlan) {
        if (!PREPARING.remove(preparation.player, preparation)) {
            return;
        }
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(preparation.player);
        if (player == null || !player.getServerWorld().getRegistryKey().equals(preparation.world)) {
            return;
        }
        PackedBlockPlan frozen = preparation.builtFrozen;
        List<PreviewDisplays.Display> displays = new ArrayList<>();
        if (clientPlan == null) {
            // Sin KomaEdit en el cliente: block displays enviados por paquetes, solo a este jugador.
            displays.addAll(PreviewDisplays.box(frozen.bounds()));
            for (int index : choice.indices()) {
                long packed = frozen.packedPosition(index);
                BlockState placed = FabricAdapter.adapt(frozen.override(index));
                displays.add(PreviewDisplays.block(PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                        PackedBlockPlan.unpackZ(packed), placed, preparation.kind(index)));
            }
        }
        Held held = new Held(preparation, frozen, choice, displays, sessionLimit(player),
                clientPlan == null ? 0 : clientPlan.id());
        ZoneCheck.Watch watch = ZoneCheck.Watch.of(frozen)
                .then(preparation.extras == null ? null : preparation.extras.watch());
        long ttl = KomaEditConfig.settings().previewTimeoutSeconds() * 1_000_000_000L;
        FLOW.hold(preparation.player, held, watch, System.nanoTime(), ttl);
        if (clientPlan != null) {
            PreviewPlanSender.start(player, clientPlan.id(), clientPlan.parts());
        } else {
            held.spawnSome(player, SPAWNS_PER_TICK);
        }
        announceHeld(player, held, ttl);
    }

    /**
     * Una línea: cuánto cambia, la caja, lo que tardará y los botones. Los detalles (cuánto se pone y se quita, la
     * muestra, los bloques con datos) van al pasar el ratón por el resumen; el aviso del límite de WorldEdit, aparte,
     * porque importa.
     */
    private static void announceHeld(ServerPlayerEntity player, Held held, long ttl) {
        BuildEstimate estimate = BuildEstimate.of(held.frozen.size(), 0, 0, held.frozen.bounds());
        MutableText summary = Feedback.format("Vista previa de %s: %s cambios", held.description,
                held.frozen.size());
        if (held.removed > 0) {
            summary.append(Feedback.format(" (%s quitados)", held.removed));
        }
        summary.append(Feedback.format(" · %s×%s×%s · ~%s", estimate.sizeX(), estimate.sizeY(), estimate.sizeZ(),
                ProgressLine.duration(Math.max(1.0, estimate.seconds()))));
        StringBuilder details = new StringBuilder()
                .append(ProgressLine.group(held.added)).append(" puestos o cambiados, ")
                .append(ProgressLine.group(held.removed)).append(" quitados")
                .append("\nCaduca en ").append(ProgressLine.duration(ttl / 1_000_000_000.0));
        if (held.choice.mode() != DisplaySampler.Mode.ALL) {
            String how = held.choice.mode() == DisplaySampler.Mode.SURFACE ? "la superficie" : "una muestra";
            details.append("\nSe ve ").append(how).append(": ").append(ProgressLine.group(held.choice.shown()))
                    .append(" de ").append(ProgressLine.group(held.frozen.size()));
        }
        if (!held.freezable) {
            details.append("\nHay bloques con datos (NBT): al aplicar se volverá a evaluar el patrón");
        }
        Text hover = Text.literal(details.toString());
        player.sendMessage(Feedback.line(Feedback.Level.INFO, Text.empty()
                .append(summary.styled(style -> style.withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))))
                .append(" ")
                .append(Feedback.button("[Aplicar]", "/komaedit confirm", Formatting.GREEN,
                        "Coloca exactamente lo que ves, sin recalcular"))
                .append(" ")
                .append(Feedback.button("[Descartar]", "/komaedit cancel", Formatting.RED, "No cambia nada"))),
                false);
        BuildEstimate.limitWarning(held.frozen.size(), held.sessionLimit).ifPresent(warning ->
                Feedback.warn(player, warning));
    }

    /** Aviso de lo que va a tardar y del límite de WorldEdit cuando un plan se encola sin vista previa. */
    private static void announce(ServerPlayerEntity player, int blocks, String description) {
        double seconds = BuildEstimate.placeSeconds(blocks);
        Optional<String> warning = BuildEstimate.limitWarning(blocks, sessionLimit(player));
        if (seconds >= ANNOUNCE_ABOVE_SECONDS) {
            Feedback.status(player, Feedback.format("%s: %s bloques · ~%s · /komaedit cancel para parar",
                    description, blocks, ProgressLine.duration(seconds)));
        }
        warning.ifPresent(text -> Feedback.warn(player, text));
    }

    private static int sessionLimit(ServerPlayerEntity player) {
        try {
            return WorldEditService.context(player).session().getBlockChangeLimit();
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static boolean discardQuietly(UUID player) {
        boolean preparing = PREPARING.remove(player) != null;
        return FLOW.discard(player) || preparing;
    }

    // ---------------------------------------------------------------- estado

    /** Una vista previa en preparación: congela el plan entrada a entrada leyendo el mundo. */
    private static final class Preparation {
        private final UUID player;
        private final RegistryKey<World> world;
        private final PackedBlockPlan plan;
        private final PlanBlocks blocks;
        private final String description;
        private final EditRequest request;
        private final Extras extras;
        private final Runnable applyOriginal;
        private final PackedBlockPlan.Builder frozen;
        /** Por entrada del plan congelado: id crudo del estado final y si antes había aire o queda aire. */
        private final IntArrayList stateIds;
        private final BitSet beforeAir = new BitSet();
        private final BitSet placedAir = new BitSet();
        private final LongOpenHashSet seen;
        private final long startedAt = System.nanoTime();
        private final BlockPos.Mutable position = new BlockPos.Mutable();
        private PackedBlockPlan builtFrozen;
        private int cursor;
        private int added;
        private int removed;
        private boolean freezable = true;
        private boolean sampling;

        private Preparation(UUID player, RegistryKey<World> world, PackedBlockPlan plan, PlanBlocks blocks,
                            String description, EditRequest request, Extras extras, Runnable applyOriginal) {
            this.player = player;
            this.world = world;
            this.plan = plan;
            this.blocks = blocks;
            this.description = description;
            this.request = request;
            this.extras = extras;
            this.applyOriginal = applyOriginal;
            this.frozen = PackedBlockPlan.builder(plan.size());
            this.stateIds = new IntArrayList(Math.min(plan.size(), 1 << 20));
            this.seen = new LongOpenHashSet(Math.min(plan.size(), 1 << 20));
        }

        /** Qué hace la entrada {@code index} del plan congelado. */
        private PreviewDisplays.Kind kind(int index) {
            return placedAir.get(index) ? PreviewDisplays.Kind.REMOVE
                    : beforeAir.get(index) ? PreviewDisplays.Kind.ADD : PreviewDisplays.Kind.REPLACE;
        }

        /** @return {@code true} cuando ha recorrido todo el plan */
        private boolean step(ServerWorld world, long deadline) {
            while (cursor < plan.size()) {
                if ((cursor & 255) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
                int index = cursor++;
                long packed = plan.packedPosition(index);
                int x = PackedBlockPlan.unpackX(packed);
                int y = PackedBlockPlan.unpackY(packed);
                int z = PackedBlockPlan.unpackZ(packed);
                BaseBlock block = blocks.block(index, BlockVector3.at(x, y, z));
                if (block == null) {
                    continue;
                }
                boolean data = block.getNbtReference() != null;
                freezable &= !data;
                com.sk89q.worldedit.world.block.BlockState state = block.toImmutableState();
                BlockState currentState = world.getBlockState(position.set(x, y, z));
                int current = Block.getRawIdFromState(currentState);
                BlockState placed = FabricAdapter.adapt(state);
                int expected = plan.expectedStateId(index);
                boolean repeated = !seen.add(packed);
                if (!repeated && !data && Block.getRawIdFromState(placed) == current) {
                    continue;
                }
                // Una posición repetida se escribe sin condición, después de la primera: gana la última, como al
                // aplicar el plan original.
                int watched = repeated ? PackedBlockPlan.NO_EXPECTED_STATE
                        : expected != PackedBlockPlan.NO_EXPECTED_STATE ? expected : current;
                int entry = stateIds.size();
                frozen.addConditionalOverride(x, y, z, watched, state);
                stateIds.add(Block.getRawIdFromState(placed));
                if (currentState.isAir()) {
                    beforeAir.set(entry);
                }
                if (placed.isAir()) {
                    placedAir.set(entry);
                    removed++;
                } else {
                    added++;
                }
            }
            return true;
        }
    }

    /** Una vista previa lista: el plan congelado, lo que se enseña y cómo aplicarlo o recalcularlo. */
    private static final class Held {
        private final UUID player;
        private final RegistryKey<World> world;
        private final PackedBlockPlan original;
        private final PlanBlocks blocks;
        private final PackedBlockPlan frozen;
        private final boolean freezable;
        private final String description;
        private final EditRequest request;
        private final Extras extras;
        private final Runnable applyOriginal;
        private final DisplaySampler.Choice choice;
        private final List<PreviewDisplays.Display> displays;
        private final IntArrayList spawned = new IntArrayList();
        private final int added;
        private final int removed;
        private final int sessionLimit;
        /** Id del plan que dibuja el cliente con KomaEdit, o 0 si se enseña con displays. */
        private final int clientPlan;
        private int spawnCursor;
        private boolean hidden;
        private long confirmedAt;

        private Held(Preparation preparation, PackedBlockPlan frozen, DisplaySampler.Choice choice,
                     List<PreviewDisplays.Display> displays, int sessionLimit, int clientPlan) {
            this.player = preparation.player;
            this.world = preparation.world;
            this.original = preparation.plan;
            this.blocks = preparation.blocks;
            this.frozen = frozen;
            this.freezable = preparation.freezable;
            this.description = preparation.description;
            this.request = preparation.request;
            this.extras = preparation.extras;
            this.applyOriginal = preparation.applyOriginal;
            this.choice = choice;
            this.displays = displays;
            this.added = preparation.added;
            this.removed = preparation.removed;
            this.sessionLimit = sessionLimit;
            this.clientPlan = clientPlan;
        }

        private void spawnSome(ServerPlayerEntity player, int budget) {
            if (hidden || spawnCursor >= displays.size()
                    || !player.getServerWorld().getRegistryKey().equals(world)) {
                return;
            }
            int end = Math.min(displays.size(), spawnCursor + budget);
            for (int id : PreviewDisplays.spawn(player, displays.subList(spawnCursor, end))) {
                spawned.add(id);
            }
            spawnCursor = end;
        }
    }

    private static final class Hooks implements PreviewFlow.Hooks<Held> {
        @Override
        public void hide(UUID player, Held held) {
            held.hidden = true;
            ServerPlayerEntity online = online(player);
            if (online != null) {
                PreviewDisplays.destroy(online, held.spawned.toIntArray());
                if (held.clientPlan != 0) {
                    PreviewPlanSender.clear(online, held.clientPlan);
                }
            }
            held.spawned.clear();
        }

        @Override
        public void apply(UUID player, Held held) {
            ServerPlayerEntity online = online(player);
            if (online == null) {
                return;
            }
            try {
                EditRequests.within(EditRequest.CONFIRMED, () -> {
                    if (held.freezable) {
                        BatchEditService.submit(online, held.frozen, "air", held.description);
                    } else {
                        held.applyOriginal.run();
                    }
                });
                Feedback.status(online, Feedback.format("Aplicando %s: %s cambios · /komaedit cancel para parar",
                        held.description, held.frozen.size()));
                Feedback.sound(online, FeedbackEventPayload.Kind.CONFIRM);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                Feedback.error(online, held.description + ": " + readable(exception));
            }
        }

        @Override
        public void changed(UUID player, Held held, long mismatch) {
            ServerPlayerEntity online = online(player);
            if (online == null) {
                return;
            }
            Feedback.warn(online, "La zona cambió desde la vista previa (en " + PackedBlockPlan.unpackX(mismatch)
                    + ", " + PackedBlockPlan.unpackY(mismatch) + ", " + PackedBlockPlan.unpackZ(mismatch)
                    + "): recalculando " + held.description + "…");
            EditRequest again = held.request.asPreview();
            try {
                if (held.extras != null && held.extras.recompute() != null) {
                    EditRequests.within(again, held.extras.recompute());
                } else if (held.request.command() != null) {
                    EditRequests.within(again, () -> dispatch(online, held.request.command()));
                } else {
                    EditRequests.within(again, () -> intercept(online, held.original, held.blocks, held.description,
                            held.applyOriginal));
                }
            } catch (RuntimeException exception) {
                Feedback.error(online, "No se pudo recalcular " + held.description + ": " + readable(exception));
            }
        }

        @Override
        public void expired(UUID player, Held held) {
            ServerPlayerEntity online = online(player);
            if (online != null) {
                Feedback.warn(online, "La vista previa de " + held.description + " caducó: no se ha cambiado nada");
            }
        }
    }

    private static void dispatch(ServerPlayerEntity player, String command) {
        try {
            player.getServer().getCommandManager().getDispatcher().execute(command, player.getCommandSource());
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private static ServerPlayerEntity online(UUID player) {
        MinecraftServer current = server;
        return current == null ? null : current.getPlayerManager().getPlayer(player);
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && (cause.getMessage() == null || cause.getMessage().isBlank())) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
