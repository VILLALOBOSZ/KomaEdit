package com.komainu.moxia.blueprint;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.SnapshotWorld;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.symmetry.SymmetryStates;
import com.komainu.moxia.symmetry.SymmetryTransform;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Una pasada del pincel «estampar». Captura el terreno alrededor del clic, decide en el worker dónde cae cada plano
 * ({@link StampPlanner}) y escribe sus bloques girados y volteados. Los planos ya están leídos (en el hilo del
 * servidor); aquí solo se recorren.
 *
 * @param anchor    bloque de aire delante de la cara que se mira
 * @param onPlanned recibe los puntos {@code {x, z}} estampados, para que la siguiente pasada los respete
 */
public record StampOperation(StampSettings settings, List<BlueprintTemplate> templates, double[] weights,
                             BlockVector3 anchor, int lookTurns, BrushMask mask, List<int[]> occupied, long seed,
                             Consumer<List<int[]>> onPlanned) implements BrushOperation {
    public static final int MAX_CHANGES = 1_000_000;

    @Override
    public String displayName() {
        return "Estampar " + settings.source();
    }

    @Override
    public int previewColor() {
        return 0xA5D6A7;
    }

    @Override
    public Capture capture() {
        double reach = 0;
        for (BlueprintTemplate template : templates) {
            reach = Math.max(reach, template.shape().radius());
        }
        int horizontal = settings.radius() + (int) Math.ceil(reach) + 1;
        return new Capture(anchor, horizontal, StampPlanner.SEARCH + 1);
    }

    @Override
    public PackedBlockPlan plan(WorldSnapshot snapshot) {
        if (mask != null) {
            mask.bind(snapshot);
        }
        List<StampShape> shapes = templates.stream().map(BlueprintTemplate::shape).toList();
        StampPlanner.Result result = StampPlanner.plan(settings, shapes, weights, anchor.x(), anchor.y(), anchor.z(),
                lookTurns, new SnapshotWorld(snapshot), mask, occupied, seed);
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(4_096);
        Map<String, Map<BlockState, BlockState>> transformed = new HashMap<>();
        int[] count = {0};
        for (StampPlanner.Stamp stamp : result.stamps()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Cálculo cancelado");
            }
            int[] m = stamp.matrix();
            SymmetryTransform transform = stamp.symmetry();
            Map<BlockState, BlockState> states = transformed.computeIfAbsent(stamp.blueprint() + ":"
                    + java.util.Arrays.toString(m), key -> new IdentityHashMap<>());
            templates.get(stamp.blueprint()).forEach(settings.placeAir(), (x, y, z, state) -> {
                // Con la transformación de la simetría: la de WorldEdit no gira los raíles ni las bisagras.
                BlockState placed = states.computeIfAbsent(state, value -> transform.identity() ? value
                        : FabricAdapter.adapt(SymmetryStates.transform(FabricAdapter.adapt(value), transform)));
                builder.addOverride(
                        stamp.x() + m[0] * x + m[1] * y + m[2] * z,
                        stamp.y() + m[3] * x + m[4] * y + m[5] * z,
                        stamp.z() + m[6] * x + m[7] * y + m[8] * z,
                        placed);
                if (++count[0] > MAX_CHANGES) {
                    throw new IllegalArgumentException("El estampado supera el límite de " + ProgressLine.group(MAX_CHANGES) + " bloques");
                }
            });
        }
        if (onPlanned != null) {
            onPlanned.accept(result.stamps().stream().map(stamp -> new int[]{stamp.x(), stamp.z()}).toList());
        }
        if (result.stamps().isEmpty() && result.withoutGround() > 0) {
            throw new IllegalArgumentException("No hay suelo donde apoyar los planos (o no cumple la máscara -m)");
        }
        return builder.build();
    }
}
