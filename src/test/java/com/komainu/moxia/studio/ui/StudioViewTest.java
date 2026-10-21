package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.StudioShortcuts;
import com.komainu.moxia.studio.layout.StudioLayout;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El marco del Studio: navegación por grupos con su tecla, barra, historial del mundo y lo que se recuerda. */
class StudioViewTest {
    private static StudioDriver studio() {
        StudioDriver studio = new StudioDriver("es_es", 960, 540);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 4, false);
        studio.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        for (int index = 0; index < 4; index++) {
            studio.host.points.add(new int[]{index * 5, 64, 0});
        }
        return studio;
    }

    /**
     * El nombre de la etapa, arriba, se lee entero con el panel al mínimo y al máximo, en los dos idiomas: con el
     * panel estrecho a 1080p (GUI 3 o 2) salía «Herramient…».
     */
    @Test
    void theStageTitleIsWholeWithThePanelAtItsLimits() {
        java.util.List<String> cut = new java.util.ArrayList<>();
        for (String language : List.of("es_es", "en_us")) {
            for (int[] screen : new int[][]{{640, 360}, {960, 540}, {427, 240}, {480, 270}}) {
                for (double share : new double[]{0.01, 1.0}) {
                    for (StudioStage stage : StudioStage.values()) {
                        StudioDriver studio = new StudioDriver(language, screen[0], screen[1]);
                        studio.host.preferences.setPanelShare(share);
                        studio.view.selectStage(stage, studio.host);
                        String title = studio.host.tr(stage.titleKey());
                        if (!studio.has(title)) {
                            cut.add(language + " " + screen[0] + "×" + screen[1] + " " + share + " " + title);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), cut);
    }

    @Test
    void theStagesAreGroupedAndEachOneHasItsKey() {
        Set<Character> keys = new HashSet<>();
        for (StudioStage stage : StudioStage.values()) {
            assertTrue(keys.add(stage.shortcut()), "tecla repetida " + stage.shortcut());
            assertEquals(stage, StudioStage.byShortcut(stage.shortcut()));
        }
        assertEquals(7, keys.size(), "del 1 al 7: Modificadores y Material viven dentro de Figuras");
        assertEquals(List.of(3, 1, 3), Arrays.stream(StudioStage.groupSizes()).boxed().toList());
        assertEquals(StudioStage.FIGURE, StudioStage.byId("structures"), "Estructuras vive ahora en Figuras");
        assertEquals(StudioStage.FIGURE, StudioStage.byId("modifiers"), "quien lo dejó en Modificadores abre Figuras");
        assertEquals(StudioStage.FIGURE, StudioStage.byId("material"), "y en Material, también");
        assertEquals(null, StudioStage.byId("weather"), "quien lo dejó en Envejecer abre en la de serie");
        assertEquals('5', StudioStage.BLUEPRINTS.shortcut());
        StudioDriver studio = studio();
        for (String group : List.of("CREAR", "AJUSTAR", "BIBLIOTECA")) {
            assertTrue(studio.has(group), group);
        }
        for (StudioStage stage : StudioStage.values()) {
            studio.key(StudioKey.digit(stage.shortcut()));
            assertEquals(stage, studio.view.stage(), "tecla " + stage.shortcut());
        }
    }

    @Test
    void theNavigationShowsNameAndKeyAndTheToolbarTheStage() {
        StudioDriver studio = studio();
        studio.render();
        assertEquals(StudioLayout.NavMode.LABELS, studio.view.frame().navMode());
        studio.click("Planos");
        assertEquals(StudioStage.BLUEPRINTS, studio.view.stage());
        assertTrue(studio.has("7"), "la tecla de cada etapa se ve a su lado");
        studio.key(StudioKey.of(StudioKey.Code.TAB));
        assertEquals(StudioStage.RECIPES, studio.view.stage());
        studio.key(new StudioKey(StudioKey.Code.TAB, '\0', false, true));
        assertEquals(StudioStage.BLUEPRINTS, studio.view.stage());
    }

    @Test
    void kClosesAndEscapeFirstClosesWhatIsOpen() {
        StudioDriver studio = studio();
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("roof"), studio.host);
        studio.click("Poner tejado");
        assertTrue(studio.view.tools().browsing());
        assertTrue(studio.key(StudioKey.of(StudioKey.Code.ESCAPE)), "Esc cierra la cuadrícula");
        assertFalse(studio.view.tools().browsing());
        assertFalse(studio.key(StudioKey.of(StudioKey.Code.ESCAPE)), "otro Esc ya es de la pantalla (cerrar)");
        studio.key(StudioKey.letter('K', false, false));
        assertTrue(studio.host.closed);
    }

    @Test
    void thePathLeadsStraightToTheToolsThatUseIt() {
        StudioDriver studio = studio();
        studio.view.selectStage(StudioStage.PATH, studio.host);
        assertTrue(studio.has("USAR CON"));
        studio.click("Excavar río");
        assertEquals(StudioStage.TOOLS, studio.view.stage());
        assertEquals("river", studio.view.tools().selected().id());
        studio.click("Tallar cauce");
        assertEquals("komaedit terreno rio natural -v", studio.host.last());
    }

    @Test
    void comingFromAToolThePathFooterBuildsThatTool() {
        StudioDriver studio = studio();
        studio.host.points.clear();
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0, false);
        studio.host.info = ToolsInfo.EMPTY;
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("river"), studio.host);
        studio.click("Marcar puntos");
        assertEquals(StudioStage.PATH, studio.view.stage());
        assertTrue(studio.shows("Excavar río · "), "el pie sigue siendo el del río");
        for (int index = 0; index < 3; index++) {
            studio.host.points.add(new int[]{index, 64, 0});
        }
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 3, false);
        studio.click("Tallar cauce");
        assertEquals("komaedit terreno rio natural -v", studio.host.last());
    }

    @Test
    void theWorldHistoryUndoesAndRedoesUpToARow() {
        StudioDriver studio = studio();
        studio.host.history = new StudioHost.WorldHistory(List.of(
                new StudioHost.WorldHistory.Entry("Tejado", 1_204),
                new StudioHost.WorldHistory.Entry("Árbol", 412),
                new StudioHost.WorldHistory.Entry("Río o cañón", 9_771)), 1);
        studio.click("Cambios en el mundo: deshacer o rehacer hasta un punto (Ctrl+Z, Ctrl+Y)");
        assertTrue(studio.view.historyOpen());
        assertTrue(studio.has("1 204 bloques"));
        studio.click("Río o cañón");
        assertEquals(2, studio.host.undos, "deshace Árbol y Río para dejar el Río como lo último hecho");
        studio.click("Tejado");
        assertEquals(1, studio.host.redos, "rehace el tejado deshecho");
        assertEquals(StudioView.Click.HANDLED, studio.clickAt(10, 10));
        assertFalse(studio.view.historyOpen(), "un clic fuera lo cierra");
        assertTrue(studio.host.requests > 0, "pide el historial al abrirlo");
    }

    @Test
    void historyStepsAreCountedFromTheCurrentPoint() {
        StudioHost.WorldHistory history = new StudioHost.WorldHistory(List.of(
                new StudioHost.WorldHistory.Entry("a", 1), new StudioHost.WorldHistory.Entry("b", 1),
                new StudioHost.WorldHistory.Entry("c", 1), new StudioHost.WorldHistory.Entry("d", 1)), 2);
        assertEquals(List.of(1, 0), List.of(StudioView.steps(history, 2)[0], StudioView.steps(history, 2)[1]));
        assertEquals(List.of(2, 0), List.of(StudioView.steps(history, 3)[0], StudioView.steps(history, 3)[1]));
        assertEquals(List.of(0, 2), List.of(StudioView.steps(history, 0)[0], StudioView.steps(history, 0)[1]));
    }

    @Test
    void controlZUndoesTheWorldAndZTheSettings() {
        StudioDriver studio = studio();
        studio.key(StudioKey.letter('Z', true, false));
        studio.key(StudioKey.letter('Y', true, false));
        assertEquals(1, studio.host.undos);
        assertEquals(1, studio.host.redos);
    }

    @Test
    void theStageAndTheScrollOfEachToolAreRemembered() {
        StudioDriver studio = new StudioDriver("es_es", 427, 240);
        studio.host.preferences.setExpanded("tools:more:roof", true);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("roof"), studio.host);
        studio.render();
        studio.view.mouseScrolled(studio.view.frame().innerLeft() + 2, 120, -1, studio.host);
        studio.view.mouseScrolled(studio.view.frame().innerLeft() + 2, 120, -1, studio.host);
        int scrolled = studio.view.scroll();
        assertTrue(scrolled > 0);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.render();
        assertEquals(0, studio.view.scroll(), "otra herramienta, su propio desplazamiento");
        studio.view.tools().select(ToolCatalog.byId("roof"), studio.host);
        studio.render();
        assertEquals(scrolled, studio.view.scroll(), "volver la deja donde estaba");
        assertEquals("tools", studio.host.preferences.stage());
    }

    @Test
    void theCheatSheetListsThePalette() {
        StudioDriver studio = studio();
        studio.key(StudioKey.of(StudioKey.Code.F1));
        assertTrue(studio.view.cheatSheet());
        assertTrue(studio.has(studio.host.tr("gui.komaedit.shortcut.palette.keys")));
        assertTrue(StudioShortcuts.ALL.stream().anyMatch(shortcut -> shortcut.id().equals("palette")));
        studio.key(StudioKey.of(StudioKey.Code.F1));
        assertFalse(studio.view.cheatSheet());
    }

    @Test
    void withoutAChosenToolTheFooterAsksForOne() {
        StudioDriver studio = studio();
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        assertTrue(studio.has("Elige una herramienta"));
        studio.click("Elige una herramienta");
        assertTrue(studio.host.sent.isEmpty());
    }

    @Test
    void withIconsTheNamesShowUpWhenTheMouseIsOverTheNavigation() {
        StudioDriver studio = new StudioDriver("es_es", 640, 360);
        studio.render();
        assertEquals(StudioLayout.NavMode.ICONS, studio.view.frame().navMode());
        assertFalse(studio.has("Trayectoria"), "sin el ratón encima, solo iconos");
        com.komainu.moxia.studio.layout.Rect icon = studio.view.frame().navItem(StudioStage.COLOR.ordinal());
        studio.mouseX = icon.centerX();
        studio.mouseY = icon.centerY();
        for (StudioStage stage : StudioStage.values()) {
            assertTrue(studio.has(studio.host.tr(stage.titleKey())), stage.id());
        }
        assertTrue(studio.view.navFlyout());
        studio.click("Planos");
        assertEquals(StudioStage.BLUEPRINTS, studio.view.stage(), "pulsar un nombre abre la etapa");
        studio.mouseX = 100;
        studio.mouseY = 100;
        studio.render();
        assertFalse(studio.view.navFlyout(), "fuera del panel, la lista se va");
    }

    @Test
    void theChosenBlueprintCardHoldsItsButtonsAndTheNamesAreWhole() {
        for (int[] screen : new int[][]{{640, 360}, {427, 240}, {480, 270}, {960, 540}}) {
            StudioDriver studio = new StudioDriver("es_es", screen[0], screen[1]);
            StudioMockupsTest.blueprints(studio);
            studio.render();
            String where = screen[0] + "×" + screen[1];
            int library = studio.canvas().texts().stream().filter(text -> text.text().startsWith("BIBLIOTECA"))
                    .mapToInt(ImageCanvas.Drawn::y).findFirst().orElseThrow();
            for (StudioUi.Widget widget : studio.ui().widgets()) {
                String first = widget.tooltip().split("\n", 2)[0];
                if (first.equals("Cargar") || first.equals("Estampar") || first.startsWith("Radio")) {
                    assertTrue(widget.bounds().bottom() < library, where + " · «" + first + "» se sale de la tarjeta");
                }
            }
            List<String> texts = studio.texts();
            assertFalse(texts.stream().anyMatch(text -> text.startsWith("roca_mu") && text.endsWith("…")),
                    where + ": " + texts);
            assertFalse(texts.stream().anyMatch(text -> text.startsWith("ruina_pa") && text.endsWith("…")),
                    where + ": " + texts);
        }
    }

    @Test
    void thePanelEdgeDragsSavesItsShareAndRightClickRestoresTheDefault() {
        StudioDriver studio = new StudioDriver("es_es", 640, 360);
        studio.render();
        assertEquals(243, studio.view.frame().panel().width(), "de serie, el 38 %");
        int edge = studio.view.frame().panel().x();
        studio.view.mouseClicked(edge, 180, 0, studio.host);
        studio.view.mouseDragged(640 - 280, 180, 0, studio.host);
        studio.view.mouseReleased(640 - 280, 180, 0, studio.host);
        studio.render();
        assertEquals(280, studio.view.frame().panel().width());
        assertEquals(280 / 640.0, studio.host.preferences().panelShare(), 1e-9, "se guarda como parte de la pantalla");
        assertTrue(studio.host.saves > 0);
        // Con otra escala de GUI se queda la misma parte de la pantalla.
        StudioDriver bigger = new StudioDriver(new StudioView(), studio.host, 960, 540);
        bigger.render();
        assertEquals(420, bigger.view.frame().panel().width());
        studio.view.mouseClicked(studio.view.frame().panel().x(), 180, 0, studio.host);
        studio.view.mouseDragged(10, 180, 0, studio.host);
        studio.view.mouseReleased(10, 180, 0, studio.host);
        studio.render();
        assertEquals(StudioLayout.maximumPanelWidth(640), studio.view.frame().panel().width(), "no pasa del máximo");
        studio.view.mouseClicked(studio.view.frame().panel().x(), 180, 1, studio.host);
        studio.render();
        assertEquals(243, studio.view.frame().panel().width(), "clic derecho en el borde: el de serie");
        assertEquals(0.0, studio.host.preferences().panelShare());
    }
}
