package com.komainu.moxia.recipe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guardar una receta llega por paquete con el nombre que quiera el cliente: sin tope, un cliente modificado llena el
 * disco de recetas (y el Studio solo enseña las 64 primeras).
 */
class RecipeStoreLimitTest {
    @Test
    void aPlayerCannotSaveMoreRecipesThanTheStudioShows(@TempDir Path directory) throws IOException {
        ProceduralRecipe recipe = ProceduralRecipe.defaults();
        for (int index = 0; index < RecipeStore.MAX_RECIPES; index++) {
            RecipeStore.save(directory, recipe.withName("r" + index));
        }
        assertThrows(IllegalArgumentException.class, () -> RecipeStore.save(directory, recipe.withName("una_mas")));
        assertDoesNotThrow(() -> RecipeStore.save(directory, recipe.withName("r3")), "sustituir una sí se puede");
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(RecipeStore.MAX_RECIPES, files.filter(file -> file.toString().endsWith(".json")).count());
        }
    }
}
