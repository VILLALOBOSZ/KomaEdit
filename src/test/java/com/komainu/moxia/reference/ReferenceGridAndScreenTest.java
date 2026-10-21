package com.komainu.moxia.reference;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La rejilla para contar bloques y la ventana flotante del modo en pantalla. */
class ReferenceGridAndScreenTest {
    @Test
    void aLineEveryCellButNotOnTheBorders() {
        ReferenceGrid grid = ReferenceGrid.DEFAULT.withEnabled(true).withPixels(16);
        List<ReferenceGrid.Line> lines = grid.lines(64, 32);
        assertEquals(List.of(16.0, 32.0, 48.0), lines.stream().filter(ReferenceGrid.Line::vertical)
                .map(ReferenceGrid.Line::at).toList());
        assertEquals(List.of(16.0), lines.stream().filter(line -> !line.vertical()).map(ReferenceGrid.Line::at)
                .toList());
        assertEquals(4, grid.columns(64));
        assertEquals(2, grid.rows(32));
        // Una imagen que no es múltiplo: la última celda va a medias, pero cuenta.
        assertEquals(5, grid.columns(65));
    }

    @Test
    void offsetAlignsTheGridAndCountsFromOne() {
        // Pixel art de 10 píxeles por bloque con un borde de 3: la tira del borde es la columna 1.
        ReferenceGrid grid = ReferenceGrid.DEFAULT.withPixels(10).withOffset(3, 0);
        assertEquals(List.of(3.0, 13.0, 23.0), grid.lines(25, 10).stream().filter(ReferenceGrid.Line::vertical)
                .map(ReferenceGrid.Line::at).toList());
        assertEquals(4, grid.columns(25));
        assertEquals(1, grid.column(0.0, 25));
        assertEquals(1, grid.column(2.99, 25));
        assertEquals(2, grid.column(3.0, 25));
        assertEquals(2, grid.column(12.9, 25));
        assertEquals(3, grid.column(13.0, 25));
        assertEquals(4, grid.column(24.9, 25));
        // Fuera de la imagen no hay celda.
        assertEquals(0, grid.column(25.0, 25));
        assertEquals(0, grid.column(-0.5, 25));
        // Sin desplazamiento se cuenta desde la primera celda.
        ReferenceGrid plain = grid.withOffset(0, 0);
        assertEquals(1, plain.column(9.9, 25));
        assertEquals(2, plain.column(10.0, 25));
        assertEquals(1, plain.row(0.0, 10));
    }

    @Test
    void markedLinesEveryFewCells() {
        ReferenceGrid grid = ReferenceGrid.DEFAULT.withPixels(4).withMajor(5);
        List<ReferenceGrid.Line> vertical = grid.lines(64, 4).stream().filter(ReferenceGrid.Line::vertical).toList();
        assertEquals(15, vertical.size());
        assertEquals(List.of(20.0, 40.0, 60.0), vertical.stream().filter(ReferenceGrid.Line::major)
                .map(ReferenceGrid.Line::at).toList());
    }

    @Test
    void tooManyLinesKeepOnlyTheMarkedOnesOrNone() {
        ReferenceGrid grid = ReferenceGrid.DEFAULT.withPixels(1);
        assertTrue(grid.lines(5_000, 10).isEmpty());
        List<ReferenceGrid.Line> marked = grid.withMajor(10).lines(5_000, 10);
        assertFalse(marked.isEmpty());
        assertTrue(marked.stream().allMatch(ReferenceGrid.Line::major));
        assertTrue(grid.withMajor(1).lines(5_000, 10).isEmpty());
    }

    @Test
    void gridValuesStayInRange() {
        ReferenceGrid grid = new ReferenceGrid(true, 0, -3, -1, 25, null);
        assertEquals(1, grid.pixels());
        assertEquals(0, grid.major());
        assertEquals(0, grid.offsetX());
        assertEquals(ReferenceGrid.Color.WHITE, grid.color());
        ReferenceGrid wide = grid.withPixels(10).withOffset(-1, 25);
        assertEquals(9, wide.offsetX());
        assertEquals(5, wide.offsetY());
        assertEquals(ReferenceGrid.MAX_PIXELS, grid.withPixels(100_000).pixels());
        assertEquals(ReferenceGrid.Color.BLACK, ReferenceGrid.Color.WHITE.next());
        assertEquals(ReferenceGrid.Color.WHITE, ReferenceGrid.Color.CYAN.next());
        assertTrue(grid.withPixels(8).visibleAt(0.5, 4.0));
        assertFalse(grid.withPixels(8).visibleAt(0.25, 4.0));
    }

    @Test
    void windowLayoutKeepsTheProportionAndTheBarAbove() {
        ScreenFrame frame = new ScreenFrame(0.5, 0.25, 0.25, false);
        ScreenFrame.Window window = frame.layout(400, 240, 200, 100);
        assertEquals(new ScreenFrame.Rect(200, 60, 100, 50), window.image());
        assertEquals(new ScreenFrame.Rect(200, 48, 100, ScreenFrame.BAR), window.bar());
        assertEquals(new ScreenFrame.Rect(288, 48, ScreenFrame.BAR, ScreenFrame.BAR), window.hide());
        assertEquals(new ScreenFrame.Rect(276, 48, ScreenFrame.BAR, ScreenFrame.BAR), window.through());
        assertEquals(new ScreenFrame.Rect(292, 102, ScreenFrame.GRIP, ScreenFrame.GRIP), window.grip());
        assertEquals(0.5, window.scale(200), 1.0E-9);
        double[] pixel = window.pixel(250, 85, 200, 100);
        assertEquals(100.0, pixel[0], 1.0E-9);
        assertEquals(50.0, pixel[1], 1.0E-9);
    }

    @Test
    void windowPartsUnderTheMouse() {
        ScreenFrame.Window window = new ScreenFrame(0.5, 0.25, 0.25, false).layout(400, 240, 200, 100);
        assertEquals(ScreenFrame.Part.HIDE, window.hit(290, 50, true));
        assertEquals(ScreenFrame.Part.THROUGH, window.hit(280, 50, true));
        assertEquals(ScreenFrame.Part.BAR, window.hit(210, 50, true));
        assertEquals(ScreenFrame.Part.GRIP, window.hit(297, 107, true));
        assertEquals(ScreenFrame.Part.IMAGE, window.hit(250, 80, true));
        assertEquals(ScreenFrame.Part.NONE, window.hit(10, 10, true));
        // Sin barra ni esquina (en el mundo o «a través»), solo la imagen.
        assertEquals(ScreenFrame.Part.NONE, window.hit(210, 50, false));
        assertEquals(ScreenFrame.Part.IMAGE, window.hit(297, 107, false));
    }

    @Test
    void windowNeverGetsLostOffScreen() {
        // Arrastrada casi fuera: quedan 24 unidades a la vista y la barra siempre dentro por arriba.
        ScreenFrame.Window right = new ScreenFrame(1.0, 0.0, 0.25, false).layout(400, 240, 200, 100);
        assertEquals(400 - ScreenFrame.KEEP, right.image().x());
        assertEquals(ScreenFrame.BAR, right.image().y());
        ScreenFrame.Window left = new ScreenFrame(-2.0, 1.0, 0.25, false).layout(400, 240, 200, 100);
        assertEquals(ScreenFrame.KEEP - 100, left.image().x());
        assertEquals(240 - ScreenFrame.KEEP, left.image().y());
        // Ni muy pequeña: el lado corto no baja de 16.
        ScreenFrame.Window tiny = new ScreenFrame(0.0, 0.5, ScreenFrame.MIN_WIDTH, false).layout(200, 100, 400,
                100);
        assertEquals(ScreenFrame.MIN_SIDE, tiny.image().height());
        assertEquals(64, tiny.image().width());
    }

    @Test
    void movingResizingAndZoomingTheWindow() {
        ScreenFrame frame = new ScreenFrame(0.5, 0.25, 0.25, false);
        ScreenFrame moved = frame.movedTo(40, 120, 400, 240);
        assertEquals(new ScreenFrame.Rect(40, 120, 100, 50), moved.layout(400, 240, 200, 100).image());
        ScreenFrame wider = frame.withImageWidth(160, 400);
        assertEquals(new ScreenFrame.Rect(200, 60, 160, 80), wider.layout(400, 240, 200, 100).image());
        // La rueda agranda alrededor del ratón: el píxel que había debajo sigue debajo.
        ScreenFrame.Window before = frame.layout(400, 240, 200, 100);
        double[] under = before.pixel(230, 70, 200, 100);
        ScreenFrame zoomed = frame.zoomed(1.5, 230, 70, 400, 240, 200, 100);
        ScreenFrame.Window after = zoomed.layout(400, 240, 200, 100);
        double[] still = after.pixel(230, 70, 200, 100);
        assertEquals(150, after.image().width());
        assertEquals(under[0], still[0], 1.5);
        assertEquals(under[1], still[1], 1.5);
        // Los extremos se respetan.
        assertEquals(ScreenFrame.MAX_WIDTH, frame.zoomed(1_000, 0, 0, 400, 240, 200, 100).width(), 1.0E-9);
        assertEquals(ScreenFrame.MIN_WIDTH, frame.zoomed(0.0001, 0, 0, 400, 240, 200, 100).width(), 1.0E-9);
        assertTrue(frame.withClickThrough(true).clickThrough());
        ScreenFrame broken = new ScreenFrame(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, false);
        assertEquals(ScreenFrame.DEFAULT, broken);
    }
}
