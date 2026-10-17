package com.komainu.moxia.command;

import com.komainu.moxia.glass.GlassGradientRequest;
import com.komainu.moxia.glass.GlassGradientService;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.region.MossOperation;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperation;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.RegionRules;
import com.komainu.moxia.region.SlabMergeOperation;
import com.komainu.moxia.region.StateCycleOperation;
import com.komainu.moxia.region.VinesOperation;
import com.komainu.moxia.texture.TextureCommandParser;
import com.komainu.moxia.texture.TextureContext;
import com.komainu.moxia.texture.TextureOperation;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Texturizado y comandos de región con la sintaxis de ezEdits: {@code texture} (los doce modos de
 * {@code //eztexture}), {@code vines}, {@code moss}, {@code slabmerge}, {@code statecycle} y la vidriera
 * {@code glassgradient}. Todos leen la selección con {@link RegionOperationService} (salvo la vidriera, que se
 * coloca delante del jugador), calculan en un worker y se deshacen con //undo.
 */
public final class TextureCommands {
    private static final SplittableRandom SEEDS = new SplittableRandom();

    private TextureCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("komaedit")
                .then(textureCommand("texture"))
                .then(textureCommand("textura"))
                .then(simple("vines", TextureCommands::vines))
                .then(simple("enredaderas", TextureCommands::vines))
                .then(simple("moss", TextureCommands::moss))
                .then(simple("musgo", TextureCommands::moss))
                .then(simple("slabmerge", TextureCommands::slabMerge))
                .then(simple("unirlosas", TextureCommands::slabMerge))
                .then(simple("statecycle", TextureCommands::stateCycle))
                .then(simple("ciclarestado", TextureCommands::stateCycle))
                .then(simple("glassgradient", TextureCommands::glassGradient))
                .then(simple("vidriera", TextureCommands::glassGradient)));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> textureCommand(String name) {
        return literal(name)
                .requires(TextureCommands::canBuild)
                .executes(TextureCommands::textureHelp)
                .then(argument("texture_mode", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (TextureCommandParser.Mode mode : TextureCommandParser.Mode.values()) {
                                builder.suggest(mode.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> texture(context, ""))
                        .then(argument("texture_arguments", StringArgumentType.greedyString())
                                .executes(context -> texture(context,
                                        StringArgumentType.getString(context, "texture_arguments")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> simple(String name, Action action) {
        return literal(name)
                .requires(TextureCommands::canBuild)
                .executes(context -> run(context, action, ""))
                .then(argument("region_arguments", StringArgumentType.greedyString())
                        .executes(context -> run(context, action,
                                StringArgumentType.getString(context, "region_arguments"))));
    }

    private static int texture(CommandContext<ServerCommandSource> context, String rest) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            long fallbackSeed = SEEDS.nextLong(1L, 1_000_000_000L);
            TextureCommandParser.Request request = TextureCommandParser.parse(
                    StringArgumentType.getString(context, "texture_mode"), rest, fallbackSeed);
            RegionMasks.Bound mask = request.mode().usesMask() ? RegionMasks.parse(player, request.mask()) : null;
            List<Pattern> palette = palette(player, request.palette());
            TextureContext.Settings settings = TextureContext.Settings.at(player.getX(), player.getEyeY(),
                    player.getZ(), request.seed());
            TextureOperation operation = new TextureOperation(request, mask, palette, settings,
                    notice(player.getServer(), player.getUuid(), request.mode().displayName()));
            checkVolume(player, operation.padding());
            String description = request.mode().displayName() + (usesSeed(request) ? " (semilla " + request.seed()
                    + ")" : "");
            RegionOperationService.submit(player, operation.padding(), description, operation);
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Selecciona primero la zona con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static boolean usesSeed(TextureCommandParser.Request request) {
        return switch (request.mode()) {
            case CELLS, FLOW, NOISE, ADVANCED -> true;
            default -> false;
        };
    }

    /** {@code vines <máscara> <patrón> [porcentaje] [mínimo] [máximo] [-s semilla]}. */
    private static void vines(ServerPlayerEntity player, String rest) throws IncompleteRegionException {
        Words words = new Words(rest);
        RegionMasks.Bound mask = RegionMasks.parse(player, words.required("la máscara de los bloques de los que cuelgan"));
        Pattern pattern = pattern(player, words.required("el patrón, por ejemplo vine o weeping_vines"));
        double chance = RegionRules.percentage(words.optional("10"));
        int minimum = words.optionalInt(2, "el largo mínimo");
        int maximum = words.optionalInt(Math.max(5, minimum), "el largo máximo");
        long seed = words.seed();
        words.finish();
        VinesOperation operation = new VinesOperation(mask, pattern, chance, minimum, maximum, seed);
        RegionOperationService.submit(player, operation.padding(), "Enredaderas (semilla " + seed + ")", operation);
    }

    /** {@code moss <patrón> [cantidad] [radios] [iteraciones] [-s semilla]}. */
    private static void moss(ServerPlayerEntity player, String rest) throws IncompleteRegionException {
        Words words = new Words(rest);
        Pattern pattern = pattern(player, words.required("el patrón, por ejemplo moss_block o moss_carpet"));
        double amount = Math.clamp(words.optionalNumber(2.0, "la cantidad"), 0.0, 20.0);
        int[] radii = RegionRules.radii(words.optional("1"));
        int iterations = Math.clamp(words.optionalInt(5, "las iteraciones"), 0, 32);
        long seed = words.seed();
        words.finish();
        var state = FabricAdapter.adapt(pattern.applyBlock(BlockVector3.ZERO).toImmutableState());
        boolean cover = !state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        MossOperation operation = new MossOperation(pattern, cover, amount, radii, iterations, seed);
        RegionOperationService.submit(player, 1, "Musgo (semilla " + seed + ")", operation);
    }

    /** {@code slabmerge <máscara> [-b] [-t]}. */
    private static void slabMerge(ServerPlayerEntity player, String rest) throws IncompleteRegionException {
        Words words = new Words(rest);
        boolean bottom = words.flag("-b");
        boolean top = words.flag("-t");
        RegionMasks.Bound mask = RegionMasks.parse(player, words.optional("*"));
        words.finish();
        RegionOperationService.submit(player, 0, "Unir losas", new SlabMergeOperation(mask, bottom, top));
    }

    /** {@code statecycle <máscara> <propiedad> [pasos]}. */
    private static void stateCycle(ServerPlayerEntity player, String rest) throws IncompleteRegionException {
        Words words = new Words(rest);
        RegionMasks.Bound mask = RegionMasks.parse(player, words.required("la máscara, por ejemplo #existing"));
        String property = words.required("la propiedad que ciclar, por ejemplo facing").toLowerCase(Locale.ROOT);
        int steps = words.optionalInt(1, "los pasos");
        words.finish();
        RegionOperationService.submit(player, 0, "Ciclar " + property, new StateCycleOperation(mask, property, steps));
    }

    /** {@code glassgradient <inicio> [fin] <capas> [longitud] [calidad] [dirección] [-c fondo] [-b] [-s]}. */
    private static void glassGradient(ServerPlayerEntity player, String rest) {
        GlassGradientService.start(player, GlassGradientRequest.parse(rest));
    }

    private static int run(CommandContext<ServerCommandSource> context, Action action, String rest)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            action.run(player, rest);
            return 1;
        } catch (IncompleteRegionException exception) {
            return fail(context.getSource(), "Selecciona primero la zona con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(context.getSource(), exception.getMessage());
        }
    }

    private static List<Pattern> palette(ServerPlayerEntity player, String input) {
        List<String> entries = PaletteSpec.parse(input);
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        List<Pattern> patterns = new ArrayList<>(entries.size());
        for (String entry : entries) {
            patterns.add(WorldEditService.parsePatternUnchecked(context, entry));
        }
        return patterns;
    }

    private static Pattern pattern(ServerPlayerEntity player, String input) {
        return WorldEditService.parsePatternUnchecked(WorldEditService.context(player), input);
    }

    /** Antes de leer nada: la selección con su margen tiene que caber en las tablas de la textura. */
    private static void checkVolume(ServerPlayerEntity player, int padding) throws IncompleteRegionException {
        Region selection = WorldEditService.selection(player);
        BlockVector3 size = selection.getMaximumPoint().subtract(selection.getMinimumPoint());
        int margin = Math.clamp(padding, 0, 32);
        long volume = (long) (size.x() + 1 + 2 * margin) * (size.y() + 1 + 2 * margin) * (size.z() + 1 + 2 * margin);
        if (volume > TextureContext.MAX_VOLUME) {
            throw new IllegalArgumentException("La selección es demasiado grande para texturizar (" + volume
                    + " bloques con su margen de " + margin + "; máximo " + TextureContext.MAX_VOLUME + ")");
        }
    }

    private static java.util.function.Consumer<String> notice(MinecraftServer server, UUID playerId, String what) {
        return text -> server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(prefix().append(Text.literal(what + ": " + text).formatted(Formatting.YELLOW)),
                        false);
            }
        });
    }

    private static int textureHelp(CommandContext<ServerCommandSource> context) {
        String[] lines = {
                "/komaedit texture <modo> …  (alias textura; como //eztexture de ezEdits)",
                "ambient|curvature <máscara> <paleta> [radio] [brillo] [contraste] [-w]",
                "axisgradient <máscara> <paleta> [x|y|z] [-r] [-w] · noise <máscara> <paleta> [ruido] [-z escala] [-s semilla]",
                "cells <máscara> <paleta> <cantidad> [brillo] [contraste] [-s semilla] [-r repulsión]",
                "flow <máscara> <paleta> [exposición] [iteraciones] [velocidad] [escalar] [ruido] [-m [peso]] [-g [dir]] [-f]",
                "pointlight <máscara> <paleta> [alcance] [radio] [intervalo] [-l x,y,z] [-o oclusión] [-r radio] [-f]",
                "sunlight <máscara> <paleta> [radio] [intervalo] [-l dir] [-o oclusión] [-r radio] · blocklight <máscara> <paleta> [-v] [-s] [-l]",
                "blend <paleta> [radio] [-v] [-w] · shift <paleta> [desplazamiento] · advanced <máscara> <paleta> <textura>",
                "Todos: -w respeta escaleras y losas, -d 0–1 tramado. Ej.: /komaedit texture ambient #existing ##Grayscale 3",
                "Región: vines, moss, slabmerge, statecycle · Vidriera: /komaedit glassgradient #3A7FD2 #E04080 4 16"
        };
        for (String line : lines) {
            context.getSource().sendFeedback(() -> Text.literal(line).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static boolean canBuild(ServerCommandSource source) {
        return source.getEntity() instanceof ServerPlayerEntity player
                && KomaPermissions.canAccess(player)
                && KomaPermissions.can(player, KomaPermissions.BUILD);
    }

    private static MutableText prefix() {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD);
    }

    private static int fail(ServerCommandSource source, String message) {
        source.sendError(prefix().append(Text.literal(
                message == null || message.isBlank() ? "Operación inválida" : message).formatted(Formatting.RED)));
        return 0;
    }

    @FunctionalInterface
    private interface Action {
        void run(ServerPlayerEntity player, String rest) throws IncompleteRegionException;
    }

    /** Argumentos de los comandos de región: posicionales y banderas {@code -b}, {@code -t}, {@code -s n}. */
    private static final class Words {
        private final List<String> positional = new ArrayList<>();
        private final List<String> flags = new ArrayList<>();
        private String seed;
        private int next;

        Words(String text) {
            String trimmed = text == null ? "" : text.trim();
            String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
            for (int index = 0; index < parts.length; index++) {
                String part = parts[index];
                String lower = part.toLowerCase(Locale.ROOT);
                if (lower.equals("-s") || lower.equals("-seed") || lower.equals("-semilla")) {
                    if (index + 1 >= parts.length) {
                        throw new IllegalArgumentException("Falta el valor de " + part);
                    }
                    seed = parts[++index];
                } else if (lower.matches("-[a-z]+")) {
                    flags.add(lower);
                } else {
                    positional.add(part);
                }
            }
        }

        boolean flag(String name) {
            return flags.remove(name);
        }

        String required(String what) {
            if (next >= positional.size()) {
                throw new IllegalArgumentException("Falta " + what);
            }
            return positional.get(next++);
        }

        String optional(String fallback) {
            return next < positional.size() ? positional.get(next++) : fallback;
        }

        double optionalNumber(double fallback, String what) {
            String value = optional(null);
            if (value == null) {
                return fallback;
            }
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("«" + value + "» no es un número válido para " + what);
            }
        }

        int optionalInt(int fallback, String what) {
            return (int) Math.round(optionalNumber(fallback, what));
        }

        long seed() {
            if (seed == null) {
                return SEEDS.nextLong(1L, 1_000_000_000L);
            }
            try {
                return Long.parseLong(seed);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("«" + seed + "» no es una semilla válida");
            }
        }

        void finish() {
            if (!flags.isEmpty()) {
                throw new IllegalArgumentException("Bandera desconocida: " + flags.getFirst());
            }
            if (next < positional.size()) {
                throw new IllegalArgumentException("Sobra «" + String.join(" ", positional.subList(next,
                        positional.size())) + "»");
            }
        }
    }
}
