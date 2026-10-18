package com.komainu.moxia.symmetry;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aplica {@link StateTransform} a un estado de Minecraft pasando sus propiedades a texto y de vuelta. */
public final class SymmetryStates {
    private SymmetryStates() {
    }

    public static BlockState transform(BlockState state, SymmetryTransform transform) {
        if (transform.identity() || state.getProperties().isEmpty()) {
            return state;
        }
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, List<String>> allowed = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            values.put(property.getName(), valueName(state, property));
            allowed.put(property.getName(), valueNames(property));
        }
        Map<String, String> transformed = StateTransform.apply(values, allowed, transform);
        BlockState result = state;
        for (Property<?> property : state.getProperties()) {
            String value = transformed.get(property.getName());
            if (value != null && !value.equals(values.get(property.getName()))) {
                result = with(result, property, value);
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }

    private static <T extends Comparable<T>> List<String> valueNames(Property<T> property) {
        List<String> names = new ArrayList<>(property.getValues().size());
        for (T value : property.getValues()) {
            names.add(property.name(value));
        }
        return names;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.parse(value).map(parsed -> state.with(property, parsed)).orElse(state);
    }
}
