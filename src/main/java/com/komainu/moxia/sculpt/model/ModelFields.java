package com.komainu.moxia.sculpt.model;

import java.util.Arrays;

/**
 * Campos continuos sobre una {@link ModelGrid}: desenfoque gaussiano separable, lectura trilineal, distancia con
 * signo exacta a la superficie y ruido de valor 3D determinista. Todo en arrays planos con el orden de la rejilla.
 */
final class ModelFields {
    /** Distancia de las celdas que no ven ninguna de la otra clase dentro de la caja. */
    static final float FAR = 1.0E4F;
    private static final float FAR_SQUARED = FAR * FAR;

    private ModelFields() {
    }

    /**
     * Desenfoque gaussiano de desviación {@code sigma} bloques, separable y con la ventana recortada (y
     * renormalizada) en los bordes de la caja: el borde de la captura no se lee como aire.
     */
    static float[] gaussian(ModelGrid grid, float[] values, double sigma) {
        float[] result = values.clone();
        if (sigma <= 0.0) {
            return result;
        }
        int radius = Math.max(1, (int) Math.ceil(sigma * 2.5));
        double[] kernel = new double[radius * 2 + 1];
        for (int offset = -radius; offset <= radius; offset++) {
            kernel[offset + radius] = Math.exp(-(offset * offset) / (2.0 * sigma * sigma));
        }
        int sizeX = grid.sizeX;
        int sizeY = grid.sizeY;
        int sizeZ = grid.sizeZ;
        double[] line = new double[Math.max(sizeX, Math.max(sizeY, sizeZ))];
        for (int row = 0; row < sizeY * sizeZ; row++) {
            blurLine(result, row * sizeX, 1, sizeX, kernel, radius, line);
        }
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                blurLine(result, y * sizeZ * sizeX + x, sizeX, sizeZ, kernel, radius, line);
            }
        }
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                blurLine(result, z * sizeX + x, sizeX * sizeZ, sizeY, kernel, radius, line);
            }
        }
        return result;
    }

    private static void blurLine(float[] values, int start, int stride, int length, double[] kernel, int radius,
                                 double[] line) {
        for (int position = 0; position < length; position++) {
            line[position] = values[start + position * stride];
        }
        for (int position = 0; position < length; position++) {
            double sum = 0.0;
            double weight = 0.0;
            int low = Math.max(0, position - radius);
            int high = Math.min(length - 1, position + radius);
            for (int other = low; other <= high; other++) {
                double factor = kernel[other - position + radius];
                sum += line[other] * factor;
                weight += factor;
            }
            values[start + position * stride] = (float) (sum / weight);
        }
    }

    /** Valor de una celda con las coordenadas recortadas a la caja. */
    static float at(ModelGrid grid, float[] values, int x, int y, int z) {
        int cx = Math.clamp(x, grid.minimumX, grid.minimumX + grid.sizeX - 1);
        int cy = Math.clamp(y, grid.minimumY, grid.minimumY + grid.sizeY - 1);
        int cz = Math.clamp(z, grid.minimumZ, grid.minimumZ + grid.sizeZ - 1);
        return values[grid.index(cx, cy, cz)];
    }

    /** Interpolación trilineal en coordenadas de mundo: el centro del bloque {@code x} está en {@code x + 0,5}. */
    static double sample(ModelGrid grid, float[] values, double x, double y, double z) {
        double u = x - 0.5;
        double v = y - 0.5;
        double w = z - 0.5;
        int x0 = (int) Math.floor(u);
        int y0 = (int) Math.floor(v);
        int z0 = (int) Math.floor(w);
        double fx = u - x0;
        double fy = v - y0;
        double fz = w - z0;
        double c00 = lerp(at(grid, values, x0, y0, z0), at(grid, values, x0 + 1, y0, z0), fx);
        double c10 = lerp(at(grid, values, x0, y0 + 1, z0), at(grid, values, x0 + 1, y0 + 1, z0), fx);
        double c01 = lerp(at(grid, values, x0, y0, z0 + 1), at(grid, values, x0 + 1, y0, z0 + 1), fx);
        double c11 = lerp(at(grid, values, x0, y0 + 1, z0 + 1), at(grid, values, x0 + 1, y0 + 1, z0 + 1), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    /**
     * Distancia con signo a la superficie, en bloques, medida entre centros: positiva fuera (en las libres),
     * negativa dentro. Una celda libre pegada a la masa vale {@code 0,5} y una con masa junto al aire,
     * {@code −0,5}; la superficie queda en el 0. Exacta (euclídea) con la transformada de Felzenszwalb.
     */
    static float[] signedDistance(ModelGrid grid) {
        int total = grid.size();
        boolean[] solid = new boolean[total];
        boolean[] free = new boolean[total];
        for (int index = 0; index < total; index++) {
            solid[index] = grid.material[index] != ModelGrid.FREE;
            free[index] = !solid[index];
        }
        float[] toSolid = squaredDistance(grid, solid);
        float[] toFree = squaredDistance(grid, free);
        float[] result = new float[total];
        for (int index = 0; index < total; index++) {
            if (solid[index]) {
                float distance = toFree[index] >= FAR_SQUARED ? FAR : (float) Math.sqrt(toFree[index]);
                result[index] = 0.5F - distance;
            } else {
                float distance = toSolid[index] >= FAR_SQUARED ? FAR : (float) Math.sqrt(toSolid[index]);
                result[index] = distance - 0.5F;
            }
        }
        return result;
    }

    /** Distancia euclídea al cuadrado a la celda marcada más cercana ({@code FAR²} si no hay ninguna). */
    static float[] squaredDistance(ModelGrid grid, boolean[] seeds) {
        int sizeX = grid.sizeX;
        int sizeY = grid.sizeY;
        int sizeZ = grid.sizeZ;
        float[] values = new float[seeds.length];
        for (int index = 0; index < values.length; index++) {
            values[index] = seeds[index] ? 0.0F : FAR_SQUARED;
        }
        int longest = Math.max(sizeX, Math.max(sizeY, sizeZ));
        double[] line = new double[longest];
        double[] output = new double[longest];
        int[] hull = new int[longest];
        double[] bounds = new double[longest + 1];
        for (int row = 0; row < sizeY * sizeZ; row++) {
            transformLine(values, row * sizeX, 1, sizeX, line, output, hull, bounds);
        }
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                transformLine(values, y * sizeZ * sizeX + x, sizeX, sizeZ, line, output, hull, bounds);
            }
        }
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                transformLine(values, z * sizeX + x, sizeX * sizeZ, sizeY, line, output, hull, bounds);
            }
        }
        return values;
    }

    /** Transformada de distancia 1D al cuadrado (envolvente inferior de parábolas, Felzenszwalb y Huttenlocher). */
    private static void transformLine(float[] values, int start, int stride, int length, double[] line,
                                      double[] output, int[] hull, double[] bounds) {
        for (int position = 0; position < length; position++) {
            line[position] = values[start + position * stride];
        }
        int count = 0;
        hull[0] = 0;
        bounds[0] = Double.NEGATIVE_INFINITY;
        bounds[1] = Double.POSITIVE_INFINITY;
        for (int position = 1; position < length; position++) {
            double intersection = intersection(line, hull[count], position);
            while (intersection <= bounds[count]) {
                count--;
                intersection = intersection(line, hull[count], position);
            }
            count++;
            hull[count] = position;
            bounds[count] = intersection;
            bounds[count + 1] = Double.POSITIVE_INFINITY;
        }
        int segment = 0;
        for (int position = 0; position < length; position++) {
            while (bounds[segment + 1] < position) {
                segment++;
            }
            int nearest = hull[segment];
            double delta = position - nearest;
            output[position] = delta * delta + line[nearest];
        }
        for (int position = 0; position < length; position++) {
            values[start + position * stride] = (float) Math.min(output[position], FAR_SQUARED);
        }
    }

    private static double intersection(double[] line, int first, int second) {
        return ((line[second] + (double) second * second) - (line[first] + (double) first * first))
                / (2.0 * second - 2.0 * first);
    }

    /** Ruido de valor 3D en [−1, 1] con rejilla de lado {@code cell} bloques, suavizado (quíntica). */
    static double valueNoise(double x, double y, double z, double cell, long seed) {
        double u = x / cell;
        double v = y / cell;
        double w = z / cell;
        int x0 = (int) Math.floor(u);
        int y0 = (int) Math.floor(v);
        int z0 = (int) Math.floor(w);
        double fx = fade(u - x0);
        double fy = fade(v - y0);
        double fz = fade(w - z0);
        double c00 = lerp(signed(x0, y0, z0, seed), signed(x0 + 1, y0, z0, seed), fx);
        double c10 = lerp(signed(x0, y0 + 1, z0, seed), signed(x0 + 1, y0 + 1, z0, seed), fx);
        double c01 = lerp(signed(x0, y0, z0 + 1, seed), signed(x0 + 1, y0, z0 + 1, seed), fx);
        double c11 = lerp(signed(x0, y0 + 1, z0 + 1, seed), signed(x0 + 1, y0 + 1, z0 + 1, seed), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    /** Suma de octavas de {@link #valueNoise}, normalizada a [−1, 1]. */
    static double fractal(double x, double y, double z, double cell, int octaves, long seed) {
        double sum = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        double size = cell;
        for (int octave = 0; octave < octaves; octave++) {
            sum += valueNoise(x, y, z, Math.max(0.5, size), seed + octave * 0x632BE59BD9B4E019L) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            size *= 0.5;
        }
        return sum / total;
    }

    /** Número en [0, 1) fijo para cada celda entera y semilla. */
    static double unit(long x, long y, long z, long seed) {
        long hash = mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ seed);
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static double signed(int x, int y, int z, long seed) {
        return unit(x, y, z, seed) * 2.0 - 1.0;
    }

    static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    /** Unión suave de dos distancias con signo (mínimo polinómico de radio {@code k}). */
    static double smoothMin(double first, double second, double k) {
        if (k <= 1.0E-9) {
            return Math.min(first, second);
        }
        double h = Math.clamp(0.5 + 0.5 * (second - first) / k, 0.0, 1.0);
        return lerp(second, first, h) - k * h * (1.0 - h);
    }

    /** Resta suave: lo de {@code first} sin lo de {@code second}. */
    static double smoothSubtract(double first, double second, double k) {
        return -smoothMin(-first, second, k);
    }

    static float[] fill(int size, float value) {
        float[] values = new float[size];
        Arrays.fill(values, value);
        return values;
    }
}
