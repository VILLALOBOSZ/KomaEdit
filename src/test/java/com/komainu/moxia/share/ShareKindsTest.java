package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.gradient.GradientAxis;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeExtensions;
import com.komainu.moxia.recipe.RecipeStore;
import com.komainu.moxia.sculpt.BrushFalloff;
import com.komainu.moxia.sculpt.BrushMask;
import com.komainu.moxia.sculpt.SculptBrushType;
import com.komainu.moxia.sculpt.SculptProfile;
import com.komainu.moxia.sculpt.SculptSettings;
import com.komainu.moxia.shape.PathShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ida y vuelta de cada tipo: lo guarda Alicia, lo exporta, pasa por un código y lo importa Bruno en otro servidor. */
class ShareKindsTest {
    private static final ShareTarget ALICE = ShareFixtures.player(ShareFixtures.ALICE, "Alicia");
    private static final ShareTarget BRUNO = ShareFixtures.player(ShareFixtures.BRUNO, "Bruno");

    @TempDir
    Path root;

    private ShareFixtures.MemorySculpts sculpts;
    private ShareFixtures.MemoryRecipes recipes;
    private ShareKinds.Registry kinds;

    @BeforeEach
    void setUp() {
        sculpts = new ShareFixtures.MemorySculpts();
        recipes = new ShareFixtures.MemoryRecipes();
        kinds = ShareFixtures.kinds(root, sculpts, recipes);
    }

    /** Exporta, pasa por el código y lo importa Bruno sin choques. */
    private List<ShareImport.Outcome> travel(String... refs) {
        SharePack pack = new ShareExport(List.of(refs), "", false).build(ALICE, kinds::find, "test", 1L,
                ShareLimits.DEFAULT);
        SharePack received = ShareCodec.fromCode(ShareCodec.toCode(pack), ShareLimits.DEFAULT);
        ShareImport plan = ShareImport.prepare(received, BRUNO, kinds::find, ShareFixtures.check());
        plan.entries().forEach(entry -> assertEquals(ShareImport.Status.NEW, entry.status(), entry.toString()));
        List<ShareImport.Outcome> outcomes = plan.apply(BRUNO, ShareImport.Policy.RENAME);
        outcomes.forEach(outcome -> assertTrue(outcome.saved(), outcome.toString()));
        return outcomes;
    }

    @Test
    void palettesTravel() {
        ShareFixtures.store(root, ALICE, "palettes").put("rocas",
                "minecraft:stone;minecraft:andesite;minecraft:oak_stairs[facing=east]", false);
        travel("paleta:rocas");
        assertEquals("stone;andesite;oak_stairs[facing=east]",
                ShareFixtures.store(root, BRUNO, "palettes").get("rocas").orElseThrow());
    }

    @Test
    void noisesTravel() {
        ShareFixtures.store(root, ALICE, "noises").put("suave", "Perlin(f:0.02)", false);
        travel("ruido:suave");
        assertEquals("Perlin(f:0.02)", ShareFixtures.store(root, BRUNO, "noises").get("suave").orElseThrow());
    }

    @Test
    void treeAndRockPresetsTravel() {
        ShareFixtures.store(root, ALICE, "trees").put("abeto", "spruce 24 -g 1.5 -h oak_leaves -t mod:fir_log", false);
        ShareFixtures.store(root, ALICE, "rocks").put("canto", "boulder 6 -b 70%stone,30%andesite -u none", false);
        travel("arbol:abeto", "roca:canto");
        // El tronco del mod que falta pasa al más parecido; las hojas, que tampoco están en el catálogo, también.
        String tree = ShareFixtures.store(root, BRUNO, "trees").get("abeto").orElseThrow();
        assertTrue(tree.startsWith("spruce 24 -g 1.5 -h "), tree);
        assertTrue(tree.endsWith("-t minecraft:oak_log"), tree);
        assertEquals("boulder 6 -b 70%minecraft:stone,30%minecraft:andesite -u none",
                ShareFixtures.store(root, BRUNO, "rocks").get("canto").orElseThrow());
    }

    @Test
    void sculptPresetsTravel() {
        SculptSettings settings = new SculptSettings(SculptBrushType.values()[0], 6, 4, 3,
                BrushFalloff.SMOOTH, 10, 5, false, new BrushMask(true, true, Set.of("minecraft:stone"), Set.of(),
                Set.of("mod:marble")));
        sculpts.save(ALICE.id(), "Monte", new SculptProfile(settings, "70%stone,30%mod:marble"));
        travel("escultura:Monte");
        SculptProfile imported = sculpts.load(BRUNO.id(), "Monte");
        assertEquals(6, imported.settings().radius());
        assertEquals("70%minecraft:stone,30%minecraft:air", imported.pattern());
        assertEquals(Set.of("minecraft:air"), imported.settings().mask().replaceOnly());
    }

    @Test
    void recipesTravelWithTheirFormula() {
        ProceduralRecipe recipe = new ProceduralRecipe("Arco", PathShape.EXPRESSION, "stone_brick_stairs",
                "andesite", 4, 3, 1, 0, 8, "", GradientAxis.Y, 0, false, Map.of(), RecipeExtensions.DEFAULT,
                "r <= 1 - 0.2 * sin(6 * a)");
        recipes.save(ALICE.id(), recipe);
        travel("receta:Arco");
        ProceduralRecipe imported = recipes.load(BRUNO.id(), "Arco");
        assertEquals("r <= 1 - 0.2 * sin(6 * a)", imported.formula());
        assertEquals("minecraft:stone_brick_stairs", imported.pattern());
    }

    @Test
    void dangerousFormulasAndBrokenDataAreRefused() {
        for (String formula : List.of("java.lang.Runtime.getRuntime().exec(\"calc\")", "exec(1)", "x;y",
                "r <= " + "1+".repeat(400) + "1", "import os")) {
            JsonObject data = recipeData();
            data.addProperty("formula", formula);
            ShareCheck check = ShareFixtures.check();
            check.begin();
            SharePack.Item item = new SharePack.Item("recipe", "mala", 1, data);
            assertFalse(ShareImport.prepare(SharePack.of("x", "", "", 0, List.of(item)), BRUNO, kinds::find, check)
                    .importable(), formula);
        }
        JsonObject tooBig = new JsonObject();
        tooBig.addProperty("e", "stone;".repeat(5000));
        ShareCheck check = ShareFixtures.check();
        ShareImport plan = ShareImport.prepare(SharePack.of("x", "", "", 0, List.of(
                new SharePack.Item("palette", "enorme", 1, tooBig),
                new SharePack.Item("noise", "roto", 1, new JsonObject()),
                new SharePack.Item("tree", "otro", 1, text("#base -g 2")),
                new SharePack.Item("tree", "masc", 1, text("oak -m =y<64")),
                new SharePack.Item("palette", "../../../etc", 1, text("stone")))), BRUNO, kinds::find, check);
        plan.entries().forEach(entry -> assertEquals(ShareImport.Status.INVALID, entry.status(), entry.toString()));
    }

    @Test
    void permissionsAreCheckedPerKind() {
        ShareTarget noTrees = ShareFixtures.player(ShareFixtures.BRUNO, "Bruno", "komaedit.build.tree");
        ShareImport plan = ShareImport.prepare(SharePack.of("x", "", "", 0, List.of(
                new SharePack.Item("tree", "abeto", 1, text("spruce 24")),
                new SharePack.Item("noise", "suave", 1, text("Perlin()")))), noTrees, kinds::find,
                ShareFixtures.check());
        assertEquals(ShareImport.Status.DENIED, plan.entries().get(0).status());
        assertEquals(ShareImport.Status.NEW, plan.entries().get(1).status());
    }

    private static JsonObject text(String value) {
        JsonObject data = new JsonObject();
        data.addProperty("t", value);
        data.addProperty("e", value);
        return data;
    }

    private static JsonObject recipeData() {
        ProceduralRecipe recipe = new ProceduralRecipe("mala", PathShape.EXPRESSION, "stone", "andesite", 4, 3, 1, 0,
                8, "", GradientAxis.Y, 0, false, Map.of(), RecipeExtensions.DEFAULT, "r <= 1");
        return RecipeStore.toJson(recipe);
    }
}
