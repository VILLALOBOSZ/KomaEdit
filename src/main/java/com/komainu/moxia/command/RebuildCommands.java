package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.rebuild.RebuildAxis;
import com.komainu.moxia.rebuild.RebuildOrder;
import com.komainu.moxia.rebuild.RebuildService;
import com.komainu.moxia.rebuild.RebuildSettings;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit rebuild} (alias {@code reconstruir}): la reconstrucción animada, como la «Animated Rebuild» de
 * Axiom. Quita la selección (o pone el portapapeles o un plano) y la levanta bloque a bloque a la vista de todos, con
 * el orden y el ritmo que se pidan; pausar, seguir, acelerar, saltar al final y cancelar.
 */
final class RebuildCommands {
    private static final List<String> SOURCES = List.of("portapapeles", "plano");
    private static final List<String> CONTROLS = List.of("pausar", "seguir", "velocidad", "acelerar", "frenar",
            "terminar", "cancelar", "estado");

    private RebuildCommands() {
    }

    static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : new String[]{"rebuild", "reconstruir"}) {
            root.addChild(command(name).build());
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(EzCommandSupport.node(KomaPermissions.REBUILD))
                .executes(context -> start(context, RebuildService.Source.SELECTION, null, ""))
                .then(argument("args", StringArgumentType.greedyString()).suggests(settingsSuggestions(true))
                        .executes(context -> start(context, RebuildService.Source.SELECTION, null,
                                StringArgumentType.getString(context, "args"))));
        for (String clipboard : new String[]{"portapapeles", "clipboard"}) {
            command.then(literal(clipboard)
                    .executes(context -> start(context, RebuildService.Source.CLIPBOARD, null, ""))
                    .then(argument("args", StringArgumentType.greedyString()).suggests(settingsSuggestions(false))
                            .executes(context -> start(context, RebuildService.Source.CLIPBOARD, null,
                                    StringArgumentType.getString(context, "args")))));
        }
        for (String blueprint : new String[]{"plano", "blueprint"}) {
            command.then(literal(blueprint)
                    .then(argument("args", StringArgumentType.greedyString()).suggests(blueprintSuggestions())
                            .executes(context -> {
                                String input = StringArgumentType.getString(context, "args").strip();
                                int space = input.indexOf(' ');
                                String reference = space < 0 ? input : input.substring(0, space);
                                String rest = space < 0 ? "" : input.substring(space + 1);
                                return start(context, RebuildService.Source.BLUEPRINT, reference, rest);
                            })));
        }
        for (String pause : new String[]{"pausar", "pause"}) {
            command.then(literal(pause).executes(context -> control(context,
                    player -> RebuildService.pause(player.getUuid(), true))));
        }
        for (String resume : new String[]{"seguir", "reanudar", "resume"}) {
            command.then(literal(resume).executes(context -> control(context,
                    player -> RebuildService.pause(player.getUuid(), false))));
        }
        for (String speed : new String[]{"velocidad", "speed"}) {
            command.then(literal(speed).then(argument("factor", StringArgumentType.word())
                    .suggests(EzCommandSupport.words(List.of("0.25", "0.5", "1", "2", "4", "8")))
                    .executes(context -> control(context, player -> RebuildService.speed(player.getUuid(),
                            factor(StringArgumentType.getString(context, "factor")), false)))));
        }
        for (String faster : new String[]{"acelerar", "faster"}) {
            command.then(literal(faster).executes(context -> control(context,
                    player -> RebuildService.speed(player.getUuid(), 2.0, true))));
        }
        for (String slower : new String[]{"frenar", "slower"}) {
            command.then(literal(slower).executes(context -> control(context,
                    player -> RebuildService.speed(player.getUuid(), 0.5, true))));
        }
        for (String finish : new String[]{"terminar", "final", "finish", "skip"}) {
            command.then(literal(finish).executes(context -> control(context,
                    player -> RebuildService.finish(player.getUuid()))));
        }
        for (String cancel : new String[]{"cancelar", "cancel"}) {
            command.then(literal(cancel).executes(context -> EzCommandSupport.run(context, player -> {
                String stopped = RebuildService.cancel(player.getUuid()).orElseThrow(() ->
                        new IllegalStateException("No tienes ninguna reconstrucción en marcha"));
                Feedback.warn(player, "Cancelada: " + stopped);
                return 1;
            })));
        }
        for (String status : new String[]{"estado", "status"}) {
            command.then(literal(status).executes(context -> EzCommandSupport.run(context, player -> {
                for (Text line : RebuildService.status(player.getUuid())) {
                    EzCommandSupport.send(player, line);
                }
                return 1;
            })));
        }
        return command;
    }

    private static int start(CommandContext<ServerCommandSource> context, RebuildService.Source source,
                             String blueprint, String arguments) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            EzCommandSupport.status(player, RebuildService.start(player, source, blueprint, arguments));
            return 1;
        });
    }

    @FunctionalInterface
    private interface Control {
        String apply(ServerPlayerEntity player);
    }

    private static int control(CommandContext<ServerCommandSource> context, Control control)
            throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            Feedback.ok(player, control.apply(player));
            return 1;
        });
    }

    /** {@code 2}, {@code 0.5}, {@code 0,5}, {@code x2}, {@code ×2}, {@code 1/4}. */
    static double factor(String text) {
        String value = text.strip().toLowerCase(Locale.ROOT).replace('×', 'x');
        if (value.startsWith("x")) {
            value = value.substring(1);
        }
        try {
            int slash = value.indexOf('/');
            double factor = slash < 0 ? Double.parseDouble(value.replace(',', '.'))
                    : Double.parseDouble(value.substring(0, slash)) / Double.parseDouble(value.substring(slash + 1));
            if (!Double.isFinite(factor) || factor <= 0.0) {
                throw new NumberFormatException();
            }
            return factor;
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException("La velocidad es un número: 2 es el doble, 0.5 la mitad");
        }
    }

    /** El orden (primera palabra), las banderas y algunos valores de ejemplo tras cada bandera. */
    private static SuggestionProvider<ServerCommandSource> settingsSuggestions(boolean selection) {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            int space = remaining.lastIndexOf(' ');
            var tail = builder.createOffset(builder.getStart() + space + 1);
            String typed = remaining.substring(space + 1).toLowerCase(Locale.ROOT);
            String previous = space < 0 ? "" : lastWord(remaining.substring(0, space));
            List<String> options = new ArrayList<>();
            switch (previous) {
                case "-b" -> options.addAll(List.of("1", "2", "4", "8", "0.5"));
                case "-t" -> options.addAll(List.of("10", "30", "60", "120"));
                case "-p", "-i", "-d", "-w" -> options.addAll(List.of("0.5", "1", "2", "10t"));
                case "-e" -> {
                    for (RebuildAxis axis : RebuildAxis.values()) {
                        options.add(axis.id());
                    }
                }
                case "-c", "-l" -> options.add("~,~,~");
                case "-s" -> options.add("42");
                default -> {
                    if (space < 0) {
                        options.addAll(RebuildOrder.names());
                        if (selection) {
                            options.addAll(SOURCES);
                            options.addAll(CONTROLS);
                        }
                    }
                    options.addAll(RebuildSettings.FLAGS);
                }
            }
            for (String option : options) {
                if (option.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    tail.suggest(option);
                }
            }
            return tail.buildFuture();
        };
    }

    private static SuggestionProvider<ServerCommandSource> blueprintSuggestions() {
        SuggestionProvider<ServerCommandSource> settings = settingsSuggestions(false);
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            if (!remaining.contains(" ") && context.getSource().getEntity() instanceof ServerPlayerEntity player) {
                String typed = remaining.toLowerCase(Locale.ROOT);
                for (String reference : BlueprintCommands.references(player)) {
                    if (reference.startsWith(typed)) {
                        builder.suggest(reference);
                    }
                }
                return builder.buildFuture();
            }
            int space = remaining.indexOf(' ');
            return settings.getSuggestions(context, builder.createOffset(builder.getStart() + space + 1));
        };
    }

    private static String lastWord(String text) {
        String stripped = text.strip();
        int space = stripped.lastIndexOf(' ');
        return (space < 0 ? stripped : stripped.substring(space + 1)).toLowerCase(Locale.ROOT);
    }
}
