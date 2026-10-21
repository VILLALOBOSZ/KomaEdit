package com.komainu.moxia.region;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.EditLimits;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.performance.ServerWork;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.preview.ZoneCheck;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
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
import net.minecraft.world.LightType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *
 * La {@link EditRequest} de la orden (por ejemplo {@code -v}) se recoge al empezar y acompaña a la captura y al
 * cálculo; con vista previa, el plan espera en {@link PlanPreviews}, que sabe recalcular la operación sobre la misma
 * selección si el mundo cambia antes de confirmar. La lectura informa de su progreso con {@link #progress}.
 */
public final class RegionOperationService {
    public static final long MAX_CAPTURE_BLOCKS = 16_000_000L;
    /** Cambios como mucho de una operación sobre la selección (sin límite de bloques, la foto entera). */
    public static final int MAX_CHANGES = 1_500_000;
    /** Hasta este volumen de foto, la vista previa vigila también toda la selección (no solo los cambios). */
    private static final long WATCH_SNAPSHOT_UP_TO = 2_000_000L;
    private static final int BLOCKS_PER_SLICE = 65_536;
    /**
     * Lo que se calculó sobre la foto de un mundo no se aplica en otro: si el jugador cambia de dimensión antes de
     * que termine, se descarta con este aviso.
     */
    public static final String OTHER_WORLD = "cambiaste de mundo antes de que terminara; no se ha cambiado nada";
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
        submit(player, padding, null, description, operation);
    }

    /**
     * Igual que {@link #submit(ServerPlayerEntity, int, String, RegionOperation)}, pero la foto cubre además la
     * caja {@code extra} ({@code {minX, minY, minZ, maxX, maxY, maxZ}}): ahí la operación puede escribir con
     * {@link RegionEdits#setInCapture} aunque quede lejos de la selección (proyecciones, giros…).
     */
    public static void submit(
            ServerPlayerEntity player,
            int padding,
            int[] extra,
            String description,
            RegionOperation operation
    ) throws IncompleteRegionException {
        start(player, WorldEditService.selection(player).clone(), padding, extra, description, operation, null);
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
        start(player, region.clone(), padding, null, description, null, onCaptured);
    }

    private static void start(
            ServerPlayerEntity player,
            Region selection,
            int padding,
            int[] extra,
            String description,
            RegionOperation operation,
            Consumer<RegionSnapshot> onCaptured
    ) {
        ServerWorld world = player.getServerWorld();
        BlockVector3 minimum = selection.getMinimumPoint();
        BlockVector3 maximum = selection.getMaximumPoint();
        int margin = Math.clamp(padding, 0, 32);
        int lowX = minimum.x() - margin;
        int lowY = minimum.y() - margin;
        int lowZ = minimum.z() - margin;
        int highX = maximum.x() + margin;
        int highY = maximum.y() + margin;
        int highZ = maximum.z() + margin;
        if (extra != null) {
            if (extra.length != 6) {
                throw new IllegalArgumentException("La caja extra necesita seis coordenadas");
            }
            lowX = Math.min(lowX, extra[0]);
            lowY = Math.min(lowY, extra[1]);
            lowZ = Math.min(lowZ, extra[2]);
            highX = Math.max(highX, extra[3]);
            highY = Math.max(highY, extra[4]);
            highZ = Math.max(highZ, extra[5]);
        }
        int minimumY = Math.max(world.getBottomY(), lowY);
        int maximumY = Math.min(world.getTopY() - 1, highY);
        int minimumX = lowX;
        int minimumZ = lowZ;
        long spanX = (long) highX - minimumX + 1;
        long spanZ = (long) highZ - minimumZ + 1;
        if (spanX > MAX_CAPTURE_BLOCKS || spanZ > MAX_CAPTURE_BLOCKS) {
            throw new IllegalArgumentException("La zona a leer es demasiado grande para esta operación");
        }
        int sizeX = (int) spanX;
        int sizeY = maximumY - minimumY + 1;
        int sizeZ = (int) spanZ;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (sizeY <= 0) {
            throw new IllegalArgumentException("La selección está fuera de la altura del mundo");
        }
        checkLimits(player, selection, description, volume, volume - selection.getVolume());
        UUID playerId = player.getUuid();
        if (JOBS.containsKey(playerId)) {
            throw new IllegalStateException("Ya hay una operación leyendo tu selección; espera a que termine");
        }
        JOBS.put(playerId, new CaptureJob(playerId, world, selection, description, operation, onCaptured,
                sizeX, sizeY, sizeZ, minimumX, minimumY, minimumZ, EditRequests.current(), padding, extra));
        double seconds = BuildEstimate.readSeconds(volume);
        QuietMode.status(player, message(description + ": leyendo " + ProgressLine.group(volume) + " bloques"
                + (seconds >= 2.0 ? " (~" + ProgressLine.duration(seconds) + ")" : "") + "…", Formatting.GRAY));
    }

    /**
     * Antes de leer nada: la selección cabe en el límite del servidor y en lo que KomaEdit lee de una vez. Si no,
     * dice cuánto ocupa, cuál es el límite y en cuántos trozos partirla; si la selección es una caja, deja los
     * trozos preparados para {@code /komaedit trozo}.
     */
    private static void checkLimits(ServerPlayerEntity player, Region selection, String description, long volume,
                                    long margin) {
        long selected = selection.getVolume();
        Optional<EditLimits.Excess> excess = EditLimits.check(EditLimits.Kind.SERVER, selected,
                OperationPolicy.limit(player));
        long readLimit = MAX_CAPTURE_BLOCKS - Math.max(0L, margin);
        if (excess.isEmpty() && volume > MAX_CAPTURE_BLOCKS) {
            excess = EditLimits.check(EditLimits.Kind.CAPTURE, selected, Math.max(1L, readLimit));
        }
        if (excess.isEmpty()) {
            return;
        }
        EditLimits.Excess found = excess.get();
        boolean parts = false;
        if (selection instanceof CuboidRegion && readLimit > 0) {
            BlockVector3 low = selection.getMinimumPoint();
            BlockVector3 high = selection.getMaximumPoint();
            try {
                List<BlockBounds> pieces = EditLimits.split(new BlockBounds(low.x(), low.y(), low.z(), high.x(),
                        high.y(), high.z()), Math.min(found.limit(), readLimit));
                SelectionParts.propose(player.getUuid(), player.getServerWorld().getRegistryKey().getValue()
                        .toString(), description, pieces);
                parts = true;
            } catch (IllegalArgumentException ignored) {
                // Ni una capa cabe en el límite: solo se dice el tamaño y el límite.
            }
        }
        throw new IllegalArgumentException(description + ": " + found.message("la selección", parts));
    }

    /** Se apunta como trabajo pesado de {@link ServerWork}: la lectura se reparte por turnos con lo demás. */
    public static void register() {
        ServerWork.lane(new ServerWork.Lane() {
            @Override
            public void owners(Collection<UUID> into) {
                for (UUID owner : JOBS.keySet()) {
                    if (!ServerWork.paused(owner)) {
                        into.add(owner);
                    }
                }
            }

            @Override
            public boolean step(MinecraftServer server, UUID owner, long deadline) {
                CaptureJob job = JOBS.get(owner);
                if (job == null || ServerWork.paused(owner)) {
                    return false;
                }
                return RegionOperationService.step(server, job, deadline);
            }
        });
    }

    /** Lectura en curso del jugador, para la barra de progreso. */
    public static Optional<ProgressLine> progress(UUID playerId) {
        CaptureJob job = JOBS.get(playerId);
        if (job == null) {
            return Optional.empty();
        }
        if (ServerWork.paused(playerId)) {
            return Optional.of(new ProgressLine(job.description, ProgressLine.Phase.PAUSED, job.reader.read(),
                    job.reader.total(), System.nanoTime() - job.startedAt, "lectura en pausa por un admin"));
        }
        return Optional.of(new ProgressLine(job.description, ProgressLine.Phase.READING, job.reader.read(),
                job.reader.total(), System.nanoTime() - job.startedAt, null));
    }

    public static boolean cancel(UUID playerId) {
        return JOBS.remove(playerId) != null;
    }

    public static void clearAll() {
        JOBS.clear();
    }

    /** Un turno de lectura de {@code job}; al acabar, calcula (o entrega la foto). */
    private static boolean step(MinecraftServer server, CaptureJob job, long deadline) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.playerId);
        if (player == null) {
            JOBS.remove(job.playerId);
            return false;
        }
        try {
            if (!job.reader.step(BLOCKS_PER_SLICE, deadline, System::nanoTime)) {
                return true;
            }
            JOBS.remove(job.playerId);
            // Quien recibe la foto (onCaptured) decide al terminar; las operaciones se descartan ya.
            if (job.onCaptured == null && player.getServerWorld() != job.world) {
                player.sendMessage(message(job.description + ": " + OTHER_WORLD, Formatting.YELLOW), false);
                return false;
            }
            RegionSnapshot snapshot = job.reader.build();
            // La petición de la orden (vista previa…) vuelve a estar activa para lo que salga de la foto.
            EditRequests.within(job.request, () -> {
                if (job.onCaptured != null) {
                    job.onCaptured.accept(snapshot);
                } else {
                    compute(server, player, job, snapshot);
                }
            });
        } catch (RuntimeException exception) {
            JOBS.remove(job.playerId);
            player.sendMessage(message(job.description + ": " + readable(exception), Formatting.RED), false);
        }
        return false;
    }

    private static void compute(
            MinecraftServer server,
            ServerPlayerEntity player,
            CaptureJob job,
            RegionSnapshot snapshot
    ) {
        String description = job.description;
        RegionOperation operation = job.operation;
        UUID playerId = player.getUuid();
        QuietMode.status(player, message(description + ": calculando fuera del hilo del servidor…", Formatting.GRAY));
        int limit = changeLimit(player, snapshot);
        AsyncEngine.submit(
                playerId,
                description,
                () -> {
                    RegionEdits edits = new RegionEdits(snapshot, limit);
                    operation.apply(snapshot, edits);
                    // El plan se monta (y se comprime si es grande) aquí, no en el hilo del servidor.
                    edits.seal();
                    return edits;
                },
                edits -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    if (current.getServerWorld() != job.world) {
                        current.sendMessage(message(description + ": " + OTHER_WORLD, Formatting.YELLOW), false);
                        return;
                    }
                    if (edits.changes() == 0) {
                        current.sendMessage(message(description + ": no había nada que cambiar", Formatting.YELLOW),
                                false);
                        return;
                    }
                    try {
                        PackedBlockPlan plan = edits.plan();
                        PlanPreviews.Extras extras = new PlanPreviews.Extras(
                                () -> recompute(server, job), watch(snapshot));
                        int queued = PlanPreviews.withExtras(extras,
                                () -> BatchEditService.submit(current, plan, "air", description));
                        if (!PlanPreviews.holds(playerId, plan)) {
                            QuietMode.status(current, message(description + ": " + ProgressLine.group(queued)
                                    + " bloques en cola · //undo para deshacer", Formatting.GREEN));
                        }
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

    /**
     * Cambios que puede producir la operación: el límite del servidor, o la foto entera si el jugador no tiene
     * límite (el plan grande va comprimido, ya no hace falta cortarlo en {@value #MAX_CHANGES}).
     */
    private static int changeLimit(ServerPlayerEntity player, RegionSnapshot snapshot) {
        long limit = OperationPolicy.limit(player);
        if (limit == Long.MAX_VALUE) {
            return (int) Math.min(Integer.MAX_VALUE - 8, Math.max(MAX_CHANGES, snapshot.volume()));
        }
        return (int) Math.min(Integer.MAX_VALUE - 8, Math.max(1L, limit));
    }

    /** Vuelve a lanzar la operación sobre la misma selección (la vista previa vio que el mundo cambió). */
    private static void recompute(MinecraftServer server, CaptureJob job) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.playerId);
        if (player == null) {
            return;
        }
        start(player, job.selection.clone(), job.padding, job.extra, job.description, job.operation, null);
    }

    /**
     * La selección entera de la foto, para que la vista previa note cualquier cambio donde actúa la operación (no
     * solo en los bloques que cambia). El margen no se vigila: ahí la nieve o un cultivo que crece harían recalcular
     * sin motivo.
     */
    private static ZoneCheck.Watch watch(RegionSnapshot snapshot) {
        if (snapshot.volume() > WATCH_SNAPSHOT_UP_TO) {
            return null;
        }
        int sizeX = snapshot.maximumX() - snapshot.minimumX() + 1;
        int sizeZ = snapshot.maximumZ() - snapshot.minimumZ() + 1;
        long layer = (long) sizeX * sizeZ;
        int size = (int) snapshot.volume();
        return new ZoneCheck.Watch() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public long position(int index) {
                int localY = (int) (index / layer);
                long remainder = index - localY * layer;
                int localZ = (int) (remainder / sizeX);
                int localX = (int) (remainder - (long) localZ * sizeX);
                return PackedBlockPlan.pack(snapshot.minimumX() + localX, snapshot.minimumY() + localY,
                        snapshot.minimumZ() + localZ);
            }

            @Override
            public int expected(int index) {
                long packed = position(index);
                int x = PackedBlockPlan.unpackX(packed);
                int y = PackedBlockPlan.unpackY(packed);
                int z = PackedBlockPlan.unpackZ(packed);
                return snapshot.selected(x, y, z) ? snapshot.rawId(x, y, z) : PackedBlockPlan.NO_EXPECTED_STATE;
            }
        };
    }

    private static Text message(String text, Formatting color) {
        return Feedback.prefix()
                .append(Text.literal(text).formatted(color));
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** Lectura por tramos de la región; cada turno avanza hasta agotar su parte del tick. */
    private static final class CaptureJob {
        private final UUID playerId;
        private final ServerWorld world;
        private final Region selection;
        private final String description;
        private final RegionOperation operation;
        private final Consumer<RegionSnapshot> onCaptured;
        private final RegionReader reader;
        private final EditRequest request;
        private final int padding;
        private final int[] extra;
        private final long startedAt = System.nanoTime();

        private CaptureJob(
                UUID playerId,
                ServerWorld world,
                Region selection,
                String description,
                RegionOperation operation,
                Consumer<RegionSnapshot> onCaptured,
                int sizeX,
                int sizeY,
                int sizeZ,
                int minimumX,
                int minimumY,
                int minimumZ,
                EditRequest request,
                int padding,
                int[] extra
        ) {
            this.playerId = playerId;
            this.request = request;
            this.padding = padding;
            this.extra = extra == null ? null : extra.clone();
            this.world = world;
            this.selection = selection;
            this.description = description;
            this.operation = operation;
            this.onCaptured = onCaptured;
            this.reader = new RegionReader(new WorldSource(world), selected(selection), minimumX, minimumY,
                    minimumZ, sizeX, sizeY, sizeZ, operation != null && operation.needsLight());
        }

        /** Una caja se mira con sus límites, sin crear un vector por bloque; las demás formas, con WorldEdit. */
        private static RegionReader.Selection selected(Region selection) {
            if (selection instanceof CuboidRegion) {
                BlockVector3 low = selection.getMinimumPoint();
                BlockVector3 high = selection.getMaximumPoint();
                return (x, y, z) -> x >= low.x() && x <= high.x() && y >= low.y() && y <= high.y()
                        && z >= low.z() && z <= high.z();
            }
            return (x, y, z) -> selection.contains(BlockVector3.at(x, y, z));
        }
    }

    /** El mundo para {@link RegionReader}: estados crudos, su descripción la primera vez y la luz. */
    private static final class WorldSource implements RegionReader.BlockSource {
        private final ServerWorld world;
        private final BlockPos.Mutable position = new BlockPos.Mutable();

        private WorldSource(ServerWorld world) {
            this.world = world;
        }

        @Override
        public int rawId(int x, int y, int z) {
            return Block.getRawIdFromState(world.getBlockState(position.set(x, y, z)));
        }

        @Override
        public RegionSnapshot.StateInfo describe(int rawId, int x, int y, int z) {
            BlockState state = world.getBlockState(position.set(x, y, z));
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
                    properties,
                    state.getLuminance()
            );
        }

        @Override
        public int light(int x, int y, int z) {
            position.set(x, y, z);
            return (world.getLightLevel(LightType.BLOCK, position) << 4)
                    | world.getLightLevel(LightType.SKY, position);
        }
    }
}
