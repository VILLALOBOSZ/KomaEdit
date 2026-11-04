package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.StudioPreferences;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo primero que lee quien abre el Studio y pone un punto: «Falta 1 punto», no «Faltan 1 puntos»; y con dos, en
 * plural. Igual en inglés.
 */
class PluralTextsTest {
    @Test
    void oneMissingPointIsSingular() {
        StudioDriver studio = new StudioDriver("es_es", 640, 360);
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.LIVE);
        studio.host.points.add(new int[]{0, 64, 0});
        studio.view.selectStage(StudioStage.FIGURE, studio.host);
        assertTrue(studio.shows("Falta 1 punto: ponlo"), studio.texts().toString());
        assertTrue(studio.shows("· 1 punto"), "el pie también: " + studio.texts());
        assertEquals("Construir · falta 1 punto", studio.view.figure().buildLabel(studio.host, 0L));

        StudioDriver none = new StudioDriver("es_es", 640, 360);
        none.view.selectStage(StudioStage.FIGURE, none.host);
        assertTrue(none.shows("Faltan 2 puntos: ponlos"), none.texts().toString());
        assertEquals("Construir · faltan 2 puntos", none.view.figure().buildLabel(none.host, 0L));

        StudioDriver english = new StudioDriver("en_us", 640, 360);
        english.host.points.add(new int[]{0, 64, 0});
        english.view.selectStage(StudioStage.FIGURE, english.host);
        assertEquals("Build · 1 more point needed", english.view.figure().buildLabel(english.host, 0L));
    }
}
