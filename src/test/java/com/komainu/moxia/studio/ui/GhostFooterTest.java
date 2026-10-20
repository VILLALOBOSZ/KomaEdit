package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El pie del Studio mientras se coloca un fantasma con gizmo: posición, giro, tamaño, opciones y botones. */
class GhostFooterTest {
    /**
     * Un fantasma de mentira; {@code counts} cuenta colocar, copiar, cancelar, conservar y aire (en ese orden).
     */
    static StudioHost.Ghost ghost(String title, String summary, String position, String message, int[] counts) {
        return new StudioHost.Ghost(title, summary, position, counts[3] % 2 == 1, true, false, true, false, message,
                () -> counts[0]++, () -> counts[1]++, () -> counts[2]++, () -> counts[3]++, () -> counts[4]++,
                () -> { });
    }

    @Test
    void showsTheGhostAndItsButtonsInsteadOfBuild() {
        StudioDriver driver = new StudioDriver("es_es", 640, 360);
        driver.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        driver.view.selectStage(StudioStage.TOOLS, driver.host);
        driver.view.tools().select(ToolCatalog.byId("tree"), driver.host);
        int[] counts = new int[5];
        driver.host.ghost = ghost("Árbol roble", "giro 90° · 7×12×7", "120, 64, -40 · (+3, 0, -2)", "", counts);
        assertTrue(driver.shows("Árbol roble · giro 90°"), () -> "lo que es y su giro: " + driver.texts());
        assertTrue(driver.shows("120, 64, -40"));
        assertTrue(driver.has("Conservar"));
        assertTrue(driver.has("Aire"));
        assertTrue(driver.has("Suelo"));
        assertFalse(driver.has("Construir"), "mientras hay fantasma, el botón coloca");
        click(driver, "Colocar");
        assertEquals(1, counts[0]);
        click(driver, "Conservar");
        assertEquals(1, counts[3]);
        click(driver, "Cancelar");
        assertEquals(1, counts[2]);
        // Sin fantasma, el pie de siempre.
        driver.host.ghost = null;
        assertFalse(driver.has("Conservar"));
    }

    @Test
    void aRejectionIsShownWhereThePositionWas() {
        StudioDriver driver = new StudioDriver("es_es", 640, 360);
        driver.view.selectStage(StudioStage.TOOLS, driver.host);
        driver.host.ghost = ghost("Pegar", "giro 0° · 4×4×4", "10, 70, 10", "Demasiado lejos", new int[5]);
        assertTrue(driver.has("Demasiado lejos"));
        assertFalse(driver.shows("Pegar · giro"), "el motivo ocupa la línea de arriba");
        assertTrue(driver.shows("10, 70, 10"));
    }

    private static void click(StudioDriver driver, String label) {
        int[] point = driver.where(label);
        driver.view.mouseClicked(point[0], point[1], 0, driver.host);
        driver.view.mouseReleased(point[0], point[1], 0, driver.host);
    }
}
