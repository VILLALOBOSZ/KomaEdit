package com.komainu.moxia.rebuild;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.blueprint.BlueprintLibrary;
import com.komainu.moxia.blueprint.BlueprintPermissions;
import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintService;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.EditLimits;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.PlacementRun;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.feedback.FeedbackEventPayload;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.performance.ServerWork;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.studio.net.StudioAim;
import com.komainu.moxia.worldedit.EditHistory;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * La reconstrucción animada en el juego, como la «Animated Rebuild» de Axiom: quita una obra (la selección, el
 * portapapeles o un plano en un punto) y la vuelve a levantar bloque a bloque a la vista de todos.
 *
 * <ol>
 *     <li><b>Leer</b> (en el hilo del servidor, repartido entre ticks con el presupuesto común): cada posición con lo
 *     que debe quedar y lo que había, con sus datos (cofres, carteles).</li>
 *     <li><b>Ordenar y guardar</b> (en otro hilo): el orden de aparición, el ritmo y el archivo de recuperación en
 *     {@code <mundo>/komaedit/rebuilds/}, antes de vaciar nada.</li>
 *     <li><b>Animar</b>: es un {@link ServerWork.Lane}; cada tick pone los bloques a los que les toca, por ventanas
 *     ({@link PlacementRun}) dentro de su parte del presupuesto. Escribe con una sesión de WorldEdit propia, sin
 *     vecinos, sin la llamada al colocarse ni validación: la arena no cae, las antorchas no saltan y el agua no corre
 *     mientras la obra está a medias.</li>
 * </ol>
 *
 * <p><b>Reinicios.</b> Si el servidor se para con normalidad, cada reconstrucción en marcha termina al momento (lo que
 * se estaba cancelando, se deja como estaba) antes de que se guarde el mundo. Si se cae, al arrancar se encuentra el
 * archivo y la obra se termina posición a posición sin pisar lo que alguien haya cambiado
 * ({@link RebuildRecovery}).</p>
 */
public final class RebuildService {
    /** Sin vecinos, sin «al colocarse» y sin validar: nada se cae ni se rompe con la obra a medias. */
    private static final SideEffectSet SIDE_EFFECTS = SideEffectSet.defaults()
            .with(SideEffect.NEIGHBORS, SideEffect.State.OFF)
            .with(SideEffect.UPDATE, SideEffect.State.OFF)
            .with(SideEffect.VALIDATION, SideEffect.State.OFF);
    /**
     * El aire de WorldEdit, pedido al usarlo: esta clase se carga en {@code onInitialize} (al registrar sus
     * eventos), antes de que WorldEdit tenga sus registros, y una constante con {@code BlockTypes} tumbaba el arranque.
     */
    private static BaseBlock worldEditAir() {
        return BlockTypes.AIR.getDefaultState().toBaseBlock();
    }
    private static final double REACH = 128.0;
    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();
    private static final Map<UUID, Recovery> RECOVERIES = new LinkedHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit reconstrucción");
        thread.setDaemon(true);
        return thread;
    });
    private static MinecraftServer server;
    private static boolean stopping;
    /** Lo que dejó a medias una caída se lee en el primer tick (ver {@link #started}). */
    private static boolean recoverPending;

    private RebuildService() {
    }

    /** De dónde sale la obra. */
    public enum Source {
        SELECTION("la selección"),
        CLIPBOARD("el portapapeles"),
        BLUEPRINT("el plano");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private enum State {
        READING,
        ORDERING,
        RUNNING
    }

    /** Se apunta como trabajo pesado de {@link ServerWork}. */
    public static void register() {
        ServerWork.lane(new ServerWork.Lane() {
            @Override
            public void owners(Collection<UUID> into) {
                if (recoverPending && server != null) {
                    recoverPending = false;
                    recover(server);
                }
                for (Job job : JOBS.values()) {
                    boolean paused = ServerWork.paused(job.owner);
                    if (!paused) {
                        job.tick();
                    }
                    if (job.wantsWork() && (!paused || job.flushing())) {
                        into.add(job.owner);
                    }
                }
                for (Recovery recovery : RECOVERIES.values()) {
                    into.add(recovery.key);
                }
            }

            @Override
            public boolean step(MinecraftServer ticking, UUID owner, long deadline) {
                Recovery recovery = RECOVERIES.get(owner);
                if (recovery != null) {
                    return recovery.step(deadline);
                }
                Job job = JOBS.get(owner);
                return job != null && job.step(deadline);
            }
        });
    }

    // ------------------------------------------------------------------ Empezar

    /**
     * Empieza una reconstrucción. Lo que tarde (leer, ordenar) llega después por el chat y la barra.
     *
     * @param blueprint el plano, con {@link Source#BLUEPRINT}
     * @return el mensaje de «leyendo»
     */
    public static String start(ServerPlayerEntity player, Source source, String blueprint, String arguments) {
        KomaPermissions.require(player, KomaPermissions.REBUILD);
        UUID owner = player.getUuid();
        if (JOBS.containsKey(owner)) {
            throw new IllegalStateException("Ya tienes una reconstrucción en marcha: /komaedit rebuild estado, "
                    + "pausar, terminar o cancelar");
        }
        if (RECOVERIES.values().stream().anyMatch(recovery -> recovery.owner.equals(owner))) {
            throw new IllegalStateException("Se está terminando tu reconstrucción que quedó a medias por un reinicio;"
                    + " espera a que acabe");
        }
        BlockPos feet = player.getBlockPos();
        RebuildSettings settings = RebuildSettings.parse(arguments, new int[]{feet.getX(), feet.getY(), feet.getZ()});
        if (settings.order() == RebuildOrder.RANDOM && settings.seed() == null) {
            settings = settings.withSeed(new SplittableRandom().nextInt(1_000_000));
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        ServerWorld world = player.getServerWorld();
        Iterator<BlockVector3> positions;
        Clipboard clipboard = null;
        BlockVector3 offset = BlockVector3.ZERO;
        long volume;
        String what;
        switch (source) {
            case SELECTION -> {
                Region region;
                try {
                    region = context.session().getSelection(context.world());
                } catch (IncompleteRegionException exception) {
                    throw new IllegalArgumentException("Selecciona primero la obra con //wand o //pos1 y //pos2");
                }
                volume = region.getVolume();
                positions = region.iterator();
                what = "la selección";
                if (settings.location() != null) {
                    throw new IllegalArgumentException("-l solo vale con el portapapeles o un plano");
                }
            }
            case CLIPBOARD -> {
                ClipboardHolder holder;
                try {
                    holder = context.session().getClipboard();
                } catch (EmptyClipboardException exception) {
                    throw new IllegalArgumentException("El portapapeles está vacío: copia algo con //copy");
                }
                Transform transform = holder.getTransform();
                try {
                    clipboard = transform.isIdentity() ? holder.getClipboard()
                            : holder.getClipboard().transform(transform);
                } catch (WorldEditException exception) {
                    throw new IllegalArgumentException("No se pudo girar el portapapeles: " + exception.getMessage());
                }
                BlockVector3 to = settings.location() == null ? BlockVector3.at(feet.getX(), feet.getY(), feet.getZ())
                        : BlockVector3.at(settings.location()[0], settings.location()[1], settings.location()[2]);
                offset = to.subtract(clipboard.getOrigin());
                volume = clipboard.getRegion().getVolume();
                positions = clipboard.getRegion().iterator();
                what = "el portapapeles";
            }
            case BLUEPRINT -> {
                KomaPermissions.require(player, BlueprintPermissions.LOAD);
                if (blueprint == null || blueprint.isBlank()) {
                    throw new IllegalArgumentException("Falta el plano: /komaedit rebuild plano <nombre>");
                }
                BlueprintLibrary.Entry entry = BlueprintService.library().require(owner, BlueprintRef.parse(blueprint));
                clipboard = BlueprintService.clipboard(entry);
                BlockVector3 minimum = clipboard.getRegion().getMinimumPoint();
                BlockVector3 maximum = clipboard.getRegion().getMaximumPoint();
                BlockVector3 base = BlockVector3.at(Math.floorDiv(minimum.x() + maximum.x(), 2), minimum.y(),
                        Math.floorDiv(minimum.z() + maximum.z(), 2));
                int[] point = settings.location() != null ? settings.location() : lookedAtSurface(player);
                offset = BlockVector3.at(point[0], point[1], point[2]).subtract(base);
                volume = clipboard.getRegion().getVolume();
                positions = clipboard.getRegion().iterator();
                what = "el plano " + entry.reference();
            }
            default -> throw new IllegalStateException();
        }
        EditLimits.check(EditLimits.Kind.CAPTURE, volume, RegionOperationService.MAX_CAPTURE_BLOCKS)
                .ifPresent(excess -> {
                    throw new IllegalArgumentException(excess.message(capitalized(what), false));
                });
        String description = "Reconstrucción de " + what + " (" + settings.order().description() + ")";
        Job job = new Job(player, source, settings, world, context.session(), positions, clipboard, offset,
                description, volume);
        JOBS.put(owner, job);
        return "Leyendo " + what + " para reconstruirla " + settings.order().description() + "…";
    }

    /** Donde va la base de un plano: encima del bloque que miras (o del clic del Studio), o a tus pies. */
    private static int[] lookedAtSurface(ServerPlayerEntity player) {
        HitResult result = StudioAim.target(player, REACH);
        if (result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK) {
            BlockPos above = hit.getBlockPos().up();
            return new int[]{above.getX(), above.getY(), above.getZ()};
        }
        BlockPos feet = player.getBlockPos();
        return new int[]{feet.getX(), feet.getY(), feet.getZ()};
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ------------------------------------------------------------------ Mandos

    public static boolean active(UUID owner) {
        return JOBS.containsKey(owner);
    }

    /** Pone o quita la pausa. */
    public static String pause(UUID owner, boolean pause) {
        Job job = require(owner);
        if (!job.pause(pause)) {
            throw new IllegalStateException(pause ? "La reconstrucción ya está en pausa o terminando"
                    : "La reconstrucción no estaba en pausa");
        }
        return pause ? "Reconstrucción en pausa: /komaedit rebuild seguir la reanuda" : "Reconstrucción reanudada";
    }

    /** Cambia la velocidad: {@code factor} absoluto, o multiplica la actual si {@code relative}. */
    public static String speed(UUID owner, double factor, boolean relative) {
        Job job = require(owner);
        double value = relative ? job.speed() * factor : factor;
        value = Math.clamp(value, RebuildRun.MIN_SPEED, RebuildRun.MAX_SPEED);
        job.speed(value);
        return "Velocidad de la reconstrucción: ×" + speedText(value);
    }

    /** Pone todo lo que falta ya, sin animación. */
    public static String finish(UUID owner) {
        Job job = require(owner);
        if (!job.skipToEnd()) {
            throw new IllegalStateException("La reconstrucción ya está terminando o deshaciéndose");
        }
        return "Saltando al final: se pone lo que falta lo antes que deje el servidor";
    }

    /**
     * Deja todo como estaba. Antes de vaciar nada, solo se olvida; a medias, se deshace (en la selección, vuelve a
     * poner la obra entera al momento).
     *
     * @return lo que se paró, o vacío si no había reconstrucción
     */
    public static Optional<String> cancel(UUID owner) {
        Job job = JOBS.get(owner);
        if (job == null) {
            return Optional.empty();
        }
        return Optional.of(job.cancel());
    }

    /** Al desconectarse quien la lanzó: se termina sola (si aún leía, se olvida). */
    public static void disconnected(UUID owner) {
        Job job = JOBS.get(owner);
        if (job == null) {
            return;
        }
        if (job.state == State.READING) {
            JOBS.remove(owner);
            return;
        }
        job.skipToEnd();
    }

    private static Job require(UUID owner) {
        Job job = JOBS.get(owner);
        if (job == null) {
            throw new IllegalStateException("No tienes ninguna reconstrucción en marcha");
        }
        return job;
    }

    static String speedText(double value) {
        if (value < 1.0) {
            long inverse = Math.round(1.0 / value);
            if (Math.abs(1.0 / inverse - value) < 1e-9) {
                return "1/" + inverse;
            }
        }
        return ProgressLine.decimal(Math.round(value * 100.0) / 100.0);
    }

    // ------------------------------------------------------------------ Estado

    /** La línea de la barra de progreso del jugador. */
    public static Optional<ProgressLine> progress(UUID owner) {
        for (Recovery recovery : RECOVERIES.values()) {
            if (recovery.owner.equals(owner)) {
                return Optional.of(recovery.progress());
            }
        }
        Job job = JOBS.get(owner);
        return job == null ? Optional.empty() : Optional.of(job.progress());
    }

    /** {@code /komaedit rebuild estado}. */
    public static List<Text> status(UUID owner) {
        Job job = require(owner);
        return job.status();
    }

    // ------------------------------------------------------------------ Servidor

    /**
     * Lo que dejó a medias una caída se lee en el primer tick, no aquí: Fabric inicia los mods por orden de id, así
     * que este {@code SERVER_STARTED} corre antes que el de WorldEdit, que es el que llena sus registros. Leerlo aquí
     * dejaba todas las constantes de {@code BlockTypes} a {@code null} para el resto del proceso y la obra no se
     * recuperaba nunca (el archivo se quedaba y volvía a pasar en cada arranque).
     */
    public static void started(MinecraftServer started) {
        server = started;
        stopping = false;
        recoverPending = true;
    }

    /**
     * El servidor se para: lo que está levantándose se termina ya (y lo que se cancelaba se deja como estaba), sin
     * presupuesto, antes de que se guarde el mundo; lo que aún leía u ordenaba se olvida, sin haber tocado nada.
     */
    public static void stopping() {
        stopping = true;
        waitForWorker();
        for (Recovery recovery : List.copyOf(RECOVERIES.values())) {
            recovery.complete();
        }
        RECOVERIES.clear();
        for (Job job : List.copyOf(JOBS.values())) {
            try {
                job.complete();
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.error("Could not finish the rebuild of {} while stopping", job.owner, exception);
            }
        }
        JOBS.clear();
    }

    public static void clearAll() {
        JOBS.clear();
        RECOVERIES.clear();
        server = null;
        recoverPending = false;
    }

    private static void waitForWorker() {
        try {
            WORKER.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException exception) {
            KomaEditMod.LOGGER.warn("KomaEdit rebuild worker still busy while stopping: {}", exception.getMessage());
        }
    }

    private static Path directory() {
        return server.getSavePath(WorldSavePath.ROOT).resolve("komaedit").resolve("rebuilds");
    }

    private static Path restoreMarker(Path file) {
        String name = file.getFileName().toString();
        return file.resolveSibling(name.substring(0, name.length() - ".bin".length()) + ".restore");
    }

    private static void delete(Path file, boolean now) {
        if (file == null) {
            return;
        }
        Runnable delete = () -> {
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(AtomicStorage.backup(file));
                Files.deleteIfExists(AtomicStorage.temporary(file));
                Files.deleteIfExists(restoreMarker(file));
            } catch (IOException exception) {
                KomaEditMod.LOGGER.warn("Could not delete the rebuild file {}: {}", file, exception.getMessage());
            }
        };
        if (now) {
            delete.run();
        } else {
            WORKER.execute(delete);
        }
    }

    // ------------------------------------------------------------------ Una reconstrucción

    private static final class Job {
        private static final int READ_CHECK = 64;
        private final UUID owner;
        private final String id = UUID.randomUUID().toString();
        private final Source source;
        private final RebuildSettings settings;
        private final ServerWorld world;
        private final LocalSession session;
        private final Iterator<BlockVector3> positions;
        private final Clipboard clipboard;
        private final BlockVector3 offset;
        private final String description;
        private final long volume;
        private final RebuildModel.Builder builder;
        private final List<BaseBlock> blocks = new ArrayList<>();
        private final Int2IntOpenHashMap paletteByRaw = new Int2IntOpenHashMap();
        private final BlockPos.Mutable cursor = new BlockPos.Mutable();
        private final long startedAt = System.nanoTime();
        private State state = State.READING;
        private long read;
        private int denied;
        private boolean history;
        private boolean cancelRequested;
        private boolean skipRequested;
        private boolean pauseRequested;
        private double requestedSpeed = 1.0;
        private RebuildModel model;
        private EditSession editSession;
        private RebuildRun run;
        private PlacementRun placement;
        private Path file;
        private long runningSince;
        private int effectsLeft;

        private Job(ServerPlayerEntity player, Source source, RebuildSettings settings, ServerWorld world,
                    LocalSession session, Iterator<BlockVector3> positions, Clipboard clipboard, BlockVector3 offset,
                    String description, long volume) {
            this.owner = player.getUuid();
            this.source = source;
            this.settings = settings;
            this.world = world;
            this.session = session;
            this.positions = positions;
            this.clipboard = clipboard;
            this.offset = offset;
            this.description = description;
            this.volume = volume;
            this.builder = RebuildModel.builder(source == Source.SELECTION)
                    .airRaw(Block.getRawIdFromState(Blocks.AIR.getDefaultState()));
            this.paletteByRaw.defaultReturnValue(-1);
            this.history = settings.order() == RebuildOrder.HISTORY && source != Source.BLUEPRINT;
        }

        private void tick() {
            effectsLeft = RebuildConfig.settings().effectsPerTick();
            if (run != null) {
                run.tick();
            }
        }

        private boolean wantsWork() {
            return switch (state) {
                case READING -> true;
                case ORDERING -> false;
                case RUNNING -> placement.flushing() || run.hasWorkNow() || run.done();
            };
        }

        private boolean flushing() {
            return placement != null && placement.flushing();
        }

        private boolean step(long deadline) {
            try {
                return switch (state) {
                    case READING -> read(deadline);
                    case ORDERING -> false;
                    case RUNNING -> animate(deadline);
                };
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.error("Rebuild failed for {}", owner, exception);
                fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                return false;
            }
        }

        // -------------------------------------------------------------- Leer

        private boolean read(long deadline) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
            if (player == null) {
                JOBS.remove(owner);
                return false;
            }
            int count = 0;
            while (positions.hasNext() && (count++ % READ_CHECK != 0 || System.nanoTime() < deadline)) {
                BlockVector3 from = positions.next();
                read++;
                int x = from.x() + offset.x();
                int y = from.y() + offset.y();
                int z = from.z() + offset.z();
                cursor.set(x, y, z);
                if (y < world.getBottomY() || y >= world.getTopY() || !world.canPlayerModifyAt(player, cursor)) {
                    denied++;
                    continue;
                }
                BlockState current = world.getBlockState(cursor);
                long packed = PackedBlockPlan.pack(x, y, z);
                if (source == Source.SELECTION) {
                    if (current.isAir()) {
                        continue;
                    }
                    int palette = worldPalette(current, x, y, z);
                    builder.add(packed, palette, palette, rank(x, y, z));
                } else {
                    BaseBlock target = clipboard.getFullBlock(from);
                    boolean targetAir = target.getBlockType().getMaterial().isAir();
                    if (targetAir && settings.skipAir()) {
                        continue;
                    }
                    int before = current.isAir() ? RebuildModel.AIR : worldPalette(current, x, y, z);
                    int after = targetAir ? RebuildModel.AIR : blockPalette(target);
                    if (after == RebuildModel.AIR && before == RebuildModel.AIR) {
                        continue;
                    }
                    builder.add(packed, after, before, source == Source.CLIPBOARD
                            ? rank(from.x(), from.y(), from.z()) : RebuildModel.NO_HISTORY);
                }
            }
            if (positions.hasNext()) {
                return true;
            }
            prepare(player);
            return false;
        }

        private long rank(int x, int y, int z) {
            return history ? PlacementHistory.rank(world, x, y, z) : RebuildModel.NO_HISTORY;
        }

        /** Lo que hay en el mundo: los bloques con datos (cofres, carteles…) van cada uno con los suyos. */
        private int worldPalette(BlockState state, int x, int y, int z) {
            int raw = Block.getRawIdFromState(state);
            if (state.hasBlockEntity()) {
                BaseBlock full = FabricAdapter.adapt(world).getFullBlock(BlockVector3.at(x, y, z));
                return add(raw, RebuildBlocks.traits(state), full);
            }
            int known = paletteByRaw.get(raw);
            if (known >= 0) {
                return known;
            }
            int palette = add(raw, RebuildBlocks.traits(state), FabricAdapter.adapt(state).toBaseBlock());
            paletteByRaw.put(raw, palette);
            return palette;
        }

        /** Un bloque del portapapeles o del plano. */
        private int blockPalette(BaseBlock block) {
            BlockState state = RebuildBlocks.state(block);
            int raw = Block.getRawIdFromState(state);
            if (block.getNbtReference() != null) {
                return add(raw, RebuildBlocks.traits(state), block);
            }
            int known = paletteByRaw.get(raw);
            if (known >= 0) {
                return known;
            }
            int palette = add(raw, RebuildBlocks.traits(state), block.toImmutableState().toBaseBlock());
            paletteByRaw.put(raw, palette);
            return palette;
        }

        private int add(int raw, BlockTraits traits, BaseBlock block) {
            int palette = builder.palette(raw, traits);
            while (blocks.size() < palette) {
                blocks.add(worldEditAir());
            }
            blocks.add(block);
            return palette;
        }

        // -------------------------------------------------------------- Ordenar y guardar

        private void prepare(ServerPlayerEntity player) {
            model = builder.build();
            if (model.buildable() == 0) {
                JOBS.remove(owner);
                Feedback.warn(player, denied > 0
                        ? "No hay nada que reconstruir donde puedes construir (" + ProgressLine.group(denied)
                        + " bloques fuera de tu alcance)"
                        : "No hay nada que reconstruir: " + (source == Source.SELECTION ? "la selección" : "la obra")
                        + " está vacía");
                return;
            }
            try {
                OperationPolicy.validate(player, model.buildable(), 0);
                EditLimits.check(EditLimits.Kind.WORLDEDIT, model.buildable(), session.getBlockChangeLimit())
                        .ifPresent(excess -> {
                            throw new IllegalArgumentException(excess.message("La reconstrucción", false));
                        });
            } catch (IllegalArgumentException exception) {
                JOBS.remove(owner);
                Feedback.error(player, exception.getMessage());
                return;
            }
            state = State.ORDERING;
            RebuildModel built = model;
            List<BaseBlock> palette = List.copyOf(blocks);
            String dimension = world.getRegistryKey().getValue().toString();
            Path target = directory().resolve(id + ".bin");
            file = target;
            long seed = settings.seed() == null ? 0L : settings.seed();
            int[] center = settings.center() != null ? settings.center() : waveCenter(player, built);
            RebuildSequence.Options options = new RebuildSequence.Options(settings.axis(), center, seed);
            WORKER.execute(() -> {
                try {
                    RebuildSequence sequence = RebuildSequence.of(built, settings.order(), options);
                    RebuildTimeline timeline = RebuildTimeline.of(built, sequence, settings.pace());
                    List<String> texts = new ArrayList<>(palette.size());
                    for (BaseBlock block : palette) {
                        texts.add(RebuildBlocks.text(block));
                    }
                    writeSnapshot(target, RebuildRecovery.Snapshot.of(dimension, owner, description, built, texts));
                    MinecraftServer current = server;
                    if (current != null) {
                        current.execute(() -> ready(sequence, timeline, target));
                    }
                } catch (IOException | RuntimeException exception) {
                    KomaEditMod.LOGGER.error("Could not prepare the rebuild of {}", owner, exception);
                    MinecraftServer current = server;
                    if (current != null) {
                        current.execute(() -> {
                            delete(target, false);
                            fail("no se pudo guardar el archivo de recuperación (" + exception.getMessage() + ")");
                        });
                    }
                }
            });
        }

        /** Sin {@code -c}: el bloque que miras si está en la obra; si no, el centro de su base. */
        private static int[] waveCenter(ServerPlayerEntity player, RebuildModel model) {
            HitResult result = StudioAim.target(player, REACH);
            if (result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = hit.getBlockPos();
                if (pos.getX() >= model.minX() - 1 && pos.getX() <= model.maxX() + 1
                        && pos.getY() >= model.minY() - 1 && pos.getY() <= model.maxY() + 1
                        && pos.getZ() >= model.minZ() - 1 && pos.getZ() <= model.maxZ() + 1) {
                    return new int[]{pos.getX(), pos.getY(), pos.getZ()};
                }
            }
            return null;
        }

        private void ready(RebuildSequence sequence, RebuildTimeline timeline, Path written) {
            if (stopping || JOBS.get(owner) != this) {
                delete(written, false);
                return;
            }
            file = written;
            if (cancelRequested) {
                JOBS.remove(owner);
                delete(file, false);
                return;
            }
            editSession = WorldEdit.getInstance().newEditSessionBuilder().world(FabricAdapter.adapt(world))
                    .maxBlocks(-1).build();
            editSession.setSideEffectApplier(SIDE_EFFECTS);
            // Reconstruir la selección deja lo mismo que había: no hay nada que deshacer y no se guarda historial.
            editSession.setTrackingHistory(source != Source.SELECTION);
            run = new RebuildRun(model, sequence, timeline, new WorldAccess(this), System::nanoTime);
            run.speed(requestedSpeed);
            placement = new PlacementRun(new PlacementRun.Source() {
                @Override
                public int place(int maxEntries, long deadline) throws MaxChangedBlocksException {
                    return run.place(maxEntries, deadline);
                }

                @Override
                public boolean done() {
                    return run.done();
                }
            }, BatchEditService.session(editSession), System::nanoTime, this::effects);
            state = State.RUNNING;
            runningSince = System.nanoTime();
            if (skipRequested) {
                run.skipToEnd();
            } else if (pauseRequested) {
                run.pause(true);
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
            if (player != null) {
                announceStart(player, sequence, timeline);
            }
        }

        private void announceStart(ServerPlayerEntity player, RebuildSequence sequence, RebuildTimeline timeline) {
            double seconds = timeline.end() / (double) RebuildTimeline.UNIT / 20.0;
            MutableText text = Feedback.format("%s: %s bloques en ~%s", description, model.buildable(),
                    ProgressLine.duration(seconds));
            if (sequence.fellBack()) {
                text.append(Text.literal(" · no hay historial de colocación de esta obra: va por capas")
                        .formatted(Formatting.YELLOW));
            }
            if (settings.order() == RebuildOrder.RANDOM && settings.seed() != null) {
                text.append(Text.literal(" · semilla " + settings.seed()).formatted(Formatting.GRAY));
            }
            if (denied > 0) {
                text.append(Feedback.format(" · %s bloques fuera de tu alcance se dejan como están", denied));
            }
            text.append(" ").append(Feedback.button("[pausar]", "/komaedit rebuild pausar", Formatting.YELLOW,
                            "Para la animación donde está; /komaedit rebuild seguir la reanuda"))
                    .append(" ").append(Feedback.button("[×2]", "/komaedit rebuild acelerar", Formatting.AQUA,
                            "Dobla la velocidad"))
                    .append(" ").append(Feedback.button("[al final]", "/komaedit rebuild terminar", Formatting.GREEN,
                            "Pone lo que falta ya, sin animación"))
                    .append(" ").append(Feedback.button("[cancelar]", "/komaedit rebuild cancelar", Formatting.RED,
                            "Lo deja todo como estaba"));
            Feedback.info(player, text);
        }

        // -------------------------------------------------------------- Animar

        private boolean animate(long deadline) {
            try {
                placement.step(deadline, !ServerWork.paused(owner));
            } catch (MaxChangedBlocksException exception) {
                // La sesión no tiene límite; si aun así salta, se termina sin animación lo que se pueda.
                throw new IllegalStateException("WorldEdit paró la reconstrucción: " + exception.getMessage(),
                        exception);
            }
            if (run.done() && !placement.flushing()) {
                finish(run.phase() == RebuildRun.Phase.DONE, false);
                return false;
            }
            return placement.flushing() || run.hasWorkNow();
        }

        /** Al acabar cada ventana, ya en el mundo: unas pocas partículas y sonidos, repartidos entre lo puesto. */
        private void effects() {
            IntArrayList placed = run.drainPlaced();
            if (placed.isEmpty() || !settings.particles() && !settings.sound() || effectsLeft <= 0) {
                return;
            }
            int shown = Math.min(effectsLeft, placed.size());
            effectsLeft -= shown;
            for (int index = 0; index < shown; index++) {
                int entry = placed.getInt((int) ((long) index * placed.size() / shown));
                BlockPos pos = new BlockPos(model.x(entry), model.y(entry), model.z(entry));
                BlockState state = world.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                if (settings.particles()) {
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5,
                            pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
                }
                if (settings.sound()) {
                    BlockSoundGroup sounds = state.getSoundGroup();
                    world.playSound(null, pos, sounds.getPlaceSound(), SoundCategory.BLOCKS,
                            (sounds.getVolume() + 1.0F) / 2.0F * 0.35F, sounds.getPitch() * 0.8F);
                }
            }
        }

        /** Sin presupuesto, al parar el servidor: termina lo que se levantaba y deja como estaba lo que se cancelaba. */
        private void complete() {
            if (state != State.RUNNING) {
                delete(file, true);
                return;
            }
            if (run.phase() != RebuildRun.Phase.RESTORING) {
                run.skipToEnd();
            }
            int guard = 0;
            try {
                while ((!run.done() || placement.flushing()) && guard++ < 1_000_000) {
                    placement.step(Long.MAX_VALUE, true);
                }
            } catch (MaxChangedBlocksException exception) {
                KomaEditMod.LOGGER.error("Rebuild of {} stopped by WorldEdit while stopping", owner, exception);
            }
            finish(run.phase() == RebuildRun.Phase.DONE, true);
        }

        private void finish(boolean done, boolean now) {
            JOBS.remove(owner);
            try {
                editSession.close();
            } finally {
                if (done && source != Source.SELECTION && editSession.size() > 0) {
                    EditHistory.label(editSession, description);
                    session.remember(editSession);
                }
            }
            delete(file, now);
            ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(owner);
            if (player == null) {
                return;
            }
            Text kept = run.kept() > 0 ? Feedback.format(" · %s bloques cambiados mientras tanto se dejan como están",
                    run.kept()) : Text.empty();
            if (done) {
                Feedback.ok(player, Feedback.format("%s terminada: %s bloques", description, model.buildable())
                        .append(kept).append(source == Source.SELECTION ? Text.empty() : Text.literal(" · //undo")));
                if (System.nanoTime() - startedAt >= 3_000_000_000L) {
                    Feedback.sound(player, FeedbackEventPayload.Kind.DONE);
                }
            } else {
                Feedback.warn(player, Feedback.format("%s cancelada: todo queda como estaba", description)
                        .append(kept));
            }
        }

        private void fail(String reason) {
            JOBS.remove(owner);
            if (editSession != null) {
                editSession.close();
            }
            boolean touched = run != null && run.writes() > 0;
            String after = " · no se cambió nada";
            if (touched) {
                // Ya tocó el mundo: se termina (o se deshace) sin animación con lo guardado, como tras una caída. Si
                // ni eso se puede, el archivo se queda y se termina al reiniciar.
                after = " · se termina sin animación";
                if (run.phase() == RebuildRun.Phase.RESTORING) {
                    markRestoreNow();
                }
                try {
                    Recovery recovery = Recovery.open(server, file);
                    if (recovery == null) {
                        after = " · se terminará al reiniciar el servidor";
                    } else {
                        RECOVERIES.put(recovery.key, recovery);
                    }
                } catch (IOException | RuntimeException exception) {
                    KomaEditMod.LOGGER.error("Rebuild {} of {} stopped half way; it will be finished on restart", id,
                            owner, exception);
                    after = " · se terminará al reiniciar el servidor";
                }
            } else {
                delete(file, false);
            }
            ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(owner);
            if (player != null) {
                Feedback.error(player, description + " falló: " + reason + after);
            }
        }

        private void markRestoreNow() {
            try {
                Files.writeString(restoreMarker(file), "restore");
            } catch (IOException exception) {
                KomaEditMod.LOGGER.warn("Could not write the rebuild restore marker for {}: {}", file,
                        exception.getMessage());
            }
        }

        // -------------------------------------------------------------- Mandos

        private boolean pause(boolean pause) {
            if (state != State.RUNNING) {
                if (pauseRequested == pause) {
                    return false;
                }
                pauseRequested = pause;
                return true;
            }
            return run.pause(pause);
        }

        private double speed() {
            return run == null ? requestedSpeed : run.speed();
        }

        private void speed(double value) {
            requestedSpeed = value;
            if (run != null) {
                run.speed(value);
            }
        }

        private boolean skipToEnd() {
            if (state != State.RUNNING) {
                if (skipRequested) {
                    return false;
                }
                skipRequested = true;
                return true;
            }
            return run.skipToEnd();
        }

        private String cancel() {
            switch (state) {
                case READING -> {
                    JOBS.remove(owner);
                    return "reconstrucción (aún leía; no se tocó nada)";
                }
                case ORDERING -> {
                    cancelRequested = true;
                    return "reconstrucción (aún ordenaba; no se tocó nada)";
                }
                default -> {
                    if (!run.cancel()) {
                        return "reconstrucción (ya acababa)";
                    }
                    markRestore();
                    return source == Source.SELECTION ? "reconstrucción (la obra vuelve a estar entera)"
                            : "reconstrucción (se deja todo como estaba)";
                }
            }
        }

        /** Si se cae el servidor deshaciendo, al arrancar se termina de deshacer en vez de acabar la obra. */
        private void markRestore() {
            Path marker = restoreMarker(file);
            WORKER.execute(() -> {
                try {
                    Files.writeString(marker, "restore");
                } catch (IOException exception) {
                    KomaEditMod.LOGGER.warn("Could not write the rebuild restore marker {}: {}", marker,
                            exception.getMessage());
                }
            });
        }

        // -------------------------------------------------------------- Estado

        private ProgressLine progress() {
            long now = System.nanoTime();
            return switch (state) {
                case READING -> new ProgressLine(description, ProgressLine.Phase.READING, read, volume,
                        now - startedAt, "/komaedit rebuild cancelar");
                case ORDERING -> new ProgressLine(description, ProgressLine.Phase.CALCULATING, 0, 0,
                        now - startedAt, "ordenando y guardando la recuperación");
                case RUNNING -> running(now);
            };
        }

        private ProgressLine running(long now) {
            if (run.paused() || ServerWork.paused(owner)) {
                return new ProgressLine(description, ProgressLine.Phase.PAUSED, run.progressDone(),
                        run.progressTotal(), now - runningSince, ServerWork.paused(owner)
                        ? "en pausa por un admin" : "en pausa · /komaedit rebuild seguir");
            }
            StringBuilder extra = new StringBuilder(run.phase().label());
            String group = run.phase() == RebuildRun.Phase.BUILDING ? run.groupLabel() : "";
            if (!group.isEmpty()) {
                extra.append(" · ").append(group);
            }
            if (run.speed() != 1.0) {
                extra.append(" · ×").append(speedText(run.speed()));
            }
            boolean animated = run.phase() == RebuildRun.Phase.BUILDING;
            double remaining = run.remainingTicks() / 20.0;
            if (animated && remaining >= 1.0) {
                extra.append(" · queda ~").append(ProgressLine.duration(remaining));
            }
            // Levantando, lo que queda lo dice el ritmo pedido, no el medido (que cuenta la espera y las pausas): sin
            // tiempo transcurrido la línea no calcula el suyo.
            return new ProgressLine(description, ProgressLine.Phase.PLACING, run.progressDone(), run.progressTotal(),
                    animated ? 0L : now - runningSince, extra.toString());
        }

        private List<Text> status() {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.literal(description).formatted(Formatting.YELLOW));
            if (state != State.RUNNING) {
                lines.add(Text.literal("  " + (state == State.READING ? "Leyendo: " + ProgressLine.group(read) + " de "
                        + ProgressLine.group(volume) : "Ordenando y guardando la recuperación")
                        + (pauseRequested ? " · empezará en pausa" : "")).formatted(Formatting.GRAY));
                return lines;
            }
            lines.add(Text.literal("  " + capitalized(run.phase().label()) + " · " + ProgressLine.group(run.step())
                    + " de " + ProgressLine.group(run.sequence().size()) + " bloques · velocidad ×"
                    + speedText(run.speed()) + (run.paused() ? " · en pausa" : "")).formatted(Formatting.GRAY));
            String group = run.groupLabel();
            double remaining = run.remainingTicks() / 20.0;
            lines.add(Text.literal("  " + (group.isEmpty() ? "" : "Ahora: " + group + " · ")
                    + (remaining >= 1.0 ? "queda ~" + ProgressLine.duration(remaining) : "casi lista")
                    + (run.slowTicks() > 0 ? " · el servidor no dio para el ritmo pedido en "
                    + ProgressLine.group(run.slowTicks()) + " ticks" : "")
                    + (run.kept() > 0 ? " · " + ProgressLine.group(run.kept()) + " bloques cambiados mientras tanto"
                    : "")).formatted(Formatting.GRAY));
            return lines;
        }
    }

    /** El mundo de una reconstrucción y su sesión de WorldEdit, para {@link RebuildRun}. */
    private record WorldAccess(Job job) implements RebuildRun.World {
        @Override
        public int rawAt(long position) {
            return Block.getRawIdFromState(job.world.getBlockState(job.cursor.set(PackedBlockPlan.unpackX(position),
                    PackedBlockPlan.unpackY(position), PackedBlockPlan.unpackZ(position))));
        }

        @Override
        public boolean air(int raw) {
            return Block.getStateFromRawId(raw).isAir();
        }

        @Override
        public void write(long position, int palette) throws MaxChangedBlocksException {
            job.editSession.setBlock(BlockVector3.at(PackedBlockPlan.unpackX(position),
                    PackedBlockPlan.unpackY(position), PackedBlockPlan.unpackZ(position)),
                    palette == RebuildModel.AIR ? worldEditAir() : job.blocks.get(palette));
        }
    }

    private static void writeSnapshot(Path target, RebuildRecovery.Snapshot snapshot) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = AtomicStorage.temporary(target);
        try (OutputStream output = Files.newOutputStream(temporary)) {
            RebuildRecovery.write(snapshot, output);
        }
        AtomicStorage.replace(temporary, target);
    }

    // ------------------------------------------------------------------ Recuperar tras una caída

    private static void recover(MinecraftServer started) {
        Path folder = directory();
        if (!Files.isDirectory(folder)) {
            return;
        }
        try {
            AtomicStorage.recoverTree(folder);
        } catch (IOException exception) {
            KomaEditMod.LOGGER.warn("Could not tidy the rebuild folder: {}", exception.getMessage());
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.bin")) {
            for (Path file : files) {
                try {
                    Recovery recovery = Recovery.open(started, file);
                    if (recovery != null) {
                        RECOVERIES.put(recovery.key, recovery);
                    }
                } catch (IOException | RuntimeException exception) {
                    KomaEditMod.LOGGER.error("Could not read the interrupted rebuild {}; it stays on disk", file,
                            exception);
                }
            }
        } catch (IOException exception) {
            KomaEditMod.LOGGER.error("Could not list interrupted rebuilds", exception);
        }
        if (!RECOVERIES.isEmpty()) {
            KomaEditMod.LOGGER.info("KomaEdit found {} rebuild(s) interrupted by a crash; finishing them",
                    RECOVERIES.size());
        }
    }

    /** Una reconstrucción que dejó a medias una caída del servidor. */
    private static final class Recovery {
        private final UUID key = UUID.randomUUID();
        private final UUID owner;
        private final Path file;
        private final ServerWorld world;
        private final BaseBlock[] blocks;
        private final RebuildRecovery.Run run;
        private final EditSession editSession;
        private final PlacementRun placement;
        private final BlockPos.Mutable cursor = new BlockPos.Mutable();
        private final long startedAt = System.nanoTime();
        private int unknown;

        private Recovery(Path file, ServerWorld world, RebuildRecovery.Snapshot snapshot, BaseBlock[] blocks,
                         int[] raw, boolean restore) {
            this.owner = snapshot.owner();
            this.file = file;
            this.world = world;
            this.blocks = blocks;
            this.run = new RebuildRecovery.Run(snapshot, raw, restore);
            this.editSession = WorldEdit.getInstance().newEditSessionBuilder().world(FabricAdapter.adapt(world))
                    .maxBlocks(-1).build();
            editSession.setSideEffectApplier(SIDE_EFFECTS);
            editSession.setTrackingHistory(false);
            RebuildRun.World access = new RebuildRun.World() {
                @Override
                public int rawAt(long position) {
                    return Block.getRawIdFromState(world.getBlockState(cursor.set(PackedBlockPlan.unpackX(position),
                            PackedBlockPlan.unpackY(position), PackedBlockPlan.unpackZ(position))));
                }

                @Override
                public boolean air(int rawId) {
                    return Block.getStateFromRawId(rawId).isAir();
                }

                @Override
                public void write(long position, int palette) throws MaxChangedBlocksException {
                    BaseBlock block = palette == RebuildModel.AIR ? worldEditAir() : blocks[palette];
                    if (block == null) {
                        unknown++;
                        return;
                    }
                    editSession.setBlock(BlockVector3.at(PackedBlockPlan.unpackX(position),
                            PackedBlockPlan.unpackY(position), PackedBlockPlan.unpackZ(position)), block);
                }
            };
            this.placement = new PlacementRun(new PlacementRun.Source() {
                @Override
                public int place(int maxEntries, long deadline) throws MaxChangedBlocksException {
                    return run.step(access, maxEntries, deadline, System::nanoTime);
                }

                @Override
                public boolean done() {
                    return run.done();
                }
            }, BatchEditService.session(editSession), System::nanoTime, null);
        }

        private static Recovery open(MinecraftServer started, Path file) throws IOException {
            RebuildRecovery.Snapshot snapshot;
            try (InputStream input = Files.newInputStream(file)) {
                snapshot = RebuildRecovery.read(input);
            }
            ServerWorld world = started.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                    Identifier.of(snapshot.dimension())));
            if (world == null) {
                KomaEditMod.LOGGER.warn("The interrupted rebuild {} is for {}, which is not loaded; it stays on disk",
                        file, snapshot.dimension());
                return null;
            }
            BaseBlock[] blocks = new BaseBlock[snapshot.palette().size()];
            int[] raw = new int[blocks.length];
            for (int index = 0; index < blocks.length; index++) {
                BaseBlock block = index == RebuildModel.AIR ? worldEditAir() : RebuildBlocks.parse(snapshot.palette().get(index));
                blocks[index] = block;
                raw[index] = block == null ? -1 : RebuildBlocks.raw(block);
            }
            boolean restore = Files.exists(restoreMarker(file));
            return new Recovery(file, world, snapshot, blocks, raw, restore);
        }

        private boolean step(long deadline) {
            try {
                placement.step(deadline, true);
            } catch (MaxChangedBlocksException | RuntimeException exception) {
                KomaEditMod.LOGGER.error("Could not finish the interrupted rebuild {}", file, exception);
                RECOVERIES.remove(key);
                editSession.close();
                return false;
            }
            if (placement.finished()) {
                done(false);
                return false;
            }
            return true;
        }

        private void complete() {
            try {
                while (!placement.finished()) {
                    placement.step(Long.MAX_VALUE, true);
                }
            } catch (MaxChangedBlocksException | RuntimeException exception) {
                KomaEditMod.LOGGER.error("Could not finish the interrupted rebuild {} while stopping", file, exception);
                editSession.close();
                return;
            }
            done(true);
        }

        private void done(boolean now) {
            RECOVERIES.remove(key);
            editSession.close();
            delete(file, now);
            String what = run.restore() ? "deshecha" : "terminada";
            KomaEditMod.LOGGER.info("Interrupted rebuild {} {}: {} blocks written, {} kept, {} unknown", file,
                    run.restore() ? "restored" : "finished", run.written(), run.kept(), unknown);
            ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(owner);
            if (player != null) {
                Feedback.info(player, Feedback.format("%s, que se quedó a medias por un reinicio, ya está %s: %s"
                        + " bloques puestos", run.snapshot().description(), what, run.written()));
            }
        }

        private ProgressLine progress() {
            return new ProgressLine(run.snapshot().description() + " (tras un reinicio)",
                    ProgressLine.Phase.PLACING, run.processed(), run.snapshot().size(),
                    System.nanoTime() - startedAt, run.restore() ? "deshaciendo" : "terminando");
        }
    }
}
