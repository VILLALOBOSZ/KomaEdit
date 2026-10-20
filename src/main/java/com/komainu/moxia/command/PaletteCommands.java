package com.komainu.moxia.command;

import com.komainu.moxia.worldedit.EditHistory;
import com.komainu.moxia.gradient.BlockSpec;
import com.komainu.moxia.gradient.GradientPalette;
import com.komainu.moxia.gradient.PaletteCodec;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.gradient.PaletteSwap;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.gradient.ServerPalettes;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.PaletteSwapOperation;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.worldedit.ez.EzContext;
import com.komainu.moxia.worldedit.ez.EzDirections;
import com.komainu.moxia.worldedit.ez.SelectionBridge;
import com.komainu.moxia.worldedit.ez.SelectionShape;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit palette} (alias {@code paleta}): paletas guardadas por jugador y mundo, como {@code //ezpalette}.
 * Las guardadas se usan con {@code #nombre} en cualquier sitio que acepte una paleta.
 */
public final class PaletteCommands {
    /** Bloques como mucho que lee {@code fetch} de una vez. */
    static final int MAX_FETCH = PaletteSpec.MAX_ENTRIES;

    private PaletteCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("palette").build());
        root.addChild(command("paleta").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .executes(context -> list(context, "all"))
                .then(literal("list").requires(EzCommandSupport.node("komaedit.palette.list"))
                        .executes(context -> list(context, "all"))
                        .then(argument("set", StringArgumentType.word())
                                .suggests(EzCommandSupport.words(List.of("all", "default", "mine")))
                                .executes(context -> list(context, StringArgumentType.getString(context, "set")))))
                .then(literal("save").requires(EzCommandSupport.node("komaedit.palette.save"))
                        .then(argument("args", StringArgumentType.greedyString())
                        .executes(PaletteCommands::save)))
                .then(literal("delete").requires(EzCommandSupport.node("komaedit.palette.delete"))
                        .then(argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player != null) {
                                try {
                                    SavedPalettes.store(player.getUuid()).all().keySet().forEach(builder::suggest);
                                } catch (RuntimeException ignored) {
                                    // Sin proyecto abierto no hay nada que sugerir.
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(PaletteCommands::delete)))
                .then(literal("fetch").requires(EzCommandSupport.node("komaedit.palette.fetch"))
                        .then(argument("args", StringArgumentType.greedyString())
                        .suggests(EzCommandSupport.words(List.of("world", "selection", "hotbar")))
                        .executes(PaletteCommands::fetch)))
                .then(literal("place")
                        .requires(EzCommandSupport.node(KomaPermissions.BUILD + ".palette.place"))
                        .then(argument("args", StringArgumentType.greedyString()).executes(PaletteCommands::place)))
                .then(literal("swap")
                        .requires(EzCommandSupport.node(KomaPermissions.BUILD + ".palette.swap"))
                        .then(argument("args", StringArgumentType.greedyString()).executes(PaletteCommands::swap)))
                .then(literal("print").requires(EzCommandSupport.node("komaedit.palette.print"))
                        .then(argument("args", StringArgumentType.greedyString())
                        .executes(PaletteCommands::print)))
                .then(literal("encode").requires(EzCommandSupport.node("komaedit.palette.encode"))
                        .then(argument("palette", StringArgumentType.greedyString())
                        .executes(PaletteCommands::encode)))
                .then(literal("decode").requires(EzCommandSupport.node("komaedit.palette.decode"))
                        .then(argument("code", StringArgumentType.greedyString())
                        .executes(PaletteCommands::decode)));
    }

    private static int list(CommandContext<ServerCommandSource> context, String set) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String which = set.toLowerCase(Locale.ROOT);
            if (!List.of("all", "default", "mine", "todas", "serie", "mias", "mías").contains(which)) {
                throw new IllegalArgumentException("Usa list all, list default o list mine");
            }
            boolean defaults = !which.equals("mine") && !which.equals("mias") && !which.equals("mías");
            boolean mine = !which.equals("default") && !which.equals("serie");
            if (defaults) {
                MutableText line = Text.literal("De serie: ").formatted(Formatting.YELLOW);
                boolean first = true;
                for (GradientPalette palette : GradientPalette.values()) {
                    if (!first) {
                        line.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                    }
                    first = false;
                    line.append(EzCommandSupport.button("##" + palette.displayName(),
                            "/komaedit palette print ##" + palette.displayName(), Formatting.WHITE));
                }
                EzCommandSupport.send(player, line);
                if (!ServerPalettes.all().isEmpty()) {
                    MutableText server = Text.literal("Del servidor: ").formatted(Formatting.YELLOW);
                    boolean firstServer = true;
                    for (String name : ServerPalettes.all().keySet()) {
                        if (!firstServer) {
                            server.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                        }
                        firstServer = false;
                        server.append(EzCommandSupport.button("##" + name, "/komaedit palette print ##" + name,
                                Formatting.WHITE));
                    }
                    EzCommandSupport.send(player, server);
                }
            }
            if (mine) {
                Map<String, String> saved = SavedPalettes.store(player.getUuid()).all();
                if (saved.isEmpty()) {
                    EzCommandSupport.info(player, "No tienes paletas guardadas: /komaedit palette save <nombre> <paleta>");
                } else {
                    MutableText line = Text.literal("Tuyas: ").formatted(Formatting.YELLOW);
                    boolean first = true;
                    for (String name : saved.keySet()) {
                        if (!first) {
                            line.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                        }
                        first = false;
                        line.append(EzCommandSupport.button("#" + name, "/komaedit palette print #" + name,
                                Formatting.AQUA));
                    }
                    EzCommandSupport.send(player, line);
                }
            }
            return 1;
        });
    }

    private static int save(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of())
                    .allow("f");
            String name = NamedDefinitions.normalizeName(tokens.require(0, "el nombre de la paleta"));
            if (tokens.positional().size() < 2) {
                throw new IllegalArgumentException("Uso: /komaedit palette save <nombre> <paleta> [-f]");
            }
            String definition = String.join(" ", tokens.positional().subList(1, tokens.positional().size()));
            List<String> entries = SavedPalettes.parse(player.getUuid(), definition);
            validatePatterns(player, entries);
            boolean replaced = SavedPalettes.store(player.getUuid()).put(name, PaletteSpec.join(entries),
                    tokens.flag("f"));
            EzCommandSupport.ok(player, (replaced ? "Paleta sustituida: #" : "Paleta guardada: #") + name + " ("
                    + ProgressLine.group(entries.size()) + " bloques)");
            return 1;
        });
    }

    private static int delete(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String name = NamedDefinitions.normalizeName(StringArgumentType.getString(context, "name"));
            if (!SavedPalettes.store(player.getUuid()).delete(name)) {
                throw new IllegalArgumentException("No tienes ninguna paleta llamada #" + name);
            }
            EzCommandSupport.ok(player, "Paleta borrada: #" + name);
            return 1;
        });
    }

    /** {@code fetch <world|selection|hotbar> <nombre> [largo] [-d dirección] [-f]} */
    private static int fetch(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of("d"))
                    .allow("d", "f").atMost(3);
            String mode = tokens.require(0, "de dónde leer: world, selection o hotbar").toLowerCase(Locale.ROOT);
            String name = NamedDefinitions.normalizeName(tokens.require(1, "el nombre de la paleta"));
            int length = parseLength(tokens.at(2, "0"));
            List<String> entries = switch (mode) {
                case "world", "mundo" -> fetchWorld(player, length, tokens.option("d", "me"));
                case "selection", "seleccion", "selección" -> fetchSelection(player, length);
                case "hotbar", "barra" -> fetchHotbar(player, length);
                default -> throw new IllegalArgumentException("Modo desconocido: " + mode + " (world, selection, hotbar)");
            };
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("No se encontró ningún bloque");
            }
            boolean replaced = SavedPalettes.store(player.getUuid()).put(name, PaletteSpec.join(entries),
                    tokens.flag("f"));
            EzCommandSupport.send(player, Text.literal((replaced ? "Paleta sustituida: #" : "Paleta guardada: #")
                            + name + " (" + ProgressLine.group(entries.size()) + " bloques) ").formatted(Formatting.GREEN)
                    .append(EzCommandSupport.copyable("[copiar]", String.join(",", entries), Formatting.AQUA)));
            return 1;
        });
    }

    private static int parseLength(String text) {
        try {
            int length = Integer.parseInt(text);
            if (length < 0 || length > MAX_FETCH) {
                throw new NumberFormatException();
            }
            return length;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El largo va de 0 (hasta encontrar aire) a " + MAX_FETCH);
        }
    }

    private static List<String> fetchWorld(ServerPlayerEntity player, int length, String direction) {
        BlockPos start = EzContext.aim(player, false);
        if (start == null) {
            start = player.getBlockPos().down();
        }
        BlockVector3 step = EzDirections.parse(direction, player.getYaw(), player.getPitch());
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        List<String> entries = new ArrayList<>();
        BlockVector3 position = BlockVector3.at(start.getX(), start.getY(), start.getZ());
        int limit = length == 0 ? MAX_FETCH : length;
        for (int index = 0; index < limit; index++) {
            BlockState state = context.world().getBlock(position);
            if (state.getBlockType().getMaterial().isAir()) {
                break;
            }
            entries.add(entry(state));
            position = position.add(step);
        }
        return entries;
    }

    private static List<String> fetchSelection(ServerPlayerEntity player, int length) throws Exception {
        SelectionShape shape = SelectionBridge.current(player);
        if (shape.kind() != SelectionShape.Kind.CUBOID && shape.kind() != SelectionShape.Kind.EXTEND) {
            throw new IllegalArgumentException("fetch selection necesita una selección cuboid de 1×1×N");
        }
        BlockVector3 first = shape.points().getFirst();
        BlockVector3 last = shape.points().getLast();
        BlockVector3 delta = last.subtract(first);
        int nonZero = (delta.x() != 0 ? 1 : 0) + (delta.y() != 0 ? 1 : 0) + (delta.z() != 0 ? 1 : 0);
        if (nonZero > 1) {
            throw new IllegalArgumentException("La selección debe ser una fila de 1×1×N bloques");
        }
        BlockVector3 step = BlockVector3.at(Integer.signum(delta.x()), Integer.signum(delta.y()),
                Integer.signum(delta.z()));
        int count = Math.max(Math.abs(delta.x()), Math.max(Math.abs(delta.y()), Math.abs(delta.z()))) + 1;
        if (length > 0) {
            count = Math.min(count, length);
        }
        if (count > MAX_FETCH) {
            throw new IllegalArgumentException("Una paleta admite como mucho " + ProgressLine.group(MAX_FETCH) + " bloques");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        List<String> entries = new ArrayList<>();
        BlockVector3 position = first;
        for (int index = 0; index < count; index++) {
            entries.add(entry(context.world().getBlock(position)));
            position = position.add(step);
        }
        return entries;
    }

    private static List<String> fetchHotbar(ServerPlayerEntity player, int length) {
        List<String> entries = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof BlockItem blockItem) {
                String id = Registries.BLOCK.getId(blockItem.getBlock()).toString();
                entries.add(id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id);
                if (length > 0 && entries.size() >= length) {
                    break;
                }
            }
        }
        return entries;
    }

    /** Un estado como entrada de paleta: el id y solo las propiedades que no tiene por defecto. */
    static String entry(BlockState state) {
        BlockState defaults = state.getBlockType().getDefaultState();
        List<String> changed = new ArrayList<>();
        for (Map.Entry<Property<?>, Object> property : state.getStates().entrySet()) {
            Object fallback = defaults.getState(property.getKey());
            if (!property.getValue().equals(fallback)) {
                changed.add(property.getKey().getName() + "="
                        + String.valueOf(property.getValue()).toLowerCase(Locale.ROOT));
            }
        }
        String id = state.getBlockType().id();
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        return changed.isEmpty() ? id : id + "[" + String.join(",", changed) + "]";
    }

    /** {@code place <paleta> [dirección]}: pone la paleta como una fila de bloques delante del jugador. */
    private static int place(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of())
                    .allow().atMost(2);
            List<String> entries = SavedPalettes.parse(player.getUuid(), tokens.require(0, "la paleta"));
            BlockVector3 step = EzDirections.parse(tokens.at(1, "me"), player.getYaw(), player.getPitch());
            WorldEditService.PlayerContext worldEdit = WorldEditService.context(player);
            List<Pattern> patterns = entries.stream()
                    .map(entry -> WorldEditService.parsePatternUnchecked(worldEdit, entry))
                    .toList();
            BlockPos feet = player.getBlockPos();
            BlockVector3 position = BlockVector3.at(feet.getX(), feet.getY(), feet.getZ()).add(step);
            EditSession editSession = worldEdit.session().createEditSession(worldEdit.actor());
            try {
                for (Pattern pattern : patterns) {
                    editSession.setBlock(position, pattern);
                    position = position.add(step);
                }
            } finally {
                editSession.close();
                EditHistory.label(editSession, "Paleta");
                worldEdit.session().remember(editSession);
            }
            EzCommandSupport.ok(player, "Paleta colocada: " + ProgressLine.group(patterns.size()) + " bloques · //undo para deshacer");
            return 1;
        });
    }

    /** {@code swap <origen> <destino> [-a] [-s] [-f] [-b]} sobre la selección. */
    private static int swap(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of())
                    .allow("a", "s", "f", "b").atMost(2);
            List<String> source = SavedPalettes.parse(player.getUuid(), tokens.require(0, "la paleta de origen"));
            List<String> target = SavedPalettes.parse(player.getUuid(), tokens.require(1, "la paleta de destino"));
            PaletteSwap swap = PaletteSwap.of(source, target, tokens.flag("a"), tokens.flag("s"), tokens.flag("f"),
                    tokens.flag("b"));
            RegionOperationService.submit(player, 0, "Cambio de paleta", new PaletteSwapOperation(swap));
            return 1;
        });
    }

    private static int print(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of())
                    .allow("v").atMost(1);
            List<String> entries = SavedPalettes.parse(player.getUuid(), tokens.require(0, "la paleta"));
            boolean verbose = tokens.flag("v");
            List<String> shown = entries.stream().map(entry -> verbose ? verbose(entry) : brief(entry)).toList();
            EzCommandSupport.send(player, Text.literal(entries.size() + " bloques: ").formatted(Formatting.YELLOW)
                    .append(EzCommandSupport.copyable(String.join(", ", shown), String.join(",", entries),
                            Formatting.WHITE)));
            return 1;
        });
    }

    private static int encode(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            List<String> entries = SavedPalettes.parse(player.getUuid(),
                    StringArgumentType.getString(context, "palette"));
            String code = PaletteCodec.encode(entries);
            EzCommandSupport.send(player, Text.literal("Código (" + ProgressLine.group(entries.size()) + " bloques): ")
                    .formatted(Formatting.YELLOW).append(EzCommandSupport.copyable(code, code, Formatting.AQUA)));
            return 1;
        });
    }

    private static int decode(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            List<String> entries = PaletteCodec.decode(StringArgumentType.getString(context, "code"));
            String joined = String.join(",", entries);
            EzCommandSupport.send(player, Text.literal(entries.size() + " bloques: ").formatted(Formatting.YELLOW)
                    .append(EzCommandSupport.copyable(joined, joined, Formatting.WHITE)));
            return 1;
        });
    }

    private static String brief(String entry) {
        try {
            return BlockSpec.parse(entry).shortName();
        } catch (IllegalArgumentException exception) {
            return entry;
        }
    }

    private static String verbose(String entry) {
        try {
            return BlockSpec.parse(entry).toString();
        } catch (IllegalArgumentException exception) {
            return entry;
        }
    }

    /** Comprueba en el hilo del servidor que WorldEdit entiende cada entrada antes de guardarla. */
    private static void validatePatterns(ServerPlayerEntity player, List<String> entries) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        String invalid = entries.stream().filter(entry -> {
            try {
                WorldEditService.parsePatternUnchecked(context, entry);
                return false;
            } catch (IllegalArgumentException exception) {
                return true;
            }
        }).collect(Collectors.joining(", "));
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("WorldEdit no reconoce: " + invalid);
        }
    }
}
