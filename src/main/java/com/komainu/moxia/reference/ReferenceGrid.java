package com.komainu.moxia.reference;

import java.util.ArrayList;
import java.util.List;

/**
 * La rejilla encima de una imagen de referencia, para contar bloques al calcar: una línea cada {@code pixels}
 * píxeles de la imagen (una celda = un bloque) y, si se quiere, una más marcada cada {@code major} celdas. El
 * desplazamiento la alinea con el pixel art cuando la imagen trae un borde.
 *
 * @param pixels  píxeles de la imagen por celda
 * @param major   cada cuántas celdas va una línea marcada (0, ninguna)
 * @param offsetX dónde empieza la primera columna, en píxeles (de 0 a {@code pixels - 1})
 * @param offsetY dónde empieza la primera fila
 */
public record ReferenceGrid(boolean enabled, int pixels, int major, int offsetX, int offsetY, Color color) {
    public static final int MIN_PIXELS = 1;
    public static final int MAX_PIXELS = 512;
    public static final int MAX_MAJOR = 64;
    /** Con más líneas que estas por eje no se distingue nada: se dibujan solo las marcadas (o ninguna). */
    public static final int MAX_LINES = 1_024;
    public static final ReferenceGrid DEFAULT = new ReferenceGrid(false, 16, 0, 0, 0, Color.WHITE);

    /** Colores de la rejilla: claros y oscuros, para que se vea sobre cualquier foto. */
    public enum Color {
        WHITE("white", 0xFFFFFF),
        BLACK("black", 0x111111),
        RED("red", 0xFF3B3B),
        CYAN("cyan", 0x3BE8FF);

        private final String id;
        private final int rgb;

        Color(String id, int rgb) {
            this.id = id;
            this.rgb = rgb;
        }

        public String id() {
            return id;
        }

        public int rgb() {
            return rgb;
        }

        public Color next() {
            Color[] colors = values();
            return colors[(ordinal() + 1) % colors.length];
        }

        public static Color byId(String id) {
            for (Color color : values()) {
                if (color.id.equalsIgnoreCase(id)) {
                    return color;
                }
            }
            return WHITE;
        }
    }

    /** Una línea de la rejilla: vertical (en {@code u}) u horizontal (en {@code v}), en píxeles de la imagen. */
    public record Line(boolean vertical, double at, boolean major) {
    }

    public ReferenceGrid {
        pixels = Math.clamp(pixels, MIN_PIXELS, MAX_PIXELS);
        major = Math.clamp(major, 0, MAX_MAJOR);
        offsetX = Math.floorMod(offsetX, pixels);
        offsetY = Math.floorMod(offsetY, pixels);
        color = color == null ? Color.WHITE : color;
    }

    public ReferenceGrid withEnabled(boolean value) {
        return new ReferenceGrid(value, pixels, major, offsetX, offsetY, color);
    }

    public ReferenceGrid withPixels(int value) {
        return new ReferenceGrid(enabled, value, major, offsetX, offsetY, color);
    }

    public ReferenceGrid withMajor(int value) {
        return new ReferenceGrid(enabled, pixels, value, offsetX, offsetY, color);
    }

    public ReferenceGrid withOffset(int x, int y) {
        return new ReferenceGrid(enabled, pixels, major, x, y, color);
    }

    public ReferenceGrid withColor(Color value) {
        return new ReferenceGrid(enabled, pixels, major, offsetX, offsetY, value);
    }

    /** Columnas de celdas que cubren la imagen (la tira de antes del desplazamiento cuenta como una). */
    public int columns(int pixelWidth) {
        return cells(Math.max(1, pixelWidth), offsetX);
    }

    public int rows(int pixelHeight) {
        return cells(Math.max(1, pixelHeight), offsetY);
    }

    private int cells(int size, int offset) {
        int whole = (int) Math.ceil((size - offset) / (double) pixels);
        return Math.max(1, whole + (offset > 0 ? 1 : 0));
    }

    /**
     * La columna de un píxel, empezando en 1 como se cuenta a mano. La tira de antes del desplazamiento es la 1;
     * fuera de la imagen, 0.
     */
    public int column(double u, int pixelWidth) {
        return cell(u, Math.max(1, pixelWidth), offsetX);
    }

    public int row(double v, int pixelHeight) {
        return cell(v, Math.max(1, pixelHeight), offsetY);
    }

    private int cell(double at, int size, int offset) {
        if (!(at >= 0.0) || at >= size) {
            return 0;
        }
        return (int) Math.floor((at - offset) / pixels) + (offset > 0 ? 2 : 1);
    }

    /**
     * Las líneas de dentro de la imagen (los bordes no: la imagen ya los tiene). Si hay demasiadas para verse, solo
     * las marcadas, y si aun así sobran, ninguna.
     */
    public List<Line> lines(int pixelWidth, int pixelHeight) {
        int width = Math.max(1, pixelWidth);
        int height = Math.max(1, pixelHeight);
        boolean dense = width / pixels > MAX_LINES || height / pixels > MAX_LINES;
        if (dense && (major == 0 || width / (pixels * major) > MAX_LINES || height / (pixels * major) > MAX_LINES)) {
            return List.of();
        }
        List<Line> lines = new ArrayList<>();
        axis(lines, true, width, offsetX, dense);
        axis(lines, false, height, offsetY, dense);
        return lines;
    }

    private void axis(List<Line> lines, boolean vertical, int size, int offset, boolean onlyMajor) {
        for (int index = 0; offset + (long) index * pixels < size; index++) {
            int at = offset + index * pixels;
            boolean marked = major > 0 && index % major == 0;
            if (at > 0 && (marked || !onlyMajor)) {
                lines.add(new Line(vertical, at, marked));
            }
        }
    }

    /** Si las celdas se ven: al menos {@code minimum} unidades de pantalla (o de bloque) por celda. */
    public boolean visibleAt(double unitsPerPixel, double minimum) {
        return pixels * unitsPerPixel >= minimum;
    }
}
