package com.komainu.moxia.color;

import com.komainu.moxia.glass.GlassColors;
import com.komainu.moxia.shape.smooth.ColorMatch;

import java.util.Locale;

/**
 * Colores {@code 0xRRGGBB} en OKLab (Ottosson, 2020), el espacio donde se comparan e interpolan los bloques: una
 * misma distancia se ve como el mismo salto de color en toda la gama, y un degradado en línea recta no pasa por
 * grises ni se oscurece a mitad. La conversión es la de {@link GlassColors}; aquí se trabaja con enteros RGB.
 *
 * <p>{@code L} va de 0 (negro) a 1 (blanco); {@code a} y {@code b}, de unos -0,4 a 0,4 (verde–rojo y azul–amarillo).
 * Una distancia de 0,02 apenas se nota; 0,1 ya es otro color.</p>
 */
public final class OkLab {
    private OkLab() {
    }

    /** {L, a, b} de un color {@code 0xRRGGBB}. */
    public static double[] of(int rgb) {
        return GlassColors.oklab(new double[]{rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF});
    }

    /** El color sRGB más cercano, recortado a la gama. */
    public static int rgb(double[] lab) {
        double[] channels = GlassColors.rgb(lab);
        return channel(channels[0]) << 16 | channel(channels[1]) << 8 | channel(channels[2]);
    }

    /** Luminosidad percibida {@code L} de un color. */
    public static double lightness(int rgb) {
        return of(rgb)[0];
    }

    /** {L, a, b} de CIELAB (D65), la que usa smoothblocks para elegir piezas. */
    public static double[] cielab(int rgb) {
        return ColorMatch.lab(rgb);
    }

    public static double distance(double[] first, double[] second) {
        return Math.sqrt(distanceSquared(first, second));
    }

    public static double distanceSquared(double[] first, double[] second) {
        double dl = first[0] - second[0];
        double da = first[1] - second[1];
        double db = first[2] - second[2];
        return dl * dl + da * da + db * db;
    }

    /** Punto a {@code t} (de 0 a 1) del camino recto entre dos colores. */
    public static double[] lerp(double[] from, double[] to, double t) {
        return new double[]{
                from[0] + (to[0] - from[0]) * t,
                from[1] + (to[1] - from[1]) * t,
                from[2] + (to[2] - from[2]) * t
        };
    }

    /** Saturación (croma) en OKLab: la distancia al gris de la misma luminosidad. */
    public static double chroma(double[] lab) {
        return Math.hypot(lab[1], lab[2]);
    }

    /** {@code #RRGGBB}, {@code RRGGBB}, {@code #RGB} o {@code 0xRRGGBB} → {@code 0xRRGGBB}. */
    public static int parseHex(String input) {
        double[] channels = GlassColors.hex(input);
        return (int) channels[0] << 16 | (int) channels[1] << 8 | (int) channels[2];
    }

    public static boolean isHex(String input) {
        String text = input.trim();
        return (text.startsWith("#") || text.toLowerCase(Locale.ROOT).startsWith("0x")) && GlassColors.isHex(text);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, 0.0, 255.0));
    }
}
