package com.komainu.moxia.brush.ez;

import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * {@link BrushWorld} sobre la foto que captura {@code SculptManager}. La clase de cada estado (aire, cubo, losa…) se
 * calcula una vez por pasada y estado, no en cada bloque. Una pasada la usa un solo hilo.
 */
public final class SnapshotWorld implements BrushWorld {
    /** Propiedades que cuentan las piezas del follaje en capas, según el bloque. */
    private static final String[] AMOUNT_PROPERTIES = {"flower_amount", "segment_amount"};

    private final WorldSnapshot snapshot;
    private final Map<BlockState, Cell> cells = new IdentityHashMap<>();
    private final Map<BlockState, PaletteShift.Kind> kinds = new IdentityHashMap<>();

    public SnapshotWorld(WorldSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public BlockState state(int x, int y, int z) {
        return snapshot.get(x, y, z);
    }

    @Override
    public String blockId(int x, int y, int z) {
        return snapshot.get(x, y, z).getBlockType().id();
    }

    @Override
    public Cell cell(int x, int y, int z) {
        BlockState state = snapshot.get(x, y, z);
        Cell cell = cells.get(state);
        if (cell == null) {
            cell = classify(state);
            cells.put(state, cell);
        }
        return cell;
    }

    /** {@link ResolvedPalette#kindOf}, recordada por estado. */
    public PaletteShift.Kind kind(BlockState state) {
        PaletteShift.Kind kind = kinds.get(state);
        if (kind == null) {
            kind = ResolvedPalette.kindOf(state);
            kinds.put(state, kind);
        }
        return kind;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return snapshot.contains(x, y, z);
    }

    /** {@code amount} se traduce a la propiedad de piezas que tenga el bloque. */
    @Override
    public int intProperty(int x, int y, int z, String property) {
        BlockState state = snapshot.get(x, y, z);
        if (property.equals("amount")) {
            for (String name : AMOUNT_PROPERTIES) {
                int value = integer(state, name);
                if (value >= 0) {
                    return value;
                }
            }
            return -1;
        }
        return integer(state, property);
    }

    private static int integer(BlockState state, String name) {
        for (Map.Entry<Property<?>, Object> entry : state.getStates().entrySet()) {
            if (entry.getKey().getName().equals(name) && entry.getValue() instanceof Integer value) {
                return value;
            }
        }
        return -1;
    }

    public static Cell classify(BlockState state) {
        var material = state.getBlockType().getMaterial();
        if (material.isAir()) {
            return Cell.AIR;
        }
        if (material.isLiquid()) {
            return Cell.LIQUID;
        }
        String id = state.getBlockType().id();
        if (id.endsWith("_slab") || id.endsWith("_stairs")) {
            return Cell.SHAPED;
        }
        return material.isFullCube() ? Cell.FULL : Cell.OTHER;
    }
}
