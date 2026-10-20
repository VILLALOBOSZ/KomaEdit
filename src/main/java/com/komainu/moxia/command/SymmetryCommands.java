package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.symmetry.SymmetryCenter;
import com.komainu.moxia.symmetry.SymmetryMode;
import com.komainu.moxia.symmetry.SymmetryService;
import com.komainu.moxia.symmetry.SymmetrySettings;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit symmetry} (alias {@code simetria} y {@code sym}): modo simetría para construir, como
 * {@code //ezsymmetry} de ezEdits. Espejos en X, Z e Y, radial de 2 a 16 brazos, centro en un bloque o entre bloques.
 */
public final class SymmetryCommands {
    private static final String ROOT = "/komaedit symmetry ";
    private static final List<String> MODES = List.of("x", "z", "xz", "y", "xy", "zy", "xzy", "radial", "radial 4",
            "radial 6", "radial 8", "radial 8 y", "radial 12", "radial 16");
    private static final List<String> AXES = List.of("x", "z", "xz", "y", "xyz");
    private static final SuggestionProvider<ServerCommandSource> MODE_SUGGESTIONS = (context, builder) -> {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        MODES.stream().filter(mode -> mode.startsWith(typed)).forEach(builder::suggest);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> AXIS_SUGGESTIONS = (context, builder) -> {
        AXES.forEach(builder::suggest);
        return builder.buildFuture();
    };

    private SymmetryCommands() {
    }

    /** Cuelga la orden de {@code /komaedit} (y de sus alias, que redirigen a la misma raíz). */
    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : new String[]{"symmetry", "simetria", "sym"}) {
            root.addChild(command(name).build());
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canAny(player, SymmetryService.PERMISSION))
                .executes(SymmetryCommands::info);
        for (String on : new String[]{"on", "activar"}) {
            command.then(literal(on)
                    .executes(context -> on(context, null))
                    .then(argument("mode", StringArgumentType.greedyString())
                            .suggests(MODE_SUGGESTIONS)
                            .executes(context -> on(context, StringArgumentType.getString(context, "mode")))));
        }
        for (String off : new String[]{"off", "desactivar"}) {
            command.then(literal(off).executes(SymmetryCommands::off));
        }
        command.then(literal("toggle").executes(context -> SymmetryService.settings(
                context.getSource().getPlayerOrThrow().getUuid()).active() ? off(context) : on(context, null)));
        for (String mode : new String[]{"mode", "modo"}) {
            command.then(literal(mode)
                    .executes(SymmetryCommands::info)
                    .then(argument("mode", StringArgumentType.greedyString())
                            .suggests(MODE_SUGGESTIONS)
                            .executes(context -> mode(context, StringArgumentType.getString(context, "mode")))));
        }
        for (String center : new String[]{"center", "centro"}) {
            LiteralArgumentBuilder<ServerCommandSource> node = literal(center).executes(context -> here(context, ""));
            for (String here : new String[]{"here", "aqui"}) {
                node.then(half(literal(here).executes(context -> here(context, "")), SymmetryCommands::here));
            }
            for (String selection : new String[]{"selection", "seleccion", "sel"}) {
                node.then(literal(selection).executes(SymmetryCommands::selection));
            }
            node.then(half(argument("pos", BlockPosArgumentType.blockPos())
                    .executes(context -> position(context, "")), SymmetryCommands::position));
            command.then(node);
        }
        for (String arms : new String[]{"arms", "brazos"}) {
            command.then(literal(arms).then(argument("arms",
                    IntegerArgumentType.integer(SymmetryMode.MIN_ARMS, SymmetryMode.MAX_ARMS))
                    .executes(context -> arms(context, IntegerArgumentType.getInteger(context, "arms")))));
        }
        for (String worldEdit : new String[]{"worldedit", "we"}) {
            command.then(literal(worldEdit)
                    .executes(context -> worldEdit(context, null))
                    .then(literal("on").executes(context -> worldEdit(context, true)))
                    .then(literal("off").executes(context -> worldEdit(context, false))));
        }
        command.then(literal("info").executes(SymmetryCommands::info));
        return command;
    }

    /** Añade {@code half [ejes]} (alias {@code medio}) detrás de una forma de fijar el centro. */
    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<ServerCommandSource, T>> T half(
            T node, CenterAction action) {
        for (String half : new String[]{"half", "medio"}) {
            node.then(literal(half)
                    .executes(context -> action.run(context, "xyz"))
                    .then(argument("axes", StringArgumentType.word())
                            .suggests(AXIS_SUGGESTIONS)
                            .executes(context -> action.run(context, StringArgumentType.getString(context, "axes")))));
        }
        return node;
    }

    @FunctionalInterface
    private interface CenterAction {
        int run(CommandContext<ServerCommandSource> context, String halfAxes) throws CommandSyntaxException;
    }

    private static int on(CommandContext<ServerCommandSource> context, String modeText) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            SymmetrySettings current = SymmetryService.settings(player.getUuid());
            SymmetryMode mode = modeText == null ? current.mode() : SymmetryMode.parse(modeText, current.mode().arms());
            requireMode(player, mode);
            String dimension = SymmetryService.dimension(player.getServerWorld());
            boolean recenter = current.center() == null || !dimension.equals(current.world());
            SymmetrySettings updated = SymmetryService.update(player.getUuid(), settings -> {
                SymmetrySettings next = settings.withMode(mode).withActive(true);
                return recenter ? next.withCenter(playerBlock(player), dimension) : next;
            });
            send(context.getSource(), tr("komaedit.symmetry.on", "Simetría activada: %s · centro %s",
                    describe(updated.mode()), updated.center().describe()).formatted(Formatting.AQUA));
            warnings(context.getSource(), updated);
            return updated.mode().images();
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int off(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        SymmetryService.update(player.getUuid(), settings -> settings.withActive(false));
        send(context.getSource(), tr("komaedit.symmetry.off", "Simetría desactivada").formatted(Formatting.GRAY));
        return 1;
    }

    private static int mode(CommandContext<ServerCommandSource> context, String text) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            SymmetryMode mode = SymmetryMode.parse(text, SymmetryService.settings(player.getUuid()).mode().arms());
            requireMode(player, mode);
            SymmetrySettings updated = SymmetryService.update(player.getUuid(), settings -> settings.withMode(mode));
            send(context.getSource(), tr("komaedit.symmetry.mode", "Modo: %s · %s copias por bloque",
                    describe(mode), mode.images()).formatted(Formatting.AQUA));
            warnings(context.getSource(), updated);
            return mode.images();
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int arms(CommandContext<ServerCommandSource> context, int arms) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            SymmetryMode mode = SymmetryService.settings(player.getUuid()).mode().withArms(arms);
            requireMode(player, mode);
            SymmetrySettings updated = SymmetryService.update(player.getUuid(), settings -> settings.withMode(mode));
            send(context.getSource(), tr("komaedit.symmetry.mode", "Modo: %s · %s copias por bloque",
                    describe(mode), mode.images()).formatted(Formatting.AQUA));
            warnings(context.getSource(), updated);
            return arms;
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int here(CommandContext<ServerCommandSource> context, String halfAxes) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        return center(context, player, halves -> SymmetryCenter.nearest(player.getX(), player.getY(), player.getZ(),
                halves[0], halves[1], halves[2]), halfAxes);
    }

    private static int position(CommandContext<ServerCommandSource> context, String halfAxes)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
        return center(context, player, halves -> SymmetryCenter.of(pos.getX(), pos.getY(), pos.getZ(),
                halves[0], halves[1], halves[2]), halfAxes);
    }

    private static int selection(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            Region region = WorldEditService.selection(player);
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            return center(context, player, halves -> SymmetryCenter.box(min.x(), min.y(), min.z(),
                    max.x(), max.y(), max.z()), "");
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Selecciona una región con WorldEdit (//pos1 y //pos2)");
        }
    }

    private static int center(CommandContext<ServerCommandSource> context, ServerPlayerEntity player,
                              Function<boolean[], SymmetryCenter> factory, String halfAxes) {
        try {
            boolean[] halves = halves(halfAxes);
            SymmetryCenter center = factory.apply(halves);
            String dimension = SymmetryService.dimension(player.getServerWorld());
            SymmetrySettings updated = SymmetryService.update(player.getUuid(),
                    settings -> settings.withCenter(center, dimension));
            send(context.getSource(), tr("komaedit.symmetry.center", "Centro: %s en %s", center.describe(), dimension)
                    .formatted(Formatting.AQUA));
            if (!updated.active()) {
                send(context.getSource(), Text.literal("  ").append(button(
                        tr("komaedit.symmetry.button.on", "[activar]"), ROOT + "on", Formatting.GREEN,
                        tr("komaedit.symmetry.hover.on", "Activa la simetría con este centro"), true)));
            }
            warnings(context.getSource(), updated);
            return 1;
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int worldEdit(CommandContext<ServerCommandSource> context, Boolean value)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        boolean enable = value == null ? !SymmetryService.settings(player.getUuid()).worldEdit() : value;
        if (enable && !KomaPermissions.can(player, SymmetryService.WORLDEDIT)) {
            return fail(context.getSource(), "Te falta el permiso " + SymmetryService.WORLDEDIT);
        }
        SymmetryService.update(player.getUuid(), settings -> settings.withWorldEdit(enable));
        send(context.getSource(), (enable
                ? tr("komaedit.symmetry.worldedit.on",
                "Tus ediciones de WorldEdit y KomaEdit también se reflejan, en el mismo //undo")
                : tr("komaedit.symmetry.worldedit.off", "Tus ediciones de WorldEdit y KomaEdit ya no se reflejan"))
                .formatted(Formatting.AQUA));
        return enable ? 1 : 0;
    }

    private static int info(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        SymmetrySettings settings = SymmetryService.settings(player.getUuid());
        MutableText state = settings.active()
                ? tr("komaedit.symmetry.state.on", "Simetría activada").formatted(Formatting.GREEN)
                : tr("komaedit.symmetry.state.off", "Simetría desactivada").formatted(Formatting.GRAY);
        send(context.getSource(), state.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(tr("komaedit.symmetry.mode", "Modo: %s · %s copias por bloque", describe(settings.mode()),
                        settings.mode().images()).formatted(Formatting.AQUA)));
        Text center = settings.center() == null
                ? tr("komaedit.symmetry.center.none", "sin fijar (se pondrá donde estés al activarla)")
                : tr("komaedit.symmetry.center.at", "%s en %s", settings.center().describe(), settings.world());
        context.getSource().sendFeedback(() -> Text.literal("  ").append(tr("komaedit.symmetry.info.center",
                "Centro: %s · ediciones de WorldEdit: %s", center, settings.worldEdit()
                        ? tr("komaedit.symmetry.yes", "sí") : tr("komaedit.symmetry.no", "no")))
                .formatted(Formatting.GRAY), false);
        MutableText buttons = Text.literal("  ").append(settings.active()
                ? button(tr("komaedit.symmetry.button.off", "[desactivar]"), ROOT + "off", Formatting.RED,
                tr("komaedit.symmetry.hover.off", "Deja de copiar lo que construyes"), true)
                : button(tr("komaedit.symmetry.button.on", "[activar]"), ROOT + "on", Formatting.GREEN,
                tr("komaedit.symmetry.hover.on", "Activa la simetría con este centro"), true));
        buttons.append(Text.literal(" ")).append(button(tr("komaedit.symmetry.button.here", "[centro aquí]"),
                ROOT + "center here", Formatting.WHITE,
                tr("komaedit.symmetry.hover.here", "Centro en el bloque donde estás"), true));
        buttons.append(Text.literal(" ")).append(button(tr("komaedit.symmetry.button.half", "[entre bloques]"),
                ROOT + "center here half", Formatting.WHITE,
                tr("komaedit.symmetry.hover.half", "Centro en el borde entre bloques más cercano"), true));
        buttons.append(Text.literal(" ")).append(button(tr("komaedit.symmetry.button.selection", "[selección]"),
                ROOT + "center selection", Formatting.WHITE,
                tr("komaedit.symmetry.hover.selection", "Centro en el centro de la selección de WorldEdit"), true));
        context.getSource().sendFeedback(() -> buttons, false);
        MutableText modes = Text.literal("  ").append(tr("komaedit.symmetry.modes", "Modo:").formatted(Formatting.GRAY));
        for (String mode : List.of("x", "z", "xz", "y", "radial " + settings.mode().arms())) {
            modes.append(Text.literal(" ")).append(button(Text.literal("[" + mode + "]"), ROOT + "mode " + mode,
                    settings.mode().id().equals(mode) ? Formatting.YELLOW : Formatting.WHITE,
                    describe(SymmetryMode.parse(mode, settings.mode().arms())), true));
        }
        modes.append(Text.literal(" ")).append(button(Text.literal("[WorldEdit]"), ROOT + "worldedit",
                settings.worldEdit() ? Formatting.YELLOW : Formatting.WHITE,
                tr("komaedit.symmetry.hover.worldedit", "Refleja también tus ediciones de WorldEdit y KomaEdit"), true));
        modes.append(Text.literal(" ")).append(button(tr("komaedit.symmetry.button.help", "[ayuda]"),
                "/komaedit help symmetry", Formatting.GRAY,
                tr("komaedit.symmetry.hover.help", "Todos los comandos de simetría"), true));
        context.getSource().sendFeedback(() -> modes, false);
        warnings(context.getSource(), settings);
        return settings.active() ? settings.mode().images() : 0;
    }

    private static void warnings(ServerCommandSource source, SymmetrySettings settings) {
        if (settings.center() == null) {
            return;
        }
        if (settings.mode().radial() && !settings.center().radialFriendly()) {
            send(source, tr("komaedit.symmetry.warn.radial", "Aviso: en radial el centro tiene que ser de bloque en X y Z "
                    + "a la vez, o entre bloques en los dos; si no, algunas copias se saltan bloques. Usa "
                    + "center here o center here half xz").formatted(Formatting.YELLOW));
        }
        if (source.getEntity() instanceof ServerPlayerEntity player
                && !settings.world().equals(SymmetryService.dimension(player.getServerWorld()))) {
            send(source, tr("komaedit.symmetry.warn.world", "La simetría está en %s y aquí no actúa; "
                    + "usa center here para traerla", settings.world()).formatted(Formatting.YELLOW));
        }
    }

    private static void requireMode(ServerPlayerEntity player, SymmetryMode mode) {
        if (!SymmetryService.canUse(player, mode)) {
            throw new IllegalArgumentException("Te falta el permiso "
                    + (mode.radial() ? SymmetryService.ROTATIONAL : SymmetryService.REFLECTIONAL));
        }
    }

    private static SymmetryCenter playerBlock(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        return SymmetryCenter.block(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Ejes donde el centro queda entre bloques: {@code ""} ninguno, {@code xz}, {@code xyz}… */
    private static boolean[] halves(String axes) {
        boolean[] halves = new boolean[3];
        for (char axis : axes.toLowerCase(Locale.ROOT).toCharArray()) {
            switch (axis) {
                case 'x' -> halves[0] = true;
                case 'y' -> halves[1] = true;
                case 'z' -> halves[2] = true;
                default -> throw new IllegalArgumentException("Eje desconocido: " + axis + " (usa x, y, z o xz)");
            }
        }
        return halves;
    }

    static MutableText describe(SymmetryMode mode) {
        if (mode.radial()) {
            return mode.mirrorY()
                    ? tr("komaedit.symmetry.mode.radial_y", "radial de %s brazos + espejo Y", mode.arms())
                    : tr("komaedit.symmetry.mode.radial", "radial de %s brazos", mode.arms());
        }
        return tr("komaedit.symmetry.mode.mirror", "espejo %s", mode.axes());
    }

    private static MutableText tr(String key, String fallback, Object... arguments) {
        return Text.translatableWithFallback(key, fallback, arguments);
    }

    private static MutableText button(Text label, String command, Formatting color, Text hover, boolean run) {
        return label.copy().styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND,
                        command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover.copy()
                        .append(Text.literal("\n" + command)).formatted(Formatting.GRAY))));
    }

    private static void send(ServerCommandSource source, Text text) {
        source.sendFeedback(() -> prefix().append(text), false);
    }

    private static MutableText prefix() {
        return Feedback.prefix();
    }

    private static int fail(ServerCommandSource source, String message) {
        return EzCommandSupport.fail(source, message);
    }
}
