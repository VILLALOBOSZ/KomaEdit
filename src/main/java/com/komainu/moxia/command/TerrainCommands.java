package com.komainu.moxia.command;

import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.terrain.RampPlanner;
import com.komainu.moxia.terrain.RiverPlanner;
import com.komainu.moxia.terrain.StampLibrary;
import com.komainu.moxia.terrain.SurfacePalette;
import com.komainu.moxia.terrain.TerrainBlocks;
import com.komainu.moxia.terrain.TerrainJobs;
import com.komainu.moxia.terrain.TerrainService;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Terraformado: {@code /komaedit terreno} ({@code terrain}) con rampas, ríos y cañones por la trayectoria, erosión
 * física de la selección y repintado de la superficie, más la lista de sellos y paletas. El pincel de relieve vive
 * en {@code /komaedit pincel heightmap}. Todo pasa por {@link TerrainService}: lectura repartida entre ticks,
 * cálculo en un worker, estimación del motor común y un solo //undo.
 */
public final class TerrainCommands {
    private static final List<String> ACTIONS = List.of("rampa", "rio", "arroyo", "canon", "barranco", "erosion",
            "pintar", "sellos", "paletas");
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("ramp", "rampa"), Map.entry("river", "rio"), Map.entry("río", "rio"),
            Map.entry("stream", "arroyo"), Map.entry("canyon", "canon"), Map.entry("cañon", "canon"),
            Map.entry("cañón", "canon"), Map.entry("gully", "barranco"), Map.entry("erode", "erosion"),
            Map.entry("erosión", "erosion"), Map.entry("erosionar", "erosion"), Map.entry("paint", "pintar"),
            Map.entry("stamps", "sellos"), Map.entry("heightmaps", "sellos"), Map.entry("palettes", "paletas"));
    private static final SuggestionProvider<ServerCommandSource> ACTION_SUGGESTIONS = (context, builder) -> {
        ACTIONS.forEach(builder::suggest);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> OPTION_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        String action = canonical(StringArgumentType.getString(context, "terreno_accion"));
        List<String> examples = new ArrayList<>(switch (action) {
            case "rampa" -> List.of("plano", "cono", "5", "6", "-t", "-u", "-m", "-e");
            case "rio", "arroyo", "canon", "barranco" -> List.of("5", "3", "-o", "-w", "-t", "-f", "agua", "lava",
                    "seco", "-n", "-z", "-y", "-k", "-m", "-e");
            case "erosion" -> List.of("hidraulica", "termica", "completa", "3", "1", "-a", "-s", "-m", "-e");
            case "pintar" -> List.of("-m", "-e");
            default -> List.of();
        });
        if (!action.equals("sellos") && !action.equals("paletas")) {
            examples.addAll(SurfacePalette.presets().keySet());
        }
        examples.forEach(tail::suggest);
        return tail.buildFuture();
    };

    private TerrainCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("terreno").build());
        root.addChild(command("terrain").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canAny(player, KomaPermissions.BUILD + ".terrain"))
                .executes(TerrainCommands::help)
                .then(argument("terreno_accion", StringArgumentType.word())
                        .suggests(ACTION_SUGGESTIONS)
                        .executes(context -> run(context, ""))
                        .then(argument("terreno_opciones", StringArgumentType.greedyString())
                                .suggests(OPTION_SUGGESTIONS)
                                .executes(context -> run(context,
                                        StringArgumentType.getString(context, "terreno_opciones")))));
    }

    private static String canonical(String action) {
        String lower = action.toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(lower, lower);
    }

    private static int run(CommandContext<ServerCommandSource> context, String options) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String action = canonical(StringArgumentType.getString(context, "terreno_accion"));
        try {
            switch (action) {
                case "rampa" -> ramp(player, options);
                case "rio", "arroyo", "canon", "barranco" -> river(player, action, options);
                case "erosion" -> erosion(player, options);
                case "pintar" -> paint(player, options);
                case "sellos" -> stamps(context);
                case "paletas" -> palettes(context);
                default -> throw new IllegalArgumentException("Acción desconocida: " + action + " ("
                        + String.join(", ", ACTIONS) + ")");
            }
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context, "Selecciona primero la zona con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context, exception.getMessage());
        }
    }

    // ---------------------------------------------------------------- rampa

    private static void ramp(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "terrain.ramp");
        BrushOptions options = BrushOptions.parse(text, Set.of('u', 'e'), Set.of('t', 'm'));
        RampPlanner.Section section = RampPlanner.Section.FLAT;
        double width = 5.0;
        double transition = 6.0;
        String paletteText = null;
        int numbers = 0;
        for (int index = 0; index < options.count(); index++) {
            String token = options.positional(index);
            if (BrushOptions.isNumber(token)) {
                if (numbers == 0) {
                    width = options.decimal(index, width, 1.0, 128.0, "El ancho");
                } else if (numbers == 1) {
                    transition = options.decimal(index, transition, 0.0, 64.0, "La transición");
                } else {
                    throw new IllegalArgumentException("Sobra el número " + token
                            + " (rampa [plano|cono] [ancho] [transición] [paleta])");
                }
                numbers++;
            } else if (isSection(token)) {
                section = RampPlanner.Section.byId(token);
            } else if (paletteText == null) {
                paletteText = token;
            } else {
                throw new IllegalArgumentException("No entiendo «" + token + "»");
            }
        }
        double talus = options.flagDecimal('t', 1.0, 0.1, 8.0, "El talud");
        RampPlanner.Settings settings = new RampPlanner.Settings(section, width / 2.0, transition, talus,
                options.has('u'));
        PathSnapshot path = WorldEditService.resolvePath(player);
        BlockVector3 from = path.points().getFirst();
        BlockVector3 to = path.points().getLast();
        if (from.equals(to)) {
            throw new IllegalArgumentException("La rampa necesita dos puntos distintos");
        }
        double grade = RampPlanner.grade(new double[]{from.x(), from.y(), from.z()},
                new double[]{to.x(), to.y(), to.z()}, settings.smooth());
        if (grade > RampPlanner.MAX_GRADE + 1e-9) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "La rampa sube %d bloques en %.0f: más de uno por bloque daría escalones; aleja los puntos",
                    Math.abs(to.y() - from.y()), Math.hypot(to.x() - from.x(), to.z() - from.z())));
        }
        int span = 32;
        int reach = (int) Math.ceil(section == RampPlanner.Section.FLAT ? settings.reach(0)
                : settings.halfWidth() + Math.min(48.0, span / talus) + 2.0);
        BlockVector3 low = BlockVector3.at(Math.min(from.x(), to.x()) - reach, Math.min(from.y(), to.y()) - span,
                Math.min(from.z(), to.z()) - reach);
        BlockVector3 high = BlockVector3.at(Math.max(from.x(), to.x()) + reach, Math.max(from.y(), to.y()) + span,
                Math.max(from.z(), to.z()) + reach);
        SurfacePalette palette = SurfacePalette.parse(paletteText);
        TerrainBlocks blocks = TerrainBlocks.resolve(palette);
        int[] start = {from.x(), from.y(), from.z()};
        int[] end = {to.x(), to.y(), to.z()};
        TerrainService.submit(player, new CuboidRegion(low, high), 0, "Rampa " + section.displayName(),
                options.flag('m'), blocks, options.has('e'),
                (view, sink, cancel) -> TerrainJobs.ramp(view, start, end, settings, palette, sink, cancel));
    }

    private static boolean isSection(String token) {
        try {
            RampPlanner.Section.byId(token);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    // ---------------------------------------------------------------- ríos y cañones

    private static void river(ServerPlayerEntity player, String action, String text)
            throws IncompleteRegionException {
        boolean canyon = action.equals("canon") || action.equals("barranco");
        KomaPermissions.requireBuild(player, canyon ? "terrain.canyon" : "terrain.river");
        BrushOptions options = BrushOptions.parse(text, Set.of('y', 'k', 'e'),
                Set.of('o', 'w', 't', 'f', 'n', 'z', 'm'));
        RiverPlanner.Settings base = RiverPlanner.preset(action);
        double width = base.halfWidth() * 2.0;
        double depth = base.depth();
        String paletteText = null;
        int numbers = 0;
        for (int index = 0; index < options.count(); index++) {
            String token = options.positional(index);
            if (BrushOptions.isNumber(token)) {
                if (numbers == 0) {
                    width = options.decimal(index, width, 1.0, 64.0, "El ancho");
                } else if (numbers == 1) {
                    depth = options.decimal(index, depth, 0.0, 96.0, "La profundidad");
                } else {
                    throw new IllegalArgumentException("Sobra el número " + token
                            + " (" + action + " [ancho] [profundidad] [paleta])");
                }
                numbers++;
            } else if (paletteText == null) {
                paletteText = token;
            } else {
                throw new IllegalArgumentException("No entiendo «" + token + "»");
            }
        }
        RiverPlanner.Fluid fluid = options.has('f') ? RiverPlanner.Fluid.byId(options.flag('f')) : base.fluid();
        double defaultLevel = fluid == RiverPlanner.Fluid.NONE ? 0.0
                : base.level() > 0.0 ? Math.min(base.level(), Math.max(1.0, depth - 1.0)) : Math.max(1.0, depth - 1.0);
        RiverPlanner.Settings settings = new RiverPlanner.Settings(
                width / 2.0,
                depth,
                options.flagDecimal('o', base.bank(), 0.5, 32.0, "La orilla"),
                options.flagDecimal('w', base.wallSlope(), 0.1, 8.0, "La pendiente de las laderas"),
                options.flagDecimal('t', base.transition(), 0.0, 64.0, "La transición"),
                fluid,
                fluid == RiverPlanner.Fluid.NONE ? 0.0 : options.flagDecimal('n', defaultLevel, 0.0, 96.0, "El nivel"),
                options.has('y'),
                options.has('k'),
                options.flagDecimal('z', base.smoothing(), 0.0, 256.0, "El suavizado"));
        PathSnapshot path = WorldEditService.resolvePath(player);
        SampledPath sampled = SampledPath.createSmooth(path.points(), path.closed());
        List<double[]> samples = new ArrayList<>();
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        int steps = Math.max(2, (int) Math.ceil(sampled.length() * 2.0));
        for (int step = 0; step <= steps; step++) {
            var position = sampled.at(sampled.length() * step / steps).position();
            samples.add(new double[]{position.x(), position.y(), position.z()});
            minimumX = Math.min(minimumX, position.x());
            maximumX = Math.max(maximumX, position.x());
            minimumY = Math.min(minimumY, position.y());
            maximumY = Math.max(maximumY, position.y());
            minimumZ = Math.min(minimumZ, position.z());
            maximumZ = Math.max(maximumZ, position.z());
        }
        int reach = (int) Math.ceil(settings.reach()) + settings.leveeMargin() + 2;
        int below = (int) Math.ceil(depth) + 8;
        int above = (int) Math.min(96, Math.max(24, Math.ceil(settings.transition() * settings.wallSlope()) + 8));
        BlockVector3 low = BlockVector3.at((int) Math.floor(minimumX) - reach, (int) Math.floor(minimumY) - below,
                (int) Math.floor(minimumZ) - reach);
        BlockVector3 high = BlockVector3.at((int) Math.floor(maximumX) + reach, (int) Math.floor(maximumY) + above,
                (int) Math.floor(maximumZ) + reach);
        SurfacePalette palette = SurfacePalette.parse(paletteText);
        TerrainBlocks blocks = TerrainBlocks.resolve(palette);
        String description = switch (action) {
            case "arroyo" -> "Arroyo";
            case "canon" -> "Cañón";
            case "barranco" -> "Barranco";
            default -> "Río";
        } + (fluid == RiverPlanner.Fluid.LAVA ? " de lava" : "");
        TerrainService.submit(player, new CuboidRegion(low, high), 0, description, options.flag('m'), blocks,
                options.has('e'),
                (view, sink, cancel) -> TerrainJobs.river(view, samples, settings, palette, sink, cancel));
    }

    // ---------------------------------------------------------------- erosión

    private static void erosion(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        BrushOptions options = BrushOptions.parse(text, Set.of('e'), Set.of('a', 's', 'm'));
        String modeText = options.positional(0);
        if (modeText == null) {
            throw new IllegalArgumentException("Falta el tipo: erosion hidraulica|termica|completa [iteraciones]"
                    + " [fuerza] [paleta]");
        }
        TerrainJobs.Erosion mode = TerrainJobs.Erosion.byId(modeText);
        if (mode != TerrainJobs.Erosion.THERMAL) {
            KomaPermissions.requireBuild(player, "terrain.erode.hydraulic");
        }
        if (mode != TerrainJobs.Erosion.HYDRAULIC) {
            KomaPermissions.requireBuild(player, "terrain.erode.thermal");
        }
        boolean thermal = mode == TerrainJobs.Erosion.THERMAL;
        double iterations = thermal ? 40.0 : 3.0;
        double strength = 1.0;
        String paletteText = null;
        int numbers = 0;
        for (int index = 1; index < options.count(); index++) {
            String token = options.positional(index);
            if (BrushOptions.isNumber(token)) {
                if (numbers == 0) {
                    iterations = thermal ? options.integer(index, 40, 1, 2_000, "Las pasadas")
                            : options.decimal(index, iterations, 0.1, 64.0, "Las gotas por columna");
                } else if (numbers == 1) {
                    strength = options.decimal(index, strength, 0.1, thermal ? 2.0 : 8.0, "La fuerza");
                } else {
                    throw new IllegalArgumentException("Sobra el número " + token
                            + " (erosion <tipo> [iteraciones] [fuerza] [paleta])");
                }
                numbers++;
            } else if (paletteText == null) {
                paletteText = token;
            } else {
                throw new IllegalArgumentException("No entiendo «" + token + "»");
            }
        }
        double talus = options.flagDecimal('a', 40.0, 5.0, 85.0, "El ángulo de reposo");
        long seed = options.has('s') ? seed(options.flag('s')) : ThreadLocalRandom.current().nextLong();
        SurfacePalette palette = SurfacePalette.parse(paletteText);
        TerrainBlocks blocks = TerrainBlocks.resolve(palette);
        Region selection = WorldEditService.selection(player).clone();
        double finalIterations = iterations;
        double finalStrength = strength;
        TerrainService.submit(player, selection, 4, "Erosión " + mode.displayName(), options.flag('m'), blocks,
                options.has('e'), (view, sink, cancel) -> TerrainJobs.erode(view, mode, finalIterations,
                        finalStrength, talus, seed, palette, sink, cancel));
    }

    private static long seed(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return text.hashCode();
        }
    }

    // ---------------------------------------------------------------- pintar

    private static void paint(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "terrain.paint");
        BrushOptions options = BrushOptions.parse(text, Set.of('e'), Set.of('m'));
        if (options.count() > 1) {
            throw new IllegalArgumentException("pintar [paleta] [-m máscara]: la paleta va junta, con comas");
        }
        SurfacePalette palette = SurfacePalette.parse(options.positional(0));
        TerrainBlocks blocks = TerrainBlocks.resolve(palette);
        Region selection = WorldEditService.selection(player).clone();
        TerrainService.submit(player, selection, 2, "Pintar terreno", options.flag('m'), blocks, options.has('e'),
                (view, sink, cancel) -> TerrainJobs.paint(view, palette, sink, cancel));
    }

    // ---------------------------------------------------------------- listas

    private static void stamps(CommandContext<ServerCommandSource> context) {
        List<String> names = TerrainService.catalog().names();
        Map<String, String> builtIn = StampLibrary.descriptions();
        feedback(context, Text.literal(names.size() + " sellos en " + TerrainService.catalog().directory()
                + " (pulsa uno para ponerlo en el pincel):").formatted(Formatting.GRAY));
        MutableText line = Text.literal("");
        for (String name : names) {
            String description = builtIn.getOrDefault(name, "PNG propio");
            line.append(Text.literal(name).styled(style -> style.withColor(builtIn.containsKey(name)
                            ? Formatting.AQUA : Formatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/komaedit pincel heightmap " + name + " 12 12"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(description)))))
                    .append(Text.literal("  "));
        }
        feedback(context, line);
    }

    private static void palettes(CommandContext<ServerCommandSource> context) {
        feedback(context, Text.literal("Paletas de superficie (o clave:bloques@valor con llano, pendiente, cumbre,"
                + " relleno, fondo, lecho, orilla y estratos):").formatted(Formatting.GRAY));
        for (Map.Entry<String, String> entry : SurfacePalette.presets().entrySet()) {
            feedback(context, Text.literal(entry.getKey()).formatted(Formatting.AQUA)
                    .append(Text.literal(" · " + entry.getValue()).formatted(Formatting.GRAY)));
        }
    }

    private static int help(CommandContext<ServerCommandSource> context) {
        feedback(context, Text.literal("Terreno: rampa, rio, arroyo, canon, barranco, erosion, pintar, sellos,"
                + " paletas · pincel de relieve: /komaedit pincel heightmap <sello> · /komaedit help terreno")
                .formatted(Formatting.GRAY));
        return 1;
    }

    private static void feedback(CommandContext<ServerCommandSource> context, Text text) {
        context.getSource().sendFeedback(() -> Text.literal("KomaEdit · ").formatted(Formatting.DARK_AQUA)
                .append(text), false);
    }

    private static int fail(CommandContext<ServerCommandSource> context, String message) {
        context.getSource().sendError(Text.literal("KomaEdit · ").formatted(Formatting.DARK_AQUA).append(Text.literal(
                message == null || message.isBlank() ? "Operación inválida" : message).formatted(Formatting.RED)));
        return 0;
    }
}
