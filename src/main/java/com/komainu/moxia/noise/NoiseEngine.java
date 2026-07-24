package com.komainu.moxia.noise;

public final class NoiseEngine {
    private NoiseEngine() {
    }

    public static double perlin2D(double x, double z, long seed) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        double localX = x - x0;
        double localZ = z - z0;
        double u = fade(localX);
        double v = fade(localZ);

        double lowerLeft = gradient(hash(x0, z0, seed), localX, localZ);
        double lowerRight = gradient(hash(x0 + 1, z0, seed), localX - 1.0, localZ);
        double upperLeft = gradient(hash(x0, z0 + 1, seed), localX, localZ - 1.0);
        double upperRight = gradient(hash(x0 + 1, z0 + 1, seed), localX - 1.0, localZ - 1.0);
        return lerp(lerp(lowerLeft, lowerRight, u), lerp(upperLeft, upperRight, u), v);
    }

    public static double fractalBrownianMotion2D(
            double x,
            double z,
            long seed,
            int octaves,
            double lacunarity,
            double persistence
    ) {
        double value = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maximum = 0.0;
        for (int octave = 0; octave < octaves; octave++) {
            value += perlin2D(x * frequency, z * frequency, seed + octave * 7_919L) * amplitude;
            maximum += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return maximum == 0.0 ? 0.0 : value / maximum;
    }

    public static double ridged2D(double x, double z, long seed, int octaves) {
        double value = fractalBrownianMotion2D(x, z, seed, octaves, 2.0, 0.5);
        return 1.0 - Math.abs(value);
    }

    public static double worley2D(double x, double z, long seed) {
        int cellX = fastFloor(x);
        int cellZ = fastFloor(z);
        double minimumSquared = Double.POSITIVE_INFINITY;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                int candidateX = cellX + offsetX;
                int candidateZ = cellZ + offsetZ;
                long hash = hash(candidateX, candidateZ, seed);
                double featureX = candidateX + unit(hash);
                double featureZ = candidateZ + unit(mix(hash));
                double deltaX = featureX - x;
                double deltaZ = featureZ - z;
                minimumSquared = Math.min(minimumSquared, deltaX * deltaX + deltaZ * deltaZ);
            }
        }
        return Math.min(1.0, Math.sqrt(minimumSquared));
    }

    public static double deterministicUnit(int x, int y, int z, long seed) {
        long value = hash(x * 734_287 + y * 912_931, z * 438_289, seed);
        return unit(value);
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double gradient(long hash, double x, double z) {
        return switch ((int) hash & 7) {
            case 0 -> x + z;
            case 1 -> x - z;
            case 2 -> -x + z;
            case 3 -> -x - z;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> z;
            default -> -z;
        };
    }

    private static double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }

    private static long hash(int x, int z, long seed) {
        long value = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        return mix(value);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
