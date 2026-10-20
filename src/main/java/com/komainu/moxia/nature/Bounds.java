package com.komainu.moxia.nature;

/** Caja de bloques (extremos incluidos) en la que un generador puede escribir: la foto capturada. */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static Bounds around(int x, int y, int z, int horizontal, int below, int above) {
        return new Bounds(x - horizontal, y - below, z - horizontal, x + horizontal, y + above, z + horizontal);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** La misma caja con {@code margin} bloques menos por cada lado. */
    public Bounds shrink(int margin) {
        return new Bounds(minX + margin, minY + margin, minZ + margin, maxX - margin, maxY - margin, maxZ - margin);
    }

    /** La caja que cubre las dos. */
    public Bounds union(Bounds other) {
        return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }
}
