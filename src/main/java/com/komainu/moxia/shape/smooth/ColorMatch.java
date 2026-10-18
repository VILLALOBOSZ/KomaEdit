package com.komainu.moxia.shape.smooth;

/**
 * Distancia de color perceptual (CIELAB, ΔE de 1976) entre colores sRGB {@code 0xRRGGBB}. Se usa para elegir la
 * pieza del color más parecido cuando un bloque no tiene losa, escalera, muro o capa propios.
 */
public final class ColorMatch {
    private ColorMatch() {
    }

    /** Cuadrado de la ΔE entre dos colores {@code 0xRRGGBB}. */
    public static double distanceSquared(int first, int second) {
        double[] a = lab(first);
        double[] b = lab(second);
        double dl = a[0] - b[0];
        double da = a[1] - b[1];
        double db = a[2] - b[2];
        return dl * dl + da * da + db * db;
    }

    /** {L, a, b} con iluminante D65. */
    public static double[] lab(int rgb) {
        double r = linear(rgb >> 16 & 0xFF);
        double g = linear(rgb >> 8 & 0xFF);
        double b = linear(rgb & 0xFF);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[]{116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)};
    }

    public static int rgb(int red, int green, int blue) {
        return (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double f(double value) {
        return value > 216.0 / 24389.0 ? Math.cbrt(value) : (24389.0 / 27.0 * value + 16.0) / 116.0;
    }
}
