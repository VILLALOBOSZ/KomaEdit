package com.komainu.moxia.client.recipe;

import com.komainu.moxia.recipe.ProceduralRecipe;

import java.util.List;

public final class ClientRecipeCatalog {
    private static volatile List<ProceduralRecipe> recipes = List.of();

    private ClientRecipeCatalog() {
    }

    public static void update(List<ProceduralRecipe> values) {
        recipes = List.copyOf(values);
    }

    public static List<ProceduralRecipe> recipes() {
        return recipes;
    }

    public static void clear() {
        recipes = List.of();
    }
}
