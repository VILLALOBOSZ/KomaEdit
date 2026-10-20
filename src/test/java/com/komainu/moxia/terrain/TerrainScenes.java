package com.komainu.moxia.terrain;

import com.komainu.moxia.noise.NoiseEngine;

import java.util.ArrayList;
import java.util.List;

/** Terrenos de ejemplo para las pruebas y los renders. */
final class TerrainScenes {
    private TerrainScenes() {
    }

    static FakeTerrain flat(int size, int height, int top) {
        return FakeTerrain.columns(size, height, size, (x, z) -> top);
    }

    static FakeTerrain hills(int size, int height, long seed) {
        return FakeTerrain.columns(size, height, size, (x, z) -> (int) Math.round(
                height * 0.42 + height * 0.2 * NoiseEngine.fractalBrownianMotion2D(x * 0.025, z * 0.025, seed, 4,
                        2.0, 0.5)));
    }

    /** Una montaña con crestas en el centro y llanura alrededor. */
    static FakeTerrain mountain(int size, int height, long seed) {
        double center = size / 2.0;
        return FakeTerrain.columns(size, height, size, (x, z) -> {
            double dx = (x - center) / (size * 0.32);
            double dz = (z - center) / (size * 0.32);
            double r = Math.sqrt(dx * dx + dz * dz);
            double peak = Math.exp(-r * r * 1.6);
            double ridges = NoiseEngine.ridged2D(x * 0.045, z * 0.045, seed, 5);
            double base = NoiseEngine.fractalBrownianMotion2D(x * 0.05, z * 0.05, seed + 3, 3, 2.0, 0.5);
            return (int) Math.round(12 + height * 0.62 * peak * (0.6 + 0.4 * ridges) + 3 * base);
        });
    }

    /** Meseta alta que baja un poco hacia el este, con bordes ondulados. */
    static FakeTerrain plateau(int size, int height, long seed) {
        return FakeTerrain.columns(size, height, size, (x, z) -> (int) Math.round(
                height * 0.62 - x * 6.0 / size
                        + 3 * NoiseEngine.fractalBrownianMotion2D(x * 0.05, z * 0.05, seed, 4, 2.0, 0.5)));
    }

    /** Ladera que baja de oeste a este con un valle sinuoso. */
    static FakeTerrain slope(int size, int height, long seed) {
        return FakeTerrain.columns(size, height, size, (x, z) -> (int) Math.round(
                height * 0.7 - x * height * 0.35 / size
                        + 5 * NoiseEngine.fractalBrownianMotion2D(x * 0.04, z * 0.04, seed, 4, 2.0, 0.5)));
    }

    /** Puntos de una curva suave muestreados cada medio bloque (centros de bloque). */
    static List<double[]> curve(double[][] controls) {
        List<double[]> samples = new ArrayList<>();
        for (int segment = 0; segment + 1 < controls.length; segment++) {
            double[] p0 = controls[Math.max(0, segment - 1)];
            double[] p1 = controls[segment];
            double[] p2 = controls[segment + 1];
            double[] p3 = controls[Math.min(controls.length - 1, segment + 2)];
            double length = Math.hypot(p2[0] - p1[0], p2[2] - p1[2]);
            int steps = Math.max(2, (int) Math.ceil(length * 2.0));
            for (int step = 0; step < steps; step++) {
                double t = step / (double) steps;
                double[] point = new double[3];
                for (int axis = 0; axis < 3; axis++) {
                    point[axis] = 0.5 * ((2 * p1[axis]) + (-p0[axis] + p2[axis]) * t
                            + (2 * p0[axis] - 5 * p1[axis] + 4 * p2[axis] - p3[axis]) * t * t
                            + (-p0[axis] + 3 * p1[axis] - 3 * p2[axis] + p3[axis]) * t * t * t);
                }
                samples.add(point);
            }
        }
        samples.add(controls[controls.length - 1].clone());
        return samples;
    }
}
