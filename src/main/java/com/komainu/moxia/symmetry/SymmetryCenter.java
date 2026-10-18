package com.komainu.moxia.symmetry;

/**
 * Centro de la simetría en medios bloques: cada coordenada guarda el doble de la posición en el mundo. Un valor impar
 * cae en el centro de un bloque (el bloque 10 da 21, es decir 10,5) y uno par entre dos bloques (22 es 11,0: entre el
 * bloque 10 y el 11). Así un espejo es aritmética entera: el bloque {@code x} va a {@code x2 - 1 - x}.
 */
public record SymmetryCenter(int x2, int y2, int z2) {
    /** Más allá de esto no hay mundo de Minecraft que valga. */
    private static final int LIMIT = 30_000_000;

    public SymmetryCenter {
        if (Math.abs((long) x2) > 2L * LIMIT || Math.abs((long) y2) > 2L * LIMIT || Math.abs((long) z2) > 2L * LIMIT) {
            throw new IllegalArgumentException("El centro de la simetría está fuera del mundo");
        }
    }

    /** Centro en el bloque {@code (x, y, z)}. */
    public static SymmetryCenter block(int x, int y, int z) {
        return new SymmetryCenter(2 * x + 1, 2 * y + 1, 2 * z + 1);
    }

    /**
     * Centro en el bloque {@code (x, y, z)}, salvo en los ejes marcados, donde queda entre ese bloque y el siguiente
     * (el de coordenada mayor).
     */
    public static SymmetryCenter of(int x, int y, int z, boolean halfX, boolean halfY, boolean halfZ) {
        return new SymmetryCenter(2 * x + (halfX ? 2 : 1), 2 * y + (halfY ? 2 : 1), 2 * z + (halfZ ? 2 : 1));
    }

    /** Centro de la caja de bloques {@code [min, max]}: de bloque si el lado es impar y entre bloques si es par. */
    public static SymmetryCenter box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new SymmetryCenter(
                Math.min(minX, maxX) + Math.max(minX, maxX) + 1,
                Math.min(minY, maxY) + Math.max(minY, maxY) + 1,
                Math.min(minZ, maxZ) + Math.max(minZ, maxZ) + 1
        );
    }

    /**
     * Centro junto a una posición exacta (la del jugador): en los ejes normales, el bloque que la contiene; en los
     * marcados, el borde entre bloques más cercano.
     */
    public static SymmetryCenter nearest(double x, double y, double z, boolean halfX, boolean halfY, boolean halfZ) {
        return new SymmetryCenter(nearest(x, halfX), nearest(y, halfY), nearest(z, halfZ));
    }

    private static int nearest(double value, boolean half) {
        return half ? 2 * (int) Math.round(value) : 2 * (int) Math.floor(value) + 1;
    }

    public boolean halfX() {
        return Math.floorMod(x2, 2) == 0;
    }

    public boolean halfY() {
        return Math.floorMod(y2, 2) == 0;
    }

    public boolean halfZ() {
        return Math.floorMod(z2, 2) == 0;
    }

    /** Coordenadas en el mundo (10,5 es el centro del bloque 10; 11,0 el borde entre el 10 y el 11). */
    public double x() {
        return x2 / 2.0;
    }

    public double y() {
        return y2 / 2.0;
    }

    public double z() {
        return z2 / 2.0;
    }

    /** ¿Cae a la vez en centro de bloque, o a la vez entre bloques, en X y en Z? El radial lo necesita para ser exacto. */
    public boolean radialFriendly() {
        return halfX() == halfZ();
    }

    /** {@code x 10, y 64, z -3|-2}: un número es el bloque del centro y {@code a|b}, el borde entre esos dos bloques. */
    public String describe() {
        return "x " + axis(x2) + ", y " + axis(y2) + ", z " + axis(z2);
    }

    private static String axis(int doubled) {
        if (Math.floorMod(doubled, 2) == 1) {
            return Integer.toString((doubled - 1) / 2);
        }
        int upper = doubled / 2;
        return (upper - 1) + "|" + upper;
    }
}
