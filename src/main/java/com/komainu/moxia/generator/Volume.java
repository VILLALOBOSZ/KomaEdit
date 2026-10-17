package com.komainu.moxia.generator;

/**
 * Zona donde trabaja un generador: la selección de WorldEdit (o una caja en los tests). Solo responde si un
 * bloque está dentro; no lee el mundo, así que se puede usar desde un worker.
 */
public interface Volume {
    boolean contains(int x, int y, int z);

    int minimumX();

    int minimumY();

    int minimumZ();

    int maximumX();

    int maximumY();

    int maximumZ();

    /** La columna (x, z) pertenece a la selección: basta con que la contenga a alguna altura de prueba. */
    default boolean containsColumn(int x, int z) {
        int middle = (minimumY() + maximumY()) >> 1;
        return contains(x, minimumY(), z) || contains(x, middle, z) || contains(x, maximumY(), z);
    }

    default int sizeX() {
        return maximumX() - minimumX() + 1;
    }

    default int sizeY() {
        return maximumY() - minimumY() + 1;
    }

    default int sizeZ() {
        return maximumZ() - minimumZ() + 1;
    }

    default double centerX() {
        return (minimumX() + maximumX() + 1) * 0.5;
    }

    default double centerY() {
        return (minimumY() + maximumY() + 1) * 0.5;
    }

    default double centerZ() {
        return (minimumZ() + maximumZ() + 1) * 0.5;
    }

    static Volume box(int minimumX, int minimumY, int minimumZ, int maximumX, int maximumY, int maximumZ) {
        return new Box(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);
    }

    record Box(int minimumX, int minimumY, int minimumZ, int maximumX, int maximumY, int maximumZ)
            implements Volume {
        public Box {
            if (maximumX < minimumX || maximumY < minimumY || maximumZ < minimumZ) {
                throw new IllegalArgumentException("La zona está vacía");
            }
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return x >= minimumX && x <= maximumX && y >= minimumY && y <= maximumY && z >= minimumZ
                    && z <= maximumZ;
        }
    }
}
