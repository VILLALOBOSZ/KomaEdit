package com.komainu.moxia.preview;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.PackedBlockPlan;
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
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
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
        return PreviewPolicy.shouldPreview(request, blocks,
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
        int threshold = request.kind() == EditRequest.Kind.DEFAULT ? PreviewPreferences.alwaysAbove(id) : 0;
        if (!PreviewPolicy.shouldPreview(request, plan.size(), threshold)) {
            // Lo confirmado ya enseñó su estimación y su aviso de //limit.
            if (request.kind() != EditRequest.Kind.CONFIRMED) {
                announce(player, plan.size(), description);
            }
            return false;
        }
        server = player.getServer();
        boolean replaced = discardQuietly(id);
        Preparation preparation = new Preparation(id, player.getServerWorld().getRegistryKey(), plan, blocks,
                description, request, extras, applyOriginal);
        PREPARING.put(id, preparation);
        if (replaced) {
            send(player, Text.literal("La vista previa anterior se ha descartado").formatted(Formatting.GRAY));
        }
        if (plan.size() > ANNOUNCE_PREPARING_ABOVE) {
            send(player, Text.literal("Preparando la vista previa de " + description + " ("
                    + ProgressLine.group(plan.size()) + " bloques)…").formatted(Formatting.GRAY));
        }
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
        cancel(player.getUuid()).ifPresent(description -> send(player, Text.literal(
                "Vista previa de " + description + " descartada " + reason + ": no se ha cambiado nada")
                .formatted(Formatting.YELLOW)));
    }

    /** El jugador se fue: se olvida todo lo suyo (el cliente ya no tiene los displays). */
    public static void forget(UUID player) {
        PREPARING.remove(player);
        FLOW.discard(player);
        EditRequests.forget(player);
        PreviewPreferences.forget(player);
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
                send(player, Text.literal("No se pudo preparar la vista previa de " + preparation.description + ": "
                        + readable(exception)).formatted(Formatting.RED));
            }
        }
    }

    private static void sample(MinecraftServer current, ServerPlayerEntity player, Preparation preparation) {
        PackedBlockPlan frozen = preparation.frozen.build();
        preparation.builtFrozen = frozen;
        if (frozen.size() == 0) {
            PREPARING.remove(preparation.player);
            send(player, Text.literal(preparation.description + ": no había nada que cambiar")
                    .formatted(Formatting.YELLOW));
            return;
        }
        preparation.sampling = true;
        long[] positions = new long[frozen.size()];
        for (int index = 0; index < positions.length; index++) {
            positions[index] = frozen.packedPosition(index);
        }
        int limit = KomaEditConfig.settings().previewMaxDisplays();
        SAMPLER.execute(() -> {
            try {
                DisplaySampler.Choice choice = DisplaySampler.choose(positions, positions.length, limit);
                current.execute(() -> show(current, preparation, choice));
            } catch (RuntimeException exception) {
                current.execute(() -> {
                    if (PREPARING.remove(preparation.player, preparation)) {
                        ServerPlayerEntity online = current.getPlayerManager().getPlayer(preparation.player);
                        if (online != null) {
                            send(online, Text.literal("No se pudo preparar la vista previa: " + readable(exception))
                                    .formatted(Formatting.RED));
                        }
                    }
                });
            }
        });
    }

    private static void show(MinecraftServer current, Preparation preparation, DisplaySampler.Choice choice) {
        if (!PREPARING.remove(preparation.player, preparation)) {
            return;
        }
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(preparation.player);
        if (player == null || !player.getServerWorld().getRegistryKey().equals(preparation.world)) {
            return;
        }
        PackedBlockPlan frozen = preparation.builtFrozen;
        ServerWorld world = player.getServerWorld();
        List<PreviewDisplays.Display> displays = new ArrayList<>(choice.shown() + 12);
        displays.addAll(PreviewDisplays.box(frozen.bounds()));
        BlockPos.Mutable position = new BlockPos.Mutable();
        for (int index : choice.indices()) {
            long packed = frozen.packedPosition(index);
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            BlockState placed = FabricAdapter.adapt(frozen.override(index));
            BlockState before = world.getBlockState(position.set(x, y, z));
            PreviewDisplays.Kind kind = placed.isAir() ? PreviewDisplays.Kind.REMOVE
                    : before.isAir() ? PreviewDisplays.Kind.ADD : PreviewDisplays.Kind.REPLACE;
            displays.add(PreviewDisplays.block(x, y, z, placed, kind));
        }
        Held held = new Held(preparation, frozen, choice, displays, sessionLimit(player));
        ZoneCheck.Watch watch = ZoneCheck.Watch.of(frozen)
                .then(preparation.extras == null ? null : preparation.extras.watch());
        long ttl = KomaEditConfig.settings().previewTimeoutSeconds() * 1_000_000_000L;
        FLOW.hold(preparation.player, held, watch, System.nanoTime(), ttl);
        held.spawnSome(player, SPAWNS_PER_TICK);
        announceHeld(player, held, ttl);
    }

    private static void announceHeld(ServerPlayerEntity player, Held held, long ttl) {
        BuildEstimate estimate = BuildEstimate.of(held.frozen.size(), 0, 0, held.frozen.bounds());
        StringBuilder summary = new StringBuilder("Vista previa de ").append(held.description).append(": ")
                .append(ProgressLine.group(held.frozen.size())).append(" cambios (")
                .append(ProgressLine.group(held.added)).append(" puestos o cambiados, ")
                .append(ProgressLine.group(held.removed)).append(" quitados) · caja ")
                .append(estimate.sizeX()).append('×').append(estimate.sizeY()).append('×').append(estimate.sizeZ())
                .append(" · ~").append(ProgressLine.duration(Math.max(1.0, estimate.seconds()))).append(" al aplicar");
        send(player, Text.literal(summary.toString()).formatted(Formatting.AQUA));
        player.sendMessage(Text.literal("  ")
                .append(button("[Aplicar]", "/komaedit confirm", Formatting.GREEN,
                        "Coloca exactamente lo que ves, sin recalcular"))
                .append(Text.literal("  "))
                .append(button("[Descartar]", "/komaedit cancel", Formatting.RED, "No cambia nada"))
                .append(Text.literal("  caduca en " + ProgressLine.duration(ttl / 1_000_000_000.0))
                        .formatted(Formatting.GRAY)), false);
        if (held.choice.mode() != DisplaySampler.Mode.ALL) {
            String how = held.choice.mode() == DisplaySampler.Mode.SURFACE ? "la superficie" : "una muestra";
            send(player, Text.literal("Se ven " + ProgressLine.group(held.choice.shown()) + " de "
                    + ProgressLine.group(held.frozen.size()) + " cambios (" + how + ") y la caja")
                    .formatted(Formatting.GRAY));
        }
        BuildEstimate.limitWarning(held.frozen.size(), held.sessionLimit).ifPresent(warning ->
                send(player, Text.literal(warning).formatted(Formatting.GOLD)));
        if (!held.freezable) {
            send(player, Text.literal("Hay bloques con datos (NBT): al aplicar se volverá a evaluar el patrón")
                    .formatted(Formatting.GRAY));
        }
        player.sendMessage(Text.literal("KomaEdit · Vista previa lista: /komaedit confirm o /komaedit cancel")
                .formatted(Formatting.YELLOW), true);
    }

    /** Aviso de lo que va a tardar y del límite de WorldEdit cuando un plan se encola sin vista previa. */
    private static void announce(ServerPlayerEntity player, int blocks, String description) {
        double seconds = BuildEstimate.placeSeconds(blocks);
        Optional<String> warning = BuildEstimate.limitWarning(blocks, sessionLimit(player));
        if (seconds >= ANNOUNCE_ABOVE_SECONDS) {
            send(player, Text.literal(description + ": " + ProgressLine.group(blocks) + " bloques · ~"
                    + ProgressLine.duration(seconds) + " · /komaedit cancel para parar").formatted(Formatting.GRAY));
        }
        warning.ifPresent(text -> send(player, Text.literal(text).formatted(Formatting.GOLD)));
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
            this.seen = new LongOpenHashSet(Math.min(plan.size(), 1 << 20));
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
                int current = Block.getRawIdFromState(world.getBlockState(position.set(x, y, z)));
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
                frozen.addConditionalOverride(x, y, z, watched, state);
                if (placed.isAir()) {
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
        private int spawnCursor;
        private boolean hidden;
        private long confirmedAt;

        private Held(Preparation preparation, PackedBlockPlan frozen, DisplaySampler.Choice choice,
                     List<PreviewDisplays.Display> displays, int sessionLimit) {
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
                send(online, Text.literal("Aplicando " + held.description + ": " + ProgressLine.group(
                        held.frozen.size()) + " cambios · /komaedit cancel para parar · //undo para deshacer")
                        .formatted(Formatting.GREEN));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                send(online, Text.literal(held.description + ": " + readable(exception)).formatted(Formatting.RED));
            }
        }

        @Override
        public void changed(UUID player, Held held, long mismatch) {
            ServerPlayerEntity online = online(player);
            if (online == null) {
                return;
            }
            send(online, Text.literal("La zona cambió desde la vista previa (en " + PackedBlockPlan.unpackX(mismatch)
                    + ", " + PackedBlockPlan.unpackY(mismatch) + ", " + PackedBlockPlan.unpackZ(mismatch)
                    + "): recalculando " + held.description + "…").formatted(Formatting.GOLD));
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
                send(online, Text.literal("No se pudo recalcular " + held.description + ": " + readable(exception))
                        .formatted(Formatting.RED));
            }
        }

        @Override
        public void expired(UUID player, Held held) {
            ServerPlayerEntity online = online(player);
            if (online != null) {
                send(online, Text.literal("La vista previa de " + held.description
                        + " caducó: no se ha cambiado nada").formatted(Formatting.YELLOW));
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

    private static void send(ServerPlayerEntity player, Text text) {
        player.sendMessage(Text.literal("KomaEdit · ").formatted(Formatting.GOLD).append(text), false);
    }

    private static MutableText button(String label, String command, Formatting color, String hover) {
        return Text.literal(label).styled(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover))));
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && (cause.getMessage() == null || cause.getMessage().isBlank())) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
