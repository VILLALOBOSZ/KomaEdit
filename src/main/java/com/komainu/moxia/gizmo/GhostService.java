package com.komainu.moxia.gizmo;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.feedback.FeedbackEventPayload;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.preview.DisplaySampler;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.preview.PreviewDisplays;
import com.komainu.moxia.preview.PreviewPlanCodec;
import com.komainu.moxia.preview.PreviewSurface;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.performance.ServerWork;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * El fantasma con gizmo en el servidor. Cuando una orden que coloca un objeto ({@link GhostScope}) llega al motor
 * común y el jugador tiene el fantasma activado, el plan no se encola:
 *
 * <ol>
 *     <li><b>Preparar</b> (repartido entre ticks): se resuelve el bloque final de cada entrada, como en la vista previa,
 *     y queda un {@link GhostModel} alrededor de su pivote.</li>
 *     <li><b>Enseñar</b>: el cliente recibe la caja ({@link GhostPayloads.Open}) y la superficie del objeto en partes;
 *     lo mueve, lo gira y lo voltea él solo, sin hablar con el servidor.</li>
 *     <li><b>Confirmar</b>: llega solo la transformación. El servidor comprueba permisos, altura, borde, distancia y
 *     chunks cargados ({@link GhostValidation}); después lee el destino entre ticks, mira dónde puede construir y
 *     escribe con condición ({@link GhostPlacement.Planner}): si algo cambia mientras se coloca, esa posición no se
 *     pisa. Todo va al motor común, con su barra de progreso y un solo //undo.</li>
 * </ol>
 *
 * Sin el mod en el cliente, o con el fantasma apagado, nada de esto ocurre: la orden coloca como siempre.
 */
public final class GhostService {
    private static final long PREPARE_NANOS_PER_TICK = 4_000_000L;
    private static final long CHECK_NANOS_PER_TICK = 4_000_000L;
    private static final int CHECK_STEP = 4_096;
    /** Más que esto no se mueve: se coloca (o se previsualiza) como siempre. */
    public static final int MAX_BLOCKS = 2_000_000;
    private static final int CLIENT_ENTRIES = 250_000;
    private static final int PARTS_PER_TICK = 2;
    /** Un fantasma olvidado se quita a la media hora sin tocarlo. */
    private static final long IDLE_NANOS = 30L * 60L * 1_000_000_000L;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Map<UUID, GizmoSettings.Mode> MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Preparing> PREPARING = new LinkedHashMap<>();
    private static final Map<UUID, Held> HELD = new LinkedHashMap<>();
    private static final Map<UUID, Checking> CHECKING = new LinkedHashMap<>();
    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit-Ghost");
        thread.setDaemon(true);
        return thread;
    });

    private GhostService() {
    }

    /** Cómo se coloca al confirmar: de serie, con el motor común; las instancias del Studio guardan además su dueño. */
    @FunctionalInterface
    public interface Applier {
        /** @return bloques en cola */
        int apply(ServerPlayerEntity player, Placement placement);
    }

    /**
     * Lo que se coloca: el plan con la transformación ya aplicada, los bloques con datos (NBT) por posición y de dónde
     * salió.
     */
    public record Placement(GhostModel model, GhostTransform transform, PackedBlockPlan plan,
                            Long2ObjectOpenHashMap<BaseBlock> data, String description, boolean copy) {
    }

    // ---------------------------------------------------------------- entrada

    /** El cliente dice cuándo quiere el fantasma. */
    public static void mode(ServerPlayerEntity player, GizmoSettings.Mode mode) {
        MODES.put(player.getUuid(), mode == null ? GizmoSettings.Mode.OFF : mode);
    }

    /** Si el plan de esta petición iría al fantasma (para no preguntar antes por un Build grande). */
    public static boolean wouldTake(ServerPlayerEntity player, EditRequest request) {
        return wants(player, request, GhostScope.movable(request));
    }

    private static boolean wants(ServerPlayerEntity player, EditRequest request, boolean movable) {
        return movable && GhostScope.applies(MODES.get(player.getUuid()), request)
                && player.networkHandler != null && ServerPlayNetworking.canSend(player, GhostPayloads.Open.ID);
    }

    /**
     * Desde {@code PlanPreviews.intercept}: si el plan va al fantasma, se queda aquí y no se encola.
     *
     * @return {@code true} si el plan se coloca con el fantasma
     */
    public static boolean offer(ServerPlayerEntity player, PackedBlockPlan plan, PlanPreviews.PlanBlocks blocks,
                                String description, EditRequest request) {
        if (!wants(player, request, GhostScope.movable(request))) {
            return false;
        }
        return start(player, plan, blocks, description, GhostService::applyDefault, null);
    }

    /**
     * Para lo que no pasa por el motor de siempre (las instancias del Studio): el fantasma con su propia forma de
     * colocarse.
     *
     * @param permission lo que debe seguir pudiendo el jugador al confirmar (además de usar KomaEdit)
     */
    public static boolean offer(ServerPlayerEntity player, PackedBlockPlan plan, PlanPreviews.PlanBlocks blocks,
                                String description, Applier applier, Predicate<ServerPlayerEntity> permission) {
        if (!wants(player, EditRequest.DEFAULT, true)) {
            return false;
        }
        return start(player, plan, blocks, description, applier, permission);
    }

    private static boolean start(ServerPlayerEntity player, PackedBlockPlan plan, PlanPreviews.PlanBlocks blocks,
                                 String description, Applier applier, Predicate<ServerPlayerEntity> permission) {
        if (plan.size() == 0) {
            return false;
        }
        if (plan.size() > MAX_BLOCKS) {
            Feedback.warn(player, Feedback.format("%s tiene %s bloques: demasiado para moverlo con el gizmo; se "
                    + "coloca como siempre", description, plan.size()));
            return false;
        }
        UUID id = player.getUuid();
        discardQuietly(player, false);
        PlanPreviews.cancel(id);
        PREPARING.put(id, new Preparing(id, player.getServerWorld().getRegistryKey(), plan, blocks, description,
                applier, permission));
        return true;
    }

    /** Si {@code plan} es el que el jugador tiene ahora en el fantasma (o preparándose). */
    public static boolean holds(UUID player, PackedBlockPlan plan) {
        Preparing preparing = PREPARING.get(player);
        if (preparing != null) {
            return preparing.plan == plan;
        }
        Held held = HELD.get(player);
        return held != null && held.source == plan;
    }

    public static boolean has(UUID player) {
        return PREPARING.containsKey(player) || HELD.containsKey(player) || CHECKING.containsKey(player);
    }

    /** {@code /komaedit confirm}: el cliente confirma con la posición que tenga ahora. */
    public static boolean requestConfirm(ServerPlayerEntity player) {
        Held held = HELD.get(player.getUuid());
        if (held == null || !ServerPlayNetworking.canSend(player, GhostPayloads.Result.ID)) {
            return false;
        }
        ServerPlayNetworking.send(player, new GhostPayloads.Result(held.id, GhostPayloads.Outcome.CONFIRM, ""));
        return true;
    }

    /**
     * Quita el fantasma del jugador sin colocar nada.
     *
     * @return su descripción, si había uno
     */
    public static Optional<String> cancel(ServerPlayerEntity player) {
        return discardQuietly(player, true);
    }

    /** Al cambiar de dimensión o reaparecer: se quita con un aviso. */
    public static void discard(ServerPlayerEntity player, String reason) {
        discardQuietly(player, true).ifPresent(description -> Feedback.warn(player,
                "Fantasma de " + description + " quitado " + reason + ": no se ha colocado nada"));
    }

    private static Optional<String> discardQuietly(ServerPlayerEntity player, boolean tell) {
        UUID id = player.getUuid();
        Preparing preparing = PREPARING.remove(id);
        Checking checking = CHECKING.remove(id);
        Held held = HELD.remove(id);
        if (held != null && tell) {
            send(player, new GhostPayloads.Result(held.id, GhostPayloads.Outcome.CLOSED, ""));
        }
        if (held != null) {
            return Optional.of(held.description);
        }
        if (checking != null) {
            return Optional.of(checking.held.description);
        }
        return preparing == null ? Optional.empty() : Optional.of(preparing.description);
    }

    public static void forget(UUID player) {
        MODES.remove(player);
        PREPARING.remove(player);
        HELD.remove(player);
        CHECKING.remove(player);
    }

    public static void clearAll() {
        MODES.clear();
        PREPARING.clear();
        HELD.clear();
        CHECKING.clear();
    }

    /** Fase del fantasma para la barra de progreso: preparándolo o comprobando el destino. */
    public static Optional<ProgressLine> progress(UUID player) {
        long now = System.nanoTime();
        Preparing preparing = PREPARING.get(player);
        if (preparing != null) {
            return Optional.of(new ProgressLine(preparing.description, ProgressLine.Phase.PREPARING,
                    preparing.cursor, preparing.plan.size(), now - preparing.startedAt, null));
        }
        Checking checking = CHECKING.get(player);
        if (checking != null) {
            return Optional.of(new ProgressLine(checking.held.description, ProgressLine.Phase.CHECKING,
                    checking.planner.cursor(), checking.planner.total(), now - checking.startedAt, null));
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- red

    /** El jugador confirma (o coloca una copia) con esta transformación. */
    public static void confirm(ServerPlayerEntity player, GhostPayloads.Confirm payload) {
        UUID id = player.getUuid();
        Held held = HELD.get(id);
        if (held == null || held.id != payload.id()) {
            send(player, new GhostPayloads.Result(payload.id(), GhostPayloads.Outcome.CLOSED,
                    "Ese fantasma ya no existe"));
            return;
        }
        held.touch();
        if (CHECKING.containsKey(id)) {
            reject(player, held, payload.copy(), "ya se está colocando");
            return;
        }
        if (!player.getServerWorld().getRegistryKey().equals(held.world)) {
            discard(player, "al cambiar de dimensión");
            return;
        }
        if (!KomaPermissions.canAccess(player) || held.permission != null && !held.permission.test(player)) {
            reject(player, held, payload.copy(), "ya no tienes permiso para colocarlo");
            return;
        }
        if (BatchEditService.status(id).isPresent()) {
            reject(player, held, payload.copy(), "espera a que termine la edición en curso (/komaedit cancel la para)");
            return;
        }
        if (payload.orientation() < 0 || payload.orientation() >= Orientation.COUNT) {
            reject(player, held, payload.copy(), "giro desconocido");
            return;
        }
        GhostTransform transform = new GhostTransform(Orientation.of(payload.orientation()), payload.x(), payload.y(),
                payload.z());
        ServerWorld world = player.getServerWorld();
        BlockBounds bounds;
        try {
            bounds = transform.bounds(held.model.bounds());
        } catch (IllegalArgumentException exception) {
            reject(player, held, payload.copy(), "posición fuera del mundo");
            return;
        }
        boolean admitted = GhostStates.admitsAll(held.model.palette(), transform.orientation());
        Optional<String> problem = GhostValidation.check(bounds, player.getX(), player.getY(), player.getZ(),
                admitted, validationWorld(world));
        if (problem.isPresent()) {
            reject(player, held, payload.copy(), "no se puede colocar ahí: " + problem.get());
            return;
        }
        GhostOptions options = GhostOptions.fromBits(payload.options());
        GhostPlacement.Planner planner = new GhostPlacement.Planner(held.model, transform, options,
                GhostStates.INSTANCE, placementWorld(player, world));
        CHECKING.put(id, new Checking(held, planner, transform, payload.copy()));
        if (held.model.size() > 50_000) {
            Feedback.status(player, "Comprobando el destino de " + held.description + "…");
        }
    }

    /** El jugador quita el fantasma. */
    public static void cancel(ServerPlayerEntity player, GhostPayloads.Cancel payload) {
        Held held = HELD.get(player.getUuid());
        if (held != null && held.id == payload.id()) {
            discardQuietly(player, false);
        }
    }

    private static void reject(ServerPlayerEntity player, Held held, boolean copy, String reason) {
        send(player, new GhostPayloads.Result(held.id, GhostPayloads.Outcome.REJECTED, capitalized(reason)));
        Feedback.warn(player, held.description + ": " + reason);
    }

    private static void send(ServerPlayerEntity player, GhostPayloads.Result result) {
        if (player.networkHandler != null && ServerPlayNetworking.canSend(player, GhostPayloads.Result.ID)) {
            ServerPlayNetworking.send(player, result);
        }
    }

    // ---------------------------------------------------------------- tick

    public static void tick(MinecraftServer server) {
        if (PREPARING.isEmpty() && HELD.isEmpty() && CHECKING.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        prepare(server, ServerWork.deadline(PREPARE_NANOS_PER_TICK));
        sendParts(server);
        check(server, ServerWork.deadline(CHECK_NANOS_PER_TICK));
        expire(server, start);
    }

    private static void prepare(MinecraftServer server, long deadline) {
        for (Preparing preparing : List.copyOf(PREPARING.values())) {
            if (System.nanoTime() >= deadline) {
                return;
            }
            if (preparing.encoding) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(preparing.player);
            if (player == null || !player.getServerWorld().getRegistryKey().equals(preparing.world)) {
                PREPARING.remove(preparing.player);
                continue;
            }
            try {
                if (preparing.step(deadline)) {
                    encode(server, preparing);
                }
            } catch (RuntimeException exception) {
                PREPARING.remove(preparing.player);
                KomaEditMod.LOGGER.warn("KomaEdit ghost preparation failed for {}", preparing.player, exception);
                Feedback.error(player, "No se pudo preparar el fantasma de " + preparing.description + ": "
                        + readable(exception));
            }
        }
    }

    /** En otro hilo: la superficie que dibuja el cliente, en partes. */
    private static void encode(MinecraftServer server, Preparing preparing) {
        GhostModel model;
        try {
            model = preparing.model.build();
        } catch (IllegalStateException exception) {
            PREPARING.remove(preparing.player);
            return;
        }
        if (model.airCount() == model.size()) {
            PREPARING.remove(preparing.player);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(preparing.player);
            if (player != null) {
                Feedback.warn(player, preparing.description + ": solo hay aire, nada que mover");
            }
            return;
        }
        Int2ObjectOpenHashMap<BaseBlock> data = preparing.data(model);
        preparing.encoding = true;
        int id = IDS.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
        ENCODER.execute(() -> {
            try {
                List<byte[]> parts = surface(id, model);
                server.execute(() -> show(server, preparing, model, data, id, parts));
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.warn("Could not encode the KomaEdit ghost", exception);
                server.execute(() -> PREPARING.remove(preparing.player, preparing));
            }
        });
    }

    /** La superficie del objeto (sin el aire) en el formato de la vista previa, en posiciones locales. */
    static List<byte[]> surface(int id, GhostModel model) {
        LongArrayList solid = new LongArrayList(model.size());
        IntArrayList states = new IntArrayList(model.size());
        for (int index = 0; index < model.size(); index++) {
            if (!model.isAir(index)) {
                solid.add(model.position(index));
                states.add(model.state(index));
            }
        }
        long[] positions = solid.toLongArray();
        PreviewSurface.Selection selection = PreviewSurface.select(positions, positions.length, CLIENT_ENTRIES);
        int count = selection.shown();
        long[] shown = new long[count];
        int[] shownStates = new int[count];
        byte[] kinds = new byte[count];
        for (int slot = 0; slot < count; slot++) {
            int index = selection.indices()[slot];
            shown[slot] = positions[index];
            shownStates[slot] = states.getInt(index);
            kinds[slot] = (byte) PreviewDisplays.Kind.REPLACE.ordinal();
        }
        BlockBounds bounds = model.bounds();
        PreviewPlanCodec.Header header = new PreviewPlanCodec.Header(id, bounds.minimumX(), bounds.minimumY(),
                bounds.minimumZ(), bounds.maximumX(), bounds.maximumY(), bounds.maximumZ(), positions.length,
                positions.length, model.airCount(), count == positions.length ? DisplaySampler.Mode.ALL
                : selection.mode(), count);
        return PreviewPlanCodec.encode(header, shown, shownStates, kinds, selection.faces(),
                PreviewPlanCodec.DEFAULT_PART_BYTES);
    }

    private static void show(MinecraftServer server, Preparing preparing, GhostModel model,
                             Int2ObjectOpenHashMap<BaseBlock> data, int id, List<byte[]> parts) {
        if (!PREPARING.remove(preparing.player, preparing)) {
            return;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(preparing.player);
        if (player == null || !player.getServerWorld().getRegistryKey().equals(preparing.world)) {
            return;
        }
        Held held = new Held(id, preparing, model, data, parts);
        HELD.put(preparing.player, held);
        GhostOptions options = GhostOptions.DEFAULT;
        ServerPlayNetworking.send(player, new GhostPayloads.Open(id, preparing.description, model.pivotX(),
                model.pivotY(), model.pivotZ(), model.bounds(), model.size(), model.airCount(), options.bits(),
                model.palette()));
        held.sendSome(player);
    }

    private static void sendParts(MinecraftServer server) {
        for (Held held : HELD.values()) {
            if (held.cursor < held.parts.size()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(held.player);
                if (player != null) {
                    held.sendSome(player);
                }
            }
        }
    }

    private static void check(MinecraftServer server, long deadline) {
        for (Checking checking : List.copyOf(CHECKING.values())) {
            UUID id = checking.held.player;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null) {
                CHECKING.remove(id);
                continue;
            }
            if (!player.getServerWorld().getRegistryKey().equals(checking.held.world)) {
                CHECKING.remove(id);
                discard(player, "al cambiar de dimensión");
                continue;
            }
            boolean done = false;
            while (!done && System.nanoTime() < deadline) {
                done = checking.planner.step(CHECK_STEP);
            }
            if (done) {
                CHECKING.remove(id);
                finish(player, checking);
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
        }
    }

    private static void finish(ServerPlayerEntity player, Checking checking) {
        Held held = checking.held;
        GhostPlacement.Planner planner = checking.planner;
        if (planner.blocked()) {
            long at = planner.blockedAt();
            reject(player, held, checking.copy, "no puedes construir en " + PackedBlockPlan.unpackX(at) + ", "
                    + PackedBlockPlan.unpackY(at) + ", " + PackedBlockPlan.unpackZ(at) + " (zona protegida)");
            return;
        }
        GhostPlacement.Writes writes = planner.writes();
        if (writes.size() == 0) {
            reject(player, held, checking.copy, writes.kept() > 0
                    ? "ahí ya está todo ocupado (conservar lo que hay está encendido)"
                    : "ahí no hay nada que cambiar");
            return;
        }
        Placement placement = placement(held, checking.transform, writes, checking.copy);
        int queued;
        try {
            queued = held.applier.apply(player, placement);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            reject(player, held, checking.copy, readable(exception));
            return;
        }
        if (!checking.copy) {
            HELD.remove(held.player, held);
        }
        String kept = writes.kept() > 0 ? Feedback.format(" · %s conservados", writes.kept()).getString() : "";
        String text = Feedback.format("%s: %s bloques en cola", held.description, queued).getString() + kept
                + " · //undo";
        send(player, new GhostPayloads.Result(held.id, checking.copy ? GhostPayloads.Outcome.COPY_PLACED
                : GhostPayloads.Outcome.PLACED, text));
        Feedback.status(player, text);
        Feedback.sound(player, FeedbackEventPayload.Kind.CONFIRM);
    }

    /** El plan del destino: cada escritura con la condición de lo que había al comprobar. */
    private static Placement placement(Held held, GhostTransform transform, GhostPlacement.Writes writes,
                                       boolean copy) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(writes.size());
        Int2ObjectOpenHashMap<com.sk89q.worldedit.world.block.BlockState> states = new Int2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<BaseBlock> data = new Long2ObjectOpenHashMap<>();
        for (int index = 0; index < writes.size(); index++) {
            long packed = writes.positions()[index];
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            int stateId = writes.states()[index];
            com.sk89q.worldedit.world.block.BlockState state = states.computeIfAbsent(stateId,
                    ignored -> FabricAdapter.adapt(Block.getStateFromRawId(stateId)));
            int expected = writes.expected()[index];
            int capture = writes.captures()[index];
            BaseBlock withData = held.data.get(writes.entries()[index]);
            BlockVector3 position = BlockVector3.at(x, y, z);
            if (withData != null) {
                data.put(packed, state.toBaseBlock(withData.getNbtReference()));
                if (capture != PackedBlockPlan.NO_CAPTURE) {
                    builder.addConditionalCapturedPattern(position, expected, capture);
                } else {
                    builder.addConditionalPattern(position, expected);
                }
            } else if (capture != PackedBlockPlan.NO_CAPTURE) {
                builder.addConditionalCapturedOverride(position, expected, state, capture);
            } else {
                builder.addConditionalOverride(x, y, z, expected, state);
            }
        }
        return new Placement(held.model, transform, builder.build(), data, held.description, copy);
    }

    /** De serie: el motor común, ya confirmado (sin otra vista previa), con su barra y un solo //undo. */
    private static int applyDefault(ServerPlayerEntity player, Placement placement) {
        return EditRequests.within(EditRequest.CONFIRMED, () -> {
            if (placement.data().isEmpty()) {
                return BatchEditService.submit(player, placement.plan(), "air", placement.description());
            }
            Long2ObjectOpenHashMap<BaseBlock> data = placement.data();
            return BatchEditService.submitStyled(player, placement.plan(),
                    position -> data.get(PackedBlockPlan.pack(position.x(), position.y(), position.z())),
                    placement.description());
        });
    }

    private static void expire(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Held>> iterator = HELD.entrySet().iterator();
        while (iterator.hasNext()) {
            Held held = iterator.next().getValue();
            if (now - held.touchedAt > IDLE_NANOS && !CHECKING.containsKey(held.player)) {
                iterator.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(held.player);
                if (player != null) {
                    send(player, new GhostPayloads.Result(held.id, GhostPayloads.Outcome.CLOSED,
                            "El fantasma caducó tras media hora sin usarse"));
                }
            }
        }
    }

    // ---------------------------------------------------------------- el mundo

    private static GhostValidation.World validationWorld(ServerWorld world) {
        return new GhostValidation.World() {
            @Override
            public int bottomY() {
                return world.getBottomY();
            }

            @Override
            public int topY() {
                return world.getTopY();
            }

            @Override
            public boolean insideBorder(int x, int z) {
                return world.getWorldBorder().contains(new BlockPos(x, 0, z));
            }

            @Override
            public boolean chunkLoaded(int chunkX, int chunkZ) {
                return world.isChunkLoaded(chunkX, chunkZ);
            }
        };
    }

    private static GhostPlacement.World placementWorld(ServerPlayerEntity player, ServerWorld world) {
        BlockPos.Mutable position = new BlockPos.Mutable();
        return new GhostPlacement.World() {
            @Override
            public int stateAt(int x, int y, int z) {
                return Block.getRawIdFromState(world.getBlockState(position.set(x, y, z)));
            }

            @Override
            public boolean isAir(int state) {
                return Block.getStateFromRawId(state).isAir();
            }

            @Override
            public boolean canBuild(int x, int y, int z) {
                return world.canPlayerModifyAt(player, position.set(x, y, z));
            }
        };
    }

    // ---------------------------------------------------------------- estado

    /** Un fantasma en preparación: resuelve el bloque final de cada entrada del plan. */
    private static final class Preparing {
        private final UUID player;
        private final RegistryKey<World> world;
        private final PackedBlockPlan plan;
        private final PlanPreviews.PlanBlocks blocks;
        private final String description;
        private final Applier applier;
        private final Predicate<ServerPlayerEntity> permission;
        private final GhostModel.Builder model;
        /** Bloques con datos (NBT), por posición del mundo. */
        private final Long2ObjectOpenHashMap<BaseBlock> data = new Long2ObjectOpenHashMap<>();
        private final long startedAt = System.nanoTime();
        private int cursor;
        private boolean encoding;

        private Preparing(UUID player, RegistryKey<World> world, PackedBlockPlan plan, PlanPreviews.PlanBlocks blocks,
                          String description, Applier applier, Predicate<ServerPlayerEntity> permission) {
            this.player = player;
            this.world = world;
            this.plan = plan;
            this.blocks = blocks;
            this.description = description;
            this.applier = applier;
            this.permission = permission;
            this.model = GhostModel.builder(plan.size());
        }

        /** @return {@code true} cuando ha recorrido todo el plan */
        private boolean step(long deadline) {
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
                BlockState state = FabricAdapter.adapt(block.toImmutableState());
                model.add(x, y, z, Block.getRawIdFromState(state), state.isAir(), plan.captureIndex(index));
                if (block.getNbtReference() != null) {
                    data.put(packed, block);
                } else {
                    data.remove(packed);
                }
            }
            return true;
        }

        /** Los bloques con datos por entrada del modelo. */
        private Int2ObjectOpenHashMap<BaseBlock> data(GhostModel built) {
            Int2ObjectOpenHashMap<BaseBlock> result = new Int2ObjectOpenHashMap<>();
            if (data.isEmpty()) {
                return result;
            }
            for (int index = 0; index < built.size(); index++) {
                long local = built.position(index);
                long world = PackedBlockPlan.pack(built.pivotX() + PackedBlockPlan.unpackX(local),
                        built.pivotY() + PackedBlockPlan.unpackY(local), built.pivotZ() + PackedBlockPlan.unpackZ(local));
                BaseBlock block = data.get(world);
                if (block != null) {
                    result.put(index, block);
                }
            }
            return result;
        }
    }

    /** Un fantasma en el cliente: el modelo entero y cómo colocarlo. */
    private static final class Held {
        private final int id;
        private final UUID player;
        private final RegistryKey<World> world;
        private final PackedBlockPlan source;
        private final String description;
        private final Applier applier;
        private final Predicate<ServerPlayerEntity> permission;
        private final GhostModel model;
        private final Int2ObjectOpenHashMap<BaseBlock> data;
        private final List<byte[]> parts;
        private int cursor;
        private long touchedAt = System.nanoTime();

        private Held(int id, Preparing preparing, GhostModel model, Int2ObjectOpenHashMap<BaseBlock> data,
                     List<byte[]> parts) {
            this.id = id;
            this.player = preparing.player;
            this.world = preparing.world;
            this.source = preparing.plan;
            this.description = preparing.description;
            this.applier = preparing.applier;
            this.permission = preparing.permission;
            this.model = model;
            this.data = data;
            this.parts = List.copyOf(parts);
        }

        private void touch() {
            touchedAt = System.nanoTime();
        }

        private void sendSome(ServerPlayerEntity player) {
            int end = Math.min(parts.size(), cursor + PARTS_PER_TICK);
            while (cursor < end) {
                int part = cursor++;
                ServerPlayNetworking.send(player, new GhostPayloads.Part(id, part, parts.size(), parts.get(part)));
            }
        }
    }

    /** Una confirmación: se lee el destino entre ticks antes de encolar. */
    private static final class Checking {
        private final Held held;
        private final GhostPlacement.Planner planner;
        private final GhostTransform transform;
        private final boolean copy;
        private final long startedAt = System.nanoTime();

        private Checking(Held held, GhostPlacement.Planner planner, GhostTransform transform, boolean copy) {
            this.held = held;
            this.planner = planner;
            this.transform = transform;
            this.copy = copy;
        }
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && (cause.getMessage() == null || cause.getMessage().isBlank())) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
