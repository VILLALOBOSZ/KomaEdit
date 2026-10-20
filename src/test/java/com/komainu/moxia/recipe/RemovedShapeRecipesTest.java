package com.komainu.moxia.recipe;

import com.google.gson.JsonObject;
import com.komainu.moxia.persistence.TolerantListing;
import com.komainu.moxia.shape.PathShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La figura Mandala ya no existe: una receta guardada que la use se salta con el aviso de siempre de receta ilegible
 * en el registro, y las demás del proyecto se siguen leyendo.
 */
class RemovedShapeRecipesTest {
    @TempDir
    Path directory;

    @Test
    void theMandalaIsGone() {
        assertTrue(Arrays.stream(PathShape.values()).noneMatch(shape -> shape.id().equals("mandala")));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PathShape.byId("mandala"));
        assertTrue(error.getMessage().contains("mandala"), error.getMessage());
    }

    @Test
    void aSavedMandalaRecipeIsSkippedAndTheRestStillLoads() throws Exception {
        ProceduralRecipe kept = ProceduralRecipe.defaults();
        Path good = write("arco.json", RecipeStore.toJson(kept));
        JsonObject mandala = RecipeStore.toJson(kept);
        mandala.addProperty("name", "roseton");
        mandala.addProperty("shape", "mandala");
        Path removed = write("roseton.json", mandala);

        List<ProceduralRecipe> loaded = TolerantListing.readAll(List.of(removed, good), RecipeStore::read,
                "la receta");
        assertEquals(List.of(kept), loaded, "la de la mandala se salta; la otra se lee");
        IllegalStateException reason = assertThrows(IllegalStateException.class, () -> RecipeStore.read(removed));
        assertTrue(reason.getMessage().contains("roseton.json"), reason.getMessage());
        assertTrue(Files.exists(removed), "se queda en disco por si se quiere recuperar");
    }

    private Path write(String name, JsonObject json) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        return file;
    }
}
