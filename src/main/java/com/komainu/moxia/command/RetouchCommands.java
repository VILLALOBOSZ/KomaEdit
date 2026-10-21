package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.SignWax;
import com.komainu.moxia.region.TweakOperation;
import com.komainu.moxia.region.TweakRules;
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit retocar <modo> …} ({@code touchup}): los arreglos sobre lo que ya hay en la selección, en una sola
 * orden con un modo por cada uno. Cada modo es la orden de antes, con sus mismos argumentos y banderas:
 *
 * <ul>
 *     <li>{@code nieve} = {@code snow}, {@code drenar} = {@code drain}, {@code inundar} = {@code waterlog},
 *     {@code gravedad} = {@code gravity} y {@code bioma} = {@code biome};</li>
 *     <li>{@code desinfestar}, {@code hojas}, {@code anegar} y {@code encerar} = {@code tweak deinfest|persist|
 *     waterlog|wax} (con {@code on} u {@code off}).</li>
 * </ul>
 *
 * Las órdenes de antes siguen funcionando igual y avisan una vez de cómo se llaman ahora ({@link Renamed}). Con el
 * pincel, {@code /komaedit pincel retocar <radio> <modo> …} hace lo mismo alrededor de cada clic.
 */
public final class RetouchCommands {
    /** Los modos, con los nombres de las órdenes de antes como alias. */
    public enum Mode {
        SNOW("nieve", "snow", "nevar"),
        DRAIN("drenar", "drain", "secar"),
        FLOOD("inundar", "flood", "inundacion"),
        GRAVITY("gravedad", "gravity", "caer"),
        DEINFEST("desinfestar", "deinfest"),
        LEAVES("hojas", "persist", "persistir", "leaves"),
        WATERLOG("anegar", "waterlog", "agua"),
        WAX("encerar", "wax", "cera"),
        BIOME("bioma", "biome");

        private final String id;
        private final String[] aliases;

        Mode(String id, String... aliases) {
            this.id = id;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        /** El modo con ese nombre o alias (sin mayúsculas ni tildes), o {@code null}. */
        public static Mode find(String input) {
            if (input == null) {
                return null;
            }
            String name = Normalizer.normalize(input.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            for (Mode mode : values()) {
                if (mode.id.equals(name)) {
                    return mode;
                }
                for (String alias : mode.aliases) {
                    if (alias.equals(name)) {
                        return mode;
                    }
                }
            }
            return null;
        }

        /** El {@code tweak} de este modo, o {@code null} si no es uno. */
        public TweakRules.Tweak tweak() {
            return switch (this) {
                case DEINFEST -> TweakRules.Tweak.DEINFEST;
                case LEAVES -> TweakRules.Tweak.PERSIST;
                case WATERLOG -> TweakRules.Tweak.WATERLOG;
                case WAX -> TweakRules.Tweak.WAX;
                default -> null;
            };
        }
    }

    private static final SuggestionProvider<ServerCommandSource> MODES = (context, builder) -> {
        for (Mode mode : Mode.values()) {
            builder.suggest(mode.id());
        }
        return builder.buildFuture();
    };

    private RetouchCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : List.of("retocar", "touchup")) {
            root.addChild(command(name).build());
        }
    }

    /** Lo que se escribe tras el modo, de ejemplo. */
    static List<String> examples(Mode mode) {
        return switch (mode) {
            case SNOW -> List.of("1", "2", "3", "-r", "-f", "-s", "-h", "-p", "-m");
            case DRAIN -> List.of("agua", "lava", "todo", "-k", "-m");
            case FLOOD -> List.of("62", "~", "~-1", "-b", "-m");
            case GRAVITY -> List.of("-a", "-m");
            case DEINFEST -> List.of();
            case LEAVES, WATERLOG, WAX -> List.of("on", "off");
            case BIOME -> List.of("-v", "-m");
        };
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        SuggestionProvider<ServerCommandSource> options = (context, builder) -> {
            Mode mode = Mode.find(StringArgumentType.getString(context, "retocar_modo"));
            if (mode == Mode.BIOME) {
                return BuilderCommands.biomeSuggestions().getSuggestions(context, builder);
            }
            String remaining = builder.getRemaining();
            var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            if (mode != null) {
                examples(mode).forEach(tail::suggest);
            }
            return tail.buildFuture();
        };
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canAny(player, KomaPermissions.BUILD))
                .executes(RetouchCommands::help)
                .then(argument("retocar_modo", StringArgumentType.word())
                        .suggests(MODES)
                        .executes(context -> run(context, ""))
                        .then(argument("retocar_opciones", StringArgumentType.greedyString())
                                .suggests(options)
                                .executes(context -> run(context,
                                        StringArgumentType.getString(context, "retocar_opciones")))));
    }

    private static int help(CommandContext<ServerCommandSource> context) {
        List<String> names = new ArrayList<>();
        for (Mode mode : Mode.values()) {
            names.add(mode.id());
        }
        context.getSource().sendFeedback(() -> Feedback.prefix().append(Text.literal("Retocar la selección: /komaedit"
                + " retocar " + String.join("|", names) + " … (con el pincel: /komaedit pincel retocar <radio>"
                + " <modo> …)").formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int run(CommandContext<ServerCommandSource> context, String options)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String written = StringArgumentType.getString(context, "retocar_modo");
        Mode mode = Mode.find(written);
        try {
            if (mode == null) {
                throw new IllegalArgumentException("Modo desconocido: " + written + " (" + String.join(", ",
                        java.util.Arrays.stream(Mode.values()).map(Mode::id).toList()) + ")");
            }
            String message = apply(player, mode, options);
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

    /** Hace el retoque con lo escrito tras el modo; devuelve lo que hay que decir, si algo. */
    static String apply(ServerPlayerEntity player, Mode mode, String options) throws IncompleteRegionException {
        return switch (mode) {
            case SNOW -> BuilderCommands.snow(player, options);
            case DRAIN -> BuilderCommands.drain(player, options);
            case FLOOD -> BuilderCommands.flood(player, options);
            case GRAVITY -> BuilderCommands.gravity(player, options);
            case BIOME -> BuilderCommands.biome(player, options);
            default -> tweak(player, mode.tweak(), state(options));
        };
    }

    /** El {@code on}/{@code off} de un {@code tweak}: una palabra como mucho. */
    static String state(String options) {
        String text = options == null ? "" : options.strip();
        if (text.contains(" ")) {
            throw new IllegalArgumentException("Solo hace falta on u off");
        }
        return text.isEmpty() ? null : text;
    }

    /**
     * Un retoque de supervivencia sobre la selección ({@code deinfest}, {@code persist}, {@code waterlog},
     * {@code wax}); la cera de los carteles va en otra edición, también con //undo.
     */
    static String tweak(ServerPlayerEntity player, TweakRules.Tweak tweak, String state)
            throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "tweak." + tweak.id());
        boolean enable = TweakRules.enabled(state);
        if (tweak == TweakRules.Tweak.DEINFEST && !enable) {
            throw new IllegalArgumentException("Quitar la infestación no tiene modo off");
        }
        RegionOperationService.submit(player, 0, tweak.displayName() + (enable ? "" : " (off)"),
                new TweakOperation(tweak, enable));
        if (tweak == TweakRules.Tweak.WAX) {
            int signs = SignWax.apply(player, enable);
            if (signs > 0) {
                return signs + (signs == 1 ? " cartel " : " carteles ") + (enable ? "encerado" : "desencerado")
                        + (signs == 1 ? "" : "s") + " · //undo para deshacerlo";
            }
        }
        return null;
    }
}
