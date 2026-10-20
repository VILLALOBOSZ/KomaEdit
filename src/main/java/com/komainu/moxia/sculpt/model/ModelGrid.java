package com.komainu.moxia.sculpt.model;

import java.util.Arrays;

/**
 * La caja capturada por un pincel de modelado vista como rejilla, sin Minecraft: un material por celda
 * ({@link #FREE} si está libre) y si el pincel puede tocarla.
 *
 * <p>Una celda <em>con masa</em> es un bloque de verdad (piedra, tierra, una valla…). Las <em>libres</em> son
 * aire, líquidos y lo que se reemplaza al construir (hierba alta, flores, nieve en capa): ahí se puede añadir
 * masa, pero no cuentan como forma. El adaptador del juego decide qué es cada cosa y qué deja tocar la máscara;
 * los pinceles solo ven números.</p>
 */
public final class ModelGrid {
    /** Celda libre: aire, líquido o planta. */
    public static final int FREE = 0;

    final int minimumX;
    final int minimumY;
    final int minimumZ;
    final int sizeX;
    final int sizeY;
    final int sizeZ;
    final int[] material;
    final boolean[] editable;

    private ModelGrid(int minimumX, int minimumY, int minimumZ, int sizeX, int sizeY, int sizeZ, int[] material,
                      boolean[] editable) {
        this.minimumX = minimumX;
        this.minimumY = minimumY;
        this.minimumZ = minimumZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.material = material;
        this.editable = editable;
    }

    /** Rejilla vacía (todo libre y editable) de {@code sizeX × sizeY × sizeZ} desde la esquina dada. */
    public static ModelGrid create(int minimumX, int minimumY, int minimumZ, int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("La rejilla del pincel está vacía");
        }
        int total = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        boolean[] editable = new boolean[total];
        Arrays.fill(editable, true);
        return new ModelGrid(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ, new int[total], editable);
    }

    /** Copia con los materiales dados (el resultado de un pincel) y la misma máscara. */
    ModelGrid withMaterials(int[] materials) {
        if (materials.length != material.length) {
            throw new IllegalArgumentException("Los materiales no coinciden con la rejilla");
        }
        return new ModelGrid(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ, materials, editable);
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

    public int size() {
        return material.length;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minimumX && x < minimumX + sizeX
                && y >= minimumY && y < minimumY + sizeY
                && z >= minimumZ && z < minimumZ + sizeZ;
    }

    public int index(int x, int y, int z) {
        return ((y - minimumY) * sizeZ + (z - minimumZ)) * sizeX + (x - minimumX);
    }

    public int x(int index) {
        return minimumX + index % sizeX;
    }

    public int y(int index) {
        return minimumY + index / (sizeX * sizeZ);
    }

    public int z(int index) {
        return minimumZ + (index / sizeX) % sizeZ;
    }

    /** Material de la celda; {@link #FREE} si está libre o fuera de la caja. */
    public int material(int x, int y, int z) {
        return contains(x, y, z) ? material[index(x, y, z)] : FREE;
    }

    public int materialAt(int index) {
        return material[index];
    }

    public boolean solid(int x, int y, int z) {
        return material(x, y, z) != FREE;
    }

    public boolean editable(int x, int y, int z) {
        return contains(x, y, z) && editable[index(x, y, z)];
    }

    public void set(int x, int y, int z, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Los materiales son 0 (libre) o positivos");
        }
        material[index(x, y, z)] = value;
    }

    public void setEditable(int x, int y, int z, boolean value) {
        editable[index(x, y, z)] = value;
    }

    /** Celdas con masa. */
    public int solidCount() {
        int count = 0;
        for (int value : material) {
            if (value != FREE) {
                count++;
            }
        }
        return count;
    }

    /**
     * Caras expuestas: entre una celda con masa y una libre de la caja. Las caras contra el borde de la captura no
     * cuentan, porque no se sabe qué hay al otro lado.
     */
    public int exposedFaces() {
        int faces = 0;
        for (int index = 0; index < material.length; index++) {
            if (material[index] == FREE) {
                continue;
            }
            int x = x(index);
            int y = y(index);
            int z = z(index);
            for (int[] offset : Neighbors.FACES) {
                int nx = x + offset[0];
                int ny = y + offset[1];
                int nz = z + offset[2];
                if (contains(nx, ny, nz) && material[index(nx, ny, nz)] == FREE) {
                    faces++;
                }
            }
        }
        return faces;
    }

    /** ¿Tiene la celda con masa algún vecino de cara libre dentro de la caja? */
    public boolean surface(int x, int y, int z) {
        if (!solid(x, y, z)) {
            return false;
        }
        for (int[] offset : Neighbors.FACES) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            int nz = z + offset[2];
            if (contains(nx, ny, nz) && !solid(nx, ny, nz)) {
                return true;
            }
        }
        return false;
    }

    /** Ocupación como densidad: 1 con masa, 0 libre. */
    float[] density() {
        float[] values = new float[material.length];
        for (int index = 0; index < values.length; index++) {
            values[index] = material[index] != FREE ? 1.0F : 0.0F;
        }
        return values;
    }

    /** Desplazamientos de los vecinos de cara y de los 26 de alrededor. */
    static final class Neighbors {
        static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        static final int[][] AROUND = around();

        private Neighbors() {
        }

        private static int[][] around() {
            int[][] offsets = new int[26][];
            int count = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            offsets[count++] = new int[]{dx, dy, dz};
                        }
                    }
                }
            }
            return offsets;
        }
    }
}
