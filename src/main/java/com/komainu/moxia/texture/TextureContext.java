package com.komainu.moxia.texture;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionSnapshot;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * Lo que ve una textura: los bloques a texturizar de la foto (los de la selección que no son aire y pasan la
 * máscara), cuáles de ellos son superficie y medidas comunes calculadas una sola vez (sólidos acumulados, normales).
 *
 * <p>Es Java puro sobre {@link RegionSnapshot}: no toca el mundo, así que sirve en el worker y en los tests.</p>
 */
public final class TextureContext {
    /** Volumen máximo de la foto que admite una textura (las tablas auxiliares ocupan unos 8 bytes por bloque). */
    public static final long MAX_VOLUME = 8_000_000L;

    private final RegionSnapshot snapshot;
    private final Settings settings;
    private final BooleanSupplier cancelled;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] grid;
    private final int[] xs;
    private final int[] ys;
    private final int[] zs;
    private final boolean[] surface;
    private final int surfaceCount;
    private int[] solidSums;
    private double[][] normals;
    private int normalRadius = -1;
    private int checks;

    public TextureContext(RegionSnapshot snapshot, Filter filter, Settings settings, BooleanSupplier cancelled) {
        if (snapshot.volume() > MAX_VOLUME) {
            throw new IllegalArgumentException("La selección es demasiado grande para texturizar ("
                    + ProgressLine.group(snapshot.volume()) + " bloques con su margen; máximo " + MAX_VOLUME + ")");
        }
        this.snapshot = snapshot;
        this.settings = settings;
        this.cancelled = cancelled;
        this.sizeX = snapshot.maximumX() - snapshot.minimumX() + 1;
        this.sizeY = snapshot.maximumY() - snapshot.minimumY() + 1;
        this.sizeZ = snapshot.maximumZ() - snapshot.minimumZ() + 1;
        this.grid = new int[(int) snapshot.volume()];
        Arrays.fill(grid, -1);
        int count = 0;
        int[] tx = new int[1024];
        int[] ty = new int[1024];
        int[] tz = new int[1024];
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    checkCancelled();
                    if (!snapshot.selected(x, y, z) || snapshot.isAir(x, y, z) || !filter.test(x, y, z)) {
                        continue;
                    }
                    if (count == tx.length) {
                        tx = Arrays.copyOf(tx, count * 2);
                        ty = Arrays.copyOf(ty, count * 2);
                        tz = Arrays.copyOf(tz, count * 2);
                    }
                    tx[count] = x;
                    ty[count] = y;
                    tz[count] = z;
                    grid[local(x, y, z)] = count;
                    count++;
                }
            }
        }
        this.xs = Arrays.copyOf(tx, count);
        this.ys = Arrays.copyOf(ty, count);
        this.zs = Arrays.copyOf(tz, count);
        this.surface = new boolean[count];
        int surfaces = 0;
        for (int index = 0; index < count; index++) {
            if (snapshot.exposedFaces(xs[index], ys[index], zs[index]) > 0) {
                surface[index] = true;
                surfaces++;
            }
        }
        this.surfaceCount = surfaces;
    }

    public RegionSnapshot snapshot() {
        return snapshot;
    }

    public Settings settings() {
        return settings;
    }

    public int size() {
        return xs.length;
    }

    public int surfaceCount() {
        return surfaceCount;
    }

    public int x(int index) {
        return xs[index];
    }

    public int y(int index) {
        return ys[index];
    }

    public int z(int index) {
        return zs[index];
    }

    public boolean surface(int index) {
        return surface[index];
    }

    /** Índice del bloque a texturizar en esa posición o -1. */
    public int index(int x, int y, int z) {
        return snapshot.contains(x, y, z) ? grid[local(x, y, z)] : -1;
    }

    public boolean solid(int x, int y, int z) {
        return snapshot.isSolid(x, y, z);
    }

    public void checkCancelled() {
        if ((checks++ & 4_095) == 0 && cancelled.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    /** Valores sin calcular: {@code NaN} significa «no tocar». */
    public double[] empty() {
        double[] values = new double[size()];
        Arrays.fill(values, Double.NaN);
        return values;
    }

    /** Bloques sólidos en la caja de radio {@code radius} centrada en el bloque (incluido él), por sumas acumuladas. */
    public int solidInBox(int x, int y, int z, int radius) {
        int[] sums = solidSums();
        int x0 = Math.max(0, x - radius - snapshot.minimumX());
        int y0 = Math.max(0, y - radius - snapshot.minimumY());
        int z0 = Math.max(0, z - radius - snapshot.minimumZ());
        int x1 = Math.min(sizeX, x + radius - snapshot.minimumX() + 1);
        int y1 = Math.min(sizeY, y + radius - snapshot.minimumY() + 1);
        int z1 = Math.min(sizeZ, z + radius - snapshot.minimumZ() + 1);
        if (x0 >= x1 || y0 >= y1 || z0 >= z1) {
            return 0;
        }
        return sums[sum(x1, y1, z1)] - sums[sum(x0, y1, z1)] - sums[sum(x1, y0, z1)] - sums[sum(x1, y1, z0)]
                + sums[sum(x0, y0, z1)] + sums[sum(x0, y1, z0)] + sums[sum(x1, y0, z0)] - sums[sum(x0, y0, z0)];
    }

    /**
     * Normal aproximada de cada bloque de superficie: la media de las direcciones hacia el aire dentro de una esfera
     * de radio {@code radius}. Apunta fuera del material; en el interior queda a cero.
     */
    public double[][] normals(int radius) {
        int clamped = Math.clamp(radius, 1, 6);
        if (normals != null && normalRadius == clamped) {
            return normals;
        }
        double[][] result = new double[size()][];
        double limit = (clamped + 0.5) * (clamped + 0.5);
        for (int index = 0; index < size(); index++) {
            checkCancelled();
            if (!surface[index]) {
                result[index] = new double[3];
                continue;
            }
            double nx = 0.0;
            double ny = 0.0;
            double nz = 0.0;
            int x = xs[index];
            int y = ys[index];
            int z = zs[index];
            for (int dy = -clamped; dy <= clamped; dy++) {
                for (int dz = -clamped; dz <= clamped; dz++) {
                    for (int dx = -clamped; dx <= clamped; dx++) {
                        int distance = dx * dx + dy * dy + dz * dz;
                        if (distance == 0 || distance > limit || snapshot.isSolid(x + dx, y + dy, z + dz)) {
                            continue;
                        }
                        double weight = 1.0 / Math.sqrt(distance);
                        nx += dx * weight;
                        ny += dy * weight;
                        nz += dz * weight;
                    }
                }
            }
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1.0E-9) {
                // Rodeado de aire por igual (un bloque suelto): se toma la cara de arriba.
                result[index] = new double[]{0.0, 1.0, 0.0};
            } else {
                result[index] = new double[]{nx / length, ny / length, nz / length};
            }
        }
        normals = result;
        normalRadius = clamped;
        return result;
    }

    /**
     * Media de un campo sobre los bloques de superficie vecinos (gaussiana de desviación {@code radius}, hasta dos
     * desviaciones); los {@code NaN} no cuentan. Con radio 0 devuelve una copia.
     */
    public double[] smoothSurface(double[] values, double radius) {
        return smooth(values, radius, true);
    }

    /**
     * Media ponderada (gaussiana de desviación {@code radius}) de un campo entre bloques a texturizar vecinos.
     *
     * @param sameKind si es {@code true}, la superficie y el interior no se mezclan entre sí
     */
    public double[] smooth(double[] values, double radius, boolean sameKind) {
        double[] result = values.clone();
        if (radius <= 0.0) {
            return result;
        }
        int reach = (int) Math.ceil(Math.min(radius * 2.0, 8.0));
        double sigma = Math.max(0.35, radius);
        double[] weights = new double[reach * reach * 3 + 1];
        for (int distance = 0; distance < weights.length; distance++) {
            weights[distance] = Math.exp(-distance / (2.0 * sigma * sigma));
        }
        for (int index = 0; index < size(); index++) {
            checkCancelled();
            if (Double.isNaN(values[index])) {
                continue;
            }
            double total = 0.0;
            double weightSum = 0.0;
            int x = xs[index];
            int y = ys[index];
            int z = zs[index];
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int neighbour = index(x + dx, y + dy, z + dz);
                        if (neighbour < 0 || Double.isNaN(values[neighbour])
                                || (sameKind && surface[neighbour] != surface[index])) {
                            continue;
                        }
                        double weight = weights[dx * dx + dy * dy + dz * dz];
                        total += values[neighbour] * weight;
                        weightSum += weight;
                    }
                }
            }
            result[index] = weightSum > 0.0 ? total / weightSum : values[index];
        }
        return result;
    }

    private int[] solidSums() {
        if (solidSums != null) {
            return solidSums;
        }
        int strideX = sizeX + 1;
        int strideZ = sizeZ + 1;
        int[] sums = new int[strideX * strideZ * (sizeY + 1)];
        for (int ly = 1; ly <= sizeY; ly++) {
            for (int lz = 1; lz <= sizeZ; lz++) {
                checkCancelled();
                for (int lx = 1; lx <= sizeX; lx++) {
                    int value = snapshot.isSolid(snapshot.minimumX() + lx - 1, snapshot.minimumY() + ly - 1,
                            snapshot.minimumZ() + lz - 1) ? 1 : 0;
                    sums[(ly * strideZ + lz) * strideX + lx] = value
                            + sums[((ly - 1) * strideZ + lz) * strideX + lx]
                            + sums[(ly * strideZ + lz - 1) * strideX + lx]
                            + sums[(ly * strideZ + lz) * strideX + lx - 1]
                            - sums[((ly - 1) * strideZ + lz - 1) * strideX + lx]
                            - sums[((ly - 1) * strideZ + lz) * strideX + lx - 1]
                            - sums[(ly * strideZ + lz - 1) * strideX + lx - 1]
                            + sums[((ly - 1) * strideZ + lz - 1) * strideX + lx - 1];
                }
            }
        }
        solidSums = sums;
        return sums;
    }

    private int sum(int lx, int ly, int lz) {
        return (ly * (sizeZ + 1) + lz) * (sizeX + 1) + lx;
    }

    private int local(int x, int y, int z) {
        return ((y - snapshot.minimumY()) * sizeZ + (z - snapshot.minimumZ())) * sizeX + (x - snapshot.minimumX());
    }

    /** Qué bloques se texturizan (la máscara del comando). */
    @FunctionalInterface
    public interface Filter {
        boolean test(int x, int y, int z);

        Filter ALL = (x, y, z) -> true;
    }

    /**
     * Datos del jugador que algunas texturas necesitan: dónde está (luz puntual) y la semilla base para las que
     * no fijan una.
     */
    public record Settings(double playerX, double playerY, double playerZ, long seed) {
        public static Settings at(double x, double y, double z, long seed) {
            return new Settings(x, y, z, seed);
        }
    }
}
