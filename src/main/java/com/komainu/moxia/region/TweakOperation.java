package com.komainu.moxia.region;

import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashMap;
import java.util.Map;

/** Aplica un retoque de supervivencia a cada bloque de la selección que lo admita. */
public final class TweakOperation implements RegionOperation {
    private final TweakRules.Tweak tweak;
    private final boolean enable;

    public TweakOperation(TweakRules.Tweak tweak, boolean enable) {
        this.tweak = tweak;
        this.enable = enable;
    }

    @Override
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        Map<BlockState, BlockState> cache = new HashMap<>();
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    edits.checkCancelled();
                    if (!snapshot.selected(x, y, z) || snapshot.isAir(x, y, z)) {
                        continue;
                    }
                    BlockState current = snapshot.state(x, y, z);
                    // Sin cambio se guarda el propio estado: un null no quedaría en la caché.
                    BlockState next = cache.computeIfAbsent(current, state -> {
                        BlockState transformed = transform(state);
                        return transformed == null ? state : transformed;
                    });
                    if (!next.equals(current)) {
                        edits.set(x, y, z, next);
                    }
                }
            }
        }
    }

    private BlockState transform(BlockState state) {
        String blockId = state.getBlockType().id();
        return switch (tweak) {
            case DEINFEST -> replaceType(state, TweakRules.deinfested(blockId));
            case WAX -> replaceType(state, TweakRules.waxed(blockId, enable));
            case PERSIST -> TweakRules.isLeaves(blockId) ? withBoolean(state, "persistent", enable) : null;
            case WATERLOG -> withBoolean(state, "waterlogged", enable);
        };
    }

    private static BlockState replaceType(BlockState state, String targetId) {
        if (targetId == null) {
            return null;
        }
        BlockType target = BlockTypes.get(targetId);
        if (target == null) {
            return null;
        }
        BlockState result = target.getDefaultState();
        for (Map.Entry<Property<?>, Object> entry : state.getStates().entrySet()) {
            result = copy(result, entry.getKey().getName(), entry.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static BlockState copy(BlockState state, String name, Object value) {
        Property<Object> property = (Property<Object>) state.getBlockType().getPropertyMap().get(name);
        if (property == null || !property.getValues().contains(value)) {
            return state;
        }
        return state.with(property, value);
    }

    private static BlockState withBoolean(BlockState state, String name, boolean value) {
        if (!state.getBlockType().getPropertyMap().containsKey(name)) {
            return null;
        }
        return copy(state, name, value);
    }
}
