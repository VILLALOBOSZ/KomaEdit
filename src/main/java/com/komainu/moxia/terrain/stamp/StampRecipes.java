package com.komainu.moxia.terrain.stamp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Todas las recetas de serie, por categoría y en el orden de la galería. */
public final class StampRecipes {
    private static final List<StampRecipe> ALL = build();
    private static final Map<String, StampRecipe> BY_NAME = index(ALL);

    private StampRecipes() {
    }

    private static List<StampRecipe> build() {
        List<StampRecipe> list = new ArrayList<>();
        list.addAll(ReliefShapes.all());
        list.addAll(CarvedShapes.all());
        list.addAll(SurfaceShapes.all());
        // Estable: dentro de cada categoría se queda el orden en que se declaran.
        list.sort(Comparator.comparingInt(recipe -> recipe.category().ordinal()));
        return List.copyOf(list);
    }

    private static Map<String, StampRecipe> index(List<StampRecipe> recipes) {
        Map<String, StampRecipe> map = new LinkedHashMap<>();
        for (StampRecipe recipe : recipes) {
            if (map.put(recipe.name(), recipe) != null) {
                throw new IllegalStateException("Sello de serie repetido: " + recipe.name());
            }
        }
        return Map.copyOf(map);
    }

    public static List<StampRecipe> all() {
        return ALL;
    }

    /** La receta, o {@code null} si no hay ninguna con ese nombre. */
    public static StampRecipe byName(String name) {
        return BY_NAME.get(name);
    }
}
