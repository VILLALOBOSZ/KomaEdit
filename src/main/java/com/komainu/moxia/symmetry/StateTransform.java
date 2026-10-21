package com.komainu.moxia.symmetry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gira y refleja un estado de bloque escrito como texto ({@code propiedad → valor}), sin Minecraft. Hace lo de
 * {@code BlockTransformExtent} de WorldEdit (direcciones, ejes, {@code rotation} de 16 pasos, cofres dobles, losas y
 * escaleras) y además lo que WorldEdit deja igual: bisagras y mitades de puerta, raíles, caras de suelo y techo,
 * campanas, faroles colgados y la orientación completa del crafter.
 *
 * <p>Cada valor nuevo se comprueba contra los que admite el bloque: si no existe (una tolva no mira arriba), la
 * propiedad se queda como estaba.</p>
 */
public final class StateTransform {
    private static final Set<String> DIRECTIONS = Set.of("north", "south", "east", "west", "up", "down");
    private static final List<String> HORIZONTAL = List.of("north", "east", "south", "west");
    private static final Set<String> AXES = Set.of("x", "y", "z");

    private StateTransform() {
    }

    /**
     * @param state   valor actual de cada propiedad
     * @param allowed valores que admite cada propiedad del bloque
     */
    public static Map<String, String> apply(Map<String, String> state, Map<String, ? extends Collection<String>> allowed,
                                            SymmetryTransform transform) {
        Map<String, String> result = new LinkedHashMap<>(state);
        if (transform.identity()) {
            return result;
        }
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String name = entry.getKey();
            if (DIRECTIONS.contains(name)) {
                continue;
            }
            Collection<String> values = values(allowed, name);
            String mapped = map(name, entry.getValue(), values, transform);
            if (mapped != null && !mapped.equals(entry.getValue()) && values.contains(mapped)) {
                result.put(name, mapped);
            }
        }
        connections(state, allowed, transform, result);
        return result;
    }

    private static String map(String name, String value, Collection<String> values, SymmetryTransform transform) {
        if (values.size() >= 2 && DIRECTIONS.containsAll(values)) {
            // facing, vertical_direction y cualquier propiedad de dirección de un mod.
            return direction(value, transform);
        }
        if (name.equals("rotation") && values.size() == 16) {
            return rotation16(value, transform);
        }
        if (name.endsWith("axis") && AXES.containsAll(values)) {
            return transform.quarterTurns() % 2 == 1 ? swap(value, "x", "z") : value;
        }
        if (values.contains("inner_left")) {
            // Forma de escalera: izquierda y derecha se cambian con un espejo horizontal.
            return transform.horizontalFlip() ? value.replace("left", "@").replace("right", "left").replace("@", "right")
                    : value;
        }
        if (name.equals("shape") && values.contains("north_south")) {
            return rail(value, values, transform);
        }
        if (values.contains("left") && values.contains("right")) {
            // Bisagra de puerta y cofre doble.
            return transform.horizontalFlip() ? swap(value, "left", "right") : value;
        }
        if (values.contains("top") && values.contains("bottom")) {
            // Mitad de escalera y trampilla, losa de arriba o de abajo.
            return transform.mirrorY() ? swap(value, "top", "bottom") : value;
        }
        if (values.contains("upper") && values.contains("lower")) {
            // Mitades de puerta y de planta alta: el espejo en Y también intercambia sus posiciones.
            return transform.mirrorY() ? swap(value, "upper", "lower") : value;
        }
        if (values.contains("floor") && values.contains("ceiling")) {
            // Botones, palancas y muelas (face); campanas (attachment).
            return transform.mirrorY() ? swap(value, "floor", "ceiling") : value;
        }
        if (name.equals("hanging") && values.contains("true")) {
            // Farol o propágulo colgado: reflejado en Y queda de pie, y al revés.
            return transform.mirrorY() ? swap(value, "true", "false") : value;
        }
        if (name.equals("orientation")) {
            return orientation(value, values, transform);
        }
        return value;
    }

    /** Lleva una dirección ({@code north}, {@code up}…) con la transformación; los giros se redondean a cuartos. */
    public static String direction(String value, SymmetryTransform transform) {
        switch (value) {
            case "up":
                return transform.mirrorY() ? "down" : "up";
            case "down":
                return transform.mirrorY() ? "up" : "down";
            default:
                break;
        }
        int[] vector = switch (value) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "east" -> new int[]{1, 0};
            case "west" -> new int[]{-1, 0};
            default -> null;
        };
        if (vector == null) {
            return value;
        }
        int[] turned = transform.direction(vector[0], vector[1]);
        return horizontalName(turned[0], turned[1]);
    }

    private static String horizontalName(int dx, int dz) {
        if (dx > 0) {
            return "east";
        }
        if (dx < 0) {
            return "west";
        }
        return dz > 0 ? "south" : "north";
    }

    /**
     * {@code rotation} de 16 pasos (carteles, estandartes, cabezas): 0 mira al sur y cada paso gira 22,5° hacia el
     * oeste. Usa el ángulo exacto, así un radial de 8 o 16 brazos gira también las diagonales.
     */
    static String rotation16(String value, SymmetryTransform transform) {
        int step;
        try {
            step = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return value;
        }
        double angle = step * Math.PI / 8.0;
        double[] turned = transform.rotate(-Math.sin(angle), Math.cos(angle));
        double result = Math.atan2(-turned[0], turned[1]);
        return Integer.toString(Math.floorMod((int) Math.round(result / (Math.PI / 8.0)), 16));
    }

    private static String rail(String value, Collection<String> values, SymmetryTransform transform) {
        String[] parts = value.split("_");
        if (parts.length != 2) {
            return value;
        }
        if (parts[0].equals("ascending")) {
            String toward = direction(parts[1], horizontalOnly(transform));
            // Reflejado en Y, la vía baja hacia donde antes subía.
            return "ascending_" + (transform.mirrorY() ? opposite(toward) : toward);
        }
        String first = direction(parts[0], horizontalOnly(transform));
        String second = direction(parts[1], horizontalOnly(transform));
        String candidate = first + "_" + second;
        return values.contains(candidate) ? candidate : second + "_" + first;
    }

    /**
     * Frente y arriba del crafter (y de la pieza de puzle). Si el par girado no existe (uno que mira de lado siempre
     * tiene arriba arriba: reflejado en Y no puede tener arriba abajo), manda el frente, que es por donde suelta lo que
     * fabrica: se gira el frente y se queda el arriba de antes.
     */
    private static String orientation(String value, Collection<String> values, SymmetryTransform transform) {
        String[] parts = value.split("_");
        if (parts.length != 2 || !DIRECTIONS.contains(parts[0]) || !DIRECTIONS.contains(parts[1])) {
            return value;
        }
        String front = direction(parts[0], transform);
        String turned = front + "_" + direction(parts[1], transform);
        if (values.contains(turned)) {
            return turned;
        }
        String keptTop = front + "_" + parts[1];
        return values.contains(keptTop) ? keptTop : value;
    }

    /** Conexiones con los vecinos (vallas, muros, cristal, redstone, setas, liquen…): se cambian de lado. */
    private static void connections(Map<String, String> state, Map<String, ? extends Collection<String>> allowed,
                                    SymmetryTransform transform, Map<String, String> result) {
        if (state.keySet().containsAll(HORIZONTAL)) {
            Map<String, String> moved = new LinkedHashMap<>();
            for (String side : HORIZONTAL) {
                moved.put(direction(side, horizontalOnly(transform)), state.get(side));
            }
            boolean valid = true;
            for (Map.Entry<String, String> entry : moved.entrySet()) {
                valid &= values(allowed, entry.getKey()).contains(entry.getValue());
            }
            if (valid) {
                result.putAll(moved);
            }
        }
        // Arriba y abajo solo se cambian si existen los dos: el «up» de un muro es su poste, no una conexión.
        if (transform.mirrorY() && state.containsKey("up") && state.containsKey("down")) {
            String up = state.get("up");
            String down = state.get("down");
            if (values(allowed, "up").contains(down)
                    && values(allowed, "down").contains(up)) {
                result.put("up", down);
                result.put("down", up);
            }
        }
    }

    private static Collection<String> values(Map<String, ? extends Collection<String>> allowed, String name) {
        Collection<String> values = allowed.get(name);
        return values == null ? List.of() : values;
    }

    private static SymmetryTransform horizontalOnly(SymmetryTransform transform) {
        return transform.mirrorY()
                ? new SymmetryTransform(transform.mirrorX(), transform.mirrorZ(), false, transform.turn(), transform.arms())
                : transform;
    }

    private static String opposite(String direction) {
        return switch (direction) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            case "up" -> "down";
            case "down" -> "up";
            default -> direction;
        };
    }

    private static String swap(String value, String first, String second) {
        if (value.equals(first)) {
            return second;
        }
        return value.equals(second) ? first : value;
    }
}
