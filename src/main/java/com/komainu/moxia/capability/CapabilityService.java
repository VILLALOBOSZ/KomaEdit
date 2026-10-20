package com.komainu.moxia.capability;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.progress.ProgressLine;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor de las capacidades: guarda lo que tiene activado cada jugador, comprueba cada intención del cliente
 * (permiso, modo de juego, alcance, que pueda construir ahí) y la convierte en ediciones para
 * {@link HandEditApplier}. También aplica el alcance largo (atributo de Minecraft, así vale incluso sin el mod en el
 * cliente) y la velocidad de vuelo.
 */
public final class CapabilityService {
    private static final Identifier REACH_MODIFIER = Identifier.of(KomaEditMod.MOD_ID, "long_reach");
    private static final float VANILLA_FLY_SPEED = 0.05F;
    private static final int RECHECK_TICKS = 40;
    private static final Map<UUID, CapabilitySettings> SETTINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingSmear> SMEARS = new ConcurrentHashMap<>();
    private static final Map<UUID, CapabilityRules.Throttle> THROTTLES = new ConcurrentHashMap<>();
    private static final Map<UUID, CapabilityStatePayload> SENT = new ConcurrentHashMap<>();
    /** Entradas por atender en el próximo tick, cuando el proyecto del jugador ya está abierto. */
    private static final Set<UUID> PENDING_JOINS = ConcurrentHashMap.newKeySet();

    /** Arrastre con el origen fijado: el bloque pulsado, la fila de origen y la cara (que da el eje). */
    private record PendingSmear(BlockPos anchor, List<BlockPos> source, Direction face, RegistryKey<World> world) {
    }

    private CapabilityService() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(CapabilityActionPayload.ID, CapabilityActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CapabilityStatePayload.ID, CapabilityStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CapabilityActionPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
        // Al tick siguiente: el proyecto del jugador se abre en otro oyente de entrada, y server.execute desde el
        // hilo del servidor se ejecutaría en el acto, antes que él.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PENDING_JOINS.add(handler.player.getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(CapabilityService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
    }

    // ---- Estado y opciones -------------------------------------------------------------------------------------

    public static CapabilitySettings settings(ServerPlayerEntity player) {
        return SETTINGS.computeIfAbsent(player.getUuid(), CapabilityStore::load);
    }

    /** ¿Puede usar la capacidad ahora (permiso y modo de juego)? */
    public static boolean usable(ServerPlayerEntity player, BuildCapability capability) {
        return KomaPermissions.can(player, capability.permission()) && modeAllowed(player);
    }

    /**
     * Activa, desactiva o alterna una capacidad.
     *
     * @param value 1 activa, 0 desactiva, negativo alterna
     * @return el mensaje para el jugador
     */
    public static String toggle(ServerPlayerEntity player, BuildCapability capability, int value) {
        if (capability == null) {
            throw new IllegalArgumentException("Capacidad desconocida");
        }
        CapabilitySettings current = settings(player);
        boolean on = value < 0 ? !current.has(capability) : value != 0;
        if (on) {
            CapabilityRules.denial(capability, player.isCreative(), KomaPermissions.can(player,
                    BuildCapability.ANY_MODE_PERMISSION), KomaPermissions.can(player, capability.permission()))
                    .ifPresent(reason -> {
                        throw new IllegalArgumentException(reason);
                    });
        }
        store(player, current.with(capability, on));
        if (capability == BuildCapability.SMEAR && !on) {
            SMEARS.remove(player.getUuid());
        }
        if (capability == BuildCapability.REACH) {
            applyReach(player);
        }
        sync(player);
        return capability.displayName() + ": " + (on ? "activado" : "desactivado");
    }

    /** Cambia una opción dentro de los límites del servidor. */
    public static String option(ServerPlayerEntity player, CapabilityOption option, int value) {
        if (option == null) {
            throw new IllegalArgumentException("Opción desconocida");
        }
        String permission = option.capability() == null
                ? BuildCapability.FLY_SPEED_PERMISSION
                : option.capability().permission();
        KomaPermissions.require(player, permission);
        CapabilityLimits limits = CapabilityConfig.limits();
        int clamped = switch (option) {
            case EXTRUDE_LIMIT -> Math.clamp(value, 1, limits.maxLayerBlocks());
            case ANGEL_DISTANCE -> Math.clamp(value, 1, limits.maxReach());
            case REACH_DISTANCE -> CapabilityRules.reach(value, limits);
            case FLY_SPEED -> CapabilityRules.flySpeed(value, limits);
            default -> value;
        };
        CapabilitySettings next = option.apply(settings(player), clamped);
        store(player, next);
        switch (option) {
            case REACH_DISTANCE -> applyReach(player);
            case FLY_SPEED -> applyFlySpeed(player, next.flySpeed());
            default -> {
            }
        }
        sync(player);
        return describe(option, next);
    }

    public static String describe(CapabilityOption option, CapabilitySettings settings) {
        return switch (option) {
            case REPLACE_FAMILY -> "Reemplazar familia: " + (settings.replaceFamily() ? "sí" : "no");
            case EXTRUDE_MATCH -> "Extruir sigue bloques del mismo "
                    + (settings.extrudeByState() ? "estado" : "tipo");
            case EXTRUDE_LIMIT -> "Extruir: hasta " + ProgressLine.group(settings.extrudeLimit()) + " bloques por capa";
            case ANGEL_DISTANCE -> "Colocar en el aire a " + ProgressLine.group(settings.angelDistance()) + " bloques";
            case REACH_DISTANCE -> "Alcance largo: " + ProgressLine.group(settings.reachDistance()) + " bloques";
            case FLY_SPEED -> "Velocidad de vuelo: ×" + String.format(java.util.Locale.ROOT, "%.1f",
                    settings.flySpeed() / 10.0);
        };
    }

    /** Cambia el modo de juego (permiso {@code komaedit.admin.gamemode}). */
    public static Text gamemode(ServerPlayerEntity player, int id) {
        KomaPermissions.require(player, BuildCapability.GAMEMODE_PERMISSION);
        GameMode mode = GameMode.byId(id);
        player.changeGameMode(mode);
        applyReach(player);
        sync(player);
        return Text.literal("Modo de juego: ").append(mode.getTranslatableName());
    }

    // ---- Red ---------------------------------------------------------------------------------------------------

    public static void handle(ServerPlayerEntity player, CapabilityActionPayload payload) {
        try {
            switch (payload.action()) {
                case SYNC -> sync(player);
                case TOGGLE -> message(player, toggle(player, BuildCapability.byOrdinal(payload.index()),
                        payload.value()));
                case OPTION -> message(player, option(player, CapabilityOption.byOrdinal(payload.index()),
                        payload.value()));
                case GAMEMODE -> message(player, gamemode(player, payload.value()));
                case SMEAR_CANCEL -> {
                    if (SMEARS.remove(player.getUuid()) != null) {
                        message(player, "Arrastre cancelado");
                    }
                    sync(player);
                }
                default -> act(player, payload);
            }
        } catch (IllegalArgumentException exception) {
            message(player, exception.getMessage() == null ? "No se pudo hacer" : exception.getMessage());
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.error("Capability action {} failed for {}", payload.action(), player.getUuid(),
                    exception);
            message(player, "Error: " + exception.getClass().getSimpleName());
        }
    }

    private static void act(ServerPlayerEntity player, CapabilityActionPayload payload) {
        CapabilitySettings settings = settings(player);
        BuildCapability capability = capabilityOf(payload.action());
        if (!settings.has(capability)) {
            // El cliente iba con un estado viejo.
            sync(player);
            return;
        }
        CapabilityRules.denial(capability, player.isCreative(), KomaPermissions.can(player,
                BuildCapability.ANY_MODE_PERMISSION), KomaPermissions.can(player, capability.permission()))
                .ifPresent(reason -> {
                    throw new IllegalArgumentException(reason);
                });
        boolean multi = switch (payload.action()) {
            case EXTRUDE, SHRINK, SMEAR_END -> true;
            default -> false;
        };
        CapabilityRules.Throttle throttle = THROTTLES.computeIfAbsent(player.getUuid(),
                id -> new CapabilityRules.Throttle());
        if (!throttle.allow(player.getServer().getTicks(), multi, CapabilityConfig.limits().actionsPerTick())) {
            return;
        }
        boolean updates = !(settings.has(BuildCapability.NO_UPDATES) && usable(player, BuildCapability.NO_UPDATES));
        BlockPos pos = payload.position();
        switch (payload.action()) {
            case ANGEL -> angel(player, settings, pos, updates);
            case SMEAR_END -> smearEnd(player, pos, updates);
            default -> {
                if (!player.canInteractWithBlockAt(pos, 1.0)) {
                    throw new IllegalArgumentException("Ese bloque está demasiado lejos");
                }
                Direction face = face(payload.face());
                Vec3d hit = clampHit(payload.hit(), pos);
                switch (payload.action()) {
                    case TWEAK -> tweak(player, settings, pos, payload.sneaking(), updates);
                    case TWEAK_PROPERTY -> tweakProperty(player, settings, pos, payload.sneaking());
                    case EXTRUDE -> extrude(player, settings, pos, face, false, updates);
                    case SHRINK -> extrude(player, settings, pos, face, true, updates);
                    case REPLACE -> replace(player, settings, pos, face, hit, updates);
                    case PLACE -> place(player, pos, face, hit);
                    case BREAK -> quietBreak(player, pos);
                    case SMEAR_START -> smearStart(player, settings, pos, face, payload.sneaking());
                    default -> throw new IllegalArgumentException("Acción desconocida");
                }
            }
        }
    }

    private static BuildCapability capabilityOf(CapabilityActionPayload.Action action) {
        return switch (action) {
            case TWEAK, TWEAK_PROPERTY -> BuildCapability.TWEAK;
            case EXTRUDE, SHRINK -> BuildCapability.EXTRUDE;
            case REPLACE -> BuildCapability.REPLACE;
            case PLACE, BREAK -> BuildCapability.NO_UPDATES;
            case ANGEL -> BuildCapability.ANGEL;
            case SMEAR_START, SMEAR_END, SMEAR_CANCEL -> BuildCapability.SMEAR;
            default -> throw new IllegalArgumentException("Acción desconocida");
        };
    }

    // ---- Acciones ----------------------------------------------------------------------------------------------

    private static void tweak(ServerPlayerEntity player, CapabilitySettings settings, BlockPos pos, boolean back,
                              boolean updates) {
        BlockState state = player.getServerWorld().getBlockState(pos);
        List<PropertyCycle.Property> ordered = PropertyCycle.ordered(BlockStates.properties(state));
        PropertyCycle.Property active = PropertyCycle.active(ordered, settings.tweakProperty());
        if (active == null) {
            throw new IllegalArgumentException("Este bloque no tiene nada que retocar");
        }
        String next = PropertyCycle.step(active, back ? -1 : 1);
        HandEditApplier.Result result = HandEditApplier.apply(player,
                List.of(new HandEdit(pos, BlockStates.withValue(state, active.name(), next))), updates);
        requireChanged(result);
        message(player, "Retocar · " + active.name() + ": " + next);
    }

    private static void tweakProperty(ServerPlayerEntity player, CapabilitySettings settings, BlockPos pos,
                                      boolean back) {
        BlockState state = player.getServerWorld().getBlockState(pos);
        List<PropertyCycle.Property> ordered = PropertyCycle.ordered(BlockStates.properties(state));
        PropertyCycle.Property active = PropertyCycle.active(ordered, settings.tweakProperty());
        if (active == null) {
            throw new IllegalArgumentException("Este bloque no tiene nada que retocar");
        }
        String name = PropertyCycle.switchProperty(ordered, active.name(), back ? -1 : 1);
        store(player, settings.withTweakProperty(name));
        PropertyCycle.Property chosen = PropertyCycle.active(ordered, name);
        message(player, "Retocar · propiedad " + (ordered.indexOf(chosen) + 1) + "/" + ordered.size() + ": "
                + chosen.name() + " = " + chosen.value());
        sync(player);
    }

    private static void extrude(ServerPlayerEntity player, CapabilitySettings settings, BlockPos pos, Direction face,
                                boolean shrink, boolean updates) {
        ServerWorld world = player.getServerWorld();
        BlockState target = world.getBlockState(pos);
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem item) || item.getBlock() != target.getBlock()) {
            throw new IllegalArgumentException("Ten en la mano el mismo bloque que la capa");
        }
        int limit = Math.min(settings.extrudeLimit(), CapabilityConfig.limits().maxLayerBlocks());
        LayerFinder.Layer layer = LayerFinder.find(view(world, target, settings.extrudeByState()),
                BlockStates.cell(pos), BlockStates.normal(face), limit);
        if (layer.isEmpty()) {
            throw new IllegalArgumentException("Esa cara está tapada");
        }
        List<HandEdit> edits = new ArrayList<>(layer.cells().size());
        for (Cell cell : layer.cells()) {
            BlockPos source = BlockStates.pos(cell);
            BlockState state = world.getBlockState(source);
            if (shrink) {
                edits.add(new HandEdit(source, state.getFluidState().getBlockState()));
            } else {
                BlockPos destination = source.offset(face);
                edits.add(new HandEdit(destination, waterlogged(state, world.getBlockState(destination))));
            }
        }
        HandEditApplier.Result result = HandEditApplier.apply(player, edits, updates);
        requireChanged(result);
        message(player, (shrink ? "Encogido: " : "Extruido: ") + ProgressLine.group(result.changed()) + " bloques"
                + (layer.truncated() ? " (límite de " + limit + ")" : "") + skipped(result) + " · //undo");
    }

    private static void replace(ServerPlayerEntity player, CapabilitySettings settings, BlockPos pos, Direction face,
                                Vec3d hit, boolean updates) {
        ServerWorld world = player.getServerWorld();
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem item)) {
            throw new IllegalArgumentException("Ten un bloque en la mano");
        }
        BlockState target = world.getBlockState(pos);
        if (target.isAir()) {
            throw new IllegalArgumentException("No hay bloque que reemplazar");
        }
        Block block = item.getBlock();
        if (settings.replaceFamily()) {
            block = BlockStates.families().swap(BlockStates.id(target.getBlock()), BlockStates.id(block))
                    .flatMap(BlockStates::block)
                    .orElse(block);
        }
        if (block == target.getBlock()) {
            throw new IllegalArgumentException("Ya es ese bloque");
        }
        if (BlockStates.multiBlock(target.getBlock()) || BlockStates.multiBlock(block)) {
            throw new IllegalArgumentException("Los bloques de dos partes (puertas, camas…) no se reemplazan");
        }
        BlockState placed;
        try {
            placed = block.getPlacementState(new TargetPlacementContext(player, Hand.MAIN_HAND, stack,
                    new BlockHitResult(hit, face, pos, false)));
        } catch (RuntimeException exception) {
            placed = null;
        }
        if (placed == null) {
            placed = block.getDefaultState();
        }
        BlockState result = BlockStates.apply(placed, StateTransfer.transfer(BlockStates.values(target),
                BlockStates.allowed(block), BlockStates.values(placed)));
        requireChanged(HandEditApplier.apply(player, List.of(new HandEdit(pos, result)), updates));
    }

    /** «Sin actualizaciones»: coloca en la cara como Minecraft, pero sin avisar a los vecinos. */
    private static void place(ServerPlayerEntity player, BlockPos pos, Direction face, Vec3d hit) {
        ServerWorld world = player.getServerWorld();
        ItemStack stack = player.getMainHandStack();
        BlockState fluid = fluidOf(stack);
        BlockPos at;
        BlockState state;
        if (fluid != null) {
            at = world.getBlockState(pos).isReplaceable() ? pos : pos.offset(face);
            if (!world.getBlockState(at).isReplaceable()) {
                throw new IllegalArgumentException("Ahí no cabe");
            }
            state = fluid;
        } else if (stack.getItem() instanceof BlockItem item) {
            if (BlockStates.multiBlock(item.getBlock())) {
                throw new IllegalArgumentException("Los bloques de dos partes se colocan sin «Sin actualizaciones»");
            }
            ItemPlacementContext context = item.getPlacementContext(new ItemPlacementContext(player, Hand.MAIN_HAND,
                    stack, new BlockHitResult(hit, face, pos, false)));
            if (context == null || !context.canPlace()) {
                throw new IllegalArgumentException("Ahí no cabe");
            }
            at = context.getBlockPos();
            state = item.getBlock().getPlacementState(context);
            if (state == null) {
                throw new IllegalArgumentException("Ahí no se puede colocar");
            }
        } else {
            throw new IllegalArgumentException("Ten un bloque o un cubo en la mano");
        }
        requireChanged(HandEditApplier.apply(player, List.of(new HandEdit(at, state)), false));
    }

    private static void quietBreak(ServerPlayerEntity player, BlockPos pos) {
        BlockState state = player.getServerWorld().getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        requireChanged(HandEditApplier.apply(player,
                List.of(new HandEdit(pos, state.getFluidState().getBlockState())), false));
    }

    private static void angel(ServerPlayerEntity player, CapabilitySettings settings, BlockPos cell,
                              boolean updates) {
        ServerWorld world = player.getServerWorld();
        int distance = Math.clamp(settings.angelDistance(), 1, CapabilityConfig.limits().maxReach());
        Vec3d eye = player.getEyePos();
        if (!CapabilityRules.withinReach(eye.x, eye.y, eye.z, BlockStates.cell(cell), distance)) {
            throw new IllegalArgumentException("Esa posición está más lejos de " + ProgressLine.group(distance) + " bloques");
        }
        if (!world.isInBuildLimit(cell) || !world.getBlockState(cell).isReplaceable()) {
            throw new IllegalArgumentException("Ahí ya hay un bloque");
        }
        ItemStack stack = player.getMainHandStack();
        BlockState state = fluidOf(stack);
        if (state == null) {
            if (!(stack.getItem() instanceof BlockItem item)) {
                throw new IllegalArgumentException("Ten un bloque o un cubo en la mano");
            }
            if (BlockStates.multiBlock(item.getBlock())) {
                throw new IllegalArgumentException("Los bloques de dos partes no se colocan en el aire");
            }
            try {
                state = item.getBlock().getPlacementState(new ItemPlacementContext(player, Hand.MAIN_HAND, stack,
                        new BlockHitResult(Vec3d.ofCenter(cell), Direction.UP, cell, false)));
            } catch (RuntimeException exception) {
                state = null;
            }
            if (state == null) {
                state = item.getBlock().getDefaultState();
            }
        }
        requireChanged(HandEditApplier.apply(player, List.of(new HandEdit(cell, state)), updates));
    }

    private static void smearStart(ServerPlayerEntity player, CapabilitySettings settings, BlockPos pos,
                                   Direction face, boolean row) {
        ServerWorld world = player.getServerWorld();
        BlockState target = world.getBlockState(pos);
        if (target.isAir()) {
            throw new IllegalArgumentException("Pulsa sobre un bloque para fijar el origen");
        }
        List<BlockPos> source = new ArrayList<>();
        if (row) {
            for (Cell cell : SmearPlanner.row(view(world, target, settings.extrudeByState()), BlockStates.cell(pos),
                    BlockStates.normal(face), CapabilityConfig.limits().maxSmearLength())) {
                source.add(BlockStates.pos(cell));
            }
        } else {
            source.add(pos.toImmutable());
        }
        SMEARS.put(player.getUuid(), new PendingSmear(pos.toImmutable(), List.copyOf(source), face,
                world.getRegistryKey()));
        message(player, "Arrastrar · origen de " + source.size() + (source.size() == 1 ? " bloque" : " bloques")
                + ": clic derecho donde acaba, izquierdo cancela");
        sync(player);
    }

    private static void smearEnd(ServerPlayerEntity player, BlockPos end, boolean updates) {
        PendingSmear pending = SMEARS.get(player.getUuid());
        ServerWorld world = player.getServerWorld();
        if (pending == null) {
            sync(player);
            throw new IllegalArgumentException("Primero fija el origen con un clic derecho sobre un bloque");
        }
        if (!pending.world().equals(world.getRegistryKey())) {
            SMEARS.remove(player.getUuid());
            sync(player);
            throw new IllegalArgumentException("El origen del arrastre estaba en otra dimensión");
        }
        CapabilityLimits limits = CapabilityConfig.limits();
        Cell axis = BlockStates.normal(pending.face());
        int length = SmearPlanner.validateEnd(BlockStates.cell(pending.anchor()), axis, BlockStates.cell(end),
                limits.maxSmearLength());
        Vec3d eye = player.getEyePos();
        if (!CapabilityRules.withinReach(eye.x, eye.y, eye.z, BlockStates.cell(end), limits.maxReach())) {
            throw new IllegalArgumentException("El final está demasiado lejos");
        }
        int steps = Math.max(1, limits.maxLayerBlocks() / pending.source().size());
        boolean truncated = Math.abs(length) > steps;
        if (truncated) {
            length = Integer.signum(length) * steps;
        }
        List<Cell> source = new ArrayList<>(pending.source().size());
        for (BlockPos pos : pending.source()) {
            source.add(BlockStates.cell(pos));
        }
        List<HandEdit> edits = new ArrayList<>();
        for (SmearPlanner.Copy copy : SmearPlanner.copies(source, axis, length)) {
            BlockPos to = BlockStates.pos(copy.to());
            if (!world.isInBuildLimit(to) || !BlockStates.loaded(world, to)) {
                continue;
            }
            BlockState destination = world.getBlockState(to);
            BlockState from = world.getBlockState(BlockStates.pos(copy.from()));
            if (destination.isReplaceable() && !from.isAir()) {
                edits.add(new HandEdit(to, waterlogged(from, destination)));
            }
        }
        if (edits.isEmpty()) {
            throw new IllegalArgumentException("No queda sitio libre en ese eje");
        }
        SMEARS.remove(player.getUuid());
        HandEditApplier.Result result = HandEditApplier.apply(player, edits, updates);
        sync(player);
        requireChanged(result);
        message(player, "Estirado: " + ProgressLine.group(result.changed()) + " bloques" + (truncated ? " (límite)" : "")
                + skipped(result) + " · //undo");
    }

    // ---- Apoyo -------------------------------------------------------------------------------------------------

    private static LayerFinder.View view(ServerWorld world, BlockState target, boolean byState) {
        return new LayerFinder.View() {
            @Override
            public boolean matches(int x, int y, int z) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!world.isInBuildLimit(pos) || !BlockStates.loaded(world, pos)) {
                    return false;
                }
                BlockState state = world.getBlockState(pos);
                return byState ? state.equals(target) : state.isOf(target.getBlock());
            }

            @Override
            public boolean free(int x, int y, int z) {
                BlockPos pos = new BlockPos(x, y, z);
                return world.isInBuildLimit(pos) && BlockStates.loaded(world, pos)
                        && world.getBlockState(pos).isReplaceable();
            }
        };
    }

    /** La copia queda anegada solo si va a parar a agua. */
    private static BlockState waterlogged(BlockState copy, BlockState destination) {
        if (!copy.contains(Properties.WATERLOGGED)) {
            return copy;
        }
        return copy.with(Properties.WATERLOGGED, destination.getFluidState().isOf(Fluids.WATER)
                && destination.getFluidState().isStill());
    }

    /** Bloque de fluido de un cubo de agua o lava, o {@code null}. */
    private static BlockState fluidOf(ItemStack stack) {
        if (stack.isOf(Items.WATER_BUCKET)) {
            return Blocks.WATER.getDefaultState();
        }
        if (stack.isOf(Items.LAVA_BUCKET)) {
            return Blocks.LAVA.getDefaultState();
        }
        return null;
    }

    private static Direction face(int id) {
        if (id < 0 || id >= Direction.values().length) {
            throw new IllegalArgumentException("Falta la cara del bloque");
        }
        return Direction.byId(id);
    }

    private static Vec3d clampHit(Vec3d hit, BlockPos pos) {
        return new Vec3d(
                Math.clamp(hit.x, pos.getX(), pos.getX() + 1.0),
                Math.clamp(hit.y, pos.getY(), pos.getY() + 1.0),
                Math.clamp(hit.z, pos.getZ(), pos.getZ() + 1.0)
        );
    }

    private static void requireChanged(HandEditApplier.Result result) {
        if (result.changed() == 0) {
            throw new IllegalArgumentException(result.reason() == null ? "No ha cambiado nada" : result.reason());
        }
    }

    private static String skipped(HandEditApplier.Result result) {
        return result.skipped() == 0 ? "" : " (" + result.skipped() + " sin tocar: " + result.reason() + ")";
    }

    private static boolean modeAllowed(ServerPlayerEntity player) {
        return player.isCreative() || KomaPermissions.can(player, BuildCapability.ANY_MODE_PERMISSION);
    }

    private static void store(ServerPlayerEntity player, CapabilitySettings settings) {
        SETTINGS.put(player.getUuid(), settings);
        CapabilityStore.save(player.getUuid(), settings);
    }

    /** Pone o quita el modificador de alcance según lo activado y lo permitido. */
    public static void applyReach(ServerPlayerEntity player) {
        EntityAttributeInstance range = player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE);
        if (range == null) {
            return;
        }
        CapabilitySettings settings = SETTINGS.get(player.getUuid());
        double bonus = settings != null && settings.has(BuildCapability.REACH)
                && usable(player, BuildCapability.REACH)
                ? CapabilityRules.reachBonus(settings.reachDistance(), CapabilityConfig.limits(), range.getBaseValue())
                : 0.0;
        EntityAttributeModifier current = range.getModifier(REACH_MODIFIER);
        if (current != null && current.value() == bonus) {
            return;
        }
        if (current != null) {
            range.removeModifier(REACH_MODIFIER);
        }
        if (bonus > 0.0) {
            range.addTemporaryModifier(new EntityAttributeModifier(REACH_MODIFIER, bonus,
                    EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void applyFlySpeed(ServerPlayerEntity player, int tenths) {
        player.getAbilities().setFlySpeed(VANILLA_FLY_SPEED * tenths / 10.0F);
        player.sendAbilitiesUpdate();
    }

    public static CapabilityStatePayload state(ServerPlayerEntity player) {
        CapabilitySettings settings = settings(player);
        CapabilityLimits limits = CapabilityConfig.limits();
        int permitted = 0;
        for (BuildCapability capability : BuildCapability.values()) {
            if (KomaPermissions.can(player, capability.permission())) {
                permitted |= capability.bit();
            }
        }
        PendingSmear smear = SMEARS.get(player.getUuid());
        BlockPos from = null;
        BlockPos to = null;
        if (smear != null && smear.world().equals(player.getServerWorld().getRegistryKey())) {
            from = smear.source().getFirst();
            to = smear.source().getFirst();
            for (BlockPos pos : smear.source()) {
                from = new BlockPos(Math.min(from.getX(), pos.getX()), Math.min(from.getY(), pos.getY()),
                        Math.min(from.getZ(), pos.getZ()));
                to = new BlockPos(Math.max(to.getX(), pos.getX()), Math.max(to.getY(), pos.getY()),
                        Math.max(to.getZ(), pos.getZ()));
            }
        }
        return new CapabilityStatePayload(
                settings.mask(),
                permitted,
                modeAllowed(player),
                KomaPermissions.can(player, BuildCapability.GAMEMODE_PERMISSION),
                KomaPermissions.can(player, BuildCapability.FLY_SPEED_PERMISSION),
                settings.replaceFamily(),
                settings.extrudeByState(),
                settings.extrudeLimit(),
                settings.angelDistance(),
                settings.reachDistance(),
                settings.flySpeed(),
                settings.tweakProperty(),
                limits.maxReach(),
                limits.maxLayerBlocks(),
                limits.maxSmearLength(),
                limits.maxFlySpeed(),
                from == null ? null : smear.anchor(),
                from,
                to,
                smear == null ? -1 : smear.face().getId()
        );
    }

    /** Manda el estado al cliente si tiene KomaEdit. */
    public static void sync(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, CapabilityStatePayload.ID)) {
            return;
        }
        CapabilityStatePayload state = state(player);
        SENT.put(player.getUuid(), state);
        ServerPlayNetworking.send(player, state);
    }

    /** Vuelve a mandar el estado a todos (tras recargar la configuración). */
    public static void resyncAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyReach(player);
            sync(player);
        }
    }

    private static void join(ServerPlayerEntity player) {
        if (player.isDisconnected()) {
            return;
        }
        CapabilitySettings settings = settings(player);
        if (settings.flySpeed() != 10) {
            if (KomaPermissions.can(player, BuildCapability.FLY_SPEED_PERMISSION)) {
                applyFlySpeed(player, CapabilityRules.flySpeed(settings.flySpeed(), CapabilityConfig.limits()));
            } else {
                applyFlySpeed(player, 10);
                store(player, settings.withFlySpeed(10));
            }
        }
        applyReach(player);
        sync(player);
    }

    private static void tick(MinecraftServer server) {
        if (!PENDING_JOINS.isEmpty()) {
            for (UUID playerId : List.copyOf(PENDING_JOINS)) {
                PENDING_JOINS.remove(playerId);
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    join(player);
                }
            }
        }
        if (server.getTicks() % RECHECK_TICKS != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!SETTINGS.containsKey(player.getUuid())) {
                continue;
            }
            // El modo de juego o los permisos pueden haber cambiado por fuera (/gamemode, LuckPerms).
            applyReach(player);
            if (ServerPlayNetworking.canSend(player, CapabilityStatePayload.ID)) {
                CapabilityStatePayload state = state(player);
                if (!state.equals(SENT.get(player.getUuid()))) {
                    SENT.put(player.getUuid(), state);
                    ServerPlayNetworking.send(player, state);
                }
            }
        }
    }

    private static void remove(UUID playerId) {
        PENDING_JOINS.remove(playerId);
        SETTINGS.remove(playerId);
        SMEARS.remove(playerId);
        THROTTLES.remove(playerId);
        SENT.remove(playerId);
    }

    private static void clearAll() {
        PENDING_JOINS.clear();
        SETTINGS.clear();
        SMEARS.clear();
        THROTTLES.clear();
        SENT.clear();
    }

    private static void message(ServerPlayerEntity player, String message) {
        message(player, Text.literal(message));
    }

    private static void message(ServerPlayerEntity player, Text message) {
        player.sendMessage(Feedback.prefix().append(message), true);
    }
}
