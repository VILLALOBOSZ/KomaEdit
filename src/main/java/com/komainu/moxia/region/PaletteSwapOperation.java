package com.komainu.moxia.region;

import com.komainu.moxia.gradient.PaletteSwap;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** {@code /komaedit palette swap}: cambia los bloques de una paleta por los de otra dentro de la selección. */
public final class PaletteSwapOperation implements RegionOperation {
    private final PaletteSwap swap;

    public PaletteSwapOperation(PaletteSwap swap) {
        this.swap = swap;
    }

    @Override
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        Map<BlockState, Optional<BlockState>> cache = new HashMap<>();
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    edits.checkCancelled();
                    if (!snapshot.selected(x, y, z)) {
                        continue;
                    }
                    BlockState current = snapshot.state(x, y, z);
                    Optional<BlockState> next = cache.computeIfAbsent(current, this::transform);
                    if (next.isPresent() && !next.get().equals(current)) {
                        edits.set(x, y, z, next.get());
                    }
                }
            }
        }
    }

    private Optional<BlockState> transform(BlockState state) {
        Map<String, String> properties = properties(state);
        PaletteSwap.Rule rule = swap.ruleFor(state.getBlockType().id(), properties);
        if (rule == null) {
            return Optional.empty();
        }
        BlockType target = BlockTypes.get(rule.target().id());
        if (target == null) {
            throw new IllegalArgumentException("No existe el bloque " + rule.target().id());
        }
        BlockState result = target.getDefaultState();
        for (Map.Entry<String, String> entry : swap.targetProperties(rule, properties).entrySet()) {
            result = with(result, entry.getKey(), entry.getValue());
        }
        return Optional.of(result);
    }

    /** Las propiedades de un estado en texto, como se escriben en una paleta. */
    public static Map<String, String> properties(BlockState state) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<Property<?>, Object> entry : state.getStates().entrySet()) {
            result.put(entry.getKey().getName(), String.valueOf(entry.getValue()).toLowerCase(Locale.ROOT));
        }
        return result;
    }

    /** Pone una propiedad escrita en texto; si el bloque no la tiene o el valor no vale, deja el estado igual. */
    @SuppressWarnings("unchecked")
    public static BlockState with(BlockState state, String name, String value) {
        Property<Object> property = (Property<Object>) state.getBlockType().getPropertyMap().get(name);
        if (property == null) {
            return state;
        }
        for (Object option : property.getValues()) {
            if (String.valueOf(option).toLowerCase(Locale.ROOT).equals(value)) {
                return state.with(property, option);
            }
        }
        return state;
    }
}
