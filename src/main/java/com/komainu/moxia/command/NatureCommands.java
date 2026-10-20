package com.komainu.moxia.command;

import com.komainu.moxia.nature.NaturePresets;
import com.komainu.moxia.nature.NatureService;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.RockType;
import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.nature.TreeSpecies;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.NamedDefinitions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit tree} (alias {@code arbol}) y {@code /komaedit rock} (alias {@code roca}): árboles y rocas
 * procedurales donde se mira o en cada punto marcado, con sus presets guardados.
 */
public final class NatureCommands {
    private static final String[] TREE_FLAGS = {"-g", "-b", "-d", "-w", "-i", "-c", "-s", "-t", "-h", "-r", "-m",
            "-p"};
    private static final String[] ROCK_FLAGS = {"-t", "-b", "-u", "-a", "-e", "-r", "-g", "-c", "-h", "-i", "-s",
            "-m", "-p"};

    private NatureCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(family("tree", Kind.TREE).build());
        root.addChild(family("arbol", Kind.TREE).build());
        root.addChild(family("rock", Kind.ROCK).build());
        root.addChild(family("roca", Kind.ROCK).build());
    }

    private enum Kind {
        TREE("tree", "árbol", "árboles"),
        ROCK("rock", "roca", "rocas");

        private final String node;
        private final String singular;
        private final String plural;

        Kind(String node, String singular, String plural) {
            this.node = node;
            this.singular = singular;
            this.plural = plural;
        }

        NamedDefinitions store(UUID playerId) {
            return this == TREE ? NaturePresets.trees(playerId) : NaturePresets.rocks(playerId);
        }

        List<String> builtIn() {
            List<String> names = new ArrayList<>();
            if (this == TREE) {
                for (TreeSpecies species : TreeSpecies.values()) {
                    names.add(species.id());
                }
            } else {
                for (RockType type : RockType.values()) {
                    names.add(type.id());
                }
            }
            return names;
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> family(String name, Kind kind) {
        String permission = KomaPermissions.BUILD + "." + kind.node;
        return literal(name)
                .requires(EzCommandSupport.node(permission))
                .executes(context -> start(context, kind, ""))
                .then(literal("save").then(argument("preset", StringArgumentType.greedyString())
                        .executes(context -> save(context, kind))))
                .then(literal("guardar").then(argument("preset", StringArgumentType.greedyString())
                        .executes(context -> save(context, kind))))
                .then(literal("list").executes(context -> list(context, kind)))
                .then(literal("lista").executes(context -> list(context, kind)))
                .then(literal("delete").then(argument("nombre", StringArgumentType.word())
                        .suggests(saved(kind)).executes(context -> delete(context, kind))))
                .then(literal("borrar").then(argument("nombre", StringArgumentType.word())
                        .suggests(saved(kind)).executes(context -> delete(context, kind))))
                .then(literal("cancel").executes(NatureCommands::cancel))
                .then(literal("cancelar").executes(NatureCommands::cancel))
                .then(argument("ajustes", StringArgumentType.greedyString())
                        .suggests(options(kind))
                        .executes(context -> start(context, kind,
                                StringArgumentType.getString(context, "ajustes"))));
    }

    private static int start(CommandContext<ServerCommandSource> context, Kind kind, String input)
            throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String started = kind == Kind.TREE ? NatureService.tree(player, input) : NatureService.rock(player, input);
            EzCommandSupport.info(player, started + "…");
            return 1;
        });
    }

    private static int cancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            if (!NatureService.cancel(player)) {
                throw new IllegalArgumentException("No hay nada calculándose");
            }
            EzCommandSupport.send(player, Text.literal("Cálculo cancelado").formatted(Formatting.YELLOW));
            return 1;
        });
    }

    /** {@code save <nombre> <ajustes…> [-f]}: comprueba los ajustes antes de guardarlos. */
    private static int save(CommandContext<ServerCommandSource> context, Kind kind) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String text = StringArgumentType.getString(context, "preset").strip();
            boolean overwrite = false;
            List<String> words = new ArrayList<>(List.of(text.split("\\s+")));
            if (words.remove("-f")) {
                overwrite = true;
            }
            if (words.size() < 2) {
                throw new IllegalArgumentException("Uso: /komaedit " + kind.node + " save <nombre> <ajustes> [-f]");
            }
            String name = NamedDefinitions.normalizeName(words.removeFirst());
            if (kind.builtIn().contains(name) || (kind == Kind.TREE ? TreeSpecies.find(name) != null
                    : RockType.find(name) != null)) {
                throw new IllegalArgumentException(name + " ya es un nombre de serie; elige otro");
            }
            String settings = String.join(" ", words);
            Function<String, String> none = preset -> null;
            String summary = kind == Kind.TREE ? TreeSettings.parse(settings, none).describe()
                    : RockSettings.parse(settings, none).describe();
            boolean replaced = kind.store(player.getUuid()).put(name, settings, overwrite);
            EzCommandSupport.ok(player, (replaced ? "Preset sustituido: #" : "Preset guardado: #") + name + " = "
                    + settings + " (" + summary + ")");
            return 1;
        });
    }

    private static int list(CommandContext<ServerCommandSource> context, Kind kind) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            EzCommandSupport.send(player, Text.literal("De serie: ").formatted(Formatting.YELLOW)
                    .append(Text.literal(String.join(", ", kind.builtIn())).formatted(Formatting.WHITE)));
            Map<String, String> saved = kind.store(player.getUuid()).all();
            if (saved.isEmpty()) {
                EzCommandSupport.info(player, "No tienes presets de " + kind.plural + ": /komaedit " + kind.node
                        + " save <nombre> <ajustes>");
                return 1;
            }
            MutableText line = Text.literal("Tuyos: ").formatted(Formatting.YELLOW);
            boolean first = true;
            for (Map.Entry<String, String> entry : saved.entrySet()) {
                if (!first) {
                    line.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                }
                first = false;
                line.append(EzCommandSupport.copyable("#" + entry.getKey(), "#" + entry.getKey(), Formatting.AQUA))
                        .append(Text.literal(" (" + entry.getValue() + ")").formatted(Formatting.GRAY));
            }
            EzCommandSupport.send(player, line);
            return 1;
        });
    }

    private static int delete(CommandContext<ServerCommandSource> context, Kind kind) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            String name = NamedDefinitions.normalizeName(StringArgumentType.getString(context, "nombre"));
            if (!kind.store(player.getUuid()).delete(name)) {
                throw new IllegalArgumentException("No tienes ningún preset de " + kind.singular + " llamado " + name);
            }
            EzCommandSupport.ok(player, "Preset borrado: #" + name);
            return 1;
        });
    }

    private static SuggestionProvider<ServerCommandSource> saved(Kind kind) {
        return (context, builder) -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null) {
                try {
                    kind.store(player.getUuid()).all().keySet().forEach(builder::suggest);
                } catch (RuntimeException ignored) {
                    // Sin proyecto abierto no hay nada que sugerir.
                }
            }
            return builder.buildFuture();
        };
    }

    /** Primero especies (o tipos) y presets; después, las banderas. */
    private static SuggestionProvider<ServerCommandSource> options(Kind kind) {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            if (!remaining.contains(" ")) {
                kind.builtIn().forEach(tail::suggest);
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    try {
                        kind.store(player.getUuid()).all().keySet().forEach(name -> tail.suggest("#" + name));
                    } catch (RuntimeException ignored) {
                        // Sin proyecto abierto no hay presets.
                    }
                }
            } else {
                for (String flag : kind == Kind.TREE ? TREE_FLAGS : ROCK_FLAGS) {
                    tail.suggest(flag);
                }
            }
            return tail.buildFuture();
        };
    }
}
