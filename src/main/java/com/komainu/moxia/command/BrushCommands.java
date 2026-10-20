package com.komainu.moxia.command;

import com.komainu.moxia.brush.BrushTool;
import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.brush.ez.EzBrush;
import com.komainu.moxia.brush.ez.EzBrushManager;
import com.komainu.moxia.brush.ez.EzBrushType;
import com.komainu.moxia.brush.superwand.SuperwandManager;
import com.komainu.moxia.brush.superwand.SuperwandMode;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.sculpt.model.ModelMode;
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
                case TREE -> new String[]{"oak", "spruce", "birch", "jungle", "acacia", "cherry", "willow", "dark_oak",
                        "dead", "palm", "baobab", "fantasy", "18", "-g", "-b", "-d", "-w", "-i", "-c", "-x", "-t", "-h",
                        "-r", "-m", "-s", "-p"};
                case ROCK -> new String[]{"boulder", "crag", "spire", "5", "-u", "-b", "-a", "-e", "-r", "-g", "-c",
                        "-h", "-i", "-x", "-m", "-s", "-p"};
                case BIOME -> new String[]{"cherry_grove", "snowy_plains", "desert", "8", "16", "-v", "-m",
                        "grass_block"};
                case HEIGHTMAP -> heightmapExamples();
                case STAMP -> stampExamples(context.getSource());
                case SCULPT -> sculptExamples(remaining);
                case PAINT -> paintExamples(remaining);
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

    /** Sugerencias del pincel «estampar»: los planos y etiquetas del jugador, el radio y las banderas. */
    private static String[] stampExamples(ServerCommandSource source) {
        java.util.List<String> examples = new java.util.ArrayList<>();
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            examples.addAll(com.komainu.moxia.command.BlueprintCommands.references(player));
            examples.addAll(com.komainu.moxia.command.BlueprintCommands.tags(player));
        }
        examples.addAll(java.util.List.of("0", "6", "12", "-n", "60", "-d", "-c", "-r", "azar", "mirada", "-f", "xz",
                "no", "-g", "apoyar", "centro", "-y", "-1", "-m", "grass_block", "-a", "-p"));
        return examples.toArray(String[]::new);
    }

    /** Sugerencias del pincel de modelado: los modos primero; después números y banderas. */
    private static String[] sculptExamples(String remaining) {
        if (!remaining.contains(" ")) {
            return new String[]{"draw", "rock", "weld", "melt", "roughen", "shatter", "esculpir", "roca", "soldar",
                    "fundir", "rugosidad", "grietas"};
        }
        return new String[]{"6", "8", "12", "2", "0.4", "-f", "smooth", "constant", "-m", "#solid", "-b",
                "stone,andesite,tuff", "air", "-s", "-g", "-k", "-z", "-w", "-e", "y", "3d", "-d", "-i", "-n",
                "-p"};
    }

    /** Sugerencias del pincel de pintura: sus paletas y degradados primero; después números, huellas y banderas. */
    private static String[] paintExamples(String remaining) {
        java.util.List<String> examples = new java.util.ArrayList<>(
                com.komainu.moxia.brush.paint.PaintPalettes.descriptions().keySet());
        examples.addAll(java.util.List.of("##Grayscale", "pendiente", "altura", "ruido", "huella", "8", "12", "60"));
        if (remaining.contains(" ")) {
            examples.addAll(java.util.List.of("-h", "-s", "-k", "-e", "-u", "-j", "-d", "-t", "-c", "-x", "-b", "-m",
                    "-y", "60..120", "-a", "30..65", "-n", "clouds", "-z", "-p"));
            examples.addAll(com.komainu.moxia.brush.paint.PaintBrush.footprints().names());
        }
        return examples.toArray(String[]::new);
    }

    /** Sugerencias del pincel de relieve: los sellos que hay, los modos, las paletas y las banderas. */
    private static String[] heightmapExamples() {
        java.util.List<String> examples = new java.util.ArrayList<>(
                com.komainu.moxia.terrain.TerrainService.catalog().names());
        examples.addAll(java.util.List.of("12", "16", "sumar", "restar", "maximo", "aplanar", "suavizar", "auto"));
        examples.addAll(com.komainu.moxia.terrain.SurfacePalette.presets().keySet());
        examples.addAll(java.util.List.of("-i", "-r", "mirada", "azar", "-x", "60", "-s", "-k", "-m", "-c", "-p"));
        return examples.toArray(String[]::new);
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
                    feedback(context, Text.literal("Vista previa aplicada: " + ProgressLine.group(queued) + " bloques · //undo")
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
                case "actualizar", "update" -> {
                    return update(player, options);
                }
                default -> {
                }
            }
            EzBrushType type;
            try {
                type = EzBrushType.byId(action);
            } catch (IllegalArgumentException unknown) {
                // Atajo: el modo de modelado como pincel («/komaedit pincel soldar 8»).
                try {
                    ModelMode.byId(action);
                } catch (IllegalArgumentException notModel) {
                    throw unknown;
                }
                type = EzBrushType.SCULPT;
                options = action + " " + options;
            }
            KomaPermissions.requireBuild(player, "brush." + type.id());
            EzBrush brush = EzBrush.create(player, type, options);
            String toolId = BrushTool.bind(player, BrushTool.Kind.BRUSH, Items.FEATHER, "Pincel de KomaEdit");
            EzBrushManager.bind(player, toolId, brush);
            BrushTool.setRadius(player, brush.radius());
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

    /**
     * {@code pincel actualizar <tipo> <opciones>}: lo que manda el Studio al cambiar un ajuste del pincel que hizo.
     * Solo cambia el pincel que ya está en la mano y es de ese tipo (nunca marca ni entrega otro objeto) y lo dice en
     * la barra de acción, sin llenar el chat.
     */
    private static int update(ServerPlayerEntity player, String input) {
        String[] parts = input.strip().split("\\s+", 2);
        EzBrushType type = EzBrushType.byId(parts[0]);
        String toolId = BrushTool.id(player.getMainHandStack());
        java.util.Optional<EzBrush> current = BrushTool.kind(toolId) == BrushTool.Kind.BRUSH
                ? EzBrushManager.brush(player.getUuid(), toolId) : java.util.Optional.empty();
        if (current.isEmpty() || current.get().type() != type) {
            Feedback.statusWarn(player, Text.literal("No tienes en la mano el pincel de " + type.displayName()
                    + ": los cambios del Studio no se le pasan"));
            return 0;
        }
        KomaPermissions.requireBuild(player, "brush." + type.id());
        EzBrush brush = EzBrush.create(player, type, parts.length > 1 ? parts[1] : "").adopt(current.get());
        EzBrushManager.bind(player, toolId, brush);
        BrushTool.setRadius(player, brush.radius());
        Feedback.status(player, type.displayName() + " al día" + (brush.radius() > 0 ? " · radio " + brush.radius()
                : "") + (brush.preview() ? " · con vista previa" : " · sin vista previa"));
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String id = BrushTool.id(player.getMainHandStack());
        var brush = id == null ? java.util.Optional.<EzBrush>empty() : EzBrushManager.brush(player.getUuid(), id);
        if (brush.isEmpty()) {
            feedback(context, Text.literal("Pinceles: paletteshift, gradient, gradientstroke, petalpainter, place,"
                    + " scatter, array, tree, rock, biome, heightmap, stamp, sculpt (draw, rock, weld, melt, roughen,"
                    + " shatter), paint (pintar). Ejemplo: /komaedit pincel pintar")
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
        context.getSource().sendFeedback(() -> Feedback.prefix().append(text),
                false);
    }

    private static int fail(CommandContext<ServerCommandSource> context, String message) {
        return EzCommandSupport.fail(context.getSource(), message);
    }

    private static MutableText button(String label, String command, Formatting color) {
        return Text.literal(label).styled(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}
