package com.komainu.moxia.sculpt.model;

/**
 * Normal de la superficie calculada con los vecinos: dentro de una bola de radio {@code r}, cada celda libre tira
 * hacia ella y cada celda con masa empuja hacia fuera. En un plano las componentes laterales se anulan y queda la
 * perpendicular; en una esfera, la dirección radial. Las celdas fuera de la captura no cuentan.
 */
public final class SurfaceNormals {
    private SurfaceNormals() {
    }

    /** Normal unitaria hacia fuera en la celda {@code (x, y, z)}, o {@code {0, 0, 0}} si no hay superficie cerca. */
    public static double[] at(ModelGrid grid, int x, int y, int z, int radius) {
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int squared = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance == 0 || distance > squared || !grid.contains(x + dx, y + dy, z + dz)) {
                        continue;
                    }
                    double sign = grid.solid(x + dx, y + dy, z + dz) ? -1.0 : 1.0;
                    sumX += sign * dx;
                    sumY += sign * dy;
                    sumZ += sign * dz;
                }
            }
        }
        return normalize(sumX, sumY, sumZ);
    }

    /**
     * Normal media de la superficie bajo el pincel: la de cada celda de superficie dentro de la bola de radio
     * {@code radius} alrededor de {@code center}, pesada con la caída. {@code {0, 0, 0}} si no hay superficie.
     */
    public static double[] average(ModelGrid grid, int centerX, int centerY, int centerZ, int radius,
                                   Weight weight) {
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int estimate = Math.clamp(radius / 3, 2, 3);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int x = centerX + dx;
                    int y = centerY + dy;
                    int z = centerZ + dz;
                    double factor = weight.at(x, y, z);
                    if (factor <= 0.0 || !grid.surface(x, y, z)) {
                        continue;
                    }
                    double[] normal = at(grid, x, y, z, estimate);
                    sumX += normal[0] * factor;
                    sumY += normal[1] * factor;
                    sumZ += normal[2] * factor;
                }
            }
        }
        return normalize(sumX, sumY, sumZ);
    }

    static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-9) {
            return new double[]{0.0, 0.0, 0.0};
        }
        return new double[]{x / length, y / length, z / length};
    }

    /** Peso del pincel en una celda (la caída): 0 fuera. */
    @FunctionalInterface
    public interface Weight {
        double at(int x, int y, int z);
    }
}
