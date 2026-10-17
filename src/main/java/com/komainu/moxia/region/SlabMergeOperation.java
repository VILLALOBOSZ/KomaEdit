package com.komainu.moxia.region;

import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code slabmerge}: cambia las losas dobles por el bloque entero de su material ({@code stone_brick_slab} →
 * {@code stone_bricks}), como {@code //ezslabmerge} de ezEdits. Con {@code -b} o {@code -t} también las losas de
 * abajo o de arriba.
 */
public final class SlabMergeOperation implements RegionOperation {
    private final RegionMasks.Bound mask;
    private final boolean bottom;
    private final boolean top;
    private final Map<String, BlockState> full = new HashMap<>();

    public SlabMergeOperation(RegionMasks.Bound mask, boolean bottom, boolean top) {
        this.mask = mask;
        this.bottom = bottom;
        this.top = top;
    }

    @Override
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        mask.bind(snapshot);
        int unknown = 0;
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    edits.checkCancelled();
                    if (!snapshot.selected(x, y, z) || !snapshot.blockId(x, y, z).endsWith("_slab")) {
                        continue;
                    }
                    BlockState state = snapshot.state(x, y, z);
                    Property<?> type = state.getBlockType().getPropertyMap().get("type");
                    String half = type == null ? "double" : String.valueOf(state.getState(type));
                    boolean merge = "double".equals(half) || ("bottom".equals(half) && bottom)
                            || ("top".equals(half) && top);
                    if (!merge || !mask.test(x, y, z)) {
                        continue;
                    }
                    BlockState block = full.computeIfAbsent(state.getBlockType().id(), id -> {
                        String fullId = RegionRules.fullBlock(id, candidate -> BlockTypes.get(candidate) != null);
                        BlockType fullType = fullId == null ? null : BlockTypes.get(fullId);
                        return fullType == null ? state : fullType.getDefaultState();
                    });
                    if (block.getBlockType().equals(state.getBlockType())) {
                        unknown++;
                    } else {
                        edits.set(x, y, z, block);
                    }
                }
            }
        }
        if (unknown > 0 && edits.changes() == 0) {
            throw new IllegalArgumentException("No se encontró el bloque entero de esas losas");
        }
    }
}
