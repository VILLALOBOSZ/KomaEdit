package com.komainu.moxia.selection;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * Un terreno inventado para las pruebas de la selección: suelo macizo hasta {@code ground} (incluida) y por encima
 * aire, con bloques sueltos puestos a mano (sólidos, plantas o aire para cavar) y una zona sin cargar si se pide.
 */
final class TestTerrain implements Voxels {
    private final int ground;
    private final int bottom;
    private final Long2IntOpenHashMap placed = new Long2IntOpenHashMap();
    private int unloadedFromX = Integer.MAX_VALUE;

    /** Suelo macizo de {@code bottom} a {@code ground}. */
    TestTerrain(int bottom, int ground) {
        this.bottom = bottom;
        this.ground = ground;
        placed.defaultReturnValue(Integer.MIN_VALUE);
    }

    static TestTerrain flat(int ground) {
        return new TestTerrain(-64, ground);
    }

    TestTerrain set(int x, int y, int z, int kind) {
        placed.put(BlockSet.pack(x, y, z), kind);
        return this;
    }

    TestTerrain fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int kind) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set(x, y, z, kind);
                }
            }
        }
        return this;
    }

    /** De esta X en adelante los trozos no están cargados. */
    TestTerrain unloadedFrom(int x) {
        unloadedFromX = x;
        return this;
    }

    @Override
    public int kind(int x, int y, int z) {
        if (x >= unloadedFromX) {
            return UNLOADED;
        }
        int custom = placed.get(BlockSet.pack(x, y, z));
        if (custom != Integer.MIN_VALUE) {
            return custom;
        }
        return y <= ground && y >= bottom ? SOLID : AIR;
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int maxY() {
        return 319;
    }
}
