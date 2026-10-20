package com.komainu.moxia.roof;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/**
 * Planta del tejado: las columnas (x, z) que cubre y la altura a la que empieza ({@code baseY}, justo encima de
 * lo alto de los muros). Incluye los muros: su borde es la cara de fuera de la pared. Es inmutable.
 */
public final class Footprint {
    /** Lado máximo de la planta, en bloques. */
    public static final int MAX_SIDE = 512;

    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final int baseY;
    private final boolean[] cells;
    private final int area;

    private Footprint(int minX, int minZ, int sizeX, int sizeZ, int baseY, boolean[] cells) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.baseY = baseY;
        this.cells = cells;
        int count = 0;
        for (boolean cell : cells) {
            if (cell) {
                count++;
            }
        }
        this.area = count;
    }

    public static Footprint rectangle(int x1, int z1, int x2, int z2, int baseY) {
        return of(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), baseY, (x, z) -> true);
    }

    /** Planta con las columnas de la caja que cumplen {@code inside}; se recorta a lo ocupado. */
    public static Footprint of(int minX, int minZ, int maxX, int maxZ, int baseY, BiPredicate<Integer, Integer> inside) {
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        if (sizeX <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("La planta está vacía");
        }
        if (sizeX > MAX_SIDE || sizeZ > MAX_SIDE) {
            throw new IllegalArgumentException("La planta es demasiado grande para un tejado: " + sizeX + "×" + sizeZ
                    + " (máximo " + MAX_SIDE + "×" + MAX_SIDE + ")");
        }
        boolean[] cells = new boolean[sizeX * sizeZ];
        int lowX = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                if (inside.test(minX + x, minZ + z)) {
                    cells[z * sizeX + x] = true;
                    lowX = Math.min(lowX, x);
                    lowZ = Math.min(lowZ, z);
                    highX = Math.max(highX, x);
                    highZ = Math.max(highZ, z);
                }
            }
        }
        if (lowX == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("La planta está vacía");
        }
        int trimmedX = highX - lowX + 1;
        int trimmedZ = highZ - lowZ + 1;
        boolean[] trimmed = new boolean[trimmedX * trimmedZ];
        for (int z = 0; z < trimmedZ; z++) {
            System.arraycopy(cells, (z + lowZ) * sizeX + lowX, trimmed, z * trimmedX, trimmedX);
        }
        return new Footprint(minX + lowX, minZ + lowZ, trimmedX, trimmedZ, baseY, trimmed);
    }

    /**
     * Saca la planta de los muros que hay en la caja: lo alto de lo construido marca la altura, las columnas con
     * bloque en las dos capas de arriba son muro y lo que los muros encierran es el interior. Así salen plantas en
     * L, T o U aunque la selección sea un cuboide.
     *
     * @throws IllegalArgumentException si no hay muros o no encierran nada
     */
    public static Detection detect(RoofWorld world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        if (sizeX > MAX_SIDE + 2 || sizeZ > MAX_SIDE + 2) {
            throw new IllegalArgumentException("La selección es demasiado grande para buscar muros");
        }
        int top = Integer.MIN_VALUE;
        for (int y = maxY; y >= minY && top == Integer.MIN_VALUE; y--) {
            for (int z = minZ; z <= maxZ && top == Integer.MIN_VALUE; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (world.solid(x, y, z)) {
                        top = y;
                        break;
                    }
                }
            }
        }
        if (top == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("No hay muros en la selección: selecciona la casa entera o quita -d");
        }
        boolean[] wall = new boolean[sizeX * sizeZ];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                for (int y = top; y >= Math.max(minY, top - 1); y--) {
                    if (world.solid(minX + x, y, minZ + z)) {
                        wall[z * sizeX + x] = true;
                        break;
                    }
                }
            }
        }
        // Lo que se alcanza desde el borde de la caja sin cruzar un muro es el exterior.
        boolean[] outside = new boolean[sizeX * sizeZ];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                if ((x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1) && !wall[z * sizeX + x]) {
                    outside[z * sizeX + x] = true;
                    queue.add(z * sizeX + x);
                }
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int x = index % sizeX;
            int z = index / sizeX;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] step : steps) {
                int nx = x + step[0];
                int nz = z + step[1];
                if (nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ) {
                    continue;
                }
                int next = nz * sizeX + nx;
                if (!outside[next] && !wall[next]) {
                    outside[next] = true;
                    queue.add(next);
                }
            }
        }
        boolean encloses = false;
        for (int index = 0; index < wall.length; index++) {
            if (!wall[index] && !outside[index]) {
                encloses = true;
                break;
            }
        }
        if (!encloses) {
            throw new IllegalArgumentException("Los muros de la selección no cierran ningún interior (¿falta un "
                    + "trozo de muro arriba?): cierra el contorno o selecciona la planta sin -d");
        }
        Footprint footprint = of(minX, minZ, maxX, maxZ, top + 1, (x, z) -> !outside[(z - minZ) * sizeX + x - minX]);
        return new Detection(footprint, wallMaterial(world, footprint));
    }

    /**
     * Material más repetido en lo alto de los muros (la fila de debajo de {@code baseY} en el borde de la planta),
     * o {@code null} si ahí no hay nada. En caso de empate gana el primero por orden alfabético.
     */
    public static String wallMaterial(RoofWorld world, Footprint footprint) {
        Map<String, Integer> counts = new TreeMap<>();
        int y = footprint.baseY() - 1;
        for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
            for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
                if (!footprint.isBoundary(x, z) || !world.solid(x, y, z)) {
                    continue;
                }
                String id = world.blockId(x, y, z);
                if (id != null) {
                    counts.merge(id, 1, Integer::sum);
                }
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /** Planta detectada y el material de sus muros ({@code null} si no se pudo leer). */
    public record Detection(Footprint footprint, String wallMaterial) {
    }

    public boolean contains(int x, int z) {
        int localX = x - minX;
        int localZ = z - minZ;
        return localX >= 0 && localZ >= 0 && localX < sizeX && localZ < sizeZ && cells[localZ * sizeX + localX];
    }

    /** Columna de la planta con algún vecino (de los cuatro) fuera: donde está el muro. */
    public boolean isBoundary(int x, int z) {
        return contains(x, z)
                && (!contains(x + 1, z) || !contains(x - 1, z) || !contains(x, z + 1) || !contains(x, z - 1));
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return minX + sizeX - 1;
    }

    public int maxZ() {
        return minZ + sizeZ - 1;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int baseY() {
        return baseY;
    }

    public int area() {
        return area;
    }

    /** La misma planta a otra altura. */
    public Footprint atBase(int y) {
        return new Footprint(minX, minZ, sizeX, sizeZ, y, cells);
    }
}
