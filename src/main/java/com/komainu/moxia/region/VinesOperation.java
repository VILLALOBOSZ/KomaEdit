package com.komainu.moxia.region;

import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.Map;

/**
 * {@code vines}: cuelga tiras del patrón bajo un porcentaje de los bloques de la máscara que tienen aire debajo,
 * como {@code //ezvines} de ezEdits. Cada tira mide entre {@code minimum} y {@code maximum} bloques y se corta al
 * tocar algo. Las enredaderas de vanilla salen bien puestas: {@code vine} se pega a las paredes vecinas (y no se
 * coloca donde no tendría apoyo), y {@code weeping_vines} y {@code cave_vines} llevan tallo y punta.
 */
public final class VinesOperation implements RegionOperation {
    private static final String[] SIDES = {"north", "south", "east", "west"};
    private static final int[][] OFFSETS = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

    private final RegionMasks.Bound mask;
    private final Pattern pattern;
    private final double chance;
    private final int minimum;
    private final int maximum;
    private final long seed;

    public VinesOperation(RegionMasks.Bound mask, Pattern pattern, double chance, int minimum, int maximum, long seed) {
        if (minimum < 1 || maximum < minimum || maximum > 32) {
            throw new IllegalArgumentException("Los largos van de 1 a 32 y el mínimo no supera al máximo");
        }
        this.mask = mask;
        this.pattern = pattern;
        this.chance = chance;
        this.minimum = minimum;
        this.maximum = maximum;
        this.seed = seed;
    }

    public int padding() {
        return maximum;
    }

    @Override
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        mask.bind(snapshot);
        for (int y = snapshot.maximumY(); y >= snapshot.minimumY(); y--) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    edits.checkCancelled();
                    if (!snapshot.selected(x, y, z) || snapshot.isAir(x, y, z) || !snapshot.contains(x, y - 1, z)
                            || !snapshot.isAir(x, y - 1, z)) {
                        continue;
                    }
                    if (RegionRules.unit(x, y, z, seed) >= chance || !mask.test(x, y, z)) {
                        continue;
                    }
                    int length = minimum + (int) Math.floor(RegionRules.unit(x, y, z, seed ^ 0x2545F4914F6CDD1DL)
                            * (maximum - minimum + 1));
                    hang(snapshot, edits, x, y, z, Math.min(length, maximum));
                }
            }
        }
    }

    private void hang(RegionSnapshot snapshot, RegionEdits edits, int x, int anchorY, int z, int length) {
        int bottom = anchorY - 1;
        while (bottom > anchorY - length && snapshot.contains(x, bottom - 1, z) && snapshot.isAir(x, bottom - 1, z)) {
            bottom--;
        }
        BlockState above = null;
        for (int y = anchorY - 1; y >= bottom; y--) {
            BlockState state = pattern.applyBlock(BlockVector3.at(x, y, z)).toImmutableState();
            String id = state.getBlockType().id();
            if (id.equals("minecraft:vine")) {
                state = vine(snapshot, state, above, x, y, z, y == anchorY - 1);
                if (state == null) {
                    return;
                }
            } else {
                String plant = RegionRules.hangingPlant(id);
                if (plant != null && y > bottom) {
                    BlockType type = BlockTypes.get(plant);
                    if (type != null) {
                        state = copy(state, type.getDefaultState());
                    }
                }
            }
            edits.setInCapture(x, y, z, state);
            above = state;
        }
    }

    /** Caras de una enredadera: las paredes que toca, el techo si cuelga del bloque y lo que herede de la de encima. */
    private static BlockState vine(RegionSnapshot snapshot, BlockState base, BlockState above, int x, int y, int z,
                                   boolean top) {
        BlockState state = base;
        boolean any = false;
        for (int side = 0; side < SIDES.length; side++) {
            int[] offset = OFFSETS[side];
            boolean face = snapshot.isSolid(x + offset[0], y, z + offset[2]);
            if (!face && above != null && above.getBlockType().id().equals("minecraft:vine")) {
                face = Boolean.TRUE.equals(get(above, SIDES[side]));
            }
            state = with(state, SIDES[side], face);
            any |= face;
        }
        boolean up = top && snapshot.isSolid(x, y + 1, z);
        state = with(state, "up", up);
        return any || up ? state : null;
    }

    @SuppressWarnings("unchecked")
    private static BlockState with(BlockState state, String name, boolean value) {
        Property<Object> property = (Property<Object>) state.getBlockType().getPropertyMap().get(name);
        return property == null ? state : state.with(property, value);
    }

    private static Object get(BlockState state, String name) {
        Property<?> property = state.getBlockType().getPropertyMap().get(name);
        return property == null ? null : state.getState(property);
    }

    @SuppressWarnings("unchecked")
    private static BlockState copy(BlockState from, BlockState to) {
        BlockState result = to;
        for (Map.Entry<Property<?>, Object> entry : from.getStates().entrySet()) {
            Property<Object> property = (Property<Object>) result.getBlockType().getPropertyMap()
                    .get(entry.getKey().getName());
            if (property != null && property.getValues().contains(entry.getValue())) {
                result = result.with(property, entry.getValue());
            }
        }
        return result;
    }
}
