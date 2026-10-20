package com.komainu.moxia.clipboard;

/**
 * Una estructura de bloques como rejilla de números, sin Minecraft: {@code 0} es aire y cada otro número es una
 * entrada de la paleta de quien la creó. Las coordenadas son relativas al origen del portapapeles (el bloque donde
 * se pega): la caja va de {@code minimum} a {@code minimum + size − 1}.
 */
public final class VoxelVolume {
    public static final int AIR = 0;

    private final int minimumX;
    private final int minimumY;
    private final int minimumZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] ids;

    public VoxelVolume(int minimumX, int minimumY, int minimumZ, int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("La estructura está vacía");
        }
        this.minimumX = minimumX;
        this.minimumY = minimumY;
        this.minimumZ = minimumZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.ids = new int[Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)];
    }

    public int minimumX() {
        return minimumX;
    }

    public int minimumY() {
        return minimumY;
    }

    public int minimumZ() {
        return minimumZ;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public long volume() {
        return ids.length;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minimumX && x < minimumX + sizeX
                && y >= minimumY && y < minimumY + sizeY
                && z >= minimumZ && z < minimumZ + sizeZ;
    }

    /** Número del bloque; aire fuera de la caja. */
    public int get(int x, int y, int z) {
        return contains(x, y, z) ? ids[index(x, y, z)] : AIR;
    }

    public void set(int x, int y, int z, int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Los números de bloque son 0 (aire) o positivos");
        }
        ids[index(x, y, z)] = id;
    }

    /** Bloques que no son aire. */
    public int blocks() {
        int count = 0;
        for (int id : ids) {
            if (id != AIR) {
                count++;
            }
        }
        return count;
    }

    /** Valor con coordenadas desde la esquina menor ({@code 0 … size − 1}); aire fuera. */
    int local(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return AIR;
        }
        return ids[(y * sizeZ + z) * sizeX + x];
    }

    private int index(int x, int y, int z) {
        return ((y - minimumY) * sizeZ + (z - minimumZ)) * sizeX + (x - minimumX);
    }

    /** Recorre los bloques que no son aire. */
    public void forEach(Visitor visitor) {
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int id = ids[(y * sizeZ + z) * sizeX + x];
                    if (id != AIR) {
                        visitor.visit(minimumX + x, minimumY + y, minimumZ + z, id);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface Visitor {
        void visit(int x, int y, int z, int id);
    }
}
