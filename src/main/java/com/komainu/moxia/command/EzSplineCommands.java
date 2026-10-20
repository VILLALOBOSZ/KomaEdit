package com.komainu.moxia.command;

import com.komainu.moxia.brush.place.PlacementTemplate;
import com.komainu.moxia.brush.place.StructureSource;
import com.komainu.moxia.brush.place.StructureSources;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.edit.SmoothBlockStates;
import com.komainu.moxia.noise.SavedNoises;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.recipe.StructureBendService;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.tube.EzSpline;
import com.komainu.moxia.shape.tube.TubeBender;
import com.komainu.moxia.shape.tube.TubeCurve;
import com.komainu.moxia.shape.tube.TubeFigures;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit ezspline} (también {@code ezsp} y {@code spline}): la sintaxis de {@code //ezspline} de ezEdits
 * sobre las figuras de espacio de tubo. {@link EzSpline} lee el texto; aquí se resuelven patrón, paleta o estructura,
 * se calcula la figura en un worker y se coloca como una edición por lotes que deshace {@code //undo}.
 */
final class EzSplineCommands {
    private static final List<String> SUBCOMMANDS = List.of("2d", "3d", "basic", "rope", "noise", "expression",
            "structure");

    private EzSplineCommands() {
    }

    static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .executes(context -> run(context, ""))
                .then(argument("spline", StringArgumentType.greedyString())
                        .suggests(EzCommandSupport.words(SUBCOMMANDS))
                        .executes(context -> run(context, StringArgumentType.getString(context, "spline"))));
    }

    private static int run(CommandContext<ServerCommandSource> context, String input) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            EzSpline.Request request = EzSpline.parse(input, SavedNoises.resolver(player.getUuid()));
            return switch (request.kind()) {
                case HELP -> help(player, request);
                case PATTERN -> MoxiaCommands.enqueueShape(context, player, true, request.material(),
                        request.parameters(), request.materials());
                case PALETTE -> palette(player, request);
                case STRUCTURE -> structure(player, request);
            };
        } catch (IncompleteRegionException exception) {
            return EzCommandSupport.fail(context.getSource(),
                    "Marca al menos dos puntos con P o selecciona con WorldEdit (//pos1 y //pos2, o //sel convex)");
        } catch (InputParseException | IllegalArgumentException | IllegalStateException exception) {
            return EzCommandSupport.fail(context.getSource(), readable(exception));
        }
    }

    private static int help(ServerPlayerEntity player, EzSpline.Request request) {
        for (String line : request.help()) {
            EzCommandSupport.send(player, Text.literal(line).formatted(Formatting.GRAY));
        }
        return 1;
    }

    /** {@code noise} y {@code expression}: cada bloque toma la entrada de la paleta que marca el valor de la figura. */
    private static int palette(ServerPlayerEntity player, EzSpline.Request parsed) throws IncompleteRegionException {
        GeneratorCommands.Palette palette = GeneratorCommands.Palette.resolve(player, parsed.material());
        EzSpline.Request request = parsed.withPaletteSize(palette.size());
        SmoothBlockStates.validate(request.materials());
        KomaPermissions.requireBuild(player, request.parameters().shape().id());
        PathSnapshot path = WorldEditService.resolvePath(player);
        UUID playerId = player.getUuid();
        var server = player.getServer();
        String description = request.describe();
        EzCommandSupport.info(player, description + ": calculando fuera del hilo del servidor…");
        int entries = palette.size();
        AsyncEngine.submit(
                playerId,
                () -> {
                    SampledPath sampled = SampledPath.createSmooth(path.points(), path.closed());
                    PackedBlockPlan plan = ShapeGenerator.generatePlan(sampled, request.parameters())
                            .withPieceMaterials(request.materials());
                    TubeFigures.MaterialProbe probe = TubeFigures.materialProbe(sampled, request.parameters(),
                            TubeCurve.NONE);
                    Long2IntOpenHashMap indices = new Long2IntOpenHashMap(plan.size());
                    for (int index = 0; index < plan.size(); index++) {
                        if ((index & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
                            throw new CancellationException();
                        }
                        BlockVector3 position = plan.position(index);
                        // Las piezas que rellenan huecos caen fuera de la figura: toman la entrada de la superficie.
                        double value = probe.value(position, 1.0);
                        indices.put(plan.packedPosition(index), paletteIndex(value, entries));
                    }
                    return new Planned(plan, indices);
                },
                planned -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    try {
                        int queued = BatchEditService.submitStyled(current, planned.plan(), position -> {
                            int index = planned.indices().getOrDefault(
                                    PackedBlockPlan.pack(position.x(), position.y(), position.z()), entries - 1);
                            return palette.state(index, 0);
                        }, description);
                        int pieces = planned.plan().pieceCount();
                        if (!PlanPreviews.holds(playerId, planned.plan())) {
                            EzCommandSupport.queued(current, description + " en cola: " + queued + " bloques"
                                    + (pieces > 0 ? " · " + pieces + " losas/escaleras" : "")
                                    + " · //undo para deshacer");
                        }
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        EzCommandSupport.fail(current.getCommandSource(), readable(exception));
                    }
                }),
                throwable -> report(server, playerId, description, throwable)
        );
        return 1;
    }

    /** Entrada de la paleta para un valor de figura en (0, 1]: como en ezEdits, el tramo que lo contiene. */
    static int paletteIndex(double value, int entries) {
        int index = (int) Math.ceil(Math.clamp(value, 0.0, 1.0) * entries) - 1;
        return Math.clamp(index, 0, entries - 1);
    }

    private record Planned(PackedBlockPlan plan, Long2IntOpenHashMap indices) {
    }

    private static int structure(ServerPlayerEntity player, EzSpline.Request request)
            throws IncompleteRegionException {
        EzSpline.Structure spline = request.structure();
        KomaPermissions.requireBuild(player, "bend");
        String name = spline.source().contains("(")
                ? spline.source().substring(0, spline.source().indexOf('('))
                : spline.source();
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("cl") || normalized.startsWith("sc") || normalized.equals("portapapeles")
                || normalized.equals("esquema")) {
            KomaPermissions.require(player, KomaPermissions.CLIPBOARD);
        }
        StructureSource source = StructureSources.parse(spline.source());
        PlacementTemplate template = source.prepare(player, ThreadLocalRandom.current().nextLong());
        // La escala, el giro y la torsión se aplican al doblar, punto a punto: la plantilla va a su tamaño.
        TubeBender.Section section = spline.section(StructureBendService.inherentRadius(template));
        StructureBendService.Captured captured = StructureBendService.fromPlacement(template, 1.0, 1.0);
        PathSnapshot path = WorldEditService.resolvePath(player);
        UUID playerId = player.getUuid();
        var server = player.getServer();
        String description = "Spline " + source.describe();
        EzCommandSupport.info(player, description + ": doblando " + captured.blocks() + " bloques a lo largo del "
                + "recorrido…");
        AsyncEngine.submit(
                playerId,
                () -> StructureBendService.plan(captured, path, spline.bend(), section),
                plan -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    try {
                        int queued = BatchEditService.submit(current, plan, "air", description);
                        if (!PlanPreviews.holds(playerId, plan)) {
                            EzCommandSupport.queued(current, description + " en cola: " + queued
                                    + " bloques · //undo para deshacer");
                        }
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        EzCommandSupport.fail(current.getCommandSource(), readable(exception));
                    }
                }),
                throwable -> report(server, playerId, description, throwable)
        );
        return 1;
    }

    private static void report(net.minecraft.server.MinecraftServer server, UUID playerId, String description,
                               Throwable throwable) {
        server.execute(() -> {
            ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
            if (current != null && !(throwable instanceof CancellationException)) {
                EzCommandSupport.fail(current.getCommandSource(), description + ": " + readable(throwable));
            }
        });
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
