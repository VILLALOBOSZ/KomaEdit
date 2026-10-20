package com.komainu.moxia.gizmo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un registro de estados de mentira para probar giros sin Minecraft: cada estado es un bloque con sus propiedades
 * (como texto) y un id, igual que el id crudo del juego. El 0 es el aire.
 */
final class FakeStates implements GhostPlacement.StateMapper {
    static final List<String> HORIZONTAL = List.of("north", "south", "west", "east");
    static final List<String> SIX = List.of("down", "up", "north", "south", "west", "east");
    static final List<String> BOOL = List.of("true", "false");
    static final Map<String, Map<String, List<String>>> BLOCKS = new LinkedHashMap<>();

    static {
        BLOCKS.put("air", Map.of());
        BLOCKS.put("stone", Map.of());
        BLOCKS.put("stone_bricks", Map.of());
        BLOCKS.put("oak_planks", Map.of());
        BLOCKS.put("grass_block", Map.of());
        BLOCKS.put("dirt", Map.of());
        BLOCKS.put("oak_log", Map.of("axis", List.of("x", "y", "z")));
        BLOCKS.put("oak_stairs", ordered("facing", HORIZONTAL, "half", List.of("top", "bottom"), "shape",
                List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"), "waterlogged", BOOL));
        BLOCKS.put("oak_slab", ordered("type", List.of("top", "bottom", "double"), "waterlogged", BOOL));
        BLOCKS.put("dispenser", ordered("facing", SIX, "triggered", BOOL));
        BLOCKS.put("furnace", ordered("facing", HORIZONTAL, "lit", BOOL));
        BLOCKS.put("oak_door", ordered("facing", HORIZONTAL, "half", List.of("upper", "lower"), "hinge",
                List.of("left", "right"), "open", BOOL, "powered", BOOL));
        BLOCKS.put("oak_fence", ordered("north", BOOL, "east", BOOL, "south", BOOL, "west", BOOL, "waterlogged",
                BOOL));
        BLOCKS.put("brown_mushroom_block", ordered("down", BOOL, "up", BOOL, "north", BOOL, "south", BOOL, "west",
                BOOL, "east", BOOL));
        BLOCKS.put("rail", ordered("shape", List.of("north_south", "east_west", "ascending_east", "ascending_west",
                "ascending_north", "ascending_south", "south_east", "south_west", "north_west", "north_east"),
                "waterlogged", BOOL));
        BLOCKS.put("quartz_pillar", Map.of("axis", List.of("x", "y", "z")));
        BLOCKS.put("nether_portal", Map.of("axis", List.of("x", "z")));
    }

    private final List<String> blocks = new ArrayList<>();
    private final List<Map<String, String>> values = new ArrayList<>();

    FakeStates() {
        state("air");
    }

    private static Map<String, List<String>> ordered(Object... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) pairs[index + 1];
            map.put((String) pairs[index], list);
        }
        return map;
    }

    /** El id del estado (lo da de alta si es nuevo); {@code pairs} son propiedad, valor… */
    int state(String block, String... pairs) {
        Map<String, String> state = new LinkedHashMap<>();
        Map<String, List<String>> allowed = BLOCKS.get(block);
        for (Map.Entry<String, List<String>> entry : allowed.entrySet()) {
            state.put(entry.getKey(), entry.getValue().getLast());
        }
        for (int index = 0; index < pairs.length; index += 2) {
            if (!allowed.containsKey(pairs[index]) || !allowed.get(pairs[index]).contains(pairs[index + 1])) {
                throw new IllegalArgumentException(block + " no admite " + pairs[index] + "=" + pairs[index + 1]);
            }
            state.put(pairs[index], pairs[index + 1]);
        }
        return id(block, state);
    }

    private int id(String block, Map<String, String> state) {
        for (int index = 0; index < blocks.size(); index++) {
            if (blocks.get(index).equals(block) && values.get(index).equals(state)) {
                return index;
            }
        }
        blocks.add(block);
        values.add(new LinkedHashMap<>(state));
        return blocks.size() - 1;
    }

    String block(int id) {
        return blocks.get(id);
    }

    Map<String, String> values(int id) {
        return values.get(id);
    }

    String value(int id, String property) {
        return values.get(id).get(property);
    }

    boolean isAir(int id) {
        return blocks.get(id).equals("air");
    }

    boolean admits(int id, Orientation orientation) {
        return OrientedStates.admits(values.get(id), BLOCKS.get(blocks.get(id)), orientation);
    }

    @Override
    public int map(int state, Orientation orientation) {
        String block = blocks.get(state);
        return id(block, OrientedStates.apply(values.get(state), BLOCKS.get(block), orientation));
    }

    @Override
    public String toString() {
        return "FakeStates" + blocks;
    }
}
