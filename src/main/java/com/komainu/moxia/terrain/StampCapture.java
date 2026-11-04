package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampCategory;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;

/**
 * Convierte la altura de la superficie de un trozo de mundo en un sello: lo que hace {@code /komaedit sello capturar}
 * en el worker, sin Minecraft.
 *
 * <ol>
 *     <li><b>Sin escalones.</b> El mundo da alturas enteras; tal cual, al estampar el sello más grande o más alto cada
 *     escalón de un bloque se volvería una terraza. Se suaviza con la condición de no apartarse más de medio bloque de
 *     lo leído: las cuestas quedan como rampas lisas y los cortados siguen siendo cortados.</li>
 *     <li><b>El nivel base</b> es la mediana del borde (lo que rodea al relieve): lo que sube por encima levanta y lo
 *     que queda por debajo hunde, en la misma proporción ({@link #NEUTRAL_BOTH} de gris neutro cuando hay de las dos
 *     cosas).</li>
 *     <li><b>El borde se funde</b>: en una franja junto al borde de la selección (o de su forma, si no es una caja) el
 *     relieve baja suave hasta el nivel base, así el sello no deja un escalón cuadrado al estamparlo.</li>
 *     <li><b>Cuadrado</b>: los sellos se estiran a un cuadrado; uno alargado se centra con margen neutro para no
 *     deformarlo.</li>
 * </ol>
 *
 * <p>Se guarda en un PNG de 16 bits: con el gris neutro en medio, más de 80 niveles por bloque en un relieve de 384,
 * de sobra para que al escalarlo no aparezcan escalones.</p>
 */
public final class StampCapture {
    public static final int NONE = Integer.MIN_VALUE;
    /** Lado mínimo de lo que se captura, en columnas. */
    public static final int MIN_SIDE = 8;
    /** Lado máximo de lo que se captura (y del PNG, un píxel por columna). */
    public static final int MAX_SIDE = 512;
    /** Gris neutro de los sellos que suben y bajan. */
    public static final double NEUTRAL_BOTH = 0.5;
    /** Pasadas del suavizado que quita los escalones. */
    private static final int SMOOTHING_PASSES = 28;

    private StampCapture() {
    }

    /** Lo leído del mundo, columna a columna, por filas de norte a sur. */
    public static final class Columns {
        private final int sizeX;
        private final int sizeZ;
        private final int[] surface;
        private final boolean[] water;
        private final String[] top;

        public Columns(int sizeX, int sizeZ) {
            if (sizeX < MIN_SIDE || sizeZ < MIN_SIDE) {
                throw new IllegalArgumentException("Selecciona al menos " + MIN_SIDE + " × " + MIN_SIDE
                        + " columnas de terreno");
            }
            if (sizeX > MAX_SIDE || sizeZ > MAX_SIDE) {
                throw new IllegalArgumentException("La selección mide " + sizeX + " × " + sizeZ + " columnas; un sello"
                        + " se captura de " + MAX_SIDE + " × " + MAX_SIDE + " como mucho");
            }
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            surface = new int[sizeX * sizeZ];
            Arrays.fill(surface, NONE);
            water = new boolean[sizeX * sizeZ];
            top = new String[sizeX * sizeZ];
        }

        public int sizeX() {
            return sizeX;
        }

        public int sizeZ() {
            return sizeZ;
        }

        public int count() {
            return surface.length;
        }

        /**
         * @param x       columna desde el oeste
         * @param z       fila desde el norte
         * @param surface altura del suelo, o {@link #NONE} si no hay o queda fuera de la forma de la selección
         * @param water   si había agua encima del suelo
         * @param block   el bloque de arriba (para elegir la paleta), o {@code null}
         */
        public void set(int x, int z, int surface, boolean water, String block) {
            int index = z * sizeX + x;
            this.surface[index] = surface;
            this.water[index] = water;
            this.top[index] = block;
        }

        public int surface(int x, int z) {
            return surface[z * sizeX + x];
        }
    }

    /**
     * El sello capturado y lo que se sabe de él.
     *
     * @param above    bloques que sube lo más alto por encima del nivel base
     * @param below    bloques que baja lo más hondo
     * @param base     el nivel base (la mediana del borde), en Y
     * @param category la de la miniatura y la paleta automática, según lo que había arriba
     */
    public record Result(HeightStamp stamp, double above, double below, double base, StampCategory category,
                         boolean hadWater) {
        /** La altura natural respecto al radio: la de verdad, para que al estamparlo salga con sus proporciones. */
        public double heightRatio() {
            return Math.max(above, below) / (stamp.width() / 2.0);
        }
    }

    /**
     * @param category la que pidió el jugador, o {@code null} para elegirla por lo que había en la superficie
     */
    public static Result build(String name, Columns columns, StampCategory category, Runnable checkCancelled) {
        int sizeX = columns.sizeX;
        int sizeZ = columns.sizeZ;
        int count = sizeX * sizeZ;
        int known = 0;
        for (int value : columns.surface) {
            if (value != NONE) {
                known++;
            }
        }
        if (known < count / 4) {
            throw new IllegalArgumentException("En la selección casi no hay suelo que leer (" + known + " de " + count
                    + " columnas)");
        }
        double[] raw = new double[count];
        for (int index = 0; index < count; index++) {
            raw[index] = columns.surface[index] == NONE ? Double.NaN : columns.surface[index];
        }
        double[] smooth = withoutSteps(raw, sizeX, sizeZ, checkCancelled);
        // Hasta dónde llega lo leído: el borde de la caja o el de la forma de la selección.
        double[] inside = distanceInside(raw, sizeX, sizeZ);
        int ring = Math.max(2, (int) Math.round(Math.min(sizeX, sizeZ) * 0.06));
        double base = borderMedian(smooth, inside, ring);
        double feather = Math.max(3.0, Math.min(sizeX, sizeZ) * 0.12);
        double above = 0.0;
        double below = 0.0;
        double[] relief = new double[count];
        for (int index = 0; index < count; index++) {
            if (Double.isNaN(smooth[index])) {
                continue;
            }
            double weight = fade(inside[index] / feather);
            relief[index] = (smooth[index] - base) * weight;
            above = Math.max(above, relief[index]);
            below = Math.max(below, -relief[index]);
        }
        checkCancelled.run();
        // Lo que apenas asoma de un lado no merece la mitad de los grises: un sello que solo sube va de 0 a 1.
        double neutral = below < 0.75 ? 0.0 : above < 0.75 ? 1.0 : NEUTRAL_BOTH;
        if (neutral == 0.0) {
            below = 0.0;
        } else if (neutral == 1.0) {
            above = 0.0;
        }
        double span = Math.max(1.0, Math.max(above, below));
        int side = Math.max(sizeX, sizeZ);
        int offsetX = (side - sizeX) / 2;
        int offsetZ = (side - sizeZ) / 2;
        float[] values = new float[side * side];
        Arrays.fill(values, (float) neutral);
        double scale = neutral == NEUTRAL_BOTH ? 0.5 : 1.0;
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int index = z * sizeX + x;
                double value = Double.isNaN(smooth[index]) ? neutral
                        : neutral + Math.clamp(relief[index] / span, neutral == 0.0 ? 0.0 : -1.0,
                        neutral == 1.0 ? 0.0 : 1.0) * scale;
                values[(z + offsetZ) * side + x + offsetX] = (float) Math.clamp(value, 0.0, 1.0);
            }
        }
        HeightStamp stamp = new HeightStamp(name, side, side, values, neutral);
        boolean hadWater = false;
        for (boolean wet : columns.water) {
            hadWater |= wet;
        }
        double ratio = Math.max(above, below) / (side / 2.0);
        StampCategory chosen = category != null ? category : guess(columns, ratio);
        return new Result(stamp, above, below, base, chosen, hadWater);
    }

    /**
     * Suavizado que no se aparta más de medio bloque de lo leído (el redondeo de la altura): converge a la superficie
     * lisa que da esas mismas alturas enteras. Las columnas sin suelo no cuentan.
     */
    static double[] withoutSteps(double[] raw, int sizeX, int sizeZ, Runnable checkCancelled) {
        double[] current = raw.clone();
        double[] next = new double[raw.length];
        for (int pass = 0; pass < SMOOTHING_PASSES; pass++) {
            checkCancelled.run();
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int index = z * sizeX + x;
                    double own = raw[index];
                    if (Double.isNaN(own)) {
                        next[index] = Double.NaN;
                        continue;
                    }
                    double sum = 0.0;
                    double weight = 0.0;
                    for (int dz = -1; dz <= 1; dz++) {
                        int nz = z + dz;
                        if (nz < 0 || nz >= sizeZ) {
                            continue;
                        }
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            if (nx < 0 || nx >= sizeX) {
                                continue;
                            }
                            double value = current[nz * sizeX + nx];
                            if (Double.isNaN(value)) {
                                continue;
                            }
                            double w = (dx == 0 ? 2.0 : 1.0) * (dz == 0 ? 2.0 : 1.0);
                            sum += value * w;
                            weight += w;
                        }
                    }
                    double average = sum / weight;
                    next[index] = Math.clamp(average, own - 0.5, own + 0.5);
                }
            }
            double[] swap = current;
            current = next;
            next = swap;
        }
        return current;
    }

    /** Pasos (en columnas) hasta lo que no se leyó o el borde de la caja: 1 en el borde. */
    static double[] distanceInside(double[] raw, int sizeX, int sizeZ) {
        int count = raw.length;
        double[] distance = new double[count];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int index = z * sizeX + x;
                if (Double.isNaN(raw[index])) {
                    distance[index] = 0.0;
                    queue.add(index);
                } else if (x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1) {
                    distance[index] = 1.0;
                    queue.add(index);
                }
            }
        }
        // Distancia en pasos de rey (8 vecinos): basta para una franja de fundido.
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int x = index % sizeX;
            int z = index / sizeX;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int nz = z + dz;
                    if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ) {
                        continue;
                    }
                    int neighbour = nz * sizeX + nx;
                    double step = dx != 0 && dz != 0 ? 1.4142 : 1.0;
                    if (distance[index] + step < distance[neighbour] - 1e-9) {
                        distance[neighbour] = distance[index] + step;
                        queue.add(neighbour);
                    }
                }
            }
        }
        return distance;
    }

    /** La mediana de las alturas de la franja del borde (las columnas a {@code ring} pasos o menos). */
    static double borderMedian(double[] heights, double[] inside, int ring) {
        int count = 0;
        double[] values = new double[heights.length];
        for (int index = 0; index < heights.length; index++) {
            if (!Double.isNaN(heights[index]) && inside[index] <= ring) {
                values[count++] = heights[index];
            }
        }
        if (count == 0) {
            for (double height : heights) {
                if (!Double.isNaN(height)) {
                    values[count++] = height;
                }
            }
        }
        Arrays.sort(values, 0, count);
        return count % 2 == 1 ? values[count / 2] : (values[count / 2 - 1] + values[count / 2]) / 2.0;
    }

    private static double fade(double t) {
        double clamped = Math.clamp(t, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    /**
     * La categoría por lo que había arriba: con mucha agua, isla; nieve, glaciar; arena roja o terracota, meseta;
     * arena, dunas; basalto o piedra negra, volcán; si no, montaña cuando es alto y roca o colina cuando no.
     */
    static StampCategory guess(Columns columns, double heightRatio) {
        int total = 0;
        int water = 0;
        int snow = 0;
        int mesa = 0;
        int sand = 0;
        int volcanic = 0;
        int rock = 0;
        for (int index = 0; index < columns.surface.length; index++) {
            if (columns.surface[index] == NONE) {
                continue;
            }
            total++;
            if (columns.water[index]) {
                water++;
            }
            String id = columns.top[index] == null ? "" : columns.top[index].toLowerCase(Locale.ROOT);
            if (id.contains("snow") || id.contains("ice")) {
                snow++;
            } else if (id.contains("red_sand") || id.contains("terracotta")) {
                mesa++;
            } else if (id.contains("sand")) {
                sand++;
            } else if (id.contains("basalt") || id.contains("blackstone") || id.contains("magma")) {
                volcanic++;
            } else if (id.contains("stone") || id.contains("andesite") || id.contains("diorite")
                    || id.contains("granite") || id.contains("tuff") || id.contains("gravel")
                    || id.contains("calcite") || id.contains("cobble")) {
                rock++;
            }
        }
        if (total == 0) {
            return StampCategory.HILLS;
        }
        boolean tall = heightRatio > 0.45;
        if (water >= total * 0.15) {
            return StampCategory.ISLANDS;
        }
        if (snow >= total * 0.35) {
            return StampCategory.GLACIAL;
        }
        if (mesa >= total * 0.3) {
            return StampCategory.PLATEAUS;
        }
        if (sand >= total * 0.4) {
            return StampCategory.DUNES;
        }
        if (volcanic >= total * 0.3) {
            return StampCategory.VOLCANOES;
        }
        if (rock >= total * 0.45) {
            return tall ? StampCategory.MOUNTAINS : StampCategory.ROCKS;
        }
        return tall ? StampCategory.MOUNTAINS : StampCategory.HILLS;
    }
}
