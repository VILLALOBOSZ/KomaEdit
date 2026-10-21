package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El ojo del pie (sin vista previa, al construir, en vivo): que se vea en qué está también con un fantasma o una
 * vista previa a la vista (antes el pie del fantasma lo tapaba y en Figuras no salía) y que se recuerde.
 */
class LivePreviewEyeTest {
    @Test
    void theEyeSaysWhatItDoesAndIsRemembered() {
        StudioDriver studio = tree();
        String live = studio.widget("Vista previa en vivo").tooltip();
        assertTrue(live.contains("250 k"), "dice hasta qué tamaño: " + live);
        int saves = studio.host.saves;
        studio.click("Vista previa en vivo");
        assertEquals(StudioPreferences.PreviewMode.OFF, studio.host.preferences.previewMode());
        assertTrue(studio.host.saves > saves, "se guarda al cambiarlo");
        assertEquals(StudioPreferences.PreviewMode.OFF,
                StudioPreferences.fromJson(studio.host.preferences.toJson()).previewMode(),
                "y sigue así al volver a abrir el juego");
        studio.click("Sin vista previa");
        studio.click("Vista previa al construir");
        assertEquals(StudioPreferences.PreviewMode.LIVE,
                StudioPreferences.fromJson(studio.host.preferences.toJson()).previewMode());

        studio.host.preferences.setLiveLimit(1_000_000L);
        StudioPreferences again = StudioPreferences.fromJson(studio.host.preferences.toJson());
        assertEquals(1_000_000L, again.liveLimit(), "el límite también se recuerda");
        again.setLiveLimit(StudioPreferences.DEFAULT_LIVE_LIMIT);
        assertFalse(again.toJson().contains("liveLimit"), "el de serie no se escribe");
    }

    @Test
    void withTheGhostOutTheEyeStillSaysAndSwitchesLive() {
        StudioDriver studio = tree();
        studio.click("Plantar");
        studio.host.ghost = new StudioHost.Ghost("Roble", "giro 0° · 7×18×7", "0, 64, 0", false, false, false, false,
                false, "", () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, true);
        String badge = studio.widget("Vista previa en vivo").tooltip();
        assertTrue(badge.contains("Siguiendo lo que acabas de construir"), badge);
        studio.click("Vista previa en vivo");
        assertEquals(StudioPreferences.PreviewMode.BUILD, studio.host.preferences.previewMode(),
                "con el fantasma fuera, un clic apaga el en vivo");
        studio.click("Vista previa al construir");
        assertEquals(StudioPreferences.PreviewMode.LIVE, studio.host.preferences.previewMode());
    }

    @Test
    void theFiguresFooterHasTheEyeToo() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 2, false);
        studio.host.points = new ArrayList<>(List.of(new int[]{0, 64, 0}, new int[]{10, 64, 0}));
        studio.host.previewBlocks = 900;
        studio.view.selectStage(StudioStage.FIGURE, studio.host);
        studio.click("Vista previa en vivo");
        assertEquals(StudioPreferences.PreviewMode.OFF, studio.host.preferences.previewMode());
    }

    private static StudioDriver tree() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.render();
        return studio;
    }
}
