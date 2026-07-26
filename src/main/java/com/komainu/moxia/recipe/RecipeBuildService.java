package com.komainu.moxia.recipe;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.gradient.GradientService;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.UUID;

public final class RecipeBuildService {
    private static final int MAX_INSTANCE_BLOCKS = 1_500_000;

    private RecipeBuildService() {
    }

    public static void submit(ServerPlayerEntity player, ProceduralRecipe recipe)
            throws IncompleteRegionException, InputParseException {
        submit(player, recipe, "", false);
    }

    public static void update(ServerPlayerEntity player, ProceduralRecipe recipe, String instanceId)
            throws InputParseException {
        try {
            submit(player, recipe, instanceId, true);
        } catch (IncompleteRegionException exception) {
            throw new IllegalStateException("La trayectoria guardada de la instancia está incompleta", exception);
        }
    }

    private static void submit(
            ServerPlayerEntity player,
            ProceduralRecipe recipe,
            String requestedInstanceId,
            boolean update
    ) throws IncompleteRegionException, InputParseException {
        validateMaterials(player, recipe);
        UUID playerId = player.getUuid();
        ProceduralInstance previous = update
                ? ProceduralInstanceStore.load(playerId, requestedInstanceId)
                : null;
        String instanceId = update ? previous.id() : UUID.randomUUID().toString();
        PathSnapshot storedPath = update
                ? previous.path()
                : WorldEditService.resolvePath(player);
        PathSnapshot buildPath = SurfaceConformService.project(player, storedPath, recipe.extensions());
        KitbashSocketService.Template socketTemplate = KitbashSocketService.capture(player, recipe.extensions());
        var server = player.getServer();
        player.sendMessage(message(
                (update ? "Actualizando instancia " : "Creando instancia ") + recipe.name() + " fuera del hilo del servidor…"
        ), false);

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
        result.blocks().forEach(instanceBuilder::addPattern);
        KitbashSocketService.append(instanceBuilder, socketTemplate, buildPath, recipe.extensions());
        if (instanceBuilder.size() > MAX_INSTANCE_BLOCKS) {
            throw new IllegalArgumentException("La geometría y sus sockets superan 1 500 000 bloques");
        }
        PackedBlockPlan instancePlan = instanceBuilder.build();

        if (previous == null) {
            return new PlanBundle(instancePlan, capturedPlacement(instancePlan), result.report());
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
        return new PlanBundle(instancePlan, updateBuilder.build(), result.report());
    }

    private static PackedBlockPlan capturedPlacement(PackedBlockPlan source) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(source.size());
        for (int index = 0; index < source.size(); index++) {
            var position = source.position(index);
            var override = source.override(index);
            if (override == null) {
                builder.addCapturedPattern(position, index);
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
        try {
            BatchEditService.CapturedJobListener listener = (
                    cancelled,
                    changedBlocks,
                    capturedStateIds,
                    protectedBlocks
            ) -> {
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
                                    + (protectedBlocks == 0 ? "" : " · cambios manuales conservados")
                    ), false);
                }
            };
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
                    recipe.shape().displayName() + " en cola: " + queued + " bloques · instancia "
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
        return Text.literal("KomaEdit · ").formatted(Formatting.DARK_RED)
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
            GeometryDiagnostics.Report report
    ) {
    }

    private record OwnershipCapture(PackedBlockPlan plan, int[] stateIds) {
    }
}
