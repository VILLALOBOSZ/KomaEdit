package com.komainu.moxia.terrain;

import com.komainu.moxia.noise.NoiseEngine;

/**
 * Erosión térmica por talud: donde una columna queda por encima de sus vecinas más de lo que permite el ángulo de
 * reposo, parte del material resbala hacia ellas. Tras unas pasadas no queda ninguna pendiente más empinada que ese
 * ángulo y al pie de los cortados se forman canchales. Cada pasada calcula todos los traspasos con las alturas de la
 * anterior, así el resultado no depende del orden en que se recorren las columnas, y lo que sale de una columna
 * entra entero en otras: la masa se conserva.
 */
public final class ThermalErosion {
    private static final long HARDNESS_SEED = 0x7A1D5L;
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final double[] DISTANCE = {1, 1, 1, 1, Math.sqrt(2), Math.sqrt(2), Math.sqrt(2), Math.sqrt(2)};

    private ThermalErosion() {
    }

    /**
     * @param talusDegrees ángulo de reposo: por encima, el suelo resbala
     * @param strength     fracción del exceso que se mueve en cada pasada (0.1 a 2; 1 normal)
     */
    public record Settings(int iterations, double talusDegrees, double strength) {
        public Settings {
            if (iterations < 1 || iterations > 2_000) {
                throw new IllegalArgumentException("Las iteraciones deben estar entre 1 y 2000");
            }
            if (talusDegrees < 5.0 || talusDegrees > 85.0) {
                throw new IllegalArgumentException("El ángulo de reposo debe estar entre 5 y 85 grados");
            }
            if (strength <= 0.0 || strength > 2.0) {
                throw new IllegalArgumentException("La fuerza térmica debe estar entre 0.1 y 2");
            }
        }
    }

    /** Masa que ha resbalado en total. */
    public static double run(HeightField field, Settings settings, Runnable checkCancelled) {
        int sizeX = field.sizeX();
        int sizeZ = field.sizeZ();
        int columns = field.columns();
        double[] map = new double[columns];
        boolean[] editable = new boolean[columns];
        double[] low = new double[columns];
        double[] high = new double[columns];
        for (int index = 0; index < columns; index++) {
            map[index] = field.at(index);
            editable[index] = field.editableAt(index) && !Double.isNaN(map[index]);
            low[index] = field.lowestAt(index);
            high[index] = field.highestAt(index);
        }
        // La roca no es igual de dura en todas partes: el ángulo de reposo varía un poco con un ruido fijo del
        // mundo, y así quedan resaltes y canchales en lugar de un cono perfecto.
        double[] talus = new double[columns];
        for (int index = 0; index < columns; index++) {
            double hardness = NoiseEngine.fractalBrownianMotion2D(field.xOf(index) * 0.06, field.zOf(index) * 0.06,
                    HARDNESS_SEED, 3, 2.0, 0.5);
            double angle = Math.clamp(settings.talusDegrees * (1.0 + 0.3 * hardness), 3.0, 87.0);
            talus[index] = Math.tan(Math.toRadians(angle));
        }
        double rate = 0.25 * settings.strength;
        double[] delta = new double[columns];
        double[] excess = new double[8];
        double moved = 0.0;
        for (int iteration = 0; iteration < settings.iterations; iteration++) {
            checkCancelled.run();
            java.util.Arrays.fill(delta, 0.0);
            boolean any = false;
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int index = z * sizeX + x;
                    if (!editable[index]) {
                        continue;
                    }
                    double height = map[index];
                    double largest = 0.0;
                    double total = 0.0;
                    for (int direction = 0; direction < 8; direction++) {
                        excess[direction] = 0.0;
                        int nx = x + DX[direction];
                        int nz = z + DZ[direction];
                        if (nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ) {
                            continue;
                        }
                        int neighbour = nz * sizeX + nx;
                        if (!editable[neighbour]) {
                            continue;
                        }
                        double difference = height - map[neighbour] - talus[index] * DISTANCE[direction];
                        if (difference > 0.0) {
                            excess[direction] = difference;
                            total += difference;
                            largest = Math.max(largest, difference);
                        }
                    }
                    if (largest <= 0.0) {
                        continue;
                    }
                    double amount = Math.min(rate * largest, Math.max(0.0, height - low[index]));
                    if (amount <= 0.0) {
                        continue;
                    }
                    any = true;
                    for (int direction = 0; direction < 8; direction++) {
                        if (excess[direction] <= 0.0) {
                            continue;
                        }
                        int neighbour = (z + DZ[direction]) * sizeX + (x + DX[direction]);
                        double share = amount * excess[direction] / total;
                        delta[neighbour] += share;
                        delta[index] -= share;
                    }
                }
            }
            if (!any) {
                break;
            }
            for (int index = 0; index < columns; index++) {
                if (delta[index] == 0.0) {
                    continue;
                }
                double next = map[index] + delta[index];
                if (next > high[index]) {
                    // Sin sitio arriba: el sobrante vuelve a donde estaba repartido como pérdida mínima.
                    next = high[index];
                }
                if (delta[index] > 0.0) {
                    moved += next - map[index];
                }
                map[index] = next;
            }
        }
        for (int index = 0; index < columns; index++) {
            if (editable[index]) {
                field.setAt(index, map[index]);
            }
        }
        return moved;
    }
}
