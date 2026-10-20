package com.komainu.moxia.studio.ui;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** La vista previa en vivo: espera a que se deje de tocar, no repite, no inunda y solo vale para obras pequeñas. */
class LivePreviewTest {
    @Test
    void waitsUntilTheSettingsStopChanging() {
        LivePreview live = new LivePreview();
        assertNull(live.tick("tree oak 19 -v", 0, false));
        assertNull(live.tick("tree oak 20 -v", 300, false), "cambió: vuelve a esperar");
        assertNull(live.tick("tree oak 20 -v", 300 + LivePreview.DELAY_MILLIS - 1, false));
        assertEquals("tree oak 20 -v", live.tick("tree oak 20 -v", 300 + LivePreview.DELAY_MILLIS, false));
        assertNull(live.tick("tree oak 20 -v", 5_000, false), "lo mismo no se vuelve a pedir");
    }

    @Test
    void neverFloodsTheServer() {
        LivePreview live = new LivePreview();
        live.tick("a", 0, false);
        assertEquals("a", live.tick("a", LivePreview.DELAY_MILLIS, false));
        long now = LivePreview.DELAY_MILLIS + 10;
        live.tick("b", now, false);
        assertNull(live.tick("b", now + LivePreview.DELAY_MILLIS, false), "menos de 1,5 s desde la anterior");
        assertNull(live.tick("b", now + LivePreview.MIN_INTERVAL_MILLIS, true), "el servidor está con otra");
        assertEquals("b", live.tick("b", now + LivePreview.MIN_INTERVAL_MILLIS, false));
    }

    @Test
    void nothingToPreviewForgetsWhatWasPending() {
        LivePreview live = new LivePreview();
        live.tick("a", 0, false);
        assertNull(live.tick(null, 100, false));
        long again = LivePreview.DELAY_MILLIS + 50;
        assertNull(live.tick("a", again, false), "vuelve a contar desde cero");
        assertNull(live.tick("a", again + LivePreview.DELAY_MILLIS - 1, false));
        assertEquals("a", live.tick("a", again + LivePreview.DELAY_MILLIS, false));
        live.reset();
        live.tick("a", 10_000, false);
        assertEquals("a", live.tick("a", 10_000 + LivePreview.DELAY_MILLIS, false), "tras reset se puede repetir");
    }

    @Test
    void inTheStudioItSendsThePreviewOfTheChosenTool() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        studio.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.LIVE);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.render();
        studio.wheelValue("Altura", 1);
        studio.advance(100);
        assertNull(studio.host.last(), "aún se está tocando");
        studio.advance(LivePreview.DELAY_MILLIS);
        assertEquals("komaedit tree oak 19 -v", studio.host.last());
        studio.advance(LivePreview.MIN_INTERVAL_MILLIS * 3);
        assertEquals(1, studio.host.sent.size(), "sin cambios no repite");

        // Mientras el servidor coloca algo, espera.
        studio.host.job = JobStatusPayload.of(new ProgressLine("Árbol", ProgressLine.Phase.PLACING, 1, 10, 1, null), 0);
        studio.wheelValue("Altura", 1);
        studio.advance(LivePreview.DELAY_MILLIS * 3);
        assertEquals(1, studio.host.sent.size());
        // Con la vista previa anterior esperando, la nueva la sustituye.
        studio.host.job = JobStatusPayload.of(new ProgressLine("Árbol", ProgressLine.Phase.WAITING, 5_000, 90_000, 0,
                null), 120);
        studio.advance(LivePreview.DELAY_MILLIS);
        assertEquals("komaedit tree oak 20 -v", studio.host.last());
    }

    @Test
    void bigSelectionsGetNoLivePreview() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 200_000L, 0, false);
        studio.host.info = new ToolsInfo(true, new int[]{100, 20, 100}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.LIVE);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("snow"), studio.host);
        studio.render();
        studio.wheelValue("Capas", 1);
        studio.advance(LivePreview.DELAY_MILLIS * 4);
        assertNull(studio.host.last(), "selección de 200 000 bloques: nada en vivo");
        studio.click("Nevar");
        assertEquals("komaedit snow 3 -v", studio.host.last(), "construir sigue pidiendo la vista previa");
    }
}
