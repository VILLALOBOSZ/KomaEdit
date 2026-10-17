package com.komainu.moxia.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Icosfera deformada (la «roca» de los constructores): un icosaedro subdividido cuyos vértices se alejan o acercan
 * al centro al azar, ajustado a su caja y con la mitad de arriba aplastada si se pide {@code Platform}.
 *
 * <p>Los vértices solo se mueven a lo largo de su radio, así que el sólido es estrellado respecto al centro y cada
 * dirección cae en un solo triángulo. Para saber si un punto está dentro se baja por el árbol de subdivisión
 * (20 caras y luego 4 hijos por nivel) hasta su triángulo y se mira de qué lado de su plano queda.</p>
 */
public final class Icosphere implements ProceduralShapes.Implicit {
    public static final int MAX_SUBDIVISIONS = 4;

    private final List<double[]> directions = new ArrayList<>();
    private double[] positions;
    private final Face[] roots;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double halfX;
    private final double halfY;
    private final double halfZ;
    private final double platform;
    private final int faces;

    /**
     * @param randomness 0 (icosfera perfecta) a 1 (muy deformada)
     * @param subdivisions 0 (pocos polígonos) a 4; más polígonos también suavizan la deformación
     * @param platform 0 a 1: cuánto se aplasta la mitad de arriba
     */
    public Icosphere(long seed, double randomness, int subdivisions, double platform) {
        if (subdivisions < 0 || subdivisions > MAX_SUBDIVISIONS) {
            throw new IllegalArgumentException("Subdivisions va de 0 a " + MAX_SUBDIVISIONS);
        }
        this.platform = Math.clamp(platform, 0.0, 0.95);
        double t = (1.0 + Math.sqrt(5.0)) / 2.0;
        double[][] base = {
                {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
                {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}
        };
        for (double[] vertex : base) {
            directions.add(unit(vertex[0], vertex[1], vertex[2]));
        }
        int[][] triangles = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
        };
        roots = new Face[triangles.length];
        Map<Long, Integer> midpoints = new HashMap<>();
        for (int index = 0; index < triangles.length; index++) {
            int[] triangle = triangles[index];
            roots[index] = build(triangle[0], triangle[1], triangle[2], subdivisions, midpoints);
        }
        faces = triangles.length << (2 * subdivisions);

        SplittableRandom random = new SplittableRandom(seed ^ 0x1C05_FE7EL);
        double amplitude = Math.clamp(randomness, 0.0, 1.0) * 0.45 / Math.pow(1.0 + subdivisions, 1.5);
        positions = new double[directions.size() * 3];
        double lowX = Double.POSITIVE_INFINITY;
        double lowY = Double.POSITIVE_INFINITY;
        double lowZ = Double.POSITIVE_INFINITY;
        double highX = Double.NEGATIVE_INFINITY;
        double highY = Double.NEGATIVE_INFINITY;
        double highZ = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < directions.size(); index++) {
            double[] direction = directions.get(index);
            double radius = 1.0 + amplitude * (random.nextDouble() * 2.0 - 1.0);
            double x = direction[0] * radius;
            double y = direction[1] * radius;
            double z = direction[2] * radius;
            positions[index * 3] = x;
            positions[index * 3 + 1] = y;
            positions[index * 3 + 2] = z;
            lowX = Math.min(lowX, x);
            lowY = Math.min(lowY, y);
            lowZ = Math.min(lowZ, z);
            highX = Math.max(highX, x);
            highY = Math.max(highY, y);
            highZ = Math.max(highZ, z);
        }
        centerX = (lowX + highX) * 0.5;
        centerY = (lowY + highY) * 0.5;
        centerZ = (lowZ + highZ) * 0.5;
        halfX = (highX - lowX) * 0.5;
        halfY = (highY - lowY) * 0.5;
        halfZ = (highZ - lowZ) * 0.5;
    }

    public int faces() {
        return faces;
    }

    public int vertices() {
        return directions.size();
    }

    private Face build(int a, int b, int c, int levels, Map<Long, Integer> midpoints) {
        if (levels == 0) {
            return new Face(a, b, c, null);
        }
        int ab = midpoint(a, b, midpoints);
        int bc = midpoint(b, c, midpoints);
        int ca = midpoint(c, a, midpoints);
        return new Face(a, b, c, new Face[]{
                build(a, ab, ca, levels - 1, midpoints),
                build(ab, b, bc, levels - 1, midpoints),
                build(ca, bc, c, levels - 1, midpoints),
                build(ab, bc, ca, levels - 1, midpoints)
        });
    }

    private int midpoint(int a, int b, Map<Long, Integer> midpoints) {
        long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
        Integer existing = midpoints.get(key);
        if (existing != null) {
            return existing;
        }
        double[] first = directions.get(a);
        double[] second = directions.get(b);
        directions.add(unit(first[0] + second[0], first[1] + second[1], first[2] + second[2]));
        int index = directions.size() - 1;
        midpoints.put(key, index);
        return index;
    }

    private static double[] unit(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / length, y / length, z / length};
    }

    @Override
    public double value(double x, double y, double z) {
        // Deshace la plataforma y el ajuste a la caja: así se trabaja con la icosfera tal cual se deformó.
        double unsquashed = y > 0.0 ? y / (1.0 - platform) : y;
        double px = centerX + x * halfX;
        double py = centerY + unsquashed * halfY;
        double pz = centerZ + z * halfZ;
        if (px * px + py * py + pz * pz < 1.0E-18) {
            return 1.0;
        }
        Face face = null;
        for (Face root : roots) {
            if (root.contains(directions, px, py, pz) >= -1.0E-12) {
                face = root;
                break;
            }
        }
        if (face == null) {
            face = best(roots, px, py, pz);
        }
        while (face.children != null) {
            Face next = null;
            for (Face child : face.children) {
                if (child.contains(directions, px, py, pz) >= -1.0E-12) {
                    next = child;
                    break;
                }
            }
            face = next == null ? best(face.children, px, py, pz) : next;
        }
        int a = face.a * 3;
        int b = face.b * 3;
        int c = face.c * 3;
        double e1x = positions[b] - positions[a];
        double e1y = positions[b + 1] - positions[a + 1];
        double e1z = positions[b + 2] - positions[a + 2];
        double e2x = positions[c] - positions[a];
        double e2y = positions[c + 1] - positions[a + 1];
        double e2z = positions[c + 2] - positions[a + 2];
        double nx = e1y * e2z - e1z * e2y;
        double ny = e1z * e2x - e1x * e2z;
        double nz = e1x * e2y - e1y * e2x;
        // La normal apunta hacia fuera porque las caras van en sentido antihorario vistas desde fuera.
        double side = nx * (px - positions[a]) + ny * (py - positions[a + 1]) + nz * (pz - positions[a + 2]);
        return side <= 0.0 ? 1.0 : 0.0;
    }

    private Face best(Face[] candidates, double x, double y, double z) {
        Face best = candidates[0];
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Face candidate : candidates) {
            double value = candidate.contains(directions, x, y, z);
            if (value > bestValue) {
                bestValue = value;
                best = candidate;
            }
        }
        return best;
    }

    private record Face(int a, int b, int c, Face[] children) {
        /** El menor de los tres productos mixtos: no negativo si la dirección cae dentro del cono de la cara. */
        double contains(List<double[]> directions, double x, double y, double z) {
            double[] first = directions.get(a);
            double[] second = directions.get(b);
            double[] third = directions.get(c);
            double ab = triple(first, second, x, y, z);
            double bc = triple(second, third, x, y, z);
            double ca = triple(third, first, x, y, z);
            return Math.min(ab, Math.min(bc, ca));
        }

        private static double triple(double[] u, double[] v, double x, double y, double z) {
            return (u[1] * v[2] - u[2] * v[1]) * x + (u[2] * v[0] - u[0] * v[2]) * y + (u[0] * v[1] - u[1] * v[0]) * z;
        }
    }
}
