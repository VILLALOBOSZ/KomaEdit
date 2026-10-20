package com.komainu.moxia.command;

import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.placement.PlacementBrushStructures;
import com.komainu.moxia.placement.PlacementService;
import com.komainu.moxia.placement.PlacementSettings;
import com.komainu.moxia.placement.ParamText;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.IncompleteRegionException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Colocación de estructuras al estilo de ezEdits:
 * {@code /komaedit place|colocar}, {@code array|hilera} y {@code scatter|dispersar}, todos con la forma
 * {@code <estructura> [<primaria>] [<secundaria>] [banderas]}.
 */
public final class PlacementCommands {
    private static final List<String> STRUCTURES = List.of("Clipboard", "Cl(O:C)", "Cl(PM:smooth)",
            "Sc(N:arbol_.*)", "Sh(S:Cone,P:stone)", "Sh(S:Crystal(Sides:6),P:amethyst_block)",
            "Sh(S:Torus(Thickness:0.4),P:gold_block)", "Ex(E:=y*.5+.5,P:##Magma)",
            "TS(S:Ellipsoid,T:=y*.5+.5,P:##Grayscale)", "Ic(P:stone,R:0.5,S:1)");
    private static final List<String> ALIGNMENTS = List.of("Constant", "C(D:aim)", "Random", "Noise", "Aim",
            "Upward", "PlayerRelative", "SurfaceNormal", "Expression(E:\"=rx=z;rz=-x\",S:N)", "10%C,2%R");
    private static final List<String> COMMON_FLAGS = List.of("-s 20,20,20", "-o 0.5,2", "-c 90", "-k x", "-f XZ",
            "-r Y", "-a", "-j MULTIPLES_90", "-x", "-w Slabs", "-w SlabsAndStairs(C:0.7)", "-i 7");
    private static final List<String> SCATTER_FLAGS = List.of("-n 2", "-u 15", "-d up", "-e 0.5", "-m grass_block",
            "-l pink_wool", "-t", "-h Box(D:\"40,40,40\")", "-h Ellipsoid", "-h Cylinder");
    private static final List<String> ARRAY_FLAGS = List.of("-g 0", "-g 4", "-y 1", "-q 0.5,2", "-p 0:0:0",
            "-n consistent", "-n upright", "-n horizontal", "-b");
    private static final List<String> COPY_FLAGS = List.of("-copias 8", "-desplazar 0,1,0", "-revolver 30",
            "-pivote 6,0,0", "-torcer 15");

    private PlacementCommands() {
    }

    /** Cuelga los comandos de {@code /komaedit} (y de sus alias, que redirigen a la misma raíz). */
    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        PlacementBrushStructures.register();
        for (String name : new String[]{"place", "colocar"}) {
            root.addChild(command(name, PlacementSettings.Kind.PLACE).build());
        }
        for (String name : new String[]{"array", "hilera"}) {
            root.addChild(command(name, PlacementSettings.Kind.ARRAY).build());
        }
        for (String name : new String[]{"scatter", "dispersar"}) {
            root.addChild(command(name, PlacementSettings.Kind.SCATTER).build());
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name, PlacementSettings.Kind kind) {
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canBuild(player, kind.id()))
                .executes(context -> usage(context, kind))
                .then(argument("placement", StringArgumentType.greedyString())
                        .suggests(suggestions(kind))
                        .executes(context -> run(context, kind,
                                StringArgumentType.getString(context, "placement"))));
    }

    private static SuggestionProvider<ServerCommandSource> suggestions(PlacementSettings.Kind kind) {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            int lastSpace = remaining.lastIndexOf(' ');
            var tail = builder.createOffset(builder.getStart() + lastSpace + 1);
            String typed = remaining.substring(lastSpace + 1).toLowerCase(Locale.ROOT);
            List<String> words = ParamText.words(remaining);
            List<String> options;
            if (lastSpace < 0) {
                options = STRUCTURES;
            } else {
                long positional = words.stream().skip(1).filter(word -> !word.startsWith("-")).count();
                options = new java.util.ArrayList<>();
                if (positional < 2 && !typed.startsWith("-")) {
                    options.addAll(ALIGNMENTS);
                    if (kind == PlacementSettings.Kind.ARRAY) {
                        options.addAll(List.of("Tangential", "Orthogonal", "O(A:90)"));
                    }
                }
                options.addAll(COMMON_FLAGS);
                options.addAll(kind == PlacementSettings.Kind.SCATTER ? SCATTER_FLAGS
                        : kind == PlacementSettings.Kind.ARRAY ? ARRAY_FLAGS : COPY_FLAGS);
            }
            for (String option : options) {
                if (option.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    tail.suggest(option);
                }
            }
            return tail.buildFuture();
        };
    }

    private static int run(CommandContext<ServerCommandSource> context, PlacementSettings.Kind kind, String input)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            String first = ParamText.words(input).stream().findFirst().orElse("");
            String structure = ParamText.normalize(first.contains("(") ? first.substring(0, first.indexOf('(')) : first);
            if (structure.startsWith("cl") || structure.startsWith("sc") || structure.equals("portapapeles")
                    || structure.equals("esquema")) {
                KomaPermissions.require(player, KomaPermissions.CLIPBOARD);
            }
            String started = PlacementService.start(player, kind, input);
            context.getSource().sendFeedback(() -> prefix().append(Text.literal(started).formatted(Formatting.GRAY)),
                    false);
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), kind == PlacementSettings.Kind.ARRAY
                    ? "Marca al menos dos puntos con P o selecciona con WorldEdit (//pos1, //pos2, //sel convex)"
                    : "Selecciona una región con WorldEdit o usa -h Box(D:\"40,40,40\")");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int usage(CommandContext<ServerCommandSource> context, PlacementSettings.Kind kind) {
        String example = switch (kind) {
            case PLACE -> "/komaedit place Sh(S:Cone,P:stone) Aim Constant -s 12,36,12";
            case ARRAY -> "/komaedit array Clipboard Tangential Constant -g 2 -q 0.5,2";
            case SCATTER -> "/komaedit scatter Clipboard SurfaceNormal Random -n 1 -d up -e 0.6";
        };
        context.getSource().sendFeedback(() -> prefix().append(Text.literal(
                "Uso: <estructura> [primaria] [secundaria] [banderas] · ejemplo: " + example
                        + " · estructuras: Clipboard, Schematic, Shape, Expression, TexturedShape, Icosphere"
        ).formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static MutableText prefix() {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD);
    }

    private static int fail(ServerCommandSource source, String message) {
        source.sendError(Text.literal("KomaEdit · ").formatted(Formatting.GOLD).append(Text.literal(
                message == null || message.isBlank() ? "Operación inválida" : message).formatted(Formatting.RED)));
        return 0;
    }
}
