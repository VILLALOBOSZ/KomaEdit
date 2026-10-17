package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

/**
 * Estructura de bloques sueltos: el portapapeles o un schematic. Cada bloque ocupa la celda centrada en su
 * coordenada entera relativa al origen; el aire de dentro de la caja queda como {@link Structure#AIR}.
 */
public final class VoxelStructure implements Structure {
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    private final int[] cells;
    private final int paletteSize;
    private final boolean smooth;
    private final double fillBias;
    private final double rotation;
    private final String name;

    private VoxelStructure(Builder builder, boolean smooth, double fillBias, double rotation, String name) {
        this.sizeX = builder.sizeX;
        this.sizeY = builder.sizeY;
        this.sizeZ = builder.sizeZ;
        this.offsetX = builder.offsetX;
        this.offsetY = builder.offsetY;
        this.offsetZ = builder.offsetZ;
        this.cells = builder.cells;
        this.paletteSize = builder.paletteSize;
        this.smooth = smooth;
        this.fillBias = fillBias;
        this.rotation = rotation;
        this.name = name;
    }

    /**
     * @param offsetX coordenada local (relativa al origen) del bloque mínimo de la caja
     */
    public static Builder builder(int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ) {
        return new Builder(sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ);
    }

    @Override
    public Vec3 min() {
        return new Vec3(offsetX - 0.5, offsetY - 0.5, offsetZ - 0.5);
    }

    @Override
    public Vec3 max() {
        return new Vec3(offsetX + sizeX - 0.5, offsetY + sizeY - 0.5, offsetZ + sizeZ - 0.5);
    }

    @Override
    public Vec3 naturalSize() {
        return new Vec3(sizeX, sizeY, sizeZ);
    }

    @Override
    public Vec3 blocksPerUnit() {
        return new Vec3(1, 1, 1);
    }

    @Override
    public Sampler sampler(long seed) {
        return (x, y, z) -> {
            int cellX = (int) Math.floor(x + 0.5) - offsetX;
            int cellY = (int) Math.floor(y + 0.5) - offsetY;
            int cellZ = (int) Math.floor(z + 0.5) - offsetZ;
            if (cellX < 0 || cellY < 0 || cellZ < 0 || cellX >= sizeX || cellY >= sizeY || cellZ >= sizeZ) {
                return EMPTY;
            }
            return cells[(cellY * sizeZ + cellZ) * sizeX + cellX];
        };
    }

    @Override
    public boolean usesPattern() {
        return false;
    }

    @Override
    public boolean smoothPaste() {
        return smooth;
    }

    @Override
    public double fillBias() {
        return fillBias;
    }

    @Override
    public double inputRotation() {
        return rotation;
    }

    public int paletteSize() {
        return paletteSize;
    }

    /** Bloques que no son aire. */
    public int solidCount() {
        int count = 0;
        for (int cell : cells) {
            if (cell >= 0) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String describe() {
        return name + " " + sizeX + "×" + sizeY + "×" + sizeZ;
    }

    public static final class Builder {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final int[] cells;
        private int paletteSize;

        private Builder(int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ) {
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("La estructura está vacía");
            }
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.cells = new int[Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)];
            java.util.Arrays.fill(cells, AIR);
        }

        /** Coordenadas locales (relativas al origen). */
        public Builder set(int x, int y, int z, int paletteIndex) {
            int cellX = x - offsetX;
            int cellY = y - offsetY;
            int cellZ = z - offsetZ;
            cells[(cellY * sizeZ + cellZ) * sizeX + cellX] = paletteIndex;
            paletteSize = Math.max(paletteSize, paletteIndex + 1);
            return this;
        }

        public VoxelStructure build(boolean smooth, double fillBias, double rotation, String name) {
            return new VoxelStructure(this, smooth, fillBias, rotation, name);
        }
    }
}
