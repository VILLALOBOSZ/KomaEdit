package com.komainu.moxia.fluid;

import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionSnapshot;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashMap;
import java.util.Map;

/** Convierte {@link FluidWrites} en estados de WorldEdit y los deja en el plan (condicional, con //undo). */
final class FluidStates {
    /** De dónde salen los estados: WorldEdit en el servidor; en las pruebas, estados de mentira. */
    interface Source {
        BlockState air();

        /** El líquido con su {@code level} (la nieve en polvo no tiene). */
        BlockState fluid(FluidKind fluid, int level);

        /** {@code state} con una propiedad booleana cambiada. */
        BlockState with(BlockState state, String property, boolean value);
    }

    static final Source WORLDEDIT = new Source() {
        @Override
        public BlockState air() {
            return BlockTypes.AIR.getDefaultState();
        }

        @Override
        public BlockState fluid(FluidKind fluid, int level) {
            return switch (fluid) {
                case WATER -> FluidStates.with(BlockTypes.WATER.getDefaultState(), "level", level);
                case LAVA -> FluidStates.with(BlockTypes.LAVA.getDefaultState(), "level", level);
                case POWDER_SNOW -> BlockTypes.POWDER_SNOW.getDefaultState();
            };
        }

        @Override
        public BlockState with(BlockState state, String property, boolean value) {
            return FluidStates.with(state, property, value);
        }
    };

    private final Source source;
    private final Map<String, BlockState> cache = new HashMap<>();

    FluidStates() {
        this(WORLDEDIT);
    }

    FluidStates(Source source) {
        this.source = source;
    }

    /** @return cuántos bloques cambian */
    int write(FluidWrites writes, RegionSnapshot snapshot, RegionEdits edits) {
        int[] changed = {0};
        writes.forEach((x, y, z, code) -> {
            BlockState state = state(writes.fluid(), code, snapshot, x, y, z);
            if (state != null && edits.setInCapture(x, y, z, state)) {
                changed[0]++;
            }
            edits.checkCancelled();
        });
        return changed[0];
    }

    private BlockState state(FluidKind fluid, int code, RegionSnapshot snapshot, int x, int y, int z) {
        if (code == FluidWrites.AIR) {
            return source.air();
        }
        if (code == FluidWrites.WATERLOG) {
            BlockState current = snapshot.state(x, y, z);
            return current == null ? null : source.with(current, "waterlogged", true);
        }
        if (FluidWrites.isBlock(code)) {
            return snapshot.stateInfo(FluidWrites.rawId(code)).state();
        }
        return fluid(fluid, code);
    }

    private BlockState fluid(FluidKind fluid, int level) {
        return cache.computeIfAbsent(fluid.id() + level, key -> source.fluid(fluid, level));
    }

    @SuppressWarnings("unchecked")
    private static BlockState with(BlockState state, String name, Object value) {
        Property<Object> property = (Property<Object>) state.getBlockType().getPropertyMap().get(name);
        return property == null ? state : state.with(property, value);
    }
}
