package com.komainu.moxia.recipe;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.PendingBuilds;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.gizmo.GhostModel;
import com.komainu.moxia.gizmo.GhostService;
import com.komainu.moxia.gizmo.GhostTransform;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.gradient.GradientService;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.shape.smooth.SmoothPieces;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class RecipeBuildService {
    private static final int MAX_INSTANCE_BLOCKS = 1_500_000;

    private RecipeBuildService() {
    }

    public static void submit(ServerPlayerEntity player, ProceduralRecipe recipe)
            throws IncompleteRegionException, InputParseException {
        submit(player, recipe, "", false, false);
    }

    /**
     * Construir desde la vista previa en vivo del Studio; con {@code quiet} (al rehacerla) no se anuncia en el chat:
     * el Studio ya lo enseña y saldría una línea por cada ajuste tocado.
     */
    public static void submit(ServerPlayerEntity player, ProceduralRecipe recipe, boolean quiet)
            throws IncompleteRegionException, InputParseException {
        submit(player, recipe, "", false, quiet);
    }

    public static void update(ServerPlayerEntity player, ProceduralRecipe recipe, String instanceId)
            throws InputParseException {
        try {
            submit(player, recipe, instanceId, true, false);
        } catch (IncompleteRegionException exception) {
            throw new IllegalStateException("La trayectoria guardada de la instancia está incompleta", exception);
        }
    }

    private static void submit(
            ServerPlayerEntity player,
            ProceduralRecipe recipe,
            String requestedInstanceId,
            boolean update,
            boolean quiet
    ) throws IncompleteRegionException, InputParseException {
        validateMaterials(player, recipe);
        UUID playerId = player.getUuid();
        ProceduralInstance previous = update
                ? ProceduralInstanceStore.load(playerId, requestedInstanceId)
                : null;
        String instanceId = update ? previous.id() : UUID.randomUUID().toString();
        PathSnapshot activePath = PathManager.saved(playerId);
        PathSnapshot storedPath = update
                ? activePath.points().size() >= 2 ? activePath : previous.path()
                : WorldEditService.resolvePath(player);
        PathSnapshot buildPath = SurfaceConformService.project(player, storedPath, recipe.extensions());
        KitbashSocketService.Template socketTemplate = KitbashSocketService.capture(player, recipe.extensions());
        var server = player.getServer();
        if (!quiet) {
            player.sendMessage(message(
                    (update ? "Actualizando instancia " : "Creando instancia ") + recipe.name()
                            + " fuera del hilo del servidor…"
            ), false);
        }

        AsyncEngine.submit(
                playerId,
                () -> preparePlan(recipe, buildPath, previous, socketTemplate),
                bundle -> server.execute(() -> finish(
                        playerId,
                        server.getPlayerManager().getPlayer(playerId),
                        instanceId,
                        recipe,
                        storedPath,
                        buildPath,
                        bundle,
                        update
                )),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null) {
                        current.sendMessage(message("No se pudo generar la receta: " + readable(throwable)), false);
                    }
                })
        );
    }

    public static void deleteInstance(ServerPlayerEntity player, String instanceId) {
        ProceduralInstance instance = ProceduralInstanceStore.load(player.getUuid(), instanceId);
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(instance.positions().length);
        long[] positions = instance.positions();
        int[] stateIds = instance.stateIds();
        for (int index = 0; index < positions.length; index++) {
            long packed = positions[index];
            if (stateIds[index] < 0) {
                builder.addOverride(
                        PackedBlockPlan.unpackX(packed),
                        PackedBlockPlan.unpackY(packed),
                        PackedBlockPlan.unpackZ(packed),
                        BlockTypes.AIR.getDefaultState()
                );
            } else {
                builder.addConditionalOverride(
                        PackedBlockPlan.unpackX(packed),
                        PackedBlockPlan.unpackY(packed),
                        PackedBlockPlan.unpackZ(packed),
                        stateIds[index],
                        BlockTypes.AIR.getDefaultState()
                );
            }
        }
        PackedBlockPlan removal = builder.build();
        if (removal.size() == 0) {
            ProceduralInstanceStore.delete(player.getUuid(), instanceId);
            RecipeActionService.sync(player);
            player.sendMessage(message("Instancia vacía eliminada"), false);
            return;
        }
        BatchEditService.submit(
                player,
                removal,
                "air",
                "Eliminar instancia " + instance.recipe().name(),
                (cancelled, changed) -> {
                    if (!cancelled) {
                        ProceduralInstanceStore.delete(player.getUuid(), instanceId);
                        RecipeActionService.sync(player);
                    }
                }
        );
    }

    private static PlanBundle preparePlan(
            ProceduralRecipe recipe,
            PathSnapshot buildPath,
            ProceduralInstance previous,
            KitbashSocketService.Template socketTemplate
    ) {
        GeometryDiagnostics.Result result = RecipeGeometryEngine.generate(buildPath, recipe);
        PackedBlockPlan.Builder instanceBuilder = PackedBlockPlan.builder(result.blocks().size());
        SmoothPieces pieces = result.pieces();
        for (var block : result.blocks()) {
            instanceBuilder.addPattern(block);
            if (!pieces.isEmpty()) {
                instanceBuilder.setPiece(instanceBuilder.size() - 1, pieces.at(block.x(), block.y(), block.z()));
            }
        }
        KitbashSocketService.append(instanceBuilder, socketTemplate, buildPath, recipe.extensions());
        if (instanceBuilder.size() > MAX_INSTANCE_BLOCKS) {
            throw new IllegalArgumentException("La geometría y sus sockets superan 1 500 000 bloques");
        }
        PackedBlockPlan instancePlan = instanceBuilder.build();

        if (previous == null) {
            return new PlanBundle(instancePlan, capturedPlacement(instancePlan), result.report(), pieces);
        }
        Long2IntOpenHashMap previousStates = ownership(previous);
        Long2IntOpenHashMap newPositions = new Long2IntOpenHashMap(instancePlan.size());
        newPositions.defaultReturnValue(PackedBlockPlan.NO_CAPTURE);
        for (int index = 0; index < instancePlan.size(); index++) {
            newPositions.putIfAbsent(instancePlan.packedPosition(index), index);
        }
        PackedBlockPlan.Builder updateBuilder = PackedBlockPlan.builder(previous.positions().length + instancePlan.size());
        long[] previousPositions = previous.positions();
        int[] previousStateIds = previous.stateIds();
        for (int index = 0; index < previousPositions.length; index++) {
            long packed = previousPositions[index];
            if (newPositions.containsKey(packed)) {
                continue;
            }
            if (previousStateIds[index] < 0) {
                updateBuilder.addOverride(
                        PackedBlockPlan.unpackX(packed),
                        PackedBlockPlan.unpackY(packed),
                        PackedBlockPlan.unpackZ(packed),
                        BlockTypes.AIR.getDefaultState()
                );
            } else {
                updateBuilder.addConditionalOverride(
                        PackedBlockPlan.unpackX(packed),
                        PackedBlockPlan.unpackY(packed),
                        PackedBlockPlan.unpackZ(packed),
                        previousStateIds[index],
                        BlockTypes.AIR.getDefaultState()
                );
            }
        }
        appendForUpdate(updateBuilder, instancePlan, previousStates);
        return new PlanBundle(instancePlan, updateBuilder.build(), result.report(), pieces);
    }

    private static PackedBlockPlan capturedPlacement(PackedBlockPlan source) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(source.size());
        for (int index = 0; index < source.size(); index++) {
            var position = source.position(index);
            var override = source.override(index);
            if (override == null) {
                builder.addCapturedPattern(position, index);
                builder.setPiece(builder.size() - 1, source.piece(index));
            } else {
                builder.addCapturedOverride(position, override, index);
            }
        }
        return builder.build();
    }

    private static Long2IntOpenHashMap ownership(ProceduralInstance previous) {
        long[] positions = previous.positions();
        int[] stateIds = previous.stateIds();
        Long2IntOpenHashMap ownership = new Long2IntOpenHashMap(positions.length);
        ownership.defaultReturnValue(PackedBlockPlan.NO_EXPECTED_STATE);
        for (int index = 0; index < positions.length; index++) {
            if (stateIds[index] >= 0) {
                ownership.putIfAbsent(positions[index], stateIds[index]);
            }
        }
        return ownership;
    }

    private static void appendForUpdate(
            PackedBlockPlan.Builder builder,
            PackedBlockPlan source,
            Long2IntOpenHashMap previousStates
    ) {
        for (int index = 0; index < source.size(); index++) {
            var position = source.position(index);
            var override = source.override(index);
            int expectedStateId = previousStates.get(source.packedPosition(index));
            if (expectedStateId == PackedBlockPlan.NO_EXPECTED_STATE) {
                if (override == null) {
                    builder.addCapturedPattern(position, index);
                } else {
                    builder.addCapturedOverride(position, override, index);
                }
            } else if (override == null) {
                builder.addConditionalCapturedPattern(position, expectedStateId, index);
            } else {
                builder.addConditionalCapturedOverride(position, expectedStateId, override, index);
            }
            if (override == null) {
                builder.setPiece(builder.size() - 1, source.piece(index));
            }
        }
    }

    private static void finish(
            UUID playerId,
            ServerPlayerEntity player,
            String instanceId,
            ProceduralRecipe recipe,
            PathSnapshot storedPath,
            PathSnapshot buildPath,
            PlanBundle bundle,
            boolean update
    ) {
        if (player == null || !player.getUuid().equals(playerId)) {
            return;
        }
        // Con el fantasma activado, la figura nueva se mueve antes de colocarse (una actualización no se mueve).
        if (!update && offerGhost(playerId, player, instanceId, recipe, storedPath, buildPath, bundle)) {
            return;
        }
        // Los Build muy grandes esperan a /komaedit confirm; la geometría ya está calculada.
        PendingBuilds.submitOrHold(
                player,
                (update ? "Actualizar " : "Construir ") + recipe.name(),
                BuildEstimate.of(bundle.placementPlan()),
                () -> enqueue(playerId, player, instanceId, recipe, storedPath, buildPath, bundle, update)
        );
    }

    /**
     * La figura como fantasma con gizmo. Al colocarla, la instancia guarda las posiciones y la trayectoria movidas y
     * giradas con ella; cada copia es una instancia nueva.
     */
    private static boolean offerGhost(
            UUID playerId,
            ServerPlayerEntity player,
            String instanceId,
            ProceduralRecipe recipe,
            PathSnapshot storedPath,
            PathSnapshot buildPath,
            PlanBundle bundle
    ) {
        PlanPreviews.PlanBlocks blocks;
        if (recipe.usesGradient()) {
            blocks = BatchEditService.blocks(bundle.placementPlan(), null, GradientService.compile(
                    player,
                    recipe.gradientAxis(),
                    recipe.palette().patterns(),
                    recipe.dither(),
                    recipe.reverse(),
                    bundle.instancePlan().bounds(),
                    recipe.extensions().materialMetric(),
                    bundle.instancePlan(),
                    recipe,
                    buildPath
            ));
        } else {
            blocks = BatchEditService.blocks(bundle.placementPlan(),
                    WorldEditService.parsePatternUnchecked(WorldEditService.context(player), recipe.pattern()), null);
        }
        String[] nextId = {instanceId};
        String shapeId = recipe.shape().id();
        return GhostService.offer(player, bundle.placementPlan(), blocks, "Construir " + recipe.name(),
                (current, placement) -> {
                    String id = nextId[0];
                    nextId[0] = UUID.randomUUID().toString();
                    GhostModel model = placement.model();
                    GhostTransform transform = placement.transform();
                    PackedBlockPlan.Builder moved = PackedBlockPlan.builder(bundle.instancePlan().size());
                    for (int index = 0; index < bundle.instancePlan().size(); index++) {
                        BlockVector3 position = bundle.instancePlan().position(index);
                        int[] carried = model.carry(position.x(), position.y(), position.z(), transform);
                        moved.addPattern(carried[0], carried[1], carried[2]);
                    }
                    List<BlockVector3> points = new ArrayList<>();
                    for (BlockVector3 point : storedPath.points()) {
                        int[] carried = model.carry(point.x(), point.y(), point.z(), transform);
                        points.add(BlockVector3.at(carried[0], carried[1], carried[2]));
                    }
                    PathSnapshot movedPath = new PathSnapshot(points, storedPath.closed(), storedPath.source());
                    PlanBundle movedBundle = new PlanBundle(moved.build(), placement.plan(), bundle.report(),
                            bundle.pieces());
                    return EditRequests.within(EditRequest.CONFIRMED, () -> BatchEditService.submitCapturing(current,
                            placement.plan(), "air", placement.description(),
                            listener(playerId, current, id, recipe, movedPath, movedBundle, false)));
                },
                current -> KomaPermissions.canBuild(current, shapeId));
    }

    private static BatchEditService.CapturedJobListener listener(
            UUID playerId,
            ServerPlayerEntity player,
            String instanceId,
            ProceduralRecipe recipe,
            PathSnapshot storedPath,
            PlanBundle bundle,
            boolean update
    ) {
        return (cancelled, changedBlocks, capturedStateIds, protectedBlocks) -> {
            if (!cancelled) {
                OwnershipCapture capture = ownershipCapture(bundle.instancePlan(), capturedStateIds);
                ProceduralInstanceStore.save(
                        playerId,
                        instanceId,
                        recipe,
                        storedPath,
                        capture.plan(),
                        capture.stateIds()
                );
                RecipeActionService.sync(player);
                player.sendMessage(message(
                        "Instancia " + (update ? "actualizada" : "creada") + ": " + recipe.name()
                                + " · " + bundle.report().summary()
                                + (bundle.pieces().isEmpty() ? "" : " · " + bundle.pieces().summary())
                                + (protectedBlocks == 0 ? "" : " · cambios manuales conservados")
                ), false);
            }
        };
    }

    private static void enqueue(
            UUID playerId,
            ServerPlayerEntity player,
            String instanceId,
            ProceduralRecipe recipe,
            PathSnapshot storedPath,
            PathSnapshot buildPath,
            PlanBundle bundle,
            boolean update
    ) {
        try {
            BatchEditService.CapturedJobListener listener = listener(playerId, player, instanceId, recipe, storedPath,
                    bundle, update);
            int queued;
            if (recipe.usesGradient()) {
                queued = BatchEditService.submitStyledCapturing(
                        player,
                        bundle.placementPlan(),
                        GradientService.compile(
                                player,
                                recipe.gradientAxis(),
                                recipe.palette().patterns(),
                                recipe.dither(),
                                recipe.reverse(),
                                bundle.instancePlan().bounds(),
                                recipe.extensions().materialMetric(),
                                bundle.instancePlan(),
                                recipe,
                                buildPath
                        ),
                        (update ? "Actualizar " : "Construir ") + recipe.name(),
                        listener
                );
            } else {
                queued = BatchEditService.submitCapturing(
                        player,
                        bundle.placementPlan(),
                        recipe.pattern(),
                        (update ? "Actualizar " : "Construir ") + recipe.name(),
                        listener
                );
            }
            player.sendMessage(message(
                    recipe.shape().displayName() + " en cola: " + ProgressLine.group(queued) + " bloques · instancia "
                            + instanceId.substring(0, 8)
            ), false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(message(exception.getMessage()), false);
        }
    }

    private static OwnershipCapture ownershipCapture(PackedBlockPlan source, int[] capturedStateIds) {
        int retained = 0;
        for (int stateId : capturedStateIds) {
            if (stateId != PackedBlockPlan.NO_EXPECTED_STATE) {
                retained++;
            }
        }
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(retained);
        int[] states = new int[retained];
        int target = 0;
        for (int index = 0; index < capturedStateIds.length; index++) {
            int stateId = capturedStateIds[index];
            if (stateId == PackedBlockPlan.NO_EXPECTED_STATE) {
                continue;
            }
            builder.addPattern(source.position(index));
            states[target++] = stateId;
        }
        return new OwnershipCapture(builder.build(), states);
    }

    private static void validateMaterials(ServerPlayerEntity player, ProceduralRecipe recipe)
            throws InputParseException {
        if (!recipe.usesGradient()) {
            WorldEditService.validatePattern(player, recipe.pattern());
            return;
        }
        for (String entry : Arrays.stream(recipe.palette().patterns().split(";"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList()) {
            WorldEditService.validatePattern(player, entry);
        }
    }

    private static Text message(String value) {
        return Feedback.prefix()
                .append(Text.literal(value).formatted(Formatting.GRAY));
    }

    private static String readable(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getMessage() == null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record PlanBundle(
            PackedBlockPlan instancePlan,
            PackedBlockPlan placementPlan,
            GeometryDiagnostics.Report report,
            SmoothPieces pieces
    ) {
    }

    private record OwnershipCapture(PackedBlockPlan plan, int[] stateIds) {
    }
}
