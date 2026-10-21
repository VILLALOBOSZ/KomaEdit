package com.komainu.moxia.command;

import com.komainu.moxia.deform.DeformArguments;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.fluid.Cascade;
import com.komainu.moxia.fluid.FloodFill;
import com.komainu.moxia.fluid.FluidBall;
import com.komainu.moxia.fluid.FluidKind;
import com.komainu.moxia.fluid.FluidService;
import com.komainu.moxia.fluid.LooseFlows;
import com.komainu.moxia.permission.KomaPermissions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.IncompleteRegionException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Agua y lava: {@code /komaedit fluido} ({@code fluid}) con rellenar un hueco cerrado, la bola de fluido, cascadas
 * y ríos cortos y quitar los flujos sueltos. Todo pasa por {@link FluidService}.
 */
public final class FluidCommands {
    static final String FILL_USAGE = "fluido rellenar [agua|lava] [nivel|clic|clic+n|borde|~] [-d hbe] [-l límite]"
            + " [-r radio] [-w] [-p x,y,z]";
    static final String BALL_USAGE = "fluido bola [agua|lava|nieve] [radio] [-s] [-a] [-c] [-p x,y,z]";
    static final String FALL_USAGE = "fluido cascada [agua|lava] [-o radio de la poza] [-n] [-r margen] [-a ancho]"
            + " [-b] [-p x,y,z]";
    static final String CLEAN_USAGE = "fluido limpiar [agua|lava|todo] [-m máscara]";

    private FluidCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : List.of("fluido", "fluid")) {
            root.addChild(family(name).build());
        }
    }

    @FunctionalInterface
    private interface Action {
        String run(ServerPlayerEntity player, String arguments) throws IncompleteRegionException;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> family(String name) {
        LiteralArgumentBuilder<ServerCommandSource> family = literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canAny(player, KomaPermissions.BUILD + ".fluid"))
                .executes(context -> execute(context, (player, arguments) -> "Uso: /komaedit " + FILL_USAGE
                        + " · /komaedit " + BALL_USAGE + " · /komaedit " + FALL_USAGE + " · /komaedit "
                        + CLEAN_USAGE, ""));
        for (String mode : List.of("rellenar", "fill", "floodfill")) {
            family.then(mode(mode, FluidService.FILL, FluidCommands::fill, "agua", "lava", "clic", "clic+1",
                    "borde", "~", "-d", "hb", "hbe", "-l", "-r", "-w", "-p"));
        }
        for (String mode : List.of("bola", "ball", "fluidball")) {
            family.then(mode(mode, FluidService.BALL, FluidCommands::ball, "agua", "lava", "nieve", "4", "6", "-s",
                    "-a", "-c", "-p"));
        }
        for (String mode : List.of("cascada", "waterfall", "fall", "rio")) {
            family.then(mode(mode, FluidService.FALL, FluidCommands::cascade, "agua", "lava", "-o", "2", "-n", "-r",
                    "-a", "-b", "-p"));
        }
        for (String mode : List.of("limpiar", "clean", "sueltos")) {
            family.then(mode(mode, FluidService.CLEAN, FluidCommands::clean, "agua", "lava", "todo", "-m"));
        }
        return family;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> mode(String name, String node, Action action,
                                                                   String... examples) {
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canBuild(player, node))
                .executes(context -> execute(context, action, ""))
                .then(argument("arguments", StringArgumentType.greedyString())
                        .suggests(tail(examples))
                        .executes(context -> execute(context, action,
                                StringArgumentType.getString(context, "arguments"))));
    }

    private static SuggestionProvider<ServerCommandSource> tail(String... examples) {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            for (String example : examples) {
                tail.suggest(example);
            }
            return tail.buildFuture();
        };
    }

    private static int execute(CommandContext<ServerCommandSource> context, Action action, String arguments)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            String message = action.run(player, arguments);
            if (message != null) {
                context.getSource().sendFeedback(() -> Feedback.prefix().append(Text.literal(message)
                        .formatted(Formatting.GRAY)), false);
            }
            return 1;
        } catch (IncompleteRegionException exception) {
            return EzCommandSupport.fail(context.getSource(), "Selecciona primero la zona con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }

    // ---------------------------------------------------------------- modos

    private static String fill(ServerPlayerEntity player, String text) {
        KomaPermissions.requireBuild(player, FluidService.FILL);
        DeformArguments args = DeformArguments.parse(text, Set.of("d", "l", "r", "p"), "w");
        args.expectAtMost(2, FILL_USAGE);
        FluidKind fluid = FluidKind.WATER;
        String level = null;
        for (int index = 0; index < args.count(); index++) {
            String token = args.text(index);
            if (FluidKind.names(token, false)) {
                fluid = FluidKind.parse(token, false);
            } else if (level == null) {
                level = token;
            } else {
                throw new IllegalArgumentException("Uso: /komaedit " + FILL_USAGE);
            }
        }
        FloodFill.Directions directions = args.value("d") == null ? FloodFill.Directions.DEFAULT
                : FloodFill.Directions.parse(args.value("d"));
        int limit = (int) args.value("l", FluidService.DEFAULT_LIMIT, 1, 4_000_000, "El límite");
        int radius = (int) args.value("r", FluidService.DEFAULT_RADIUS, 4, FluidService.MAX_RADIUS, "El radio");
        return FluidService.fill(player, fluid, level, directions, limit, radius, args.has('w'), point(args));
    }

    private static String ball(ServerPlayerEntity player, String text) {
        KomaPermissions.requireBuild(player, FluidService.BALL);
        DeformArguments args = DeformArguments.parse(text, Set.of("p"), "sac");
        args.expectAtMost(2, BALL_USAGE);
        FluidKind fluid = FluidKind.WATER;
        double radius = 4.0;
        for (int index = 0; index < args.count(); index++) {
            String token = args.text(index);
            if (FluidKind.names(token, true)) {
                fluid = FluidKind.parse(token, true);
            } else {
                radius = DeformArguments.parseNumber(token, 0.0, FluidBall.Settings.MAX_RADIUS, "El radio");
            }
        }
        return FluidService.ball(player, new FluidBall.Settings(fluid, radius, args.has('s'), args.has('a'),
                args.has('c')), point(args));
    }

    private static String cascade(ServerPlayerEntity player, String text) {
        KomaPermissions.requireBuild(player, FluidService.FALL);
        DeformArguments args = DeformArguments.parse(text, Set.of("o", "r", "a", "p"), "nb");
        args.expectAtMost(1, FALL_USAGE);
        FluidKind fluid = args.text(0) == null ? FluidKind.WATER : FluidKind.parse(args.text(0), false);
        Cascade.Settings settings = new Cascade.Settings(fluid, !args.has('n'),
                (int) args.value("o", 2, 0, 4, "El radio de la poza"),
                (int) args.value("r", 2, 0, 16, "El margen"),
                (int) args.value("a", 1, 1, 7, "El ancho"),
                !args.has('b'));
        return FluidService.cascade(player, settings, point(args));
    }

    /** También es el modo {@code sueltos} de {@code /komaedit retocar} (y de su pincel). */
    static String clean(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, FluidService.CLEAN);
        DeformArguments args = DeformArguments.parse(text, Set.of("m"), "");
        args.expectAtMost(1, CLEAN_USAGE);
        String which = args.text(0) == null ? "todo" : args.text(0).toLowerCase(Locale.ROOT);
        LooseFlows.Target target = switch (which) {
            case "agua", "water" -> LooseFlows.Target.WATER;
            case "lava" -> LooseFlows.Target.LAVA;
            case "todo", "all", "ambos", "both" -> LooseFlows.Target.ALL;
            default -> throw new IllegalArgumentException("Limpia agua, lava o todo, no «" + args.text(0) + "»");
        };
        return FluidService.clean(player, target, args.value("m"));
    }

    private static int[] point(DeformArguments args) {
        return args.value("p") == null ? null : FluidService.parsePoint(args.value("p"));
    }
}
