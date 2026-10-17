package com.komainu.moxia.command;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.tube.TubeFormulaPreset;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** {@code /komaedit help [tema]}: índice de temas y comandos clicables que se escriben solos en el chat. */
public final class HelpCommands {
    private static final SuggestionProvider<ServerCommandSource> TOPIC_SUGGESTIONS = (context, builder) -> {
        for (HelpTopics.Topic topic : HelpTopics.TOPICS) {
            builder.suggest(topic.id(), Text.literal(topic.title()));
        }
        return builder.buildFuture();
    };

    private HelpCommands() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .then(argument("topic", StringArgumentType.word())
                        .suggests(TOPIC_SUGGESTIONS)
                        .executes(context -> show(context, StringArgumentType.getString(context, "topic"))))
                .executes(HelpCommands::index);
    }

    private static int index(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> prefix().append(Text.literal("Ayuda · clic en un tema").formatted(Formatting.AQUA)), false);
        MutableText topics = Text.literal("  ");
        for (int index = 0; index < HelpTopics.TOPICS.size(); index++) {
            HelpTopics.Topic topic = HelpTopics.TOPICS.get(index);
            if (index > 0) {
                topics.append(Text.literal("  "));
            }
            topics.append(PathCommands.button("[" + topic.title() + "]", "/komaedit help " + topic.id(),
                    Formatting.WHITE, "Comandos de " + topic.title().toLowerCase(java.util.Locale.ROOT), true));
        }
        source.sendFeedback(() -> topics, false);
        source.sendFeedback(() -> Text.literal("  Para empezar: marca puntos con P, elige figura con K y pulsa Build. "
                + "Por comando: ").formatted(Formatting.GRAY)
                .append(PathCommands.button("/komaedit build tiles stone 60 5 2 0", "/komaedit build tiles stone 60 5 2 0",
                        Formatting.YELLOW, "Ejemplo de figura", false)), false);
        source.sendFeedback(() -> Text.literal("  Alias: /ke, /mx, /moxia · ruta = path · ayuda = help · estimar = estimate · "
                + "historial de bloques: //undo y //redo").formatted(Formatting.DARK_GRAY), false);
        return 1;
    }

    static int show(CommandContext<ServerCommandSource> context, String requested) {
        ServerCommandSource source = context.getSource();
        HelpTopics.Topic topic = HelpTopics.find(requested).orElse(null);
        if (topic == null) {
            String message = "Tema de ayuda desconocido: " + requested
                    + HelpTopics.closest(requested).map(found -> " · ¿Quisiste decir «" + found + "»?").orElse("")
                    + " · temas: " + String.join(", ", HelpTopics.TOPICS.stream().map(HelpTopics.Topic::id).toList());
            source.sendError(prefix().append(Text.literal(message).formatted(Formatting.RED)));
            return 0;
        }
        source.sendFeedback(() -> prefix().append(Text.literal(topic.title() + " · clic para escribir el comando")
                .formatted(Formatting.AQUA)), false);
        for (HelpTopics.Entry entry : topic.entries()) {
            source.sendFeedback(() -> Text.literal("  ")
                    .append(PathCommands.button(entry.syntax(), entry.suggestion(), Formatting.YELLOW,
                            entry.description(), false))
                    .append(Text.literal("  " + entry.description()).formatted(Formatting.GRAY)), false);
        }
        notes(source, topic.id());
        return topic.entries().size();
    }

    private static void notes(ServerCommandSource source, String topic) {
        switch (topic) {
            case "build" -> {
                MutableText figures = Text.literal("  Figuras: ").formatted(Formatting.GRAY);
                boolean first = true;
                for (PathShape shape : PathShape.values()) {
                    if (!first) {
                        figures.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                    }
                    first = false;
                    figures.append(PathCommands.button(shape.id(), "/komaedit build " + shape.id() + " ",
                            Formatting.WHITE, shape.displayName() + " · " + shape.description(), false));
                }
                source.sendFeedback(() -> figures, false);
                source.sendFeedback(() -> Text.literal("  Motor exacto (sin huecos) para chainlink, rings, beads, "
                        + "oscillate, fishnet, honeycomb, knots y coil con engine:exact.").formatted(Formatting.GRAY), false);
            }
            case "tubes" -> {
                source.sendFeedback(() -> Text.literal("  Banderas: -e round|spike|soft|cube · -n horizontal|upright · "
                        + "-t torsión · -r giro,giroFinal · -p tensión:sesgo:continuidad · -hollow pared · "
                        + "-w slabs|stairs · -c cobertura 0–1 · -q fast|balanced|high. Radios con fotogramas: "
                        + "\"2,0.3:12,2\" o 2_12_2.").formatted(Formatting.GRAY), false);
                source.sendFeedback(() -> Text.literal("  Fórmula: x e y de -1 a 1 en la sección, z a lo largo, t de 0 a 1, "
                        + "r radio, a ángulo, n repeticiones; > 0 coloca bloque. De serie: " + TubeFormulaPreset.ids()
                        + ".").formatted(Formatting.GRAY), false);
            }
            case "generate" -> source.sendFeedback(() -> Text.literal("  Arco y catenaria usan el primer y el último "
                    + "punto (o //pos1 y //pos2 si no hay recorrido). Máximo 32 puntos.").formatted(Formatting.GRAY), false);
            case "saved" -> source.sendFeedback(() -> Text.literal("  Se guardan por mundo y jugador en "
                    + "komaedit/players/<uuid>/paths. Nombres: letras sin tilde, números, _ y -.")
                    .formatted(Formatting.GRAY), false);
            default -> {
            }
        }
    }

    private static MutableText prefix() {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD);
    }
}
