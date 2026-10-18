package com.komainu.moxia.studio.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnTest {
    @Test
    void rowsStackWithTheGapAndCountTheirHeight() {
        Column column = new Column(10, 100, 200);
        Rect first = column.next();
        Rect second = column.next(30);
        assertEquals(new Rect(10, 100, 200, Column.ROW), first);
        assertEquals(100 + Column.ROW + Column.GAP, second.y());
        assertEquals(Column.ROW + 30 + Column.GAP * 2, column.height());
    }

    @Test
    void sectionGapIsOnlyAddedAfterSomething() {
        Column column = new Column(0, 0, 100);
        column.section();
        assertEquals(0, column.y());
        column.next();
        int before = column.y();
        column.section();
        assertEquals(before + Column.SECTION_GAP - Column.GAP, column.y());
    }

    @Test
    void cellsOfARowCoverTheWholeWidthWithoutOverlapping() {
        for (int width = 100; width < 400; width += 7) {
            Rect[] cells = new Column(5, 0, width).row(3, 20);
            assertEquals(5, cells[0].x());
            assertEquals(5 + width, cells[2].right());
            assertFalse(cells[0].intersects(cells[1]));
            assertFalse(cells[1].intersects(cells[2]));
        }
    }

    @Test
    void gridFillsRowByRowAndSkipsMissingCells() {
        Column column = new Column(0, 0, 100);
        List<Rect> cells = column.grid(5, 2, 20);
        assertEquals(5, cells.size());
        assertEquals(cells.get(0).y(), cells.get(1).y());
        assertTrue(cells.get(2).y() > cells.get(0).y());
        assertEquals(cells.get(4).x(), cells.get(0).x());
        assertEquals(3 * (20 + Column.GAP), column.height());
    }

    @Test
    void flowWrapsWhenALineIsFullAndNeverOverflows() {
        List<Rect> placed = Column.flow(new int[]{40, 40, 40, 300}, 16, 0, 0, 100);
        assertEquals(0, placed.get(0).y());
        assertEquals(0, placed.get(1).y());
        assertEquals(16 + Column.GAP, placed.get(2).y());
        assertEquals(100, placed.get(3).width(), "lo que no cabe se recorta al ancho");
        for (Rect rect : placed) {
            assertTrue(rect.right() <= 100);
        }
    }
}
