package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.terrain.OwnStamp;
import com.komainu.moxia.terrain.OwnStamps;
import com.komainu.moxia.terrain.StampService;
import com.komainu.moxia.terrain.TerrainService;
import com.komainu.moxia.terrain.stamp.StampCategory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.IncompleteRegionException;
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

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Sellos propios: {@code /komaedit sello capturar [nombre] [descripción…] [-w] [-c categoría] [-r]} guarda la
 * selección como sello; {@code borrar}, {@code describir} y {@code lista}. Los usa el pincel de relieve por su nombre
 * y salen en la galería del Studio, en «Míos».
 */
public final class StampCommands {
    private static final List<String> ACTIONS = List.of("capturar", "lista", "describir", "borrar");
    private static final Map<String, String> ALIASES = Map.ofEntries(Map.entry("capture", "capturar"),
            Map.entry("guardar", "capturar"), Map.entry("save", "capturar"), Map.entry("list", "lista"),
            Map.entry("describe", "describir"), Map.entry("descripcion", "describir"),
            Map.entry("delete", "borrar"), Map.entry("remove", "borrar"), Map.entry("quitar", "borrar"));
    private static final SuggestionProvider<ServerCommandSource> ACTION_SUGGESTIONS = (context, builder) -> {
        ACTIONS.forEach(builder::suggest);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> OPTION_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        String action = canonical(StringArgumentType.getString(context, "sello_accion"));
        String last = remaining.stripTrailing();
        last = last.contains(" ") ? last.substring(last.lastIndexOf(' ') + 1) : last;
        if (action.equals("capturar")) {
            if (remaining.endsWith(" ") && last.equals("-c")) {
                for (StampCategory category : StampCategory.values()) {
                    tail.suggest(category.id());
                }
            } else {
                List.of("-w", "-c", "-r").forEach(tail::suggest);
            }
        } else if ((action.equals("borrar") || action.equals("describir")) && !remaining.strip().contains(" ")) {
            for (OwnStamps.Entry entry : TerrainService.ownStamps().list()) {
                tail.suggest(entry.name());
            }
        }
        return tail.buildFuture();
    };

    private StampCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("sello").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && (KomaPermissions.can(player, StampService.CAPTURE)
                        || KomaPermissions.can(player, StampService.USE)))
                .executes(context -> run(context, "lista", ""))
                .then(argument("sello_accion", StringArgumentType.word())
                        .suggests(ACTION_SUGGESTIONS)
                        .executes(context -> run(context, StringArgumentType.getString(context, "sello_accion"), ""))
                        .then(argument("sello_opciones", StringArgumentType.greedyString())
                                .suggests(OPTION_SUGGESTIONS)
                                .executes(context -> run(context,
                                        StringArgumentType.getString(context, "sello_accion"),
                                        StringArgumentType.getString(context, "sello_opciones")))));
    }

    static String canonical(String action) {
        String lower = action.toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(lower, lower);
    }

    private static int run(CommandContext<ServerCommandSource> context, String action, String options)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            switch (canonical(action)) {
                case "capturar" -> {
                    Capture capture = Capture.parse(options);
                    StampService.capture(player, capture.name(), capture.description(), capture.keepWater(),
                            capture.category(), capture.replace());
                }
                case "lista" -> list(context);
                case "borrar" -> {
                    String name = options.strip();
                    if (name.isEmpty() || name.contains(" ")) {
                        throw new IllegalArgumentException("sello borrar <nombre>");
                    }
                    StampService.delete(player, name);
                }
                case "describir" -> {
                    String text = options.strip();
                    int space = text.indexOf(' ');
                    if (text.isEmpty()) {
                        throw new IllegalArgumentException("sello describir <nombre> <texto>");
                    }
                    StampService.describe(player, space < 0 ? text : text.substring(0, space),
                            space < 0 ? "" : text.substring(space + 1));
                }
                default -> throw new IllegalArgumentException("Acción desconocida: " + action + " ("
                        + String.join(", ", ACTIONS) + ")");
            }
            return 1;
        } catch (IncompleteRegionException exception) {
            return EzCommandSupport.fail(context.getSource(), "Selecciona primero el trozo de terreno con //pos1 y"
                    + " //pos2 (o la caja del Studio)");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }

    /**
     * Lo escrito tras {@code capturar}: el nombre (si lo hay), la descripción con sus mayúsculas y las banderas.
     *
     * @param keepWater {@code -w}: el agua cuenta como suelo
     * @param category  {@code -c}: la categoría (miniatura y paleta), o {@code null} para elegirla sola
     * @param replace   {@code -r}: sustituye uno propio con ese nombre
     */
    record Capture(String name, String description, boolean keepWater, StampCategory category, boolean replace) {
        static Capture parse(String text) {
            String[] words = text.strip().isEmpty() ? new String[0] : text.strip().split("\\s+");
            String name = null;
            List<String> description = new ArrayList<>();
            boolean keepWater = false;
            boolean replace = false;
            StampCategory category = null;
            for (int index = 0; index < words.length; index++) {
                String word = words[index];
                switch (word.toLowerCase(Locale.ROOT)) {
                    case "-w" -> keepWater = true;
                    case "-r" -> replace = true;
                    case "-c" -> {
                        if (index + 1 >= words.length) {
                            throw new IllegalArgumentException("Falta la categoría tras -c (montanas, dunas…)");
                        }
                        category = StampCategory.byId(words[++index]);
                    }
                    default -> {
                        if (name == null && description.isEmpty()) {
                            name = word;
                        } else {
                            description.add(word);
                        }
                    }
                }
            }
            String joined = String.join(" ", description);
            if (joined.length() > OwnStamp.MAX_DESCRIPTION) {
                throw new IllegalArgumentException("La descripción pasa de " + OwnStamp.MAX_DESCRIPTION
                        + " caracteres");
            }
            return new Capture(name, joined, keepWater, category, replace);
        }
    }

    private static void list(CommandContext<ServerCommandSource> context) {
        List<OwnStamps.Entry> entries = TerrainService.ownStamps().list();
        if (entries.isEmpty()) {
            feedback(context, Text.literal("Aún no hay sellos propios. Selecciona un trozo de terreno y escribe"
                    + " /komaedit sello capturar <nombre>, o deja un PNG en " + TerrainService.catalog().directory())
                    .formatted(Formatting.GRAY));
            return;
        }
        feedback(context, Text.literal(entries.size() + " sellos propios (pulsa uno para ponerlo en el pincel):")
                .formatted(Formatting.GRAY));
        MutableText line = Text.empty();
        for (OwnStamps.Entry entry : entries) {
            OwnStamp meta = entry.meta();
            String hover = entry.name() + (meta.description().isEmpty() ? "" : "\n" + meta.description())
                    + (meta.author().isEmpty() ? "\nPNG del servidor" : "\nde " + meta.author())
                    + "\n" + meta.sizeX() + " × " + meta.sizeZ() + " columnas, " + Math.round(meta.above()
                    + meta.below()) + " de alto";
            line.append(Text.literal(entry.name()).styled(style -> style.withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/komaedit pincel heightmap " + entry.name() + " 16"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover)))))
                    .append(Text.literal(" "));
        }
        feedback(context, line);
    }

    private static void feedback(CommandContext<ServerCommandSource> context, Text text) {
        context.getSource().sendFeedback(() -> Feedback.prefix().append(text), false);
    }
}
