package com.komainu.moxia.brush.place;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/** Una colocación del pincel: la plantilla girada con la base de {@link Alignment} y llevada al punto. */
public record PlacementOperation(String label, PlacementTemplate template, int[] basis, BlockVector3 target,
                                 boolean placeAir) implements BrushOperation {
    public static final int MAX_CHANGES = 1_000_000;

    @Override
    public String displayName() {
        return "Colocar " + label;
    }

    @Override
    public int previewColor() {
        return 0x4FC3F7;
    }

    @Override
    public Capture capture() {
        return null;
    }

    @Override
    public PackedBlockPlan plan(WorldSnapshot snapshot) {
        AffineTransform transform = new AffineTransform(
                basis[0], basis[1], basis[2], 0,
                basis[3], basis[4], basis[5], 0,
                basis[6], basis[7], basis[8], 0
        );
        Map<BlockState, BlockState> rotated = new IdentityHashMap<>();
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(Math.min(template.size(), 65_536));
        int[] count = {0};
        template.forEach((x, y, z, state) -> {
            if (!placeAir && state.getBlockType().getMaterial().isAir()) {
                return;
            }
            if ((count[0] & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Cálculo cancelado");
            }
            BlockState placed = rotated.computeIfAbsent(state, value -> BlockTransformExtent.transform(value, transform));
            builder.addOverride(
                    target.x() + Alignment.applyX(basis, x, y, z),
                    target.y() + Alignment.applyY(basis, x, y, z),
                    target.z() + Alignment.applyZ(basis, x, y, z),
                    placed
            );
            if (++count[0] > MAX_CHANGES) {
                throw new IllegalArgumentException("La colocación supera el límite de " + ProgressLine.group(MAX_CHANGES) + " bloques");
            }
        });
        return builder.build();
    }
}
