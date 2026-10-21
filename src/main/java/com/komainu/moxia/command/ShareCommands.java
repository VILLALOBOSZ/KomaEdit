package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.share.ShareKind;
import com.komainu.moxia.share.ShareKinds;
import com.komainu.moxia.share.SharePermissions;
import com.komainu.moxia.share.ShareService;
import com.komainu.moxia.share.ShareTarget;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit share} (alias {@code compartir}): pasar paletas, ruidos, presets, recetas y planos de un servidor a
 * otro con un código o un archivo {@code .kpack}. Importar enseña antes lo que trae y lo que choca.
 */
public final class ShareCommands {
    /** Nombres de cada tipo que se enseñan en la lista, como mucho. */
    private static final int LISTED = 24;

    private ShareCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("share").build());
        root.addChild(command("compartir").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(source -> EzCommandSupport.canAny(source, "komaedit.share"))
                .executes(ShareCommands::list);
        for (String export : new String[]{"export", "exportar"}) {
            command.then(literal(export).requires(EzCommandSupport.node(SharePermissions.EXPORT))
                    .executes(ShareCommands::list)
                    .then(argument("things", StringArgumentType.greedyString())
                            .suggests(ShareCommands::suggestThings)
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                ShareService.export(player, StringArgumentType.getString(context, "things"));
                                return 1;
                            }))));
        }
        for (String importName : new String[]{"import", "importar"}) {
            LiteralArgumentBuilder<ServerCommandSource> importing = literal(importName)
                    .requires(EzCommandSupport.node(SharePermissions.IMPORT));
            for (String file : new String[]{"archivo", "file"}) {
                importing.then(literal(file)
                        .executes(ShareCommands::files)
                        .then(argument("file", StringArgumentType.greedyString())
                                .suggests(fileSuggestions())
                                .executes(context -> EzCommandSupport.run(context, player -> {
                                    ShareService.importFile(player, StringArgumentType.getString(context, "file"));
                                    return 1;
                                }))));
            }
            importing.then(argument("code", StringArgumentType.greedyString())
                    .executes(context -> EzCommandSupport.run(context, player -> {
                        ShareService.importCode(player, StringArgumentType.getString(context, "code"));
                        return 1;
                    })));
            command.then(importing);
        }
        for (String files : new String[]{"files", "archivos"}) {
            command.then(literal(files).requires(EzCommandSupport.node(SharePermissions.IMPORT))
                    .executes(ShareCommands::files));
        }
        for (String accept : new String[]{"accept", "aceptar"}) {
            command.then(literal(accept).requires(EzCommandSupport.node(SharePermissions.IMPORT))
                    .executes(context -> accept(context, ""))
                    .then(argument("policy", StringArgumentType.word())
                            .suggests(EzCommandSupport.words(List.of("renombrar", "reemplazar", "saltar")))
                            .executes(context -> accept(context, StringArgumentType.getString(context, "policy")))));
        }
        for (String cancel : new String[]{"cancel", "cancelar"}) {
            command.then(literal(cancel).requires(EzCommandSupport.node(SharePermissions.IMPORT))
                    .executes(context -> EzCommandSupport.run(context, player -> {
                        if (!ShareService.cancel(player)) {
                            throw new IllegalArgumentException("No tienes ninguna importación pendiente");
                        }
                        Feedback.reply(player, "Importación cancelada: no se guardó nada");
                        return 1;
                    })));
        }
        return command;
    }

    private static int accept(CommandContext<ServerCommandSource> context, String policy)
            throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            ShareService.accept(player, policy);
            return 1;
        });
    }

    /** Lo que el jugador puede compartir, con un botón por cosa. */
    private static int list(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            ShareTarget target = new ShareTarget(player.getUuid(), player.getGameProfile().getName(), node -> true);
            boolean any = false;
            Feedback.reply(player, Text.literal("Pulsa una cosa para sacar su código, o escribe ")
                    .append(Feedback.fix("/ke share export tipo:nombre…", "/komaedit share export "))
                    .append(" para juntar varias."));
            for (ShareKind kind : ShareKinds.all()) {
                List<String> names;
                try {
                    names = kind.names(target);
                } catch (RuntimeException exception) {
                    continue;
                }
                if (names.isEmpty()) {
                    continue;
                }
                any = true;
                String prefix = kind.aliases().isEmpty() ? kind.id() : kind.aliases().getFirst();
                MutableText line = Text.literal("  " + capitalized(kind.plural()) + ": ").formatted(Formatting.YELLOW);
                for (int index = 0; index < Math.min(LISTED, names.size()); index++) {
                    String ref = prefix + ":" + names.get(index);
                    line.append(Feedback.button(names.get(index), "/komaedit share export " + ref, Formatting.WHITE,
                            "/komaedit share export " + ref)).append(" ");
                }
                if (names.size() > 1) {
                    line.append(Feedback.button("[todos]", "/komaedit share export " + prefix + ":*",
                            Formatting.AQUA, "Un paquete con " + names.size() + " " + kind.plural()));
                }
                player.sendMessage(line, false);
            }
            if (!any) {
                Feedback.reply(player, "Aún no tienes nada guardado que compartir: paletas, ruidos, presets, recetas"
                        + " o planos");
            }
            return 1;
        });
    }

    /** Los archivos de {@code config/komaedit/imports}, con un botón para importar cada uno. */
    private static int files(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            List<String> files = ShareService.importable();
            if (files.isEmpty()) {
                Feedback.reply(player, "No hay archivos en config/komaedit/imports: deja ahí un .kpack (o un .txt con"
                        + " un código) y vuelve a mirar");
                return 0;
            }
            MutableText line = Text.literal("Archivos para importar: ").formatted(Formatting.YELLOW);
            for (String file : files) {
                line.append(Feedback.button(file, "/komaedit share import archivo " + file, Formatting.WHITE,
                        "Ver qué trae " + file)).append(" ");
            }
            Feedback.reply(player, line);
            return files.size();
        });
    }

    private static CompletableFuture<Suggestions> suggestThings(
            CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int space = remaining.lastIndexOf(' ');
        String word = remaining.substring(space + 1).toLowerCase(Locale.ROOT);
        SuggestionsBuilder last = builder.createOffset(builder.getStart() + space + 1);
        ServerPlayerEntity player = context.getSource().getPlayer();
        int colon = word.indexOf(':');
        if (colon < 0) {
            for (ShareKind kind : ShareKinds.all()) {
                String prefix = kind.aliases().isEmpty() ? kind.id() : kind.aliases().getFirst();
                if (prefix.startsWith(word)) {
                    last.suggest(prefix + ":");
                }
            }
            for (String flag : List.of("-f", "-n")) {
                if (flag.startsWith(word) && !word.isEmpty()) {
                    last.suggest(flag);
                }
            }
            return last.buildFuture();
        }
        ShareKind kind = ShareKinds.find(word.substring(0, colon));
        if (kind == null || player == null) {
            return last.buildFuture();
        }
        String typed = word.substring(colon + 1);
        try {
            ShareTarget target = new ShareTarget(player.getUuid(), player.getGameProfile().getName(), node -> true);
            for (String name : kind.names(target)) {
                if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    last.suggest(word.substring(0, colon + 1) + name);
                }
            }
        } catch (RuntimeException ignored) {
            // Sin proyecto abierto no hay nada que sugerir.
        }
        if ("*".startsWith(typed)) {
            last.suggest(word.substring(0, colon + 1) + "*");
        }
        return last.buildFuture();
    }

    private static SuggestionProvider<ServerCommandSource> fileSuggestions() {
        return (context, builder) -> {
            try {
                for (String file : ShareService.importable()) {
                    if (file.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                        builder.suggest(file);
                    }
                }
            } catch (RuntimeException ignored) {
                // Sin carpeta no hay nada que sugerir.
            }
            return builder.buildFuture();
        };
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
