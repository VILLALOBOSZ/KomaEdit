package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.studio.layout.StudioLayout;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ToolParam;
import com.komainu.moxia.studio.ui.ChoiceThumbnails;
import com.komainu.moxia.studio.ui.FigureStages;
import com.komainu.moxia.studio.ui.ImageCanvas;
import com.komainu.moxia.studio.ui.StudioDriver;
import com.komainu.moxia.studio.ui.StudioStage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bajo cada miniatura se lee el nombre entero, sin «…», en las pantallas habituales y en los dos idiomas: la
 * galería pone las columnas que hagan falta para que quepa en dos líneas como mucho.
 */
class GalleryNamesTest {
    /** 1080p con GUI 2, 3 y 4, 1440p con GUI 3 y 854×480 con GUI 2 (en píxeles de GUI). */
    private static final int[][] SCREENS = {{960, 540}, {640, 360}, {480, 270}, {853, 480}, {427, 240}};

    @BeforeAll
    static void thumbnails() {
        ChoiceThumbnails.setBlocking(true);
    }

    private static List<ToolParam.Choice> galleries(ToolCard card) {
        List<ToolParam.Choice> galleries = new ArrayList<>();
        for (ToolParam param : card.params()) {
            if (param instanceof ToolParam.Choice choice && choice.style() == ToolParam.Choice.Style.GALLERY) {
                galleries.add(choice);
            }
        }
        return galleries;
    }

    @Test
    void everyNameFitsWholeAtEveryUsualScreen() {
        int checked = 0;
        for (String language : List.of("es_es", "en_us")) {
            for (int[] screen : SCREENS) {
                StudioDriver studio = new StudioDriver(language, screen[0], screen[1]);
                studio.render();
                int width = StudioLayout.compute(screen[0], screen[1], StudioLayout.AUTO, StudioStage.groupSizes())
                        .innerWidth();
                for (ToolCard card : ToolCatalog.ALL) {
                    for (ToolParam.Choice choice : galleries(card)) {
                        List<String> labels = choice.options().stream()
                                .map(option -> studio.host.tr(choice.optionKey(option))).toList();
                        ToolsPanel.GalleryLayout layout = ToolsPanel.galleryLayout(studio.ui(), labels, width);
                        for (int index = 0; index < labels.size(); index++) {
                            List<String> lines = layout.names().get(index);
                            String where = language + " " + screen[0] + "×" + screen[1] + " · " + card.id() + ": "
                                    + labels.get(index) + " → " + lines;
                            assertEquals(labels.get(index), String.join(" ", lines), where);
                            assertTrue(lines.size() <= 2, where);
                            for (String line : lines) {
                                assertTrue(ImageCanvas.font().width(line) <= layout.tile() - 4, where);
                            }
                            checked++;
                        }
                        assertTrue(layout.perRow() >= 3, language + " " + screen[0] + "×" + screen[1] + " · "
                                + card.id() + ": " + layout.perRow() + " columnas");
                    }
                }
            }
        }
        assertTrue(checked > 300, "se miraron " + checked + " nombres");
    }

    @Test
    void toolAndFigureNamesAreNotCutInTheirGrids() {
        for (String language : List.of("es_es", "en_us")) {
            for (int[] screen : SCREENS) {
                StudioDriver studio = new StudioDriver(language, screen[0], screen[1]);
                List<String> names = new ArrayList<>();
                ToolCatalog.ALL.forEach(card -> names.add(studio.host.tr(card.nameKey())));
                for (PathShape shape : PathShape.values()) {
                    names.add(FigureStages.shapeName(studio.host, shape));
                }
                studio.view.selectStage(StudioStage.TOOLS, studio.host);
                List<String> texts = new ArrayList<>(studio.texts());
                studio.view.selectStage(StudioStage.FIGURE, studio.host);
                studio.view.figure().setBrowsing(true);
                // Toda la cuadrícula de figuras, desplazando de arriba abajo.
                for (int step = 0; step < 12; step++) {
                    texts.addAll(studio.texts());
                    studio.view.mouseScrolled(screen[0] - 40, screen[1] / 2.0, -3, studio.host);
                }
                for (String text : texts) {
                    if (text.endsWith("…") && !text.substring(0, text.length() - 1).isBlank()) {
                        String start = text.substring(0, text.length() - 1);
                        assertFalse(names.stream().anyMatch(name -> name.startsWith(start) && !name.equals(start)),
                                language + " " + screen[0] + "×" + screen[1] + ": «" + text + "» cortado");
                    }
                }
            }
        }
    }

    @Test
    void theRoofTypesReadWholeAt1080pWithGui3() {
        StudioDriver studio = new StudioDriver("es_es", 640, 360);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        studio.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("roof"), studio.host);
        List<String> texts = studio.texts();
        assertTrue(texts.contains("Cuatro") && texts.contains("aguas"), texts.toString());
        assertTrue(texts.contains("Mansarda") && texts.contains("Holandés"), texts.toString());
        assertFalse(texts.stream().anyMatch(text -> text.endsWith("…") && "Cuatro aguas".startsWith(
                text.substring(0, text.length() - 1))), texts.toString());
        studio.click("Mansarda");
        assertEquals("mansard", studio.view.tools().values(ToolCatalog.byId("roof")).choice("type"));
    }
}
