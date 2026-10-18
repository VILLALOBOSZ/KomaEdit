package com.komainu.moxia.ivy;

import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.sculpt.SnapshotCaptureService;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /komaedit ivy}: captura el entorno de las semillas repartido por ticks, hace crecer la hiedra en un worker y
 * la coloca por lotes, deshacible con //undo. Solo ocupa celdas que seguían siendo aire al colocarlas.
 */
public final class IvyService {
    private static final int MAX_HORIZONTAL_RADIUS = 72;
    private static final int MAX_VERTICAL_RADIUS = 48;
    private static final long MAX_CAPTURE_VOLUME = 3_000_000L;
    /** Tope propio de la hiedra: más allá deja de parecer una planta y solo gasta memoria. */
    private static final int MAX_BLOCKS = 200_000;
    private static final int FREE = 1 << 6;
    private static final int RESTS = 1 << 7;
    private static final Map<String, BlockState> STATES = new HashMap<>();

    private IvyService() {
    }

    /** @return mensaje para el jugador con lo que se va a hacer */
    public static String start(ServerPlayerEntity player, IvySettings requested) {
        KomaPermissions.requireBuild(player, "ivy");
        UUID playerId = player.getUuid();
        if (BatchEditService.status(playerId).isPresent()) {
            throw new IllegalStateException("Ya tienes una edición en curso; usa /komaedit job status");
        }
        Seeds seeds = seeds(player);
        long seed = requested.seed().orElseGet(() -> ThreadLocalRandom.current().nextLong(1L, 1_000_000L));
        IvySettings settings = requested.withSeed(seed);
        Region region = region(seeds.points(), settings.reach());

        SnapshotCaptureService.cancel(playerId);
        AsyncEngine.cancel(playerId);
        MinecraftServer server = player.getServer();
        RegistryKey<World> worldKey = player.getWorld().getRegistryKey();
        int maxBlocks = KomaPermissions.can(player, KomaPermissions.LIMIT_UNRESTRICTED)
                ? MAX_BLOCKS
                : Math.min(MAX_BLOCKS, KomaEditConfig.settings().maxBlocksPerOperation());
        SnapshotCaptureService.submit(
                player,
                WorldEditService.context(player).world(),
                region.center(),
                region.horizontal(),
                region.vertical(),
                snapshot -> grow(server, playerId, worldKey, snapshot, seeds.points(), settings, maxBlocks),
                throwable -> notify(server, playerId, "No se pudo capturar el entorno: " + readable(throwable), true)
        );
        return "Capturando el entorno de " + seeds.points().size() + " semilla(s) " + seeds.source()
                + " · " + settings.style().displayName() + " · semilla " + seed;
    }

    public static boolean cancel(UUID playerId) {
        boolean capture = SnapshotCaptureService.cancel(playerId);
        boolean task = AsyncEngine.cancel(playerId);
        return capture || task;
    }

    /** Semillas: los puntos de trayectoria marcados con P o, si no hay, la celda delante de la cara que se mira. */
    private static Seeds seeds(ServerPlayerEntity player) {
        PathSnapshot path = PathManager.saved(player.getUuid());
        if (!path.points().isEmpty()) {
            List<IvyGrower.Point> points = new ArrayList<>();
            for (BlockVector3 point : path.points()) {
                points.add(new IvyGrower.Point(point.x(), point.y(), point.z()));
            }
            return new Seeds(points, "de la trayectoria");
        }
        HitResult result = player.raycast(160.0, 1.0F, false);
        if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("Mira la cara de un bloque o marca semillas con P");
        }
        BlockPos cell = hit.getBlockPos().offset(hit.getSide());
        return new Seeds(List.of(new IvyGrower.Point(cell.getX(), cell.getY(), cell.getZ())), "del bloque que miras");
    }

    private static Region region(List<IvyGrower.Point> points, int reach) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (IvyGrower.Point point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }
        int halfHorizontal = (Math.max(maxX - minX, maxZ - minZ) + 1) / 2;
        int halfVertical = (maxY - minY + 1) / 2;
        if (halfHorizontal > MAX_HORIZONTAL_RADIUS - 8 || halfVertical > MAX_VERTICAL_RADIUS - 8) {
            throw new IllegalArgumentException("Las semillas están demasiado separadas; usa grupos más pequeños");
        }
        int horizontal = Math.min(MAX_HORIZONTAL_RADIUS, halfHorizontal + reach);
        int vertical = Math.min(MAX_VERTICAL_RADIUS, halfVertical + reach);
        while (volume(horizontal, vertical) > MAX_CAPTURE_VOLUME && horizontal > halfHorizontal + 8) {
            horizontal--;
        }
        BlockVector3 center = BlockVector3.at(
                Math.floorDiv(minX + maxX, 2),
                Math.floorDiv(minY + maxY, 2),
                Math.floorDiv(minZ + maxZ, 2)
        );
        return new Region(center, horizontal, vertical);
    }

    private static long volume(int horizontal, int vertical) {
        long side = 2L * horizontal + 1;
        return side * side * (2L * vertical + 1);
    }

    /** Hilo del servidor: clasifica los estados capturados (una vez cada uno) y manda el crecimiento al worker. */
    private static void grow(
            MinecraftServer server,
            UUID playerId,
            RegistryKey<World> worldKey,
            WorldSnapshot snapshot,
            List<IvyGrower.Point> points,
            IvySettings settings,
            int maxBlocks
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !player.getWorld().getRegistryKey().equals(worldKey)) {
            return;
        }
        Map<BlockState, Integer> masks = new IdentityHashMap<>();
        Map<BlockState, Integer> rawIds = new IdentityHashMap<>();
        for (BlockState state : snapshot.distinctStates()) {
            net.minecraft.block.BlockState minecraft = FabricAdapter.adapt(state);
            masks.put(state, classify(minecraft));
            rawIds.put(state, Block.getRawIdFromState(minecraft));
        }
        player.sendMessage(message("La hiedra está creciendo…"), true);
        SnapshotTerrain terrain = new SnapshotTerrain(snapshot, masks);
        long seed = settings.seed().orElseThrow();
        AsyncEngine.submit(
                playerId,
                () -> IvyGrower.grow(terrain, points, settings, seed, maxBlocks,
                        () -> Thread.currentThread().isInterrupted()),
                result -> server.execute(() -> place(server, playerId, worldKey, snapshot, rawIds, settings, result)),
                throwable -> {
                    if (!(throwable instanceof CancellationException)) {
                        notify(server, playerId, "No se pudo calcular la hiedra: " + readable(throwable), true);
                    }
                }
        );
    }

    private static int classify(net.minecraft.block.BlockState state) {
        if (state.isAir()) {
            return FREE;
        }
        VoxelShape shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (Block.isFaceFullSquare(shape, direction)) {
                mask |= 1 << direction.ordinal();
            }
        }
        if (!shape.isEmpty() && state.getFluidState().isEmpty()) {
            mask |= RESTS;
        }
        return mask;
    }

    private static void place(
            MinecraftServer server,
            UUID playerId,
            RegistryKey<World> worldKey,
            WorldSnapshot snapshot,
            Map<BlockState, Integer> rawIds,
            IvySettings settings,
            IvyResult result
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !player.getWorld().getRegistryKey().equals(worldKey)) {
            return;
        }
        if (result.blocks().isEmpty()) {
            player.sendMessage(error("No hay ninguna superficie donde agarrarse a menos de "
                    + IvyGrower.SEED_SEARCH + " bloques de las semillas"), false);
            return;
        }
        try {
            PackedBlockPlan.Builder builder = PackedBlockPlan.builder(result.blocks().size());
            for (IvyBlock block : result.blocks()) {
                // Si alguien ha construido ahí mientras crecía, esa celda se respeta.
                int expected = rawIds.get(snapshot.get(block.x(), block.y(), block.z()));
                builder.addConditionalOverride(block.x(), block.y(), block.z(), expected, state(block));
            }
            PackedBlockPlan built = builder.build();
            int queued = BatchEditService.submit(player, built, "air", "Hiedra viva");
            if (PlanPreviews.holds(player.getUuid(), built)) {
                return;
            }
            StringBuilder text = new StringBuilder("Hiedra viva en cola: " + queued + " bloques · "
                    + settings.style().displayName() + " · semilla " + settings.seed().orElseThrow());
            if (result.seedsSkipped() > 0) {
                text.append(" · ").append(result.seedsSkipped()).append(" semilla(s) sin superficie cerca");
            }
            if (result.truncated()) {
                text.append(" · se paró en el límite de bloques");
            }
            text.append(" · //undo para deshacer");
            player.sendMessage(Text.literal("KomaEdit · ").formatted(Formatting.GOLD)
                    .append(Text.literal(text.toString()).formatted(Formatting.GREEN)), false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(error(readable(exception)), false);
        }
    }

    /** Estado de WorldEdit del bloque; se analiza una vez por estado distinto con el analizador de Minecraft. */
    static synchronized BlockState state(IvyBlock block) {
        String text = block.stateString();
        BlockState cached = STATES.get(text);
        if (cached != null) {
            return cached;
        }
        try {
            net.minecraft.block.BlockState minecraft = BlockArgumentParser.block(
                    Registries.BLOCK.getReadOnlyWrapper(), text, false
            ).blockState();
            BlockState adapted = FabricAdapter.adapt(minecraft);
            STATES.put(text, adapted);
            return adapted;
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("Estado de hiedra no válido: " + text, exception);
        }
    }

    private static void notify(MinecraftServer server, UUID playerId, String value, boolean failure) {
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(failure ? error(value) : message(value), false);
            }
        });
    }

    private static Text message(String value) {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD)
                .append(Text.literal(value).formatted(Formatting.GRAY));
    }

    private static Text error(String value) {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD)
                .append(Text.literal(value).formatted(Formatting.RED));
    }

    private static String readable(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record Seeds(List<IvyGrower.Point> points, String source) {
    }

    private record Region(BlockVector3 center, int horizontal, int vertical) {
    }

    /** La captura vista como {@link IvyTerrain}; fuera de ella no hay nada donde crecer. */
    private record SnapshotTerrain(WorldSnapshot snapshot, Map<BlockState, Integer> masks) implements IvyTerrain {
        @Override
        public boolean free(int x, int y, int z) {
            return (mask(x, y, z) & FREE) != 0;
        }

        @Override
        public boolean sturdy(int x, int y, int z, int face) {
            return (mask(x, y, z) & (1 << face)) != 0;
        }

        @Override
        public boolean rests(int x, int y, int z) {
            return (mask(x, y, z) & RESTS) != 0;
        }

        private int mask(int x, int y, int z) {
            if (!snapshot.contains(x, y, z)) {
                return 0;
            }
            Integer mask = masks.get(snapshot.get(x, y, z));
            return mask == null ? 0 : mask;
        }
    }
}
