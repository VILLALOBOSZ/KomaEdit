package com.komainu.moxia.symmetry;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.brush.BrushMarkers;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.symmetry.SymmetryGeometry.Copy;
import com.komainu.moxia.symmetry.SymmetryGeometry.Point;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * Modo simetría de {@code /komaedit symmetry}: guarda la simetría de cada jugador, repite en las copias los bloques que
 * pone y rompe a mano, dibuja los planos con partículas y, si lo pide, refleja sus ediciones de WorldEdit y KomaEdit
 * ({@link SymmetryExtent}).
 *
 * <p>Una copia hecha a mano nunca pisa un bloque: solo se coloca sobre aire, agua o plantas que se puedan reemplazar,
 * y solo se rompe si en su sitio está el mismo bloque. Antes de tocar cada sitio se comprueban los límites del mundo,
 * la protección del spawn y los mods de protección (con el evento de rotura de Fabric, como si el jugador fuera a romper
 * ese bloque). Si una parte de la copia falla, la copia entera se descarta.</p>
 */
public final class SymmetryService {
    public static final String PERMISSION = KomaPermissions.BUILD + ".symmetry";
    public static final String REFLECTIONAL = PERMISSION + ".reflectional";
    public static final String ROTATIONAL = PERMISSION + ".rotational";
    public static final String WORLDEDIT = PERMISSION + ".worldedit";
    /** Cada cuántos ticks se redibujan los planos. */
    private static final int GUIDE_INTERVAL = 10;
    /** Colocar sin avisar a los vecinos ni soltar objetos: los avisos van al final, con la copia entera puesta. */
    private static final int SILENT = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final Direction[] NEIGHBOURS = Direction.values();

    private static final Map<UUID, SymmetrySettings> SETTINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Snapshot> PENDING_BREAKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Snapshot> PENDING_PLACE = new ThreadLocal<>();
    /** Mientras se pregunta a los mods de protección, el propio evento de rotura no debe tomarse por una rotura. */
    private static final ThreadLocal<Boolean> QUERYING = ThreadLocal.withInitial(() -> false);
    private static volatile MinecraftServer server;
    private static boolean hookInstalled;

    private SymmetryService() {
    }

    /** Estado de un sitio y sus seis vecinos justo antes de colocar o romper. */
    public record Snapshot(UUID player, BlockPos pos, BlockState[] before) {
    }

    private record Part(BlockPos pos, BlockState state, BlockState before) {
    }

    private enum Result {
        DONE,
        /** No había nada que copiar en ese sitio, o estaba ocupado. */
        SKIPPED,
        /** Fuera del mundo o en una zona donde el jugador no puede construir. */
        BLOCKED
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!QUERYING.get() && wants(player, world)) {
                PENDING_BREAKS.put(player.getUuid(), snapshot(player, world, pos));
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            Snapshot snapshot = PENDING_BREAKS.remove(player.getUuid());
            if (snapshot != null && snapshot.pos().equals(pos) && player instanceof ServerPlayerEntity serverPlayer
                    && world instanceof ServerWorld serverWorld) {
                broken(serverPlayer, serverWorld, snapshot, state);
            }
        });
        PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, blockEntity) ->
                PENDING_BREAKS.remove(player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(SymmetryService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, ignored) -> remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STARTING.register(starting -> {
            server = starting;
            installWorldEditHook();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            clearAll();
        });
    }

    private static synchronized void installWorldEditHook() {
        if (hookInstalled) {
            return;
        }
        WorldEdit.getInstance().getEventBus().register(new SymmetryWorldEditHook());
        hookInstalled = true;
    }

    public static SymmetrySettings settings(UUID player) {
        return SETTINGS.getOrDefault(player, SymmetrySettings.DEFAULT);
    }

    public static SymmetrySettings update(UUID player, UnaryOperator<SymmetrySettings> change) {
        return SETTINGS.compute(player, (id, current) -> change.apply(current == null ? SymmetrySettings.DEFAULT : current));
    }

    public static void remove(UUID player) {
        SETTINGS.remove(player);
        PENDING_BREAKS.remove(player);
    }

    public static void clearAll() {
        SETTINGS.clear();
        PENDING_BREAKS.clear();
    }

    public static String dimension(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    /** ¿Tiene permiso para el tipo de simetría del modo? */
    public static boolean canUse(ServerPlayerEntity player, SymmetryMode mode) {
        return KomaPermissions.can(player, mode.radial() ? ROTATIONAL : REFLECTIONAL);
    }

    private static boolean wants(PlayerEntity player, World world) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity)) {
            return false;
        }
        SymmetrySettings settings = SETTINGS.get(player.getUuid());
        return settings != null && settings.appliesIn(dimension(world));
    }

    private static Snapshot snapshot(PlayerEntity player, World world, BlockPos pos) {
        BlockState[] before = new BlockState[NEIGHBOURS.length + 1];
        before[0] = world.getBlockState(pos);
        for (int index = 0; index < NEIGHBOURS.length; index++) {
            before[index + 1] = world.getBlockState(pos.offset(NEIGHBOURS[index]));
        }
        return new Snapshot(player.getUuid(), pos.toImmutable(), before);
    }

    private static BlockPos cell(BlockPos pos, int index) {
        return index == 0 ? pos : pos.offset(NEIGHBOURS[index - 1]);
    }

    /** Lo llama {@code BlockItemMixin} antes de que un jugador coloque un bloque con la mano. */
    public static void beforePlace(PlayerEntity player, World world, BlockPos pos) {
        PENDING_PLACE.set(player != null && wants(player, world) ? snapshot(player, world, pos) : null);
    }

    /**
     * Lo llama {@code BlockItemMixin} después: copia el bloque y, si lo hay, su compañero (puerta, cama…). El bloque
     * es el que ha aparecido en el sitio, no el del objeto: una antorcha o un cartel en la pared son otro bloque.
     */
    public static void afterPlace(PlayerEntity player, World world, BlockPos pos, boolean placed) {
        Snapshot snapshot = PENDING_PLACE.get();
        PENDING_PLACE.remove();
        if (!placed || snapshot == null || player == null || !snapshot.pos().equals(pos)
                || !snapshot.player().equals(player.getUuid()) || !(player instanceof ServerPlayerEntity serverPlayer)
                || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        BlockState result = world.getBlockState(pos);
        if (result.isAir() || result.equals(snapshot.before()[0])) {
            return;
        }
        Block block = result.getBlock();
        List<Part> parts = new ArrayList<>();
        for (int index = 0; index < snapshot.before().length; index++) {
            BlockPos at = cell(pos, index);
            BlockState now = world.getBlockState(at);
            // El sitio colocado siempre cuenta; un vecino solo si ese mismo bloque apareció en él (la otra mitad).
            if (index == 0 || now.isOf(block) && !snapshot.before()[index].isOf(block)) {
                parts.add(new Part(at, now, snapshot.before()[index]));
            }
        }
        replicate(serverPlayer, serverWorld, parts, true);
    }

    private static void broken(ServerPlayerEntity player, ServerWorld world, Snapshot snapshot, BlockState state) {
        Block block = state.getBlock();
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(snapshot.pos(), state, state));
        for (int index = 1; index < snapshot.before().length; index++) {
            BlockPos at = cell(snapshot.pos(), index);
            // La otra mitad de una puerta o una cama desaparece con ella.
            if (snapshot.before()[index].isOf(block) && !world.getBlockState(at).isOf(block)) {
                parts.add(new Part(at, snapshot.before()[index], snapshot.before()[index]));
            }
        }
        replicate(player, world, parts, false);
    }

    private static void replicate(ServerPlayerEntity player, ServerWorld world, List<Part> parts, boolean place) {
        SymmetrySettings settings = SETTINGS.get(player.getUuid());
        if (settings == null || !settings.appliesIn(dimension(world)) || !player.getAbilities().allowModifyWorld
                || !canUse(player, settings.mode())) {
            return;
        }
        List<Point> points = new ArrayList<>(parts.size());
        for (Part part : parts) {
            points.add(new Point(part.pos().getX(), part.pos().getY(), part.pos().getZ()));
        }
        List<Copy> copies = SymmetryGeometry.copies(settings.mode(), settings.center(), points);
        int missed = 0;
        for (Copy copy : copies) {
            Result result;
            try {
                result = place ? placeCopy(player, world, parts, copy) : breakCopy(player, world, parts, copy);
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.warn("Could not mirror a block for {}", player.getName().getString(), exception);
                result = Result.BLOCKED;
            }
            if (result == Result.BLOCKED || place && result == Result.SKIPPED) {
                missed++;
            }
        }
        if (missed > 0) {
            player.sendMessage(Text.translatableWithFallback("komaedit.symmetry.missed",
                    "Simetría · %s de %s copias sin hacer (ocupado, zona protegida o fuera del mundo)",
                    missed, copies.size()), true);
        }
    }

    private static Result placeCopy(ServerPlayerEntity player, ServerWorld world, List<Part> parts, Copy copy) {
        int size = parts.size();
        BlockPos[] targets = new BlockPos[size];
        BlockState[] wanted = new BlockState[size];
        BlockState[] previous = new BlockState[size];
        for (int index = 0; index < size; index++) {
            Point point = copy.points().get(index);
            BlockPos target = new BlockPos(point.x(), point.y(), point.z());
            if (!inside(world, target)) {
                return Result.BLOCKED;
            }
            Part part = parts.get(index);
            BlockState current = world.getBlockState(target);
            BlockState state = waterlogged(SymmetryStates.transform(part.state(), copy.transform()), current);
            // Encima de lo mismo vale si el original también se puso sobre lo mismo (losa doble, más velas…).
            boolean free = current.isReplaceable() || current.equals(state)
                    || current.isOf(state.getBlock()) && part.before().isOf(state.getBlock());
            if (!free) {
                return Result.SKIPPED;
            }
            if (!allowed(player, world, target, current)) {
                return Result.BLOCKED;
            }
            targets[index] = target;
            wanted[index] = state;
            previous[index] = current;
        }
        for (int index = 0; index < size; index++) {
            if (!previous[index].equals(wanted[index])) {
                world.setBlockState(targets[index], wanted[index], SILENT);
            }
        }
        // Con la copia entera puesta, cada bloque se ajusta a sus vecinos (vallas, escaleras, la otra mitad). Si alguno
        // no se sostiene (una antorcha sin pared), la copia no se hace: como el jugador, no se puede poner en el aire.
        for (int index = 0; index < size; index++) {
            BlockState placed = world.getBlockState(targets[index]);
            BlockState fixed = Block.postProcessState(placed, world, targets[index]);
            if (fixed.isAir() && !placed.isAir()) {
                for (int undo = 0; undo < size; undo++) {
                    world.setBlockState(targets[undo], previous[undo], SILENT);
                }
                return Result.SKIPPED;
            }
            if (fixed != placed) {
                world.setBlockState(targets[index], fixed, SILENT);
            }
        }
        for (int index = 0; index < size; index++) {
            notifyAround(world, targets[index], previous[index], world.getBlockState(targets[index]));
        }
        return Result.DONE;
    }

    private static Result breakCopy(ServerPlayerEntity player, ServerWorld world, List<Part> parts, Copy copy) {
        int size = parts.size();
        BlockPos[] targets = new BlockPos[size];
        BlockState[] previous = new BlockState[size];
        for (int index = 0; index < size; index++) {
            Point point = copy.points().get(index);
            BlockPos target = new BlockPos(point.x(), point.y(), point.z());
            if (!inside(world, target)) {
                return Result.BLOCKED;
            }
            BlockState current = world.getBlockState(target);
            if (!current.isOf(parts.get(index).state().getBlock())) {
                if (index == 0) {
                    return Result.SKIPPED;
                }
                // Falta la otra mitad: se rompe lo que haya de la copia.
                continue;
            }
            if (!allowed(player, world, target, current)) {
                return Result.BLOCKED;
            }
            targets[index] = target;
            previous[index] = current;
        }
        for (int index = 0; index < size; index++) {
            if (targets[index] != null) {
                world.setBlockState(targets[index], previous[index].getFluidState().getBlockState(), SILENT);
            }
        }
        for (int index = 0; index < size; index++) {
            if (targets[index] != null) {
                if (index == 0) {
                    world.syncWorldEvent(null, WorldEvents.BLOCK_BROKEN, targets[index],
                            Block.getRawIdFromState(previous[index]));
                }
                notifyAround(world, targets[index], previous[index], world.getBlockState(targets[index]));
            }
        }
        return Result.DONE;
    }

    /** Lo mismo que hace {@code World.setBlockState} con los vecinos, pero cuando ya está puesta la copia entera. */
    private static void notifyAround(ServerWorld world, BlockPos pos, BlockState before, BlockState now) {
        world.updateNeighbors(pos, before.getBlock());
        if (now.hasComparatorOutput()) {
            world.updateComparators(pos, now.getBlock());
        }
        before.prepare(world, pos, Block.NOTIFY_LISTENERS);
        now.updateNeighbors(world, pos, Block.NOTIFY_LISTENERS);
        now.prepare(world, pos, Block.NOTIFY_LISTENERS);
    }

    /** Como al colocar a mano: anegado solo si en el sitio hay agua quieta. */
    private static BlockState waterlogged(BlockState state, BlockState current) {
        if (!state.contains(Properties.WATERLOGGED)) {
            return state;
        }
        return state.with(Properties.WATERLOGGED, current.getFluidState().getFluid() == Fluids.WATER);
    }

    /** Dentro de la altura del mundo, del borde y de un chunk ya cargado (las copias nunca cargan chunks). */
    private static boolean inside(ServerWorld world, BlockPos pos) {
        return world.isInBuildLimit(pos) && world.getWorldBorder().contains(pos) && world.isChunkLoaded(pos);
    }

    /** ¿Podría el jugador construir ahí? Protección del spawn y mods de protección que usan el evento de rotura. */
    private static boolean allowed(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
        if (!player.canModifyAt(world, pos)) {
            return false;
        }
        QUERYING.set(true);
        try {
            return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, state,
                    world.getBlockEntity(pos));
        } finally {
            QUERYING.set(false);
        }
    }

    /**
     * Copias simétricas de una edición a mano que no pasa por la colocación de Minecraft (las capacidades de
     * construcción rápida, que escriben con su propia edición de WorldEdit). Quien la llama aplica las copias en la
     * misma operación y con sus propias comprobaciones de dónde se puede construir; aquí solo se saltan las que caen
     * fuera de la altura del mundo o del borde. Un aire en {@code states} copia una rotura.
     *
     * <p>No da nada si la simetría no actúa ahí, o si la opción de WorldEdit ya va a reflejar esa edición
     * ({@link SymmetryExtent}): así no se copia dos veces.</p>
     *
     * @param positions bloques de la edición
     * @param states    estado que recibe cada uno, en el mismo orden
     * @param extra     recibe cada copia
     */
    public static void mirrorHandEdits(ServerPlayerEntity player, ServerWorld world, List<BlockPos> positions,
                                       List<BlockState> states, BiConsumer<BlockPos, BlockState> extra) {
        SymmetrySettings settings = SETTINGS.get(player.getUuid());
        if (positions.size() != states.size() || settings == null || !settings.appliesIn(dimension(world))
                || !player.getAbilities().allowModifyWorld || !canUse(player, settings.mode())
                || settings.worldEdit() && KomaPermissions.can(player, WORLDEDIT)) {
            return;
        }
        List<Point> points = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            points.add(new Point(pos.getX(), pos.getY(), pos.getZ()));
        }
        long limit = KomaPermissions.can(player, KomaPermissions.LIMIT_UNRESTRICTED)
                ? Long.MAX_VALUE : KomaEditConfig.settings().maxBlocksPerOperation();
        long copies = 0;
        for (SymmetryGeometry.Image image : SymmetryGeometry.images(settings.mode(), settings.center(), points)) {
            BlockPos target = new BlockPos(image.point().x(), image.point().y(), image.point().z());
            if (!world.isInBuildLimit(target) || !world.getWorldBorder().contains(target)) {
                continue;
            }
            if (++copies > limit) {
                player.sendMessage(Text.translatableWithFallback("komaedit.symmetry.limit",
                        "Simetría · las copias se pararon en %s bloques: es el límite por edición", limit), false);
                return;
            }
            extra.accept(target, SymmetryStates.transform(states.get(image.source()), image.transform()));
        }
    }

    /**
     * Extensión que refleja las ediciones de WorldEdit y KomaEdit del jugador, o {@code null} si no toca: simetría
     * apagada, sin la opción de WorldEdit, otra dimensión o sin permiso.
     */
    static Extent worldEditExtent(UUID playerId, com.sk89q.worldedit.world.World editWorld, Extent delegate) {
        MinecraftServer current = server;
        SymmetrySettings settings = SETTINGS.get(playerId);
        if (current == null || settings == null || !settings.worldEdit()) {
            return null;
        }
        World world;
        try {
            world = FabricAdapter.adapt(editWorld);
        } catch (RuntimeException exception) {
            return null;
        }
        if (!(world instanceof ServerWorld serverWorld) || !settings.appliesIn(dimension(serverWorld))) {
            return null;
        }
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(playerId);
        if (player == null || !canUse(player, settings.mode()) || !KomaPermissions.can(player, WORLDEDIT)) {
            return null;
        }
        long limit = KomaPermissions.can(player, KomaPermissions.LIMIT_UNRESTRICTED)
                ? Long.MAX_VALUE : KomaEditConfig.settings().maxBlocksPerOperation();
        return new SymmetryExtent(delegate, settings.mode(), settings.center(), serverWorld, limit,
                () -> current.execute(() -> {
                    ServerPlayerEntity online = current.getPlayerManager().getPlayer(playerId);
                    if (online != null) {
                        online.sendMessage(Text.translatableWithFallback("komaedit.symmetry.limit",
                                "Simetría · las copias se pararon en %s bloques: es el límite por edición", limit), false);
                    }
                }));
    }

    private static void tick(MinecraftServer current) {
        if (SETTINGS.isEmpty() || current.getTicks() % GUIDE_INTERVAL != 0) {
            return;
        }
        for (Map.Entry<UUID, SymmetrySettings> entry : SETTINGS.entrySet()) {
            SymmetrySettings settings = entry.getValue();
            ServerPlayerEntity player = current.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !settings.appliesIn(dimension(player.getServerWorld()))) {
                continue;
            }
            for (SymmetryGuides.Mark mark : SymmetryGuides.marks(settings.mode(), settings.center(),
                    player.getX(), player.getY(), player.getZ())) {
                BrushMarkers.dot(player, mark.x(), mark.y(), mark.z(), mark.color());
            }
        }
    }
}
