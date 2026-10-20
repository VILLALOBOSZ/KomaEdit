package com.komainu.moxia.command;

import com.komainu.moxia.analysis.AnalyzeService;
import com.komainu.moxia.biome.BiomeService;
import com.komainu.moxia.deform.DeformArguments;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.text.TextFonts;
import com.komainu.moxia.text.TextRequest;
import com.komainu.moxia.text.TextService;
import com.komainu.moxia.utility.FluidEngine;
import com.komainu.moxia.utility.SnowEngine;
import com.komainu.moxia.utility.UtilityOperations;
import com.komainu.moxia.worldedit.WorldEditService;
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
 * Utilidades de uso diario: {@code text}, {@code analyze}, {@code gravity}, {@code snow}, {@code drain},
 * {@code waterlog} y {@code biome}, con sus alias en español.
 */
public final class BuilderCommands {
    private BuilderCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : List.of("text", "texto")) {
            root.addChild(textCommand(name).build());
        }
        for (String name : List.of("analyze", "analizar", "materiales")) {
            root.addChild(command(name, player -> KomaPermissions.can(player, AnalyzeService.PERMISSION),
                    BuilderCommands::analyze, "-m", "#existing", "-n", "20").build());
        }
        for (String name : List.of("gravity", "gravedad")) {
            root.addChild(command(name, player -> KomaPermissions.canBuild(player, "gravity"),
                    BuilderCommands::gravity, "-a", "-m", "sand,gravel", "!#existing").build());
        }
        for (String name : List.of("snow", "nieve")) {
            root.addChild(command(name, player -> KomaPermissions.canBuild(player, "snow"), BuilderCommands::snow,
                    "1", "2", "3", "4", "-r", "-f", "-s", "-h", "-p", "-m", "grass_block,stone").build());
        }
        for (String name : List.of("drain", "drenar")) {
            root.addChild(command(name, player -> KomaPermissions.canBuild(player, "drain"), BuilderCommands::drain,
                    "water", "lava", "all", "-k", "-m").build());
        }
        for (String name : List.of("waterlog", "inundar")) {
            root.addChild(command(name, player -> KomaPermissions.canBuild(player, "waterlog"),
                    BuilderCommands::flood, "62", "~", "~-1", "-b", "-m").build());
        }
        for (String name : List.of("biome", "bioma")) {
            root.addChild(biomeCommand(name).build());
        }
    }

    @FunctionalInterface
    private interface Action {
        String run(ServerPlayerEntity player, String arguments) throws IncompleteRegionException;
    }

    @FunctionalInterface
    private interface PlayerRun {
        /** @return mensaje para el jugador o {@code null} si la operación ya avisa */
        String run(ServerPlayerEntity player) throws IncompleteRegionException;
    }

    @FunctionalInterface
    private interface Gate {
        boolean test(ServerPlayerEntity player);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name, Gate gate, Action action,
                                                                      String... examples) {
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player && gate.test(player))
                .executes(context -> execute(context, player -> action.run(player, "")))
                .then(argument("arguments", StringArgumentType.greedyString())
                        .suggests(tail(examples))
                        .executes(context -> execute(context, player -> action.run(player,
                                StringArgumentType.getString(context, "arguments")))));
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

    private static int execute(CommandContext<ServerCommandSource> context, PlayerRun action)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            String message = action.run(player);
            if (message != null) {
                context.getSource().sendFeedback(() -> prefix().append(Text.literal(message)
                        .formatted(Formatting.GRAY)), false);
            }
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Selecciona primero la zona con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    // ---------------------------------------------------------------- text

    private static LiteralArgumentBuilder<ServerCommandSource> textCommand(String name) {
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canBuild(player, "text"))
                .executes(context -> execute(context, player -> "Uso: /komaedit " + TextRequest.USAGE))
                .then(literal("fonts").executes(BuilderCommands::fonts))
                .then(literal("fuentes").executes(BuilderCommands::fonts))
                .then(argument("text", StringArgumentType.greedyString())
                        .suggests(tail("\"Hola\"", "stone", "##Magma", "-f", "sans", "mono", "fina", "-s", "8",
                                "-d", "2", "-b", "1", "-o", "black_concrete", "-g", "v", "-w", "SlabsAndStairs", "-m",
                                "air", "-k", "-l", "-a", "-v", "-h"))
                        .executes(context -> execute(context, player -> TextService.start(player,
                                TextRequest.parse(StringArgumentType.getString(context, "text"))))));
    }

    private static int fonts(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return execute(context, player -> {
            StringBuilder builder = new StringBuilder("Fuentes de serie (licencia OFL): ");
            for (TextFonts.Builtin builtin : TextFonts.builtins()) {
                builder.append(builtin.id()).append(" (").append(builtin.displayName()).append(") ");
            }
            List<String> custom = TextFonts.custom(TextService.fontsDirectory());
            builder.append("· Propias en config/komaedit/fonts: ")
                    .append(custom.isEmpty() ? "ninguna" : String.join(", ", custom));
            return builder.toString();
        });
    }

    // ---------------------------------------------------------------- analyze

    private static String analyze(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        DeformArguments args = DeformArguments.parse(text, Set.of("m", "n"), "");
        args.expectAtMost(0, "analyze [-m máscara] [-n líneas]");
        int lines = (int) args.value("n", 10.0, 1.0, 100.0, "Las líneas");
        AnalyzeService.start(player, args.value("m"), lines);
        return null;
    }

    // ---------------------------------------------------------------- gravity

    private static String gravity(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "gravity");
        DeformArguments args = DeformArguments.parse(text, Set.of("m"), "a");
        args.expectAtMost(0, "gravity [-a] [-m máscara]");
        RegionMasks.Bound mask = mask(player, args.value("m"));
        boolean unsupported = args.has('a');
        RegionOperationService.submit(player, 1, unsupported ? "Gravedad (solo sin apoyo)" : "Gravedad",
                new UtilityOperations.Gravity(mask, unsupported));
        return null;
    }

    // ---------------------------------------------------------------- snow

    private static String snow(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "snow");
        DeformArguments args = DeformArguments.parse(text, Set.of("r", "f", "s", "h", "m"), "p");
        args.expectAtMost(1, "snow [capas 1–8] [-r radio] [-f relleno] [-s sombra] [-h altura] [-p] [-m máscara]");
        SnowEngine.Settings settings = new SnowEngine.Settings(
                (int) args.number(0, 2.0, 1.0, 8.0, "Las capas"),
                (int) args.value("r", 2.0, 0.0, 16.0, "El radio"),
                args.value("f", 1.0, 0.0, 1.0, "El relleno"),
                (int) args.value("s", 2.0, 0.0, 8.0, "La sombra"),
                (int) args.value("h", 0.0, 0.0, 8.0, "La altura"),
                args.has('p')
        );
        RegionMasks.Bound mask = mask(player, args.value("m"));
        RegionOperationService.submit(player, settings.padding(), "Nieve (" + settings.layers() + " capas)",
                new UtilityOperations.Snow(settings, mask));
        return null;
    }

    // ---------------------------------------------------------------- drain / waterlog

    private static String drain(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "drain");
        DeformArguments args = DeformArguments.parse(text, Set.of("m"), "k");
        args.expectAtMost(1, "drain [water|lava|all] [-k] [-m máscara]");
        String modeText = args.text(0) == null ? "water" : args.text(0).toLowerCase(Locale.ROOT);
        FluidEngine.Drain mode = switch (modeText) {
            case "water", "agua" -> FluidEngine.Drain.WATER;
            case "lava" -> FluidEngine.Drain.LAVA;
            case "all", "todo", "both", "ambos" -> FluidEngine.Drain.ALL;
            default -> throw new IllegalArgumentException("Drena water, lava o all, no «" + args.text(0) + "»");
        };
        RegionMasks.Bound mask = mask(player, args.value("m"));
        String description = switch (mode) {
            case WATER -> "Drenar agua";
            case LAVA -> "Drenar lava";
            case ALL -> "Drenar agua y lava";
        };
        RegionOperationService.submit(player, 0, description,
                new UtilityOperations.Drain(mode, args.has('k'), mask));
        return null;
    }

    private static String flood(ServerPlayerEntity player, String text) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "waterlog");
        DeformArguments args = DeformArguments.parse(text, Set.of("m"), "b");
        args.expectAtMost(1, "waterlog [nivel|~] [-b] [-m máscara]");
        int top = WorldEditService.selection(player).getMaximumPoint().y();
        int level = level(args.text(0), top, player.getBlockY());
        RegionMasks.Bound mask = mask(player, args.value("m"));
        RegionOperationService.submit(player, 0, "Inundar hasta y=" + level,
                new UtilityOperations.Flood(level, args.has('b'), mask));
        return null;
    }

    /** Nivel del agua: un número, {@code ~} (tus pies) o {@code ~n}; sin nada, lo alto de la selección. */
    static int level(String input, int fallback, int feet) {
        if (input == null) {
            return fallback;
        }
        try {
            if (input.startsWith("~")) {
                return feet + (input.length() == 1 ? 0 : Integer.parseInt(input.substring(1)));
            }
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El nivel es una altura (62), ~ o ~-2, no «" + input + "»");
        }
    }

    // ---------------------------------------------------------------- biome

    private static LiteralArgumentBuilder<ServerCommandSource> biomeCommand(String name) {
        SuggestionProvider<ServerCommandSource> biomes = (context, builder) -> {
            String remaining = builder.getRemaining();
            var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            if (remaining.indexOf(' ') < 0) {
                for (String id : BiomeService.ids(context.getSource().getServer())) {
                    String shortId = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
                    if (shortId.startsWith(tail.getRemaining()) || id.startsWith(tail.getRemaining())) {
                        tail.suggest(shortId);
                    }
                }
            } else {
                tail.suggest("-v");
                tail.suggest("-m");
            }
            return tail.buildFuture();
        };
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canBuild(player, "biome"))
                .executes(context -> execute(context, player -> "Uso: /komaedit biome <bioma> [-v] [-m máscara]"))
                .then(argument("arguments", StringArgumentType.greedyString())
                        .suggests(biomes)
                        .executes(context -> execute(context, player -> {
                            DeformArguments args = DeformArguments.parse(
                                    StringArgumentType.getString(context, "arguments"), Set.of("m"), "v");
                            args.expectAtMost(1, "biome <bioma> [-v] [-m máscara]");
                            return BiomeService.paintSelection(player, BiomeService.parse(args.text(0)),
                                    args.has('v'), args.value("m"));
                        })));
    }

    // ---------------------------------------------------------------- comunes

    private static RegionMasks.Bound mask(ServerPlayerEntity player, String input) {
        return input == null ? null : RegionMasks.parse(player, input);
    }

    private static Text prefixText(String text, Formatting color) {
        return prefix().append(Text.literal(text).formatted(color));
    }

    private static net.minecraft.text.MutableText prefix() {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD);
    }

    private static int fail(ServerCommandSource source, String message) {
        source.sendError(prefixText(message == null || message.isBlank() ? "Operación inválida" : message,
                Formatting.RED));
        return 0;
    }
}
