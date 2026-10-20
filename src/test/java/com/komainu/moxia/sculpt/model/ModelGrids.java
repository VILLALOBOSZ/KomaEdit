package com.komainu.moxia.sculpt.model;

/** Rejillas de prueba: planos, esferas, cajas y paredes con materiales numerados. */
final class ModelGrids {
    static final int STONE = 1;
    static final int GRASS = 2;
    static final int DIRT = 3;
    static final int SAND = 4;

    private ModelGrids() {
    }

    /** Suelo de piedra con hierba encima: masa en {@code y < height}, centrado en el origen. */
    static ModelGrid plane(int half, int sizeY, int height) {
        ModelGrid grid = ModelGrid.create(-half, 0, -half, half * 2 + 1, sizeY, half * 2 + 1);
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = 0; y < height; y++) {
                    grid.set(x, y, z, y == height - 1 ? GRASS : (y >= height - 3 ? DIRT : STONE));
                }
            }
        }
        return grid;
    }

    /** Esfera maciza de radio {@code radius} centrada en el origen (centros de bloque a menos del radio). */
    static ModelGrid sphere(int half, double radius) {
        ModelGrid grid = ModelGrid.create(-half, -half, -half, half * 2 + 1, half * 2 + 1, half * 2 + 1);
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        grid.set(x, y, z, STONE);
                    }
                }
            }
        }
        return grid;
    }

    /** Caja de material dado entre dos esquinas incluidas. */
    static void box(ModelGrid grid, int x0, int y0, int z0, int x1, int y1, int z1, int material) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (grid.contains(x, y, z)) {
                        grid.set(x, y, z, material);
                    }
                }
            }
        }
    }

    /** Copia exacta (materiales y máscara) para comparar antes y después. */
    static ModelGrid copy(ModelGrid grid) {
        ModelGrid copy = ModelGrid.create(grid.minimumX(), grid.minimumY(), grid.minimumZ(), grid.sizeX(),
                grid.sizeY(), grid.sizeZ());
        for (int index = 0; index < grid.size(); index++) {
            copy.set(grid.x(index), grid.y(index), grid.z(index), grid.materialAt(index));
            copy.setEditable(grid.x(index), grid.y(index), grid.z(index),
                    grid.editable(grid.x(index), grid.y(index), grid.z(index)));
        }
        return copy;
    }

    /** Cuántas piezas conexas (por caras) tiene la masa. */
    static int components(ModelGrid grid) {
        boolean[] seen = new boolean[grid.size()];
        int count = 0;
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int start = 0; start < grid.size(); start++) {
            if (seen[start] || grid.materialAt(start) == ModelGrid.FREE) {
                continue;
            }
            count++;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int index = queue.poll();
                int x = grid.x(index);
                int y = grid.y(index);
                int z = grid.z(index);
                for (int[] offset : ModelGrid.Neighbors.FACES) {
                    int nx = x + offset[0];
                    int ny = y + offset[1];
                    int nz = z + offset[2];
                    if (!grid.contains(nx, ny, nz)) {
                        continue;
                    }
                    int next = grid.index(nx, ny, nz);
                    if (!seen[next] && grid.materialAt(next) != ModelGrid.FREE) {
                        seen[next] = true;
                        queue.add(next);
                    }
                }
            }
        }
        return count;
    }

    static final Runnable NO_CANCEL = () -> {
    };
}
