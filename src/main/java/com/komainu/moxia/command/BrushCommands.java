package com.komainu.moxia.command;

import com.komainu.moxia.brush.BrushTool;
import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.brush.ez.EzBrush;
import com.komainu.moxia.brush.ez.EzBrushManager;
import com.komainu.moxia.brush.ez.EzBrushType;
import com.komainu.moxia.brush.superwand.SuperwandManager;
import com.komainu.moxia.brush.superwand.SuperwandMode;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.sculpt.SculptManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit pincel} (los pinceles de ezEdits sobre el sistema de brochas) y {@code /komaedit superwand}.
 * Cada pincel se liga al objeto de la mano, como los de WorldEdit; con la mano vacía se entrega una pluma.
 */
public final class BrushCommands {
    private static final SuggestionProvider<ServerCommandSource> TYPES = (context, builder) -> {
        for (EzBrushType type : EzBrushType.values()) {
            builder.suggest(type.id());
        }
        for (String action : new String[]{"aplicar", "cancelar", "quitar", "estado"}) {
            builder.suggest(action);
        }
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> OPTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        String type = StringArgumentType.getString(context, "pincel_tipo");
        String[] examples;
        try {
            examples = switch (EzBrushType.byId(type)) {
                case PALETTE_SHIFT -> new String[]{"##Grayscale", "stone,andesite,cobblestone", "8", "2", "-r", "-p"};
                case GRADIENT -> new String[]{"##Magma", "8", "tapered", "linear", "bezier", "0.5", "-a", "-w", "-v",
                        "-n", "Perlin(F:0.25)", "-z", "-d", "euclidean", "-p"};
                case GRADIENT_STROKE -> new String[]{"##Grayscale", "4", "linear", "tapered", "0.5", "-a", "-d",
                        "-w", "-x", "-n", "-z", "-p"};
                case PETAL_PAINTER -> new String[]{"petals", "wildflowers", "leaves", "8", "0.3", "-n",
                        "Perlin(F:0.05)", "-z", "-m", "#fullblock", "grass_block,moss_block", "-p"};
                case PLACE -> new String[]{"Clipboard", "Cl(O:C)", "Cl(R:90)", "Sh(S:Cone,P:stone)", "Constant",
                        "SurfaceNormal", "Aim", "Random", "ViewDiff", "C(D:east)", "-s", "-o", "-j", "-a", "-x", "-w",
                        "-p"};
                case SCATTER -> new String[]{"Clipboard", "Sc(N:arbol_.*)", "Ic(P:stone,R:0.6)", "Constant", "Random",
                        "SurfaceNormal", "-h", "Box(D:\"30,20,30\")", "-n", "-d", "up", "-e", "-m", "-u", "-s", "-o",
                        "-t"};
                case ARRAY -> new String[]{"Clipboard", "Sh(S:Cylinder,P:stone)", "Tangential", "Orthogonal",
                        "Constant", "-g", "-q", "-y", "-b", "-n", "-p", "-s"};
            };
        } catch (IllegalArgumentException exception) {
            examples = new String[0];
        }
        for (String example : examples) {
            tail.suggest(example);
        }
        return tail.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> WAND_OPTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        for (SuperwandMode mode : SuperwandMode.values()) {
            tail.suggest(mode.id());
        }
        tail.suggest("off");
        tail.suggest("-d");
        return tail.buildFuture();
    };

    private BrushCommands() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> brush(String name) {
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canAny(player, KomaPermissions.BUILD + ".brush"))
                .executes(BrushCommands::status)
                .then(argument("pincel_tipo", StringArgumentType.word())
                        .suggests(TYPES)
                        .executes(context -> configure(context, ""))
                        .then(argument("pincel_opciones", StringArgumentType.greedyString())
                                .suggests(OPTIONS)
                                .executes(context -> configure(context,
                                        StringArgumentType.getString(context, "pincel_opciones")))));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> superwand(String name) {
        return literal(name)
                .requires(source -> can(source, KomaPermissions.SELECT + ".superwand"))
                .executes(context -> superwand(context, ""))
                .then(argument("superwand_opciones", StringArgumentType.greedyString())
                        .suggests(WAND_OPTIONS)
                        .executes(context -> superwand(context,
                                StringArgumentType.getString(context, "superwand_opciones"))));
    }

    private static int configure(CommandContext<ServerCommandSource> context, String options)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String action = StringArgumentType.getString(context, "pincel_tipo");
        try {
            switch (BrushOptions.normalize(action)) {
                case "aplicar", "apply" -> {
                    int queued = SculptManager.apply(player);
                    feedback(context, Text.literal("Vista previa aplicada: " + queued + " bloques · //undo")
                            .formatted(Formatting.GREEN));
                    return 1;
                }
                case "cancelar", "cancel" -> {
                    if (!SculptManager.cancelPreview(player)) {
                        return fail(context, "No hay ninguna vista previa ni cálculo de pincel");
                    }
                    feedback(context, Text.literal("Vista previa descartada").formatted(Formatting.YELLOW));
                    return 1;
                }
                case "quitar", "off", "none", "ninguno" -> {
                    String id = BrushTool.unbind(player);
                    if (id == null || !EzBrushManager.unbind(player.getUuid(), id)) {
                        return fail(context, "El objeto de tu mano no tiene ningún pincel");
                    }
                    feedback(context, Text.literal("Pincel quitado de este objeto").formatted(Formatting.YELLOW));
                    return 1;
                }
                case "estado", "status" -> {
                    return status(context);
                }
                default -> {
                }
            }
            EzBrushType type = EzBrushType.byId(action);
            KomaPermissions.requireBuild(player, "brush." + type.id());
            EzBrush brush = EzBrush.create(player, type, options);
            String toolId = BrushTool.bind(player, BrushTool.Kind.BRUSH, Items.FEATHER, "Pincel de KomaEdit");
            EzBrushManager.bind(player, toolId, brush);
            MutableText text = Text.literal(type.displayName() + " en la mano"
                    + (brush.radius() > 0 ? " · radio " + brush.radius() : "")
                    + (brush.preview() ? " · con vista previa" : "") + ". ").formatted(Formatting.GREEN)
                    .append(Text.literal(brush.controls()).formatted(Formatting.GRAY));
            if (brush.preview()) {
                text.append(Text.literal(" ")).append(button("[APLICAR]", "/komaedit pincel aplicar",
                        Formatting.GREEN));
            }
            feedback(context, text);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context, exception.getMessage());
        }
    }

    private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String id = BrushTool.id(player.getMainHandStack());
        var brush = id == null ? java.util.Optional.<EzBrush>empty() : EzBrushManager.brush(player.getUuid(), id);
        if (brush.isEmpty()) {
            feedback(context, Text.literal("Pinceles: paletteshift, gradient, gradientstroke, petalpainter, place,"
                    + " scatter, array. Ejemplo: /komaedit pincel gradient ##Magma 8 tapered 0.6")
                    .formatted(Formatting.GRAY));
            return 1;
        }
        EzBrush current = brush.get();
        feedback(context, Text.literal(current.type().displayName() + (current.radius() > 0 ? " · radio "
                + current.radius() : "") + " · " + current.controls()).formatted(Formatting.GRAY));
        return 1;
    }

    private static int superwand(CommandContext<ServerCommandSource> context, String input)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            BrushOptions options = BrushOptions.parse(input, Set.of(), Set.of('d'));
            String first = options.positional(0);
            if (first != null && (first.equalsIgnoreCase("off") || first.equalsIgnoreCase("quitar"))) {
                BrushTool.unbind(player);
                SuperwandManager.deactivate(player.getUuid());
                feedback(context, Text.literal("Supervarita desactivada").formatted(Formatting.YELLOW));
                return 1;
            }
            SuperwandMode mode = first == null ? SuperwandMode.TRACE : SuperwandMode.byId(first);
            double distance = options.flagDecimal('d', 0.0, 0.0, 512.0, "La distancia del modo relativo");
            BrushTool.bind(player, BrushTool.Kind.SUPERWAND, Items.BLAZE_ROD, "Supervarita");
            SuperwandManager.activate(player, mode, distance);
            feedback(context, Text.literal("Supervarita en modo " + mode.displayName() + " · "
                    + SuperwandManager.points(player.getUuid()).size() + " punto(s) de tu selección. ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal("Clic derecho: punto o agarrar · clic izquierdo: quita el último o suelta"
                            + " · Mayús + izquierdo: cambia de modo · F: invierte el orden · Mayús + F: desplaza"
                            + " los puntos").formatted(Formatting.GRAY)));
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context, exception.getMessage());
        }
    }

    private static boolean can(ServerCommandSource source, String permission) {
        return source.getEntity() instanceof ServerPlayerEntity player && KomaPermissions.can(player, permission);
    }

    private static void feedback(CommandContext<ServerCommandSource> context, Text text) {
        context.getSource().sendFeedback(() -> Text.literal("KomaEdit · ").formatted(Formatting.GOLD).append(text),
                false);
    }

    private static int fail(CommandContext<ServerCommandSource> context, String message) {
        context.getSource().sendError(Text.literal("KomaEdit · ").formatted(Formatting.GOLD).append(Text.literal(
                message == null || message.isBlank() ? "Operación inválida" : message).formatted(Formatting.RED)));
        return 0;
    }

    private static MutableText button(String label, String command, Formatting color) {
        return Text.literal(label).styled(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}
