package com.komainu.moxia.brush.superwand;

import com.komainu.moxia.brush.BrushInput;
import com.komainu.moxia.brush.BrushMarkers;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.ConvexPolyhedralRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supervarita (Superwand de ezEdits): herramienta de selección con cuatro modos que escribe directamente en la
 * selección de WorldEdit, así //set, //copy, WorldEditCUI y las operaciones de KomaEdit la ven al momento.
 *
 * <p>Los puntos se guardan aquí en orden; cada cambio reconstruye la selección del selector actual (cuboide,
 * esfera, cilindro, polígono o poliedro convexo) con el primero como primario y los demás como secundarios. Si la
 * selección cambia por otro camino (//pos1, la varita de WorldEdit), la supervarita la vuelve a leer.</p>
 */
public final class SuperwandManager {
    public static final double REACH = 192.0;
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private SuperwandManager() {
    }

    public static void activate(ServerPlayerEntity player, SuperwandMode mode, double relativeDistance) {
        State state = STATES.computeIfAbsent(player.getUuid(), id -> new State());
        state.mode = mode;
        state.relativeDistance = relativeDistance;
        state.grab = null;
        importSelection(player, state, true);
    }

    public static Optional<SuperwandMode> mode(UUID playerId) {
        State state = STATES.get(playerId);
        return state == null ? Optional.empty() : Optional.of(state.mode);
    }

    public static List<BlockVector3> points(UUID playerId) {
        State state = STATES.get(playerId);
        return state == null ? List.of() : state.points.points();
    }

    public static boolean deactivate(UUID playerId) {
        return STATES.remove(playerId) != null;
    }

    public static void remove(UUID playerId) {
        STATES.remove(playerId);
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static void handle(ServerPlayerEntity player, BrushInput input) {
        State state = STATES.get(player.getUuid());
        if (state == null) {
            player.sendMessage(message("Activa la supervarita con /komaedit superwand", Formatting.YELLOW), true);
            return;
        }
        try {
            importSelection(player, state, false);
            handle(player, state, input);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(message("Supervarita: " + exception.getMessage(), Formatting.RED), true);
        }
    }

    private static void handle(ServerPlayerEntity player, State state, BrushInput input) {
        switch (input) {
            case SNEAK_LEFT -> {
                state.grab = null;
                state.mode = state.mode.next();
                player.sendMessage(message("Modo " + state.mode.displayName(), Formatting.AQUA), true);
            }
            case SWAP -> {
                state.points.reverse();
                sync(player, state, "Orden de los puntos invertido");
            }
            case SNEAK_SWAP -> {
                state.points.rotate();
                sync(player, state, "Puntos desplazados una posición");
            }
            case LEFT -> {
                if (state.mode == SuperwandMode.GRAB) {
                    if (state.grab != null) {
                        state.grab = null;
                        sync(player, state, "Soltado");
                    }
                    return;
                }
                if (state.points.removeLast()) {
                    sync(player, state, "Último punto quitado");
                }
            }
            case RIGHT, SNEAK_RIGHT -> {
                if (state.mode == SuperwandMode.GRAB) {
                    grab(player, state);
                    return;
                }
                BlockVector3 point = pointFor(player, state);
                state.points.add(point, capacity(player));
                sync(player, state, "Punto " + state.points.size() + ": " + point.x() + ", " + point.y() + ", "
                        + point.z());
            }
        }
    }

    private static BlockVector3 pointFor(ServerPlayerEntity player, State state) {
        return switch (state.mode) {
            case TRACE -> {
                HitResult result = player.raycast(REACH, 1.0F, false);
                if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
                    throw new IllegalArgumentException("Mira un bloque");
                }
                yield BlockVector3.at(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ());
            }
            case POSITION -> BlockVector3.at(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            case RELATIVE -> {
                BlockVector3 last = state.points.last();
                if (last == null) {
                    throw new IllegalArgumentException("El modo relativo parte del último punto: marca uno antes");
                }
                Vec3d look = player.getRotationVector();
                double distance = state.relativeDistance > 0 ? state.relativeDistance : distance(player, last);
                yield SuperwandPoints.relative(last, look.x, look.y, look.z, Math.max(1.0, distance));
            }
            case GRAB -> throw new IllegalStateException("El modo agarrar no marca puntos");
        };
    }

    private static void grab(ServerPlayerEntity player, State state) {
        if (state.grab != null) {
            state.grab = null;
            sync(player, state, "Soltado");
            return;
        }
        if (state.points.isEmpty()) {
            throw new IllegalArgumentException("No hay selección que agarrar");
        }
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        int index = state.points.nearestToRay(eye.x, eye.y, eye.z, look.x, look.y, look.z, 1.5);
        BlockVector3 reference;
        if (index >= 0) {
            reference = state.points.get(index);
        } else {
            reference = centroid(state.points.points());
        }
        double distance = Math.max(1.0, distance(player, reference));
        BlockVector3 held = SuperwandPoints.heldAt(eye.x, eye.y, eye.z, look.x, look.y, look.z, distance);
        state.grab = new Grab(index, distance, held);
        if (index >= 0) {
            state.points.set(index, held);
        } else {
            state.points.translate(held.x() - reference.x(), held.y() - reference.y(), held.z() - reference.z());
        }
        sync(player, state, index >= 0 ? "Agarrado el punto " + (index + 1) + " · clic izquierdo para soltar"
                : "Agarrada la selección · clic izquierdo para soltar");
    }

    /** Mueve lo agarrado con la mirada del jugador; se llama en cada tick del servidor. */
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, State> entry : STATES.entrySet()) {
            State state = entry.getValue();
            if (state.grab == null) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Vec3d eye = player.getEyePos();
            Vec3d look = player.getRotationVector();
            BlockVector3 held = SuperwandPoints.heldAt(eye.x, eye.y, eye.z, look.x, look.y, look.z,
                    state.grab.distance);
            if (held.equals(state.grab.held)) {
                continue;
            }
            if (state.grab.index >= 0 && state.grab.index < state.points.size()) {
                state.points.set(state.grab.index, held);
            } else {
                BlockVector3 delta = held.subtract(state.grab.held);
                state.points.translate(delta.x(), delta.y(), delta.z());
            }
            state.grab = new Grab(state.grab.index, state.grab.distance, held);
            try {
                sync(player, state, null);
            } catch (RuntimeException exception) {
                state.grab = null;
                player.sendMessage(message("Supervarita: " + exception.getMessage(), Formatting.RED), true);
            }
        }
    }

    /** Escribe los puntos en el selector actual de WorldEdit y avisa a WorldEditCUI. */
    private static void sync(ServerPlayerEntity player, State state, String text) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        RegionSelector selector = context.session().getRegionSelector(context.world());
        selector.clear();
        ActorSelectorLimits limits = ActorSelectorLimits.forActor(context.actor());
        List<BlockVector3> points = state.points.points();
        for (int index = 0; index < points.size(); index++) {
            if (index == 0) {
                selector.selectPrimary(points.get(index), limits);
            } else {
                selector.selectSecondary(points.get(index), limits);
            }
        }
        selector.learnChanges();
        context.session().dispatchCUISelection(context.actor());
        state.signature = signature(selector);
        if (text != null) {
            player.sendMessage(message(text + " · " + points.size() + " punto(s) · " + state.mode.displayName(),
                    Formatting.GRAY), true);
            BrushMarkers.path(player, points, BrushMarkers.POINT);
        }
    }

    /** Lee la selección de WorldEdit si cambió por otro camino desde la última vez. */
    private static void importSelection(ServerPlayerEntity player, State state, boolean force) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        RegionSelector selector = context.session().getRegionSelector(context.world());
        String signature = signature(selector);
        if (!force && Objects.equals(signature, state.signature)) {
            return;
        }
        state.points.clear();
        for (BlockVector3 point : pointsOf(selector)) {
            state.points.add(point, SuperwandPoints.UNLIMITED);
        }
        state.signature = signature;
    }

    static List<BlockVector3> pointsOf(RegionSelector selector) {
        List<BlockVector3> points = new ArrayList<>();
        Region region;
        try {
            region = selector.getRegion();
        } catch (IncompleteRegionException exception) {
            try {
                points.add(selector.getPrimaryPosition());
            } catch (IncompleteRegionException ignored) {
                // Sin ningún punto todavía.
            }
            return points;
        }
        switch (region) {
            case CuboidRegion cuboid -> {
                points.add(cuboid.getPos1());
                points.add(cuboid.getPos2());
            }
            case ConvexPolyhedralRegion convex -> points.addAll(convex.getVertices());
            case Polygonal2DRegion polygon -> {
                int y = polygon.getMinimumPoint().y();
                for (BlockVector2 point : polygon.getPoints()) {
                    points.add(BlockVector3.at(point.x(), y, point.z()));
                }
                if (polygon.getMaximumPoint().y() != y && !points.isEmpty()) {
                    // El segundo punto lleva la altura de arriba, como al marcarlo con la varita.
                    BlockVector3 second = points.size() > 1 ? points.get(1) : points.getFirst();
                    points.set(points.size() > 1 ? 1 : 0, second.withY(polygon.getMaximumPoint().y()));
                }
            }
            case EllipsoidRegion ellipsoid -> {
                BlockVector3 center = ellipsoid.getCenter().toBlockPoint();
                Vector3 radius = ellipsoid.getRadius();
                points.add(center);
                points.add(center.add((int) Math.round(radius.x()), 0, 0));
            }
            case CylinderRegion cylinder -> {
                BlockVector3 center = cylinder.getCenter().toBlockPoint();
                points.add(center);
                points.add(center.add((int) Math.round(cylinder.getRadius().x()), 0, 0));
            }
            default -> {
                points.add(region.getMinimumPoint());
                points.add(region.getMaximumPoint());
            }
        }
        return points;
    }

    /** Cuántos puntos admite el selector actual antes de sustituir el último. */
    private static int capacity(ServerPlayerEntity player) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        RegionSelector selector = context.session().getRegionSelector(context.world());
        if (selector instanceof ConvexPolyhedralRegionSelector || selector instanceof Polygonal2DRegionSelector) {
            return SuperwandPoints.UNLIMITED;
        }
        return 2;
    }

    private static String signature(RegionSelector selector) {
        Region region = selector.getIncompleteRegion();
        return selector.getClass().getSimpleName() + ":" + selector.isDefined() + ":" + region.getMinimumPoint() + ":"
                + region.getMaximumPoint() + ":" + region.getVolume() + ":" + pointsOf(selector);
    }

    private static double distance(ServerPlayerEntity player, BlockVector3 point) {
        Vec3d eye = player.getEyePos();
        double dx = point.x() + 0.5 - eye.x;
        double dy = point.y() + 0.5 - eye.y;
        double dz = point.z() + 0.5 - eye.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static BlockVector3 centroid(List<BlockVector3> points) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (BlockVector3 point : points) {
            x += point.x();
            y += point.y();
            z += point.z();
        }
        int count = Math.max(1, points.size());
        return BlockVector3.at(Math.floorDiv(x, count), Math.floorDiv(y, count), Math.floorDiv(z, count));
    }

    private static Text message(String text, Formatting color) {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD).append(Text.literal(text).formatted(color));
    }

    private static final class State {
        private final SuperwandPoints points = new SuperwandPoints();
        private SuperwandMode mode = SuperwandMode.TRACE;
        private double relativeDistance;
        private Grab grab;
        private String signature;
    }

    private record Grab(int index, double distance, BlockVector3 held) {
    }
}
