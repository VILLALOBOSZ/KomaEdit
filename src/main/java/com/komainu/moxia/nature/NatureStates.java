package com.komainu.moxia.nature;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Propiedades que se fijan en cada bloque según su papel, sin depender de Minecraft: el eje de troncos y raíces
 * según la dirección de su rama, y en las hojas {@code persistent=true} (nunca se caen solas), {@code waterlogged=false}
 * y su {@code distance} real a la madera. {@link NatureMaterials} las aplica a los estados que tengan esas propiedades.
 */
public final class NatureStates {
    private static final String[] AXES = {"x", "y", "z"};

    private NatureStates() {
    }

    /** Propiedades de la celda, en el orden en que se aplican; vacío si su papel no lleva ninguna. */
    public static Map<String, Object> properties(int info) {
        Map<String, Object> properties = new LinkedHashMap<>();
        switch (NatureVoxels.role(info)) {
            case WOOD, ROOT -> properties.put("axis", axis(NatureVoxels.axis(info)));
            case LEAF, HANGING -> leaf(properties, NatureVoxels.distance(info));
            default -> {
            }
        }
        return properties;
    }

    static String axis(int axis) {
        return AXES[Math.clamp(axis, 0, 2)];
    }

    /** Hojas: persistentes, secas y con su distancia (de 1 a 7, como en Minecraft). */
    static Map<String, Object> leaf(int distance) {
        Map<String, Object> properties = new LinkedHashMap<>();
        leaf(properties, distance);
        return properties;
    }

    private static void leaf(Map<String, Object> properties, int distance) {
        properties.put("persistent", Boolean.TRUE);
        properties.put("waterlogged", Boolean.FALSE);
        properties.put("distance", Math.clamp(distance, 1, 7));
    }
}
