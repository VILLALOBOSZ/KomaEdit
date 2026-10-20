package com.komainu.moxia.studio.layout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioLayoutTest {
    private static final int STAGES = 10;
    private static final int[][] WINDOWS = {
            {1920, 1080}, {2560, 1440}, {1366, 768}, {1280, 720}, {1600, 900}, {854, 480}, {3840, 2160}
    };

    /** Las escalas de GUI que Minecraft permite: la pantalla escalada nunca baja de 320×240. */
    private static List<int[]> scaledScreens() {
        List<int[]> screens = new ArrayList<>();
        for (int[] window : WINDOWS) {
            for (int scale = 1; scale <= 6; scale++) {
                int width = window[0] / scale;
                int height = window[1] / scale;
                if (width >= 320 && height >= 240) {
                    screens.add(new int[]{width, height, scale});
                }
            }
        }
        return screens;
    }

    @Test
    void everyRegionStaysInsideItsParentAtEveryResolutionAndScale() {
        for (int[] screen : scaledScreens()) {
            for (int preferred : new int[]{0, StudioLayout.DEFAULT_PANEL_WIDTH, 10_000}) {
                StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], preferred, STAGES);
                String where = screen[0] + "×" + screen[1] + " (GUI " + screen[2] + ", panel " + preferred + ")";
                assertTrue(frame.screen().encloses(frame.panel()), where);
                assertEquals(screen[1], frame.panel().height(), "el panel ocupa todo el alto · " + where);
                for (Rect part : List.of(frame.toolbar(), frame.nav(), frame.content(), frame.footer())) {
                    assertTrue(frame.panel().encloses(part), where + " · " + part);
                }
                assertTrue(frame.content().encloses(frame.header()), where);
                assertTrue(frame.content().encloses(frame.viewport()), where);
                assertTrue(frame.footer().encloses(frame.buildButton()), where);
                assertTrue(frame.footer().encloses(frame.footerSummary()), where);
                assertTrue(frame.footer().encloses(frame.footerStatus()), where);
                assertFalse(frame.footerStatus().intersects(frame.buildButton()), where);
                assertFalse(frame.footerSummary().intersects(frame.footerStatus()), where);
                assertEquals(frame.panel().x(), frame.world().right(), where);
            }
        }
    }

    @Test
    void panelPartsNeverOverlap() {
        for (int[] screen : scaledScreens()) {
            StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], StudioLayout.DEFAULT_PANEL_WIDTH,
                    STAGES);
            List<Rect> parts = List.of(frame.toolbar(), frame.nav(), frame.content(), frame.footer());
            for (int first = 0; first < parts.size(); first++) {
                for (int second = first + 1; second < parts.size(); second++) {
                    assertFalse(parts.get(first).intersects(parts.get(second)),
                            screen[0] + "×" + screen[1] + " · " + parts.get(first) + " / " + parts.get(second));
                }
            }
        }
    }

    @Test
    void everyStageFitsInTheNavigationWithoutScrolling() {
        for (int[] screen : scaledScreens()) {
            StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], StudioLayout.DEFAULT_PANEL_WIDTH,
                    STAGES);
            for (int index = 0; index < STAGES; index++) {
                Rect item = frame.navItem(index);
                assertTrue(frame.nav().encloses(item), screen[0] + "×" + screen[1] + " · etapa " + index);
                assertTrue(item.height() >= StudioLayout.MIN_NAV_ROW);
                if (index > 0) {
                    assertFalse(item.intersects(frame.navItem(index - 1)));
                }
            }
        }
    }

    @Test
    void toolbarButtonsFitNextToTheTitle() {
        for (int[] screen : scaledScreens()) {
            StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], 0, STAGES);
            for (int index = 0; index < 5; index++) {
                Rect button = frame.toolButton(index);
                assertTrue(frame.toolbar().encloses(button), screen[0] + "×" + screen[1] + " · botón " + index);
            }
            // Queda sitio para «KomaEdit» (unos 50 px) a la izquierda del último botón.
            assertTrue(frame.toolButton(4).x() - frame.toolbar().x() >= 60, screen[0] + "×" + screen[1]);
        }
    }

    @Test
    void contentKeepsAUsableWidthAndHeight() {
        for (int[] screen : scaledScreens()) {
            StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], 0, STAGES);
            String where = screen[0] + "×" + screen[1];
            assertTrue(frame.innerWidth() >= 160, where + " · ancho " + frame.innerWidth());
            assertTrue(frame.viewport().height() >= 100, where + " · alto " + frame.viewport().height());
        }
    }

    @Test
    void labelsAreShownWheneverThereIsRoom() {
        StudioLayout.Frame wide = StudioLayout.compute(960, 540, StudioLayout.DEFAULT_PANEL_WIDTH, STAGES);
        assertEquals(StudioLayout.NavMode.LABELS, wide.navMode());
        StudioLayout.Frame narrowest = StudioLayout.compute(960, 540, 0, STAGES);
        assertEquals(StudioLayout.NavMode.LABELS, narrowest.navMode(), "el ancho mínimo ya deja nombres");
        StudioLayout.Frame tiny = StudioLayout.compute(320, 240, 0, STAGES);
        assertEquals(StudioLayout.NavMode.ICONS, tiny.navMode());
    }

    @Test
    void theStudioLeavesTheChatFreeWheneverTheScreenAllowsIt() {
        for (int[] screen : scaledScreens()) {
            for (int preferred : new int[]{0, StudioLayout.DEFAULT_PANEL_WIDTH, 10_000}) {
                StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], preferred, STAGES);
                if (screen[0] - frame.panel().width() >= StudioLayout.CHAT_WIDTH) {
                    assertFalse(frame.panel().intersects(frame.chatZone()),
                            screen[0] + "×" + screen[1] + " · panel " + frame.panel());
                }
            }
        }
        // 1080p con GUI 2 y 3, lo habitual: el chat siempre queda a la vista.
        assertFalse(StudioLayout.compute(960, 540, 10_000, STAGES).panel()
                .intersects(StudioLayout.chatZone(960, 540)));
        assertFalse(StudioLayout.compute(640, 360, 0, STAGES).panel()
                .intersects(StudioLayout.chatZone(640, 360)));
    }

    @Test
    void thePanelNeverTakesMoreThanHalfOfAWideScreen() {
        for (int[] screen : scaledScreens()) {
            if (screen[0] >= 2 * StudioLayout.MIN_PANEL_WIDTH) {
                StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], 10_000, STAGES);
                assertTrue(frame.panel().width() <= screen[0] / 2, screen[0] + "×" + screen[1]);
            }
        }
    }

    @Test
    void narrowScreensKeepMostOfTheWorldVisible() {
        // GUI 4 en 1080p (la escala automática): 480×270.
        StudioLayout.Frame frame = StudioLayout.compute(480, 270, 10_000, STAGES);
        assertEquals(StudioLayout.NavMode.ICONS, frame.navMode());
        assertTrue(frame.world().width() >= frame.panel().width(), "mundo " + frame.world().width());
    }

    @Test
    void preferredWidthIsRespectedInsideTheLimits() {
        assertEquals(400, StudioLayout.compute(1920, 1080, 400, STAGES).panel().width());
        assertEquals(StudioLayout.MIN_PANEL_WIDTH, StudioLayout.compute(1920, 1080, 10, STAGES).panel().width());
        assertEquals(StudioLayout.MAX_PANEL_WIDTH, StudioLayout.compute(1920, 1080, 9_000, STAGES).panel().width());
    }
}
