package com.komainu.moxia.terrain;

import java.util.Arrays;

/**
 * Mapa de alturas de una zona: la altura (con decimales) del bloque de suelo más alto de cada columna, qué columnas
 * se pueden cambiar y entre qué alturas. Las herramientas de terreno trabajan sobre esto y {@link TerrainWriter} lo
 * vuelve a pasar a bloques.
 *
 * <p>Una columna sin suelo vale {@code NaN}. Las columnas fijas (fuera de la selección o de la máscara) siguen en el
 * mapa para que las demás vean a sus vecinas, pero no se tocan.</p>
 */
public final class HeightField {
    private final int minimumX;
    private final int minimumZ;
    private final int sizeX;
    private final int sizeZ;
    private final double[] heights;
    private final boolean[] editable;
    private final double[] lowest;
    private final double[] highest;

    public HeightField(int minimumX, int minimumZ, int sizeX, int sizeZ) {
        if (sizeX <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("La zona está vacía");
        }
        this.minimumX = minimumX;
        this.minimumZ = minimumZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        int columns = Math.multiplyExact(sizeX, sizeZ);
        heights = new double[columns];
        editable = new boolean[columns];
        lowest = new double[columns];
        highest = new double[columns];
        Arrays.fill(heights, Double.NaN);
        Arrays.fill(lowest, Double.NEGATIVE_INFINITY);
        Arrays.fill(highest, Double.POSITIVE_INFINITY);
    }

    private HeightField(HeightField other) {
        minimumX = other.minimumX;
        minimumZ = other.minimumZ;
        sizeX = other.sizeX;
        sizeZ = other.sizeZ;
        heights = other.heights.clone();
        editable = other.editable.clone();
        lowest = other.lowest.clone();
        highest = other.highest.clone();
    }

    public HeightField copy() {
        return new HeightField(this);
    }

    public int minimumX() {
        return minimumX;
    }

    public int minimumZ() {
        return minimumZ;
    }

    public int maximumX() {
        return minimumX + sizeX - 1;
    }

    public int maximumZ() {
        return minimumZ + sizeZ - 1;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int columns() {
        return heights.length;
    }

    public boolean contains(int x, int z) {
        return x >= minimumX && x < minimumX + sizeX && z >= minimumZ && z < minimumZ + sizeZ;
    }

    /** Índice de la columna, o -1 fuera del mapa. */
    public int index(int x, int z) {
        return contains(x, z) ? (z - minimumZ) * sizeX + (x - minimumX) : -1;
    }

    public int xOf(int index) {
        return minimumX + index % sizeX;
    }

    public int zOf(int index) {
        return minimumZ + index / sizeX;
    }

    /** Altura de la columna; {@code NaN} fuera del mapa o sin suelo. */
    public double get(int x, int z) {
        int index = index(x, z);
        return index < 0 ? Double.NaN : heights[index];
    }

    public double at(int index) {
        return heights[index];
    }

    /** Cambia una columna editable, sin salirse de sus límites. Las fijas no cambian. */
    public void set(int x, int z, double height) {
        int index = index(x, z);
        if (index >= 0) {
            setAt(index, height);
        }
    }

    public void setAt(int index, double height) {
        if (editable[index] && !Double.isNaN(heights[index]) && !Double.isNaN(height)) {
            heights[index] = Math.clamp(height, lowest[index], highest[index]);
        }
    }

    /** Pone el valor tal cual, también en columnas fijas: solo para montar el mapa. */
    public void define(int x, int z, double height, boolean canEdit, double minimum, double maximum) {
        int index = index(x, z);
        if (index < 0) {
            throw new IllegalArgumentException("Columna fuera del mapa: " + x + ", " + z);
        }
        heights[index] = height;
        editable[index] = canEdit && !Double.isNaN(height);
        lowest[index] = minimum;
        highest[index] = maximum;
    }

    public boolean editable(int x, int z) {
        int index = index(x, z);
        return index >= 0 && editable[index];
    }

    public boolean editableAt(int index) {
        return editable[index];
    }

    public double lowestAt(int index) {
        return lowest[index];
    }

    public double highestAt(int index) {
        return highest[index];
    }

    /** Cuántas columnas se pueden editar. */
    public int editableColumns() {
        int count = 0;
        for (boolean value : editable) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    /** Suma de alturas de las columnas editables: el «volumen» que deben conservar las erosiones. */
    public double editableSum() {
        double sum = 0.0;
        for (int index = 0; index < heights.length; index++) {
            if (editable[index]) {
                sum += heights[index];
            }
        }
        return sum;
    }

    /**
     * Pendiente de la columna en grados (0 llano, 45 un bloque por bloque), con diferencias centradas a uno y dos
     * bloques. Las vecinas
     * sin suelo o fuera del mapa se sustituyen por la propia columna.
     */
    public double slopeDegrees(int x, int z) {
        double center = get(x, z);
        if (Double.isNaN(center)) {
            return 0.0;
        }
        // Media de la diferencia a un bloque y a dos: un escalón suelto no cuenta como cortado.
        double gradientX = (neighbour(x + 1, z, center) - neighbour(x - 1, z, center)) / 4.0
                + (neighbour(x + 2, z, center) - neighbour(x - 2, z, center)) / 8.0;
        double gradientZ = (neighbour(x, z + 1, center) - neighbour(x, z - 1, center)) / 4.0
                + (neighbour(x, z + 2, center) - neighbour(x, z - 2, center)) / 8.0;
        return Math.toDegrees(Math.atan(Math.sqrt(gradientX * gradientX + gradientZ * gradientZ)));
    }

    private double neighbour(int x, int z, double fallback) {
        double value = get(x, z);
        return Double.isNaN(value) ? fallback : value;
    }

    /** Altura interpolada en un punto con decimales (centros de bloque en {@code x + 0.5}); NaN si falta alguna. */
    public double sample(double x, double z) {
        double localX = x - 0.5;
        double localZ = z - 0.5;
        int x0 = (int) Math.floor(localX);
        int z0 = (int) Math.floor(localZ);
        double fractionX = localX - x0;
        double fractionZ = localZ - z0;
        double a = get(x0, z0);
        double b = get(x0 + 1, z0);
        double c = get(x0, z0 + 1);
        double d = get(x0 + 1, z0 + 1);
        double fallback = !Double.isNaN(a) ? a : !Double.isNaN(b) ? b : !Double.isNaN(c) ? c : d;
        if (Double.isNaN(fallback)) {
            return Double.NaN;
        }
        a = Double.isNaN(a) ? fallback : a;
        b = Double.isNaN(b) ? fallback : b;
        c = Double.isNaN(c) ? fallback : c;
        d = Double.isNaN(d) ? fallback : d;
        double top = a + (b - a) * fractionX;
        double bottom = c + (d - c) * fractionX;
        return top + (bottom - top) * fractionZ;
    }
}
