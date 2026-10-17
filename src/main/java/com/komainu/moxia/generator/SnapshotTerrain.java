package com.komainu.moxia.generator;

import com.komainu.moxia.region.RegionSnapshot;

import java.util.Arrays;

/**
 * La foto de la selección vista por los generadores: qué bloques están dentro, la superficie de cada columna y la
 * máscara. Calcula las columnas una vez al crearla; es de solo lectura y no toca el mundo.
 */
public final class SnapshotTerrain implements Volume, NoiseGenerator.Surface, FlowField.Terrain {
    private static final int UNKNOWN = Integer.MAX_VALUE;

    private final RegionSnapshot snapshot;
    private final SnapshotMask mask;
    private final int minimumX;
    private final int minimumY;
    private final int minimumZ;
    private final int maximumX;
    private final int maximumY;
    private final int maximumZ;
    private final int[] maskTops;
    private final int[] surfaces;

    /**
     * @param mask máscara de {@code -m} o {@code null}
     */
    public SnapshotTerrain(RegionSnapshot snapshot, SnapshotMask mask) {
        this.snapshot = snapshot;
        this.mask = mask;
        int[] bounds = selectionBounds(snapshot);
        minimumX = bounds[0];
        minimumY = bounds[1];
        minimumZ = bounds[2];
        maximumX = bounds[3];
        maximumY = bounds[4];
        maximumZ = bounds[5];
        int columns = (maximumX - minimumX + 1) * (maximumZ - minimumZ + 1);
        maskTops = new int[columns];
        surfaces = new int[columns];
        Arrays.fill(maskTops, UNKNOWN);
        Arrays.fill(surfaces, UNKNOWN);
    }

    /** Caja de lo seleccionado dentro de la foto (sin el margen). */
    private static int[] selectionBounds(RegionSnapshot snapshot) {
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    if (snapshot.selected(x, y, z)) {
                        bounds[0] = Math.min(bounds[0], x);
                        bounds[1] = Math.min(bounds[1], y);
                        bounds[2] = Math.min(bounds[2], z);
                        bounds[3] = Math.max(bounds[3], x);
                        bounds[4] = Math.max(bounds[4], y);
                        bounds[5] = Math.max(bounds[5], z);
                    }
                }
            }
        }
        if (bounds[0] == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("La selección está vacía");
        }
        return bounds;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return snapshot.selected(x, y, z);
    }

    @Override
    public int minimumX() {
        return minimumX;
    }

    @Override
    public int minimumY() {
        return minimumY;
    }

    @Override
    public int minimumZ() {
        return minimumZ;
    }

    @Override
    public int maximumX() {
        return maximumX;
    }

    @Override
    public int maximumY() {
        return maximumY;
    }

    @Override
    public int maximumZ() {
        return maximumZ;
    }

    private int column(int x, int z) {
        if (x < minimumX || x > maximumX || z < minimumZ || z > maximumZ) {
            return -1;
        }
        return (z - minimumZ) * (maximumX - minimumX + 1) + (x - minimumX);
    }

    /** Bloque más alto de la columna que cumple la máscara (sin máscara, el más alto sólido o líquido). */
    @Override
    public int top(int x, int z) {
        int column = column(x, z);
        if (column < 0) {
            return NoiseGenerator.Surface.NONE;
        }
        if (maskTops[column] == UNKNOWN) {
            int found = NoiseGenerator.Surface.NONE;
            for (int y = maximumY; y >= minimumY; y--) {
                if (snapshot.selected(x, y, z) && (mask == null ? ground(x, y, z) : mask.test(snapshot.info(x, y, z)))) {
                    found = y;
                    break;
                }
            }
            maskTops[column] = found;
        }
        return maskTops[column];
    }

    @Override
    public int surface(int x, int z) {
        int column = column(x, z);
        if (column < 0) {
            return FlowField.Terrain.NONE;
        }
        if (surfaces[column] == UNKNOWN) {
            int found = FlowField.Terrain.NONE;
            for (int y = maximumY; y >= minimumY; y--) {
                if (snapshot.selected(x, y, z) && ground(x, y, z)) {
                    found = y;
                    break;
                }
            }
            surfaces[column] = found;
        }
        return surfaces[column];
    }

    @Override
    public boolean source(int x, int y, int z) {
        return mask == null || mask.test(snapshot.info(x, y, z));
    }

    private boolean ground(int x, int y, int z) {
        return snapshot.isSolid(x, y, z) || snapshot.isLiquid(x, y, z);
    }
}
