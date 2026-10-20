package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.ui.StudioTheme.Token;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Elegir tema: se guarda por jugador, se pone al pintar y se cambia desde la chuleta o la paleta de órdenes. */
class StudioThemeSwitchTest {
    @AfterEach
    void restore() {
        StudioTheme.use(StudioPalette.DEFAULT);
    }

    @Test
    void theDefaultIsSlate() {
        assertEquals(StudioPalette.SLATE, StudioPalette.DEFAULT);
        assertEquals(StudioPalette.DEFAULT, StudioPalette.byId(""));
        assertEquals(StudioPalette.DEFAULT, StudioPalette.byId(null));
        assertEquals(StudioPalette.DEFAULT, StudioPalette.byId("rosa"), "un tema que ya no existe vuelve al de serie");
        assertEquals(StudioPalette.FOREST, StudioPalette.byId(" Forest "));
    }

    @Test
    void useFillsEveryToken() {
        for (StudioPalette palette : StudioPalette.values()) {
            StudioTheme.use(palette);
            assertEquals(palette, StudioTheme.current());
            assertEquals(palette.color(Token.PANEL), StudioTheme.PANEL);
            assertEquals(palette.color(Token.ACCENT), StudioTheme.ACCENT);
            assertEquals(palette.color(Token.ON_ACCENT), StudioTheme.ON_ACCENT);
            assertEquals(palette.color(Token.TEXT_STRONG), StudioTheme.TEXT_STRONG);
            assertEquals(palette.color(Token.SCRIM), StudioTheme.SCRIM);
        }
        StudioTheme.use(null);
        assertEquals(StudioPalette.DEFAULT, StudioTheme.current());
    }

    @Test
    void theThemesReallyDiffer() {
        for (StudioPalette a : StudioPalette.values()) {
            for (StudioPalette b : StudioPalette.values()) {
                if (a != b) {
                    assertNotEquals(a.color(Token.ACCENT), b.color(Token.ACCENT), a + " y " + b);
                    assertNotEquals(a.color(Token.PANEL), b.color(Token.PANEL), a + " y " + b);
                }
            }
        }
    }

    @Test
    void thePreferenceIsRemembered() {
        StudioPreferences preferences = new StudioPreferences();
        assertEquals("", preferences.theme());
        assertTrue(!preferences.toJson().contains("theme"), "el de serie no se escribe");
        preferences.setTheme("prismarine");
        StudioPreferences read = StudioPreferences.fromJson(preferences.toJson());
        assertEquals("prismarine", read.theme());
        assertEquals("", StudioPreferences.fromJson("{\"theme\": 7}").theme());
    }

    @Test
    void theViewPaintsWithThePlayersTheme() {
        StudioDriver driver = new StudioDriver("es_es", 640, 360);
        driver.host.context = StudioMockupsTest.SELECTED;
        driver.host.info = StudioMockupsTest.info();
        StudioMockupsTest.tools(driver, "tree");
        driver.host.preferences.setTheme("classic");
        driver.render();
        assertEquals(StudioPalette.CLASSIC, StudioTheme.current());
        ImageCanvas.Drawn plant = driver.canvas().texts().stream().filter(drawn -> drawn.text().equals("Plantar"))
                .findFirst().orElseThrow();
        assertEquals(StudioPalette.CLASSIC.color(Token.ON_ACCENT), plant.argb(),
                "el texto del botón principal va en el color de encima del acento");

        driver.host.preferences.setTheme("");
        driver.render();
        assertEquals(StudioPalette.DEFAULT, StudioTheme.current());
    }

    @Test
    void theCheatSheetChangesTheTheme() {
        StudioDriver driver = new StudioDriver("es_es", 640, 360);
        driver.host.context = StudioMockupsTest.SELECTED;
        driver.host.info = StudioMockupsTest.info();
        StudioMockupsTest.tools(driver, "tree");
        driver.key(StudioKey.of(StudioKey.Code.F1));
        assertTrue(driver.has("Tema"));
        int saves = driver.host.saves;
        driver.click("Bosque");
        assertEquals("forest", driver.host.preferences.theme());
        assertEquals(StudioPalette.FOREST, StudioTheme.current());
        assertTrue(driver.host.saves > saves, "se guarda al momento");
        assertEquals("Tema: Bosque", driver.view.status());
        driver.render();
        assertEquals(StudioPalette.FOREST, StudioTheme.current(), "y sigue al volver a pintar");
    }

    @Test
    void theCommandPaletteChangesTheTheme() {
        StudioDriver driver = new StudioDriver("en_us", 640, 360);
        driver.host.context = StudioMockupsTest.SELECTED;
        driver.host.info = StudioMockupsTest.info();
        StudioMockupsTest.tools(driver, "tree");
        driver.key(StudioKey.letter('K', true, false));
        driver.type("theme prismarine");
        driver.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("prismarine", driver.host.preferences.theme());
        assertEquals(StudioPalette.PRISMARINE, StudioTheme.current());
    }
}
