package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.image.Anchor;
import com.komainu.moxia.image.Dithering;
import com.komainu.moxia.image.ImageConfig;
import com.komainu.moxia.image.ImageFiles;
import com.komainu.moxia.image.ImagePalettes;
import com.komainu.moxia.image.ImageRequest;
import com.komainu.moxia.image.ImageService;
import com.komainu.moxia.permission.KomaPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.IncompleteRegionException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit image} (alias {@code imagen} y {@code stainedglassimage}, como {@code //stainedglassimage} de
 * ezEdits): una imagen convertida en bloques. Ver {@link ImageRequest} y {@link ImageService}.
 */
public final class ImageCommands {
    public static final String NODE = "image";
    private static final List<String> FLAGS = List.of("-w", "-h", "-p", "-d", "-b", "-c", "-a", "-u", "-o", "-s",
            "-e", "-r");

    private ImageCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("komaedit")
                .then(command("image"))
                .then(command("imagen"))
                .then(command("stainedglassimage")));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && KomaPermissions.canBuild(player, NODE))
                .executes(ImageCommands::help)
                .then(argument("image_arguments", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggest(builder))
                        .executes(context -> run(context, StringArgumentType.getString(context, "image_arguments"))));
    }

    private static int run(CommandContext<ServerCommandSource> context, String arguments)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            ImageService.start(player, ImageRequest.parse(arguments));
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Con -s selecciona primero un rectángulo plano con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int start = remaining.lastIndexOf(' ') + 1;
        String current = remaining.substring(start).toLowerCase(Locale.ROOT);
        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + start);
        List<String> options = new ArrayList<>();
        if (start == 0) {
            try {
                options.addAll(ImageFiles.list(ImageConfig.directory()));
            } catch (IllegalStateException ignored) {
                // Sin configuración cargada no hay lista.
            }
            options.add("https://");
        } else {
            String[] words = remaining.substring(0, start).trim().split("\\s+");
            String previous = words[words.length - 1].toLowerCase(Locale.ROOT);
            switch (previous) {
                case "-p" -> addPresets(options);
                case "-d" -> {
                    for (Dithering dithering : Dithering.values()) {
                        options.add(dithering.id());
                    }
                }
                case "-o" -> {
                    for (Anchor anchor : Anchor.values()) {
                        options.add(anchor.id());
                    }
                }
                case "-a" -> options.addAll(List.of("air", "keep", "white_concrete", "black_stained_glass"));
                case "-u" -> options.addAll(List.of("50", "25", "75"));
                case "-b", "-c" -> options.addAll(List.of("0", "20", "-20"));
                case "-w", "-h" -> options.addAll(List.of("32", "64", "128"));
                default -> {
                    if (words.length == 1) {
                        options.addAll(List.of("32", "64", "128"));
                    }
                    addPresets(options);
                    options.addAll(FLAGS);
                }
            }
        }
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(current)) {
                offset.suggest(option);
            }
        }
        return offset.buildFuture();
    }

    private static void addPresets(List<String> options) {
        for (ImagePalettes.Preset preset : ImagePalettes.Preset.values()) {
            options.add(preset.id());
        }
    }

    private static int help(CommandContext<ServerCommandSource> context) {
        String[] lines = {
                "/komaedit image <archivo|https://…> [ancho|anchoxalto] [paleta] [banderas]  (alias imagen, stainedglassimage)",
                "Archivos de config/komaedit/images/ (PNG, JPG, GIF); URL solo si el servidor las activa",
                "Paletas: glass (vidrio, por defecto), panes, wool, concrete, terracotta, solid o ##Nombre/#mía/a,b,c",
                "-w ancho · -h alto · -d none|fs|bayer · -b brillo · -c contraste (−100 a 100)",
                "-a air|keep|<bloque> (transparentes) · -u umbral % · -o anclaje · -s selección · -e estirar · -r superficie",
                "Ej.: /komaedit image logo.png 64 · /komaedit image foto.jpg -s concrete -d bayer · /komaedit image icono.png 32 wool -d none"
        };
        for (String line : lines) {
            context.getSource().sendFeedback(() -> Text.literal(line).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static MutableText prefix() {
        return Feedback.prefix();
    }

    private static int fail(ServerCommandSource source, String message) {
        return EzCommandSupport.fail(source, message);
    }
}
