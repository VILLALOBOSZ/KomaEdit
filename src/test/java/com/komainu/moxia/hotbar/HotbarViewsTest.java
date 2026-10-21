package com.komainu.moxia.hotbar;

import com.komainu.moxia.hotbar.ui.HotbarDeck;
import com.komainu.moxia.hotbar.ui.HotbarPainter;
import com.komainu.moxia.hotbar.ui.HotbarStripView;
import com.komainu.moxia.hotbar.ui.HotbarSwitcher;
import com.komainu.moxia.hotbar.ui.HotbarTexts;
import com.komainu.moxia.hotbar.ui.HotbarWheelView;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.ui.FakeStudioHost;
import com.komainu.moxia.studio.ui.ImageCanvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La tecla de las barras, la tira y el menú circular, sin el juego. */
class HotbarViewsTest {
    static HotbarTexts texts(String language) {
        Map<String, String> lang = FakeStudioHost.load(language);
        return (key, arguments) -> FakeStudioHost.format(lang.getOrDefault(key, key), arguments);
    }

    static HotbarDeck deck(int size, int active, HotbarRules.Mode mode) {
        List<HotbarDeck.Row> rows = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            rows.add(new HotbarDeck.Row("barra" + index, "minecraft:stone",
                    List.of(HotbarItem.of("stone", 64), HotbarItem.of("dirt", index + 1))));
        }
        return new HotbarDeck(rows, active, mode, mode == HotbarRules.Mode.DENIED ? "En espectador no hay barra" : null);
    }

    // ---- La tecla ----------------------------------------------------------------------------------------------

    @Test
    void aTapOpensTheMenu() {
        HotbarSwitcher switcher = new HotbarSwitcher();
        switcher.press(5, 1);
        switcher.tick();
        assertFalse(switcher.visible(), "un toque no llega a enseñar la tira");
        assertEquals(HotbarSwitcher.Outcome.OPEN_MENU, switcher.release(true, 1).outcome());
    }

    @Test
    void holdingAndScrollingSwitchesOnRelease() {
        HotbarSwitcher switcher = new HotbarSwitcher();
        switcher.press(5, 1);
        for (int tick = 0; tick < HotbarSwitcher.SHOW_TICKS; tick++) {
            switcher.tick();
        }
        assertTrue(switcher.visible());
        assertEquals(1, switcher.selected(), "empieza en la que está en uso");
        assertTrue(switcher.scroll(-1.0), "la rueda es de la tira");
        assertTrue(switcher.scroll(-1.0));
        assertEquals(3, switcher.selected(), "rueda abajo: la siguiente");
        HotbarSwitcher.Result result = switcher.release(true, 1);
        assertEquals(HotbarSwitcher.Outcome.APPLY, result.outcome());
        assertEquals(3, result.index());
        assertFalse(switcher.held());
    }

    @Test
    void theWheelGoesRoundAndBackToTheActiveDoesNothing() {
        HotbarSwitcher switcher = new HotbarSwitcher();
        switcher.press(3, 0);
        switcher.scroll(1.0);
        assertEquals(2, switcher.selected(), "rueda arriba desde la primera: la última");
        switcher.scroll(-1.0);
        assertEquals(HotbarSwitcher.Outcome.NONE, switcher.release(true, 0).outcome(), "volver a la de antes no cambia");

        switcher.press(3, -1);
        switcher.scroll(-1.0);
        assertEquals(0, switcher.selected(), "sin barra en uso, rueda abajo da la primera");
    }

    @Test
    void altTabDoesNotSwitchOrOpenAnything() {
        HotbarSwitcher switcher = new HotbarSwitcher();
        switcher.press(4, 0);
        assertEquals(HotbarSwitcher.Outcome.NONE, switcher.release(false, 0).outcome());
        switcher.press(4, 0);
        switcher.scroll(-1.0);
        assertEquals(HotbarSwitcher.Outcome.NONE, switcher.release(false, 0).outcome());
    }

    /**
     * Alt también es modificador: Alt+clic en un display, Ctrl+Alt+R del fantasma, Alt para arrastrar sin pasos. Si
     * con la tecla pulsada se usa otra tecla o el ratón, soltarla no abre el menú ni la tira sale encima; la rueda
     * sí sigue siendo de la tira.
     */
    @Test
    void altUsedWithAnotherKeyOrAClickIsNotATap() {
        HotbarSwitcher switcher = new HotbarSwitcher();
        switcher.press(5, 1);
        switcher.usedAsModifier();
        switcher.tick();
        assertEquals(HotbarSwitcher.Outcome.NONE, switcher.release(true, 1).outcome(), "Alt+clic rápido");

        switcher.press(5, 1);
        switcher.usedAsModifier();
        for (int tick = 0; tick < HotbarSwitcher.SHOW_TICKS; tick++) {
            switcher.tick();
        }
        assertFalse(switcher.visible(), "arrastrando con Alt, la tira no tapa el display");
        assertTrue(switcher.scroll(-1.0), "la rueda con Alt sigue siendo de la tira");
        assertTrue(switcher.visible());
        HotbarSwitcher.Result result = switcher.release(true, 1);
        assertEquals(HotbarSwitcher.Outcome.APPLY, result.outcome());
        assertEquals(2, result.index());

        switcher.press(5, 1);
        switcher.usedAsModifier();
        switcher.cancel();
        switcher.press(5, 1);
        switcher.tick();
        assertEquals(HotbarSwitcher.Outcome.OPEN_MENU, switcher.release(true, 1).outcome(),
                "la pulsación siguiente vuelve a ser un toque");
    }

    @Test
    void withoutHotbarsTheWheelIsStillTaken() {
        HotbarSwitcher switcher = new HotbarSwitcher();
        assertFalse(switcher.scroll(-1.0), "sin la tecla, la rueda es de Minecraft");
        switcher.press(0, -1);
        assertTrue(switcher.scroll(-1.0), "con la tecla y sin barras no cambia de casilla");
        assertEquals(-1, switcher.selected());
        assertEquals(HotbarSwitcher.Outcome.NONE, switcher.release(true, -1).outcome());
    }

    @Test
    void aNewCatalogKeepsTheChosenHotbar() {
        HotbarSwitcher switcher = new HotbarSwitcher();
        switcher.press(5, 0);
        switcher.scroll(-1.0);
        switcher.scroll(-1.0);
        switcher.resize(6, 3);
        assertEquals(3, switcher.selected());
        switcher.resize(2, 7);
        assertEquals(-1, switcher.selected(), "la elegida se borró");
    }

    // ---- La tira -----------------------------------------------------------------------------------------------

    @Test
    void theStripKeepsTheChosenHotbarInViewAboveTheRealOne() {
        int width = 640;
        int height = 360;
        HotbarStripView.Layout few = HotbarStripView.layout(width, height, 3, 2);
        assertEquals(3, few.rows().size());
        assertEquals(0, few.first());
        assertFalse(few.moreAbove() || few.moreBelow());

        HotbarStripView.Layout many = HotbarStripView.layout(width, height, 20, 10);
        assertEquals(HotbarStripView.VISIBLE, many.rows().size());
        assertTrue(many.first() <= 10 && many.last() >= 10, "la elegida se ve");
        assertEquals(8, many.first(), "y va en el centro");
        assertTrue(many.moreAbove() && many.moreBelow());
        assertEquals(0, HotbarStripView.layout(width, height, 20, 0).first());
        assertEquals(15, HotbarStripView.layout(width, height, 20, 19).first());

        Rect hotbar = new Rect(width / 2 - 91, height - 22, 182, 22);
        for (Rect row : many.rows()) {
            assertEquals(hotbar.x(), row.x(), "del ancho y en el sitio de la barra de verdad");
            assertEquals(182, row.width());
            assertTrue(row.bottom() <= hotbar.y() - 6, "por encima de la barra y de la experiencia");
            assertTrue(row.y() >= 0);
        }
    }

    @Test
    void theStripSaysWhatReleasingDoes() {
        HotbarTexts es = texts("es_es");
        ImageCanvas canvas = new ImageCanvas();
        HotbarDeck creative = deck(4, 1, HotbarRules.Mode.SET);
        HotbarStripView.draw(canvas, HotbarStripView.layout(640, 360, 4, 2), creative, 2, HotbarPainter.ICONS, es);
        assertTrue(written(canvas, "Suelta para usarla"));
        assertTrue(written(canvas, "barra2"));
        assertTrue(written(canvas, "3/4"), "la posición de la elegida");

        canvas = new ImageCanvas();
        HotbarStripView.draw(canvas, HotbarStripView.layout(640, 360, 4, 2), deck(4, 1, HotbarRules.Mode.REORDER), 2,
                HotbarPainter.ICONS, es);
        assertTrue(written(canvas, "solo se mueve lo que llevas"), "en supervivencia avisa");

        canvas = new ImageCanvas();
        HotbarStripView.draw(canvas, HotbarStripView.layout(640, 360, 0, -1), HotbarDeck.EMPTY, -1, HotbarPainter.ICONS,
                es);
        assertTrue(written(canvas, "Aún no tienes barras guardadas"));
    }

    @Test
    void longNamesAreCutToFit() {
        ImageCanvas canvas = new ImageCanvas();
        List<HotbarDeck.Row> rows = List.of(new HotbarDeck.Row("x".repeat(32), "minecraft:stone", List.of()));
        HotbarStripView.draw(canvas, HotbarStripView.layout(427, 240, 1, 0), new HotbarDeck(rows, 0,
                HotbarRules.Mode.SET, null), 0, HotbarPainter.ICONS, texts("es_es"));
        assertTrue(canvas.texts().stream().anyMatch(drawn -> drawn.text().endsWith("…") && drawn.x() >= 0),
                "el nombre largo acaba en … y no se sale de la pantalla");
    }

    // ---- El menú -----------------------------------------------------------------------------------------------

    @Test
    void eachTileAnswersToItsWholeSector() {
        HotbarWheelView.Layout layout = HotbarWheelView.layout(640, 360, 9, 0);
        assertEquals(9, layout.tiles().size());
        for (int index = 0; index < layout.tiles().size(); index++) {
            Rect tile = layout.tiles().get(index);
            assertEquals(index, HotbarWheelView.tileAt(layout, tile.centerX(), tile.centerY()), "barra " + index);
            double outX = layout.centerX() + (tile.centerX() - layout.centerX()) * 1.3;
            double outY = layout.centerY() + (tile.centerY() - layout.centerY()) * 1.3;
            assertEquals(index, HotbarWheelView.tileAt(layout, outX, outY), "un poco por fuera también");
        }
        assertEquals(-1, HotbarWheelView.tileAt(layout, layout.centerX(), layout.centerY()), "el centro es la vista");
        Rect center = layout.center();
        assertEquals(-1, HotbarWheelView.tileAt(layout, center.right() - 2, center.centerY()),
                "ni el borde de los nueve huecos del centro");
        assertEquals(-1, HotbarWheelView.tileAt(layout, layout.save().centerX(), layout.save().centerY()));
        assertEquals(HotbarWheelView.Button.SAVE,
                HotbarWheelView.buttonAt(layout, layout.save().centerX(), layout.save().centerY()));
        assertEquals(HotbarWheelView.Button.COPY_ALL,
                HotbarWheelView.buttonAt(layout, layout.copyAll().centerX(), layout.copyAll().centerY()));
    }

    @Test
    void fewHotbarsDoNotEndUpOnOppositeSides() {
        HotbarWheelView.Layout two = HotbarWheelView.layout(640, 360, 2, 0);
        Rect first = two.tiles().get(0);
        Rect second = two.tiles().get(1);
        assertTrue(second.centerX() > first.centerX() && second.centerY() > first.centerY()
                && second.centerY() < two.centerY(), "la segunda va arriba a la derecha, no abajo del todo");
        assertEquals(-1, HotbarWheelView.tileAt(two, two.centerX(), two.centerY() + two.radiusY()),
                "un sector sin barra no responde");
    }

    @Test
    void moreThanTwelveGoInPages() {
        assertEquals(1, HotbarWheelView.pages(12));
        assertEquals(2, HotbarWheelView.pages(13));
        HotbarWheelView.Layout second = HotbarWheelView.layout(640, 360, 20, 1);
        assertEquals(8, second.tiles().size());
        assertEquals(12, second.first());
        Rect tile = second.tiles().getFirst();
        assertEquals(12, HotbarWheelView.tileAt(second, tile.centerX(), tile.centerY()));
        assertEquals(0, HotbarWheelView.layout(640, 360, 20, 2).page(), "pasada la última vuelve a la primera");
    }

    @Test
    void everythingFitsOnASmallScreen() {
        HotbarWheelView.Layout layout = HotbarWheelView.layout(427, 240, 12, 0);
        Rect screen = new Rect(0, 0, 427, 240);
        for (Rect tile : layout.tiles()) {
            assertTrue(screen.encloses(tile), "dentro de la pantalla: " + tile);
            assertTrue(tile.bottom() + 12 < layout.nameField().y(), "y por encima de los botones");
        }
        assertTrue(screen.encloses(layout.copyAll()));
        assertTrue(layout.nameField().x() >= 0);
    }

    @Test
    void theCenterShowsTheHoveredHotbar() {
        HotbarTexts es = texts("es_es");
        HotbarWheelView.Layout layout = HotbarWheelView.layout(640, 360, 5, 0);
        ImageCanvas canvas = new ImageCanvas();
        HotbarWheelView.draw(canvas, layout, deck(5, 0, HotbarRules.Mode.REORDER), 3, HotbarWheelView.Button.NONE, "",
                null, HotbarPainter.ICONS, es);
        assertTrue(written(canvas, "solo se mueve lo que llevas"));
        assertTrue(canvas.items().stream().filter("minecraft:dirt"::equals).count() >= 1, "con sus huecos");
        assertTrue(written(canvas, "Guardar"));
        assertTrue(written(canvas, "Nombre de la barra"), "el recuadro del nombre dice para qué es");

        canvas = new ImageCanvas();
        HotbarWheelView.draw(canvas, HotbarWheelView.layout(640, 360, 0, 0), HotbarDeck.EMPTY, -1,
                HotbarWheelView.Button.NONE, "", null, HotbarPainter.ICONS, texts("en_us"));
        assertTrue(written(canvas, "No hotbars yet"));
    }

    private static boolean written(ImageCanvas canvas, String text) {
        return canvas.texts().stream().anyMatch(drawn -> drawn.text().contains(text));
    }
}
