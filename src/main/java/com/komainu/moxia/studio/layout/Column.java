package com.komainu.moxia.studio.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Cursor vertical del contenido de una etapa: cada control pide su alto y recibe su rectángulo, uno debajo
 * de otro. Así ninguna etapa calcula coordenadas a mano y el alto total sale solo para el desplazamiento.
 */
public final class Column {
    public static final int ROW = 20;
    public static final int GAP = 4;
    public static final int SECTION_GAP = 10;

    private final int x;
    private final int width;
    private final int top;
    private int y;

    public Column(int x, int y, int width) {
        this.x = x;
        this.width = width;
        this.top = y;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int width() {
        return width;
    }

    public int y() {
        return y;
    }

    /** Alto ocupado desde el principio. */
    public int height() {
        return y - top;
    }

    /** Una fila de ancho completo y el hueco normal debajo. */
    public Rect next(int height) {
        Rect row = new Rect(x, y, width, height);
        y += height + GAP;
        return row;
    }

    public Rect next() {
        return next(ROW);
    }

    /** {@code count} celdas iguales en una misma fila. */
    public Rect[] row(int count, int height) {
        return next(height).columns(count, GAP);
    }

    /** Rejilla de {@code items} celdas en {@code columns} columnas, fila a fila. */
    public List<Rect> grid(int items, int columns, int height) {
        List<Rect> cells = new ArrayList<>(items);
        for (int start = 0; start < items; start += columns) {
            Rect[] line = row(columns, height);
            for (int index = 0; index < columns && start + index < items; index++) {
                cells.add(line[index]);
            }
        }
        return cells;
    }

    public void space(int amount) {
        y += amount;
    }

    /** Separación antes de una sección nueva; no se añade al principio. */
    public void section() {
        if (y > top) {
            y += SECTION_GAP - GAP;
        }
    }

    /** Coloca botones de ancho variable en líneas, saltando de línea cuando no caben. */
    public List<Rect> flow(int[] widths, int height) {
        List<Rect> placed = flow(widths, height, x, y, width);
        int bottom = y;
        for (Rect rect : placed) {
            bottom = Math.max(bottom, rect.bottom());
        }
        y = bottom + GAP;
        return placed;
    }

    public static List<Rect> flow(int[] widths, int height, int left, int top, int maximumWidth) {
        List<Rect> placed = new ArrayList<>(widths.length);
        int cursorX = left;
        int cursorY = top;
        int right = left + maximumWidth;
        for (int requested : widths) {
            int itemWidth = Math.min(requested, maximumWidth);
            if (cursorX > left && cursorX + itemWidth > right) {
                cursorX = left;
                cursorY += height + GAP;
            }
            placed.add(new Rect(cursorX, cursorY, itemWidth, height));
            cursorX += itemWidth + GAP;
        }
        return placed;
    }
}
