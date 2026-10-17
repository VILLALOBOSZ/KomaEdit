package com.komainu.moxia.deform;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Índice espacial de puntos en cubetas regulares para buscar el más cercano y el segundo más cercano (celdas de
 * Voronoi). Busca en anillos de cubetas cada vez más anchos hasta que ningún punto más lejano puede ganar.
 */
final class PointIndex {
    private final double[] points;
    private final double cell;
    private final Long2ObjectOpenHashMap<IntArrayList> buckets = new Long2ObjectOpenHashMap<>();
    private final int maximumRing;

    /** @param points coordenadas x, y, z seguidas */
    PointIndex(double[] points, double cell) {
        if (points.length == 0 || points.length % 3 != 0) {
            throw new IllegalArgumentException("Hacen falta puntos");
        }
        this.points = points;
        this.cell = Math.max(1.0, cell);
        int lowX = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int lowY = Integer.MAX_VALUE;
        int highY = Integer.MIN_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (int point = 0; point < count(); point++) {
            int bx = bucket(points[point * 3]);
            int by = bucket(points[point * 3 + 1]);
            int bz = bucket(points[point * 3 + 2]);
            buckets.computeIfAbsent(key(bx, by, bz), ignored -> new IntArrayList(4)).add(point);
            lowX = Math.min(lowX, bx);
            highX = Math.max(highX, bx);
            lowY = Math.min(lowY, by);
            highY = Math.max(highY, by);
            lowZ = Math.min(lowZ, bz);
            highZ = Math.max(highZ, bz);
        }
        maximumRing = Math.max(highX - lowX, Math.max(highY - lowY, highZ - lowZ)) + 2;
    }

    int count() {
        return points.length / 3;
    }

    double x(int point) {
        return points[point * 3];
    }

    double y(int point) {
        return points[point * 3 + 1];
    }

    double z(int point) {
        return points[point * 3 + 2];
    }

    /**
     * @param result se rellena con {@code {primero, segundo}}; el segundo es -1 si solo hay un punto
     * @return distancia al cuadrado del primero
     */
    double nearestTwo(double x, double y, double z, int[] result) {
        int bx = bucket(x);
        int by = bucket(y);
        int bz = bucket(z);
        int first = -1;
        int second = -1;
        double firstDistance = Double.POSITIVE_INFINITY;
        double secondDistance = Double.POSITIVE_INFINITY;
        for (int ring = 0; ring <= maximumRing; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != ring) {
                            continue;
                        }
                        IntArrayList list = buckets.get(key(bx + dx, by + dy, bz + dz));
                        if (list == null) {
                            continue;
                        }
                        for (int item = 0; item < list.size(); item++) {
                            int point = list.getInt(item);
                            double ox = points[point * 3] - x;
                            double oy = points[point * 3 + 1] - y;
                            double oz = points[point * 3 + 2] - z;
                            double distance = ox * ox + oy * oy + oz * oz;
                            if (distance < firstDistance) {
                                second = first;
                                secondDistance = firstDistance;
                                first = point;
                                firstDistance = distance;
                            } else if (distance < secondDistance) {
                                second = point;
                                secondDistance = distance;
                            }
                        }
                    }
                }
            }
            // Todo lo que queda fuera de este anillo está al menos a ring * cell.
            double reach = ring * cell;
            if (second >= 0 && secondDistance <= reach * reach) {
                break;
            }
            if (second < 0 && first >= 0 && count() == 1) {
                break;
            }
        }
        result[0] = first;
        result[1] = second;
        return firstDistance;
    }

    /** Distancia del punto al plano bisector entre dos sitios (positiva en el lado del primero). */
    double bisector(double x, double y, double z, int first, int second) {
        double ax = x(first);
        double ay = y(first);
        double az = z(first);
        double nx = x(second) - ax;
        double ny = y(second) - ay;
        double nz = z(second) - az;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-9) {
            return Double.POSITIVE_INFINITY;
        }
        double along = ((x - ax) * nx + (y - ay) * ny + (z - az) * nz) / length;
        return length / 2.0 - along;
    }

    private int bucket(double value) {
        return (int) Math.floor(value / cell);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
}
