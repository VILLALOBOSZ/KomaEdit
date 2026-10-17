package com.komainu.moxia.ivy;

import java.util.HashMap;
import java.util.Map;

/** Mundo de pruebas: bloques enteros, escaleras (solo sostienen alfombras) y una caja fuera de la cual no hay nada. */
final class FakeTerrain implements IvyTerrain {
    static final int SOLID = 1;
    /** Bloque que no es entero por ninguna cara pero sostiene una alfombra, como una escalera vista de frente. */
    static final int PARTIAL = 2;

    final Map<Long, Integer> blocks = new HashMap<>();
    final int minX;
    final int minY;
    final int minZ;
    final int maxX;
    final int maxY;
    final int maxZ;
    int queries;

    FakeTerrain(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    FakeTerrain fill(int x0, int y0, int z0, int x1, int y1, int z1, int kind) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, kind);
                }
            }
        }
        return this;
    }

    FakeTerrain set(int x, int y, int z, int kind) {
        if (kind == 0) {
            blocks.remove(IvyGrower.key(x, y, z));
        } else {
            blocks.put(IvyGrower.key(x, y, z), kind);
        }
        return this;
    }

    int kind(int x, int y, int z) {
        return blocks.getOrDefault(IvyGrower.key(x, y, z), 0);
    }

    boolean inside(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    @Override
    public boolean free(int x, int y, int z) {
        queries++;
        return inside(x, y, z) && kind(x, y, z) == 0;
    }

    @Override
    public boolean sturdy(int x, int y, int z, int face) {
        return inside(x, y, z) && kind(x, y, z) == SOLID;
    }

    @Override
    public boolean rests(int x, int y, int z) {
        return inside(x, y, z) && kind(x, y, z) != 0;
    }

    /**
     * Casa de pruebas: suelo, muro sur de 17 de ancho y 9 de alto con una ventana abierta de 3x3 y un dintel que
     * sobresale, laterales y fondo, y un tejado a dos aguas con alero de un bloque.
     */
    static FakeTerrain house() {
        FakeTerrain terrain = new FakeTerrain(-14, -1, -16, 30, 26, 20);
        terrain.fill(-14, -1, -16, 30, -1, 20, SOLID);
        // Muros: x 0..16, z 0..10, alto y 0..8.
        terrain.fill(0, 0, 0, 16, 8, 0, SOLID);
        terrain.fill(0, 0, 10, 16, 8, 10, SOLID);
        terrain.fill(0, 0, 0, 0, 8, 10, SOLID);
        terrain.fill(16, 0, 0, 16, 8, 10, SOLID);
        // Ventana abierta en el muro frontal (z = 0) y puerta.
        terrain.fill(9, 3, 0, 11, 5, 0, 0);
        terrain.fill(3, 0, 0, 4, 2, 0, 0);
        // Dintel que sobresale sobre la ventana.
        terrain.fill(8, 6, -1, 12, 6, -1, SOLID);
        // Tejado a dos aguas a lo largo de x, alero de 1 bloque: losas escalonadas como bloques enteros.
        for (int step = 0; step <= 6; step++) {
            int y = 9 + step;
            terrain.fill(-1, y, -1 + step, 17, y, -1 + step, SOLID);
            terrain.fill(-1, y, 11 - step, 17, y, 11 - step, SOLID);
        }
        // Hastiales bajo el tejado.
        for (int step = 0; step <= 5; step++) {
            terrain.fill(0, 9 + step, step, 0, 9 + step, 10 - step, SOLID);
            terrain.fill(16, 9 + step, step, 16, 9 + step, 10 - step, SOLID);
        }
        return terrain;
    }
}
