package com.komainu.moxia.selection;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.performance.ServerWork;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.worldedit.BlockSetSelector;
import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.worldedit.ez.SelectionBridge;
import com.komainu.moxia.worldedit.ez.SelectionShape;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * La selección a mano del servidor: el lazo (que se proyecta por tramos, un rato en cada tick), el pincel de
 * selección, deshacer trazos y las operaciones de {@code /komaedit sel} (ampliar, reducir, invertir, pasar a caja y
 * quitar). El resultado va a la sesión de WorldEdit como {@link BlockSetSelector}, así que todas las órdenes lo usan
 * como región. Solo en el hilo del servidor.
 */
public final class SelectionToolService {
    public static final String LASSO = KomaPermissions.SELECT + ".lasso";
    public static final String BRUSH = KomaPermissions.SELECT + ".brush";
    public static final String GROW = KomaPermissions.SELECT + ".grow";
    public static final String SHRINK = KomaPermissions.SELECT + ".shrink";
    public static final String COMPLEMENT = KomaPermissions.SELECT + ".complement";
    public static final String UNDO = KomaPermissions.SELECT + ".undo";
    public static final String BOX = KomaPermissions.SELECT + ".box";
    public static final String CLEAR = KomaPermissions.SELECT + ".clear";
    /** Bloques como mucho en una selección de bloques sueltos (y nunca más que lo que se puede editar de una vez). */
    public static final int DEFAULT_MAX = 1_000_000;
    /** Ni con permiso sin límites: cada bloque ocupa memoria en el servidor mientras está seleccionado. */
    public static final int HARD_MAX = 2_000_000;
    /** Hasta dónde llegan los rayos del lazo. */
    public static final double MAX_REACH = 256.0;
    /** Lo que puede separarse la cámara del lazo de los ojos del jugador (la vista en tercera persona incluida). */
    public static final double CAMERA_SLACK = 8.0;
    /** Pasadas del pincel como mucho en un trazo. */
    public static final int MAX_DABS = 4_096;
    private static final long TICK_NANOS = 8_000_000L;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private SelectionToolService() {
    }

    /** Lo de antes de un trazo, para deshacerlo: bloques sueltos, una forma de WorldEdit o nada. */
    record Before(long[] blocks, SelectionShape shape) {
        static final Before NOTHING = new Before(null, null);

        long weight() {
            return blocks == null ? 1 : blocks.length;
        }
    }

    private record Job(LassoProjection projection, SelectionCombine mode, RegistryKey<World> world) {
    }

    private static final class Stroke {
        final SelectionCombine mode;
        final BrushShape shape;
        final int radius;
        final boolean nonSolid;
        final RegistryKey<World> world;
        final BlockSet current;
        final BlockSet working;
        int dabs;

        Stroke(SelectionCombine mode, BrushShape shape, int radius, boolean nonSolid, RegistryKey<World> world,
               BlockSet current) {
            this.mode = mode;
            this.shape = shape;
            this.radius = radius;
            this.nonSolid = nonSolid;
            this.world = world;
            this.current = current;
            // Restar y sumar parten de lo que hay; sustituir e intersecar, de nada (lo común se va añadiendo).
            this.working = mode == SelectionCombine.ADD || mode == SelectionCombine.SUBTRACT ? current.copy()
                    : new BlockSet();
        }
    }

    private static final class State {
        final SelectionHistory<Before> history = new SelectionHistory<>(Before::weight);
        Job job;
        Stroke stroke;
    }

    private static State state(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), ignored -> new State());
    }

    /** Bloques como mucho que puede tener la selección de este jugador. */
    public static int limit(ServerPlayerEntity player) {
        long policy = OperationPolicy.limit(player);
        return (int) Math.min(policy == Long.MAX_VALUE ? HARD_MAX : DEFAULT_MAX, policy);
    }

    // ── Lazo ─────────────────────────────────────────────────────────────────────────────────────────────

    /** Un lazo terminado en el cliente: se comprueba y se empieza a proyectar (acaba en unos ticks). */
    public static void lasso(ServerPlayerEntity player, SelectionPayloads.Lasso payload) {
        try {
            KomaPermissions.require(player, LASSO);
            LassoCamera camera = payload.camera();
            Vec3d eyes = player.getEyePos();
            double distance = eyes.distanceTo(new Vec3d(camera.position().x(), camera.position().y(),
                    camera.position().z()));
            if (distance > CAMERA_SLACK) {
                throw new IllegalArgumentException("El lazo se dibujó desde otro sitio; vuelve a intentarlo");
            }
            SelectionCombine mode = SelectionCombine.byId(payload.mode());
            LassoProjection projection = new LassoProjection(camera, payload.polygon(), payload.settings(),
                    new ServerVoxels(player.getServerWorld()), MAX_REACH, limit(player));
            State state = state(player);
            boolean replaced = state.job != null;
            state.job = new Job(projection, mode, player.getServerWorld().getRegistryKey());
            message(player, Feedback.Level.INFO, replaced ? "Lazo nuevo: el anterior se descarta"
                    : "Seleccionando con el lazo…");
        } catch (RuntimeException exception) {
            message(player, Feedback.Level.ERROR, readable(exception));
        }
    }

    /** Cada tick: avanza los lazos pendientes un rato y aplica los que acaban. */
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, State>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, State> entry = iterator.next();
            Job job = entry.getValue().job;
            if (job == null) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !player.getServerWorld().getRegistryKey().equals(job.world())) {
                entry.getValue().job = null;
                continue;
            }
            long deadline = ServerWork.deadline(TICK_NANOS);
            try {
                if (!job.projection().step(() -> System.nanoTime() >= deadline)) {
                    entry.getValue().job = null;
                    BlockSet stroke = job.projection().result();
                    if (stroke.isEmpty()) {
                        message(player, Feedback.Level.WARN, "El lazo no rodeó ningún bloque a la vista");
                        continue;
                    }
                    int changed = commit(player, job.mode(), stroke);
                    message(player, Feedback.Level.OK, "Lazo: " + describe(job.mode(), stroke.size(), changed));
                }
            } catch (RuntimeException exception) {
                entry.getValue().job = null;
                message(player, Feedback.Level.ERROR, readable(exception));
            }
        }
    }

    // ── Pincel ───────────────────────────────────────────────────────────────────────────────────────────

    public static void brush(ServerPlayerEntity player, SelectionPayloads.Brush payload) {
        State state = state(player);
        try {
            KomaPermissions.require(player, BRUSH);
            if (payload.phase() == SelectionPayloads.Brush.END) {
                finishStroke(player, state);
                return;
            }
            if (payload.phase() == SelectionPayloads.Brush.START) {
                finishStroke(player, state);
                if (payload.radius() < 0 || payload.radius() > BrushShape.MAX_RADIUS) {
                    throw new IllegalArgumentException("El radio del pincel va de 0 a " + BrushShape.MAX_RADIUS);
                }
                BrushShape[] shapes = BrushShape.values();
                if (payload.shape() < 0 || payload.shape() >= shapes.length) {
                    throw new IllegalArgumentException("Forma de pincel desconocida");
                }
                SelectionCombine mode = SelectionCombine.byId(payload.mode());
                Before before = capture(player);
                BlockSet current = mode.needsCurrent() ? current(player, limit(player)) : new BlockSet();
                state.history.push(before);
                state.stroke = new Stroke(mode, shapes[payload.shape()], payload.radius(), payload.nonSolid(),
                        player.getServerWorld().getRegistryKey(), current);
            } else if (payload.phase() != SelectionPayloads.Brush.DAB) {
                throw new IllegalArgumentException("Pasada de pincel desconocida");
            }
            Stroke stroke = state.stroke;
            if (stroke == null || !player.getServerWorld().getRegistryKey().equals(stroke.world)) {
                return;
            }
            BlockPos position = payload.position();
            if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(position)) > MAX_REACH * MAX_REACH
                    || !player.getServerWorld().isInBuildLimit(position)) {
                return;
            }
            if (++stroke.dabs > MAX_DABS) {
                throw new IllegalArgumentException("Trazo demasiado largo: suelta y vuelve a pintar");
            }
            dab(player, stroke, position);
        } catch (RuntimeException exception) {
            state.stroke = null;
            message(player, Feedback.Level.ERROR, readable(exception));
        }
    }

    /** Una pasada: se pinta la forma en {@code position} y la selección cambia ya, sin esperar a soltar. */
    private static void dab(ServerPlayerEntity player, Stroke stroke, BlockPos position) {
        BlockSet stamp = stroke.shape.stamp(position.getX(), position.getY(), position.getZ(), stroke.radius,
                new ServerVoxels(player.getServerWorld()), stroke.nonSolid);
        switch (stroke.mode) {
            case REPLACE, ADD -> stroke.working.addAll(stamp);
            case SUBTRACT -> stroke.working.removeAll(stamp);
            case INTERSECT -> stamp.forEach(block -> {
                if (stroke.current.containsPacked(block)) {
                    stroke.working.addPacked(block);
                }
            });
        }
        int limit = limit(player);
        if (stroke.working.size() > limit) {
            throw new IllegalArgumentException("La selección pasaría de " + ProgressLine.group(limit) + " bloques");
        }
        select(player, stroke.working, limit);
    }

    private static void finishStroke(ServerPlayerEntity player, State state) {
        Stroke stroke = state.stroke;
        state.stroke = null;
        if (stroke != null && stroke.dabs > 0) {
            message(player, Feedback.Level.OK, "Pincel: " + ProgressLine.group(stroke.working.size())
                    + " bloques seleccionados · /komaedit sel undo lo deshace");
        }
    }

    // ── Órdenes ──────────────────────────────────────────────────────────────────────────────────────────

    /** Deshace el último trazo (lazo, pasada de pincel u operación). */
    public static boolean undo(ServerPlayerEntity player) {
        State state = state(player);
        state.stroke = null;
        Before before = state.history.pop();
        if (before == null) {
            return false;
        }
        restore(player, before);
        return true;
    }

    /** Amplía la selección; {@code surface}: por columnas sobre lo que se ve. */
    public static int grow(ServerPlayerEntity player, int steps, BrushShape shape, boolean surface, boolean nonSolid) {
        int limit = limit(player);
        BlockSet current = requireCurrent(player, limit);
        BlockSet result = surface
                ? SelectionMorphology.growSurface(current, steps, shape, new ServerVoxels(player.getServerWorld()),
                nonSolid, limit)
                : SelectionMorphology.grow(current, steps, shape, limit);
        return replace(player, result);
    }

    public static int shrink(ServerPlayerEntity player, int steps, BrushShape shape, boolean surface,
                             boolean nonSolid) {
        BlockSet current = requireCurrent(player, limit(player));
        BlockSet result = surface
                ? SelectionMorphology.shrinkSurface(current, steps, shape, new ServerVoxels(player.getServerWorld()),
                nonSolid)
                : SelectionMorphology.shrink(current, steps, shape);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No quedaría nada seleccionado; prueba con menos bloques");
        }
        return replace(player, result);
    }

    /** Invierte: lo que no estaba (en su caja o, por la superficie, en las columnas de su rectángulo). */
    public static int complement(ServerPlayerEntity player, boolean surface, boolean nonSolid) {
        int limit = limit(player);
        BlockSet current = requireCurrent(player, limit);
        BlockSet result = surface
                ? SelectionMorphology.invertSurface(current, new ServerVoxels(player.getServerWorld()), nonSolid,
                limit)
                : SelectionMorphology.invert(current, limit);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No queda nada fuera de la selección");
        }
        return replace(player, result);
    }

    /** Cambia la selección por la caja que la contiene (una selección de WorldEdit de siempre). */
    public static BlockVector3[] box(ServerPlayerEntity player) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        RegionSelector selector = context.session().getRegionSelector(context.world());
        if (!selector.isDefined()) {
            throw new IllegalArgumentException("No tienes nada seleccionado");
        }
        Region region;
        try {
            region = selector.getRegion();
        } catch (IncompleteRegionException exception) {
            throw new IllegalArgumentException("No tienes nada seleccionado");
        }
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        state(player).history.push(capture(player));
        SelectionBridge.apply(player, SelectionShape.cuboid(min, max, context.world().getName()));
        return new BlockVector3[]{min, max};
    }

    /** Quita la selección (se puede deshacer). */
    public static boolean clear(ServerPlayerEntity player) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        if (!context.session().getRegionSelector(context.world()).isDefined()) {
            return false;
        }
        state(player).history.push(capture(player));
        select(player, new BlockSet(), limit(player));
        return true;
    }

    // ── Sesión de WorldEdit ──────────────────────────────────────────────────────────────────────────────

    /**
     * Junta el trazo con la selección según el modo, apunta lo de antes en el historial y deja el resultado.
     *
     * @return los bloques que tiene ahora la selección
     */
    static int commit(ServerPlayerEntity player, SelectionCombine mode, BlockSet stroke) {
        int limit = limit(player);
        Before before = capture(player);
        BlockSet current = mode.needsCurrent() ? current(player, limit) : new BlockSet();
        BlockSet result = mode.apply(current, stroke);
        if (result.size() > limit) {
            throw new IllegalArgumentException("La selección pasaría de " + ProgressLine.group(limit) + " bloques");
        }
        state(player).history.push(before);
        select(player, result, limit);
        return result.size();
    }

    private static int replace(ServerPlayerEntity player, BlockSet result) {
        state(player).history.push(capture(player));
        select(player, result, limit(player));
        return result.size();
    }

    /** Deja {@code blocks} como selección de WorldEdit; vacío, quita la selección. */
    static void select(ServerPlayerEntity player, BlockSet blocks, int limit) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        RegionSelector selector = blocks.isEmpty() ? new CuboidRegionSelector(context.world())
                : new BlockSetSelector(context.world(), blocks, limit);
        context.session().setRegionSelector(context.world(), selector);
        context.session().dispatchCUISelection(context.actor());
    }

    /** La selección de ahora como bloques sueltos (una caja u otra forma de WorldEdit se convierte). */
    static BlockSet current(ServerPlayerEntity player, int limit) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        RegionSelector selector = context.session().getRegionSelector(context.world());
        if (selector instanceof BlockSetSelector blocks && blocks.blocks() != null) {
            return blocks.blocks();
        }
        if (!selector.isDefined()) {
            return new BlockSet();
        }
        Region region;
        try {
            region = selector.getRegion();
        } catch (IncompleteRegionException exception) {
            return new BlockSet();
        }
        if (region.getVolume() > limit) {
            throw new IllegalArgumentException("Tu selección tiene " + ProgressLine.group(region.getVolume())
                    + " bloques: para sumarle, restarle o cambiarla no puede pasar de " + ProgressLine.group(limit));
        }
        BlockSet blocks = new BlockSet();
        for (BlockVector3 position : region) {
            if (BlockSet.storable(position.y())) {
                blocks.add(position.x(), position.y(), position.z());
            }
        }
        return blocks;
    }

    private static BlockSet requireCurrent(ServerPlayerEntity player, int limit) {
        BlockSet current = current(player, limit);
        if (current.isEmpty()) {
            throw new IllegalArgumentException("No tienes nada seleccionado");
        }
        return current;
    }

    /** Lo que hay seleccionado ahora, para poder volver a ello. */
    static Before capture(ServerPlayerEntity player) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        RegionSelector selector = context.session().getRegionSelector(context.world());
        if (selector instanceof BlockSetSelector blocks && blocks.blocks() != null) {
            return new Before(blocks.blocks().toSortedArray(), null);
        }
        if (!selector.isDefined()) {
            return Before.NOTHING;
        }
        try {
            return new Before(null, SelectionBridge.read(selector, context.world()));
        } catch (IncompleteRegionException | RuntimeException exception) {
            try {
                return new Before(current(player, limit(player)).toSortedArray(), null);
            } catch (RuntimeException tooBig) {
                return Before.NOTHING;
            }
        }
    }

    private static void restore(ServerPlayerEntity player, Before before) {
        if (before.blocks() != null) {
            select(player, BlockSet.ofPacked(before.blocks()), limit(player));
        } else if (before.shape() != null) {
            SelectionBridge.apply(player, before.shape());
        } else {
            select(player, new BlockSet(), limit(player));
        }
    }

    // ── Avisos y limpieza ────────────────────────────────────────────────────────────────────────────────

    private static String describe(SelectionCombine mode, int stroke, int total) {
        String verb = switch (mode) {
            case REPLACE -> "seleccionados";
            case ADD -> "sumados";
            case SUBTRACT -> "restados";
            case INTERSECT -> "para intersecar";
        };
        return ProgressLine.group(stroke) + " bloques " + verb + "; la selección tiene " + ProgressLine.group(total);
    }

    static void message(ServerPlayerEntity player, Feedback.Level level, String text) {
        player.sendMessage(Feedback.line(level, text), true);
    }

    private static String readable(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /** Si hay un lazo calculándose para el jugador. */
    public static boolean busy(UUID player) {
        State state = STATES.get(player);
        return state != null && state.job != null;
    }

    /** Al cambiar de mundo: el lazo y el trazo a medias ya no valen. */
    public static void discard(ServerPlayerEntity player) {
        State state = STATES.get(player.getUuid());
        if (state != null) {
            state.job = null;
            state.stroke = null;
        }
    }

    public static void forget(UUID player) {
        STATES.remove(player);
    }

    public static void clearAll() {
        STATES.clear();
    }
}
