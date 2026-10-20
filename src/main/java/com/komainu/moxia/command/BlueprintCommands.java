package com.komainu.moxia.command;

import com.komainu.moxia.blueprint.BlueprintLibrary;
import com.komainu.moxia.blueprint.BlueprintMeta;
import com.komainu.moxia.blueprint.BlueprintPermissions;
import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintScope;
import com.komainu.moxia.blueprint.BlueprintService;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.placement.PlacementService;
import com.komainu.moxia.placement.PlacementSettings;
import com.komainu.moxia.progress.ProgressLine;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit blueprint} (alias {@code planos}, {@code plano}): la biblioteca de planos, como los Blueprints de
 * Axiom. Guarda el portapapeles con miniatura y etiquetas, lo busca, lo carga, lo coloca con la colocación de
 * KomaEdit y lo comparte con el servidor. El pincel que los reparte es {@code /komaedit pincel stamp}.
 */
public final class BlueprintCommands {
    private static final int PAGE = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final List<String> PLACE_HINTS = List.of("Constant", "Random", "SurfaceNormal", "Aim",
            "-r", "Y", "-f", "XZ", "-a", "-v");

    private BlueprintCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : new String[]{"blueprint", "blueprints", "planos", "plano"}) {
            root.addChild(command(name).build());
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(source -> EzCommandSupport.canAny(source, "komaedit.blueprint")
                        || EzCommandSupport.can(source, BlueprintPermissions.PLACE))
                .executes(context -> list(context, ""));
        for (String save : new String[]{"save", "guardar"}) {
            command.then(literal(save).requires(EzCommandSupport.node(BlueprintPermissions.SAVE))
                    .then(argument("args", StringArgumentType.greedyString())
                            .suggests(tagSuggestions(1))
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                List<String> words = words(StringArgumentType.getString(context, "args"));
                                String tags = String.join(" ", words.subList(1, words.size()));
                                EzCommandSupport.info(player, BlueprintService.save(player, words.getFirst(), tags));
                                return 1;
                            }))));
        }
        for (String list : new String[]{"list", "lista", "search", "buscar"}) {
            command.then(literal(list).requires(EzCommandSupport.node(BlueprintPermissions.LOAD))
                    .executes(context -> list(context, ""))
                    .then(argument("filter", StringArgumentType.greedyString())
                            .suggests(tagSuggestions(0))
                            .executes(context -> list(context, StringArgumentType.getString(context, "filter")))));
        }
        for (String info : new String[]{"info", "ver"}) {
            command.then(literal(info).requires(EzCommandSupport.node(BlueprintPermissions.LOAD))
                    .then(argument("args", StringArgumentType.greedyString()).suggests(blueprintSuggestions(false))
                            .executes(context -> EzCommandSupport.run(context, player -> info(player,
                                    single(StringArgumentType.getString(context, "args")))))));
        }
        for (String load : new String[]{"load", "cargar"}) {
            command.then(literal(load).requires(EzCommandSupport.node(BlueprintPermissions.LOAD))
                    .then(argument("args", StringArgumentType.greedyString()).suggests(blueprintSuggestions(false))
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                EzCommandSupport.info(player, BlueprintService.load(player,
                                        single(StringArgumentType.getString(context, "args"))));
                                return 1;
                            }))));
        }
        for (String place : new String[]{"place", "colocar"}) {
            command.then(literal(place).requires(EzCommandSupport.node(BlueprintPermissions.PLACE))
                    .then(argument("args", StringArgumentType.greedyString()).suggests(placeSuggestions())
                            .executes(context -> EzCommandSupport.run(context, player ->
                                    place(context, player, StringArgumentType.getString(context, "args"))))));
        }
        for (String delete : new String[]{"delete", "borrar"}) {
            command.then(literal(delete).requires(EzCommandSupport.node(BlueprintPermissions.SAVE))
                    .then(argument("args", StringArgumentType.greedyString()).suggests(blueprintSuggestions(true))
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                EzCommandSupport.ok(player, BlueprintService.delete(player,
                                        single(StringArgumentType.getString(context, "args"))));
                                return 1;
                            }))));
        }
        for (String share : new String[]{"share", "compartir"}) {
            command.then(literal(share).requires(EzCommandSupport.node(BlueprintPermissions.SHARE))
                    .then(argument("args", StringArgumentType.greedyString()).suggests(blueprintSuggestions(true))
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                EzCommandSupport.ok(player, BlueprintService.share(player,
                                        single(StringArgumentType.getString(context, "args"))));
                                return 1;
                            }))));
        }
        for (String tags : new String[]{"tags", "etiquetas", "tag"}) {
            command.then(literal(tags).requires(EzCommandSupport.node(BlueprintPermissions.SAVE))
                    .then(argument("args", StringArgumentType.greedyString()).suggests(blueprintSuggestions(true))
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                List<String> words = words(StringArgumentType.getString(context, "args"));
                                EzCommandSupport.ok(player, BlueprintService.retag(player, words.getFirst(),
                                        String.join(" ", words.subList(1, words.size()))));
                                return 1;
                            }))));
        }
        return command;
    }

    // ------------------------------------------------------------------ Órdenes

    private static int list(CommandContext<ServerCommandSource> context, String input) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            KomaPermissions.require(player, BlueprintPermissions.LOAD);
            List<String> words = words(input);
            int page = 1;
            if (!words.isEmpty() && words.getLast().matches("\\d{1,4}")) {
                page = Math.max(1, Integer.parseInt(words.removeLast()));
            }
            String query = String.join(" ", words);
            List<BlueprintLibrary.Entry> found = BlueprintService.library().search(player.getUuid(), query);
            if (found.isEmpty()) {
                EzCommandSupport.send(player, Text.literal(query.isBlank()
                        ? "No hay planos todavía: copia algo con //copy y guárdalo con /komaedit blueprint save <nombre>"
                        : "Ningún plano casa con «" + query + "»").formatted(Formatting.YELLOW));
                return 0;
            }
            int pages = (found.size() + PAGE - 1) / PAGE;
            page = Math.min(page, pages);
            EzCommandSupport.send(player, Text.literal("Planos" + (query.isBlank() ? "" : " · «" + query + "»")
                    + " · " + found.size() + " · página " + page + "/" + pages).formatted(Formatting.YELLOW));
            boolean place = KomaPermissions.can(player, BlueprintPermissions.PLACE);
            boolean stamp = KomaPermissions.can(player, BlueprintPermissions.STAMP);
            for (BlueprintLibrary.Entry entry : found.subList((page - 1) * PAGE, Math.min(found.size(), page * PAGE))) {
                player.sendMessage(line(player, entry, place, stamp), false);
            }
            if (page < pages) {
                String next = "/komaedit blueprint list " + (query.isBlank() ? "" : query + " ") + (page + 1);
                EzCommandSupport.send(player, EzCommandSupport.button("[siguiente página]", next, Formatting.AQUA));
            }
            return found.size();
        });
    }

    private static MutableText line(ServerPlayerEntity player, BlueprintLibrary.Entry entry, boolean place,
                                    boolean stamp) {
        BlueprintMeta meta = entry.meta();
        String reference = meta.reference();
        MutableText hover = Text.literal(reference).formatted(Formatting.WHITE)
                .append(Text.literal("\n" + meta.describeSize() + " · " + ProgressLine.group(meta.blocks()) + " bloques"
                        + "\nde " + meta.author() + " · " + DATE.format(Instant.ofEpochMilli(meta.created()))
                        + (meta.tags().isEmpty() ? "" : "\n#" + String.join(" #", meta.tags()))).formatted(Formatting.GRAY));
        MutableText line = Text.literal(meta.scope() == BlueprintScope.SHARED ? " ★ " : " • ")
                .formatted(meta.scope() == BlueprintScope.SHARED ? Formatting.GOLD : Formatting.DARK_GRAY)
                .append(Text.literal(reference).styled(style -> style.withColor(Formatting.WHITE)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/komaedit blueprint info " + reference))))
                .append(Text.literal(" " + meta.describeSize()).formatted(Formatting.DARK_GRAY));
        if (!meta.tags().isEmpty()) {
            line.append(Text.literal(" #" + String.join(" #", meta.tags())).formatted(Formatting.DARK_AQUA));
        }
        line.append(Text.literal(" "));
        line.append(EzCommandSupport.button("[cargar]", "/komaedit blueprint load " + reference, Formatting.GREEN));
        if (place) {
            line.append(Text.literal(" ")).append(suggest("[colocar]",
                    "/komaedit blueprint place " + reference + " -v", Formatting.AQUA));
        }
        if (stamp) {
            line.append(Text.literal(" ")).append(suggest("[estampar]", "/komaedit pincel stamp " + reference,
                    Formatting.LIGHT_PURPLE));
        }
        boolean mine = meta.owner().equals(player.getUuid());
        if (mine || KomaPermissions.can(player, BlueprintPermissions.MANAGE)) {
            line.append(Text.literal(" ")).append(suggest("[borrar]", "/komaedit blueprint delete " + reference,
                    Formatting.RED));
        }
        return line;
    }

    private static int info(ServerPlayerEntity player, String reference) {
        BlueprintLibrary.Entry entry = BlueprintService.library().require(player.getUuid(),
                BlueprintRef.parse(reference));
        BlueprintMeta meta = entry.meta();
        EzCommandSupport.send(player, Text.literal(meta.reference()).formatted(Formatting.YELLOW)
                .append(Text.literal(meta.scope() == BlueprintScope.SHARED ? " · compartido" : " · tuyo")
                        .formatted(Formatting.GRAY)));
        player.sendMessage(Text.literal("  Tamaño " + meta.describeSize() + " · " + ProgressLine.group(meta.blocks()) + " bloques · "
                + (entry.schematic().toFile().length() / 1024 + 1) + " KiB").formatted(Formatting.GRAY), false);
        String updated = meta.updated() > meta.created()
                ? " · cambiado " + DATE.format(Instant.ofEpochMilli(meta.updated())) : "";
        player.sendMessage(Text.literal("  De " + meta.author() + " · "
                + DATE.format(Instant.ofEpochMilli(meta.created())) + updated
                + (meta.sharedBy().isEmpty() ? "" : " · compartido por " + meta.sharedBy()))
                .formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  Etiquetas: " + (meta.tags().isEmpty() ? "ninguna"
                : "#" + String.join(" #", meta.tags()))).formatted(Formatting.GRAY), false);
        player.sendMessage(line(player, entry, KomaPermissions.can(player, BlueprintPermissions.PLACE),
                KomaPermissions.can(player, BlueprintPermissions.STAMP)), false);
        return 1;
    }

    /**
     * Lo mismo que {@code /komaedit place Bp(N:<plano>) …}: alineación, banderas y la vista previa {@code -v} de
     * KomaEdit.
     */
    private static int place(CommandContext<ServerCommandSource> context, ServerPlayerEntity player, String input)
            throws IncompleteRegionException {
        List<String> words = words(input);
        BlueprintRef ref = BlueprintRef.parse(words.getFirst());
        String rest = String.join(" ", words.subList(1, words.size()));
        String structure = "Bp(N:" + (ref.sharedOnly() ? BlueprintRef.SHARED_PREFIX : "") + ref.name() + ")";
        String started = PlacementService.start(player, PlacementSettings.Kind.PLACE,
                rest.isBlank() ? structure : structure + " " + rest);
        context.getSource().sendFeedback(() -> EzCommandSupport.prefix()
                .append(Text.literal(started).formatted(Formatting.GRAY)), false);
        return 1;
    }

    // ------------------------------------------------------------------ Sugerencias y utilidades

    /** Los nombres de plano que ve el jugador, como se escriben en las órdenes. */
    public static List<String> references(ServerPlayerEntity player) {
        try {
            return BlueprintService.library().visible(player.getUuid()).stream()
                    .map(BlueprintLibrary.Entry::reference).toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** Las etiquetas en uso entre los planos que ve el jugador, con su {@code #}. */
    public static List<String> tags(ServerPlayerEntity player) {
        Set<String> tags = new LinkedHashSet<>();
        try {
            for (BlueprintLibrary.Entry entry : BlueprintService.library().visible(player.getUuid())) {
                entry.meta().tags().forEach(tag -> tags.add("#" + tag));
            }
        } catch (RuntimeException ignored) {
            // Sin biblioteca legible no hay nada que sugerir.
        }
        return List.copyOf(tags);
    }

    private static SuggestionProvider<ServerCommandSource> blueprintSuggestions(boolean ownOnly) {
        return (context, builder) -> {
            if (!builder.getRemaining().contains(" ")
                    && context.getSource().getEntity() instanceof ServerPlayerEntity player) {
                String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
                for (String reference : references(player)) {
                    boolean shared = reference.startsWith(BlueprintRef.SHARED_PREFIX);
                    if ((!ownOnly || !shared || KomaPermissions.can(player, BlueprintPermissions.MANAGE))
                            && reference.startsWith(typed)) {
                        builder.suggest(reference);
                    }
                }
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<ServerCommandSource> placeSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            if (!remaining.contains(" ")) {
                return blueprintSuggestions(false).getSuggestions(context, builder);
            }
            var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            String typed = remaining.substring(remaining.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
            for (String hint : PLACE_HINTS) {
                if (hint.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    tail.suggest(hint);
                }
            }
            return tail.buildFuture();
        };
    }

    /** Etiquetas en uso a partir de la palabra {@code from} (la 0 en list, la 1 en save, tras el nombre). */
    private static SuggestionProvider<ServerCommandSource> tagSuggestions(int from) {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            int words = remaining.isBlank() ? 0 : remaining.split(" ", -1).length - 1;
            if (words < from || !(context.getSource().getEntity() instanceof ServerPlayerEntity player)) {
                return builder.buildFuture();
            }
            var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            String typed = remaining.substring(remaining.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
            for (String tag : tags(player)) {
                String shown = from == 0 ? tag : tag.substring(1);
                if (shown.startsWith(typed)) {
                    tail.suggest(shown);
                }
            }
            return tail.buildFuture();
        };
    }

    private static MutableText suggest(String label, String command, Formatting color) {
        return Text.literal(label).styled(style -> style.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command))));
    }

    private static List<String> words(String input) {
        List<String> words = new ArrayList<>();
        for (String word : input.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        if (words.isEmpty() && !input.isBlank()) {
            words.add(input.trim());
        }
        return words;
    }

    private static String single(String input) {
        List<String> words = words(input);
        if (words.size() != 1) {
            throw new IllegalArgumentException(words.isEmpty() ? "Falta el nombre del plano"
                    : "Escribe un solo plano; sobra «" + words.get(1) + "»");
        }
        return words.getFirst();
    }
}
