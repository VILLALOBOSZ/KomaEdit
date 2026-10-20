package com.komainu.moxia.utility;

import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperation;
import com.komainu.moxia.region.RegionSnapshot;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Las operaciones de región de gravedad, nieve, drenar e inundar: conectan la máscara a la foto, llaman al motor
 * puro y convierten sus cambios en estados de WorldEdit (escritos de forma condicional, con //undo).
 */
public final class UtilityOperations {
    private UtilityOperations() {
    }

    /** {@code /komaedit gravity}. */
    public record Gravity(RegionMasks.Bound mask, boolean unsupportedOnly) implements RegionOperation {
        @Override
        public void apply(RegionSnapshot snapshot, RegionEdits edits) {
            bind(mask, snapshot);
            States states = new States();
            GravityEngine.apply(snapshot, new MinecraftKinds(), mask == null ? null : mask::test, unsupportedOnly,
                    (x, y, z, content, waterlogged) -> {
                        BlockState state = switch (content) {
                            case GravityEngine.AIR -> States.AIR;
                            case GravityEngine.WATER -> States.WATER;
                            case GravityEngine.LAVA -> States.LAVA;
                            default -> snapshot.stateInfo(content).state();
                        };
                        if (state == null) {
                            return;
                        }
                        if (waterlogged != GravityEngine.KEEP_WATERLOG) {
                            state = states.withBoolean(state, "waterlogged", waterlogged == 1);
                        }
                        edits.set(x, y, z, state);
                    }, edits::checkCancelled);
        }
    }

    /** {@code /komaedit snow}. */
    public record Snow(SnowEngine.Settings settings, RegionMasks.Bound mask) implements RegionOperation {
        @Override
        public void apply(RegionSnapshot snapshot, RegionEdits edits) {
            bind(mask, snapshot);
            States states = new States();
            SnowEngine.apply(snapshot, new MinecraftKinds(), settings, mask == null ? null : mask::test,
                    new SnowEngine.Sink() {
                        @Override
                        public void snow(int x, int y, int z, int layers) {
                            edits.set(x, y, z, states.snow(layers));
                        }

                        @Override
                        public void snowy(int x, int y, int z) {
                            BlockState below = snapshot.state(x, y, z);
                            if (below != null) {
                                edits.set(x, y, z, states.withBoolean(below, "snowy", true));
                            }
                        }
                    }, edits::checkCancelled);
        }
    }

    /** {@code /komaedit drain}. */
    public record Drain(FluidEngine.Drain mode, boolean keepPlants, RegionMasks.Bound mask)
            implements RegionOperation {
        @Override
        public void apply(RegionSnapshot snapshot, RegionEdits edits) {
            bind(mask, snapshot);
            FluidEngine.drain(snapshot, new MinecraftKinds(), mode, keepPlants, mask == null ? null : mask::test,
                    sink(snapshot, edits), edits::checkCancelled);
        }
    }

    /** {@code /komaedit waterlog}. */
    public record Flood(int level, boolean blocksOnly, RegionMasks.Bound mask) implements RegionOperation {
        @Override
        public void apply(RegionSnapshot snapshot, RegionEdits edits) {
            bind(mask, snapshot);
            FluidEngine.flood(snapshot, new MinecraftKinds(), level, blocksOnly, mask == null ? null : mask::test,
                    sink(snapshot, edits), edits::checkCancelled);
        }
    }

    private static void bind(RegionMasks.Bound mask, RegionSnapshot snapshot) {
        if (mask != null) {
            mask.bind(snapshot);
        }
    }

    private static FluidEngine.Sink sink(RegionSnapshot snapshot, RegionEdits edits) {
        States states = new States();
        return new FluidEngine.Sink() {
            @Override
            public void air(int x, int y, int z) {
                edits.set(x, y, z, States.AIR);
            }

            @Override
            public void water(int x, int y, int z) {
                edits.set(x, y, z, States.WATER);
            }

            @Override
            public void waterlogged(int x, int y, int z, boolean value) {
                BlockState state = snapshot.state(x, y, z);
                if (state != null) {
                    edits.set(x, y, z, states.withBoolean(state, "waterlogged", value));
                }
            }
        };
    }

    /** Estados de WorldEdit con una propiedad cambiada, con caché. */
    static final class States {
        static final BlockState AIR = BlockTypes.AIR.getDefaultState();
        static final BlockState WATER = BlockTypes.WATER.getDefaultState();
        static final BlockState LAVA = BlockTypes.LAVA.getDefaultState();
        private final Map<String, BlockState> cache = new HashMap<>();

        @SuppressWarnings("unchecked")
        BlockState withBoolean(BlockState state, String name, boolean value) {
            Property<Object> property = (Property<Object>) state.getBlockType().getPropertyMap().get(name);
            if (property == null) {
                return state;
            }
            return state.with(property, value);
        }

        @SuppressWarnings("unchecked")
        BlockState snow(int layers) {
            return cache.computeIfAbsent("snow" + layers, key -> {
                BlockState snow = BlockTypes.SNOW.getDefaultState();
                Property<Object> property = (Property<Object>) snow.getBlockType().getPropertyMap().get("layers");
                return property == null ? snow : snow.with(property, layers);
            });
        }
    }
}
