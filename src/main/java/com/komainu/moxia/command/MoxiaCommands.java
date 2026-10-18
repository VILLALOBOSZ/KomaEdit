package com.komainu.moxia.command;

import com.komainu.moxia.brush.BrushManager;
import com.komainu.moxia.brush.BrushSettings;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.PendingBuilds;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.edit.SmoothBlockStates;
import com.komainu.moxia.shape.smooth.PieceMaterials;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.gradient.GradientAxis;
import com.komainu.moxia.gradient.GradientPalette;
import com.komainu.moxia.gradient.GradientService;
import com.komainu.moxia.ivy.IvyService;
import com.komainu.moxia.ivy.IvySettings;
import com.komainu.moxia.ivy.IvyStyle;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathHologramSync;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.persistence.ProjectStorage;
import com.komainu.moxia.persistence.SculptPresetStore;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeBuildService;
import com.komainu.moxia.recipe.RecipeExtensions;
import com.komainu.moxia.recipe.StructureBendService;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.SignWax;
import com.komainu.moxia.region.TweakOperation;
import com.komainu.moxia.region.TweakRules;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.ShapeExpression;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.tube.TubeBender;
import com.komainu.moxia.shape.tube.TubeCommandParser;
import com.komainu.moxia.shape.tube.TubeFormulaPreset;
import com.komainu.moxia.shape.tube.TubeSettings;
import com.komainu.moxia.sculpt.BrushFalloff;
import com.komainu.moxia.sculpt.BrushMask;
import com.komainu.moxia.sculpt.SculptBrushType;
import com.komainu.moxia.sculpt.SculptManager;
import com.komainu.moxia.sculpt.SculptPreviewManager;
import com.komainu.moxia.sculpt.SculptProfile;
import com.komainu.moxia.sculpt.SculptSettings;
import com.komainu.moxia.weathering.WeatheringService;
import com.komainu.moxia.weathering.WeatheringSettings;
import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.ui.QuickBuildPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.math.BlockVector3;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MoxiaCommands {
    private static final String LIGHTNING_PATTERN =
            "45%light_blue_concrete,25%white_concrete,20%sea_lantern,10%verdant_froglight";
    private static final SuggestionProvider<ServerCommandSource> SHAPE_SUGGESTIONS = (context, builder) -> {
        Arrays.stream(PathShape.values()).forEach(shape -> builder.suggest(shape.id(), Text.literal(shape.displayName())));
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> FIGURE_SUGGESTIONS = (context, builder) -> {
        Arrays.stream(PathShape.values()).filter(PathShape::isFigure)
                .forEach(shape -> builder.suggest(shape.id(), Text.literal(shape.displayName())));
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> PROFILE_SUGGESTIONS = (context, builder) -> {
        Arrays.stream(TubeSettings.TubeProfile.values())
                .forEach(profile -> builder.suggest(
                        profile.usesSides() ? profile.id() + "6" : profile.id(),
                        Text.literal(profile.displayName() + (profile.usesSides() ? " · el número son los lados" : ""))
                ));
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> TUBE_FLAG_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        var tail = builder.createOffset(builder.getStart() + lastSpace + 1);
        for (String flag : new String[]{"-e round", "-e spike", "-e soft", "-e cube", "-n horizontal",
                "-n upright", "-t 90", "-r 0,180", "-p 0:0:0", "-q high", "-hollow 1", "-s 2", "-w SlabsAndStairs",
                "-w Slabs(C:0.7)", "-w Layers", "-w SSW", "-w SlabsAndStairs2D", "-c 0.5"}) {
            tail.suggest(flag);
        }
        return tail.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> BEND_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        for (String option : new String[]{"repeat", "stretch", "-n upright", "-n horizontal", "-g 1", "-a x", "-a z",
                "-y -1", "-p 0:0:0"}) {
            tail.suggest(option);
        }
        return tail.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> WEATHER_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        for (String option : new String[]{"30", "60", "90", "templado", "desierto", "nevado", "hundido", "-d 50",
                "-v 50", "-seed 7", "-agua"}) {
            tail.suggest(option);
        }
        return tail.buildFuture();
    };
    /** Al empezar, las fórmulas de serie, un ejemplo y las banderas; luego solo banderas, que van delante. */
    private static final SuggestionProvider<ServerCommandSource> EXPRESSION_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        var tail = builder.createOffset(builder.getStart() + lastSpace + 1);
        for (TubeFormulaPreset preset : TubeFormulaPreset.values()) {
            tail.suggest(preset.id());
        }
        tail.suggest("r<1&&abs(sin(a*3+z))>0.4");
        for (String flag : new String[]{"-e round", "-e spike", "-n horizontal", "-n upright", "-t 90",
                "-q high", "-hollow 1", "-s 2", "-seed 7", "-w SlabsAndStairs", "-w Slabs", "-w Layers"}) {
            tail.suggest(flag);
        }
        return tail.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> IVY_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        if (remaining.isBlank()) {
            tail.suggest("cancel");
        }
        for (IvyStyle style : IvyStyle.values()) {
            tail.suggest(style.id());
        }
        for (String flag : new String[]{"-l 28", "-l 60", "-b 0.4", "-g 0.5", "-g 1", "-n 3", "-seed 7"}) {
            tail.suggest(flag);
        }
        return tail.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> PALETTE_SUGGESTIONS = (context, builder) -> {
        Arrays.stream(GradientPalette.values())
                .forEach(palette -> builder.suggest(palette.id(), Text.literal(palette.displayName())));
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> SCULPT_SUGGESTIONS = (context, builder) -> {
        Arrays.stream(SculptBrushType.values()).map(SculptBrushType::id).forEach(builder::suggest);
        return builder.buildFuture();
    };
    private static final SuggestionProvider<ServerCommandSource> FALLOFF_SUGGESTIONS = (context, builder) -> {
        Arrays.stream(BrushFalloff.values()).map(BrushFalloff::id).forEach(builder::suggest);
        return builder.buildFuture();
    };
    private MoxiaCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = dispatcher.register(literal("komaedit")
                .requires(MoxiaCommands::canUse)
                .then(literal("menu").executes(MoxiaCommands::openHudMenu))
                .then(literal("quick").executes(MoxiaCommands::openHudMenu))
                .then(literal("gui").executes(MoxiaCommands::guiHint))
                .then(literal("shapes").executes(MoxiaCommands::openHudMenu))
                .then(generationCommand("build", true))
                .then(generationCommand("estimate", false))
                .then(generationCommand("estimar", false))
                .then(confirmCommand("confirm"))
                .then(confirmCommand("confirmar"))
                .then(PathCommands.command().requires(source -> can(source, KomaPermissions.PATH)))
                .then(bridgeCommand())
                .then(pillarCommand())
                .then(pillarArchesCommand())
                .then(toriiCommand())
                .then(cloudCommand("cloud"))
                .then(cloudCommand("nube"))
                .then(mandalaCommand())
                .then(webCommand())
                .then(lightningCommand("lightning"))
                .then(lightningCommand("rayos"))
                .then(lightningCircleCommand("lightningcircle"))
                .then(lightningCircleCommand("circulorayos"))
                .then(noodlesCommand())
                .then(bendCommand("bend"))
                .then(bendCommand("doblar"))
                .then(weatherCommand("weather"))
                .then(weatherCommand("envejecer"))
                .then(sweepCommand("sweep"))
                .then(sweepCommand("perfil"))
                .then(tubeCommand("braid", PathShape.BRAID))
                .then(tubeCommand("trenza", PathShape.BRAID))
                .then(tubeCommand("noisetube", PathShape.NOISE_TUBE))
                .then(tubeCommand("erosion", PathShape.NOISE_TUBE))
                .then(expressionCommand("expr"))
                .then(expressionCommand("expresion"))
                .then(EzSplineCommands.command("ezspline"))
                .then(EzSplineCommands.command("ezsp"))
                .then(EzSplineCommands.command("spline"))
                .then(ivyCommand("ivy"))
                .then(ivyCommand("hiedra"))
                .then(BrushCommands.brush("pincel"))
                .then(BrushCommands.brush("ezbrush"))
                .then(BrushCommands.superwand("superwand"))
                .then(BrushCommands.superwand("supervarita"))
                .then(jobCommand())
                .then(projectCommand())
                .then(gradientCommand().requires(source -> can(source, KomaPermissions.BUILD)))
                .then(tweakCommand("tweak").requires(MoxiaCommands::canTweak))
                .then(tweakCommand("retocar").requires(MoxiaCommands::canTweak))
                .then(tweakCommand("survival").requires(MoxiaCommands::canTweak))
                .then(HelpCommands.command("help"))
                .then(HelpCommands.command("ayuda"))
                .executes(MoxiaCommands::openHudMenu));
        DeformCommands.register(root);
        PlacementCommands.register(root);
        TextureCommands.register(dispatcher);
        ImageCommands.register(dispatcher);
        GeneratorCommands.register(dispatcher);
        PaletteCommands.register(root);
        ColorCommands.register(root);
        NoiseCommands.register(root);
        SelectionCommands.register(root);
        AdminCommands.register(root);
        SymmetryCommands.register(root);
        BuilderCommands.register(root);
        PreviewCommands.register(root);
        // «ruta» es el mismo nodo que «path»: hereda todos sus subcomandos y sugerencias.
        dispatcher.register(literal("komaedit").then(literal("ruta")
                .requires(source -> can(source, KomaPermissions.PATH))
                .executes(PathCommands::status)
                .redirect(root.getChild("path"))));
        dispatcher.register(literal("ke").requires(MoxiaCommands::canUse).redirect(root));
        dispatcher.register(literal("moxia").requires(MoxiaCommands::canUse).redirect(root));
        dispatcher.register(literal("mx").requires(MoxiaCommands::canUse).redirect(root));
        // Siempre lo último: la bandera -v solo llega a las órdenes registradas antes.
        PreviewCommands.installFlags(dispatcher, root);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> bridgeCommand() {
        return literal("bridge").then(bridgeArguments());
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Integer> bridgeArguments() {
        RequiredArgumentBuilder<ServerCommandSource, Integer> height = argument(
                "bridge_height", IntegerArgumentType.integer(3, 64)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> width = argument(
                "bridge_width", IntegerArgumentType.integer(3, 64)
        ).executes(context -> generateBridge(
                context,
                "stone",
                IntegerArgumentType.getInteger(context, "bridge_height"),
                IntegerArgumentType.getInteger(context, "bridge_width")
        ));
        width.then(argument("bridge_pattern", StringArgumentType.greedyString())
                .executes(context -> generateBridge(
                        context,
                        StringArgumentType.getString(context, "bridge_pattern"),
                        IntegerArgumentType.getInteger(context, "bridge_height"),
                        IntegerArgumentType.getInteger(context, "bridge_width")
                )));
        height.then(width);
        return height;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> pillarCommand() {
        RequiredArgumentBuilder<ServerCommandSource, Integer> radius = argument(
                "pillar_radius", IntegerArgumentType.integer(3, 64)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> detail = argument(
                "pillar_detail", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generateFixed(
                context,
                true,
                PathShape.PILLAR,
                "stone",
                IntegerArgumentType.getInteger(context, "pillar_radius"),
                IntegerArgumentType.getInteger(context, "pillar_detail"),
                2,
                0
        ));
        detail.then(argument("pillar_pattern", StringArgumentType.greedyString())
                .executes(context -> generateFixed(
                        context,
                        true,
                        PathShape.PILLAR,
                        StringArgumentType.getString(context, "pillar_pattern"),
                        IntegerArgumentType.getInteger(context, "pillar_radius"),
                        IntegerArgumentType.getInteger(context, "pillar_detail"),
                        2,
                        0
                )));
        radius.then(detail);
        return literal("pillar").then(radius);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> pillarArchesCommand() {
        RequiredArgumentBuilder<ServerCommandSource, Integer> height = argument(
                "pillar_arches_height", IntegerArgumentType.integer(8, 128)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> radius = argument(
                "pillar_arches_radius", IntegerArgumentType.integer(3, 64)
        ).executes(context -> generatePillarArches(
                context,
                "stone",
                IntegerArgumentType.getInteger(context, "pillar_arches_height"),
                IntegerArgumentType.getInteger(context, "pillar_arches_radius")
        ));
        radius.then(argument("pillar_arches_pattern", StringArgumentType.greedyString())
                .executes(context -> generatePillarArches(
                        context,
                        StringArgumentType.getString(context, "pillar_arches_pattern"),
                        IntegerArgumentType.getInteger(context, "pillar_arches_height"),
                        IntegerArgumentType.getInteger(context, "pillar_arches_radius")
                )));
        height.then(radius);
        return literal("pillararches").then(height);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> toriiCommand() {
        RequiredArgumentBuilder<ServerCommandSource, Integer> height = argument(
                "torii_height", IntegerArgumentType.integer(16, 256)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> radius = argument(
                "torii_radius", IntegerArgumentType.integer(3, 48)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> detail = argument(
                "torii_detail", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generateTorii(
                context,
                "red_concrete",
                IntegerArgumentType.getInteger(context, "torii_height"),
                IntegerArgumentType.getInteger(context, "torii_radius"),
                IntegerArgumentType.getInteger(context, "torii_detail")
        ));
        detail.then(argument("torii_pattern", StringArgumentType.greedyString())
                .executes(context -> generateTorii(
                        context,
                        StringArgumentType.getString(context, "torii_pattern"),
                        IntegerArgumentType.getInteger(context, "torii_height"),
                        IntegerArgumentType.getInteger(context, "torii_radius"),
                        IntegerArgumentType.getInteger(context, "torii_detail")
                )));
        radius.then(detail);
        height.then(radius);
        return literal("torii").then(height);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> cloudCommand(String commandName) {
        String naturalCloud = "70%white_wool,20%calcite,10%light_gray_wool";
        RequiredArgumentBuilder<ServerCommandSource, Integer> radius = argument(
                "cloud_radius", IntegerArgumentType.integer(4, 64)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> organicity = argument(
                "cloud_organicity", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generateCloud(
                context,
                naturalCloud,
                IntegerArgumentType.getInteger(context, "cloud_radius"),
                IntegerArgumentType.getInteger(context, "cloud_organicity"),
                12
        ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> gap = argument(
                "cloud_gap", IntegerArgumentType.integer(3, 128)
        ).executes(context -> generateCloud(
                context,
                naturalCloud,
                IntegerArgumentType.getInteger(context, "cloud_radius"),
                IntegerArgumentType.getInteger(context, "cloud_organicity"),
                IntegerArgumentType.getInteger(context, "cloud_gap")
        ));
        gap.then(argument("cloud_gap_pattern", StringArgumentType.greedyString())
                .executes(context -> generateCloud(
                        context,
                        StringArgumentType.getString(context, "cloud_gap_pattern"),
                        IntegerArgumentType.getInteger(context, "cloud_radius"),
                        IntegerArgumentType.getInteger(context, "cloud_organicity"),
                        IntegerArgumentType.getInteger(context, "cloud_gap")
                )));
        organicity.then(gap);
        organicity.then(argument("cloud_pattern", StringArgumentType.greedyString())
                .executes(context -> generateCloud(
                        context,
                        StringArgumentType.getString(context, "cloud_pattern"),
                        IntegerArgumentType.getInteger(context, "cloud_radius"),
                        IntegerArgumentType.getInteger(context, "cloud_organicity"),
                        12
                )));
        radius.then(organicity);
        return literal(commandName).then(radius);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> mandalaCommand() {
        RequiredArgumentBuilder<ServerCommandSource, Integer> radius = argument(
                "mandala_radius", IntegerArgumentType.integer(6, 96)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> symmetry = argument(
                "mandala_symmetry", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generateFixed(
                context,
                true,
                PathShape.MANDALA,
                "quartz_block",
                IntegerArgumentType.getInteger(context, "mandala_radius"),
                IntegerArgumentType.getInteger(context, "mandala_symmetry"),
                2,
                0
        ));
        symmetry.then(argument("mandala_pattern", StringArgumentType.greedyString())
                .executes(context -> generateFixed(
                        context,
                        true,
                        PathShape.MANDALA,
                        StringArgumentType.getString(context, "mandala_pattern"),
                        IntegerArgumentType.getInteger(context, "mandala_radius"),
                        IntegerArgumentType.getInteger(context, "mandala_symmetry"),
                        2,
                        0
                )));
        radius.then(symmetry);
        return literal("mandala").then(radius);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> webCommand() {
        RequiredArgumentBuilder<ServerCommandSource, Integer> elasticity = argument(
                "web_elasticity", IntegerArgumentType.integer(3, 64)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> spacing = argument(
                "web_spacing", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generateFixed(
                context,
                true,
                PathShape.WEB,
                "white_wool",
                IntegerArgumentType.getInteger(context, "web_elasticity"),
                IntegerArgumentType.getInteger(context, "web_spacing"),
                1,
                0
        ));
        spacing.then(argument("web_pattern", StringArgumentType.greedyString())
                .executes(context -> generateFixed(
                        context,
                        true,
                        PathShape.WEB,
                        StringArgumentType.getString(context, "web_pattern"),
                        IntegerArgumentType.getInteger(context, "web_elasticity"),
                        IntegerArgumentType.getInteger(context, "web_spacing"),
                        1,
                        0
                )));
        elasticity.then(spacing);
        return literal("web").then(elasticity);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> lightningCommand(String commandName) {
        RequiredArgumentBuilder<ServerCommandSource, Integer> amplitude = argument(
                commandName + "_amplitude", IntegerArgumentType.integer(2, 32)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> branching = argument(
                commandName + "_branching", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generateFixed(
                context,
                true,
                PathShape.LIGHTNING,
                LIGHTNING_PATTERN,
                IntegerArgumentType.getInteger(context, commandName + "_amplitude"),
                IntegerArgumentType.getInteger(context, commandName + "_branching"),
                1,
                45
        ));
        branching.then(argument(commandName + "_pattern", StringArgumentType.greedyString())
                .executes(context -> generateFixed(
                        context,
                        true,
                        PathShape.LIGHTNING,
                        StringArgumentType.getString(context, commandName + "_pattern"),
                        IntegerArgumentType.getInteger(context, commandName + "_amplitude"),
                        IntegerArgumentType.getInteger(context, commandName + "_branching"),
                        1,
                        45
                )));
        amplitude.then(branching);
        return literal(commandName).then(amplitude);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> lightningCircleCommand(String commandName) {
        RequiredArgumentBuilder<ServerCommandSource, Integer> radius = argument(
                commandName + "_radius", IntegerArgumentType.integer(4, 128)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> branching = argument(
                commandName + "_branching", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generateLightningCircle(
                context,
                LIGHTNING_PATTERN,
                IntegerArgumentType.getInteger(context, commandName + "_radius"),
                IntegerArgumentType.getInteger(context, commandName + "_branching")
        ));
        branching.then(argument(commandName + "_pattern", StringArgumentType.greedyString())
                .executes(context -> generateLightningCircle(
                        context,
                        StringArgumentType.getString(context, commandName + "_pattern"),
                        IntegerArgumentType.getInteger(context, commandName + "_radius"),
                        IntegerArgumentType.getInteger(context, commandName + "_branching")
                )));
        radius.then(branching);
        return literal(commandName).then(radius);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> noodlesCommand() {
        return literal("noodles").then(noodlesArguments());
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Integer> noodlesArguments() {
        String texture = "20%blackstone,20%warped_wart_block,20%cyan_terracotta,20%prismarine_bricks,10%sea_lantern,10%verdant_froglight";
        RequiredArgumentBuilder<ServerCommandSource, Integer> radius = argument(
                "noodles_radius", IntegerArgumentType.integer(2, 64)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> flow = argument(
                "noodles_spacing", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generateFixed(
                context,
                true,
                PathShape.NOODLES,
                texture,
                IntegerArgumentType.getInteger(context, "noodles_radius"),
                IntegerArgumentType.getInteger(context, "noodles_spacing"),
                2,
                0
        ));
        flow.then(argument("noodles_pattern", StringArgumentType.greedyString())
                .executes(context -> generateFixed(
                        context,
                        true,
                        PathShape.NOODLES,
                        StringArgumentType.getString(context, "noodles_pattern"),
                        IntegerArgumentType.getInteger(context, "noodles_radius"),
                        IntegerArgumentType.getInteger(context, "noodles_spacing"),
                        2,
                        0
                )));
        radius.then(flow);
        return radius;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> sweepCommand(String name) {
        RequiredArgumentBuilder<ServerCommandSource, String> profile = argument(
                "sweep_profile", StringArgumentType.word()
        ).suggests(PROFILE_SUGGESTIONS);
        profile.then(tubeArguments(PathShape.SWEEP, "sweep_profile", "stone"));
        return literal(name).then(profile);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> tubeCommand(String name, PathShape shape) {
        String pattern = shape == PathShape.BRAID
                ? "stripped_birch_wood"
                : "60%stone,25%andesite,15%cobblestone";
        return literal(name).then(tubeArguments(shape, null, pattern));
    }

    /** {@code <radios> [patrón y banderas]}: los radios admiten fotogramas, p. ej. {@code 2,0.3:12,2}. */
    private static RequiredArgumentBuilder<ServerCommandSource, String> tubeArguments(
            PathShape shape,
            String profileArgument,
            String defaultPattern
    ) {
        RequiredArgumentBuilder<ServerCommandSource, String> radii = argument(
                "tube_radii", StringArgumentType.string()
        ).executes(context -> generateTube(context, shape, profileArgument, defaultPattern, ""));
        radii.then(argument("tube_options", StringArgumentType.greedyString())
                .suggests(TUBE_FLAG_SUGGESTIONS)
                .executes(context -> generateTube(context, shape, profileArgument, defaultPattern,
                        StringArgumentType.getString(context, "tube_options"))));
        return radii;
    }

    private static int generateTube(
            CommandContext<ServerCommandSource> context,
            PathShape shape,
            String profileArgument,
            String defaultPattern,
            String options
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            TubeCommandParser.Result parsed = TubeCommandParser.parse(
                    shape,
                    profileArgument == null ? null : StringArgumentType.getString(context, profileArgument),
                    StringArgumentType.getString(context, "tube_radii"),
                    options,
                    defaultPattern
            );
            return enqueueShape(context, player, true, parsed.pattern(), parsed.parameters(), parsed.materials());
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca al menos dos puntos con P o completa //pos1 y //pos2");
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    /** {@code /komaedit bend [repeat|stretch] [banderas]}: dobla el portapapeles a lo largo de la trayectoria. */
    private static LiteralArgumentBuilder<ServerCommandSource> bendCommand(String name) {
        return literal(name)
                .executes(context -> generateBend(context, ""))
                .then(argument("bend_options", StringArgumentType.greedyString())
                        .suggests(BEND_SUGGESTIONS)
                        .executes(context -> generateBend(context,
                                StringArgumentType.getString(context, "bend_options"))));
    }

    private static int generateBend(CommandContext<ServerCommandSource> context, String options)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            KomaPermissions.requireBuild(player, "bend");
            KomaPermissions.require(player, KomaPermissions.CLIPBOARD);
            TubeBender.Settings settings = TubeBender.Settings.parse(options);
            StructureBendService.Captured captured = StructureBendService.capture(player);
            PathSnapshot pathSnapshot = WorldEditService.resolvePath(player);
            UUID playerId = player.getUuid();
            var server = player.getServer();
            String axis = TubeBender.resolvedAxis(captured.template(), settings.axis()).id();
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal("Doblando " + captured.blocks()
                            + " bloques del portapapeles; avanza por su eje " + axis
                            + " (cámbialo con -a x|z|-x|-z)…").formatted(Formatting.GRAY)),
                    false
            );
            AsyncEngine.submit(
                    playerId,
                    () -> StructureBendService.plan(captured, pathSnapshot, settings),
                    plan -> server.execute(() -> {
                        ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                        if (current == null) {
                            return;
                        }
                        try {
                            int changed = BatchEditService.submit(current, plan, "air", "Doblar estructura");
                            if (PlanPreviews.holds(playerId, plan)) {
                                return;
                            }
                            current.getCommandSource().sendFeedback(
                                    () -> prefix().append(Text.literal("Estructura doblada en cola: " + changed
                                            + " bloques · /komaedit job status").formatted(Formatting.GREEN)),
                                    false
                            );
                        } catch (IllegalArgumentException | IllegalStateException exception) {
                            fail(current.getCommandSource(), message(exception));
                        }
                    }),
                    throwable -> server.execute(() -> {
                        ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                        if (current != null) {
                            fail(current.getCommandSource(), throwableMessage(throwable));
                        }
                    })
            );
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca al menos dos puntos con P o completa //pos1 y //pos2");
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    /**
     * {@code /komaedit weather [intensidad] [clima] [-d derrumbe] [-v vida] [-seed semilla] [-agua]}: convierte la
     * selección de WorldEdit en una ruina.
     */
    private static LiteralArgumentBuilder<ServerCommandSource> weatherCommand(String name) {
        return literal(name)
                .executes(context -> weather(context, ""))
                .then(argument("weather_options", StringArgumentType.greedyString())
                        .suggests(WEATHER_SUGGESTIONS)
                        .executes(context -> weather(context,
                                StringArgumentType.getString(context, "weather_options"))));
    }

    private static int weather(CommandContext<ServerCommandSource> context, String options)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            WeatheringSettings settings = WeatheringSettings.parse(options);
            long seed = WeatheringService.start(player, settings);
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal("Envejeciendo la selección (intensidad " + settings.intensity()
                            + ", derrumbe " + settings.collapse() + ", vida " + settings.vegetation() + ", "
                            + settings.style().id() + ", semilla " + seed + ")…").formatted(Formatting.GRAY)),
                    false
            );
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Selecciona la construcción con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    /** {@code ivy [estilo] [banderas]}: la hiedra viva crece desde las semillas sobre lo que ya hay construido. */
    private static LiteralArgumentBuilder<ServerCommandSource> ivyCommand(String name) {
        return literal(name)
                .executes(context -> generateIvy(context, ""))
                .then(argument("ivy_options", StringArgumentType.greedyString())
                        .suggests(IVY_SUGGESTIONS)
                        .executes(context -> generateIvy(context,
                                StringArgumentType.getString(context, "ivy_options"))));
    }

    private static int generateIvy(CommandContext<ServerCommandSource> context, String options)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String trimmed = options.strip();
        if (trimmed.equalsIgnoreCase("cancel") || trimmed.equalsIgnoreCase("cancelar")) {
            if (!IvyService.cancel(player.getUuid())) {
                return fail(context.getSource(), "No hay ninguna hiedra creciendo");
            }
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal("Hiedra cancelada").formatted(Formatting.YELLOW)),
                    false
            );
            return 1;
        }
        try {
            String started = IvyService.start(player, IvySettings.parse(trimmed));
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal(started + "…").formatted(Formatting.GRAY)),
                    false
            );
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    /** {@code expr <radios> [banderas] [patrón] <fórmula…>}: la fórmula ocupa el resto de la línea. */
    private static LiteralArgumentBuilder<ServerCommandSource> expressionCommand(String name) {
        RequiredArgumentBuilder<ServerCommandSource, String> radii = argument(
                "tube_radii", StringArgumentType.string()
        );
        radii.then(argument("expression", StringArgumentType.greedyString())
                .suggests(EXPRESSION_SUGGESTIONS)
                .executes(context -> generateExpression(context, StringArgumentType.getString(context, "expression"))));
        return literal(name).then(radii);
    }

    private static int generateExpression(CommandContext<ServerCommandSource> context, String expression)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            TubeCommandParser.Result parsed = TubeCommandParser.parseExpression(
                    StringArgumentType.getString(context, "tube_radii"),
                    expression,
                    "stone"
            );
            return enqueueShape(context, player, true, parsed.pattern(), parsed.parameters(), parsed.materials());
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca al menos dos puntos con P o completa //pos1 y //pos2");
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    /** {@code /komaedit tweak <deinfest|persist|waterlog|wax> [on|off]}: retoques sobre la selección de WorldEdit. */
    private static LiteralArgumentBuilder<ServerCommandSource> tweakCommand(String name) {
        RequiredArgumentBuilder<ServerCommandSource, String> tweak = argument("tweak", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (TweakRules.Tweak option : TweakRules.Tweak.values()) {
                        builder.suggest(option.id());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> runTweak(context, null));
        tweak.then(argument("tweak_state", StringArgumentType.word())
                .suggests((context, builder) -> builder.suggest("on").suggest("off").buildFuture())
                .executes(context -> runTweak(context, StringArgumentType.getString(context, "tweak_state"))));
        return literal(name).then(tweak);
    }

    private static boolean canTweak(ServerCommandSource source) {
        return source.getEntity() instanceof ServerPlayerEntity player
                && KomaPermissions.canAny(player, KomaPermissions.BUILD + ".tweak");
    }

    private static int runTweak(CommandContext<ServerCommandSource> context, String state)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            TweakRules.Tweak tweak = TweakRules.Tweak.byId(StringArgumentType.getString(context, "tweak"));
            KomaPermissions.requireBuild(player, "tweak." + tweak.id());
            boolean enable = TweakRules.enabled(state);
            if (tweak == TweakRules.Tweak.DEINFEST && !enable) {
                throw new IllegalArgumentException("Quitar la infestación no tiene modo off");
            }
            RegionOperationService.submit(player, 0, tweak.displayName() + (enable ? "" : " (off)"),
                    new TweakOperation(tweak, enable));
            if (tweak == TweakRules.Tweak.WAX) {
                // Los carteles guardan la cera en sus datos: van en una edición aparte, también con //undo.
                int signs = SignWax.apply(player, enable);
                if (signs > 0) {
                    context.getSource().sendFeedback(() -> prefix().append(Text.literal(signs
                            + (signs == 1 ? " cartel " : " carteles ") + (enable ? "encerado" : "desencerado")
                            + (signs == 1 ? "" : "s") + " · //undo para deshacerlo").formatted(Formatting.GREEN)),
                            false);
                }
            }
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Selecciona primero la zona con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> generationCommand(String name, boolean buildNow) {
        // string() acepta lo mismo que word() sin comillas y, entre comillas, expresiones como "coil(engine:exact)".
        RequiredArgumentBuilder<ServerCommandSource, String> shape = argument("shape", StringArgumentType.string())
                .suggests(SHAPE_SUGGESTIONS)
                .executes(context -> generate(context, buildNow, "stone", null, null, null, null));
        RequiredArgumentBuilder<ServerCommandSource, String> pattern = argument("pattern", StringArgumentType.string())
                .executes(context -> generate(
                        context, buildNow, StringArgumentType.getString(context, "pattern"), null, null, null, null
                ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> scale = argument(
                "scale", IntegerArgumentType.integer(1, 128)
        ).executes(context -> generate(
                context,
                buildNow,
                StringArgumentType.getString(context, "pattern"),
                IntegerArgumentType.getInteger(context, "scale"),
                null,
                null,
                null
        ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> spacing = argument(
                "spacing", IntegerArgumentType.integer(2, 8)
        ).executes(context -> generate(
                context,
                buildNow,
                StringArgumentType.getString(context, "pattern"),
                IntegerArgumentType.getInteger(context, "scale"),
                IntegerArgumentType.getInteger(context, "spacing"),
                null,
                null
        ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> thickness = argument(
                "thickness", IntegerArgumentType.integer(1, 4)
        ).executes(context -> generate(
                context,
                buildNow,
                StringArgumentType.getString(context, "pattern"),
                IntegerArgumentType.getInteger(context, "scale"),
                IntegerArgumentType.getInteger(context, "spacing"),
                IntegerArgumentType.getInteger(context, "thickness"),
                null
        ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> roll = argument(
                "roll", IntegerArgumentType.integer(-180, 180)
        ).executes(context -> generate(
                context,
                buildNow,
                StringArgumentType.getString(context, "pattern"),
                IntegerArgumentType.getInteger(context, "scale"),
                IntegerArgumentType.getInteger(context, "spacing"),
                IntegerArgumentType.getInteger(context, "thickness"),
                IntegerArgumentType.getInteger(context, "roll")
        ));
        thickness.then(roll);
        spacing.then(thickness);
        scale.then(spacing);
        pattern.then(scale);
        shape.then(pattern);
        return literal(name).then(shape);
    }

    /** {@code confirm} lanza el Build grande en espera; {@code confirm cancel} lo descarta. */
    private static LiteralArgumentBuilder<ServerCommandSource> confirmCommand(String name) {
        return literal(name)
                .then(literal("cancel").executes(MoxiaCommands::discardPendingBuild))
                .executes(MoxiaCommands::confirmPendingBuild);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> brushCommand() {
        RequiredArgumentBuilder<ServerCommandSource, String> figure = argument("brush_figure", StringArgumentType.word())
                .suggests(FIGURE_SUGGESTIONS)
                .executes(context -> configureBrush(context, "hand", null, null, null));
        RequiredArgumentBuilder<ServerCommandSource, String> pattern = argument("brush_pattern", StringArgumentType.string())
                .executes(context -> configureBrush(
                        context, StringArgumentType.getString(context, "brush_pattern"), null, null, null
                ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> scale = argument(
                "brush_scale", IntegerArgumentType.integer(1, 128)
        ).executes(context -> configureBrush(
                context,
                StringArgumentType.getString(context, "brush_pattern"),
                IntegerArgumentType.getInteger(context, "brush_scale"),
                null,
                null
        ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> thickness = argument(
                "brush_thickness", IntegerArgumentType.integer(1, 4)
        ).executes(context -> configureBrush(
                context,
                StringArgumentType.getString(context, "brush_pattern"),
                IntegerArgumentType.getInteger(context, "brush_scale"),
                IntegerArgumentType.getInteger(context, "brush_thickness"),
                null
        ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> roll = argument(
                "brush_roll", IntegerArgumentType.integer(-180, 180)
        ).executes(context -> configureBrush(
                context,
                StringArgumentType.getString(context, "brush_pattern"),
                IntegerArgumentType.getInteger(context, "brush_scale"),
                IntegerArgumentType.getInteger(context, "brush_thickness"),
                IntegerArgumentType.getInteger(context, "brush_roll")
        ));
        thickness.then(roll);
        scale.then(thickness);
        pattern.then(scale);
        figure.then(pattern);
        return literal("brush")
                .then(literal("set").then(figure))
                .then(literal("stamp").executes(MoxiaCommands::stampBrush))
                .then(literal("off").executes(MoxiaCommands::disableBrush))
                .executes(MoxiaCommands::brushStatus);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> sculptCommand() {
        RequiredArgumentBuilder<ServerCommandSource, String> type = argument(
                "sculpt_type", StringArgumentType.word()
        ).suggests(SCULPT_SUGGESTIONS);
        RequiredArgumentBuilder<ServerCommandSource, String> pattern = argument(
                "sculpt_pattern", StringArgumentType.string()
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> radius = argument(
                "sculpt_radius", IntegerArgumentType.integer(1, 32)
        ).executes(context -> configureSculpt(context, null, null, null, null));
        RequiredArgumentBuilder<ServerCommandSource, Integer> height = argument(
                "sculpt_height", IntegerArgumentType.integer(1, 64)
        ).executes(context -> configureSculpt(
                context,
                IntegerArgumentType.getInteger(context, "sculpt_height"),
                null,
                null,
                null
        ));
        RequiredArgumentBuilder<ServerCommandSource, Integer> intensity = argument(
                "sculpt_intensity", IntegerArgumentType.integer(1, 16)
        ).executes(context -> configureSculpt(
                context,
                IntegerArgumentType.getInteger(context, "sculpt_height"),
                IntegerArgumentType.getInteger(context, "sculpt_intensity"),
                null,
                null
        ));
        RequiredArgumentBuilder<ServerCommandSource, String> falloff = argument(
                "sculpt_falloff", StringArgumentType.word()
        ).suggests(FALLOFF_SUGGESTIONS).executes(context -> configureSculpt(
                context,
                IntegerArgumentType.getInteger(context, "sculpt_height"),
                IntegerArgumentType.getInteger(context, "sculpt_intensity"),
                StringArgumentType.getString(context, "sculpt_falloff"),
                null
        ));
        RequiredArgumentBuilder<ServerCommandSource, Boolean> hollow = argument(
                "sculpt_hollow", BoolArgumentType.bool()
        ).executes(context -> configureSculpt(
                context,
                IntegerArgumentType.getInteger(context, "sculpt_height"),
                IntegerArgumentType.getInteger(context, "sculpt_intensity"),
                StringArgumentType.getString(context, "sculpt_falloff"),
                BoolArgumentType.getBool(context, "sculpt_hollow")
        ));
        falloff.then(hollow);
        intensity.then(falloff);
        height.then(intensity);
        radius.then(height);
        pattern.then(radius);
        type.then(pattern);

        RequiredArgumentBuilder<ServerCommandSource, Integer> noise = argument(
                "sculpt_noise", IntegerArgumentType.integer(0, 100)
        );
        RequiredArgumentBuilder<ServerCommandSource, Integer> randomness = argument(
                "sculpt_randomness", IntegerArgumentType.integer(0, 100)
        ).executes(MoxiaCommands::tuneSculpt);
        noise.then(randomness);

        return literal("sculpt")
                .then(literal("set").then(type))
                .then(literal("preview").executes(MoxiaCommands::previewSculpt))
                .then(literal("apply").executes(MoxiaCommands::applySculpt))
                .then(literal("show").executes(MoxiaCommands::showSculpt))
                .then(literal("cancel").executes(MoxiaCommands::cancelSculpt))
                .then(literal("tune").then(noise))
                .then(sculptMaskCommand())
                .then(sculptPresetCommand())
                .then(literal("off").executes(MoxiaCommands::disableSculpt))
                .then(literal("status").executes(MoxiaCommands::sculptStatus))
                .executes(MoxiaCommands::sculptStatus);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> sculptMaskCommand() {
        return literal("mask")
                .then(literal("ignore_air")
                        .then(argument("mask_ignore_air", BoolArgumentType.bool())
                                .executes(context -> updateMaskBoolean(context, true))))
                .then(literal("ignore_liquids")
                        .then(argument("mask_ignore_liquids", BoolArgumentType.bool())
                                .executes(context -> updateMaskBoolean(context, false))))
                .then(literal("whitelist")
                        .then(argument("mask_whitelist", StringArgumentType.greedyString())
                                .executes(context -> updateMaskList(context, MaskList.WHITELIST))))
                .then(literal("blacklist")
                        .then(argument("mask_blacklist", StringArgumentType.greedyString())
                                .executes(context -> updateMaskList(context, MaskList.BLACKLIST))))
                .then(literal("replace_only")
                        .then(argument("mask_replace", StringArgumentType.greedyString())
                                .executes(context -> updateMaskList(context, MaskList.REPLACE_ONLY))))
                .then(literal("clear").executes(MoxiaCommands::clearSculptMask))
                .executes(MoxiaCommands::sculptStatus);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> jobCommand() {
        return literal("job")
                .then(literal("status").executes(MoxiaCommands::jobStatus))
                .then(literal("cancel").executes(MoxiaCommands::cancelJob))
                .executes(MoxiaCommands::jobStatus);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> projectCommand() {
        return literal("project")
                .then(literal("status").executes(MoxiaCommands::projectStatus))
                .then(literal("backup")
                        .requires(source -> can(source, KomaPermissions.PROJECT_BACKUP))
                        .executes(MoxiaCommands::backupProject))
                .executes(MoxiaCommands::projectStatus);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> sculptPresetCommand() {
        return literal("preset")
                .then(literal("save")
                        .then(argument("preset_save_name", StringArgumentType.word())
                                .executes(MoxiaCommands::saveSculptPreset)))
                .then(literal("load")
                        .then(argument("preset_load_name", StringArgumentType.word())
                                .executes(MoxiaCommands::loadSculptPreset)))
                .then(literal("delete")
                        .then(argument("preset_delete_name", StringArgumentType.word())
                                .executes(MoxiaCommands::deleteSculptPreset)))
                .then(literal("list").executes(MoxiaCommands::listSculptPresets))
                .executes(MoxiaCommands::listSculptPresets);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> gradientCommand() {
        RequiredArgumentBuilder<ServerCommandSource, String> preset = argument("gradient_palette", StringArgumentType.word())
                .suggests(PALETTE_SUGGESTIONS);
        RequiredArgumentBuilder<ServerCommandSource, String> presetAxis = argument("gradient_axis", StringArgumentType.word())
                .suggests((context, builder) -> {
                    builder.suggest("x");
                    builder.suggest("y");
                    builder.suggest("z");
                    builder.suggest("diagonal");
                    builder.suggest("radial");
                    return builder.buildFuture();
                })
                .executes(context -> applyPresetGradient(context, 82, false));
        RequiredArgumentBuilder<ServerCommandSource, Integer> presetDither = argument(
                "gradient_dither", IntegerArgumentType.integer(0, 100)
        ).executes(context -> applyPresetGradient(
                context, IntegerArgumentType.getInteger(context, "gradient_dither"), false
        ));
        RequiredArgumentBuilder<ServerCommandSource, Boolean> presetReverse = argument(
                "gradient_reverse", BoolArgumentType.bool()
        ).executes(context -> applyPresetGradient(
                context,
                IntegerArgumentType.getInteger(context, "gradient_dither"),
                BoolArgumentType.getBool(context, "gradient_reverse")
        ));
        presetDither.then(presetReverse);
        presetAxis.then(presetDither);
        preset.then(presetAxis);

        RequiredArgumentBuilder<ServerCommandSource, String> customAxis = argument("custom_axis", StringArgumentType.word())
                .suggests((context, builder) -> {
                    builder.suggest("x");
                    builder.suggest("y");
                    builder.suggest("z");
                    builder.suggest("diagonal");
                    builder.suggest("radial");
                    return builder.buildFuture();
                });
        RequiredArgumentBuilder<ServerCommandSource, Integer> customDither = argument(
                "custom_dither", IntegerArgumentType.integer(0, 100)
        );
        RequiredArgumentBuilder<ServerCommandSource, Boolean> customReverse = argument(
                "custom_reverse", BoolArgumentType.bool()
        );
        RequiredArgumentBuilder<ServerCommandSource, String> customPalette = argument(
                "custom_palette", StringArgumentType.greedyString()
        ).executes(MoxiaCommands::applyCustomGradient);
        customReverse.then(customPalette);
        customDither.then(customReverse);
        customAxis.then(customDither);

        return literal("gradient")
                .then(literal("preset").then(preset))
                .then(literal("custom").then(customAxis));
    }

    private static int configureBrush(
            CommandContext<ServerCommandSource> context,
            String pattern,
            Integer scale,
            Integer thickness,
            Integer roll
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            PathShape shape = PathShape.byId(StringArgumentType.getString(context, "brush_figure"));
            if (!shape.isFigure()) {
                return fail(context.getSource(), "El brush usa rombos, cuadrados, círculos, triángulos o hexágonos");
            }
            ShapeParameters parameters = shape.defaults();
            if (scale != null) {
                parameters = parameters.withRadius(scale);
            }
            if (thickness != null) {
                parameters = parameters.withThickness(thickness);
            }
            if (roll != null) {
                parameters = parameters.withRollDegrees(roll);
            }
            String resolvedPattern = resolveBrushPattern(player, pattern);
            WorldEditService.validatePattern(player, resolvedPattern);
            SculptManager.deactivate(player);
            BrushManager.activate(player, new BrushSettings(parameters, resolvedPattern));
            if (!player.getInventory().contains(new ItemStack(Items.BRUSH))) {
                player.getInventory().offerOrDrop(new ItemStack(Items.BRUSH));
            }
            ShapeParameters active = parameters;
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal(
                            "Brush activo: " + active.shape().displayName()
                                    + " · escala " + active.radius()
                                    + " · grosor " + active.thickness()
                                    + " · giro " + active.rollDegrees() + "°"
                    ).formatted(Formatting.GREEN)),
                    false
            );
            context.getSource().sendFeedback(
                    () -> Text.literal("Usa el cepillo sobre una cara; cada clic coloca una figura completa. ")
                            .formatted(Formatting.GRAY)
                            .append(commandButton("[ESTAMPAR AHORA]", "/komaedit brush stamp", Formatting.AQUA)),
                    false
            );
            return 1;
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static String resolveBrushPattern(ServerPlayerEntity player, String pattern) {
        if (!pattern.equalsIgnoreCase("hand")) {
            return pattern;
        }
        if (player.getMainHandStack().getItem() instanceof BlockItem blockItem) {
            return Registries.BLOCK.getId(blockItem.getBlock()).toString();
        }
        if (player.getOffHandStack().getItem() instanceof BlockItem blockItem) {
            return Registries.BLOCK.getId(blockItem.getBlock()).toString();
        }
        Optional<BrushSettings> previous = BrushManager.settings(player.getUuid());
        if (previous.isPresent() && !previous.get().pattern().equalsIgnoreCase("hand")) {
            return previous.get().pattern();
        }
        Optional<SculptProfile> sculpt = SculptManager.profile(player.getUuid());
        if (sculpt.isPresent() && !sculpt.get().pattern().equalsIgnoreCase("hand")) {
            return sculpt.get().pattern();
        }
        throw new IllegalArgumentException("Para usar hand, sostén un bloque en la mano principal o secundaria");
    }

    private static int stampBrush(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            int changed = BrushManager.stampTarget(player);
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal(
                            "Figura estampada: " + changed + " bloques · usa //undo"
                    ).formatted(Formatting.GREEN)),
                    false
            );
            return changed;
        } catch (InputParseException | MaxChangedBlocksException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int disableBrush(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!BrushManager.deactivate(player.getUuid())) {
            return fail(context.getSource(), "No había un brush de KomaEdit activo");
        }
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal("Brush desactivado").formatted(Formatting.YELLOW)),
                false
        );
        return 1;
    }

    private static int brushStatus(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<BrushSettings> settings = BrushManager.settings(player.getUuid());
        if (settings.isEmpty()) {
            return fail(context.getSource(), "Brush inactivo; usa /komaedit brush set diamonds hand 5 1 0");
        }
        BrushSettings active = settings.get();
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        "Brush: " + active.parameters().shape().displayName()
                                + " · escala " + active.parameters().radius()
                                + " · patrón " + active.pattern()
                ).formatted(Formatting.AQUA)),
                false
        );
        return 1;
    }

    private static int configureSculpt(
            CommandContext<ServerCommandSource> context,
            Integer height,
            Integer intensity,
            String falloff,
            Boolean hollow
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            SculptBrushType type = SculptBrushType.byId(
                    StringArgumentType.getString(context, "sculpt_type")
            );
            String pattern = resolveBrushPattern(
                    player,
                    StringArgumentType.getString(context, "sculpt_pattern")
            );
            WorldEditService.validatePattern(player, pattern);
            int radius = IntegerArgumentType.getInteger(context, "sculpt_radius");
            SculptSettings defaults = SculptSettings.defaults(type);
            SculptSettings settings = defaults.withGeometry(
                    radius,
                    height == null ? Math.min(64, radius * 2) : height,
                    intensity == null ? defaults.intensity() : intensity,
                    falloff == null ? defaults.falloff() : BrushFalloff.byId(falloff),
                    hollow != null && hollow
            );
            BrushManager.deactivate(player.getUuid());
            SculptManager.activate(player, new SculptProfile(settings, pattern));
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal(
                            "Brocha " + type.displayName() + " activa · radio " + radius
                                    + " · patrón " + pattern
                    ).formatted(Formatting.GREEN)),
                    false
            );
            context.getSource().sendFeedback(
                    () -> Text.literal("El cepillo solo crea previews; confirma con ")
                            .formatted(Formatting.GRAY)
                            .append(commandButton("[APLICAR]", "/komaedit sculpt apply", Formatting.GREEN)),
                    false
            );
            return 1;
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int previewSculpt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            SculptManager.previewTarget(player);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int applySculpt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            int scheduled = SculptManager.apply(player);
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal(
                            "Edición en cola: " + scheduled + " bloques; el mundo seguirá respondiendo"
                    ).formatted(Formatting.GREEN)),
                    false
            );
            return scheduled;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int showSculpt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        var preview = SculptPreviewManager.get(player.getUuid());
        if (preview.isEmpty()) {
            return fail(context.getSource(), "No hay una previsualización de brocha activa");
        }
        try {
            SculptPreviewManager.show(player, preview.get());
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal(
                            "Preview: " + preview.get().plan().size() + " bloques"
                    ).formatted(Formatting.AQUA)),
                    false
            );
            return preview.get().plan().size();
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int cancelSculpt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!SculptManager.cancelPreview(player)) {
            return fail(context.getSource(), "No había un cálculo o preview de brocha activo");
        }
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal("Cálculo y preview cancelados").formatted(Formatting.YELLOW)),
                false
        );
        return 1;
    }

    private static int disableSculpt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!SculptManager.deactivate(player)) {
            return fail(context.getSource(), "No había una brocha de terreno activa");
        }
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal("Brocha de terreno desactivada").formatted(Formatting.YELLOW)),
                false
        );
        return 1;
    }

    private static int tuneSculpt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<SculptProfile> current = SculptManager.profile(player.getUuid());
        if (current.isEmpty()) {
            return fail(context.getSource(), "Activa primero una brocha con /komaedit sculpt set");
        }
        int noise = IntegerArgumentType.getInteger(context, "sculpt_noise");
        int randomness = IntegerArgumentType.getInteger(context, "sculpt_randomness");
        SculptSettings settings = current.get().settings().withTuning(noise, randomness);
        SculptManager.activate(player, current.get().withSettings(settings));
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        "Ajustes: ruido " + noise + "% · aleatoriedad " + randomness + "%"
                ).formatted(Formatting.GREEN)),
                false
        );
        return 1;
    }

    private static int updateMaskBoolean(
            CommandContext<ServerCommandSource> context,
            boolean air
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<SculptProfile> current = SculptManager.profile(player.getUuid());
        if (current.isEmpty()) {
            return fail(context.getSource(), "Activa primero una brocha de terreno");
        }
        BrushMask mask = current.get().settings().mask();
        if (air) {
            mask = mask.withIgnoreAir(BoolArgumentType.getBool(context, "mask_ignore_air"));
        } else {
            mask = mask.withIgnoreLiquids(BoolArgumentType.getBool(context, "mask_ignore_liquids"));
        }
        SculptSettings settings = current.get().settings().withMask(mask);
        SculptManager.activate(player, current.get().withSettings(settings));
        BrushMask updated = mask;
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        "Máscara: aire " + (updated.ignoreAir() ? "ignorado" : "permitido")
                                + " · líquidos " + (updated.ignoreLiquids() ? "ignorados" : "permitidos")
                ).formatted(Formatting.GREEN)),
                false
        );
        return 1;
    }

    private static int updateMaskList(
            CommandContext<ServerCommandSource> context,
            MaskList list
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<SculptProfile> current = SculptManager.profile(player.getUuid());
        if (current.isEmpty()) {
            return fail(context.getSource(), "Activa primero una brocha de terreno");
        }
        String argumentName = switch (list) {
            case WHITELIST -> "mask_whitelist";
            case BLACKLIST -> "mask_blacklist";
            case REPLACE_ONLY -> "mask_replace";
        };
        Set<String> blocks = parseBlockList(StringArgumentType.getString(context, argumentName));
        BrushMask mask = current.get().settings().mask();
        mask = switch (list) {
            case WHITELIST -> mask.withWhitelist(blocks);
            case BLACKLIST -> mask.withBlacklist(blocks);
            case REPLACE_ONLY -> mask.withReplaceOnly(blocks);
        };
        SculptSettings settings = current.get().settings().withMask(mask);
        SculptManager.activate(player, current.get().withSettings(settings));
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        "Máscara " + list.displayName + ": " + blocks.size() + " bloques"
                ).formatted(Formatting.GREEN)),
                false
        );
        return blocks.size();
    }

    private static int clearSculptMask(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<SculptProfile> current = SculptManager.profile(player.getUuid());
        if (current.isEmpty()) {
            return fail(context.getSource(), "Activa primero una brocha de terreno");
        }
        SculptSettings settings = current.get().settings().withMask(BrushMask.DEFAULT);
        SculptManager.activate(player, current.get().withSettings(settings));
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal("Máscaras restablecidas").formatted(Formatting.YELLOW)),
                false
        );
        return 1;
    }

    private static int saveSculptPreset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<SculptProfile> current = SculptManager.profile(player.getUuid());
        if (current.isEmpty()) {
            return fail(context.getSource(), "Activa primero una brocha de terreno");
        }
        String name = StringArgumentType.getString(context, "preset_save_name");
        try {
            SculptPresetStore.save(player.getUuid(), name, current.get());
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal("Preset guardado: " + name).formatted(Formatting.GREEN)),
                    false
            );
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int loadSculptPreset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String name = StringArgumentType.getString(context, "preset_load_name");
        try {
            SculptProfile profile = SculptPresetStore.load(player.getUuid(), name);
            WorldEditService.validatePattern(player, profile.pattern());
            BrushManager.deactivate(player.getUuid());
            SculptManager.activate(player, profile);
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal(
                            "Preset cargado: " + name + " · " + profile.settings().type().displayName()
                    ).formatted(Formatting.GREEN)),
                    false
            );
            return 1;
        } catch (InputParseException | IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int deleteSculptPreset(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "preset_delete_name");
        try {
            UUID playerId = context.getSource().getPlayer().getUuid();
            if (!SculptPresetStore.delete(playerId, name)) {
                return fail(context.getSource(), "No existe el preset " + name);
            }
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal("Preset eliminado: " + name).formatted(Formatting.YELLOW)),
                    false
            );
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int listSculptPresets(CommandContext<ServerCommandSource> context) {
        try {
            UUID playerId = context.getSource().getPlayer().getUuid();
            List<String> presets = SculptPresetStore.list(playerId);
            if (presets.isEmpty()) {
                return fail(context.getSource(), "No hay presets guardados");
            }
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal("Presets: " + String.join(", ", presets))
                            .formatted(Formatting.AQUA)),
                    false
            );
            return presets.size();
        } catch (IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int sculptStatus(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<SculptProfile> current = SculptManager.profile(player.getUuid());
        if (current.isEmpty()) {
            return fail(context.getSource(), "Brocha inactiva; usa /komaedit sculpt set sphere hand 8");
        }
        SculptProfile profile = current.get();
        int previewSize = SculptPreviewManager.get(player.getUuid())
                .map(preview -> preview.plan().size())
                .orElse(0);
        String calculation = SculptManager.isCalculating(player.getUuid()) ? " · calculando" : "";
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        profile.settings().type().displayName()
                                + " · radio " + profile.settings().radius()
                                + " · altura " + profile.settings().height()
                                + " · intensidad " + profile.settings().intensity()
                                + " · preview " + previewSize + calculation
                ).formatted(Formatting.AQUA)),
                false
        );
        return 1;
    }

    private static int jobStatus(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        UUID playerId = context.getSource().getPlayerOrThrow().getUuid();
        Optional<BatchEditService.JobStatus> status = BatchEditService.status(playerId);
        if (status.isEmpty()) {
            return fail(context.getSource(), "No hay una edición por lotes activa");
        }
        BatchEditService.JobStatus job = status.get();
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        job.description() + " · " + job.percentage() + "% · "
                                + job.processed() + "/" + job.total()
                ).formatted(Formatting.AQUA)),
                false
        );
        return job.percentage();
    }

    private static int cancelJob(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        UUID playerId = context.getSource().getPlayerOrThrow().getUuid();
        if (!BatchEditService.cancel(playerId, true)) {
            return fail(context.getSource(), "No hay una edición por lotes activa");
        }
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        "Edición detenida; los cambios parciales están disponibles en //undo"
                ).formatted(Formatting.YELLOW)),
                false
        );
        return 1;
    }

    private static int projectStatus(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        UUID playerId = context.getSource().getPlayerOrThrow().getUuid();
        ProjectStorage.ProjectStatus status = ProjectStorage.status(playerId);
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        "Proyecto persistente · " + status.recipes() + " recetas · "
                                + status.instances() + " instancias · " + status.jobs() + " jobs · "
                                + status.interruptedJobs() + " interrumpidos · " + readableBytes(status.bytes())
                ).formatted(Formatting.AQUA)),
                false
        );
        return (int) Math.min(Integer.MAX_VALUE, status.instances());
    }

    private static int backupProject(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        UUID playerId = context.getSource().getPlayerOrThrow().getUuid();
        var backup = ProjectStorage.backup(playerId);
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        "Backup creado: " + backup.getFileName()
                ).formatted(Formatting.GREEN)),
                false
        );
        return 1;
    }

    private static String readableBytes(long bytes) {
        if (bytes < 1_024L) {
            return bytes + " B";
        }
        if (bytes < 1_048_576L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1_024.0);
        }
        return String.format(Locale.ROOT, "%.1f MiB", bytes / 1_048_576.0);
    }

    private static Set<String> parseBlockList(String input) {
        Set<String> blocks = Arrays.stream(input.split("[,;\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Indica al menos un bloque");
        }
        return blocks;
    }

    private static int applyPresetGradient(
            CommandContext<ServerCommandSource> context,
            int dither,
            boolean reverse
    ) throws CommandSyntaxException {
        try {
            GradientPalette palette = GradientPalette.byId(StringArgumentType.getString(context, "gradient_palette"));
            return applyGradient(
                    context,
                    GradientAxis.byId(StringArgumentType.getString(context, "gradient_axis")),
                    palette.patterns(),
                    palette.displayName(),
                    dither,
                    reverse
            );
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int applyCustomGradient(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        try {
            return applyGradient(
                    context,
                    GradientAxis.byId(StringArgumentType.getString(context, "custom_axis")),
                    StringArgumentType.getString(context, "custom_palette"),
                    "Personalizado",
                    IntegerArgumentType.getInteger(context, "custom_dither"),
                    BoolArgumentType.getBool(context, "custom_reverse")
            );
        } catch (IllegalArgumentException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static int applyGradient(
            CommandContext<ServerCommandSource> context,
            GradientAxis axis,
            String palette,
            String paletteName,
            int dither,
            boolean reverse
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            int changed = GradientService.apply(player, axis, palette, dither, reverse);
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal(
                            "Degradado " + paletteName + " en cola: " + changed + " bloques · eje "
                                    + axis.id() + " · mezcla " + dither + "% · /komaedit job status"
                    ).formatted(Formatting.GREEN)),
                    false
            );
            return changed;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Completa la selección de WorldEdit con //pos1 y //pos2");
        } catch (InputParseException | IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static boolean canUse(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return false;
        }
        return KomaPermissions.canAccess(player);
    }

    private static boolean can(ServerCommandSource source, String permission) {
        return source.getEntity() instanceof ServerPlayerEntity player
                && KomaPermissions.can(player, permission);
    }

    private static int generate(
            CommandContext<ServerCommandSource> context,
            boolean buildNow,
            String pattern,
            Integer scale,
            Integer spacing,
            Integer thickness,
            Integer roll
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            ShapeExpression expression = ShapeExpression.parse(StringArgumentType.getString(context, "shape"));
            PathShape shape = expression.shape();
            ShapeParameters parameters = shape.defaults().withOptions(expression.options());
            if (scale != null) {
                parameters = parameters.withRadius(scale);
            }
            if (spacing != null) {
                parameters = parameters.withSpacing(spacing);
            }
            if (thickness != null) {
                parameters = parameters.withThickness(thickness);
            }
            if (roll != null) {
                parameters = parameters.withRollDegrees(roll);
            }
            return enqueueShape(context, player, buildNow, pattern, parameters);
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Define //pos1 y //pos2, o añade puntos con /komaedit path add");
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int generateFixed(
            CommandContext<ServerCommandSource> context,
            boolean buildNow,
            PathShape shape,
            String pattern,
            int radius,
            int spacing,
            int thickness,
            int roll
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            ShapeParameters parameters = shape.defaults()
                    .withRadius(radius)
                    .withSpacing(spacing)
                    .withThickness(thickness)
                    .withRollDegrees(roll);
            return enqueueShape(context, player, buildNow, pattern, parameters);
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca al menos dos puntos con P o completa //pos1 y //pos2");
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int generateBridge(
            CommandContext<ServerCommandSource> context,
            String pattern,
            int height,
            int width
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            ShapeParameters parameters = PathShape.ARCH_BRIDGE.defaults()
                    .withRadius(width)
                    .withOptions(new ShapeOptions(Map.of("height", (double) height)));
            return enqueueShape(context, player, true, pattern, parameters);
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca al menos dos puntos con P o completa //pos1 y //pos2");
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int generatePillarArches(
            CommandContext<ServerCommandSource> context,
            String pattern,
            int height,
            int radius
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            ShapeParameters parameters = PathShape.PILLAR_ARCHES.defaults()
                    .withRadius(radius)
                    .withOptions(new ShapeOptions(Map.of("height", (double) height)));
            return enqueueShape(context, player, true, pattern, parameters);
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca las bases de los dos pilares con P1 y P2");
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int generateCloud(
            CommandContext<ServerCommandSource> context,
            String pattern,
            int radius,
            int organicity,
            int gap
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            ShapeParameters parameters = PathShape.CLOUDS.defaults()
                    .withRadius(radius)
                    .withSpacing(organicity)
                    .withOptions(new ShapeOptions(Map.of("gap", (double) gap)));
            return enqueueShape(context, player, true, pattern, parameters);
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca la ruta de los bancos nubosos con al menos dos puntos P");
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int generateLightningCircle(
            CommandContext<ServerCommandSource> context,
            String pattern,
            int circleRadius,
            int branching
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        var center = player.getBlockPos();
        int requestedPoints = Math.clamp(
                (int) Math.ceil(Math.PI * 2.0 * circleRadius / 7.0),
                12,
                PathManager.MAX_POINTS
        );
        LinkedHashSet<BlockVector3> uniquePoints = new LinkedHashSet<>();
        for (int pointIndex = 0; pointIndex < requestedPoints; pointIndex++) {
            double angle = Math.PI * 2.0 * pointIndex / requestedPoints;
            uniquePoints.add(BlockVector3.at(
                    center.getX() + (int) Math.round(Math.cos(angle) * circleRadius),
                    center.getY(),
                    center.getZ() + (int) Math.round(Math.sin(angle) * circleRadius)
            ));
        }
        if (uniquePoints.size() < 3) {
            return fail(context.getSource(), "El círculo de rayos necesita al menos tres puntos distintos");
        }
        PathManager.replace(player.getUuid(), new ArrayList<>(uniquePoints), true);
        PathHologramSync.sync(player);
        int amplitude = Math.clamp((int) Math.round(circleRadius * 0.10), 3, 16);
        return generateFixed(
                context,
                true,
                PathShape.LIGHTNING,
                pattern,
                amplitude,
                branching,
                1,
                45
        );
    }

    private static int generateTorii(
            CommandContext<ServerCommandSource> context,
            String pattern,
            int height,
            int radius,
            int detail
    ) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            ShapeParameters parameters = PathShape.TORII.defaults()
                    .withRadius(radius)
                    .withSpacing(detail)
                    .withOptions(new ShapeOptions(Map.of("height", (double) height)));
            return enqueueShape(context, player, true, pattern, parameters);
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Marca con P las bases de los dos pilares del torii");
        } catch (InputParseException | IllegalArgumentException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    static int enqueueShape(
            CommandContext<ServerCommandSource> context,
            ServerPlayerEntity player,
            boolean buildNow,
            String pattern,
            ShapeParameters requestedParameters
    ) throws IncompleteRegionException, InputParseException {
        return enqueueShape(context, player, buildNow, pattern, requestedParameters, PieceMaterials.NONE);
    }

    /** @param pieceMaterials materiales fijos de las piezas de {@code -w} ({@code Slabs(Slab:acacia)}) */
    static int enqueueShape(
            CommandContext<ServerCommandSource> context,
            ServerPlayerEntity player,
            boolean buildNow,
            String pattern,
            ShapeParameters requestedParameters,
            PieceMaterials pieceMaterials
    ) throws IncompleteRegionException, InputParseException {
        SmoothBlockStates.validate(pieceMaterials);
        // buildNow = false es /komaedit estimate: calcula la misma geometría y solo informa.
        boolean estimateOnly = !buildNow;
        KomaPermissions.requireBuild(player, requestedParameters.shape().id());
        WorldEditService.validatePattern(player, pattern);
        PathSnapshot pathSnapshot = WorldEditService.resolvePath(player);
        UUID playerId = player.getUuid();
        var server = player.getServer();
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal((estimateOnly ? "Estimando: " : "")
                                + "Calculando geometría 3D fuera del hilo del servidor…")
                        .formatted(Formatting.GRAY)),
                false
        );
        AsyncEngine.submit(
                playerId,
                () -> {
                    boolean legacyScalePath = requestedParameters.shape() == PathShape.SCALES
                            || requestedParameters.shape() == PathShape.SPIKED_SCALES;
                    SampledPath sampledPath = legacyScalePath
                            ? SampledPath.create(pathSnapshot.points(), pathSnapshot.closed())
                            : SampledPath.createSmooth(pathSnapshot.points(), pathSnapshot.closed());
                    return ShapeGenerator.generatePlan(sampledPath, requestedParameters)
                            .withPieceMaterials(pieceMaterials);
                },
                plan -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    if (estimateOnly) {
                        reportEstimate(current, requestedParameters.shape().displayName(), BuildEstimate.of(plan));
                        return;
                    }
                    Runnable submit = () -> {
                        try {
                            int changed = BatchEditService.submit(
                                    current,
                                    plan,
                                    pattern,
                                    requestedParameters.shape().displayName()
                            );
                            if (PlanPreviews.holds(playerId, plan)) {
                                return;
                            }
                            int pieces = plan.pieceCount();
                            current.getCommandSource().sendFeedback(
                                    () -> prefix().append(Text.literal(
                                            requestedParameters.shape().displayName() + " en cola: " + changed
                                                    + " bloques" + (pieces == 0
                                                    ? " · espaciado " + requestedParameters.spacing()
                                                    : " · " + pieces + " piezas")
                                                    + " · /komaedit job status"
                                    ).formatted(Formatting.GREEN)),
                                    false
                            );
                        } catch (IllegalArgumentException | IllegalStateException exception) {
                            fail(current.getCommandSource(), message(exception));
                        }
                    };
                    PendingBuilds.submitOrHold(
                            current, requestedParameters.shape().displayName(), BuildEstimate.of(plan), submit, true
                    );
                }),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null) {
                        fail(current.getCommandSource(), throwableMessage(throwable));
                    }
                })
        );
        return 1;
    }

    private static int confirmPendingBuild(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Optional<Integer> preview = PreviewCommands.confirm(context);
        if (preview.isPresent()) {
            return preview.get();
        }
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            // Ya confirmado: el Build no vuelve a pasar por la vista previa.
            String description = EditRequests.within(EditRequest.CONFIRMED, () -> PendingBuilds.confirm(player));
            context.getSource().sendFeedback(
                    () -> prefix().append(Text.literal("Confirmado: " + description).formatted(Formatting.GREEN)),
                    false
            );
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), message(exception));
        }
    }

    private static int discardPendingBuild(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<String> discarded = PreviewCommands.discard(player).map(description -> "vista previa de " + description)
                .or(() -> PendingBuilds.cancel(player.getUuid()));
        if (discarded.isEmpty()) {
            return fail(context.getSource(), "No hay ninguna vista previa ni Build esperando confirmación");
        }
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal("Descartado: " + discarded.get()).formatted(Formatting.YELLOW)),
                false
        );
        return 1;
    }

    private static void reportEstimate(ServerPlayerEntity player, String description, BuildEstimate estimate) {
        int threshold = KomaEditConfig.settings().confirmBuildAboveBlocks();
        int limit = KomaEditConfig.settings().maxBlocksPerOperation();
        String verdict = estimate.blocks() > limit && !KomaPermissions.can(player, KomaPermissions.LIMIT_UNRESTRICTED)
                ? " · supera tu límite de " + limit + " bloques"
                : BuildEstimate.needsConfirmation(estimate.blocks(), threshold) ? " · pedirá confirmación" : "";
        player.getCommandSource().sendFeedback(
                () -> prefix().append(Text.literal("Estimación de " + description + ": " + estimate.summary() + verdict)
                        .formatted(Formatting.AQUA)),
                false
        );
    }

    private static int openHudMenu(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!ServerPlayNetworking.canSend(player, QuickBuildPayload.ID)) {
            return fail(context.getSource(), "El cliente no tiene disponible la paleta QuickBuild");
        }
        ServerPlayNetworking.send(player, QuickBuildPayload.INSTANCE);
        return 1;
    }

    private static int guiHint(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(
                () -> prefix().append(Text.literal(
                        "K abre la paleta visual: elige bloque y forma con clic; arrastra con clic derecho para girar."
                ).formatted(Formatting.AQUA)),
                false
        );
        return 1;
    }

    private static MutableText commandButton(String label, String command, Formatting color) {
        return Text.literal(label).styled(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static MutableText prefix() {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD);
    }

    private static int fail(ServerCommandSource source, String message) {
        source.sendError(prefix().append(Text.literal(
                message == null || message.isBlank() ? "Operación inválida" : CommandHints.enrich(message)
        ).formatted(Formatting.RED)));
        return 0;
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName().toLowerCase(Locale.ROOT)
                : message;
    }

    private static String throwableMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName().toLowerCase(Locale.ROOT)
                : message;
    }

    private enum MaskList {
        WHITELIST("lista blanca"),
        BLACKLIST("lista negra"),
        REPLACE_ONLY("reemplazo exclusivo");

        private final String displayName;

        MaskList(String displayName) {
            this.displayName = displayName;
        }
    }
}
