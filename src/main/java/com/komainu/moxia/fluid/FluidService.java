package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.PendingBuilds;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.studio.net.StudioAim;
import com.komainu.moxia.utility.MinecraftKinds;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Lanza las herramientas de agua por el motor común: lee la zona alrededor del clic repartiendo la lectura entre
 * ticks ({@link RegionOperationService#capture}), calcula en un worker de {@link AsyncEngine} y escribe con
 * {@link BatchEditService}: vista previa con {@code -v}, confirmación si es grande, progreso y una sola entrada de
 * //undo. Lo que no se puede hacer bien (un hueco que se escapa, una cascada que inundaría) no escribe nada: lo dice
 * y lo señala en el mundo ({@link LeakMarkers}).
 */
public final class FluidService {
    public static final String FILL = "fluid.fill";
    public static final String BALL = "fluid.ball";
    public static final String FALL = "fluid.fall";
    public static final String CLEAN = "fluid.clean";
    /** Hasta dónde se mira para el clic. */
    public static final double REACH = 160.0;
    public static final int DEFAULT_RADIUS = 32;
    public static final int MAX_RADIUS = 128;
    public static final int DEFAULT_LIMIT = 50_000;
    /** Corrientes como mucho de una bola o una cascada. */
    public static final int MAX_FLOWS = 20_000;
    /** Lo más que se mira hacia abajo desde una cascada. */
    public static final int FALL_DEPTH = 96;
    private static final Runnable CANCELLATION = () -> {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    };

    private FluidService() {
    }

    /** Lo que sale del cálculo: un plan o, si no se puede, el aviso (sin escribir nada). */
    private record Outcome(RegionEdits edits, String detail, Consumer<ServerPlayerEntity> refusal) {
        static Outcome refuse(Consumer<ServerPlayerEntity> refusal) {
            return new Outcome(null, "", refusal);
        }
    }

    /** El bloque del clic y su cara, o un punto dado con {@code -p}. */
    private record Point(BlockPos block, BlockPos front, boolean given) {
    }

    // ---------------------------------------------------------------- rellenar

    /**
     * {@code /komaedit fluido rellenar}: llena el hueco del clic hasta el nivel si está cerrado.
     *
     * @param levelText {@code null} (el del clic), {@code clic±n}, {@code borde}, {@code ~±n} o una altura
     * @param at        un punto exacto ({@code -p}) o {@code null} para el clic
     */
    public static String fill(ServerPlayerEntity player, FluidKind fluid, String levelText,
                              FloodFill.Directions directions, int limit, int radius, boolean waterlog, int[] at) {
        Point point = point(player, at);
        ServerWorld world = player.getServerWorld();
        MinecraftKinds kinds = new MinecraftKinds();
        BlockPos start = point.given() || open(world, kinds, point.block(), fluid) ? point.block() : point.front();
        FloodFill.Level level = level(levelText, start.getY(), player.getBlockY());
        int reach = Math.clamp(radius, 4, MAX_RADIUS);
        int low = Math.min(start.getY(), level.mode() == FloodFill.LevelMode.FIXED ? level.y() : start.getY()) - reach;
        int high = Math.max(start.getY(), level.mode() == FloodFill.LevelMode.FIXED ? level.y() : start.getY())
                + (level.mode() == FloodFill.LevelMode.BRIM ? reach : 1);
        Box box = fit(player, world, start.getX(), start.getZ(), reach, low, high);
        int cap = (int) Math.min(Math.min(limit, OperationPolicy.limit(player)), Integer.MAX_VALUE - 8);
        FloodFill.Settings settings = new FloodFill.Settings(fluid, level, directions, Math.max(1, cap), waterlog,
                world.getBottomY());
        String description = "Rellenar con " + fluid.displayName();
        BlockPos seed = start;
        run(player, box.region(world), description, snapshot -> {
            FloodFill.Result result = FloodFill.plan(snapshot, kinds, seed.getX(), seed.getY(), seed.getZ(),
                    settings, CANCELLATION);
            if (!result.closed()) {
                return Outcome.refuse(current -> leaked(current, fluid, seed, result, directions, cap, box.radius(),
                        waterlog));
            }
            FluidWrites writes = new FluidWrites(fluid);
            result.write(new FloodFill.Sink() {
                @Override
                public void source(int x, int y, int z) {
                    writes.fluid(x, y, z, 0);
                }

                @Override
                public void waterlog(int x, int y, int z) {
                    writes.waterlog(x, y, z);
                }
            });
            RegionEdits edits = edits(player, snapshot, writes);
            String detail = "hasta y=" + result.level() + " · " + Feedback.count(result.filled(), "fuente", "fuentes")
                    + (result.waterlogged() > 0 ? ", " + Feedback.count(result.waterlogged(), "bloque anegado",
                    "bloques anegados") : "")
                    + (result.contacts() > 0 ? " · toca " + (fluid == FluidKind.WATER ? "lava" : "agua") + " en "
                    + result.contacts() + " sitios (saldrá piedra u obsidiana)" : "");
            return new Outcome(edits, detail, null);
        }, true);
        return null;
    }

    /** El aviso de una fuga: por qué, por dónde y hasta qué altura sí cerraría, con su botón. */
    private static void leaked(ServerPlayerEntity player, FluidKind fluid, BlockPos seed, FloodFill.Result result,
                               FloodFill.Directions directions, int limit, int radius, boolean waterlog) {
        FloodFill.Leak leak = result.leak();
        MutableText text = Feedback.prefix().append(Text.literal("Rellenar con " + fluid.displayName() + " hasta y="
                + result.level() + ": no cierra, no se ha puesto nada. ").formatted(Formatting.YELLOW));
        text.append(Text.literal(reason(leak, limit, radius)).formatted(Formatting.GRAY));
        if (leak.hole().length > 0) {
            long first = leak.hole()[0];
            String where = " Se escapa por " + coordinates(first)
                    + (leak.hole().length > 1 ? " (un hueco de " + leak.hole().length + " bloques)" : "");
            text.append(Text.literal(where).formatted(Formatting.RED));
            text.append(Text.literal(leak.pluggable() ? ": tápalo y cerrará." : " (lo más estrecho de la salida).")
                    .formatted(Formatting.GRAY));
        } else {
            text.append(Text.literal(" Sale por " + coordinates(leak.exit()) + ".").formatted(Formatting.RED));
        }
        if (leak.closesSomewhere()) {
            text.append(Text.literal(" Cierra hasta y=" + leak.highestClosed() + " ").formatted(Formatting.GRAY));
            String command = "/komaedit fluido rellenar " + fluid.id() + " " + leak.highestClosed() + " -p "
                    + seed.getX() + "," + seed.getY() + "," + seed.getZ() + " -d " + directions.letters()
                    + " -l " + limit + " -r " + radius + (waterlog ? " -w" : "");
            text.append(Feedback.button("[llenar hasta y=" + leak.highestClosed() + "]", command, Formatting.AQUA,
                    "Llena el mismo hueco hasta y=" + leak.highestClosed()));
        }
        player.sendMessage(text, false);
        LeakMarkers.show(player, leak.hole(), leak.path(), leak.exit());
    }

    private static String reason(FloodFill.Leak leak, int limit, int radius) {
        return switch (leak.reason()) {
            case BOUNDS -> "Llega al borde de lo leído (" + radius + " bloques del clic; -r para mirar más lejos).";
            case DEEP -> "Baja más de lo leído (" + radius + " bloques).";
            case BOTTOM -> "Llega al fondo del mundo.";
            case LIMIT -> "Pasa de " + ProgressLine.group(limit) + " bloques: o es muy grande (-l) o se escapa.";
            case BELOW -> "Hay hueco debajo y el relleno no baja (-d).";
            case SIDE -> "Hay hueco a un lado y el relleno no va de lado (-d).";
        };
    }

    // ---------------------------------------------------------------- bola

    /** {@code /komaedit fluido bola}: una esfera de agua, lava o nieve en polvo en el clic. */
    public static String ball(ServerPlayerEntity player, FluidBall.Settings settings, int[] at) {
        Point point = point(player, at);
        ServerWorld world = player.getServerWorld();
        MinecraftKinds kinds = new MinecraftKinds();
        BlockPos center = point.given() || open(world, kinds, point.block(), settings.fluid()) ? point.block()
                : point.front();
        int reach = (int) Math.ceil(settings.radius());
        boolean flows = settings.fluid().flows() && !settings.sourcesOnly();
        int side = reach + (flows ? 9 : 1);
        Box box = fit(player, world, center.getX(), center.getZ(), side, center.getY() - reach - (flows ? 24 : 1),
                center.getY() + reach + 1);
        FlowSim.Rules rules = FlowSim.Rules.of(settings.fluid(), world.getDimension().ultrawarm());
        String description = "Bola de " + settings.fluid().displayName();
        run(player, box.region(world), description, snapshot -> {
            FluidBall.Result result = FluidBall.plan(snapshot, kinds, center.getX(), center.getY(), center.getZ(),
                    settings, rules, MAX_FLOWS, CANCELLATION);
            RegionEdits edits = edits(player, snapshot, result.writes());
            List<String> parts = new ArrayList<>();
            parts.add("radio " + format(settings.radius()) + " · " + Feedback.count(result.sources(),
                    settings.fluid() == FluidKind.POWDER_SNOW ? "bloque" : "fuente",
                    settings.fluid() == FluidKind.POWDER_SNOW ? "bloques" : "fuentes"));
            if (result.waterlogged() > 0) {
                parts.add(Feedback.count(result.waterlogged(), "anegado", "anegados"));
            }
            if (result.flows() > 0) {
                parts.add(Feedback.count(result.flows(), "corriente", "corrientes"));
            }
            if (result.trimmed() > 0) {
                parts.add(result.trimmed() + " quitadas al cerrar bordes");
            }
            if (result.kept() > 0) {
                parts.add(Feedback.count(result.kept(), "bloque con datos respetado", "bloques con datos respetados"));
            }
            if (result.overflow()) {
                parts.add("el líquido sigue corriendo más allá: el juego lo extenderá (prueba -c o -s)");
            }
            return new Outcome(edits, String.join(" · ", parts), null);
        }, false);
        return null;
    }

    // ---------------------------------------------------------------- cascada

    /**
     * {@code /komaedit fluido cascada}: con dos puntos o más en la trayectoria, un río corto con sus saltos; si no,
     * una cascada desde el bloque del clic.
     */
    public static String cascade(ServerPlayerEntity player, Cascade.Settings settings, int[] at) {
        ServerWorld world = player.getServerWorld();
        MinecraftKinds kinds = new MinecraftKinds();
        FlowSim.Rules rules = FlowSim.Rules.of(settings.fluid(), world.getDimension().ultrawarm());
        PathSnapshot path = PathManager.saved(player.getUuid());
        String description = "Cascada de " + settings.fluid().displayName();
        if (at == null && path.points().size() >= 2) {
            List<int[]> points = new ArrayList<>();
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockVector3 vector : path.points()) {
                points.add(new int[]{vector.x(), vector.y(), vector.z()});
                minX = Math.min(minX, vector.x());
                minY = Math.min(minY, vector.y());
                minZ = Math.min(minZ, vector.z());
                maxX = Math.max(maxX, vector.x());
                maxY = Math.max(maxY, vector.y());
                maxZ = Math.max(maxZ, vector.z());
            }
            int margin = settings.width() + settings.spread() + settings.poolRadius() + 10;
            Region region = new CuboidRegion(FabricAdapter.adapt(world), BlockVector3.at(minX - margin,
                    Math.max(world.getBottomY(), minY - 24), minZ - margin), BlockVector3.at(maxX + margin,
                    Math.min(world.getTopY() - 1, maxY + 8), maxZ + margin));
            run(player, region, description + " por " + Feedback.count(points.size(), "punto", "puntos"),
                    snapshot -> cascadeOutcome(player, snapshot, settings, Cascade.alongPath(snapshot, kinds, points,
                            settings, rules, MAX_FLOWS, CANCELLATION)), true);
            return null;
        }
        Point point = point(player, at);
        BlockPos source = point.block();
        int side = settings.spread() + settings.poolRadius() + 14;
        Box box = fit(player, world, source.getX(), source.getZ(), side, source.getY() - FALL_DEPTH,
                source.getY() + 3);
        run(player, box.region(world), description, snapshot -> cascadeOutcome(player, snapshot, settings,
                Cascade.fromPoint(snapshot, kinds, source.getX(), source.getY(), source.getZ(), settings, rules,
                        MAX_FLOWS, CANCELLATION)), true);
        return null;
    }

    private static Outcome cascadeOutcome(ServerPlayerEntity player, RegionSnapshot snapshot,
                                          Cascade.Settings settings, Cascade.Result result) {
        if (result.floods()) {
            Cascade.Flood flood = result.flood();
            return Outcome.refuse(current -> {
                String text = flood.escaped()
                        ? "el líquido seguiría corriendo más allá de lo leído (cerca de " + coordinates(flood.at()) + ")"
                        : "se derramaría " + Feedback.count(flood.outside(), "bloque", "bloques") + " fuera del cauce,"
                        + " hasta " + coordinates(flood.at());
                current.sendMessage(Feedback.prefix().append(Text.literal("Cascada: no se ha puesto nada, " + text + ".")
                        .formatted(Formatting.YELLOW)).append(Text.literal(" Busca un sitio con suelo llano abajo, deja"
                        + " la poza (-o 2), da más margen (-r) o marca el cauce con puntos (Ctrl+P).")
                        .formatted(Formatting.GRAY)), false);
                LeakMarkers.show(current, new long[]{flood.at()}, null, flood.at());
            });
        }
        RegionEdits edits = edits(player, snapshot, result.writes());
        List<String> parts = new ArrayList<>();
        if (result.fall() > 0) {
            parts.add("caída de " + Feedback.count(result.fall(), "bloque", "bloques"));
        }
        parts.add(Feedback.count(result.sources(), "fuente", "fuentes"));
        if (result.flows() > 0) {
            parts.add(Feedback.count(result.flows(), "corriente", "corrientes"));
        }
        if (result.pools() > 0) {
            parts.add("poza de " + Feedback.count(result.pools(), "bloque", "bloques"));
        }
        if (result.banks() > 0) {
            parts.add(Feedback.count(result.banks(), "bloque de orilla", "bloques de orilla"));
        }
        if (result.cuts() > 0) {
            parts.add(Feedback.count(result.cuts(), "bloque cortado", "bloques cortados"));
        }
        return new Outcome(edits, String.join(" · ", parts), null);
    }

    // ---------------------------------------------------------------- flujos sueltos

    /** {@code /komaedit fluido limpiar}: quita de la selección las corrientes que no salen de ninguna fuente. */
    public static String clean(ServerPlayerEntity player, LooseFlows.Target target, String maskText)
            throws IncompleteRegionException {
        RegionMasks.Bound mask = maskText == null ? null : RegionMasks.parse(player, maskText);
        boolean ultrawarm = player.getServerWorld().getDimension().ultrawarm();
        String description = switch (target) {
            case WATER -> "Quitar flujos sueltos de agua";
            case LAVA -> "Quitar flujos sueltos de lava";
            case ALL -> "Quitar flujos sueltos";
        };
        // El margen deja ver las fuentes de alrededor: una corriente que viene de fuera no está suelta.
        RegionOperationService.submit(player, 8, description, (snapshot, edits) -> {
            if (mask != null) {
                mask.bind(snapshot);
            }
            LooseFlows.Result result = LooseFlows.clean(snapshot, new MinecraftKinds(), target, ultrawarm,
                    mask == null ? null : mask::test, edits::checkCancelled);
            new FluidStates().write(result.writes(), snapshot, edits);
        });
        return null;
    }

    // ---------------------------------------------------------------- común

    /** Lee la zona, calcula en un worker y deja el plan en vista previa o en la cola. */
    private static void run(ServerPlayerEntity player, Region region, String description,
                            Function<RegionSnapshot, Outcome> compute, boolean watchAll) {
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUuid();
        ServerWorld world = player.getServerWorld();
        Region frozen = region.clone();
        // Si al confirmar una vista previa la zona ha cambiado, se vuelve a leer y a calcular entero.
        Runnable recompute = () -> {
            ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
            if (current != null) {
                run(current, frozen, description, compute, watchAll);
            }
        };
        RegionOperationService.capture(player, frozen, 0, description, snapshot -> {
            ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
            if (current == null) {
                return;
            }
            QuietMode.status(current, message(description + ": calculando fuera del hilo del servidor…",
                    Formatting.GRAY));
            AsyncEngine.submit(playerId, description, () -> compute.apply(snapshot),
                    outcome -> server.execute(() -> finish(server, playerId, world, description, outcome,
                            recompute, watchAll ? RegionOperationService.watch(snapshot) : null)),
                    throwable -> server.execute(() -> {
                        ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerId);
                        if (target != null && !(throwable instanceof CancellationException)) {
                            target.sendMessage(message(description + ": " + readable(throwable), Formatting.RED),
                                    false);
                        }
                    }));
        });
    }

    private static void finish(MinecraftServer server, UUID playerId, ServerWorld world, String description,
                               Outcome outcome, Runnable recompute, com.komainu.moxia.preview.ZoneCheck.Watch watch) {
        ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
        if (current == null) {
            return;
        }
        if (current.getServerWorld() != world) {
            current.sendMessage(message(description + ": " + RegionOperationService.OTHER_WORLD, Formatting.YELLOW),
                    false);
            return;
        }
        if (outcome.refusal() != null) {
            outcome.refusal().accept(current);
            return;
        }
        LeakMarkers.forget(playerId);
        if (outcome.edits().changes() == 0) {
            current.sendMessage(message(description + ": no había nada que cambiar"
                    + (outcome.detail().isBlank() ? "" : " (" + outcome.detail() + ")"), Formatting.YELLOW), false);
            return;
        }
        PackedBlockPlan plan = outcome.edits().plan();
        BuildEstimate estimate = BuildEstimate.of(plan);
        Runnable submit = () -> {
            try {
                int queued = PlanPreviews.withExtras(new PlanPreviews.Extras(recompute, watch),
                        () -> BatchEditService.submit(current, plan, "air", description));
                if (!PlanPreviews.holds(playerId, plan)) {
                    QuietMode.status(current, message(description + ": " + ProgressLine.group(queued)
                            + " bloques en cola · " + outcome.detail() + " · //undo para deshacer", Formatting.GREEN));
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                current.sendMessage(message(description + ": " + readable(exception), Formatting.RED), false);
            }
        };
        PendingBuilds.submitOrHold(current, description, estimate, submit, true);
    }

    private static RegionEdits edits(ServerPlayerEntity player, RegionSnapshot snapshot, FluidWrites writes) {
        long limit = OperationPolicy.limit(player);
        RegionEdits edits = new RegionEdits(snapshot, (int) Math.min(Integer.MAX_VALUE - 8,
                limit == Long.MAX_VALUE ? Math.max(RegionOperationService.MAX_CHANGES, snapshot.volume()) : limit));
        new FluidStates().write(writes, snapshot, edits);
        edits.seal();
        return edits;
    }

    /** El clic (con el Studio, donde se hizo; si no, lo que se mira) o el punto de {@code -p}. */
    private static Point point(ServerPlayerEntity player, int[] at) {
        if (at != null) {
            BlockPos pos = new BlockPos(at[0], at[1], at[2]);
            if (!player.getServerWorld().isInBuildLimit(pos)) {
                throw new IllegalArgumentException("El punto " + at[0] + ", " + at[1] + ", " + at[2]
                        + " está fuera del mundo");
            }
            return new Point(pos, pos, true);
        }
        HitResult result = StudioAim.target(player, REACH);
        if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("Mira (o haz clic en) el sitio, hasta " + (int) REACH + " bloques");
        }
        return new Point(hit.getBlockPos(), hit.getBlockPos().offset(hit.getSide()), false);
    }

    /** Si el líquido puede ocupar ese bloque (aire, plantas, su propio líquido). */
    private static boolean open(ServerWorld world, MinecraftKinds kinds, BlockPos pos, FluidKind fluid) {
        int rawId = Block.getRawIdFromState(world.getBlockState(pos));
        return kinds.washable(rawId) || fluid == FluidKind.WATER && kinds.water(rawId)
                || fluid == FluidKind.LAVA && kinds.lava(rawId);
    }

    /** La caja que se lee alrededor de un punto, ya encogida para caber en el límite del jugador. */
    private record Box(int centerX, int centerZ, int radius, int low, int high) {
        Region region(ServerWorld world) {
            return new CuboidRegion(FabricAdapter.adapt(world), BlockVector3.at(centerX - radius, low,
                    centerZ - radius), BlockVector3.at(centerX + radius, high, centerZ + radius));
        }
    }

    private static Box fit(ServerPlayerEntity player, ServerWorld world, int x, int z, int radius, int low,
                           int high) {
        int bottom = Math.max(world.getBottomY(), low);
        int top = Math.min(world.getTopY() - 1, high);
        if (top < bottom) {
            throw new IllegalArgumentException("El punto está fuera de la altura del mundo");
        }
        long allowed = Math.min(OperationPolicy.limit(player), RegionOperationService.MAX_CAPTURE_BLOCKS);
        int fitted = radius;
        while (fitted > 4 && (long) (2 * fitted + 1) * (2 * fitted + 1) * (top - bottom + 1) > allowed) {
            fitted--;
        }
        return new Box(x, z, fitted, bottom, top);
    }

    /**
     * El nivel escrito: nada o {@code clic} (la altura del clic), {@code clic+2}, {@code borde} (lo más alto que
     * cierra), {@code ~} o {@code ~-1} (tus pies) o una altura.
     */
    static FloodFill.Level level(String text, int clickY, int feetY) {
        if (text == null) {
            return FloodFill.Level.CLICK;
        }
        String value = text.toLowerCase(Locale.ROOT);
        try {
            if (value.equals("clic") || value.equals("click")) {
                return FloodFill.Level.CLICK;
            }
            if (value.equals("borde") || value.equals("brim") || value.equals("auto") || value.equals("max")) {
                return FloodFill.Level.BRIM;
            }
            for (String prefix : new String[]{"clic", "click"}) {
                if (value.startsWith(prefix + "+") || value.startsWith(prefix + "-")) {
                    String offset = value.substring(prefix.length());
                    return FloodFill.Level.fixed(clickY + Integer.parseInt(offset.startsWith("+")
                            ? offset.substring(1) : offset));
                }
            }
            if (value.startsWith("~")) {
                return FloodFill.Level.fixed(feetY + (value.length() == 1 ? 0 : Integer.parseInt(value.substring(1))));
            }
            return FloodFill.Level.fixed(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El nivel es una altura (62), clic, clic+2, borde, ~ o ~-1, no «"
                    + text + "»");
        }
    }

    /** {@code x,y,z} de {@code -p}. */
    public static int[] parsePoint(String text) {
        String[] parts = text.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("El punto va como x,y,z (por ejemplo 10,64,-3), no «" + text + "»");
        }
        int[] point = new int[3];
        try {
            for (int index = 0; index < 3; index++) {
                point[index] = Integer.parseInt(parts[index].strip());
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El punto va como x,y,z con números enteros, no «" + text + "»");
        }
        return point;
    }

    static String coordinates(long packed) {
        return PackedBlockPlan.unpackX(packed) + ", " + PackedBlockPlan.unpackY(packed) + ", "
                + PackedBlockPlan.unpackZ(packed);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }

    private static Text message(String text, Formatting color) {
        return Feedback.prefix().append(Text.literal(text).formatted(color));
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
