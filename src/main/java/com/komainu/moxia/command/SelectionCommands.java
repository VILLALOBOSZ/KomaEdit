package com.komainu.moxia.command;

import com.komainu.moxia.path.PathGenerators;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.worldedit.ez.EzDirections;
import com.komainu.moxia.worldedit.ez.SavedSelections;
import com.komainu.moxia.worldedit.ez.SelectionBridge;
import com.komainu.moxia.worldedit.ez.SelectionShape;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit sel}: órdenes de selección de ezEdits ({@code //selload}, {@code //next}, {@code //selhere},
 * {@code //ezselinvert}, {@code //delpos2}, {@code //encapsulate}, {@code //encapsulatenear} y {@code //ezsel}) sobre la
 * selección de WorldEdit. La catenaria ({@code //selcatenary}) es {@code /komaedit path catenary}.
 */
public final class SelectionCommands {
    /** Bloques como mucho que revisa {@code encapsulate}. */
    public static final long MAX_SCAN = 1_000_000L;
    public static final int MAX_NEAR_RADIUS = 48;

    private SelectionCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("sel").build());
        root.addChild(command("seleccion").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        SuggestionProvider<ServerCommandSource> saved = (context, builder) -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null) {
                try {
                    SavedSelections.store(player.getUuid()).all().keySet().forEach(builder::suggest);
                } catch (RuntimeException ignored) {
                    // Sin proyecto abierto no hay nada que sugerir.
                }
            }
            return builder.buildFuture();
        };
        return literal(name)
                .requires(source -> EzCommandSupport.canAny(source, KomaPermissions.SELECT))
                .executes(context -> list(context, ""))
                .then(literal("list").requires(selection("list"))
                        .executes(context -> list(context, ""))
                        .then(argument("flags", StringArgumentType.greedyString())
                                .suggests(EzCommandSupport.words(List.of("-g")))
                                .executes(context -> list(context, StringArgumentType.getString(context, "flags")))))
                .then(literal("save").requires(selection("save"))
                        .then(argument("args", StringArgumentType.greedyString())
                        .executes(SelectionCommands::save)))
                .then(literal("load").requires(selection("load"))
                        .then(argument("args", StringArgumentType.greedyString())
                        .suggests(saved)
                        .executes(SelectionCommands::load)))
                .then(literal("delete").requires(selection("delete"))
                        .then(argument("name", StringArgumentType.word())
                        .suggests(saved)
                        .executes(SelectionCommands::delete)))
                .then(literal("next").requires(selection("next"))
                        .executes(context -> next(context, "me", 0))
                        .then(argument("direction", StringArgumentType.word())
                                .suggests(EzCommandSupport.words(List.of("me", "up", "down", "north", "south", "east",
                                        "west", "left", "right", "back")))
                                .executes(context -> next(context, StringArgumentType.getString(context, "direction"), 0))
                                .then(argument("gap", IntegerArgumentType.integer(-4096, 4096))
                                        .executes(context -> next(context,
                                                StringArgumentType.getString(context, "direction"),
                                                IntegerArgumentType.getInteger(context, "gap"))))))
                .then(hereCommand("here"))
                .then(hereCommand("tome"))
                .then(literal("invert").requires(selection("invert")).executes(SelectionCommands::invert))
                .then(literal("delpos2").requires(selection("delpos2"))
                        .executes(SelectionCommands::deleteLastPoint))
                .then(encapsulateCommand("encapsulate"))
                .then(encapsulateCommand("enc"))
                .then(encapsulateNearCommand("encapsulatenear"))
                .then(encapsulateNearCommand("encnear"))
                .then(catenaryCommand("catenary"))
                .then(catenaryCommand("catenaria"));
    }

    private static java.util.function.Predicate<ServerCommandSource> selection(String sub) {
        return EzCommandSupport.node(KomaPermissions.SELECT + "." + sub);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> hereCommand(String name) {
        return literal(name)
                .requires(selection("here"))
                .executes(context -> here(context, "pos1"))
                .then(argument("point", StringArgumentType.word())
                        .suggests(EzCommandSupport.words(List.of("pos1", "pos2", "center")))
                        .executes(context -> here(context, StringArgumentType.getString(context, "point"))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> catenaryCommand(String name) {
        return literal(name).requires(selection("catenary"))
                .then(argument("args", StringArgumentType.greedyString())
                        .suggests(EzCommandSupport.words(List.of("16", "0.1", "0.3", "down", "up", "left", "right")))
                        .executes(SelectionCommands::catenary));
    }

    /**
     * {@code catenary <puntos> [comba=0,1] [dirección=down]}: cambia la selección por una convexa a lo largo de una
     * catenaria entre su principio y su final, como {@code //selcatenary} de ezEdits.
     */
    private static int catenary(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String[] words = StringArgumentType.getString(context, "args").trim().split("\\s+");
            if (words.length > 3) {
                throw new IllegalArgumentException("Uso: sel catenary <puntos> [comba] [down|up|left|right]");
            }
            int points;
            double sag;
            try {
                points = Integer.parseInt(words[0]);
                sag = words.length > 1 ? Double.parseDouble(words[1].replace(',', '.')) : 0.1;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Uso: sel catenary <puntos> [comba] [down|up|left|right]");
            }
            if (points < 3 || points > 256) {
                throw new IllegalArgumentException("Los puntos van de 3 a 256");
            }
            PathGenerators.Bulge bulge = PathGenerators.Bulge.byId(words.length > 2 ? words[2] : "down");
            SelectionShape shape = SelectionBridge.current(player);
            if (shape.kind().isRound()) {
                throw new IllegalArgumentException("Una selección " + shape.kind().id()
                        + " no tiene principio ni final; usa cuboid, poly o convex");
            }
            List<BlockVector3> curve = PathGenerators.catenarySelection(shape.points().getFirst(),
                    shape.points().getLast(), sag, bulge, points);
            SelectionBridge.apply(player, new SelectionShape(SelectionShape.Kind.CONVEX, curve, 0, 0, 0, 0, 0,
                    shape.world()));
            EzCommandSupport.ok(player, "Selección convexa de " + curve.size() + " puntos a lo largo de la catenaria");
            return 1;
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> encapsulateCommand(String name) {
        return literal(name).requires(selection("encapsulate"))
                .then(argument("mask", StringArgumentType.greedyString())
                .executes(context -> encapsulate(context, StringArgumentType.getString(context, "mask"), -1)));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> encapsulateNearCommand(String name) {
        return literal(name).requires(selection("encapsulatenear"))
                .then(argument("radius", IntegerArgumentType.integer(1, MAX_NEAR_RADIUS))
                .then(argument("mask", StringArgumentType.greedyString())
                        .executes(context -> encapsulate(context, StringArgumentType.getString(context, "mask"),
                                IntegerArgumentType.getInteger(context, "radius")))));
    }

    private static int list(CommandContext<ServerCommandSource> context, String flags) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            boolean grouped = CommandTokens.parse(flags, Set.of()).allow("g").atMost(0).flag("g");
            Map<String, String> saved = SavedSelections.store(player.getUuid()).all();
            if (saved.isEmpty()) {
                EzCommandSupport.info(player, "No tienes selecciones guardadas: /komaedit sel save <nombre>");
                return 1;
            }
            Map<String, List<String>> groups = new TreeMap<>();
            for (Map.Entry<String, String> entry : saved.entrySet()) {
                String group;
                try {
                    group = grouped ? SelectionShape.fromJson(entry.getValue()).kind().id() : "";
                } catch (IllegalArgumentException exception) {
                    group = grouped ? "dañadas" : "";
                }
                groups.computeIfAbsent(group, key -> new ArrayList<>()).add(entry.getKey());
            }
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                MutableText line = Text.literal(group.getKey().isEmpty() ? "Selecciones: " : group.getKey() + ": ")
                        .formatted(Formatting.YELLOW);
                boolean first = true;
                for (String selection : group.getValue()) {
                    if (!first) {
                        line.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                    }
                    first = false;
                    line.append(EzCommandSupport.button(selection, "/komaedit sel load " + selection,
                            Formatting.AQUA));
                }
                EzCommandSupport.send(player, line);
            }
            return 1;
        });
    }

    private static int save(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of())
                    .allow("f").atMost(1);
            String name = NamedDefinitions.normalizeName(tokens.require(0, "el nombre de la selección"));
            SelectionShape shape = SelectionBridge.current(player);
            boolean replaced = SavedSelections.save(player.getUuid(), name, shape, tokens.flag("f"));
            EzCommandSupport.ok(player, (replaced ? "Selección sustituida: " : "Selección guardada: ") + name + " ("
                    + shape.describe() + ")");
            return 1;
        });
    }

    /** {@code load <nombre> [-h]}: recupera una selección; con {@code -h} la trae al jugador. */
    private static int load(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of())
                    .allow("h").atMost(1);
            String name = tokens.require(0, "el nombre de la selección");
            SelectionShape shape = SavedSelections.load(player.getUuid(), name);
            String world = WorldEditService.context(player).world().getName();
            if (tokens.flag("h")) {
                shape = shape.moveTo(SelectionShape.Anchor.POS1, feet(player));
            } else if (shape.world() != null && !shape.world().equals(world)) {
                EzCommandSupport.info(player, "Ojo: la selección se guardó en " + shape.world());
            }
            SelectionBridge.apply(player, shape);
            EzCommandSupport.ok(player, "Selección cargada: " + NamedDefinitions.normalizeName(name) + " ("
                    + shape.describe() + ")");
            return 1;
        });
    }

    private static int delete(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String name = NamedDefinitions.normalizeName(StringArgumentType.getString(context, "name"));
            if (!SavedSelections.store(player.getUuid()).delete(name)) {
                throw new IllegalArgumentException("No tienes ninguna selección llamada " + name);
            }
            EzCommandSupport.ok(player, "Selección borrada: " + name);
            return 1;
        });
    }

    private static int next(CommandContext<ServerCommandSource> context, String direction, int gap)
            throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            BlockVector3 step = EzDirections.parse(direction, player.getYaw(), player.getPitch());
            SelectionShape shape = SelectionBridge.current(player).next(step, gap);
            SelectionBridge.apply(player, shape);
            EzCommandSupport.ok(player, "Selección desplazada a " + format(shape.minimum()));
            return 1;
        });
    }

    private static int here(CommandContext<ServerCommandSource> context, String point) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            SelectionShape.Anchor anchor = SelectionShape.Anchor.byName(point);
            SelectionShape shape = SelectionBridge.current(player).moveTo(anchor, feet(player));
            SelectionBridge.apply(player, shape);
            EzCommandSupport.ok(player, "Selección traída a " + format(feet(player)));
            return 1;
        });
    }

    private static int invert(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            SelectionShape shape = SelectionBridge.current(player);
            if (shape.kind().isRound()) {
                throw new IllegalArgumentException("Una selección " + shape.kind().id() + " no tiene orden que invertir");
            }
            SelectionBridge.apply(player, shape.inverted());
            EzCommandSupport.ok(player, "Orden de los puntos invertido");
            return 1;
        });
    }

    private static int deleteLastPoint(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            SelectionShape shape = SelectionBridge.current(player).withoutLastPoint();
            SelectionBridge.apply(player, shape);
            EzCommandSupport.ok(player, "Último punto quitado; quedan " + shape.points().size());
            return 1;
        });
    }

    /**
     * {@code encapsulate <máscara>} (radio -1): ajusta la selección al cubo más pequeño con los bloques de la máscara.
     * {@code encapsulatenear <radio> <máscara>}: lo mismo buscando en un cubo de ese radio alrededor del jugador.
     */
    private static int encapsulate(CommandContext<ServerCommandSource> context, String maskInput, int radius)
            throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            WorldEditService.PlayerContext worldEdit = WorldEditService.context(player);
            Region region;
            if (radius < 0) {
                region = worldEdit.session().getSelection(worldEdit.world()).clone();
            } else {
                BlockVector3 center = feet(player);
                region = new CuboidRegion(worldEdit.world(), center.subtract(radius, radius, radius),
                        center.add(radius, radius, radius));
            }
            if (region.getVolume() > MAX_SCAN) {
                throw new IllegalArgumentException("La zona tiene " + ProgressLine.group(region.getVolume()) + " bloques; el máximo es "
                        + MAX_SCAN);
            }
            ParserContext parser = new ParserContext();
            parser.setActor(worldEdit.actor());
            parser.setSession(worldEdit.session());
            parser.setWorld(worldEdit.world());
            parser.setExtent(worldEdit.world());
            parser.setRestricted(!worldEdit.actor().hasPermission("worldedit.anyblock"));
            Mask mask = WorldEdit.getInstance().getMaskFactory().parseFromInput(maskInput, parser);
            BlockVector3 minimum = null;
            BlockVector3 maximum = null;
            int minY = worldEdit.world().getMinY();
            int maxY = worldEdit.world().getMaxY();
            for (BlockVector3 position : region) {
                if (position.y() < minY || position.y() > maxY || !mask.test(position)) {
                    continue;
                }
                minimum = minimum == null ? position : minimum.getMinimum(position);
                maximum = maximum == null ? position : maximum.getMaximum(position);
            }
            if (minimum == null) {
                throw new IllegalArgumentException("Ningún bloque de la zona cumple la máscara " + maskInput);
            }
            SelectionBridge.apply(player, SelectionShape.cuboid(minimum, maximum, worldEdit.world().getName()));
            BlockVector3 size = maximum.subtract(minimum).add(1, 1, 1);
            EzCommandSupport.ok(player, "Selección ajustada: " + size.x() + "×" + size.y() + "×" + size.z()
                    + " desde " + format(minimum));
            return 1;
        });
    }

    private static BlockVector3 feet(ServerPlayerEntity player) {
        BlockPos position = player.getBlockPos();
        return BlockVector3.at(position.getX(), position.getY(), position.getZ());
    }

    private static String format(BlockVector3 position) {
        return position.x() + ", " + position.y() + ", " + position.z();
    }
}
