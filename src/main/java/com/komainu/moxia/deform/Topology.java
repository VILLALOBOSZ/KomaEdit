package com.komainu.moxia.deform;

/**
 * Prueba de punto simple con conectividad (26, 6): quitar o poner un bloque simple no crea ni cierra agujeros,
 * no parte una pieza en dos ni une dos piezas. Es lo que usa la bandera {@code -t} de superficie para intentar
 * conservar la topología.
 */
final class Topology {
    private static final int CENTER = 13;
    private static final int[] FACES = {4, 10, 12, 14, 16, 22};

    private Topology() {
    }

    /**
     * @param neighbourhood 27 casillas indexadas {@code (dx + 1) + 3 (dy + 1) + 9 (dz + 1)}; el centro se ignora
     */
    static boolean simple(boolean[] neighbourhood) {
        return solidComponents(neighbourhood) == 1 && backgroundComponents(neighbourhood) == 1;
    }

    /** Rellena la vecindad de {@code index} desde una ocupación con las dimensiones de {@code volume}. */
    static void gather(Volume volume, boolean[] occupancy, int index, boolean[] neighbourhood) {
        int x = (index % volume.sizeX);
        int z = (index / volume.sizeX) % volume.sizeZ;
        int y = index / (volume.sizeX * volume.sizeZ);
        int cell = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    int nz = z + dz;
                    boolean inside = nx >= 0 && ny >= 0 && nz >= 0
                            && nx < volume.sizeX && ny < volume.sizeY && nz < volume.sizeZ;
                    neighbourhood[cell++] = inside && occupancy[(ny * volume.sizeZ + nz) * volume.sizeX + nx];
                }
            }
        }
    }

    private static int solidComponents(boolean[] cells) {
        int[] stack = new int[27];
        boolean[] seen = new boolean[27];
        int components = 0;
        for (int start = 0; start < 27; start++) {
            if (start == CENTER || !cells[start] || seen[start]) {
                continue;
            }
            components++;
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            while (top > 0) {
                int cell = stack[--top];
                for (int other = 0; other < 27; other++) {
                    if (other != CENTER && cells[other] && !seen[other] && adjacent26(cell, other)) {
                        seen[other] = true;
                        stack[top++] = other;
                    }
                }
            }
        }
        return components;
    }

    private static int backgroundComponents(boolean[] cells) {
        int[] stack = new int[27];
        boolean[] seen = new boolean[27];
        int components = 0;
        for (int face : FACES) {
            if (cells[face] || seen[face]) {
                continue;
            }
            components++;
            int top = 0;
            stack[top++] = face;
            seen[face] = true;
            while (top > 0) {
                int cell = stack[--top];
                for (int other = 0; other < 27; other++) {
                    if (other != CENTER && !cells[other] && !seen[other] && in18(other) && adjacent6(cell, other)) {
                        seen[other] = true;
                        stack[top++] = other;
                    }
                }
            }
        }
        return components;
    }

    private static boolean in18(int cell) {
        int dx = cell % 3 - 1;
        int dy = cell / 3 % 3 - 1;
        int dz = cell / 9 - 1;
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 2;
    }

    private static boolean adjacent26(int first, int second) {
        return Math.abs(first % 3 - second % 3) <= 1
                && Math.abs(first / 3 % 3 - second / 3 % 3) <= 1
                && Math.abs(first / 9 - second / 9) <= 1
                && first != second;
    }

    private static boolean adjacent6(int first, int second) {
        int distance = Math.abs(first % 3 - second % 3)
                + Math.abs(first / 3 % 3 - second / 3 % 3)
                + Math.abs(first / 9 - second / 9);
        return distance == 1;
    }
}
