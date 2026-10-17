package com.komainu.moxia.command;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.noise.SavedNoises;
import com.komainu.moxia.persistence.NamedDefinitions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit noise} (alias {@code ruido}): ruidos guardados por jugador y mundo, como {@code //eznoise}. Un ruido
 * guardado se usa por su nombre (o {@code #nombre}) en cualquier sitio que acepte un ruido.
 */
public final class NoiseCommands {
    private NoiseCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("noise").build());
        root.addChild(command("ruido").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .executes(context -> list(context, "all"))
                .then(literal("list")
                        .executes(context -> list(context, "all"))
                        .then(argument("set", StringArgumentType.word())
                                .suggests(EzCommandSupport.words(List.of("all", "default", "mine")))
                                .executes(context -> list(context, StringArgumentType.getString(context, "set")))))
                .then(literal("save").then(argument("args", StringArgumentType.greedyString())
                        .executes(NoiseCommands::save)))
                .then(literal("delete").then(argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player != null) {
                                try {
                                    SavedNoises.store(player.getUuid()).all().keySet().forEach(builder::suggest);
                                } catch (RuntimeException ignored) {
                                    // Sin proyecto abierto no hay nada que sugerir.
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(NoiseCommands::delete)))
                .then(literal("print").then(argument("noise", StringArgumentType.greedyString())
                        .suggests(EzCommandSupport.words(NoiseSpec.PRESETS.keySet()))
                        .executes(NoiseCommands::print)));
    }

    private static int list(CommandContext<ServerCommandSource> context, String set) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String which = set.toLowerCase(Locale.ROOT);
            if (!List.of("all", "default", "mine", "todos", "serie", "mios", "míos").contains(which)) {
                throw new IllegalArgumentException("Usa list all, list default o list mine");
            }
            if (!which.equals("mine") && !which.equals("mios") && !which.equals("míos")) {
                EzCommandSupport.send(player, names("De serie: ", NoiseSpec.PRESETS.keySet(), "", Formatting.WHITE));
            }
            if (!which.equals("default") && !which.equals("serie")) {
                Map<String, String> saved = SavedNoises.store(player.getUuid()).all();
                if (saved.isEmpty()) {
                    EzCommandSupport.info(player, "No tienes ruidos guardados: /komaedit noise save <nombre> <ruido>");
                } else {
                    EzCommandSupport.send(player, names("Tuyos: ", saved.keySet(), "#", Formatting.AQUA));
                }
            }
            return 1;
        });
    }

    private static MutableText names(String title, Iterable<String> names, String prefix, Formatting color) {
        MutableText line = Text.literal(title).formatted(Formatting.YELLOW);
        boolean first = true;
        for (String name : names) {
            if (!first) {
                line.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
            }
            first = false;
            line.append(EzCommandSupport.button(prefix + name, "/komaedit noise print " + prefix + name, color));
        }
        return line;
    }

    private static int save(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of())
                    .allow("f");
            String name = NamedDefinitions.normalizeName(tokens.require(0, "el nombre del ruido"));
            if (NoiseSpec.isReservedName(name)) {
                throw new IllegalArgumentException(name + " es un ruido de serie o un tipo de ruido; elige otro nombre");
            }
            if (tokens.positional().size() < 2) {
                throw new IllegalArgumentException("Uso: /komaedit noise save <nombre> <ruido> [-f]");
            }
            String definition = String.join("", tokens.positional().subList(1, tokens.positional().size()));
            NoiseSpec noise = SavedNoises.parse(player.getUuid(), definition);
            boolean replaced = SavedNoises.store(player.getUuid()).put(name, noise.toString(), tokens.flag("f"));
            EzCommandSupport.ok(player, (replaced ? "Ruido sustituido: " : "Ruido guardado: ") + name + " = "
                    + noise);
            return 1;
        });
    }

    private static int delete(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String name = NamedDefinitions.normalizeName(StringArgumentType.getString(context, "name"));
            if (!SavedNoises.store(player.getUuid()).delete(name)) {
                throw new IllegalArgumentException("No tienes ningún ruido llamado " + name);
            }
            EzCommandSupport.ok(player, "Ruido borrado: " + name);
            return 1;
        });
    }

    private static int print(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            NoiseSpec noise = SavedNoises.parse(player.getUuid(), StringArgumentType.getString(context, "noise"));
            MutableText text = Text.literal(noise.type().displayName()).formatted(Formatting.YELLOW);
            if (noise.parameters().isEmpty()) {
                text.append(Text.literal(" con los valores por defecto").formatted(Formatting.GRAY));
            }
            for (Map.Entry<String, String> entry : noise.parameters().entrySet()) {
                text.append(Text.literal("\n  " + entry.getKey() + ": ").formatted(Formatting.GRAY))
                        .append(Text.literal(entry.getValue()).formatted(Formatting.WHITE));
            }
            text.append(Text.literal("\n  ").append(EzCommandSupport.copyable(noise.toString(), noise.toString(),
                    Formatting.AQUA)));
            EzCommandSupport.send(player, text);
            return 1;
        });
    }
}
