package com.komainu.moxia.studio.ui;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El pie siempre cabe: sobra antes un dato entero que media palabra («Tejado a…»). */
class FooterTextTest {
    private static String fit(List<String> segments, int width) {
        return FooterText.fit(text -> text.length() * 6, segments, width, (text, room) -> text.substring(0,
                Math.max(0, room / 6 - 1)) + "…");
    }

    @Test
    void dropsTheLeastImportantPiecesFirst() {
        List<String> segments = List.of("3 412 cambios", "caduca en 1 min 14 s", "Tejado a dos aguas");
        assertEquals("3 412 cambios · caduca en 1 min 14 s · Tejado a dos aguas", fit(segments, 1_000));
        assertEquals("3 412 cambios · caduca en 1 min 14 s", fit(segments, 6 * 38));
        assertEquals("3 412 cambios", fit(segments, 6 * 20));
    }

    @Test
    void onlyTheFirstPieceIsEverCut() {
        assertEquals("Colocand…", fit(List.of("Colocando 45 %", "~8 s"), 6 * 9));
        assertEquals("", fit(List.of("", " "), 100));
    }

    @Test
    void theRealFooterNeverCutsAWordAtAnySizeOrLanguage() {
        for (String language : List.of("es_es", "en_us")) {
            for (int[] screen : new int[][]{{427, 240}, {480, 270}, {640, 360}, {960, 540}}) {
                for (ToolCard card : ToolCatalog.ALL) {
                    StudioDriver studio = new StudioDriver(language, screen[0], screen[1]);
                    studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 3, false);
                    studio.host.info = new ToolsInfo(true, new int[]{120, 64, 140}, List.of(), Map.of(), Map.of(),
                            ToolsInfo.Symmetry.OFF, null, List.of(), null);
                    studio.host.job = JobStatusPayload.of(new ProgressLine("Tejado a cuatro aguas con buhardillas",
                            ProgressLine.Phase.WAITING, 74_000, 90_000, 0, null), 123_412);
                    studio.view.selectStage(StudioStage.TOOLS, studio.host);
                    studio.view.tools().select(card, studio.host);
                    studio.render();
                    Rect footer = studio.view.frame().footer();
                    for (ImageCanvas.Drawn drawn : studio.canvas().texts()) {
                        if (footer.contains(drawn.x(), drawn.y() + 4)) {
                            assertFalse(drawn.text().contains("…"), language + " " + screen[0] + "×" + screen[1]
                                    + " · " + card.id() + ": «" + drawn.text() + "»");
                            assertTrue(drawn.x() + ImageCanvas.font().width(drawn.text()) <= footer.right(),
                                    "dentro del pie: " + drawn.text());
                        }
                    }
                }
            }
        }
    }
}
