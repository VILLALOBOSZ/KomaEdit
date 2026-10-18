package com.komainu.moxia.studio.layout;

/** Rectángulo en píxeles escalados de la GUI: esquina superior izquierda, ancho y alto. */
public record Rect(int x, int y, int width, int height) {
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public Rect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    public boolean intersects(Rect other) {
        return !isEmpty() && !other.isEmpty()
                && x < other.right() && other.x < right()
                && y < other.bottom() && other.y < bottom();
    }

    /** Si {@code other} cabe entero dentro de este. */
    public boolean encloses(Rect other) {
        return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
    }

    public Rect inset(int horizontal, int vertical) {
        return new Rect(x + horizontal, y + vertical, width - horizontal * 2, height - vertical * 2);
    }

    /** Franja izquierda de {@code amount} píxeles. */
    public Rect left(int amount) {
        return new Rect(x, y, Math.min(width, amount), height);
    }

    /** Franja derecha de {@code amount} píxeles. */
    public Rect rightPart(int amount) {
        int taken = Math.min(width, amount);
        return new Rect(right() - taken, y, taken, height);
    }

    /** Lo que queda a la izquierda tras quitar {@code amount} píxeles por la derecha y un hueco. */
    public Rect withoutRight(int amount, int gap) {
        return new Rect(x, y, width - amount - gap, height);
    }

    /** {@code count} celdas iguales en horizontal separadas por {@code gap}; la última absorbe el resto. */
    public Rect[] columns(int count, int gap) {
        Rect[] cells = new Rect[count];
        int cell = (width - gap * (count - 1)) / count;
        for (int index = 0; index < count; index++) {
            int cellX = x + index * (cell + gap);
            int cellWidth = index == count - 1 ? right() - cellX : cell;
            cells[index] = new Rect(cellX, y, cellWidth, height);
        }
        return cells;
    }
}
