package com.komainu.moxia.capability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Recorrido de propiedades de «Cambiar estado». Trabaja con nombres y valores en texto, como los da el estado del bloque
 * ({@code facing=north}, {@code half=bottom}…), y no toca Minecraft.
 *
 * <p>Hay una propiedad activa por jugador. El clic derecho la avanza al valor siguiente (con Mayús, al anterior) y
 * la F cambia de propiedad. Si el bloque no tiene la activa, se usa la primera del orden de {@link #PRIORITY}.</p>
 */
public final class PropertyCycle {
    /** Orden de preferencia: lo que más se retoca a mano va primero. El resto sigue el orden del propio bloque. */
    public static final List<String> PRIORITY = List.of(
            "facing", "half", "type", "shape", "axis", "rotation", "open", "hinge", "face", "attachment",
            "hanging", "north", "east", "south", "west", "up", "down", "layers", "slant", "tilt",
            "waterlogged", "lit", "persistent", "snowy", "bottom", "in_wall"
    );

    /** Una propiedad del bloque: nombre, valores posibles en su orden y el valor actual. */
    public record Property(String name, List<String> values, String value) {
        public Property {
            values = List.copyOf(values);
        }
    }

    private PropertyCycle() {
    }

    /**
     * ¿Se puede retocar sin romper el bloque? Las mitades de los bloques de dos partes (puertas, plantas altas,
     * camas) no: cambiar una sola deja la otra huérfana.
     */
    public static boolean editable(Property property) {
        if (property.values().size() < 2) {
            return false;
        }
        Set<String> values = Set.copyOf(property.values());
        if (property.name().equals("half") && values.contains("upper") && values.contains("lower")) {
            return false;
        }
        return !(property.name().equals("part") && values.contains("head") && values.contains("foot"));
    }

    /** Propiedades retocables en el orden en que se recorren. */
    public static List<Property> ordered(List<Property> properties) {
        List<Property> editable = new ArrayList<>();
        for (Property property : properties) {
            if (editable(property)) {
                editable.add(property);
            }
        }
        List<Property> sorted = new ArrayList<>(editable);
        sorted.sort(Comparator.comparingInt(property -> rank(property.name(), editable.indexOf(property))));
        return List.copyOf(sorted);
    }

    /** La propiedad activa: {@code preferred} si el bloque la tiene, si no la primera; {@code null} si no hay. */
    public static Property active(List<Property> ordered, String preferred) {
        if (ordered.isEmpty()) {
            return null;
        }
        if (preferred != null) {
            for (Property property : ordered) {
                if (property.name().equals(preferred)) {
                    return property;
                }
            }
        }
        return ordered.getFirst();
    }

    /** Valor que queda al avanzar {@code steps} pasos (negativo hacia atrás), dando la vuelta. */
    public static String step(Property property, int steps) {
        List<String> values = property.values();
        int current = Math.max(0, values.indexOf(property.value()));
        return values.get(Math.floorMod(current + steps, values.size()));
    }

    /** Nombre de la propiedad que queda activa al cambiar {@code steps} propiedades desde la actual. */
    public static String switchProperty(List<Property> ordered, String current, int steps) {
        if (ordered.isEmpty()) {
            return current;
        }
        int index = 0;
        for (int candidate = 0; candidate < ordered.size(); candidate++) {
            if (ordered.get(candidate).name().equals(current)) {
                index = candidate;
                break;
            }
        }
        return ordered.get(Math.floorMod(index + steps, ordered.size())).name();
    }

    private static int rank(String name, int original) {
        int priority = PRIORITY.indexOf(name);
        return priority >= 0 ? priority : PRIORITY.size() + original;
    }
}
