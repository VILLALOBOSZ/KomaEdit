package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintScope;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.recipe.RecipeExtensions;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.studio.FigureVariation;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.BrushSync;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que se le pidió al Studio tras la prueba del autor sobre la 1.8.0: que se entienda sin leer la documentación y que
 * todo se haga con el ratón. Modificadores y Material viven dentro de Figuras, cada control dice qué hace y el pie (y
 * la leyenda del mundo) dicen qué hace cada clic.
 */
class FigureClarityTest {
    @BeforeAll
    static void thumbnails() {
        ChoiceThumbnails.setBlocking(true);
    }

    private static StudioDriver figure(String language) {
        StudioDriver studio = new StudioDriver(language, 960, 540);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 3, false);
        studio.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        for (int index = 0; index < 3; index++) {
            studio.host.points.add(new int[]{index * 8, 64, 0});
        }
        studio.view.figure().selectShape(studio.host, PathShape.BRAID);
        studio.view.selectStage(StudioStage.FIGURE, studio.host);
        studio.render();
        return studio;
    }

    @Test
    void theFigureHasItsSizeMaterialAndVariationsInOnePlace() {
        StudioDriver studio = figure("es_es");
        for (String section : List.of("TAMAÑO", "MATERIAL", "VARIACIONES")) {
            assertTrue(studio.has(section), section + ": " + studio.texts());
        }
        for (String variation : List.of("Afinar", "Retorcer", "Otra figura encima", "Pegar al terreno",
                "Repetir lo copiado")) {
            assertTrue(studio.has(variation), variation);
        }
        assertFalse(studio.has("Modificadores"), "ya no hay etapa aparte");
        assertFalse(studio.has("Giro"), "lo que se toca poco va plegado");
        // Abrir Más ajustes (al final, desplazando) enseña el giro, el tubo y los remates.
        for (int step = 0; step < 30 && !studio.has("MÁS AJUSTES"); step++) {
            studio.view.mouseScrolled(studio.view.frame().innerLeft() + 2, studio.view.frame().viewport().y() + 2,
                    -1, studio.host);
        }
        studio.click("MÁS AJUSTES");
        for (int step = 0; step < 30 && !studio.has("Quitar bloques sueltos"); step++) {
            studio.view.mouseScrolled(studio.view.frame().innerLeft() + 2, studio.view.frame().viewport().y() + 2,
                    -1, studio.host);
        }
        assertTrue(studio.has("Giro") && studio.has("Cerrar huecos") && studio.has("Quitar bloques sueltos"));
    }

    @Test
    void aVariationTurnsOnWithOneClickItsControlsAppearBelowAndRightClickRemovesIt() {
        StudioDriver studio = figure("es_es");
        assertFalse(studio.has("Cuánto gira"));
        studio.click("Retorcer");
        RecipeExtensions extensions = studio.view.figure().draft().extensions();
        assertEquals(FigureVariation.TWIST_DEGREES, extensions.twistDegrees(), "una vuelta, que se nota");
        assertTrue(studio.has("Cuánto gira"), "sus controles, justo debajo");
        assertTrue(studio.has("1 puesta"), "y el título dice cuántas hay");
        assertTrue(studio.view.status().contains("Retorcer"), studio.view.status());
        // Clic derecho sobre la fila (un rato después): fuera. Z: vuelve.
        studio.advance(2_000);
        studio.reset("Retorcer");
        assertEquals(0, studio.view.figure().draft().extensions().twistDegrees());
        studio.key(StudioKey.letter('Z', false, false));
        assertEquals(FigureVariation.TWIST_DEGREES, studio.view.figure().draft().extensions().twistDegrees(),
                "deshacer ajustes también vale para las variaciones");
    }

    @Test
    void structuresOnlyOfferToStickToTheGround() {
        StudioDriver studio = figure("es_es");
        studio.view.figure().selectShape(studio.host, PathShape.TORII);
        studio.render();
        assertTrue(studio.has("Pegar al terreno"));
        assertFalse(studio.has("Retorcer"), "retorcer un torii lo rompería");
        assertFalse(studio.has("Afinar"));
    }

    @Test
    void theGradientIsASwitchUnderTheBlockAndRemembersItsPalette() {
        StudioDriver studio = figure("es_es");
        assertTrue(studio.has("Bloque"));
        assertFalse(studio.has("Paleta"));
        studio.click("Degradado");
        assertTrue(studio.view.figure().draft().usesGradient());
        assertTrue(studio.has("Paleta"));
        studio.wheel("Paleta", 1);
        String chosen = studio.view.figure().draft().paletteId();
        studio.click("Degradado");
        assertFalse(studio.view.figure().draft().usesGradient(), "otra vez un solo bloque");
        studio.click("Degradado");
        assertEquals(chosen, studio.view.figure().draft().paletteId(), "y al volver, la misma paleta");
    }

    @Test
    void overTheNumberAShortHelpWithItsEndsAndTheGesturesInTheFooter() {
        StudioDriver studio = figure("es_es");
        int[] value = studio.valuePoint("Radio");
        studio.mouseX = value[0];
        studio.mouseY = value[1];
        studio.render();
        assertTrue(studio.has("Lo ancha que es la figura alrededor del camino. Es lo que más cambia cuántos"
                + " bloques lleva.") || studio.shows("Lo ancha que es la figura"), studio.texts().toString());
        assertTrue(studio.shows("de estrecha a ancha"), "los extremos, en palabras");
        assertEquals(studio.host.tr("gui.komaedit.hint.number"), studio.view.pointerHint());
        assertTrue(studio.shows("Rueda o − +: cambiar"), "el pie dice los gestos");
    }

    @Test
    void draggingANumberShowsItsEndsInTheRow() {
        StudioDriver studio = figure("es_es");
        int[] value = studio.valuePoint("Radio");
        studio.view.mouseClicked(value[0], value[1], 0, studio.host);
        studio.view.mouseDragged(value[0] + 20, value[1], 0, studio.host);
        studio.mouseX = value[0] + 20;
        studio.mouseY = value[1];
        assertTrue(studio.has("estrecha") && studio.has("ancha"), studio.texts().toString());
        studio.view.mouseReleased(value[0] + 20, value[1], 0, studio.host);
        assertTrue(studio.has("Radio"), "al soltar, la etiqueta otra vez");
    }

    @Test
    void overTheWorldTheFooterSaysWhatAClickDoesAndRestingShowsTheWholeLegend() {
        StudioDriver studio = figure("es_es");
        studio.mouseX = 100;
        studio.mouseY = 200;
        studio.render();
        assertTrue(studio.view.pointerHint().startsWith("Clic: punto nuevo"), studio.view.pointerHint());
        assertFalse(studio.has("Qué hace el ratón en el mundo"), "la leyenda espera a que el ratón se pare");
        studio.advance(StudioView.LEGEND_MILLIS + 50);
        studio.render();
        assertTrue(studio.has("Qué hace el ratón en el mundo"));
        assertTrue(studio.has("Arrastra un punto: moverlo"), studio.texts().toString());
        assertTrue(studio.has("Alt+clic: copiar ese bloque"));
        studio.mouseX = 140;
        studio.render();
        assertFalse(studio.has("Qué hace el ratón en el mundo"), "al moverlo se esconde");
    }

    @Test
    void theAltEyedropperSetsTheMaterialOfWhatYouAreMaking() {
        StudioDriver studio = figure("es_es");
        studio.view.eyedropper("minecraft:mossy_cobblestone", false, studio.host);
        assertEquals("minecraft:mossy_cobblestone", studio.view.figure().draft().pattern());
        studio.key(StudioKey.letter('Z', false, false));
        assertEquals("stone", studio.view.figure().draft().pattern(), "se deshace como cualquier ajuste");

        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("roof"), studio.host);
        studio.view.eyedropper("minecraft:deepslate_tiles", false, studio.host);
        assertTrue(studio.view.tools().buildCommand(studio.host, false).contains("deepslate_tiles"),
                studio.view.tools().buildCommand(studio.host, false));

        studio.view.selectStage(StudioStage.COLOR, studio.host);
        studio.view.eyedropper("minecraft:stone", false, studio.host);
        studio.view.eyedropper("minecraft:calcite", true, studio.host);
        studio.render();
        assertTrue(studio.view.status().contains("Calcita") || studio.view.status().contains("Calcite"), studio.view.status());
    }

    @Test
    void whatEachClickInTheWorldDoesInEveryStage() {
        StudioDriver studio = figure("es_es");
        assertEquals(StudioView.WorldAction.POINTS, studio.view.worldAction(studio.host));
        studio.view.selectStage(StudioStage.COLOR, studio.host);
        assertEquals(StudioView.WorldAction.SELECT, studio.view.worldAction(studio.host), "Color sombrea la selección");
        studio.view.selectStage(StudioStage.BLUEPRINTS, studio.host);
        assertEquals(StudioView.WorldAction.NONE, studio.view.worldAction(studio.host), "sin plano elegido");
        studio.host.blueprints.list.add(new BlueprintSummary(BlueprintRef.display("casita", BlueprintScope.SHARED),
                "casita", true, false, "Villalobos", 0L, 9, 9, 7, 342, List.of("aldea"), "casita"));
        studio.view.blueprints().select(BlueprintRef.display("casita", BlueprintScope.SHARED));
        studio.render();
        assertEquals(StudioView.WorldAction.PLACE, studio.view.worldAction(studio.host), "los planos, con clic");
        studio.view.placeAtClick(studio.host);
        assertEquals("komaedit blueprint place " + BlueprintRef.display("casita", BlueprintScope.SHARED) + " -v",
                studio.host.last());
        studio.host.heldTool = "pincel:1";
        assertEquals(StudioView.WorldAction.PAINT, studio.view.worldAction(studio.host), "con el sello, pinta");
        studio.host.ghost = GhostFooterTest.ghost("Árbol roble", "giro 0°", "1, 2, 3", "", new int[5]);
        assertEquals(StudioView.WorldAction.GHOST, studio.view.worldAction(studio.host), "con fantasma, lo mueve");
        studio.view.selectStage(StudioStage.PATH, studio.host);
        assertEquals(StudioView.WorldAction.POINTS, studio.view.worldAction(studio.host),
                "en Trayectoria siempre son los puntos");
    }

    @Test
    void theBlueprintStampBrushFollowsTheStage() {
        StudioDriver studio = figure("es_es");
        StudioMockupsTest.blueprints(studio);
        studio.render();
        studio.click("Estampar");
        String casita = BlueprintRef.display("casita", BlueprintScope.SHARED);
        assertEquals("komaedit pincel stamp " + casita, studio.host.last());
        studio.host.heldTool = "pincel:9";
        studio.tick();
        // Otro radio: el pincel de la mano lo recibe sin volver a pulsar Estampar.
        studio.click("Radio auto");
        studio.advance(10);
        studio.advance(BrushSync.MIN_INTERVAL_MILLIS + 10);
        assertEquals("komaedit pincel actualizar stamp " + casita + " 4", studio.host.last());
        // Otro plano elegido: también.
        studio.view.blueprints().select(BlueprintRef.display("pozo", BlueprintScope.SHARED));
        studio.advance(10);
        studio.advance(BrushSync.MIN_INTERVAL_MILLIS + 10);
        assertEquals("komaedit pincel actualizar stamp " + BlueprintRef.display("pozo", BlueprintScope.SHARED) + " 4",
                studio.host.last());
        // Con otra cosa en la mano, nada.
        int sent = studio.host.sent.size();
        studio.host.heldTool = null;
        studio.click("Radio 4");
        studio.advance(10);
        studio.advance(BrushSync.MIN_INTERVAL_MILLIS + 10);
        assertEquals(sent, studio.host.sent.size());
    }
}
