package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.math.Vector3;

import java.util.Locale;

/**
 * Cuentas de las máscaras y patrones de degradado ({@code #radialgradientmask}, {@code #vectorgradientpattern}…):
 * cuánto se ha avanzado (de 0 a 1) y qué valor aleatorio estable toca a cada bloque.
 */
public final class GradientMath {
    private GradientMath() {
    }

    /** Forma de medir la distancia al jugador en los degradados radiales. */
    public enum DistanceMode {
        SPHERE("sphere", "esfera", "euclidean", "e"),
        CUBE("cube", "cubo", "chebyshev", "c"),
        DIAMOND("diamond", "rombo", "manhattan", "m"),
        CYLINDER("cylinder", "cilindro", "horizontal", "h");

        private final String[] names;

        DistanceMode(String... names) {
            this.names = names;
        }

        public static DistanceMode byName(String input) {
            String name = input.trim().toLowerCase(Locale.ROOT);
            for (DistanceMode mode : values()) {
                for (String alias : mode.names) {
                    if (alias.equals(name)) {
                        return mode;
                    }
                }
            }
            throw new IllegalArgumentException("Modo de distancia desconocido: " + input
                    + " (sphere, cube, diamond, cylinder)");
        }

        public double distance(double dx, double dy, double dz) {
            return switch (this) {
                case SPHERE -> Math.sqrt(dx * dx + dy * dy + dz * dz);
                case CUBE -> Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                case DIAMOND -> Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                case CYLINDER -> Math.sqrt(dx * dx + dz * dz);
            };
        }
    }

    /** Avance de 0 a 1: 0 hasta {@code offset}, 1 a partir de {@code offset + length}. */
    public static double fraction(double distance, double offset, double length) {
        if (length <= 0) {
            throw new IllegalArgumentException("La distancia del degradado debe ser mayor que 0");
        }
        return Math.clamp((distance - offset) / length, 0.0, 1.0);
    }

    /** Avance a lo largo de un vector unitario desde un origen, de 0 a 1 en {@code length} bloques. */
    public static double along(Vector3 origin, Vector3 unit, double x, double y, double z, double length) {
        double projection = (x - origin.x()) * unit.x() + (y - origin.y()) * unit.y() + (z - origin.z()) * unit.z();
        return fraction(projection, 0.0, length);
    }

    /** ¿Pasa la máscara? Cerca (avance 0) pasa siempre; lejos (avance 1) nunca. */
    public static boolean passes(double fraction, double random) {
        return random >= fraction;
    }

    /** Índice de la paleta según el avance más medio bloque de mezcla hacia cada lado. */
    public static int paletteIndex(double fraction, double random, int size) {
        int index = (int) Math.floor(fraction * size + (random - 0.5));
        return Math.clamp(index, 0, size - 1);
    }

    /** Valor de 0 (incluido) a 1 (excluido) fijo para cada posición y semilla. */
    public static double hash01(int x, int y, int z, long seed) {
        long value = seed ^ 0x9E3779B97F4A7C15L;
        value ^= x * 0xBF58476D1CE4E5B9L;
        value = Long.rotateLeft(value, 27) * 0x94D049BB133111EBL;
        value ^= y * 0x632BE59BD9B4E019L;
        value = Long.rotateLeft(value, 31) * 0xBF58476D1CE4E5B9L;
        value ^= z * 0x8CB92BA72F3D8DD7L;
        value ^= value >>> 31;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 29;
        return (value >>> 11) * 0x1.0p-53;
    }
}
