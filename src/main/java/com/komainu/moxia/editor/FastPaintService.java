package com.komainu.moxia.editor;

import com.komainu.moxia.worldedit.EditHistory;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.performance.ServerWork;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class FastPaintService {
    private static final int MAX_CANDIDATES_PER_TICK = 45_000;
    private static final long MAX_NANOS_PER_TICK = 9_000_000L;
    private static final Map<UUID, Stroke> STROKES = new LinkedHashMap<>();

    private FastPaintService() {
    }

    public static void begin(ServerPlayerEntity player, int requestedRadius, String patternInput) {
        int radius = Math.clamp(requestedRadius, 1, 12);
        Stroke existing = STROKES.get(player.getUuid());
        if (existing != null && existing.radius == radius && existing.patternInput.equals(patternInput)) {
            existing.ending = false;
            existing.trail.restart();
            return;
        }
        if (existing != null) {
            finish(existing, player, true);
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        EditSession editSession = context.session().createEditSession(context.actor());
        editSession.setBlockChangeLimit(strokeLimit(editSession.getBlockChangeLimit(),
                OperationPolicy.limit(player)));
        STROKES.put(player.getUuid(), new Stroke(
                player.getUuid(), radius, patternInput, pattern, editSession, context
        ));
    }

    /**
     * Bloques que puede cambiar un trazo: el {@code //limit} de WorldEdit ({@code -1}, sin límite) y el del servidor
     * ({@link OperationPolicy#limit}), el menor.
     */
    static int strokeLimit(int worldEditLimit, long serverLimit) {
        if (serverLimit == Long.MAX_VALUE) {
            return worldEditLimit;
        }
        int server = (int) Math.min(serverLimit, Integer.MAX_VALUE);
        return worldEditLimit < 0 ? server : Math.min(worldEditLimit, server);
    }

    public static void dab(ServerPlayerEntity player, BlockPos center, int radius, String patternInput) {
        Stroke stroke = STROKES.get(player.getUuid());
        if (stroke == null || stroke.radius != Math.clamp(radius, 1, 12)
                || !stroke.patternInput.equals(patternInput)) {
            begin(player, radius, patternInput);
            stroke = STROKES.get(player.getUuid());
        }
        stroke.trail.add(center);
    }

    public static void end(ServerPlayerEntity player) {
        Stroke stroke = STROKES.get(player.getUuid());
        if (stroke != null) {
            stroke.ending = true;
            if (stroke.current == null && stroke.trail.isEmpty()) {
                STROKES.remove(player.getUuid());
                finish(stroke, player, false);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (STROKES.isEmpty()) {
            return;
        }
        long deadline = ServerWork.deadline(MAX_NANOS_PER_TICK);
        for (Stroke stroke : java.util.List.copyOf(STROKES.values())) {
            if (System.nanoTime() >= deadline || !STROKES.containsKey(stroke.playerId)) {
                break;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(stroke.playerId);
            if (player == null) {
                STROKES.remove(stroke.playerId);
                finish(stroke, null, true);
                continue;
            }
            process(stroke, player, deadline);
            if (stroke.ending && stroke.current == null && stroke.trail.isEmpty()) {
                STROKES.remove(stroke.playerId);
                finish(stroke, player, false);
            }
        }
    }

    public static void remove(UUID playerId) {
        Stroke stroke = STROKES.remove(playerId);
        if (stroke != null) {
            finish(stroke, null, true);
        }
    }

    public static void shutdown() {
        for (Stroke stroke : java.util.List.copyOf(STROKES.values())) {
            finish(stroke, null, true);
        }
        STROKES.clear();
    }

    private static void process(Stroke stroke, ServerPlayerEntity player, long deadline) {
        ServerWorld world = player.getServerWorld();
        int candidates = 0;
        try {
            while (candidates < MAX_CANDIDATES_PER_TICK && System.nanoTime() < deadline) {
                if (stroke.current == null) {
                    BlockPos next = stroke.trail.poll();
                    if (next == null) {
                        return;
                    }
                    stroke.current = new Dab(next, stroke.radius);
                }
                Dab dab = stroke.current;
                while (dab.cursor < dab.total && candidates < MAX_CANDIDATES_PER_TICK) {
                    int cursor = dab.cursor++;
                    candidates++;
                    int x = cursor % dab.side - dab.radius;
                    int y = cursor / dab.side % dab.side - dab.radius;
                    int z = cursor / (dab.side * dab.side) - dab.radius;
                    if (x * x + y * y + z * z > dab.radiusSquared) {
                        continue;
                    }
                    BlockPos position = dab.center.add(x, y, z);
                    if (!world.isInBuildLimit(position) || !world.isChunkLoaded(position)) {
                        continue;
                    }
                    long packed = position.asLong();
                    if (!stroke.painted.add(packed)) {
                        continue;
                    }
                    BlockState existing = world.getBlockState(position);
                    if (existing.isAir() || !existing.getFluidState().isEmpty()) {
                        continue;
                    }
                    stroke.editSession.setBlock(
                            BlockVector3.at(position.getX(), position.getY(), position.getZ()),
                            stroke.pattern
                    );
                }
                if (dab.cursor >= dab.total) {
                    stroke.current = null;
                }
            }
        } catch (MaxChangedBlocksException | RuntimeException exception) {
            STROKES.remove(stroke.playerId);
            finish(stroke, player, true);
            KomaEditMod.LOGGER.error("Fast paint stroke failed for {}", stroke.playerId, exception);
            player.sendMessage(Feedback.line(Feedback.Level.ERROR, "La pintura falló: " + readable(exception)), true);
        }
    }

    private static void finish(Stroke stroke, ServerPlayerEntity player, boolean cancelled) {
        try {
            stroke.editSession.close();
        } finally {
            if (stroke.editSession.size() > 0) {
                EditHistory.label(stroke.editSession, "Trazo");
                stroke.context.session().remember(stroke.editSession);
            }
        }
        if (player != null && stroke.editSession.size() > 0) {
            String state = cancelled ? "parcial" : "completo";
            player.sendMessage(Feedback.line(cancelled ? Feedback.Level.WARN : Feedback.Level.OK,
                    Feedback.format("Trazo %s: %s bloques · Ctrl+Z", state, stroke.editSession.size())), true);
        }
    }

    private static String readable(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private static final class Stroke {
        private final UUID playerId;
        private final int radius;
        private final String patternInput;
        private final Pattern pattern;
        private final EditSession editSession;
        private final WorldEditService.PlayerContext context;
        private final DabTrail trail;
        private final LongOpenHashSet painted = new LongOpenHashSet();
        private Dab current;
        private boolean ending;

        private Stroke(
                UUID playerId,
                int radius,
                String patternInput,
                Pattern pattern,
                EditSession editSession,
                WorldEditService.PlayerContext context
        ) {
            this.playerId = playerId;
            this.radius = radius;
            this.trail = new DabTrail(radius);
            this.patternInput = patternInput;
            this.pattern = pattern;
            this.editSession = editSession;
            this.context = context;
        }
    }

    private static final class Dab {
        private final BlockPos center;
        private final int radius;
        private final int radiusSquared;
        private final int side;
        private final int total;
        private int cursor;

        private Dab(BlockPos center, int radius) {
            this.center = center;
            this.radius = radius;
            radiusSquared = radius * radius;
            side = radius * 2 + 1;
            total = side * side * side;
        }
    }
}
