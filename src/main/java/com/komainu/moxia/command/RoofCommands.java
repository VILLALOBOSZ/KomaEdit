package com.komainu.moxia.command;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.PendingBuilds;
import com.komainu.moxia.edit.SmoothBlockStates;
import com.komainu.moxia.loft.LoftGenerator;
import com.komainu.moxia.loft.LoftResult;
import com.komainu.moxia.loft.LoftSettings;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.PathStore;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.roof.Footprint;
import com.komainu.moxia.roof.RoofGenerator;
import com.komainu.moxia.roof.RoofResult;
import com.komainu.moxia.roof.RoofSettings;
import com.komainu.moxia.roof.RoofType;
import com.komainu.moxia.roof.RoofWorld;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit roof} (o {@code tejado}) y {@code /komaedit loft} (o {@code lona}): tejados sobre la planta de la
 * selección y superficies tendidas entre curvas. La geometría se calcula en un worker y se coloca como una sola
 * edición por lotes, con la estimación y la confirmación de los Build grandes, que deshace un solo {@code //undo}.
 */
final class RoofCommands {
    private static final String ROOF_NODE = "roof";
    private static final String LOFT_NODE = "loft";
    private static final List<String> ROOF_FLAGS = List.of("-p 1:2", "-p 1:1", "-p 2:1", "-p 30", "-a 1", "-a 2",
            "-g 2", "-b 2", "-c dark_oak_planks", "-m stone_bricks", "-abierto", "-v glass_pane", "-lado s",
            "-lados 8", "-h 6", "-pretil 1", "-q 2", "-d", "-eje x", "-eje z", "-escaleras dark_oak", "-losas oak",
            "-r", "-e");
    private static final List<String> LOFT_FLAGS = List.of("-i lineal", "-i catmull", "-i catenaria", "-comba 3",
            "-comba 20%", "-g 2", "-w SlabsAndStairs", "-cerrar", "-n 2", "-n 3", "-e", "actual");

    private static final SuggestionProvider<ServerCommandSource> ROOF_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        if (!remaining.contains(" ")) {
            for (RoofType type : RoofType.values()) {
                tail.suggest(type.id(), Text.literal(type.displayName()));
            }
            return tail.buildFuture();
        }
        for (String flag : ROOF_FLAGS) {
            tail.suggest(flag);
        }
        return tail.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> LOFT_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
            try {
                PathStore.names(player.getUuid()).forEach(tail::suggest);
            } catch (RuntimeException ignored) {
                // Sin proyecto enlazado todavía: no hay recorridos que sugerir.
            }
        }
        for (String flag : LOFT_FLAGS) {
            tail.suggest(flag);
        }
        return tail.buildFuture();
    };

    private RoofCommands() {
    }

    static LiteralArgumentBuilder<ServerCommandSource> roof(String name) {
        return literal(name)
                .requires(source -> !(source.getEntity() instanceof ServerPlayerEntity player)
                        || KomaPermissions.canBuild(player, ROOF_NODE))
                .executes(context -> HelpCommands.show(context, "roof"))
                .then(argument("roof_options", StringArgumentType.greedyString())
                        .suggests(ROOF_SUGGESTIONS)
                        .executes(context -> roof(context, StringArgumentType.getString(context, "roof_options"))));
    }

    static LiteralArgumentBuilder<ServerCommandSource> loft(String name) {
        return literal(name)
                .requires(source -> !(source.getEntity() instanceof ServerPlayerEntity player)
                        || KomaPermissions.canBuild(player, LOFT_NODE))
                .executes(context -> loft(context, ""))
                .then(argument("loft_options", StringArgumentType.greedyString())
                        .suggests(LOFT_SUGGESTIONS)
                        .executes(context -> loft(context, StringArgumentType.getString(context, "loft_options"))));
    }

    // ---------------------------------------------------------------- tejado

    private static int roof(CommandContext<ServerCommandSource> context, String input) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            RoofSettings parsed = RoofSettings.parse(input);
            KomaPermissions.requireBuild(player, ROOF_NODE);
            Region selection = WorldEditService.selection(player);
            RoofSettings settings = parsed.type() == RoofType.SHED && parsed.lowSide() == null
                    ? parsed.withLowSide(nearestSide(player, selection)) : parsed;
            RoofMaterials materials = RoofMaterials.resolve(player, settings);
            startRoof(player, settings, selection, materials);
            return 1;
        } catch (IncompleteRegionException exception) {
            return EzCommandSupport.fail(context.getSource(), "Selecciona la planta del tejado con WorldEdit (//pos1 y "
                    + "//pos2 a la altura de lo alto de los muros, o //sel poly) y vuelve a probar");
        } catch (InputParseException | IllegalArgumentException | IllegalStateException exception) {
            return EzCommandSupport.fail(context.getSource(), readable(exception));
        }
    }

    /** Lado de la selección más cercano al jugador: ahí queda el alero bajo del tejado a un agua. */
    private static RoofSettings.Side nearestSide(ServerPlayerEntity player, Region selection) {
        BlockVector3 minimum = selection.getMinimumPoint();
        BlockVector3 maximum = selection.getMaximumPoint();
        double dx = player.getX() - (minimum.x() + maximum.x() + 1) / 2.0;
        double dz = player.getZ() - (minimum.z() + maximum.z() + 1) / 2.0;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? RoofSettings.Side.EAST : RoofSettings.Side.WEST;
        }
        return dz > 0 ? RoofSettings.Side.SOUTH : RoofSettings.Side.NORTH;
    }

    private static void startRoof(ServerPlayerEntity player, RoofSettings settings, Region selection,
                                  RoofMaterials materials) {
        BlockVector3 minimum = selection.getMinimumPoint();
        BlockVector3 maximum = selection.getMaximumPoint();
        int sizeX = maximum.x() - minimum.x() + 1;
        int sizeZ = maximum.z() - minimum.z() + 1;
        if (sizeX > Footprint.MAX_SIDE || sizeZ > Footprint.MAX_SIDE) {
            throw new IllegalArgumentException("La selección es demasiado grande para un tejado (máximo "
                    + Footprint.MAX_SIDE + "×" + Footprint.MAX_SIDE + ")");
        }
        Footprint footprint = settings.detectWalls() ? null : Footprint.of(minimum.x(), minimum.z(), maximum.x(),
                maximum.z(), maximum.y() + 1, (x, z) -> selection.contains(BlockVector3.at(x, maximum.y(), z)));
        int base = maximum.y() + 1;
        int margin = settings.overhang() + 2;
        double slope = Math.max(settings.slope(), 1.0);
        double layer = settings.thickness() * Math.sqrt(1.0 + slope * slope);
        int rise = (int) Math.ceil(Math.max(3.0, slope + 1.0) * (Math.max(sizeX, sizeZ) / 2.0 + margin) + 2 * layer + 8);
        if (!Double.isNaN(settings.height())) {
            rise = Math.max(rise, (int) Math.ceil(settings.height() + layer + 4));
        }
        rise = Math.min(rise, RoofGenerator.MAX_HEIGHT + 8);
        int drop = (int) Math.ceil(settings.overhang() * slope * 2 + layer + 3);
        int low = settings.detectWalls() ? minimum.y() : base - drop;
        CuboidRegion capture = new CuboidRegion(WorldEditService.context(player).world(),
                BlockVector3.at(minimum.x() - margin, Math.min(low, base - drop), minimum.z() - margin),
                BlockVector3.at(maximum.x() + margin, base + rise, maximum.z() + margin));
        String description = "Tejado " + settings.type().displayName().toLowerCase(Locale.ROOT);
        UUID playerId = player.getUuid();
        MinecraftServer server = player.getServer();
        RegionOperationService.capture(player, capture, 0, description, snapshot -> {
            SnapshotWorld world = new SnapshotWorld(snapshot);
            EzCommandSupport.info(player, description + ": calculando fuera del hilo del servidor…");
            AsyncEngine.submit(
                    playerId,
                    () -> {
                        Footprint plan = footprint;
                        String detected = null;
                        if (plan == null) {
                            Footprint.Detection detection = Footprint.detect(world, minimum.x(), minimum.y(),
                                    minimum.z(), maximum.x(), maximum.y(), maximum.z());
                            plan = detection.footprint();
                            detected = detection.wallMaterial();
                        } else if (settings.wallMaterial() == null) {
                            detected = Footprint.wallMaterial(world, plan);
                        }
                        RoofResult result = RoofGenerator.generate(plan, settings, world);
                        return new PlannedRoof(result, result.plan(world), detected, plan);
                    },
                    planned -> server.execute(() -> finishRoof(server, playerId, settings, materials, planned,
                            description)),
                    throwable -> report(server, playerId, description, throwable)
            );
        });
    }

    private record PlannedRoof(RoofResult result, PackedBlockPlan plan, String detectedWall, Footprint footprint) {
    }

    private static void finishRoof(MinecraftServer server, UUID playerId, RoofSettings settings,
                                   RoofMaterials materials, PlannedRoof planned, String description) {
        ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
        if (current == null) {
            return;
        }
        try {
            RoofResult result = planned.result();
            Pattern wall = materials.wall(current, settings.wallMaterial(), planned.detectedWall());
            String summary = planned.footprint().sizeX() + "×" + planned.footprint().sizeZ()
                    + (settings.type() == RoofType.FLAT ? "" : " · pendiente " + RoofSettings.describeSlope(
                    settings.slope())) + (result.dormers() > 0 ? " · " + result.dormers() + " buhardillas" : "");
            for (String note : result.notes()) {
                EzCommandSupport.info(current, note);
            }
            if (settings.estimate()) {
                MoxiaCommands.reportEstimate(current, description + " (" + summary + ")",
                        BuildEstimate.of(planned.plan()));
                return;
            }
            BatchEditService.PositionPattern pattern = position -> materials.pattern(result, wall, position);
            Runnable submit = () -> {
                try {
                    int queued = BatchEditService.submitStyled(current, planned.plan(), pattern, description);
                    EzCommandSupport.ok(current, description + " en cola: " + queued + " bloques · "
                            + result.pieceCount() + " losas y escaleras · " + summary + " · //undo para deshacer");
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    EzCommandSupport.fail(current.getCommandSource(), readable(exception));
                }
            };
            PendingBuilds.submitOrHold(current, description, BuildEstimate.of(planned.plan()), submit);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            EzCommandSupport.fail(current.getCommandSource(), description + ": " + readable(exception));
        }
    }

    /** La foto del mundo vista como lo que necesita el tejado. Fuera de lo leído todo es aire y no se comprueba. */
    private record SnapshotWorld(RegionSnapshot snapshot) implements RoofWorld {
        @Override
        public boolean air(int x, int y, int z) {
            return !snapshot.contains(x, y, z) || snapshot.isAir(x, y, z);
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return snapshot.contains(x, y, z) && snapshot.isSolid(x, y, z);
        }

        @Override
        public String blockId(int x, int y, int z) {
            return snapshot.contains(x, y, z) && !snapshot.isAir(x, y, z) ? snapshot.blockId(x, y, z) : null;
        }

        @Override
        public int stateId(int x, int y, int z) {
            return snapshot.contains(x, y, z) ? snapshot.rawId(x, y, z) : PackedBlockPlan.NO_EXPECTED_STATE;
        }
    }

    /**
     * Materiales del tejado ya convertidos en patrones en el hilo del servidor. Si el material es una escalera o una
     * losa se usa como tal en las piezas y su bloque entero para el resto.
     */
    private record RoofMaterials(Pattern roof, List<BlockState> palette, String roofText, Pattern ridge,
                                 Pattern windowX, Pattern windowZ, Pattern stairs, Pattern slabs) {
        static RoofMaterials resolve(ServerPlayerEntity player, RoofSettings settings) throws InputParseException {
            WorldEditService.PlayerContext context = WorldEditService.context(player);
            String text = settings.material();
            Pattern roof = null;
            List<BlockState> palette = null;
            Pattern stairs = optional(player, settings.stairMaterial(), "_stairs");
            Pattern slabs = optional(player, settings.slabMaterial(), "_slab");
            String roofText = text;
            if (text.startsWith("#")) {
                palette = GeneratorCommands.Palette.resolve(player, text).states();
            } else {
                WorldEditService.validatePattern(player, text);
                roof = WorldEditService.parsePatternUnchecked(context, text);
                if (roof instanceof BlockStateHolder<?> holder) {
                    Block block = FabricAdapter.adapt(holder.toImmutableState()).getBlock();
                    if (block instanceof StairsBlock || block instanceof SlabBlock) {
                        String full = fullBlock(Registries.BLOCK.getId(block));
                        if (block instanceof StairsBlock && stairs == null) {
                            stairs = roof;
                        } else if (block instanceof SlabBlock && slabs == null) {
                            slabs = roof;
                        }
                        roof = WorldEditService.parsePatternUnchecked(context, full);
                        roofText = full;
                    }
                }
            }
            Pattern ridge = settings.ridgeMaterial() == null ? null : parse(player, settings.ridgeMaterial());
            String window = settings.windowMaterial();
            boolean pane = !window.contains("[") && (window.endsWith("_pane") || window.endsWith("iron_bars"));
            Pattern windowX = parse(player, pane ? window + "[east=true,west=true]" : window);
            Pattern windowZ = parse(player, pane ? window + "[north=true,south=true]" : window);
            if (settings.wallMaterial() != null) {
                parse(player, settings.wallMaterial());
            }
            return new RoofMaterials(roof, palette, roofText, ridge, windowX, windowZ, stairs, slabs);
        }

        /** Material de los hastiales: el pedido, el del muro de debajo o el del propio tejado. */
        Pattern wall(ServerPlayerEntity player, String requested, String detected) {
            WorldEditService.PlayerContext context = WorldEditService.context(player);
            if (requested != null) {
                return WorldEditService.parsePatternUnchecked(context, requested);
            }
            if (detected != null) {
                try {
                    return WorldEditService.parsePatternUnchecked(context, detected);
                } catch (RuntimeException ignored) {
                    // Un bloque de muro raro (con datos que no se pueden escribir así): se usa el del tejado.
                }
            }
            return roof != null ? roof : palette.getFirst();
        }

        Pattern pattern(RoofResult result, Pattern wall, BlockVector3 position) {
            long packed = PackedBlockPlan.pack(position.x(), position.y(), position.z());
            int index = Arrays.binarySearch(result.positions(), packed);
            byte material = index < 0 ? RoofResult.ROOF : result.materials()[index];
            byte piece = index < 0 ? SmoothPiece.FULL : result.pieces()[index];
            return switch (material) {
                case RoofResult.WALL -> wall;
                case RoofResult.WINDOW_X -> windowX;
                case RoofResult.WINDOW_Z -> windowZ;
                case RoofResult.RIDGE -> ridge != null ? ridge : roof(result, position.y(), piece);
                default -> roof(result, position.y(), piece);
            };
        }

        private Pattern roof(RoofResult result, int y, byte piece) {
            if (stairs != null && SmoothPiece.isStairs(piece)) {
                return stairs;
            }
            if (slabs != null && SmoothPiece.isSlab(piece)) {
                return slabs;
            }
            if (palette == null) {
                return roof;
            }
            // Degradado por altura: de la primera entrada abajo a la última en el caballete.
            int span = Math.max(1, result.roofMaxY() - result.roofMinY() + 1);
            int entry = (int) Math.floor((double) (y - result.roofMinY()) * palette.size() / span);
            return palette.get(Math.clamp(entry, 0, palette.size() - 1));
        }

        /**
         * Pieza fija de {@code -escaleras}/{@code -losas}: vale la pieza ({@code dark_oak_stairs}), su bloque entero
         * ({@code dark_oak_planks}) o el nombre corto ({@code dark_oak}).
         */
        private static Pattern optional(ServerPlayerEntity player, String text, String suffix)
                throws InputParseException {
            if (text == null) {
                return null;
            }
            Pattern pattern;
            try {
                pattern = parse(player, text);
            } catch (InputParseException | RuntimeException exception) {
                pattern = parse(player, text + suffix);
            }
            if (pattern instanceof BlockStateHolder<?> holder) {
                net.minecraft.block.BlockState state = FabricAdapter.adapt(holder.toImmutableState());
                Block block = state.getBlock();
                boolean stairs = suffix.equals("_stairs");
                if (stairs ? !(block instanceof StairsBlock) : !(block instanceof SlabBlock)) {
                    Block piece = SmoothBlockStates.pieceFamily(state)[stairs ? 1 : 0];
                    if (piece == null) {
                        throw new IllegalArgumentException("«" + text + "» no tiene " + (stairs ? "escaleras"
                                : "losas") + ": pasa la pieza directamente, como oak_" + suffix.substring(1));
                    }
                    pattern = FabricAdapter.adapt(piece.getDefaultState());
                }
            }
            return pattern;
        }

        private static Pattern parse(ServerPlayerEntity player, String text) throws InputParseException {
            WorldEditService.validatePattern(player, text);
            return WorldEditService.parsePatternUnchecked(WorldEditService.context(player), text);
        }

        /** Bloque entero de una escalera o losa: {@code oak_stairs} → {@code oak_planks}, {@code stone_brick_slab} → … */
        private static String fullBlock(Identifier piece) {
            String path = piece.getPath();
            String stem = path.endsWith("_stairs") ? path.substring(0, path.length() - "_stairs".length())
                    : path.endsWith("_slab") ? path.substring(0, path.length() - "_slab".length()) : path;
            for (String candidate : List.of(stem, stem + "s", stem + "_planks", stem + "_block")) {
                Identifier id = Identifier.of(piece.getNamespace(), candidate);
                if (Registries.BLOCK.containsId(id)) {
                    return id.toString();
                }
            }
            throw new IllegalArgumentException("No encuentro el bloque entero de " + piece
                    + ": usa el bloque entero como material y -escaleras/-losas para las piezas");
        }
    }

    // ---------------------------------------------------------------- loft

    private static int loft(CommandContext<ServerCommandSource> context, String input) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (input.isBlank()) {
            return HelpCommands.show(context, "roof");
        }
        try {
            UUID playerId = player.getUuid();
            LoftSettings settings = LoftSettings.parse(input, name -> savedPath(playerId, name));
            KomaPermissions.requireBuild(player, LOFT_NODE);
            SmoothBlockStates.validate(settings.smooth());
            List<List<BlockVector3>> curves = new ArrayList<>();
            if (settings.curves().isEmpty()) {
                curves.addAll(LoftGenerator.split(WorldEditService.resolvePath(player).points(), settings.split()));
            } else {
                for (String name : settings.curves()) {
                    curves.add(name.equals(LoftSettings.CURRENT) ? WorldEditService.resolvePath(player).points()
                            : PathStore.load(playerId, name).points());
                }
            }
            String material = settings.material();
            GeneratorCommands.Palette palette = null;
            if (material.startsWith("#")) {
                palette = GeneratorCommands.Palette.resolve(player, material);
            } else {
                WorldEditService.validatePattern(player, material);
            }
            String description = "Loft " + settings.interpolation().displayName().toLowerCase(Locale.ROOT)
                    + " entre " + curves.size() + " curvas";
            MinecraftServer server = player.getServer();
            GeneratorCommands.Palette chosen = palette;
            EzCommandSupport.info(player, description + ": calculando fuera del hilo del servidor…");
            AsyncEngine.submit(
                    playerId,
                    () -> LoftGenerator.generate(curves, settings),
                    result -> server.execute(() -> finishLoft(server, playerId, settings, chosen, result,
                            description)),
                    throwable -> report(server, playerId, description, throwable)
            );
            return 1;
        } catch (IncompleteRegionException exception) {
            return EzCommandSupport.fail(context.getSource(), "Marca los puntos con P o la supervarita, o guarda "
                    + "curvas con /komaedit path save <nombre> y pásalas por su nombre");
        } catch (InputParseException | IllegalArgumentException | IllegalStateException exception) {
            return EzCommandSupport.fail(context.getSource(), readable(exception));
        }
    }

    private static boolean savedPath(UUID playerId, String name) {
        try {
            return PathStore.exists(playerId, name);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void finishLoft(MinecraftServer server, UUID playerId, LoftSettings settings,
                                   GeneratorCommands.Palette palette, LoftResult result, String description) {
        ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
        if (current == null) {
            return;
        }
        PackedBlockPlan plan = result.plan().withPieceMaterials(settings.smooth().materials());
        if (settings.estimate()) {
            MoxiaCommands.reportEstimate(current, description, BuildEstimate.of(plan));
            return;
        }
        Runnable submit = () -> {
            try {
                int queued;
                if (palette == null) {
                    queued = BatchEditService.submit(current, plan, settings.material(), description);
                } else {
                    int entries = palette.size();
                    queued = BatchEditService.submitStyled(current, plan, position -> {
                        float across = result.across(PackedBlockPlan.pack(position.x(), position.y(), position.z()));
                        return palette.state(Math.clamp((int) Math.floor(across * entries), 0, entries - 1), 0);
                    }, description);
                }
                int pieces = plan.pieceCount();
                EzCommandSupport.ok(current, description + " en cola: " + queued + " bloques"
                        + (pieces > 0 ? " · " + pieces + " losas y escaleras" : "") + " · //undo para deshacer");
            } catch (IllegalArgumentException | IllegalStateException exception) {
                EzCommandSupport.fail(current.getCommandSource(), readable(exception));
            }
        };
        PendingBuilds.submitOrHold(current, description, BuildEstimate.of(plan), submit);
    }

    // ---------------------------------------------------------------- comunes

    private static void report(MinecraftServer server, UUID playerId, String description, Throwable throwable) {
        server.execute(() -> {
            ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
            if (current != null && !(throwable instanceof CancellationException)) {
                EzCommandSupport.fail(current.getCommandSource(), description + ": " + readable(throwable));
            }
        });
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
