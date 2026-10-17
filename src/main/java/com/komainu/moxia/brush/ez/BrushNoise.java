package com.komainu.moxia.brush.ez;

import com.komainu.moxia.noise.NoiseSpec;

/**
 * Campo de 0 a 1 que mueve el reparto de los pinceles. Sin {@code -n} es ruido blanco por bloque (cada bloque, su
 * propio valor); con {@code -n} se muestrea el {@link NoiseSpec} en las coordenadas del mundo multiplicadas por la
 * escala de {@code -z}.
 */
@FunctionalInterface
public interface BrushNoise {
    double sample(int x, int y, int z);

    static BrushNoise white(long seed) {
        return (x, y, z) -> unit(x, y, z, seed);
    }

    static BrushNoise of(NoiseSpec spec, double scale, long seed) {
        if (spec == null) {
            return white(seed);
        }
        NoiseSpec.NoiseField field = spec.build(seed);
        return (x, y, z) -> Math.clamp(field.sample(x * scale, y * scale, z * scale), 0.0, 1.0);
    }

    /** Valor determinista en [0, 1) para una celda y una semilla. */
    static double unit(int x, int y, int z, long seed) {
        long value = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ seed;
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;
        return (value >>> 11) * 0x1.0p-53;
    }
}
