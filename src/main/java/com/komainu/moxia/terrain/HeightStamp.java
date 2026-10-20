package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampPlacement;

/**
 * Un sello de relieve: una imagen en escala de grises donde el blanco es lo más alto. {@code neutral} es el gris que
 * no cambia nada; por debajo, el sello hunde (cráteres, dolinas). Los valores van de 0 a 1.
 */
public final class HeightStamp {
    public static final int MAX_SIZE = 1_024;

    private final String name;
    private final int width;
    private final int height;
    private final float[] values;
    private final double neutral;

    public HeightStamp(String name, int width, int height, float[] values, double neutral) {
        if (width < 2 || height < 2 || width > MAX_SIZE || height > MAX_SIZE) {
            throw new IllegalArgumentException("El sello debe medir entre 2 y " + MAX_SIZE + " píxeles por lado");
        }
        if (values.length != width * height) {
            throw new IllegalArgumentException("El sello no tiene " + width + "×" + height + " valores");
        }
        if (neutral < 0.0 || neutral > 1.0) {
            throw new IllegalArgumentException("El gris neutro va de 0 a 1");
        }
        this.name = name;
        this.width = width;
        this.height = height;
        this.values = values;
        this.neutral = neutral;
    }

    public String name() {
        return name;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public double neutral() {
        return neutral;
    }

    public HeightStamp withNeutral(double value) {
        return new HeightStamp(name, width, height, values, value);
    }

    /** Valor del píxel (columna, fila), de 0 a 1. */
    public double pixel(int column, int row) {
        return values[Math.clamp(row, 0, height - 1) * width + Math.clamp(column, 0, width - 1)];
    }

    /**
     * Valor interpolado en coordenadas de la imagen normalizadas: {@code u} de izquierda (0) a derecha (1) y
     * {@code v} de arriba (0) a abajo (1). Fuera de la imagen vale el gris neutro.
     */
    public double sample(double u, double v) {
        if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0 || Double.isNaN(u) || Double.isNaN(v)) {
            return neutral;
        }
        double px = u * (width - 1);
        double py = v * (height - 1);
        int x0 = Math.min((int) px, width - 2);
        int y0 = Math.min((int) py, height - 2);
        double fx = px - x0;
        double fy = py - y0;
        double a = values[y0 * width + x0];
        double b = values[y0 * width + x0 + 1];
        double c = values[(y0 + 1) * width + x0];
        double d = values[(y0 + 1) * width + x0 + 1];
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    /**
     * Relieve del sello en un punto del mundo relativo a su centro, ya escalado y girado: de -1 a 1 respecto al gris
     * neutro (lo normal es de 0 a 1). El sello ocupa un cuadrado de lado {@code 2·radio}; {@code angle} es el giro
     * en radianes, con 0 = la parte de arriba de la imagen hacia el norte (-Z) y creciendo en sentido horario visto
     * desde arriba, como la orientación de un jugador.
     */
    public double relief(double dx, double dz, double radius, double angle) {
        return StampPlacement.of(radius, angle).relief(this, dx, dz);
    }

    /**
     * Relieve en coordenadas de la imagen ({@code u}, {@code v} de 0 a 1, como {@link #sample}): de -1 a 1 respecto
     * al gris neutro. Fuera de la imagen, 0.
     */
    public double reliefAt(double u, double v) {
        double value = sample(u, v) - neutral;
        double span = value >= 0.0 ? 1.0 - neutral : neutral;
        return span <= 0.0 ? 0.0 : value / span;
    }

    /** Valores en bruto, por filas (para guardarlo como PNG). */
    float[] values() {
        return values;
    }
}
