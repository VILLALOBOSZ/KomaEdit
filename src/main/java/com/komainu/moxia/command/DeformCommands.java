package com.komainu.moxia.command;

import com.komainu.moxia.deform.BlockMatcher;
import com.komainu.moxia.deform.DeformArguments;
import com.komainu.moxia.deform.DeformOperation;
import com.komainu.moxia.deform.Deformations;
import com.komainu.moxia.deform.Projection;
import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.deform.SmoothingOps;
import com.komainu.moxia.deform.SurfaceOps;
import com.komainu.moxia.edit.SmoothBlockStates;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.noise.SavedNoises;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Deformación, superficie y suavizado sobre la selección de WorldEdit, con los parámetros de ezEdits:
 * {@code /komaedit deform <modo> …}, {@code surface <modo> …}, {@code smooth}, {@code inflate}, {@code deflate} y
 * {@code smoothblocks}. Todo pasa por {@link RegionOperationService}: lectura repartida entre ticks, cálculo en un
 * worker y escritura condicional con //undo.
 */
public final class DeformCommands {
    private static final List<String> DEFORM_MODES = List.of("hexagonalize", "noise", "rotate", "voronoialize",
            "voronoialize2", "voxelize", "cylindrify", "spherify");
    private static final List<String> SURFACE_MODES = List.of("fuzzify", "rockify", "voronoify", "noisify");

    private DeformCommands() {
    }

    /** Cuelga los comandos de {@code /komaedit} (y así de sus alias {@code /ke}, {@code /mx}…). */
    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(modeCommand("deform", DEFORM_MODES, DeformCommands::deform).build());
        root.addChild(modeCommand("deformar", DEFORM_MODES, DeformCommands::deform).build());
        root.addChild(modeCommand("surface", SURFACE_MODES, DeformCommands::surface).build());
        root.addChild(modeCommand("superficie", SURFACE_MODES, DeformCommands::surface).build());
        root.addChild(plainCommand("smooth", DeformCommands::smooth).build());
        root.addChild(plainCommand("suavizar", DeformCommands::smooth).build());
        root.addChild(plainCommand("inflate", (player, args) -> volumeChange(player, args, true)).build());
        root.addChild(plainCommand("inflar", (player, args) -> volumeChange(player, args, true)).build());
        root.addChild(plainCommand("deflate", (player, args) -> volumeChange(player, args, false)).build());
        root.addChild(plainCommand("desinflar", (player, args) -> volumeChange(player, args, false)).build());
        root.addChild(plainCommand("smoothblocks", DeformCommands::smoothBlocks).build());
        root.addChild(plainCommand("suavizarpiezas", DeformCommands::smoothBlocks).build());
    }

    @FunctionalInterface
    private interface ModeAction {
        void run(ServerPlayerEntity player, String mode, String arguments) throws IncompleteRegionException;
    }

    @FunctionalInterface
    private interface PlainAction {
        void run(ServerPlayerEntity player, String arguments) throws IncompleteRegionException;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> modeCommand(
            String name,
            List<String> modes,
            ModeAction action
    ) {
        return literal(name)
                .requires(DeformCommands::canBuild)
                .then(argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            modes.forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> execute(context, player -> action.run(player,
                                StringArgumentType.getString(context, "mode"), "")))
                        .then(argument("arguments", StringArgumentType.greedyString())
                                .executes(context -> execute(context, player -> action.run(player,
                                        StringArgumentType.getString(context, "mode"),
                                        StringArgumentType.getString(context, "arguments"))))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> plainCommand(String name, PlainAction action) {
        return literal(name)
                .requires(DeformCommands::canBuild)
                .executes(context -> execute(context, player -> action.run(player, "")))
                .then(argument("arguments", StringArgumentType.greedyString())
                        .executes(context -> execute(context, player -> action.run(player,
                                StringArgumentType.getString(context, "arguments")))));
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(ServerPlayerEntity player) throws IncompleteRegionException;
    }

    private static int execute(CommandContext<ServerCommandSource> context, PlayerAction action)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            action.run(player);
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Selecciona primero la zona con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    // ---------------------------------------------------------------- deform

    private static void deform(ServerPlayerEntity player, String modeInput, String text)
            throws IncompleteRegionException {
        String mode = modeInput.toLowerCase(Locale.ROOT);
        long random = ThreadLocalRandom.current().nextLong();
        switch (mode) {
            case "hexagonalize", "hexagonalise", "hexagonos", "hex" -> {
                DeformArguments args = DeformArguments.parse(text, Set.of("w"), "");
                args.expectAtMost(5, "deform hexagonalize [tamaño] [hueco] [giroX] [giroZ] [ángulo] [-w perfil]");
                double size = args.number(0, 12.0, 1.0, 256.0, "El tamaño");
                double gap = args.number(1, 0.0, 0.0, 64.0, "El hueco");
                double xRotation = args.number(2, 0.0, -360.0, 360.0, "El giro en X");
                double zRotation = args.number(3, 0.0, -360.0, 360.0, "El giro en Z");
                double offset = args.number(4, 60.0, 0.0, 90.0, "El ángulo");
                submitInSelection(player, 0, "Hexagonalizar", args,
                        (volume, cancel) -> Deformations.hexagonalize(volume, size, gap, xRotation, zRotation, offset));
            }
            case "noise", "ruido" -> {
                DeformArguments args = DeformArguments.parse(text, Set.of("w", "z", "s", "seed", "semilla"), "hv");
                args.expectAtMost(2, "deform noise <ruido> [fuerza] [-z zoom] [-s semilla] [-h] [-v] [-w perfil]");
                if (args.text(0) == null) {
                    throw new IllegalArgumentException("Falta el ruido, por ejemplo deform noise Perlin(F:0.05) 3");
                }
                NoiseSpec spec = SavedNoises.parse(player.getUuid(), args.text(0));
                double strength = args.number(1, 2.0, 0.0, 64.0, "La fuerza");
                double zoom = args.value("z", 1.0, 0.01, 1_000.0, "El zoom");
                long seed = args.seed(args.value("seed") != null ? "seed" : "s", random);
                boolean horizontal = args.has('h');
                boolean vertical = args.has('v');
                submitInSelection(player, 0, "Deformar con ruido", args,
                        (volume, cancel) -> Deformations.noise(volume, spec, strength, zoom, seed, horizontal, vertical));
            }
            case "rotate", "girar", "rotar" -> {
                DeformArguments args = DeformArguments.parse(text, Set.of("w"), "o");
                args.expectAtMost(1, "deform rotate <ángulo> [-o] [-w perfil]");
                double angle = args.required(0, -3_600.0, 3_600.0, "el ángulo");
                int[] selection = selectionBounds(player);
                double[] axis = aimAxis(player.getRotationVector());
                double[] center = args.has('o')
                        ? vector(player.getEyePos())
                        : new double[]{(selection[0] + selection[3] + 1) / 2.0, (selection[1] + selection[4] + 1) / 2.0,
                        (selection[2] + selection[5] + 1) / 2.0};
                int[] box = Deformations.rotatedBounds(selection, axis, angle, center);
                SmoothProfile profile = smoothProfile(args.value("w"));
                RegionOperationService.submit(player, 1, box, "Girar " + format(angle) + "°",
                        new DeformOperation((volume, cancel) -> new DeformOperation.Plan(
                                Deformations.rotate(volume, axis, angle, center), box), profile));
            }
            case "voronoialize", "voronoialise", "voronoi" -> {
                DeformArguments args = DeformArguments.parse(text, Set.of("w", "s", "seed", "semilla"), "");
                args.expectAtMost(2, "deform voronoialize [tamaño] [hueco] [-s semilla] [-w perfil]");
                double size = args.number(0, 12.0, 2.0, 256.0, "El tamaño");
                double gap = args.number(1, 0.0, 0.0, 64.0, "El hueco");
                long seed = args.seed(args.value("seed") != null ? "seed" : "s", random);
                submitInSelection(player, 0, "Voronoi", args,
                        (volume, cancel) -> Deformations.voronoialize(volume, size, gap, seed));
            }
            case "voronoialize2", "voronoialise2", "voronoi2" -> {
                DeformArguments args = DeformArguments.parse(text, Set.of("w", "s", "seed", "semilla", "r", "n"), "");
                args.expectAtMost(2, "deform voronoialize2 <cantidad> [hueco] [-s semilla] [-r uniformidad] "
                        + "[-n profundidad] [-w perfil]");
                int amount = (int) args.required(0, 1.0, 4_096.0, "la cantidad de celdas");
                double gap = args.number(1, 0.0, 0.0, 64.0, "El hueco");
                long seed = args.seed(args.value("seed") != null ? "seed" : "s", random);
                int uniformity = (int) args.value("r", 15.0, 0.0, 64.0, "La uniformidad");
                double normal = args.value("n", 5.0, 0.0, 64.0, "La profundidad del sitio");
                submitInSelection(player, 0, "Voronoi de superficie", args,
                        (volume, cancel) -> Deformations.voronoialize2(volume, amount, gap, seed, uniformity, normal,
                                cancel));
            }
            case "voxelize", "voxelise", "vóxeles", "voxeles" -> {
                DeformArguments args = DeformArguments.parse(text, Set.of("w", "s", "seed", "semilla", "i", "j"), "hv");
                args.expectAtMost(3, "deform voxelize [escalas] [hueco] [distorsión] [-i eje] [-j eje] [-s semilla] "
                        + "[-h] [-v] [-w perfil]");
                double[] scales = scales(args.text(0) == null ? "3" : args.text(0));
                double gap = args.number(1, 0.0, 0.0, 64.0, "El hueco");
                double distortion = args.number(2, 0.0, 0.0, 1.0, "La distorsión");
                Deformations.Axis primary = Deformations.Axis.parse(args.value("i") == null ? "y" : args.value("i"));
                Deformations.Axis secondary = Deformations.Axis.parse(args.value("j") == null ? "-x" : args.value("j"));
                long seed = args.seed(args.value("seed") != null ? "seed" : "s", random);
                boolean horizontal = args.has('h');
                boolean vertical = args.has('v');
                submitInSelection(player, 0, "Voxelizar", args,
                        (volume, cancel) -> Deformations.voxelize(volume, scales, gap, distortion, primary, secondary,
                                seed, horizontal, vertical, cancel));
            }
            case "cylindrify", "cylindrise", "cilindro" -> project(player, text, false);
            case "spherify", "spherise", "esfera" -> project(player, text, true);
            default -> throw new IllegalArgumentException("Modo de deformación desconocido: " + modeInput + " ("
                    + String.join(", ", DEFORM_MODES) + ")");
        }
    }

    private static void project(ServerPlayerEntity player, String text, boolean sphere)
            throws IncompleteRegionException {
        DeformArguments args = DeformArguments.parse(text, Set.of("w", "r", "d", "x", "i", "j"),
                sphere ? "aflost" : "afost");
        args.expectAtMost(0, (sphere ? "deform spherify [-aflost]" : "deform cylindrify [-afost]")
                + " [-r radios] [-d profundidad] [-x mapa] [-i eje] [-j eje] [-w perfil]");
        double[] radii = null;
        if (args.value("r") != null) {
            String[] parts = args.value("r").split(",");
            radii = new double[parts.length];
            for (int index = 0; index < parts.length; index++) {
                radii[index] = DeformArguments.parseNumber(parts[index].trim(), 0.5, 1_024.0, "Cada radio");
            }
        }
        double depth = args.value("d", Double.NaN, 0.5, 1_024.0, "La profundidad");
        Projection.AxisMap axisMap = args.value("x") == null
                ? Projection.AxisMap.DEFAULT
                : Projection.AxisMap.parse(args.value("x"));
        Deformations.Axis primary = Deformations.Axis.parse(args.value("i") == null ? "y" : args.value("i"));
        Deformations.Axis secondary = Deformations.Axis.parse(args.value("j") == null ? "x" : args.value("j"));
        double[] anchor = args.has('o') ? vector(player.getPos()) : null;
        Projection projection = new Projection(new Projection.Settings(sphere, args.has('l'), radii, depth, axisMap,
                primary, secondary, args.has('a'), args.has('f'), args.has('s'), args.has('t'), anchor),
                selectionBounds(player), player.getServerWorld().getTopY());
        int[] box = projection.box();
        SmoothProfile profile = smoothProfile(args.value("w"));
        double[] figureRadii = projection.radii();
        String radiusText = sphere
                ? "radio " + format(figureRadii[0]) + (figureRadii[0] == figureRadii[1] && figureRadii[1] == figureRadii[2]
                ? "" : "," + format(figureRadii[1]) + "," + format(figureRadii[2]))
                : "radio " + format(figureRadii[0]) + (figureRadii[0] == figureRadii[2] ? "" : "," + format(figureRadii[2]))
                + " y largo " + format(projection.halfLength() * 2.0);
        player.sendMessage(prefix().append(Text.literal((sphere ? "Esfera" : "Cilindro") + " de " + radiusText
                + " y profundidad " + format(projection.depth()) + " en " + box[0] + " " + box[1] + " " + box[2]
                + " → " + box[3] + " " + box[4] + " " + box[5] + ": " + projection.boxVolume()
                + " bloques fuera de la selección se " + (args.has('a') ? "rellenan donde toque la figura"
                : "sustituyen (con -a se respeta lo que la figura no ocupa)") + ".").formatted(Formatting.GRAY)),
                false);
        RegionOperationService.submit(player, 0, box, sphere ? "Esferificar" : "Cilindrificar",
                new DeformOperation((volume, cancel) -> new DeformOperation.Plan(projection.field(volume), box),
                        profile));
    }

    // ---------------------------------------------------------------- surface

    private static void surface(ServerPlayerEntity player, String modeInput, String text)
            throws IncompleteRegionException {
        SurfaceOps.Mode mode = SurfaceOps.Mode.byId(modeInput);
        DeformArguments args = DeformArguments.parse(text, Set.of("w", "m", "seed", "semilla"), "cet");
        long seed = args.seed("seed", ThreadLocalRandom.current().nextLong());
        double radius = args.required(0, 0.1, 32.0, "el radio");
        SurfaceOps.Displacement displacement;
        int smoothRadius;
        int smoothIterations;
        switch (mode) {
            case FUZZIFY -> {
                args.expectAtMost(3, "surface fuzzify <radio> [radioSuave] [iteraciones] [-c] [-e] [-m máscara] [-t]");
                displacement = SurfaceOps.Displacement.white(seed);
                smoothRadius = (int) args.number(1, 0.0, 0.0, 8.0, "El radio de suavizado");
                smoothIterations = (int) args.number(2, 0.0, 0.0, 16.0, "Las iteraciones");
            }
            case ROCKIFY -> {
                args.expectAtMost(5, "surface rockify <radio> [tamaño] [octavas] [radioSuave] [iteraciones] [-c] [-e] "
                        + "[-m máscara] [-t]");
                double size = args.number(1, 10.0, 1.0, 512.0, "El tamaño del ruido");
                int octaves = (int) args.number(2, 1.0, 1.0, 8.0, "Las octavas");
                displacement = SurfaceOps.Displacement.perlin(size, octaves, seed);
                smoothRadius = (int) args.number(3, 1.0, 0.0, 8.0, "El radio de suavizado");
                smoothIterations = (int) args.number(4, 4.0, 0.0, 16.0, "Las iteraciones");
            }
            case VORONOIFY -> {
                args.expectAtMost(4, "surface voronoify <radio> [celda] [radioSuave] [iteraciones] [-c] [-e] "
                        + "[-m máscara] [-t]");
                double cell = args.number(1, 12.0, 1.0, 512.0, "El tamaño de celda");
                displacement = SurfaceOps.Displacement.cells(cell, seed);
                smoothRadius = (int) args.number(2, 0.0, 0.0, 8.0, "El radio de suavizado");
                smoothIterations = (int) args.number(3, 0.0, 0.0, 16.0, "Las iteraciones");
            }
            case NOISIFY -> {
                args.expectAtMost(5, "surface noisify <radio> <ruido> [escala] [radioSuave] [iteraciones] [-c] [-e] "
                        + "[-m máscara] [-t]");
                if (args.text(1) == null) {
                    throw new IllegalArgumentException("Falta el ruido, por ejemplo surface noisify 3 Perlin(F:0.08)");
                }
                NoiseSpec spec = SavedNoises.parse(player.getUuid(), args.text(1));
                double scale = args.number(2, 1.0, 0.01, 1_000.0, "La escala");
                displacement = SurfaceOps.Displacement.of(spec, scale, seed);
                smoothRadius = (int) args.number(3, 1.0, 0.0, 8.0, "El radio de suavizado");
                smoothIterations = (int) args.number(4, 4.0, 0.0, 16.0, "Las iteraciones");
            }
            default -> throw new IllegalStateException();
        }
        IntPredicate mask = args.value("m") == null ? null : rawIdMask(BlockMatcher.parse(args.value("m"),
                DeformCommands::tagIds));
        SurfaceOps.Settings settings = new SurfaceOps.Settings(radius, displacement, smoothRadius, smoothIterations,
                args.has('c'), args.has('e'), mask, args.has('t'));
        SmoothProfile profile = smoothProfile(args.value("w"));
        RegionOperationService.submit(player, settings.padding(), "Superficie · " + mode.id(),
                new DeformOperation(DeformOperation.inSelection(
                        (volume, cancel) -> SurfaceOps.apply(volume, settings, cancel)), profile));
    }

    // ---------------------------------------------------------------- smoothing

    private static void smooth(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        DeformArguments args = DeformArguments.parse(text, Set.of("w"), "");
        args.expectAtMost(3, "smooth <radios> [iteraciones] [sesgo] [-w perfil]");
        double[] radii = SmoothingOps.parseRadii(args.text(0) == null ? "2" : args.text(0), 16.0);
        int iterations = (int) args.number(1, 1.0, 1.0, 32.0, "Las iteraciones");
        double bias = args.number(2, 0.0, -1.0, 1.0, "El sesgo");
        SmoothProfile profile = smoothProfile(args.value("w"));
        RegionOperationService.submit(player, SmoothingOps.padding(radii, iterations), "Suavizar",
                new DeformOperation(DeformOperation.inSelection(
                        (volume, cancel) -> SmoothingOps.smooth(volume, radii, iterations, bias, cancel)), profile));
    }

    private static void volumeChange(ServerPlayerEntity player, String text, boolean grow)
            throws IncompleteRegionException {
        DeformArguments args = DeformArguments.parse(text, Set.of("w"), "");
        args.expectAtMost(1, (grow ? "inflate" : "deflate") + " <radios> [-w perfil]");
        if (args.text(0) == null) {
            throw new IllegalArgumentException("Faltan los radios, por ejemplo " + (grow ? "inflate" : "deflate")
                    + " 2 o 2,1,2");
        }
        double[] radii = SmoothingOps.parseRadii(args.text(0), 30.0);
        SmoothProfile profile = smoothProfile(args.value("w"));
        RegionOperationService.submit(player, SmoothingOps.padding(radii, 1) / 2 + 2, grow ? "Inflar" : "Desinflar",
                new DeformOperation(DeformOperation.inSelection((volume, cancel) -> grow
                        ? SmoothingOps.inflate(volume, radii, cancel)
                        : SmoothingOps.deflate(volume, radii, cancel)), profile));
    }

    private static void smoothBlocks(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        DeformArguments args = DeformArguments.parse(text, Set.of("w"), "");
        args.expectAtMost(3, "smoothblocks <perfil> [radio] [sesgo]");
        String profileText = args.text(0) != null ? args.text(0) : args.value("w");
        if (profileText == null) {
            throw new IllegalArgumentException("Falta el perfil, por ejemplo smoothblocks SlabsAndStairs 2 0");
        }
        SmoothProfile profile = smoothProfile(profileText);
        if (!profile.active()) {
            throw new IllegalArgumentException("smoothblocks necesita un perfil con piezas (" + SmoothMode.names() + ")");
        }
        double radius = args.number(1, 2.0, 0.0, 16.0, "El radio");
        double bias = args.number(2, 0.0, -1.0, 1.0, "El sesgo");
        double[] radii = {radius, radius, radius};
        RegionOperationService.submit(player, SmoothingOps.padding(radii, 1), "Suavizar con piezas",
                new DeformOperation(DeformOperation.inSelection(
                        (volume, cancel) -> SmoothingOps.smooth(volume, radii, 1, bias, cancel)), profile));
    }

    // ---------------------------------------------------------------- helpers

    private static void submitInSelection(
            ServerPlayerEntity player,
            int padding,
            String description,
            DeformArguments args,
            java.util.function.BiFunction<com.komainu.moxia.deform.Volume, Runnable,
                    com.komainu.moxia.deform.DeformField> field
    ) throws IncompleteRegionException {
        SmoothProfile profile = smoothProfile(args.value("w"));
        RegionOperationService.submit(player, padding, description,
                new DeformOperation(DeformOperation.inSelection(field), profile));
    }

    private static int[] selectionBounds(ServerPlayerEntity player) throws IncompleteRegionException {
        Region selection = WorldEditService.selection(player);
        BlockVector3 minimum = selection.getMinimumPoint();
        BlockVector3 maximum = selection.getMaximumPoint();
        return new int[]{minimum.x(), minimum.y(), minimum.z(), maximum.x(), maximum.y(), maximum.z()};
    }

    /** Eje de giro según la mirada; si está a menos de 10° de un eje del mundo, se endereza a ese eje. */
    static double[] aimAxis(Vec3d look) {
        double[] axis = {look.x, look.y, look.z};
        double length = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        for (int index = 0; index < 3; index++) {
            axis[index] /= length;
        }
        double cosine = Math.cos(Math.toRadians(10.0));
        for (int index = 0; index < 3; index++) {
            if (Math.abs(axis[index]) >= cosine) {
                double[] snapped = new double[3];
                snapped[index] = Math.signum(axis[index]);
                return snapped;
            }
        }
        return axis;
    }

    private static double[] scales(String text) {
        String[] parts = text.split(",");
        if (parts.length != 1 && parts.length != 3) {
            throw new IllegalArgumentException("Las escalas son un número o tres separados por comas");
        }
        double[] scales = new double[3];
        for (int index = 0; index < 3; index++) {
            scales[index] = DeformArguments.parseNumber(parts[parts.length == 1 ? 0 : index].trim(), 1.0, 256.0,
                    "Cada escala");
        }
        return scales;
    }

    private static Set<String> tagIds(String tag) {
        Identifier identifier = Identifier.tryParse(tag);
        if (identifier == null) {
            throw new IllegalArgumentException("Etiqueta no válida: #" + tag);
        }
        Set<String> ids = new HashSet<>();
        for (RegistryEntry<Block> entry : Registries.BLOCK.iterateEntries(TagKey.of(RegistryKeys.BLOCK, identifier))) {
            ids.add(Registries.BLOCK.getId(entry.value()).toString());
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("La etiqueta #" + tag + " no existe o está vacía");
        }
        return ids;
    }

    /** La máscara pasada a ids crudos de estado, para usarla en el worker sin tocar los registros. */
    private static IntPredicate rawIdMask(BlockMatcher matcher) {
        IntOpenHashSet rawIds = new IntOpenHashSet();
        for (String id : matcher.ids()) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
                throw new IllegalArgumentException("Bloque desconocido en la máscara: " + id);
            }
            for (BlockState state : Registries.BLOCK.get(identifier).getStateManager().getStates()) {
                rawIds.add(Block.getRawIdFromState(state));
            }
        }
        boolean inverted = matcher.inverted();
        return rawId -> rawIds.contains(rawId) != inverted;
    }

    private static double[] vector(Vec3d vector) {
        return new double[]{vector.x, vector.y, vector.z};
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static boolean canBuild(ServerCommandSource source) {
        return source.getEntity() instanceof ServerPlayerEntity player
                && KomaPermissions.can(player, KomaPermissions.BUILD);
    }

    private static net.minecraft.text.MutableText prefix() {
        return Text.literal("KomaEdit · ").formatted(Formatting.DARK_AQUA);
    }

    private static int fail(ServerCommandSource source, String message) {
        source.sendError(prefix().append(Text.literal(message == null || message.isBlank()
                ? "Operación inválida" : message).formatted(Formatting.RED)));
        return 0;
    }

    /** Perfil de {@code -w} con sus materiales fijos ya comprobados, para que un error salga al escribir la orden. */
    private static SmoothProfile smoothProfile(String text) {
        SmoothProfile profile = SmoothProfile.parse(text);
        SmoothBlockStates.validate(profile);
        return profile;
    }
}
