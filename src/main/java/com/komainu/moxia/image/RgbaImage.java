package com.komainu.moxia.image;

/**
 * Imagen en memoria con un {@code 0xAARRGGBB} por píxel, fila a fila desde arriba a la izquierda. Es la forma común
 * de todo el proceso de {@code /komaedit image}: decodificar, ajustar, reescalar y cuantizar.
 */
public record RgbaImage(int width, int height, int[] argb) {
    public RgbaImage {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("La imagen no tiene píxeles");
        }
        if (argb.length != width * height) {
            throw new IllegalArgumentException("La imagen no cuadra: " + argb.length + " píxeles para " + width + "×"
                    + height);
        }
    }

    public static RgbaImage blank(int width, int height) {
        return new RgbaImage(width, height, new int[width * height]);
    }

    public int get(int x, int y) {
        return argb[y * width + x];
    }

    public void set(int x, int y, int value) {
        argb[y * width + x] = value;
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }
}
