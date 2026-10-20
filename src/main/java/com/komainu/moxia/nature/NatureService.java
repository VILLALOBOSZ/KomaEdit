package com.komainu.moxia.nature;

import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.SculptManager;
import com.komainu.moxia.sculpt.SnapshotCaptureService;
import com.komainu.moxia.studio.net.StudioAim;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Punto de entrada de {@code /komaedit tree}, {@code /komaedit rock} y sus pinceles. En el hilo del servidor busca el
 * suelo, resuelve materiales, máscara y semillas; el cálculo va a un worker y se coloca con el motor común, en una
 * sola operación de //undo. Las órdenes llevan la vista previa universal ({@code -v}) y la barra de progreso; los
 * pinceles van por {@link SculptManager#run}, con su holograma ({@code -p}).
 */
public final class NatureService {
    /** Como mucho, un árbol o una roca por punto marcado y estos puntos por orden. */
    public static final int MAX_SITES = 16;
    private static final int TREE_COLOR = 0x7CC35A;
    private static final int ROCK_COLOR = 0xA8A39A;
    private static final double BRUSH_VARIATION = 0.2;
    private static final double GROUP_VARIATION = 0.15;

    private NatureService() {
    }

    /** {@code /komaedit tree}: en cada punto de la trayectoria o, si no hay, donde mira el jugador. */
    public static String tree(ServerPlayerEntity player, String input) {
        TreeSettings settings = NaturePresets.tree(player.getUuid(), input);
        KomaPermissions.requireBuild(player, "tree");
        commandPreviewFlag(settings.preview());
        Anchors anchors = anchors(player);
        long seed = settings.seed().orElseGet(NatureService::randomSeed);
        double variation = variation(settings.variation(), anchors.points().size() > 1 ? GROUP_VARIATION : 0.0);
        runCommand(player, trees(player, settings, anchors.points(), seed, variation));
        return settings.describe() + " " + anchors.source() + " · semilla " + seed + " (repítelo con -s " + seed + ")";
    }

    /** {@code /komaedit rock}: igual que los árboles. */
    public static String rock(ServerPlayerEntity player, String input) {
        RockSettings settings = NaturePresets.rock(player.getUuid(), input);
        KomaPermissions.requireBuild(player, "rock");
        commandPreviewFlag(settings.preview());
        Anchors anchors = anchors(player);
        long seed = settings.seed().orElseGet(NatureService::randomSeed);
        double variation = variation(settings.variation(), anchors.points().size() > 1 ? GROUP_VARIATION : 0.0);
        runCommand(player, rocks(player, settings, anchors.points(), seed, variation));
        return settings.describe() + " " + anchors.source() + " · semilla " + seed + " (repítela con -s " + seed + ")";
    }

    /** Un clic del pincel de árboles: una variante nueva cada vez salvo que fije la semilla. */
    public static void brushTree(ServerPlayerEntity player, TreeSettings settings, BlockHitResult hit) {
        KomaPermissions.requireBuild(player, "brush.tree");
        long seed = settings.seed().orElseGet(NatureService::randomSeed);
        SculptManager.run(player, trees(player, settings, List.of(target(hit)), seed,
                variation(settings.variation(), BRUSH_VARIATION)), settings.preview());
    }

    /** Un clic del pincel de rocas. */
    public static void brushRock(ServerPlayerEntity player, RockSettings settings, BlockHitResult hit) {
        KomaPermissions.requireBuild(player, "brush.rock");
        long seed = settings.seed().orElseGet(NatureService::randomSeed);
        SculptManager.run(player, rocks(player, settings, List.of(target(hit)), seed,
                variation(settings.variation(), BRUSH_VARIATION)), settings.preview());
    }

    /** Detiene la captura o el cálculo en curso. */
    public static boolean cancel(ServerPlayerEntity player) {
        boolean capture = SnapshotCaptureService.cancel(player.getUuid());
        boolean task = AsyncEngine.cancel(player.getUuid());
        return capture || task;
    }

    /** En las órdenes la vista previa es la universal ({@code -v}); {@code -p} es la de los pinceles. */
    private static void commandPreviewFlag(boolean brushPreview) {
        if (brushPreview) {
            throw new IllegalArgumentException("En la orden la vista previa es -v (o --preview); -p es la de los "
                    + "pinceles");
        }
    }

    /**
     * Camino de las órdenes: captura repartida entre ticks, cálculo en un worker y {@code BatchEditService}. La
     * petición de la orden viaja con ellos, así que {@code -v}, la preferencia «previsualizar siempre», la barra de
     * progreso y {@code /komaedit cancel} funcionan como en el resto del mod.
     */
    private static void runCommand(ServerPlayerEntity player, NatureOperation operation) {
        UUID playerId = player.getUuid();
        SnapshotCaptureService.cancel(playerId);
        AsyncEngine.cancel(playerId);
        MinecraftServer server = player.getServer();
        RegistryKey<World> worldKey = player.getWorld().getRegistryKey();
        BrushOperation.Capture capture = operation.capture();
        long side = capture.horizontalRadius() * 2L + 1L;
        long volume = side * side * (capture.verticalRadius() * 2L + 1L);
        if (volume > SculptManager.MAX_OPERATION_CAPTURE) {
            throw new IllegalArgumentException(operation.displayName() + ": la zona es demasiado grande (" + ProgressLine.group(volume)
                    + " bloques; máximo " + SculptManager.MAX_OPERATION_CAPTURE + "); usa menos puntos o más juntos");
        }
        SnapshotCaptureService.submit(player, WorldEditService.context(player).world(), capture.center(),
                capture.horizontalRadius(), capture.verticalRadius(),
                snapshot -> AsyncEngine.submit(playerId, operation.displayName(), () -> operation.plan(snapshot),
                        plan -> server.execute(() -> place(server, playerId, worldKey, operation, plan)),
                        throwable -> {
                            if (!(throwable instanceof CancellationException)) {
                                notify(server, playerId, operation.displayName() + ": " + readable(throwable));
                            }
                        }),
                throwable -> notify(server, playerId, "No se pudo capturar el terreno: " + readable(throwable)));
    }

    private static void place(MinecraftServer server, UUID playerId, RegistryKey<World> worldKey,
                              NatureOperation operation, PackedBlockPlan plan) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !player.getWorld().getRegistryKey().equals(worldKey)) {
            return;
        }
        if (plan.size() == 0) {
            player.sendMessage(message(operation.displayName() + ": no hay nada que cambiar aquí", Formatting.YELLOW),
                    false);
            return;
        }
        try {
            int queued = BatchEditService.submit(player, plan, "air", operation.displayName());
            if (PlanPreviews.holds(playerId, plan)) {
                return;
            }
            QuietMode.status(player, message(operation.displayName() + " en cola: " + ProgressLine.group(queued) + " bloques · //undo para "
                    + "deshacer", Formatting.GREEN));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(message(operation.displayName() + ": " + exception.getMessage(), Formatting.RED), false);
        }
    }

    private static void notify(MinecraftServer server, UUID playerId, String text) {
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message(text, Formatting.RED), false);
            }
        });
    }

    private static Text message(String text, Formatting color) {
        return Feedback.prefix().append(Text.literal(text).formatted(color));
    }

    private static String readable(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        String text = current.getMessage();
        return text == null || text.isBlank() ? current.getClass().getSimpleName() : text;
    }

    private static NatureOperation trees(ServerPlayerEntity player, TreeSettings settings, List<BlockPos> points,
                                         long seed, double variation) {
        NatureMaterials materials = NatureMaterials.tree(player, settings, seed);
        NatureMask mask = NatureMask.parse(player, settings.mask());
        List<NatureOperation.Site> sites = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            long own = seed + index * 1_000_003L;
            TreeSettings varied = settings.vary(variation, new SplittableRandom(own)).withSeed(own);
            BlockPos base = ground(player.getWorld(), points.get(index));
            int x = base.getX();
            int y = base.getY();
            int z = base.getZ();
            Bounds reach = Bounds.around(x, y, z, TreeGrower.reach(varied), 14,
                    (int) Math.ceil(varied.height() * 1.2) + 10);
            sites.add(new NatureOperation.Site(reach, (terrain, bounds, limit, cancelled) ->
                    TreeGrower.grow(varied, terrain, bounds, x, y, z, own, limit, cancelled)));
        }
        String name = points.size() == 1 ? settings.species().displayName()
                : points.size() + " árboles (" + settings.species().displayName().toLowerCase(Locale.ROOT) + ")";
        return new NatureOperation(name, TREE_COLOR, sites, materials, mask, limit(player));
    }

    private static NatureOperation rocks(ServerPlayerEntity player, RockSettings settings, List<BlockPos> points,
                                         long seed, double variation) {
        NatureMaterials materials = NatureMaterials.rock(player, settings, seed);
        NatureMask mask = NatureMask.parse(player, settings.mask());
        List<NatureOperation.Site> sites = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            long own = seed + index * 1_000_003L;
            RockSettings varied = settings.vary(variation, new SplittableRandom(own)).withSeed(own);
            BlockPos base = ground(player.getWorld(), points.get(index));
            int x = base.getX();
            int y = base.getY();
            int z = base.getZ();
            int tall = (int) Math.ceil(RockShaper.height(varied));
            Bounds reach = Bounds.around(x, y, z, RockShaper.reach(varied), tall + 20, tall + 8);
            sites.add(new NatureOperation.Site(reach, (terrain, bounds, limit, cancelled) ->
                    RockShaper.shape(varied, terrain, bounds, x, y, z, own, limit, cancelled)));
        }
        String name = points.size() == 1 ? settings.type().displayName()
                : points.size() + " rocas (" + settings.type().displayName().toLowerCase(Locale.ROOT) + ")";
        return new NatureOperation(name, ROCK_COLOR, sites, materials, mask, limit(player));
    }

    private static int limit(ServerPlayerEntity player) {
        return KomaPermissions.can(player, KomaPermissions.LIMIT_UNRESTRICTED) ? 4_000_000
                : KomaEditConfig.settings().maxBlocksPerOperation();
    }

    private static double variation(double requested, double fallback) {
        return Double.isNaN(requested) ? fallback : requested;
    }

    private static long randomSeed() {
        return ThreadLocalRandom.current().nextLong(1L, 1_000_000L);
    }

    private record Anchors(List<BlockPos> points, String source) {
    }

    /** Los puntos de la trayectoria (P) o, si no hay, el bloque que se mira (con el Studio, donde se hizo clic). */
    private static Anchors anchors(ServerPlayerEntity player) {
        PathSnapshot path = PathManager.saved(player.getUuid());
        if (!path.points().isEmpty()) {
            if (path.points().size() > MAX_SITES) {
                throw new IllegalArgumentException("Hay " + path.points().size() + " puntos marcados; como mucho "
                        + MAX_SITES + " a la vez (/komaedit path clear para mirar un bloque)");
            }
            List<BlockPos> points = new ArrayList<>();
            for (BlockVector3 point : path.points()) {
                points.add(new BlockPos(point.x(), point.y(), point.z()));
            }
            return new Anchors(points, points.size() == 1 ? "en el punto marcado"
                    : "en los " + points.size() + " puntos marcados");
        }
        HitResult result = StudioAim.target(player, 160.0);
        if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("Mira el suelo donde va (hasta 160 bloques) o marca puntos con P");
        }
        return new Anchors(List.of(hit.getBlockPos()), StudioAim.clicked(player) ? "donde hiciste clic"
                : "donde miras");
    }

    private static BlockPos target(BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("Mira un bloque del suelo");
        }
        return hit.getBlockPos();
    }

    /**
     * Celda de la base: la primera libre sobre el suelo. Desde un bloque de suelo sube hasta su superficie (por si se
     * mira la ladera de un cerro); desde el aire, una planta o unas hojas baja hasta encontrarlo.
     */
    static BlockPos ground(World world, BlockPos start) {
        BlockPos position = start;
        if (solid(world, position)) {
            for (int step = 0; step < 24 && solid(world, position.up()); step++) {
                position = position.up();
            }
            return position.up();
        }
        for (int step = 0; step < 64 && position.getY() > world.getBottomY(); step++) {
            position = position.down();
            if (solid(world, position)) {
                return position.up();
            }
        }
        throw new IllegalArgumentException("No hay suelo bajo " + start.getX() + ", " + start.getY() + ", "
                + start.getZ());
    }

    private static boolean solid(World world, BlockPos position) {
        BlockState state = world.getBlockState(position);
        return !state.isAir() && state.getFluidState().isEmpty() && !(state.getBlock() instanceof LeavesBlock)
                && state.isSolidBlock(world, position);
    }
}
