package com.komainu.moxia.command;

import com.komainu.moxia.display.DisplayEntities;
import com.komainu.moxia.display.DisplayLook;
import com.komainu.moxia.display.DisplayPose;
import com.komainu.moxia.display.DisplaySelection;
import com.komainu.moxia.display.DisplayService;
import com.komainu.moxia.display.DisplayText;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.shape.Vec3;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit display} (alias {@code displays} y {@code decorar}): crear displays de bloque, de objeto y de texto
 * contra el bloque mirado y editarlos. Con el mod en el cliente se editan con el gizmo; estas órdenes hacen lo mismo
 * sin él (y son las que manda la tarjeta «Decorar» del Studio). Todo pasa por {@link DisplayService}, que lo comprueba.
 */
public final class DisplayCommands {
    private static final List<String> ITEM_MODES = List.of("fixed", "none", "gui", "ground", "head", "mano");
    private static final List<String> BILLBOARDS = List.of("fixed", "vertical", "horizontal", "center");
    private static final SuggestionProvider<ServerCommandSource> ITEM_MODE_SUGGESTIONS = (context, builder) -> {
        ITEM_MODES.forEach(builder::suggest);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> BILLBOARD_SUGGESTIONS = (context, builder) -> {
        BILLBOARDS.forEach(builder::suggest);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> AXIS_SUGGESTIONS = (context, builder) -> {
        List.of("x", "y", "z").forEach(builder::suggest);
        return builder.buildFuture();
    };

    private DisplayCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : new String[]{"display", "displays", "decorar"}) {
            root.addChild(command(name).build());
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && DisplayService.allowed(player))
                .executes(DisplayCommands::info);
        for (String edit : new String[]{"edit", "editar"}) {
            command.then(literal(edit)
                    .executes(context -> editing(context, null))
                    .then(literal("on").executes(context -> editing(context, true)))
                    .then(literal("off").executes(context -> editing(context, false))));
        }
        for (String block : new String[]{"block", "bloque"}) {
            command.then(literal(block)
                    .executes(context -> createBlock(context, null))
                    .then(argument("block", StringArgumentType.greedyString())
                            .executes(context -> createBlock(context, StringArgumentType.getString(context,
                                    "block")))));
        }
        for (String item : new String[]{"item", "objeto"}) {
            command.then(literal(item)
                    .executes(context -> createItem(context, "fixed"))
                    .then(argument("mode", StringArgumentType.word())
                            .suggests(ITEM_MODE_SUGGESTIONS)
                            .executes(context -> createItem(context, StringArgumentType.getString(context,
                                    "mode")))));
        }
        for (String text : new String[]{"text", "texto"}) {
            command.then(literal(text)
                    .then(argument("text", StringArgumentType.greedyString())
                            .executes(context -> createText(context, StringArgumentType.getString(context,
                                    "text")))));
        }
        for (String select : new String[]{"select", "elegir"}) {
            command.then(literal(select)
                    .executes(context -> select(context, DisplaySelection.Mode.REPLACE))
                    .then(literal("add").executes(context -> select(context, DisplaySelection.Mode.ADD)))
                    .then(literal("anadir").executes(context -> select(context, DisplaySelection.Mode.ADD)))
                    .then(literal("toggle").executes(context -> select(context, DisplaySelection.Mode.TOGGLE)))
                    .then(literal("clear").executes(DisplayCommands::clear))
                    .then(literal("ninguno").executes(DisplayCommands::clear)));
        }
        for (String move : new String[]{"move", "mover"}) {
            command.then(literal(move).then(argument("dx", DoubleArgumentType.doubleArg(-64, 64))
                    .then(argument("dy", DoubleArgumentType.doubleArg(-64, 64))
                            .then(argument("dz", DoubleArgumentType.doubleArg(-64, 64))
                                    .executes(DisplayCommands::move)))));
        }
        for (String rotate : new String[]{"rotate", "girar"}) {
            command.then(literal(rotate).then(argument("axis", StringArgumentType.word())
                    .suggests(AXIS_SUGGESTIONS)
                    .then(argument("degrees", DoubleArgumentType.doubleArg(-360, 360))
                            .executes(context -> rotate(context, false))
                            .then(literal("local").executes(context -> rotate(context, true))))));
        }
        for (String scale : new String[]{"scale", "escalar"}) {
            command.then(literal(scale).then(argument("x", DoubleArgumentType.doubleArg(0.01, 64))
                    .executes(context -> scale(context, true))
                    .then(argument("y", DoubleArgumentType.doubleArg(0.01, 64))
                            .then(argument("z", DoubleArgumentType.doubleArg(0.01, 64))
                                    .executes(context -> scale(context, false))))));
        }
        for (String reset : new String[]{"reset", "enderezar"}) {
            command.then(literal(reset).executes(context -> run(context, DisplayService::reset,
                    count -> Feedback.format("%s displays sin giro y con escala 1", count))));
        }
        for (String duplicate : new String[]{"duplicate", "duplicar"}) {
            command.then(literal(duplicate).executes(context -> run(context, DisplayService::duplicate,
                    count -> Feedback.format("%s copias en el mismo sitio, elegidas: muévelas · //undo", count))));
        }
        for (String delete : new String[]{"delete", "borrar"}) {
            command.then(literal(delete).executes(context -> run(context, DisplayService::delete,
                    count -> Feedback.format("%s displays borrados · //undo", count))));
        }
        for (String group : new String[]{"group", "agrupar"}) {
            command.then(literal(group).executes(context -> run(context, DisplayService::group,
                    count -> Feedback.format("Grupo de %s displays: un clic elige todos", count))));
        }
        for (String ungroup : new String[]{"ungroup", "desagrupar"}) {
            command.then(literal(ungroup).executes(context -> run(context, DisplayService::ungroup,
                    count -> Feedback.format("%s displays sin grupo", count))));
        }
        for (String set : new String[]{"set", "ajustes"}) {
            command.then(settings(literal(set)));
        }
        return command;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> settings(LiteralArgumentBuilder<ServerCommandSource> set) {
        for (String all : new String[]{"all", "todo"}) {
            set.then(literal(all).then(argument("settings", StringArgumentType.greedyString())
                    .executes(context -> {
                        try {
                            return setting(context, new DisplayService.LookSetting(DisplayLook.parse(
                                    StringArgumentType.getString(context, "settings"))));
                        } catch (IllegalArgumentException exception) {
                            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
                        }
                    })));
        }
        for (String brightness : new String[]{"brightness", "brillo"}) {
            set.then(literal(brightness)
                    .then(literal("auto").executes(context -> setting(context,
                            new DisplayService.BrightnessSetting(null, null))))
                    .then(argument("block", IntegerArgumentType.integer(0, 15))
                            .executes(context -> {
                                int value = IntegerArgumentType.getInteger(context, "block");
                                return setting(context, new DisplayService.BrightnessSetting(value, value));
                            })
                            .then(argument("sky", IntegerArgumentType.integer(0, 15))
                                    .executes(context -> setting(context, new DisplayService.BrightnessSetting(
                                            IntegerArgumentType.getInteger(context, "block"),
                                            IntegerArgumentType.getInteger(context, "sky")))))));
        }
        for (String shadow : new String[]{"shadow", "sombra"}) {
            set.then(literal(shadow).then(argument("radius", FloatArgumentType.floatArg(0, 64))
                    .executes(context -> setting(context, new DisplayService.ShadowSetting(
                            FloatArgumentType.getFloat(context, "radius"), 1.0F)))
                    .then(argument("strength", FloatArgumentType.floatArg(0, 1))
                            .executes(context -> setting(context, new DisplayService.ShadowSetting(
                                    FloatArgumentType.getFloat(context, "radius"),
                                    FloatArgumentType.getFloat(context, "strength")))))));
        }
        for (String view : new String[]{"viewrange", "distancia"}) {
            set.then(literal(view).then(argument("range", FloatArgumentType.floatArg(0.05F, 16))
                    .executes(context -> setting(context, new DisplayService.ViewRangeSetting(
                            FloatArgumentType.getFloat(context, "range"))))));
        }
        set.then(literal("billboard").then(argument("mode", StringArgumentType.word())
                .suggests(BILLBOARD_SUGGESTIONS)
                .executes(context -> {
                    try {
                        return setting(context, new DisplayService.BillboardSetting(DisplayPose.Billboard.parse(
                                StringArgumentType.getString(context, "mode"))));
                    } catch (IllegalArgumentException exception) {
                        return EzCommandSupport.fail(context.getSource(), exception.getMessage());
                    }
                })));
        for (String glow : new String[]{"glow", "contorno"}) {
            set.then(literal(glow)
                    .then(literal("on").executes(context -> setting(context,
                                    new DisplayService.GlowSetting(true, -1)))
                            .then(argument("color", StringArgumentType.word()).executes(context -> {
                                try {
                                    return setting(context, new DisplayService.GlowSetting(true,
                                            DisplayText.color(StringArgumentType.getString(context, "color"))));
                                } catch (IllegalArgumentException exception) {
                                    return EzCommandSupport.fail(context.getSource(), exception.getMessage());
                                }
                            })))
                    .then(literal("off").executes(context -> setting(context,
                            new DisplayService.GlowSetting(false, -1)))));
        }
        return set;
    }

    // ---------------------------------------------------------------- órdenes

    private static int info(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        MutableText text = Feedback.prefix().append(Text.literal(" · Decorar con displays").formatted(Formatting.GOLD));
        for (String line : DisplayService.info(player)) {
            text.append(Text.literal("\n" + line).formatted(Formatting.GRAY));
        }
        text.append("\n")
                .append(Feedback.button("[editar]", "/komaedit display edit", Formatting.AQUA,
                        "Abre o cierra el editor: clic en un display para elegirlo y moverlo con el gizmo"))
                .append(" ")
                .append(Feedback.button("[bloque]", "/komaedit display block", Formatting.GREEN,
                        "Un display del bloque que llevas en la mano, contra el bloque que miras"))
                .append(" ")
                .append(Feedback.button("[objeto]", "/komaedit display item", Formatting.GREEN,
                        "Un display del objeto que llevas en la mano"))
                .append(" ")
                .append(Feedback.fix("[texto]", "/komaedit display text "))
                .append(" ")
                .append(Feedback.button("[elegir el que miro]", "/komaedit display select", Formatting.YELLOW,
                        "Sin el mod en el cliente: elige el display que miras"));
        context.getSource().sendFeedback(() -> text, false);
        return 1;
    }

    private static int editing(CommandContext<ServerCommandSource> context, Boolean on) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            boolean open = DisplayService.setEditing(player, on == null ? !DisplayService.editing(player) : on);
            if (open && !DisplayService.hasMod(player)) {
                Feedback.info(player, "Editor abierto. Sin KomaEdit en el cliente no hay gizmo: usa /komaedit "
                        + "display select, move, rotate y scale");
            } else {
                Feedback.status(player, open ? "Editor de displays abierto: clic en un display para elegirlo"
                        : "Editor de displays cerrado");
            }
            return open ? 1 : 0;
        } catch (IllegalArgumentException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }

    private static int createBlock(CommandContext<ServerCommandSource> context, String text)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            BlockState state;
            if (text == null || text.isBlank()) {
                state = handBlock(player);
            } else {
                state = BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), text.strip(), false)
                        .blockState();
            }
            return created(player, DisplayService.create(player, new DisplayService.BlockSpec(state)));
        } catch (CommandSyntaxException exception) {
            return EzCommandSupport.fail(context.getSource(), "Bloque no válido: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }

    private static BlockState handBlock(ServerPlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof BlockItem block) {
                return block.getBlock().getDefaultState();
            }
        }
        throw new IllegalArgumentException("Lleva un bloque en la mano o escríbelo: /komaedit display block "
                + "minecraft:oak_stairs[facing=east]");
    }

    private static int createItem(CommandContext<ServerCommandSource> context, String mode)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            ItemStack stack = player.getMainHandStack().isEmpty() ? player.getOffHandStack()
                    : player.getMainHandStack();
            return created(player, DisplayService.create(player, new DisplayService.ItemSpec(stack,
                    DisplayEntities.itemMode(mode))));
        } catch (IllegalArgumentException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }

    private static int createText(CommandContext<ServerCommandSource> context, String text)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            return created(player, DisplayService.create(player, new DisplayService.TextSpec(
                    DisplayText.parse(text))));
        } catch (IllegalArgumentException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }

    private static int created(ServerPlayerEntity player, String description) {
        if (DisplayService.hasMod(player)) {
            Feedback.status(player, description + " creado y elegido: muévelo con el gizmo · //undo lo quita");
        } else {
            Feedback.ok(player, Text.literal(description + " creado y elegido · ")
                    .append(Feedback.fix("[mover]", "/komaedit display move 0 0.0625 0"))
                    .append(" ")
                    .append(Feedback.fix("[girar]", "/komaedit display rotate y 15"))
                    .append(" ")
                    .append(Feedback.fix("[escalar]", "/komaedit display scale 0.5")));
        }
        return 1;
    }

    private static int select(CommandContext<ServerCommandSource> context, DisplaySelection.Mode mode)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            Feedback.status(player, DisplayService.selectLooking(player, mode) + " elegido");
            return 1;
        } catch (IllegalArgumentException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }

    private static int clear(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        DisplayService.act(player, new com.komainu.moxia.display.DisplayPayloads.Act(
                com.komainu.moxia.display.DisplayPayloads.Action.CLEAR));
        Feedback.status(player, "Nada elegido");
        return 1;
    }

    private static int move(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Vec3 delta = new Vec3(DoubleArgumentType.getDouble(context, "dx"), DoubleArgumentType.getDouble(context, "dy"),
                DoubleArgumentType.getDouble(context, "dz"));
        return run(context, player -> DisplayService.move(player, delta),
                count -> Feedback.format("%s displays movidos · //undo", count));
    }

    private static int rotate(CommandContext<ServerCommandSource> context, boolean local)
            throws CommandSyntaxException {
        String axisText = StringArgumentType.getString(context, "axis").toLowerCase(Locale.ROOT);
        int axis = switch (axisText) {
            case "x" -> 0;
            case "y" -> 1;
            case "z" -> 2;
            default -> -1;
        };
        if (axis < 0) {
            return EzCommandSupport.fail(context.getSource(), "Eje desconocido: " + axisText + " (x, y o z)");
        }
        double degrees = DoubleArgumentType.getDouble(context, "degrees");
        return run(context, player -> DisplayService.rotate(player, axis, degrees, local),
                count -> Feedback.format("%s displays girados · //undo", count));
    }

    private static int scale(CommandContext<ServerCommandSource> context, boolean uniform)
            throws CommandSyntaxException {
        double x = DoubleArgumentType.getDouble(context, "x");
        Vec3 factors = uniform ? new Vec3(x, x, x) : new Vec3(x, DoubleArgumentType.getDouble(context, "y"),
                DoubleArgumentType.getDouble(context, "z"));
        return run(context, player -> DisplayService.scale(player, factors, uniform),
                count -> Feedback.format("%s displays escalados · //undo", count));
    }

    private static int setting(CommandContext<ServerCommandSource> context, DisplayService.Setting setting)
            throws CommandSyntaxException {
        return run(context, player -> DisplayService.set(player, setting),
                count -> Feedback.format("Ajuste puesto en %s displays · //undo", count));
    }

    @FunctionalInterface
    private interface Operation {
        int run(ServerPlayerEntity player);
    }

    private static int run(CommandContext<ServerCommandSource> context, Operation operation,
                           Function<Integer, Text> done) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            int count = operation.run(player);
            Feedback.status(player, done.apply(count));
            return count;
        } catch (IllegalArgumentException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }
}
