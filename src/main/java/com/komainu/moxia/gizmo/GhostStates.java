package com.komainu.moxia.gizmo;

import com.komainu.moxia.symmetry.SymmetryStates;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link OrientedStates} sobre los estados de Minecraft, en el servidor y en el cliente (el id crudo de un estado es
 * el mismo en los dos). De pie usa directamente la transformación de la simetría ({@link SymmetryStates}); tumbado,
 * la de {@link OrientedStates}. Guarda cada resultado por estado y orientación.
 */
public final class GhostStates implements GhostPlacement.StateMapper {
    public static final GhostStates INSTANCE = new GhostStates();
    private static final Map<Long, Integer> MAPPED = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> ADMITTED = new ConcurrentHashMap<>();

    private GhostStates() {
    }

    @Override
    public int map(int state, Orientation orientation) {
        if (orientation == Orientation.IDENTITY) {
            return state;
        }
        return MAPPED.computeIfAbsent(key(state, orientation), ignored ->
                Block.getRawIdFromState(transform(Block.getStateFromRawId(state), orientation)));
    }

    /** Si todos esos estados quedan bien con la orientación (de pie, siempre). */
    public static boolean admitsAll(int[] palette, Orientation orientation) {
        if (orientation.upright()) {
            return true;
        }
        for (int state : palette) {
            boolean admitted = ADMITTED.computeIfAbsent(key(state, orientation), ignored ->
                    admits(Block.getStateFromRawId(state), orientation));
            if (!admitted) {
                return false;
            }
        }
        return true;
    }

    public static BlockState transform(BlockState state, Orientation orientation) {
        if (orientation == Orientation.IDENTITY || state.getProperties().isEmpty()) {
            return state;
        }
        if (orientation.upright()) {
            return SymmetryStates.transform(state, orientation.symmetry());
        }
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, List<String>> allowed = new LinkedHashMap<>();
        read(state, values, allowed);
        Map<String, String> turned = OrientedStates.apply(values, allowed, orientation);
        BlockState result = state;
        for (Property<?> property : state.getProperties()) {
            String value = turned.get(property.getName());
            if (value != null && !value.equals(values.get(property.getName()))) {
                result = with(result, property, value);
            }
        }
        return result;
    }

    public static boolean admits(BlockState state, Orientation orientation) {
        if (orientation.upright() || state.getProperties().isEmpty()) {
            return true;
        }
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, List<String>> allowed = new LinkedHashMap<>();
        read(state, values, allowed);
        return OrientedStates.admits(values, allowed, orientation);
    }

    private static long key(int state, Orientation orientation) {
        return (long) state << 6 | orientation.index();
    }

    private static void read(BlockState state, Map<String, String> values, Map<String, List<String>> allowed) {
        for (Property<?> property : state.getProperties()) {
            values.put(property.getName(), valueName(state, property));
            allowed.put(property.getName(), valueNames(property));
        }
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
