package com.komainu.moxia.path;

import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class PathManager {
    public static final int MAX_POINTS = 32;
    private static final Map<UUID, MutablePath> PATHS = new ConcurrentHashMap<>();
    private static final Set<UUID> CAPTURE_MODES = ConcurrentHashMap.newKeySet();

    private PathManager() {
    }

    public static BlockVector3 addTarget(ServerPlayerEntity player) {
        HitResult result = player.raycast(160.0, 1.0F, false);
        if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("Mira la cara de un bloque para añadir un punto");
        }
        return add(player.getUuid(), hit.getBlockPos().offset(hit.getSide()));
    }

    public static BlockVector3 addHere(ServerPlayerEntity player) {
        return add(player.getUuid(), player.getBlockPos());
    }

    public static BlockVector3 add(UUID playerId, BlockPos point) {
        return add(playerId, BlockVector3.at(point.getX(), point.getY(), point.getZ()));
    }

    public static BlockVector3 add(UUID playerId, BlockVector3 point) {
        MutablePath path = PATHS.computeIfAbsent(playerId, ignored -> new MutablePath());
        if (path.points.size() >= MAX_POINTS) {
            throw new IllegalArgumentException("La trayectoria admite un máximo de " + MAX_POINTS + " puntos");
        }
        if (!path.points.isEmpty() && path.points.getLast().equals(point)) {
            return point;
        }
        path.history.record(path.state());
        path.points.add(point);
        return point;
    }

    public static void replace(UUID playerId, List<BlockVector3> points) {
        replace(playerId, points, false);
    }

    public static void replace(UUID playerId, List<BlockVector3> points, boolean closed) {
        if (points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("La selección tiene más de " + MAX_POINTS + " puntos");
        }
        MutablePath path = PATHS.computeIfAbsent(playerId, ignored -> new MutablePath());
        path.change(new PathHistory.State(points, closed));
    }

    /**
     * Aplica una edición sobre los puntos actuales y la anota en el historial si cambia algo.
     * Devuelve el estado resultante.
     */
    public static PathHistory.State update(UUID playerId, UnaryOperator<PathHistory.State> edit) {
        MutablePath path = PATHS.computeIfAbsent(playerId, ignored -> new MutablePath());
        PathHistory.State next = edit.apply(path.state());
        if (next.points().size() > MAX_POINTS) {
            throw new IllegalArgumentException("La trayectoria admite un máximo de " + MAX_POINTS
                    + " puntos (saldrían " + next.points().size() + ")");
        }
        path.change(next);
        return next;
    }

    public static Optional<PathHistory.State> undo(UUID playerId) {
        MutablePath path = PATHS.get(playerId);
        if (path == null) {
            return Optional.empty();
        }
        Optional<PathHistory.State> previous = path.history.undo(path.state());
        previous.ifPresent(path::restore);
        return previous;
    }

    public static Optional<PathHistory.State> redo(UUID playerId) {
        MutablePath path = PATHS.get(playerId);
        if (path == null) {
            return Optional.empty();
        }
        Optional<PathHistory.State> next = path.history.redo(path.state());
        next.ifPresent(path::restore);
        return next;
    }

    /** Pasos disponibles para deshacer y rehacer, en ese orden. */
    public static int[] historySteps(UUID playerId) {
        MutablePath path = PATHS.get(playerId);
        return path == null ? new int[]{0, 0} : new int[]{path.history.undoSteps(), path.history.redoSteps()};
    }

    public static boolean removeLast(UUID playerId) {
        MutablePath path = PATHS.get(playerId);
        if (path == null || path.points.isEmpty()) {
            return false;
        }
        path.history.record(path.state());
        path.points.removeLast();
        return true;
    }

    public static BlockVector3 move(UUID playerId, int index, BlockPos point) {
        return move(playerId, index, BlockVector3.at(point.getX(), point.getY(), point.getZ()));
    }

    public static BlockVector3 move(UUID playerId, int index, BlockVector3 point) {
        MutablePath path = PATHS.get(playerId);
        if (path == null || index < 0 || index >= path.points.size()) {
            throw new IllegalArgumentException("El punto seleccionado ya no existe");
        }
        if (path.points.get(index).equals(point)) {
            return point;
        }
        path.history.recordMove(path.state(), index, System.nanoTime());
        path.points.set(index, point);
        return point;
    }

    public static boolean remove(UUID playerId, int index) {
        MutablePath path = PATHS.get(playerId);
        if (path == null || index < 0 || index >= path.points.size()) {
            return false;
        }
        path.history.record(path.state());
        path.points.remove(index);
        return true;
    }

    /** Vacía la trayectoria; se puede deshacer. */
    public static void clear(UUID playerId) {
        MutablePath path = PATHS.get(playerId);
        if (path != null) {
            path.change(new PathHistory.State(List.of(), false));
        }
    }

    public static boolean toggleClosed(UUID playerId) {
        return setClosed(playerId, !saved(playerId).closed());
    }

    public static boolean setClosed(UUID playerId, boolean closed) {
        MutablePath path = PATHS.computeIfAbsent(playerId, ignored -> new MutablePath());
        path.change(new PathHistory.State(path.points, closed));
        return path.closed;
    }

    public static boolean toggleCaptureMode(UUID playerId) {
        if (CAPTURE_MODES.remove(playerId)) {
            return false;
        }
        CAPTURE_MODES.add(playerId);
        return true;
    }

    public static boolean setCaptureMode(UUID playerId, boolean active) {
        if (active) {
            CAPTURE_MODES.add(playerId);
        } else {
            CAPTURE_MODES.remove(playerId);
        }
        return active;
    }

    public static boolean isCaptureMode(UUID playerId) {
        return CAPTURE_MODES.contains(playerId);
    }

    public static PathSnapshot saved(UUID playerId) {
        MutablePath path = PATHS.get(playerId);
        if (path == null) {
            return new PathSnapshot(List.of(), false, "manual");
        }
        return new PathSnapshot(path.points, path.closed, "manual");
    }

    public static void remove(UUID playerId) {
        PATHS.remove(playerId);
        CAPTURE_MODES.remove(playerId);
    }

    public static void clearAll() {
        PATHS.clear();
        CAPTURE_MODES.clear();
    }

    private static final class MutablePath {
        private final List<BlockVector3> points = new ArrayList<>();
        private final PathHistory history = new PathHistory();
        private boolean closed;

        private PathHistory.State state() {
            return new PathHistory.State(points, closed);
        }

        private void change(PathHistory.State next) {
            PathHistory.State current = state();
            if (current.equals(next)) {
                return;
            }
            history.record(current);
            restore(next);
        }

        private void restore(PathHistory.State state) {
            points.clear();
            points.addAll(state.points());
            closed = state.closed();
        }
    }
}
