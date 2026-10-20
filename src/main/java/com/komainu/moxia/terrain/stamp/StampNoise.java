package com.komainu.moxia.terrain.stamp;

/**
 * Ruido de gradiente para generar los sellos. Es un Perlin con 64 direcciones de gradiente (el de
 * {@code NoiseEngine} usa 8 y deja crestas alineadas con los ejes, que en una montaña se notan) y las variantes que
 * hacen falta para el relieve: fBm, crestas multifractales de Musgrave y «billow». Solo usa sumas, productos y una
 * tabla calculada con {@link StrictMath}: el mismo sello sale igual en cualquier máquina.
 */
final class StampNoise {
    private static final int DIRECTIONS = 64;
    private static final double[] GRADIENT_X = new double[DIRECTIONS];
    private static final double[] GRADIENT_Y = new double[DIRECTIONS];
    /** El Perlin con gradientes unitarios llega a ±√½; se estira a ±1. */
    private static final double SCALE = 1.41;

    static {
        for (int index = 0; index < DIRECTIONS; index++) {
            double angle = index * 2.0 * StrictMath.PI / DIRECTIONS;
            GRADIENT_X[index] = StrictMath.cos(angle);
            GRADIENT_Y[index] = StrictMath.sin(angle);
        }
    }

    private StampNoise() {
    }

    /** Ruido de -1 a 1 (casi nunca llega a los extremos). */
    static double noise(double x, double y, long seed) {
        int x0 = floor(x);
        int y0 = floor(y);
        double fx = x - x0;
        double fy = y - y0;
        double u = fade(fx);
        double v = fade(fy);
        double a = dot(hash(x0, y0, seed), fx, fy);
        double b = dot(hash(x0 + 1, y0, seed), fx - 1.0, fy);
        double c = dot(hash(x0, y0 + 1, seed), fx, fy - 1.0);
        double d = dot(hash(x0 + 1, y0 + 1, seed), fx - 1.0, fy - 1.0);
        double top = a + (b - a) * u;
        double bottom = c + (d - c) * u;
        return Math.clamp((top + (bottom - top) * v) * SCALE, -1.0, 1.0);
    }

    /** fBm de -1 a 1: {@code octaves} capas, cada una al doble de frecuencia y la mitad de fuerza. */
    static double fbm(double x, double y, long seed, int octaves) {
        return fbm(x, y, seed, octaves, 0.5);
    }

    static double fbm(double x, double y, long seed, int octaves, double gain) {
        double sum = 0.0;
        double amplitude = 1.0;
        double norm = 0.0;
        double frequency = 1.0;
        for (int octave = 0; octave < octaves; octave++) {
            // Cada capa girada un poco: así no se apilan las rejillas de las octavas.
            double angle = octave * 0.618;
            double rx = x * COS[octave & 7] - y * SIN[octave & 7];
            double ry = x * SIN[octave & 7] + y * COS[octave & 7];
            sum += noise(rx * frequency + angle * 13.1, ry * frequency - angle * 7.3, seed + octave * 1_013L)
                    * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= 2.0;
        }
        return sum / norm;
    }

    /**
     * Crestas multifractales (Musgrave): de 0 a 1, crestas afiladas y valles suaves. Cada octava pesa más donde la
     * anterior ya era cresta, así el detalle se concentra en las cumbres y no ensucia los valles.
     */
    static double ridged(double x, double y, long seed, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double norm = 0.0;
        double frequency = 1.0;
        double weight = 1.0;
        for (int octave = 0; octave < octaves; octave++) {
            double rx = x * COS[octave & 7] - y * SIN[octave & 7];
            double ry = x * SIN[octave & 7] + y * COS[octave & 7];
            double signal = 1.0 - Math.abs(noise(rx * frequency, ry * frequency, seed + octave * 2_027L));
            signal *= signal;
            signal *= weight;
            weight = Math.clamp(signal * 2.0, 0.0, 1.0);
            sum += signal * amplitude;
            norm += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / norm;
    }

    /** Ruido «billow» de 0 a 1: lomas redondas separadas por pliegues. */
    static double billow(double x, double y, long seed, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double norm = 0.0;
        double frequency = 1.0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += Math.abs(noise(x * frequency, y * frequency, seed + octave * 3_061L)) * amplitude;
            norm += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / norm;
    }

    /** Un número de 0 a 1 fijo para una semilla y un índice: posiciones de cráteres, rocas… */
    static double unit(long seed, int index) {
        long value = mix(seed * 0x9E3779B97F4A7C15L + index * 0xD1B54A32D192ED03L);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static final double[] COS = new double[8];
    private static final double[] SIN = new double[8];

    static {
        for (int index = 0; index < 8; index++) {
            COS[index] = StrictMath.cos(index * 0.618);
            SIN[index] = StrictMath.sin(index * 0.618);
        }
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double dot(long hash, double x, double y) {
        int index = (int) (hash >>> 58);
        return GRADIENT_X[index] * x + GRADIENT_Y[index] * y;
    }

    private static long hash(int x, int y, long seed) {
        return mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL));
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
