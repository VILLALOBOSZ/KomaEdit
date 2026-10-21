package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.notes.NoteColor;
import com.komainu.moxia.notes.NoteIcon;
import com.komainu.moxia.notes.NotePermissions;
import com.komainu.moxia.notes.NoteRequest;
import com.komainu.moxia.notes.NoteService;
import com.komainu.moxia.notes.WorldNote;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.views.ViewPermissions;
import com.komainu.moxia.views.ViewService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit nota} (alias {@code notas}, {@code note}, {@code notes}): notas en el mundo, ancladas al bloque
 * que miras o a donde estás, privadas o del equipo. El cliente con KomaEdit las dibuja y tiene su lista
 * ({@code /kegui notas}); sin el mod, esta orden lo hace todo por el chat.
 */
public final class NoteCommands {
    private static final int PAGE = 8;
    private static final int PREVIEW_CHARS = 48;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    /** Filtros de la lista. */
    private static final List<String> FILTERS = List.of("abiertas", "todas", "resueltas", "mias", "equipo");
    private static final List<String> FLAG_HINTS = List.of("-c", "-i", "-d", "-e", "-p");

    private NoteCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : new String[]{"nota", "notas", "note", "notes"}) {
            root.addChild(command(name).build());
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(source -> EzCommandSupport.canAny(source, "komaedit.notes"))
                .executes(context -> list(context, "abiertas", 1));
        for (String add : new String[]{"poner", "nueva", "add"}) {
            command.then(literal(add).then(argument("texto", StringArgumentType.greedyString())
                    .suggests(addSuggestions())
                    .executes(context -> EzCommandSupport.run(context, player -> {
                        WorldNote note = NoteService.add(player,
                                NoteRequest.parse(StringArgumentType.getString(context, "texto")));
                        EzCommandSupport.send(player, Text.literal((note.team() ? "Nota del equipo" : "Nota privada")
                                        + " puesta · ").formatted(Formatting.GREEN)
                                .append(Text.literal(note.id()).formatted(Formatting.WHITE)).append(" ")
                                .append(Feedback.button("[deshacer]", "/komaedit nota borrar " + note.id(),
                                        Formatting.RED, "Borrar la nota que acabas de poner")));
                        return 1;
                    }))));
        }
        for (String list : new String[]{"lista", "list"}) {
            command.then(literal(list).executes(context -> list(context, "abiertas", 1))
                    .then(argument("filtro", StringArgumentType.word()).suggests(EzCommandSupport.words(FILTERS))
                            .executes(context -> list(context, StringArgumentType.getString(context, "filtro"), 1))
                            .then(argument("pagina", IntegerArgumentType.integer(1))
                                    .executes(context -> list(context, StringArgumentType.getString(context, "filtro"),
                                            IntegerArgumentType.getInteger(context, "pagina"))))));
        }
        for (String go : new String[]{"ir", "go", "tp"}) {
            command.then(literal(go).requires(EzCommandSupport.node(ViewPermissions.TELEPORT))
                    .then(argument("id", StringArgumentType.word()).suggests(ids(note -> true))
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                String problem = NoteService.jump(player, StringArgumentType.getString(context, "id"));
                                if (problem != null) {
                                    return EzCommandSupport.fail(context.getSource(), problem);
                                }
                                EzCommandSupport.status(player, "Junto a la nota "
                                        + StringArgumentType.getString(context, "id"));
                                return 1;
                            }))));
        }
        for (String[] mode : new String[][]{{"resolver", "true"}, {"resolve", "true"}, {"reabrir", "false"},
                {"reopen", "false"}}) {
            boolean resolve = Boolean.parseBoolean(mode[1]);
            command.then(literal(mode[0]).then(argument("id", StringArgumentType.word())
                    .suggests(ids(note -> note.resolved() != resolve))
                    .executes(context -> EzCommandSupport.run(context, player -> {
                        WorldNote note = NoteService.resolve(player, StringArgumentType.getString(context, "id"),
                                resolve);
                        EzCommandSupport.ok(player, (resolve ? "Nota resuelta: " : "Nota reabierta: ")
                                + preview(note.text()));
                        return 1;
                    }))));
        }
        for (String delete : new String[]{"borrar", "delete"}) {
            command.then(literal(delete).then(argument("id", StringArgumentType.word()).suggests(ids(note -> true))
                    .executes(context -> EzCommandSupport.run(context, player -> {
                        WorldNote note = NoteService.remove(player, StringArgumentType.getString(context, "id"));
                        EzCommandSupport.ok(player, "Nota borrada: " + preview(note.text()));
                        return 1;
                    }))));
        }
        for (String clear : new String[]{"limpiar", "clear"}) {
            command.then(literal(clear).executes(context -> EzCommandSupport.run(context, player -> {
                int removed = NoteService.clearResolved(player).size();
                EzCommandSupport.ok(player, removed == 0 ? "No hay notas resueltas que borrar"
                        : Feedback.count(removed, "nota resuelta borrada", "notas resueltas borradas"));
                return 1;
            })));
        }
        for (String info : new String[]{"ver", "info"}) {
            command.then(literal(info).then(argument("id", StringArgumentType.word()).suggests(ids(note -> true))
                    .executes(context -> EzCommandSupport.run(context, player -> {
                        WorldNote note = NoteService.find(player, StringArgumentType.getString(context, "id"));
                        EzCommandSupport.send(player, line(player, note, true));
                        return 1;
                    }))));
        }
        return command;
    }

    private static int list(CommandContext<ServerCommandSource> context, String filter, int page)
            throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String key = filter.toLowerCase(Locale.ROOT).replace("í", "i");
            if (!FILTERS.contains(key)) {
                throw new IllegalArgumentException("Filtro desconocido: " + filter + " (usa " + String.join(", ",
                        FILTERS) + ")");
            }
            List<WorldNote> notes = new ArrayList<>();
            for (WorldNote note : NoteService.visible(player)) {
                boolean keep = switch (key) {
                    case "abiertas" -> !note.resolved();
                    case "resueltas" -> note.resolved();
                    case "mias" -> note.authorId().equals(player.getUuid());
                    case "equipo" -> note.team();
                    default -> true;
                };
                if (keep) {
                    notes.add(note);
                }
            }
            if (notes.isEmpty()) {
                EzCommandSupport.info(player, "No hay notas (" + key + ") · /komaedit nota poner <texto> pone una en"
                        + " el bloque que miras");
                return 1;
            }
            int pages = (notes.size() + PAGE - 1) / PAGE;
            int shown = Math.clamp(page, 1, pages);
            EzCommandSupport.send(player, Text.literal("Notas " + key + " (" + notes.size() + ")"
                    + (pages > 1 ? " · página " + shown + "/" + pages : "")).formatted(Formatting.GOLD));
            for (WorldNote note : notes.subList((shown - 1) * PAGE, Math.min(notes.size(), shown * PAGE))) {
                player.sendMessage(Text.literal("  ").append(line(player, note, false)), false);
            }
            if (shown < pages) {
                EzCommandSupport.send(player, Feedback.button("[siguiente página]", "/komaedit nota lista " + key + " "
                        + (shown + 1), Formatting.AQUA, "Página " + (shown + 1)));
            }
            return 1;
        });
    }

    /** Una nota en el chat: su color, el texto, autor, fecha, dónde está y los botones. */
    private static MutableText line(ServerPlayerEntity player, WorldNote note, boolean full) {
        MutableText line = Text.literal("■ ").styled(style -> style.withColor(TextColor.fromRgb(note.color())));
        line.append(Text.literal(full ? note.text() : preview(note.text()))
                .formatted(note.resolved() ? Formatting.GRAY : Formatting.WHITE));
        StringBuilder details = new StringBuilder(" · ").append(note.author()).append(" · ")
                .append(DATE.format(Instant.ofEpochMilli(note.created())));
        details.append(note.team() ? " · equipo" : " · privada");
        if (note.dimension().equals(ViewService.dimension(player.getWorld()))) {
            double dx = note.labelX() - player.getX();
            double dy = note.labelY() - player.getY();
            double dz = note.labelZ() - player.getZ();
            details.append(" · a ").append(Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))).append(" bloques");
        } else {
            details.append(" · ").append(ViewCommands.shortDimension(note.dimension()));
        }
        if (note.resolved()) {
            details.append(" · resuelta por ").append(note.resolvedBy());
        }
        line.append(Text.literal(details.toString()).formatted(Formatting.DARK_GRAY)).append(" ");
        if (KomaPermissions.can(player, ViewPermissions.TELEPORT)) {
            line.append(Feedback.button("[ir]", "/komaedit nota ir " + note.id(), Formatting.GREEN,
                    "Saltar junto a la nota")).append(" ");
        }
        line.append(note.resolved()
                ? Feedback.button("[reabrir]", "/komaedit nota reabrir " + note.id(), Formatting.YELLOW,
                "Volver a abrirla")
                : Feedback.button("[resolver]", "/komaedit nota resolver " + note.id(), Formatting.AQUA,
                "Marcarla como resuelta"));
        if (note.authorId().equals(player.getUuid()) || note.team()
                && KomaPermissions.can(player, NotePermissions.MANAGE)) {
            line.append(" ").append(Feedback.button("[borrar]", "/komaedit nota borrar " + note.id(), Formatting.RED,
                    "Borrar la nota"));
        }
        return line;
    }

    static String preview(String text) {
        return text.length() <= PREVIEW_CHARS ? text : text.substring(0, PREVIEW_CHARS - 1) + "…";
    }

    private static SuggestionProvider<ServerCommandSource> ids(Predicate<WorldNote> shown) {
        return (context, builder) -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null && NoteService.canUse(player)) {
                String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
                try {
                    for (WorldNote note : NoteService.visible(player)) {
                        if (shown.test(note) && note.id().startsWith(typed)) {
                            builder.suggest(note.id(), Text.literal(preview(note.text())));
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Las notas aún no están listas.
                }
            }
            return builder.buildFuture();
        };
    }

    /** Tras el texto, las banderas y sus valores. */
    private static SuggestionProvider<ServerCommandSource> addSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            int space = remaining.lastIndexOf(' ');
            if (space < 0) {
                return builder.buildFuture();
            }
            String previous = remaining.substring(0, space).strip();
            String last = previous.substring(previous.lastIndexOf(' ') + 1);
            List<String> options = switch (last) {
                case "-c", "--color" -> NoteColor.ids();
                case "-i", "--icono" -> NoteIcon.ids();
                case "-d", "--distancia" -> List.of("16", "32", "48", "96", "128");
                default -> FLAG_HINTS;
            };
            var tail = builder.createOffset(builder.getStart() + space + 1);
            String typed = remaining.substring(space + 1).toLowerCase(Locale.ROOT);
            for (String option : options) {
                if (option.startsWith(typed)) {
                    tail.suggest(option);
                }
            }
            return tail.buildFuture();
        };
    }
}
