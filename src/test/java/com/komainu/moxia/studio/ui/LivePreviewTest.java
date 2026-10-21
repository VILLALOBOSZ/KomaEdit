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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La vista previa en vivo: encendida de serie, rehace la vista previa que se pidió al construir en cuanto se deja de
 * tocar un ajuste, no repite, no inunda, no abre ninguna sola y solo vale para obras pequeñas.
 */
class LivePreviewTest {
    @Test
    void waitsUntilTheSettingsStopChanging() {
        LivePreview live = new LivePreview();
        assertNull(live.tick("tool:tree", "tree oak 19 -v", 0, false, true), "sin Construir antes, nada");
        live.arm("tool:tree", "tree oak -v", 0);
        assertNull(live.tick("tool:tree", "tree oak 19 -v", 1_000, false, true));
        assertNull(live.tick("tool:tree", "tree oak 20 -v", 1_100, false, true), "cambió: vuelve a esperar");
        assertNull(live.tick("tool:tree", "tree oak 20 -v", 1_100 + LivePreview.CHAT_DELAY_MILLIS - 1, false, true));
        assertEquals("tree oak 20 -v", live.tick("tool:tree", "tree oak 20 -v", 1_100 + LivePreview.CHAT_DELAY_MILLIS,
                false, true));
        assertNull(live.tick("tool:tree", "tree oak 20 -v", 5_000, false, true), "lo mismo no se vuelve a pedir");
    }

    @Test
    void neverFloodsTheServer() {
        LivePreview live = new LivePreview();
        live.arm("tool:tree", "a", 0);
        live.tick("tool:tree", "b", 0, false, true);
        assertNull(live.tick("tool:tree", "b", LivePreview.CHAT_DELAY_MILLIS, false, true),
                "menos de " + LivePreview.MIN_INTERVAL_MILLIS + " ms desde Construir");
        assertNull(live.tick("tool:tree", "b", LivePreview.MIN_INTERVAL_MILLIS, true, true), "el servidor calcula");
        assertEquals("b", live.tick("tool:tree", "b", LivePreview.MIN_INTERVAL_MILLIS, false, true));
    }

    @Test
    void onlyTheObjectThatWasBuiltIsRedone() {
        LivePreview live = new LivePreview();
        live.arm("tool:tree", "tree oak -v", 0);
        assertNull(live.tick("tool:rock", "rock boulder -v", 2_000, false, true), "otra herramienta no la sustituye");
        assertNull(live.tick("tool:rock", "rock boulder -v", 4_000, false, true));
        assertNull(live.tick("tool:tree", "tree oak 20 -v", 4_000, false, true), "de vuelta al árbol: espera");
        assertEquals("tree oak 20 -v", live.tick("tool:tree", "tree oak 20 -v", 4_000 + LivePreview.CHAT_DELAY_MILLIS,
                false, true));
    }

    @Test
    void placedOrDiscardedEndsIt() {
        LivePreview live = new LivePreview();
        live.arm("tool:tree", "tree oak -v", 0);
        assertNull(live.tick("tool:tree", "tree oak 20 -v", 1_000, false, false), "aún llegando: espera");
        assertEquals("tool:tree", live.owner());
        assertNull(live.tick("tool:tree", "tree oak 20 -v", LivePreview.GRACE_MILLIS, false, false));
        assertNull(live.owner(), "colocada o quitada: se acabó hasta el próximo Construir");
        assertNull(live.tick("tool:tree", "tree oak 21 -v", 10_000, false, true));
    }

    @Test
    void inTheStudioBuildingArmsItAndChangesRedoThePreview() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        studio.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        assertEquals(StudioPreferences.PreviewMode.LIVE, studio.host.preferences.previewMode(), "de serie");
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.render();
        studio.wheelValue("Altura", 1);
        studio.advance(LivePreview.CHAT_DELAY_MILLIS * 4);
        assertNull(studio.host.last(), "sin Construir no sale ninguna vista previa sola");

        studio.click("Plantar");
        assertEquals("komaedit tree oak 19 -v", studio.host.last());
        assertEquals(List.of(false), studio.host.expectedGhosts);
        // Sale el fantasma del árbol: cada cambio lo rehace.
        studio.host.ghost = ghost(true);
        studio.wheelValue("Altura", 1);
        studio.advance(100);
        assertEquals(1, studio.host.sent.size(), "aún se está tocando");
        studio.advance(LivePreview.MIN_INTERVAL_MILLIS);
        assertEquals("komaedit tree oak 20 -v", studio.host.last());
        assertEquals(List.of(false, true), studio.host.expectedGhosts, "el nuevo sale donde se dejó el de antes");
        studio.advance(LivePreview.MIN_INTERVAL_MILLIS * 3);
        assertEquals(2, studio.host.sent.size(), "sin cambios no repite");

        // Mientras el servidor calcula, espera.
        studio.host.job = JobStatusPayload.of(new ProgressLine("Árbol", ProgressLine.Phase.PREPARING, 1, 10, 1, null),
                0);
        studio.wheelValue("Grosor", -1);
        studio.advance(LivePreview.CHAT_DELAY_MILLIS * 3);
        assertEquals(2, studio.host.sent.size());
        studio.host.job = null;
        studio.advance(LivePreview.CHAT_DELAY_MILLIS);
        assertEquals("komaedit tree oak 20 -g 0.95 -v", studio.host.last());

        // Colocado: el fantasma se va y la vista en vivo se olvida.
        studio.host.ghost = null;
        studio.advance(LivePreview.GRACE_MILLIS);
        studio.wheelValue("Altura", 1);
        studio.advance(LivePreview.GRACE_MILLIS);
        assertEquals(3, studio.host.sent.size(), "no vuelve a abrir nada");
    }

    @Test
    void aGhostThatDidNotComeFromTheStudioIsLeftAlone() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.render();
        studio.click("Plantar");
        studio.host.ghost = ghost(false);
        studio.wheelValue("Altura", 1);
        studio.advance(LivePreview.GRACE_MILLIS * 2);
        assertEquals(1, studio.host.sent.size(), "un //paste no se cambia por el árbol");
    }

    @Test
    void theFigureGhostFollowsTheFigure() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 2, false);
        studio.host.points = new java.util.ArrayList<>(List.of(new int[]{0, 64, 0}, new int[]{10, 64, 0}));
        studio.host.previewBlocks = 900;
        studio.view.selectStage(StudioStage.FIGURE, studio.host);
        studio.render();
        studio.click("Construir");
        assertEquals(1, studio.host.recipeActions.size());
        studio.host.ghost = ghost(true);
        widen(studio);
        studio.advance(100);
        studio.advance(LivePreview.MIN_INTERVAL_MILLIS);
        assertEquals(2, studio.host.recipeActions.size(), "la figura nueva sustituye al fantasma");
        assertTrue(studio.host.expectedGhosts.getLast());

        // Grande: no se rehace sola.
        studio.host.previewBlocks = 300_000;
        widen(studio);
        studio.advance(LivePreview.MIN_INTERVAL_MILLIS * 3);
        assertEquals(2, studio.host.recipeActions.size());
    }

    @Test
    void aFigureWithoutItsGhostIsNeverBuiltAgain() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 2, false);
        studio.host.points = new java.util.ArrayList<>(List.of(new int[]{0, 64, 0}, new int[]{10, 64, 0}));
        studio.host.previewBlocks = 900;
        studio.view.selectStage(StudioStage.FIGURE, studio.host);
        studio.render();
        studio.click("Construir");
        // Sin fantasma (con el gizmo apagado la figura ya se colocó) y con otra vista previa esperando.
        studio.host.job = JobStatusPayload.of(new ProgressLine("Nieve", ProgressLine.Phase.WAITING, 5_000, 90_000, 0,
                null), 120);
        widen(studio);
        studio.advance(100);
        studio.advance(LivePreview.MIN_INTERVAL_MILLIS * 3);
        assertEquals(1, studio.host.recipeActions.size(), "no se construye otra vez");
    }

    private static void widen(StudioDriver studio) {
        com.komainu.moxia.recipe.ProceduralRecipe draft = studio.view.figure().draft();
        studio.view.figure().setDraft(draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius() + 1,
                draft.spacing(), draft.thickness(), draft.rollDegrees(), draft.height(), draft.paletteId(),
                draft.gradientAxis(), draft.dither(), draft.reverse()));
    }

    @Test
    void bigSelectionsPauseTheLivePreviewAndSaySo() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 300_000L, 0, false);
        studio.host.info = new ToolsInfo(true, new int[]{100, 30, 100}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("snow"), studio.host);
        studio.render();
        studio.click("Nevar");
        assertEquals("komaedit retocar nieve 2 -v", studio.host.last(), "construir sigue pidiendo la vista previa");
        studio.host.job = JobStatusPayload.of(new ProgressLine("Nieve", ProgressLine.Phase.WAITING, 5_000, 90_000, 0,
                null), 120);
        studio.wheelValue("Capas", 1);
        for (int step = 0; step < 8; step++) {
            studio.advance(LivePreview.CHAT_DELAY_MILLIS);
        }
        assertEquals(1, studio.host.sent.size(), "selección de 300 000 bloques (límite de serie 250 000): nada");
        studio.host.job = null;
        studio.render();
        assertTrue(studio.texts().stream().anyMatch(text -> text.contains("En vivo en pausa")),
                "el pie dice por qué no se rehace: " + studio.texts());

        // Con el límite más alto, la misma selección sí se rehace.
        studio.host.preferences.setLiveLimit(1_000_000L);
        studio.host.job = JobStatusPayload.of(new ProgressLine("Nieve", ProgressLine.Phase.WAITING, 5_000, 90_000, 0,
                null), 120);
        studio.advance(LivePreview.CHAT_DELAY_MILLIS);
        studio.advance(LivePreview.CHAT_DELAY_MILLIS);
        assertEquals("komaedit retocar nieve 3 -v", studio.host.last());
    }

    @Test
    void mediumSelectionsAreLiveByDefault() {
        StudioDriver studio = new StudioDriver("es_es", 960, 900);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 200_000L, 0, false);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("snow"), studio.host);
        studio.render();
        studio.click("Nevar");
        studio.host.job = JobStatusPayload.of(new ProgressLine("Nieve", ProgressLine.Phase.WAITING, 5_000, 90_000, 0,
                null), 120);
        studio.wheelValue("Capas", 1);
        studio.advance(LivePreview.CHAT_DELAY_MILLIS);
        studio.advance(LivePreview.MIN_INTERVAL_MILLIS);
        assertEquals("komaedit retocar nieve 3 -v", studio.host.last(),
                "antes, con más de 64 000 bloques se apagaba sin decir nada");
    }

    private static StudioHost.Ghost ghost(boolean fromStudio) {
        return new StudioHost.Ghost("Roble", "giro 0° · 7×18×7", "0, 64, 0", false, false, false, false, false, "",
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, fromStudio);
    }
}
