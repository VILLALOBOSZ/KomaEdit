package com.komainu.moxia.studio.layout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioLayoutTest {
    private static final int STAGES = 10;
    /** Crear, Ajustar y Biblioteca, como en el Studio. */
    private static final int[] GROUPS = {3, 4, 3};
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
            for (int preferred : new int[]{1, StudioLayout.AUTO, 10_000}) {
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
                if (!frame.compact()) {
                    // En compacto el estado comparte la línea de arriba; si no, van en dos líneas.
                    assertFalse(frame.footerSummary().intersects(frame.footerStatus()), where);
                }
                assertTrue(frame.footer().encloses(frame.footerProgress()), where);
                assertFalse(frame.footerProgress().intersects(frame.footerStatus()), where);
                assertFalse(frame.footerProgress().intersects(frame.buildButton()), where);
                Rect[] split = frame.footerButtons(70);
                assertTrue(frame.buildButton().encloses(split[0]), where);
                assertTrue(frame.buildButton().encloses(split[1]), where);
                assertFalse(split[0].intersects(split[1]), where);
                assertTrue(split[0].width() >= split[1].width(), "el botón principal no queda más estrecho · " + where);
                assertEquals(frame.panel().x(), frame.world().right(), where);
            }
        }
    }

    @Test
    void panelPartsNeverOverlap() {
        for (int[] screen : scaledScreens()) {
            StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], StudioLayout.AUTO,
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
            StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], StudioLayout.AUTO,
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
    void withRoomTheStagesKeepTheirComfortableRowAndGap() {
        StudioLayout.Frame nine = StudioLayout.compute(960, 540, StudioLayout.AUTO, 9);
        assertEquals(StudioLayout.NAV_GAP, nine.navGap());
        StudioLayout.Frame ten = StudioLayout.compute(960, 540, StudioLayout.AUTO, GROUPS);
        assertEquals(StudioLayout.NAV_GAP, ten.navGap());
        assertTrue(ten.navRowHeight() >= StudioLayout.COMFORT_NAV_ROW);
        // 854×480 con GUI 2: las diez caben, con sus rayas de grupo, sin desplazar.
        StudioLayout.Frame tight = StudioLayout.compute(427, 240, 0, GROUPS);
        assertTrue(tight.nav().encloses(tight.navItem(STAGES - 1)));
        assertTrue(tight.navRowHeight() >= StudioLayout.MIN_NAV_ROW);
    }

    @Test
    void groupsHaveTheirCaptionAboveTheirFirstStage() {
        for (int[] screen : scaledScreens()) {
            StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], StudioLayout.AUTO,
                    GROUPS);
            int first = 0;
            for (int group = 0; group < GROUPS.length; group++) {
                if (frame.navMode() == StudioLayout.NavMode.ICONS && group == 0) {
                    // Con iconos solo hay raya entre grupos, no antes del primero.
                    first += GROUPS[group];
                    continue;
                }
                Rect caption = frame.groupCaption(group);
                Rect item = frame.navItem(first);
                assertTrue(caption.bottom() <= item.y(), screen[0] + "×" + screen[1] + " · grupo " + group);
                assertTrue(frame.nav().encloses(caption), screen[0] + "×" + screen[1] + " · grupo " + group);
                if (first > 0) {
                    assertFalse(caption.intersects(frame.navItem(first - 1)), "la raya no pisa la etapa de antes");
                }
                assertEquals(group, frame.groupOf(first));
                first += GROUPS[group];
            }
        }
    }

    @Test
    void toolbarButtonsFitNextToTheTitle() {
        for (int[] screen : scaledScreens()) {
            StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], 0, GROUPS);
            // Siete botones con el panel ancho; en uno estrecho, holograma y rehacer se quedan en sus teclas.
            int buttons = frame.panel().width() < 300 ? 5 : 7;
            for (int index = 0; index < buttons; index++) {
                Rect button = frame.toolButton(index);
                assertTrue(frame.toolbar().encloses(button), screen[0] + "×" + screen[1] + " · botón " + index);
            }
            // Queda sitio para el nombre de la etapa a la izquierda del último botón.
            assertTrue(frame.toolButton(buttons - 1).x() - frame.toolbar().x() >= 60, screen[0] + "×" + screen[1]);
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
        StudioLayout.Frame wide = StudioLayout.compute(960, 540, StudioLayout.AUTO, STAGES);
        assertEquals(StudioLayout.NavMode.LABELS, wide.navMode());
        StudioLayout.Frame narrowest = StudioLayout.compute(960, 540, 1, STAGES);
        assertEquals(StudioLayout.NavMode.ICONS, narrowest.navMode(), "al mínimo, el sitio es para el contenido");
        StudioLayout.Frame gui3 = StudioLayout.compute(640, 360, StudioLayout.AUTO, STAGES);
        assertEquals(StudioLayout.NavMode.ICONS, gui3.navMode(), "1080p con GUI 3: iconos y el contenido entero");
        assertTrue(gui3.innerWidth() >= 190, "ancho del contenido " + gui3.innerWidth());
        StudioLayout.Frame gui3At1440 = StudioLayout.compute(853, 480, StudioLayout.AUTO, STAGES);
        assertEquals(StudioLayout.NavMode.LABELS, gui3At1440.navMode(), "1440p con GUI 3: con nombres");
        StudioLayout.Frame tiny = StudioLayout.compute(320, 240, 0, STAGES);
        assertEquals(StudioLayout.NavMode.ICONS, tiny.navMode());
    }

    @Test
    void theStudioLeavesTheChatFreeWheneverTheScreenAllowsIt() {
        for (int[] screen : scaledScreens()) {
            for (int preferred : new int[]{1, StudioLayout.AUTO, 10_000}) {
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
    void byDefaultThePanelLeavesMostOfABigScreenToTheWorld() {
        for (int[] screen : scaledScreens()) {
            if (screen[0] >= StudioLayout.NARROW_SCREEN) {
                StudioLayout.Frame frame = StudioLayout.compute(screen[0], screen[1], StudioLayout.AUTO, STAGES);
                assertTrue(frame.panel().width() * 100 <= screen[0] * 40,
                        screen[0] + "×" + screen[1] + " · panel " + frame.panel().width());
                StudioLayout.Frame widest = StudioLayout.compute(screen[0], screen[1], 10_000, STAGES);
                assertTrue(widest.panel().width() * 100 <= screen[0] * StudioLayout.MAX_SHARE_PERCENT,
                        "arrastrando, como mucho el " + StudioLayout.MAX_SHARE_PERCENT + " %");
            }
        }
        // 1080p con GUI 3: antes 320 de 640 (la mitad); ahora el 38 %.
        assertEquals(243, StudioLayout.compute(640, 360, StudioLayout.AUTO, STAGES).panel().width());
        assertEquals(365, StudioLayout.compute(960, 540, StudioLayout.AUTO, STAGES).panel().width());
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
