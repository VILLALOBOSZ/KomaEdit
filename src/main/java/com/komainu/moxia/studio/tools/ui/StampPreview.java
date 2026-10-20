package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.terrain.HeightStamp;
import com.komainu.moxia.terrain.StampLibrary;

/**
 * Miniatura de un sello de relieve: el mapa de alturas pintado como un mapa topográfico (verde abajo, roca y nieve
 * arriba) con sombreado de colina desde el noroeste, para que se lea la forma de un vistazo. El gris neutro queda a
 * la altura del terreno, así los sellos que hunden (cráter, dolina, valle) se ven hundidos.
 */
public final class StampPreview {
    private static final int[] RAMP = {0xFF2C3A2E, 0xFF3F6B3B, 0xFF6E8F45, 0xFF9C8A5E, 0xFF8E8C88, 0xFFE8E6EC};

    private StampPreview() {
    }

    /** Píxeles ARGB de {@code size}×{@code size}, por filas. */
    public static int[] argb(String name, int size) {
        HeightStamp stamp = StampLibrary.render(name, Math.max(8, size));
        int[] pixels = new int[size * size];
        double neutral = stamp.neutral();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                double u = column / (size - 1.0);
                double v = row / (size - 1.0);
                double height = stamp.sample(u, v);
                double step = 1.0 / size;
                double dx = stamp.sample(Math.min(1.0, u + step), v) - stamp.sample(Math.max(0.0, u - step), v);
                double dy = stamp.sample(u, Math.min(1.0, v + step)) - stamp.sample(u, Math.max(0.0, v - step));
                // Luz desde arriba a la izquierda: las laderas que miran al noroeste se aclaran.
                double shade = Math.clamp(1.0 - (dx + dy) * 6.0, 0.55, 1.35);
                double level = Math.clamp(0.35 + (height - neutral) * 0.75, 0.0, 1.0);
                pixels[row * size + column] = shade(color(level), shade);
            }
        }
        return pixels;
    }

    private static int color(double level) {
        double position = level * (RAMP.length - 1);
        int index = Math.min(RAMP.length - 2, (int) position);
        double t = position - index;
        int a = RAMP[index];
        int b = RAMP[index + 1];
        int red = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int green = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int blue = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int shade(int argb, double factor) {
        int red = Math.clamp((int) Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int green = Math.clamp((int) Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int blue = Math.clamp((int) Math.round((argb & 0xFF) * factor), 0, 255);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
