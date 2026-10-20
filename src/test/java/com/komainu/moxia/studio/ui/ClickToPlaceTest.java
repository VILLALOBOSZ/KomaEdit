package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Colocar con el ratón: con el Studio abierto, un clic en el mundo pone ahí el árbol, la roca o el texto (la pantalla
 * le dice antes al servidor en qué bloque se hizo clic) y el pie dice «Donde haces clic».
 */
class ClickToPlaceTest {
    @BeforeAll
    static void thumbnails() {
        // Las miniaturas, al pedirlas: sin hilos que sigan pintando cuando empiece otra prueba.
        ChoiceThumbnails.setBlocking(true);
    }

    private static StudioDriver studio(int points) {
        StudioDriver studio = new StudioDriver("es_es", 960, 600);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, points, false);
        studio.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        return studio;
    }

    @Test
    void aClickInTheWorldPlacesTreesRocksAndTextThere() {
        for (String id : List.of("tree", "rock", "text")) {
            StudioDriver studio = studio(0);
            studio.view.tools().select(ToolCatalog.byId(id), studio.host);
            studio.render();
            assertTrue(studio.shows("Donde haces clic"), () -> id + ": " + studio.texts());
            assertEquals(StudioView.Click.WORLD, studio.clickAt(100, 300), "el clic es del mundo");
            assertTrue(studio.view.placesOnClick(studio.host), id);
            studio.host.clickedSpot = new int[]{12, 64, -30};
            studio.view.placeAtClick(studio.host);
            assertTrue(studio.host.last().startsWith("komaedit " + id + " "), studio.host.last());
            assertTrue(studio.shows("Clic en 12, 64, -30"), () -> id + ": " + studio.texts());
            assertEquals(List.of(false), studio.host.expectedGhosts, "sale su fantasma y la vista en vivo lo sigue");
        }
    }

    @Test
    void withPathPointsOrOtherToolsAClickPlacesNothing() {
        StudioDriver withPoints = studio(3);
        withPoints.view.tools().select(ToolCatalog.byId("tree"), withPoints.host);
        withPoints.render();
        assertFalse(withPoints.view.placesOnClick(withPoints.host), "los puntos de la trayectoria mandan");
        assertTrue(withPoints.shows("3 puntos"));

        for (String id : List.of("roof", "snow", "stamp", "symmetry")) {
            StudioDriver studio = studio(0);
            studio.view.tools().select(ToolCatalog.byId(id), studio.host);
            studio.render();
            assertFalse(studio.view.placesOnClick(studio.host), id);
        }
        StudioDriver figure = studio(0);
        figure.view.selectStage(StudioStage.FIGURE, figure.host);
        assertFalse(figure.view.placesOnClick(figure.host), "las figuras siguen la trayectoria");
    }
}
