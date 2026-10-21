package com.komainu.moxia.command;

import com.komainu.moxia.brush.paint.PaintPalettes;
import com.komainu.moxia.capability.BlockStates;
import com.komainu.moxia.color.BlockColor;
import com.komainu.moxia.color.BlockColorCatalog;
import com.komainu.moxia.color.BlockColorCatalogs;
import com.komainu.moxia.color.ColorFilter;
import com.komainu.moxia.color.ColorGradient;
import com.komainu.moxia.gradient.BlockSpec;
import com.komainu.moxia.gradient.PaletteCodec;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.hotbar.HotbarBook;
import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.hotbar.HotbarCodec;
import com.komainu.moxia.hotbar.HotbarItem;
import com.komainu.moxia.hotbar.HotbarRules;
import com.komainu.moxia.hotbar.HotbarService;
import com.komainu.moxia.hotbar.HotbarSources;
import com.komainu.moxia.hotbar.SavedHotbar;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.terrain.SurfacePalette;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit hotbar}: barras rápidas guardadas, como el Hotbar Swapper de Axiom. Guardar la barra actual,
 * cambiar a otra, exportarlas e importarlas como texto y crearlas desde una paleta, un degradado de color, los
 * bloques más usados de la selección o la familia de un material. La lógica está en {@link HotbarService}; aquí solo
 * se leen los argumentos y se contesta.
 */
public final class HotbarCommands {
    static final String NODE = HotbarRules.PERMISSION;
    private static final int GRADIENT_STEPS = SavedHotbar.SLOTS;

    private HotbarCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(hotbar("hotbar").build());
        root.addChild(hotbar("barra").build());
    }

    private interface Action {
        int run(ServerPlayerEntity player, CommandTokens tokens, CommandContext<ServerCommandSource> context)
                throws Exception;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> hotbar(String name) {
        LiteralArgumentBuilder<ServerCommandSource> node = literal(name)
                .requires(EzCommandSupport.node(NODE))
                .executes(context -> EzCommandSupport.run(context, HotbarCommands::list));
        sub(node, HotbarCommands::save, Set.of("i"), "save", "guardar");
        sub(node, HotbarCommands::load, Set.of(), "load", "usar", "cargar");
        node.then(literal("list").executes(context -> EzCommandSupport.run(context, HotbarCommands::list)));
        node.then(literal("lista").executes(context -> EzCommandSupport.run(context, HotbarCommands::list)));
        sub(node, HotbarCommands::delete, Set.of(), "delete", "borrar");
        sub(node, HotbarCommands::rename, Set.of(), "rename", "renombrar");
        sub(node, HotbarCommands::icon, Set.of(), "icon", "icono");
        sub(node, HotbarCommands::move, Set.of(), "move", "mover");
        for (String undo : List.of("undo", "deshacer")) {
            node.then(literal(undo).executes(context -> EzCommandSupport.run(context, player -> {
                HotbarService.undo(player);
                EzCommandSupport.ok(player, "Barra de antes recuperada");
                return 1;
            })));
        }
        for (String autosave : List.of("autosave", "autoguardado")) {
            node.then(literal(autosave)
                    .executes(context -> EzCommandSupport.run(context, player -> autosave(player, null)))
                    .then(argument("args", StringArgumentType.greedyString())
                            .suggests(EzCommandSupport.words(List.of("on", "off")))
                            .executes(context -> EzCommandSupport.run(context, player ->
                                    autosave(player, StringArgumentType.getString(context, "args"))))));
        }
        for (String export : List.of("export", "exportar")) {
            node.then(literal(export)
                    .executes(context -> EzCommandSupport.run(context, player -> export(player, "")))
                    .then(argument("args", StringArgumentType.greedyString())
                            .suggests((context, builder) -> names(context, builder, true))
                            .executes(context -> EzCommandSupport.run(context, player ->
                                    export(player, StringArgumentType.getString(context, "args").trim())))));
        }
        sub(node, HotbarCommands::importCode, Set.of(), "import", "importar");
        sub(node, HotbarCommands::fromPalette, Set.of(), "palette", "paleta");
        sub(node, HotbarCommands::fromGradient, Set.of("v"), "gradient", "degradado");
        sub(node, HotbarCommands::fromSelection, Set.of(), "selection", "seleccion");
        sub(node, HotbarCommands::fromStamp, Set.of(), "stamp", "sello");
        sub(node, HotbarCommands::fromPaint, Set.of(), "paint", "pintura");
        sub(node, HotbarCommands::fromRoof, Set.of(), "roof", "tejado");
        return node;
    }

    private static void sub(LiteralArgumentBuilder<ServerCommandSource> node, Action action, Set<String> withValue,
                            String... names) {
        for (String name : names) {
            node.then(literal(name).then(argument("args", StringArgumentType.greedyString())
                    .suggests((context, builder) -> names(context, builder, false))
                    .executes(context -> EzCommandSupport.run(context, player -> action.run(player,
                            CommandTokens.parse(StringArgumentType.getString(context, "args"), withValue),
                            context)))));
        }
    }

    /** Sugiere los nombres de las barras del jugador en la primera palabra. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> names(
            CommandContext<ServerCommandSource> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            boolean all) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (player != null && !remaining.contains(" ")) {
            try {
                for (String name : HotbarService.book(player).names()) {
                    if (name.startsWith(remaining)) {
                        builder.suggest(name);
                    }
                }
            } catch (RuntimeException ignored) {
                // Sin proyecto abierto no hay nada que sugerir.
            }
            if (all && "all".startsWith(remaining)) {
                builder.suggest("all");
            }
        }
        return builder.buildFuture();
    }

    // ---- Barras guardadas --------------------------------------------------------------------------------------

    /** {@code save <nombre> [-i hueco|objeto] [-f]}. */
    private static int save(ServerPlayerEntity player, CommandTokens tokens, CommandContext<ServerCommandSource> context) {
        tokens.allow("i", "f").atMost(1);
        SavedHotbar saved = HotbarService.save(player, tokens.require(0, "el nombre de la barra"),
                tokens.option("i", ""), tokens.flag("f"));
        EzCommandSupport.send(player, Text.literal("Barra guardada: " + saved.name() + " · "
                        + saved.filled() + " de 9 huecos ").formatted(Formatting.GREEN)
                .append(EzCommandSupport.button("[exportar]", "/komaedit hotbar export " + saved.name(),
                        Formatting.AQUA)));
        return 1;
    }

    /** {@code load <nombre>}. */
    private static int load(ServerPlayerEntity player, CommandTokens tokens, CommandContext<ServerCommandSource> context) {
        tokens.allow().atMost(1);
        HotbarService.Applied applied = HotbarService.apply(player, tokens.require(0, "la barra que quieres"));
        if (applied != null) {
            EzCommandSupport.send(player, Text.literal(HotbarService.describe(applied)).formatted(
                    applied.missing().isEmpty() ? Formatting.GREEN : Formatting.YELLOW));
        }
        return 1;
    }

    private static int list(ServerPlayerEntity player) {
        HotbarBook book = HotbarService.book(player);
        if (book.isEmpty()) {
            EzCommandSupport.send(player, Text.literal("No tienes barras guardadas. ").formatted(Formatting.GRAY)
                    .append(Text.literal("/komaedit hotbar save <nombre>").formatted(Formatting.WHITE))
                    .append(Text.literal(" guarda la que llevas.").formatted(Formatting.GRAY)));
            return 1;
        }
        EzCommandSupport.send(player, Text.literal("Barras guardadas (" + book.size() + "/" + HotbarBook.MAX_HOTBARS
                + ")" + (book.autosave() ? " · la que dejas se guarda sola" : "")).formatted(Formatting.YELLOW));
        for (SavedHotbar hotbar : book.hotbars()) {
            boolean active = hotbar.name().equals(book.active());
            MutableText line = Text.literal(active ? " ▶ " : "   ").formatted(Formatting.GREEN)
                    .append(EzCommandSupport.button(hotbar.name(), "/komaedit hotbar load " + hotbar.name(),
                            active ? Formatting.GREEN : Formatting.WHITE))
                    .append(Text.literal(" " + contents(hotbar)).formatted(Formatting.GRAY))
                    .append(Text.literal(" "))
                    .append(EzCommandSupport.button("[exportar]", "/komaedit hotbar export " + hotbar.name(),
                            Formatting.AQUA))
                    .append(Text.literal(" "))
                    .append(EzCommandSupport.button("[borrar]", "/komaedit hotbar delete " + hotbar.name(),
                            Formatting.RED));
            player.sendMessage(line, false);
        }
        return 1;
    }

    private static String contents(SavedHotbar hotbar) {
        StringBuilder text = new StringBuilder();
        for (HotbarItem item : hotbar.slots()) {
            if (!item.isEmpty()) {
                if (!text.isEmpty()) {
                    text.append(", ");
                }
                text.append(item.shortId());
                if (text.length() > 60) {
                    return text.append("…").toString();
                }
            }
        }
        return text.isEmpty() ? "(vacía)" : text.toString();
    }

    private static int delete(ServerPlayerEntity player, CommandTokens tokens, CommandContext<ServerCommandSource> context) {
        tokens.allow().atMost(1);
        String name = tokens.require(0, "la barra que quieres borrar");
        HotbarService.delete(player, name);
        EzCommandSupport.ok(player, "Barra borrada: " + name.toLowerCase(Locale.ROOT));
        return 1;
    }

    private static int rename(ServerPlayerEntity player, CommandTokens tokens, CommandContext<ServerCommandSource> context) {
        tokens.allow().atMost(2);
        String from = tokens.require(0, "la barra");
        String to = tokens.require(1, "el nombre nuevo");
        HotbarService.rename(player, from, to);
        EzCommandSupport.ok(player, "Barra " + from.toLowerCase(Locale.ROOT) + " → " + to.toLowerCase(Locale.ROOT));
        return 1;
    }

    private static int icon(ServerPlayerEntity player, CommandTokens tokens, CommandContext<ServerCommandSource> context) {
        tokens.allow().atMost(2);
        SavedHotbar changed = HotbarService.icon(player, tokens.require(0, "la barra"),
                tokens.require(1, "el hueco (1–9) o el objeto del icono"));
        EzCommandSupport.ok(player, "Icono de " + changed.name() + ": " + HotbarItem.shortId(changed.iconItem()));
        return 1;
    }

    private static int move(ServerPlayerEntity player, CommandTokens tokens, CommandContext<ServerCommandSource> context) {
        tokens.allow().atMost(2);
        String name = tokens.require(0, "la barra");
        int position;
        try {
            position = Integer.parseInt(tokens.require(1, "la posición (1 es la primera)"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La posición es un número: 1 es la primera");
        }
        HotbarService.move(player, name, position);
        EzCommandSupport.ok(player, "Barra " + name.toLowerCase(Locale.ROOT) + " movida al puesto " + Math.max(1,
                HotbarService.book(player).indexOf(name) + 1));
        return 1;
    }

    private static int autosave(ServerPlayerEntity player, String value) {
        boolean on = value == null ? !HotbarService.book(player).autosave() : switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "on", "si", "sí", "true", "1" -> true;
            case "off", "no", "false", "0" -> false;
            default -> throw new IllegalArgumentException("autosave on u off");
        };
        HotbarService.autosave(player, on);
        EzCommandSupport.ok(player, on ? "Al cambiar de barra, la que dejas se guarda sola"
                : "Al cambiar de barra, la que dejas ya no se guarda sola");
        return 1;
    }

    private static int export(ServerPlayerEntity player, String name) {
        List<SavedHotbar> hotbars = HotbarService.exported(player, name);
        String code = HotbarCodec.encode(hotbars);
        String label = hotbars.size() == 1 ? hotbars.getFirst().name() : hotbars.size() + " barras";
        String command = "/komaedit hotbar import " + code;
        MutableText line = Text.literal("Código de " + label + " (" + code.length() + " letras): ")
                .formatted(Formatting.YELLOW)
                .append(EzCommandSupport.copyable("[copiar]", code, Formatting.AQUA));
        if (command.length() <= HotbarCodec.CHAT_LIMIT) {
            line.append(Text.literal(" ")).append(EzCommandSupport.copyable("[copiar orden]", command,
                    Formatting.AQUA));
        } else {
            line.append(Text.literal(" · no cabe en el chat: impórtalo con Pegar en el Studio o en el menú de barras")
                    .formatted(Formatting.GRAY));
        }
        EzCommandSupport.send(player, line);
        return 1;
    }

    /** {@code import <código> [nombre] [-f]}. */
    private static int importCode(ServerPlayerEntity player, CommandTokens tokens,
                                  CommandContext<ServerCommandSource> context) {
        tokens.allow("f").atMost(2);
        HotbarService.Imported imported = HotbarService.importCode(player, tokens.require(0, "el código kh1:…"),
                tokens.at(1, ""), tokens.flag("f"));
        EzCommandSupport.send(player, Text.literal(HotbarService.describe(imported) + " ").formatted(Formatting.GREEN)
                .append(EzCommandSupport.button("[usar]", "/komaedit hotbar load " + imported.names().getFirst(),
                        Formatting.AQUA)));
        return 1;
    }

    // ---- Crear desde otra cosa ---------------------------------------------------------------------------------

    /** {@code palette <nombre> <paleta|kp1:…> [-f] [-l]}: también {@code ##Nombre} y {@code #guardada}. */
    private static int fromPalette(ServerPlayerEntity player, CommandTokens tokens,
                                   CommandContext<ServerCommandSource> context) {
        tokens.allow("f", "l");
        String name = tokens.require(0, "el nombre de la barra");
        String palette = rest(tokens, "la paleta (##Nombre, #guardada o bloques)");
        List<String> entries = palette.startsWith(PaletteCodec.PREFIX) ? PaletteCodec.decode(palette)
                : SavedPalettes.parse(player.getUuid(), palette);
        return created(player, name, HotbarSources.fromBlocks(entries, HotbarCommands::itemOf, true), tokens,
                "de la paleta " + palette);
    }

    /** {@code gradient <nombre> <A> <B> [-c -s -o -u -x] [-v variante] [-f] [-l]}: el degradado de color en 9 pasos. */
    private static int fromGradient(ServerPlayerEntity player, CommandTokens tokens,
                                    CommandContext<ServerCommandSource> context) {
        tokens.allow("c", "s", "o", "u", "x", "v", "f", "l").atMost(3);
        String name = tokens.require(0, "el nombre de la barra");
        BlockColorCatalog catalog = BlockColorCatalogs.get();
        ColorCommands.Reference from = ColorCommands.reference(tokens.require(1, "el bloque o color de inicio"),
                catalog);
        ColorCommands.Reference to = ColorCommands.reference(tokens.require(2, "el bloque o color final"), catalog);
        ColorFilter filter = ColorCommands.filter(tokens);
        int variant;
        try {
            variant = Math.clamp(Integer.parseInt(tokens.option("v", "0")), 0, 1_000);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La variante es un número");
        }
        List<String> ids = ColorGradient.between(catalog, from.anchor(), to.anchor(), GRADIENT_STEPS, filter, variant)
                .stream().map(BlockColor::id).toList();
        return created(player, name, HotbarSources.fromBlocks(ids, HotbarCommands::itemOf, true), tokens,
                "del degradado " + from.label() + " → " + to.label());
    }

    /** {@code selection <nombre> [-f] [-l]}: los nueve bloques más usados de la selección. */
    private static int fromSelection(ServerPlayerEntity player, CommandTokens tokens,
                                     CommandContext<ServerCommandSource> context) throws Exception {
        tokens.allow("f", "l").atMost(1);
        String name = NamedDefinitions.normalizeName(tokens.require(0, "el nombre de la barra"));
        if (HotbarService.book(player).get(name).isPresent() && !tokens.flag("f")) {
            throw new IllegalArgumentException("Ya tienes una barra llamada " + name + "; añade -f para sustituirla");
        }
        Region selection = WorldEditService.selection(player);
        UUID playerId = player.getUuid();
        MinecraftServer server = player.getServer();
        RegionOperationService.capture(player, selection, 0, "Barra de la selección", snapshot -> {
            ServerPlayerEntity current = server == null ? null : server.getPlayerManager().getPlayer(playerId);
            if (current == null) {
                return;
            }
            try {
                Map<String, Long> counts = new HashMap<>();
                for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
                    for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                        for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                            if (snapshot.selected(x, y, z) && !snapshot.isAir(x, y, z)
                                    && !snapshot.isLiquid(x, y, z)) {
                                counts.merge(snapshot.blockId(x, y, z), 1L, Long::sum);
                            }
                        }
                    }
                }
                if (counts.isEmpty()) {
                    throw new IllegalArgumentException("La selección está vacía");
                }
                created(current, name, HotbarSources.fromBlocks(HotbarSources.mostUsed(counts),
                        HotbarCommands::itemOf, false), tokens, "con lo más usado de la selección");
            } catch (RuntimeException exception) {
                EzCommandSupport.send(current, Text.literal(exception.getMessage()).formatted(Formatting.RED));
            }
        });
        return 1;
    }

    /** {@code stamp <nombre> <paleta de superficie>}: los bloques de un sello de relieve ({@code natural}…). */
    private static int fromStamp(ServerPlayerEntity player, CommandTokens tokens,
                                 CommandContext<ServerCommandSource> context) {
        tokens.allow("f", "l");
        String name = tokens.require(0, "el nombre de la barra");
        String palette = tokens.positional().size() > 1 ? rest(tokens, "") : SurfacePalette.DEFAULT;
        List<String> blocks = List.copyOf(SurfacePalette.parse(palette).blockIds());
        return created(player, name, HotbarSources.fromBlocks(blocks, HotbarCommands::itemOf, false), tokens,
                "del sello " + palette);
    }

    /** {@code paint <nombre> <paleta>}: la paleta del pincel de pintura ({@code hierba_piedra}…) o cualquier otra. */
    private static int fromPaint(ServerPlayerEntity player, CommandTokens tokens,
                                 CommandContext<ServerCommandSource> context) {
        tokens.allow("f", "l");
        String name = tokens.require(0, "el nombre de la barra");
        String palette = tokens.positional().size() > 1 ? rest(tokens, "") : PaintPalettes.DEFAULT;
        List<String> entries = SavedPalettes.parse(player.getUuid(), PaintPalettes.expand(palette));
        return created(player, name, HotbarSources.fromBlocks(entries, HotbarCommands::itemOf, true), tokens,
                "de la pintura " + palette);
    }

    /** {@code roof <nombre> <material>}: el material del tejado con sus escaleras, losas y demás piezas. */
    private static int fromRoof(ServerPlayerEntity player, CommandTokens tokens,
                                CommandContext<ServerCommandSource> context) {
        tokens.allow("f", "l").atMost(2);
        String name = tokens.require(0, "el nombre de la barra");
        String material = BlockSpec.parse(tokens.require(1, "el material del tejado")).id();
        if (itemOf(material) == null) {
            throw new IllegalArgumentException("«" + HotbarItem.shortId(material) + "» no es un bloque con objeto");
        }
        List<String> blocks = HotbarSources.family(material, BlockStates.families().family(material).orElse(null));
        return created(player, name, HotbarSources.fromBlocks(blocks, HotbarCommands::itemOf, false), tokens,
                "del tejado de " + HotbarItem.shortId(material));
    }

    /** Guarda la barra creada, la pone si se pidió ({@code -l}) y lo cuenta. */
    private static int created(ServerPlayerEntity player, String name, HotbarSources.Built built, CommandTokens tokens,
                               String from) {
        SavedHotbar saved = HotbarService.create(player, name, built.slots(), tokens.flag("f"));
        MutableText line = Text.literal("Barra " + saved.name() + " " + from + ": " + contents(saved) + " ")
                .formatted(Formatting.GREEN);
        if (!built.skipped().isEmpty()) {
            line.append(Text.literal("· sin objeto: " + String.join(", ", built.skipped()) + " ")
                    .formatted(Formatting.GRAY));
        }
        if (tokens.flag("l")) {
            EzCommandSupport.send(player, line);
            HotbarService.Applied applied = HotbarService.apply(player, saved.name());
            if (applied != null) {
                EzCommandSupport.send(player, Text.literal(HotbarService.describe(applied)).formatted(Formatting.GRAY));
            }
        } else {
            EzCommandSupport.send(player, line.append(EzCommandSupport.button("[usar]",
                    "/komaedit hotbar load " + saved.name(), Formatting.AQUA)));
        }
        return 1;
    }

    /** Lo que queda tras el nombre, unido: una paleta puede llevar espacios. */
    private static String rest(CommandTokens tokens, String what) {
        if (tokens.positional().size() < 2) {
            throw new IllegalArgumentException("Falta " + what);
        }
        return String.join(" ", tokens.positional().subList(1, tokens.positional().size())).trim();
    }

    /** El objeto de un bloque y si coloca ese mismo bloque (para llevar su estado). */
    static HotbarSources.ItemRef itemOf(String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            return null;
        }
        Block block = Registries.BLOCK.get(id);
        Item item = block.asItem();
        if (item == Items.AIR) {
            return null;
        }
        boolean same = item instanceof BlockItem blockItem && blockItem.getBlock() == block;
        return new HotbarSources.ItemRef(Registries.ITEM.getId(item).toString(), same);
    }
}
