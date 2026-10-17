package com.komainu.moxia.deform;

import com.komainu.moxia.region.RegionSnapshot;

/**
 * La caja capturada de una foto vista como rejilla: índices, ocupación y materiales en arrays planos para que
 * los cálculos de deformación no pregunten bloque a bloque a la foto.
 *
 * <p>Un bloque está <em>ocupado</em> si no es aire ni líquido: es lo que se mueve, se talla o se suaviza. Los
 * líquidos cuentan como hueco, y donde una operación dejaría aire sobre un líquido el líquido se conserva.</p>
 */
public final class Volume {
    final int minimumX;
    final int minimumY;
    final int minimumZ;
    final int sizeX;
    final int sizeY;
    final int sizeZ;
    /** Id crudo de cada posición ocupada; 0 en las vacías. */
    final int[] material;
    final boolean[] selected;

    private Volume(RegionSnapshot snapshot) {
        minimumX = snapshot.minimumX();
        minimumY = snapshot.minimumY();
        minimumZ = snapshot.minimumZ();
        sizeX = snapshot.maximumX() - minimumX + 1;
        sizeY = snapshot.maximumY() - minimumY + 1;
        sizeZ = snapshot.maximumZ() - minimumZ + 1;
        int total = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        material = new int[total];
        selected = new boolean[total];
        int index = 0;
        for (int y = minimumY; y < minimumY + sizeY; y++) {
            for (int z = minimumZ; z < minimumZ + sizeZ; z++) {
                for (int x = minimumX; x < minimumX + sizeX; x++) {
                    RegionSnapshot.StateInfo info = snapshot.info(x, y, z);
                    material[index] = occupied(info) ? info.rawId() : 0;
                    selected[index] = snapshot.selected(x, y, z);
                    index++;
                }
            }
        }
    }

    public static Volume of(RegionSnapshot snapshot) {
        return new Volume(snapshot);
    }

    static boolean occupied(RegionSnapshot.StateInfo info) {
        return !info.air() && !info.liquid();
    }

    public int size() {
        return material.length;
    }

    boolean contains(int x, int y, int z) {
        return x >= minimumX && x < minimumX + sizeX
                && y >= minimumY && y < minimumY + sizeY
                && z >= minimumZ && z < minimumZ + sizeZ;
    }

    int index(int x, int y, int z) {
        return ((y - minimumY) * sizeZ + (z - minimumZ)) * sizeX + (x - minimumX);
    }

    int x(int index) {
        return minimumX + index % sizeX;
    }

    int z(int index) {
        return minimumZ + (index / sizeX) % sizeZ;
    }

    int y(int index) {
        return minimumY + index / (sizeX * sizeZ);
    }

    /** Material ocupado en una posición; 0 si está vacía o fuera de la captura. */
    int materialAt(int x, int y, int z) {
        return contains(x, y, z) ? material[index(x, y, z)] : 0;
    }

    /** Material ocupado y dentro de la selección: lo que las deformaciones toman como entrada. */
    int sourceAt(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return 0;
        }
        int index = index(x, y, z);
        return selected[index] ? material[index] : 0;
    }

    /** Lo mismo en un punto continuo: el bloque que lo contiene. */
    int sourceAt(double x, double y, double z) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            return 0;
        }
        return sourceAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    boolean selected(int x, int y, int z) {
        return contains(x, y, z) && selected[index(x, y, z)];
    }

    boolean[] occupancy() {
        boolean[] occupancy = new boolean[material.length];
        for (int index = 0; index < material.length; index++) {
            occupancy[index] = material[index] != 0;
        }
        return occupancy;
    }

    /** Caja de la selección ({@code minX, minY, minZ, maxX, maxY, maxZ}) o {@code null} si no hay selección. */
    int[] selectionBounds() {
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        boolean any = false;
        for (int index = 0; index < selected.length; index++) {
            if (!selected[index]) {
                continue;
            }
            any = true;
            int x = x(index);
            int y = y(index);
            int z = z(index);
            bounds[0] = Math.min(bounds[0], x);
            bounds[1] = Math.min(bounds[1], y);
            bounds[2] = Math.min(bounds[2], z);
            bounds[3] = Math.max(bounds[3], x);
            bounds[4] = Math.max(bounds[4], y);
            bounds[5] = Math.max(bounds[5], z);
        }
        return any ? bounds : null;
    }
}
