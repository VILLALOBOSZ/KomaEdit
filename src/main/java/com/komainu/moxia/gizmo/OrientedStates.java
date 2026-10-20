package com.komainu.moxia.gizmo;

import com.komainu.moxia.symmetry.StateTransform;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gira y refleja un estado de bloque escrito como texto ({@code propiedad → valor}) con una {@link Orientation}, sin
 * Minecraft.
 *
 * <ul>
 *     <li>Si el objeto sigue de pie (giros en Y, volteos y boca abajo), es la misma transformación que la simetría y
 *     el escalado del portapapeles: {@link StateTransform} (escaleras con su esquina, troncos, puertas con bisagra,
 *     raíles, carteles de 16 pasos, conexiones…).</li>
 *     <li>Si se tumba (un cuarto de vuelta en X o en Z), las direcciones de seis valores ({@code facing} de un
 *     dispensador, un pistón o una vara del End), los ejes ({@code axis} de troncos y pilares) y las conexiones de
 *     seis lados (setas, liquen) se llevan con la matriz. Lo que no se puede tumbar (escaleras, losas de media altura,
 *     puertas, vallas, cofres dobles…) no admite ese giro: {@link #admits} lo dice y el gizmo no ofrece el anillo.</li>
 * </ul>
 *
 * <p>Cada valor nuevo se comprueba contra los que admite el bloque.</p>
 */
public final class OrientedStates {
    private static final List<String> SIDES = List.of("west", "east", "down", "up", "north", "south");
    private static final Set<String> DIRECTIONS = Set.copyOf(SIDES);
    private static final Set<String> AXES = Set.of("x", "y", "z");
    /** Propiedades que dependen de dónde está arriba y no tienen equivalente tumbadas. */
    private static final Set<String> UPRIGHT_ONLY = Set.of("half", "shape", "hinge", "face", "attachment", "hanging",
            "orientation", "rotation", "tilt", "thickness", "vertical_direction", "leaves", "bottom",
            "in_wall", "attached");
    /** Valores de {@code type} que se mantienen tumbados (una losa doble es un bloque entero). */
    private static final Set<String> TYPE_INVARIANT = Set.of("double", "single", "normal", "sticky");

    private OrientedStates() {
    }

    /**
     * @param state   valor de cada propiedad
     * @param allowed valores que admite cada propiedad del bloque
     */
    public static Map<String, String> apply(Map<String, String> state, Map<String, ? extends Collection<String>> allowed,
                                            Orientation orientation) {
        if (orientation == Orientation.IDENTITY || state.isEmpty()) {
            return new LinkedHashMap<>(state);
        }
        if (orientation.upright()) {
            return StateTransform.apply(state, allowed, orientation.symmetry());
        }
        Map<String, String> result = new LinkedHashMap<>(state);
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String name = entry.getKey();
            if (DIRECTIONS.contains(name)) {
                continue;
            }
            Collection<String> values = values(allowed, name);
            String mapped = tipped(name, entry.getValue(), values, orientation);
            if (mapped != null && values.contains(mapped)) {
                result.put(name, mapped);
            }
        }
        if (state.keySet().containsAll(SIDES)) {
            Map<String, String> moved = new LinkedHashMap<>();
            for (int face = 0; face < 6; face++) {
                moved.put(SIDES.get(orientation.face(face)), state.get(SIDES.get(face)));
            }
            boolean valid = true;
            for (Map.Entry<String, String> entry : moved.entrySet()) {
                valid &= values(allowed, entry.getKey()).contains(entry.getValue());
            }
            if (valid) {
                result.putAll(moved);
            }
        }
        return result;
    }

    /**
     * ¿Queda el estado igual de bien con esta orientación? De pie siempre; tumbado, solo si cada propiedad que
     * depende de la orientación tiene un valor válido después.
     */
    public static boolean admits(Map<String, String> state, Map<String, ? extends Collection<String>> allowed,
                                 Orientation orientation) {
        if (orientation.upright() || state.isEmpty()) {
            return true;
        }
        boolean horizontalSides = false;
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            Collection<String> values = values(allowed, name);
            if (DIRECTIONS.contains(name)) {
                horizontalSides |= !name.equals("up") && !name.equals("down");
                continue;
            }
            if (UPRIGHT_ONLY.contains(name)) {
                return false;
            }
            if (name.equals("type") && (values.contains("top") || values.contains("bottom")
                    || values.contains("left") || values.contains("right")) && !TYPE_INVARIANT.contains(value)) {
                return false;
            }
            String mapped = tipped(name, value, values, orientation);
            if (mapped != null && !values.contains(mapped)) {
                return false;
            }
        }
        // Conexiones: solo se tumban las de seis lados (vallas, muros y cristal no tienen arriba y abajo).
        return !horizontalSides || state.keySet().containsAll(SIDES);
    }

    /** Valor tumbado de una propiedad de dirección o de eje; {@code null} si no depende de la orientación. */
    private static String tipped(String name, String value, Collection<String> values, Orientation orientation) {
        if (values.size() >= 2 && DIRECTIONS.containsAll(values) && DIRECTIONS.contains(value)) {
            return SIDES.get(orientation.face(SIDES.indexOf(value)));
        }
        if (name.endsWith("axis") && AXES.containsAll(values) && AXES.contains(value)) {
            int axis = value.charAt(0) - 'x';
            return String.valueOf((char) ('x' + orientation.axisOf(axis)));
        }
        return null;
    }

    private static Collection<String> values(Map<String, ? extends Collection<String>> allowed, String name) {
        Collection<String> values = allowed.get(name);
        return values == null ? List.of() : values;
    }
}
