package com.komainu.moxia.recipe;

import com.google.gson.JsonObject;
import com.komainu.moxia.gradient.GradientAxis;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeParameters;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeFormulaTest {
    private static final String FORMULA = "r <= 1 && abs(sin(a * n / 2 + z)) > 0.4";

    @Test
    void formulaSurvivesJsonRoundTrip() {
        ProceduralRecipe recipe = expression().withFormula(FORMULA);
        JsonObject json = RecipeStore.toJson(recipe);
        assertEquals(ProceduralRecipe.FORMAT_VERSION, json.get("version").getAsInt());
        assertEquals(FORMULA, json.get("formula").getAsString());
        ProceduralRecipe read = RecipeStore.fromJson(json, json.get("version").getAsInt());
        assertEquals(recipe, read);
    }

    @Test
    void olderRecipesReadWithoutFormula() {
        JsonObject json = RecipeStore.toJson(expression());
        assertFalse(json.has("formula"), "sin fórmula propia no se escribe el campo");
        json.addProperty("version", 3);
        json.addProperty("formula", "x");
        // En la versión 3 el campo no existía: aunque aparezca, no se lee.
        assertEquals("", RecipeStore.fromJson(json, 3).formula());
    }

    @Test
    void shapeParametersCarryTheFormula() {
        ShapeParameters parameters = expression().withFormula(FORMULA).shapeParameters();
        assertEquals(FORMULA, parameters.options().formula());
        assertEquals("", expression().shapeParameters().options().formula());
    }

    @Test
    void onlyTheExpressionShapeKeepsAFormula() {
        ProceduralRecipe recipe = expression().withFormula(FORMULA);
        assertEquals("", recipe.withShapeDefaults(PathShape.SWEEP).formula());
        assertEquals("", recipe.withShapeDefaults(PathShape.EXPRESSION).formula(), "cambiar de figura reinicia");
        assertEquals(FORMULA, recipe.withOptions(Map.of("preset", 2.0)).formula());
        assertEquals(FORMULA, recipe.withExtensions(RecipeExtensions.DEFAULT).formula());
    }

    @Test
    void invalidFormulasAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> expression().withFormula("r < "));
        assertTrue(error.getMessage().startsWith("Fórmula"), error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> expression().withFormula("x".repeat(300)));
    }

    private static ProceduralRecipe expression() {
        ShapeParameters defaults = PathShape.EXPRESSION.defaults();
        return new ProceduralRecipe("tubo_formula", PathShape.EXPRESSION, "stone", defaults.radius(),
                defaults.spacing(), defaults.thickness(), 0, 18, "", GradientAxis.Y, 35, false, Map.of());
    }
}
