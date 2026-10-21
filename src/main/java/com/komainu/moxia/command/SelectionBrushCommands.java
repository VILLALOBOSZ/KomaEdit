package com.komainu.moxia.command;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.BrushMasks;
import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.brush.ez.BrushWorld;
import com.komainu.moxia.brush.ez.EzBrushType;
import com.komainu.moxia.brush.paint.PaintBrush;
import com.komainu.moxia.brush.paint.PaintConfig;
import com.komainu.moxia.brush.paint.PaintPalettes;
import com.komainu.moxia.brush.paint.PaintSelection;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.sculpt.SculptManager;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.terrain.HeightmapBrush;
import com.komainu.moxia.terrain.SurfacePalette;
import com.komainu.moxia.terrain.TerrainService;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Los pinceles de pintar y de relieve sobre la selección, con los mismos ajustes que con el pincel en la mano: así
 * cada herramienta de pintar o esculpir vale de las dos maneras (el interruptor «Pincel / Selección» del Studio).
 *
 * <ul>
 *     <li>{@code /komaedit pintar [paleta] [pendiente|altura|ruido|huella|natural] [fuerza] [banderas]}: las mismas
 *     del {@code pincel pintar}, sin radio. {@code natural} es lo que antes era {@code terreno pintar}: la paleta de
 *     superficie por reglas.</li>
 *     <li>{@code /komaedit relieve <sello> [altura] [modo] [paleta] [banderas]}: las mismas del
 *     {@code pincel heightmap}, con el sello estirado hasta cubrir la selección.</li>
 * </ul>
 */
public final class SelectionBrushCommands {
    /** El sello más grande que cabe en una pasada de pincel: 97 × 97 columnas. */
    public static final int MAX_STAMP_RADIUS = 48;

    private SelectionBrushCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : List.of("pintar", "paint")) {
            root.addChild(command(name, "brush.paint", "terrain.paint", SelectionBrushCommands::paint,
                    SelectionBrushCommands::paintExamples).build());
        }
        for (String name : List.of("relieve", "relief")) {
            root.addChild(command(name, "brush.heightmap", null, SelectionBrushCommands::relief,
                    SelectionBrushCommands::reliefExamples).build());
        }
    }

    private static List<String> paintExamples() {
        List<String> examples = new ArrayList<>(PaintPalettes.descriptions().keySet());
        examples.addAll(SurfacePalette.presets().keySet());
        examples.addAll(List.of("pendiente", "altura", "ruido", "huella", "natural", "60", "-h", "-s", "-u", "-j",
                "-d", "-t", "-c", "-x", "-b", "-m", "-y", "-a", "-n", "-z", "-e"));
        return examples;
    }

    private static List<String> reliefExamples() {
        List<String> examples = new ArrayList<>(TerrainService.catalog().names());
        examples.addAll(List.of("auto", "sumar", "restar", "maximo", "aplanar", "suavizar", "-i", "-r", "azar", "-x",
                "-s", "-c", "-m"));
        return examples;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name, String node, String other,
                                                                      BiFunction<ServerPlayerEntity, String, String> run,
                                                                      Supplier<List<String>> examples) {
        // Las sugerencias se piden al escribir: el catálogo de sellos aún no está al registrar las órdenes.
        SuggestionProvider<ServerCommandSource> suggestions = (context, builder) -> {
            String remaining = builder.getRemaining();
            var tail = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
            examples.get().forEach(tail::suggest);
            return tail.buildFuture();
        };
        return literal(name)
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                        && (KomaPermissions.canBuild(player, node) || other != null
                        && KomaPermissions.canBuild(player, other)))
                .executes(context -> execute(context, run, ""))
                .then(argument("opciones", StringArgumentType.greedyString())
                        .suggests(suggestions)
                        .executes(context -> execute(context, run, StringArgumentType.getString(context,
                                "opciones"))));
    }

    private static int execute(CommandContext<ServerCommandSource> context,
                               BiFunction<ServerPlayerEntity, String, String> run, String options)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            String message = run.apply(player, options);
            if (message != null) {
                context.getSource().sendFeedback(() -> Feedback.prefix().append(Text.literal(message)
                        .formatted(Formatting.GRAY)), false);
            }
            return 1;
        } catch (Unselected exception) {
            return EzCommandSupport.fail(context.getSource(), "Selecciona primero la zona con //pos1 y //pos2");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
    }

    /** Sin selección hecha (va sin comprobar por las lambdas). */
    private static final class Unselected extends RuntimeException {
        private Unselected(IncompleteRegionException cause) {
            super(cause);
        }
    }

    private static Region selection(ServerPlayerEntity player) {
        try {
            return WorldEditService.selection(player);
        } catch (IncompleteRegionException exception) {
            throw new Unselected(exception);
        }
    }

    /**
     * Si lo escrito pide el modo Natural, lo mismo sin esa palabra (la última, porque la paleta de serie también se
     * llama {@code natural}); si no, {@code null}.
     */
    static String withoutNatural(String text) {
        List<String> words = new ArrayList<>(Arrays.asList(text.strip().split("\\s+")));
        int found = -1;
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            if (word.length() == 2 && word.charAt(0) == '-' && PaintConfig.VALUED.contains(word.charAt(1))) {
                // Lo que va detrás de una bandera con valor (una máscara, una paleta) no es el modo.
                index++;
            } else if (PaintConfig.NATURAL_WORDS.contains(BrushOptions.normalize(word))) {
                found = index;
            }
        }
        if (found < 0) {
            return null;
        }
        words.remove(found);
        return String.join(" ", words).strip();
    }

    /** {@code pintar}: con {@code natural}, la paleta de superficie de siempre; si no, el pincel sobre la selección. */
    static String paint(ServerPlayerEntity player, String text) {
        String natural = withoutNatural(text);
        try {
            if (natural != null) {
                TerrainCommands.paint(player, natural);
                return null;
            }
        } catch (IncompleteRegionException exception) {
            throw new Unselected(exception);
        }
        KomaPermissions.requireBuild(player, "brush.paint");
        BrushOptions options = BrushOptions.parse(text, PaintConfig.SWITCHES, PaintConfig.VALUED);
        PaintBrush brush = PaintBrush.create(player, options, true);
        Region region = selection(player);
        SculptManager.runAsCommand(player, PaintSelection.of(brush, region, brush.mask(player)),
                EditRequests.current());
        return "Pintando la selección: " + brush.config().describe();
    }

    /** {@code relieve}: un sello estirado hasta cubrir la selección, sin tocar las columnas de fuera. */
    static String relief(ServerPlayerEntity player, String text) {
        KomaPermissions.requireBuild(player, "brush.heightmap");
        Region region = selection(player);
        BlockVector3 low = region.getMinimumPoint();
        BlockVector3 high = region.getMaximumPoint();
        int radius = (int) Math.ceil(Math.max(high.x() - low.x() + 1, high.z() - low.z() + 1) / 2.0);
        if (radius > MAX_STAMP_RADIUS) {
            throw new IllegalArgumentException("La selección es demasiado grande para un sello: como mucho "
                    + (MAX_STAMP_RADIUS * 2 + 1) + " × " + (MAX_STAMP_RADIUS * 2 + 1) + " columnas (usa el pincel)");
        }
        BrushOptions options = EzBrushType.HEIGHTMAP.options(text);
        HeightmapBrush.Config config = HeightmapBrush.parse(options, Math.max(1, radius));
        BrushMask written = config.mask() == null ? null
                : BrushMasks.forClick(BrushMasks.fixed(config.mask()), player, config.mask());
        int x = Math.floorDiv(low.x() + high.x(), 2);
        int z = Math.floorDiv(low.z() + high.z(), 2);
        int ground = player.getServerWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockVector3 center = BlockVector3.at(x, Math.clamp(ground, low.y(), high.y()), z);
        SculptManager.runAsCommand(player, HeightmapBrush.operation(config, config.mode(), center,
                player.getYaw(), config.seed(), 0, new Columns(region, written)), EditRequests.current());
        return "Relieve " + config.stamp().name() + " sobre la selección (radio " + radius + ")";
    }

    /** Solo las columnas de la selección (y las de la máscara escrita, si hay). */
    private record Columns(Region region, BrushMask mask) implements BrushMask {
        @Override
        public boolean matches(BrushWorld world, int x, int y, int z) {
            int inside = Math.clamp(y, region.getMinimumPoint().y(), region.getMaximumPoint().y());
            return region.contains(BlockVector3.at(x, inside, z)) && (mask == null || mask.matches(world, x, y, z));
        }

        @Override
        public void bind(WorldSnapshot snapshot) {
            if (mask != null) {
                mask.bind(snapshot);
            }
        }
    }
}
