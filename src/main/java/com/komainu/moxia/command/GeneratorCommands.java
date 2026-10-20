package com.komainu.moxia.command;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.generator.FlowField;
import com.komainu.moxia.generator.FlowLine;
import com.komainu.moxia.generator.GeneratedBlocks;
import com.komainu.moxia.generator.GeneratorRequests;
import com.komainu.moxia.generator.NoiseGenerator;
import com.komainu.moxia.generator.PaletteTraits;
import com.komainu.moxia.generator.ShapeRequest;
import com.komainu.moxia.generator.ShapeVoxelizer;
import com.komainu.moxia.generator.SnapshotMask;
import com.komainu.moxia.generator.SnapshotTerrain;
import com.komainu.moxia.generator.Volume;
import com.komainu.moxia.noise.SavedNoises;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.selector.ConvexPolyhedralRegionSelector;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Generadores al estilo de ezEdits: formas sueltas ({@code shape}), terreno con ruido ({@code noisegen}) y campos y
 * líneas de flujo ({@code flowfield}, {@code flowline}). La geometría se calcula en un worker de
 * {@link AsyncEngine}; lo que necesita leer el mundo (máscaras, superficies) usa la foto de
 * {@link RegionOperationService}. Todo pasa por el historial de WorldEdit.
 */
public final class GeneratorCommands {
    private static final int MAX_BLOCKS = RegionOperationService.MAX_CHANGES;
    /** Columnas × altura que un generador sin foto recorre como mucho. */
    private static final long MAX_SCAN = 64_000_000L;

    private GeneratorCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal("komaedit");
        root.then(shapeCommand("shape")).then(shapeCommand("forma"))
                .then(noiseCommand("noisegen")).then(noiseCommand("ruidogen"))
                .then(flowFieldCommand("flowfield")).then(flowFieldCommand("flujo"))
                .then(flowLineCommand("flowline")).then(flowLineCommand("lineaflujo"));
        dispatcher.register(root);
    }

    private static boolean canBuild(ServerCommandSource source, String node) {
        return source.getEntity() instanceof ServerPlayerEntity player && KomaPermissions.canBuild(player, node);
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> rest(Runner runner) {
        return argument("args", StringArgumentType.greedyString())
                .executes(context -> run(context, runner, StringArgumentType.getString(context, "args")));
    }

    @FunctionalInterface
    private interface Runner {
        void run(ServerPlayerEntity player, String args) throws Exception;
    }

    private static int run(CommandContext<ServerCommandSource> context, Runner runner, String args)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            runner.run(player, args);
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Selecciona primero la zona con //pos1 y //pos2");
        } catch (InputParseException | IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), readable(exception));
        } catch (Exception exception) {
            return fail(context.getSource(), readable(exception));
        }
    }

    // ── shape ────────────────────────────────────────────────────────────────────────────────────────────

    private static LiteralArgumentBuilder<ServerCommandSource> shapeCommand(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canAny(player, KomaPermissions.BUILD + ".shape"));
        for (ShapeRequest.Form form : ShapeRequest.Form.values()) {
            Runner runner = (player, args) -> shape(player, form, args);
            java.util.function.Predicate<ServerCommandSource> allowed = source ->
                    source.getEntity() instanceof ServerPlayerEntity player
                            && KomaPermissions.canBuild(player, "shape." + form.id());
            command.then(literal(form.id()).requires(allowed).then(rest(runner)));
            command.then(literal(form.alias()).requires(allowed).then(rest(runner)));
        }
        return command;
    }

    private static void shape(ServerPlayerEntity player, ShapeRequest.Form form, String args) throws Exception {
        ShapeRequest request = ShapeRequest.parse(form, args);
        KomaPermissions.requireBuild(player, "shape." + form.id());
        WorldEditService.validatePattern(player, request.pattern());
        if (request.vertexPattern() != null) {
            WorldEditService.validatePattern(player, request.vertexPattern());
        }
        BlockPos feet = player.getBlockPos();
        int[] origin = {feet.getX(), feet.getY(), feet.getZ()};
        Vec3d rotation = player.getRotationVector();
        double[] look = {rotation.x, rotation.y, rotation.z};
        double yaw = player.getYaw();
        ServerWorld world = player.getServerWorld();
        int bottom = world.getBottomY();
        int top = world.getTopY() - 1;
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUuid();
        String description = request.describe();
        status(player, description + ": calculando fuera del hilo del servidor…");
        AsyncEngine.submit(
                playerId,
                () -> {
                    GeneratedBlocks blocks = ShapeVoxelizer.generate(request, origin, look, yaw, MAX_BLOCKS);
                    PackedBlockPlan.Builder plan = PackedBlockPlan.builder(blocks.size());
                    LongOpenHashSet vertices = new LongOpenHashSet();
                    for (int index = 0; index < blocks.size(); index++) {
                        int y = blocks.y(index);
                        if (y < bottom || y > top) {
                            continue;
                        }
                        plan.addPattern(blocks.x(index), y, blocks.z(index));
                        if (blocks.material(index) != 0) {
                            vertices.add(blocks.packed(index));
                        }
                    }
                    return new ShapeResult(plan.build(), vertices, blocks.bounds());
                },
                result -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    try {
                        if (result.plan().size() == 0) {
                            throw new IllegalArgumentException("La forma no tiene bloques dentro del mundo");
                        }
                        int queued;
                        if (result.vertices().isEmpty()) {
                            queued = BatchEditService.submit(current, result.plan(), request.pattern(), description);
                        } else {
                            WorldEditService.PlayerContext context = WorldEditService.context(current);
                            Pattern main = WorldEditService.parsePatternUnchecked(context, request.pattern());
                            Pattern vertex = WorldEditService.parsePatternUnchecked(context, request.vertexPattern());
                            LongOpenHashSet vertices = result.vertices();
                            queued = BatchEditService.submitStyled(current, result.plan(), position ->
                                    vertices.contains(PackedBlockPlan.pack(position.x(), position.y(), position.z()))
                                            ? vertex : main, description);
                        }
                        if (request.moveSelection() && result.bounds() != null) {
                            int[] bounds = result.bounds();
                            WorldEditService.setCuboid(current,
                                    new BlockPos(bounds[0], Math.max(bottom, bounds[1]), bounds[2]),
                                    new BlockPos(bounds[3], Math.min(top, bounds[4]), bounds[5]));
                        }
                        if (!PlanPreviews.holds(playerId, result.plan())) {
                            queued(current, description + " en cola: " + ProgressLine.group(queued) + " bloques · //undo para deshacer");
                        }
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        fail(current.getCommandSource(), readable(exception));
                    }
                }),
                throwable -> report(server, playerId, description, throwable)
        );
    }

    private record ShapeResult(PackedBlockPlan plan, LongOpenHashSet vertices, int[] bounds) {
    }

    // ── noisegen ─────────────────────────────────────────────────────────────────────────────────────────

    private static LiteralArgumentBuilder<ServerCommandSource> noiseCommand(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name).requires(source -> canBuild(source,
                "noisegen"));
        for (NoiseGenerator.Mode mode : NoiseGenerator.Mode.values()) {
            Runner runner = (player, args) -> noise(player, mode, args);
            command.then(literal(mode.id()).then(rest(runner)));
            command.then(literal(mode.alias()).then(rest(runner)));
        }
        return command;
    }

    private static void noise(ServerPlayerEntity player, NoiseGenerator.Mode mode, String args) throws Exception {
        GeneratorRequests.NoiseRequest request = GeneratorRequests.noise(mode, args,
                () -> ThreadLocalRandom.current().nextLong(1L << 31),
                text -> SavedNoises.parse(player.getUuid(), text));
        KomaPermissions.requireBuild(player, "noisegen");
        Palette palette = Palette.resolve(player, request.palette());
        RegionMasks.Bound mask = request.mask() == null ? null
                : RegionMasks.parse(player, request.mask(), SnapshotMask.LISTS);
        NoiseGenerator.Settings settings = request.settings();
        String description = "Noisegen " + mode.id();
        ServerWorld world = player.getServerWorld();
        int bottom = world.getBottomY();
        int top = world.getTopY() - 1;
        info(player, description + " · semilla " + settings.seed() + " (repítela con -s " + settings.seed() + ")");
        if (mask != null) {
            RegionOperationService.submit(player, mask.padding(), description, (snapshot, edits) -> {
                SnapshotTerrain terrain = new SnapshotTerrain(snapshot, mask);
                GeneratedBlocks blocks = new GeneratedBlocks(MAX_BLOCKS);
                NoiseGenerator.generate(settings, terrain, terrain, palette.traits(), bottom, top, blocks);
                write(blocks, palette, edits, false);
            });
            return;
        }
        Volume volume = volume(player);
        int height = settings.height() > 0 ? settings.height() : volume.sizeY();
        if ((long) volume.sizeX() * volume.sizeZ() * Math.max(height, volume.sizeY()) > MAX_SCAN) {
            throw new IllegalArgumentException("La selección es demasiado grande para generar terreno (máximo "
                    + ProgressLine.group(MAX_SCAN) + " bloques recorridos)");
        }
        submitDirect(player, description, palette, blocks ->
                NoiseGenerator.generate(settings, volume, null, palette.traits(), bottom, top, blocks));
    }

    // ── flowfield ────────────────────────────────────────────────────────────────────────────────────────

    private static LiteralArgumentBuilder<ServerCommandSource> flowFieldCommand(String name) {
        return literal(name).requires(source -> canBuild(source, "flowfield"))
                .then(rest(GeneratorCommands::flowField));
    }

    private static void flowField(ServerPlayerEntity player, String args) throws Exception {
        GeneratorRequests.FlowFieldRequest request = GeneratorRequests.flowField(args,
                () -> ThreadLocalRandom.current().nextLong(1L << 31),
                text -> SavedNoises.parse(player.getUuid(), text));
        KomaPermissions.requireBuild(player, "flowfield");
        Palette palette = Palette.resolve(player, request.palette());
        RegionMasks.Bound mask = request.mask() == null ? null
                : RegionMasks.parse(player, request.mask(), SnapshotMask.LISTS);
        FlowField.Settings settings = request.settings();
        String description = "Campo de flujo";
        info(player, description + " · semilla " + settings.seed() + " (repítela con -s " + settings.seed() + ")");
        if (settings.heightmap() != null || mask != null) {
            boolean above = settings.heightmap() == FlowField.Distribution.ABOVE;
            int padding = Math.max(above ? 1 : 0, mask == null ? 0 : mask.padding());
            RegionOperationService.submit(player, padding, description, (snapshot, edits) -> {
                SnapshotTerrain terrain = new SnapshotTerrain(snapshot, mask);
                GeneratedBlocks blocks = new GeneratedBlocks(MAX_BLOCKS);
                FlowField.generate(settings, terrain, terrain, palette.size(), blocks);
                write(blocks, palette, edits, above);
            });
            return;
        }
        Volume volume = volume(player);
        if ((long) volume.sizeX() * volume.sizeY() * volume.sizeZ() > MAX_SCAN) {
            throw new IllegalArgumentException("La selección es demasiado grande para un campo de flujo (máximo "
                    + ProgressLine.group(MAX_SCAN) + " bloques)");
        }
        submitDirect(player, description, palette, blocks ->
                FlowField.generate(settings, volume, null, palette.size(), blocks));
    }

    // ── flowline ─────────────────────────────────────────────────────────────────────────────────────────

    private static LiteralArgumentBuilder<ServerCommandSource> flowLineCommand(String name) {
        return literal(name).requires(source -> canBuild(source, "flowline"))
                .then(rest(GeneratorCommands::flowLine));
    }

    /** Pocos miles de bloques: se calcula en el hilo del servidor y lee el mundo para pegarse a las superficies. */
    private static void flowLine(ServerPlayerEntity player, String args) throws Exception {
        GeneratorRequests.FlowLineRequest request = GeneratorRequests.flowLine(args,
                () -> ThreadLocalRandom.current().nextLong(1L << 31),
                text -> SavedNoises.parse(player.getUuid(), text));
        KomaPermissions.requireBuild(player, "flowline");
        WorldEditService.validatePattern(player, request.pattern());
        ServerWorld world = player.getServerWorld();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        FlowLine.Terrain terrain = (x, y, z) -> {
            cursor.set(x, y, z);
            var state = world.getBlockState(cursor);
            return state.isSolidBlock(world, cursor) || !state.getFluidState().isEmpty();
        };
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        FlowLine.Result result = FlowLine.trace(request.settings(), new double[]{eye.x, eye.y, eye.z},
                new double[]{look.x, look.y, look.z}, terrain);
        int bottom = world.getBottomY();
        int top = world.getTopY() - 1;
        PackedBlockPlan.Builder plan = PackedBlockPlan.builder(result.blocks().size());
        result.blocks().forEach((long packed) -> {
            int y = PackedBlockPlan.unpackY(packed);
            if (y >= bottom && y <= top) {
                plan.addPattern(PackedBlockPlan.unpackX(packed), y, PackedBlockPlan.unpackZ(packed));
            }
        });
        PackedBlockPlan built = plan.build();
        if (built.size() == 0) {
            throw new IllegalArgumentException("La línea se sale del mundo");
        }
        int queued = BatchEditService.submit(player, built, request.pattern(), "Línea de flujo");
        if (!result.convex().isEmpty()) {
            convexSelection(player, result.convex());
        }
        if (PlanPreviews.holds(player.getUuid(), built)) {
            return;
        }
        queued(player, "Línea de flujo en cola: " + ProgressLine.group(queued) + " bloques"
                + (result.convex().isEmpty() ? "" : " · selección convexa de " + result.convex().size() + " puntos")
                + " · //undo para deshacer");
    }

    private static void convexSelection(ServerPlayerEntity player, List<double[]> points) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        ConvexPolyhedralRegionSelector selector = new ConvexPolyhedralRegionSelector(context.world());
        boolean first = true;
        for (double[] point : points) {
            BlockVector3 vector = BlockVector3.at(Math.floor(point[0]), Math.floor(point[1]), Math.floor(point[2]));
            if (first) {
                selector.selectPrimary(vector, PermissiveSelectorLimits.getInstance());
                first = false;
            } else {
                selector.selectSecondary(vector, PermissiveSelectorLimits.getInstance());
            }
        }
        context.session().setRegionSelector(context.world(), selector);
        context.session().dispatchCUISelection(context.actor());
    }

    // ── común ────────────────────────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Generation {
        void generate(GeneratedBlocks blocks);
    }

    /** Generador sin foto del mundo: calcula en un worker y escribe con bloques fijos de la paleta. */
    private static void submitDirect(ServerPlayerEntity player, String description, Palette palette,
                                     Generation generation) {
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUuid();
        AsyncEngine.submit(
                playerId,
                () -> {
                    GeneratedBlocks blocks = new GeneratedBlocks(MAX_BLOCKS);
                    generation.generate(blocks);
                    PackedBlockPlan.Builder plan = PackedBlockPlan.builder(blocks.size());
                    for (int index = 0; index < blocks.size(); index++) {
                        plan.addOverride(blocks.x(index), blocks.y(index), blocks.z(index),
                                palette.state(blocks.material(index), blocks.layers(index)));
                    }
                    return plan.build();
                },
                plan -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    if (plan.size() == 0) {
                        current.sendMessage(prefix().append(Text.literal(description
                                + ": no salió ningún bloque; prueba otro ruido, zoom o altura")
                                .formatted(Formatting.YELLOW)), false);
                        return;
                    }
                    try {
                        int queued = BatchEditService.submit(current, plan, "air", description);
                        if (!PlanPreviews.holds(playerId, plan)) {
                            queued(current, description + " en cola: " + ProgressLine.group(queued) + " bloques · //undo para deshacer");
                        }
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        fail(current.getCommandSource(), readable(exception));
                    }
                }),
                throwable -> report(server, playerId, description, throwable)
        );
    }

    /** Escribe en la foto: dentro de la selección o, si {@code capture}, también en el margen capturado. */
    private static void write(GeneratedBlocks blocks, Palette palette, RegionEdits edits, boolean capture) {
        for (int index = 0; index < blocks.size(); index++) {
            edits.checkCancelled();
            BlockState state = palette.state(blocks.material(index), blocks.layers(index));
            if (capture) {
                edits.setInCapture(blocks.x(index), blocks.y(index), blocks.z(index), state);
            } else {
                edits.set(blocks.x(index), blocks.y(index), blocks.z(index), state);
            }
        }
    }

    /** La selección actual como {@link Volume}; la región se clona para poder consultarla desde el worker. */
    private static Volume volume(ServerPlayerEntity player) throws IncompleteRegionException {
        Region region = WorldEditService.selection(player).clone();
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        return new RegionVolume(region, minimum.x(), minimum.y(), minimum.z(), maximum.x(), maximum.y(),
                maximum.z());
    }

    private record RegionVolume(Region region, int minimumX, int minimumY, int minimumZ, int maximumX,
                                int maximumY, int maximumZ) implements Volume {
        @Override
        public boolean contains(int x, int y, int z) {
            return region.contains(BlockVector3.at(x, y, z));
        }
    }

    /**
     * Paleta resuelta en el hilo del servidor: cada entrada tiene que ser un bloque (con o sin estado), porque el
     * worker escribe estados fijos. Las capas de nieve del modo suave se preparan aquí.
     */
    record Palette(List<BlockState> states, PaletteTraits traits, BlockState[][] snowLayers) {
        static Palette resolve(ServerPlayerEntity player, String text) {
            List<String> entries = SavedPalettes.parse(player.getUuid(), text);
            WorldEditService.PlayerContext context = WorldEditService.context(player);
            List<BlockState> states = new ArrayList<>(entries.size());
            List<String> ids = new ArrayList<>(entries.size());
            for (String entry : entries) {
                Pattern pattern = WorldEditService.parsePatternUnchecked(context, entry);
                BlockState state;
                if (pattern instanceof BlockStateHolder<?> holder) {
                    state = holder.toImmutableState();
                } else if (pattern instanceof BlockPattern block) {
                    state = block.getBlock().toImmutableState();
                } else {
                    throw new IllegalArgumentException("La entrada «" + entry + "» de la paleta no es un bloque: los"
                            + " generadores usan paletas de bloques (con estado si quieres, como oak_log[axis=x])");
                }
                states.add(state);
                ids.add(state.getBlockType().id());
            }
            PaletteTraits traits = PaletteTraits.of(ids);
            BlockState[][] layers = new BlockState[states.size()][];
            for (int index = 0; index < states.size(); index++) {
                if (traits.snow()[index]) {
                    layers[index] = snowLayers(states.get(index));
                }
            }
            return new Palette(List.copyOf(states), traits, layers);
        }

        private static BlockState[] snowLayers(BlockState snow) {
            Property<Integer> property = snow.getBlockType().getProperty("layers");
            BlockState[] result = new BlockState[9];
            for (int layers = 1; layers <= 8; layers++) {
                result[layers] = property == null ? snow : snow.with(property, layers);
            }
            return result;
        }

        int size() {
            return states.size();
        }

        BlockState state(int material, int layers) {
            if (layers > 0 && snowLayers[material] != null) {
                return snowLayers[material][layers];
            }
            return states.get(material);
        }
    }

    private static void report(MinecraftServer server, UUID playerId, String description, Throwable throwable) {
        server.execute(() -> {
            ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
            if (current != null && !(throwable instanceof CancellationException)) {
                fail(current.getCommandSource(), description + ": " + readable(throwable));
            }
        });
    }

    private static MutableText prefix() {
        return Feedback.prefix();
    }

    private static void status(ServerPlayerEntity player, String text) {
        QuietMode.status(player, prefix().append(Text.literal(text).formatted(Formatting.GRAY)));
    }

    private static void info(ServerPlayerEntity player, String text) {
        QuietMode.info(player, prefix().append(Text.literal(text).formatted(Formatting.GRAY)));
    }

    /** El aviso de bloques en cola: no sale con {@code /komaedit quiet} (el de «completada» sí). */
    private static void queued(ServerPlayerEntity player, String text) {
        QuietMode.status(player, prefix().append(Text.literal(text).formatted(Formatting.GREEN)));
    }

    private static void success(ServerPlayerEntity player, String text) {
        player.sendMessage(prefix().append(Text.literal(text).formatted(Formatting.GREEN)), false);
    }

    private static int fail(ServerCommandSource source, String message) {
        return EzCommandSupport.fail(source, message);
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
