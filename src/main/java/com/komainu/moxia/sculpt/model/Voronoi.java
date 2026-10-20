package com.komainu.moxia.sculpt.model;

/**
 * Voronoi de sitios repartidos en una rejilla (uno por celda de lado {@code size}, desplazado al azar dentro de
 * ella) y distancia de un punto a la arista más cercana: la distancia a los planos bisectores entre su sitio y los
 * vecinos (método de Íñigo Quílez), medida en la norma que da planos digitales sin huecos (la euclídea dividida por
 * la norma L1 de la normal unitaria). Con un eje, el Voronoi es 2D en el plano perpendicular y las aristas son
 * paredes a lo largo de ese eje (columnas, como el basalto).
 */
final class Voronoi {
    private Voronoi() {
    }

    static double edgeDistance(double x, double y, double z, double size, ModelMode.Axis axis, long seed) {
        return switch (axis) {
            case FREE -> edge3d(x / size, y / size, z / size, seed) * size;
            case X -> edge2d(y / size, z / size, seed) * size;
            case Y -> edge2d(x / size, z / size, seed) * size;
            case Z -> edge2d(x / size, y / size, seed) * size;
        };
    }

    private static double edge3d(double x, double y, double z, long seed) {
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        int cellZ = (int) Math.floor(z);
        double best = Double.POSITIVE_INFINITY;
        int nearestX = 0;
        int nearestY = 0;
        int nearestZ = 0;
        double siteX = 0;
        double siteY = 0;
        double siteZ = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = cellX + dx;
                    int cy = cellY + dy;
                    int cz = cellZ + dz;
                    double sx = cx + jitter(cx, cy, cz, seed, 0);
                    double sy = cy + jitter(cx, cy, cz, seed, 1);
                    double sz = cz + jitter(cx, cy, cz, seed, 2);
                    double distance = square(sx - x) + square(sy - y) + square(sz - z);
                    if (distance < best) {
                        best = distance;
                        nearestX = cx;
                        nearestY = cy;
                        nearestZ = cz;
                        siteX = sx;
                        siteY = sy;
                        siteZ = sz;
                    }
                }
            }
        }
        double edge = Double.POSITIVE_INFINITY;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int cx = nearestX + dx;
                    int cy = nearestY + dy;
                    int cz = nearestZ + dz;
                    double ox = cx + jitter(cx, cy, cz, seed, 0) - siteX;
                    double oy = cy + jitter(cx, cy, cz, seed, 1) - siteY;
                    double oz = cz + jitter(cx, cy, cz, seed, 2) - siteZ;
                    double length = Math.sqrt(ox * ox + oy * oy + oz * oz);
                    if (length < 1.0E-9) {
                        continue;
                    }
                    // Distancia del punto al plano bisector entre los dos sitios, hacia el lado del suyo.
                    double midX = siteX + ox * 0.5;
                    double midY = siteY + oy * 0.5;
                    double midZ = siteZ + oz * 0.5;
                    // Dividida por la norma L1 de la normal: con medio ancho 0,5 sale un plano digital
                    // estándar, sin huecos entre bloques aunque la grieta vaya en diagonal.
                    double distance = ((midX - x) * ox + (midY - y) * oy + (midZ - z) * oz)
                            / (Math.abs(ox) + Math.abs(oy) + Math.abs(oz));
                    edge = Math.min(edge, distance);
                }
            }
        }
        return Math.max(0.0, edge);
    }

    private static double edge2d(double u, double v, long seed) {
        int cellU = (int) Math.floor(u);
        int cellV = (int) Math.floor(v);
        double best = Double.POSITIVE_INFINITY;
        int nearestU = 0;
        int nearestV = 0;
        double siteU = 0;
        double siteV = 0;
        for (int dv = -1; dv <= 1; dv++) {
            for (int du = -1; du <= 1; du++) {
                int cu = cellU + du;
                int cv = cellV + dv;
                double su = cu + jitter(cu, 0, cv, seed, 0);
                double sv = cv + jitter(cu, 0, cv, seed, 2);
                double distance = square(su - u) + square(sv - v);
                if (distance < best) {
                    best = distance;
                    nearestU = cu;
                    nearestV = cv;
                    siteU = su;
                    siteV = sv;
                }
            }
        }
        double edge = Double.POSITIVE_INFINITY;
        for (int dv = -2; dv <= 2; dv++) {
            for (int du = -2; du <= 2; du++) {
                if (du == 0 && dv == 0) {
                    continue;
                }
                int cu = nearestU + du;
                int cv = nearestV + dv;
                double ou = cu + jitter(cu, 0, cv, seed, 0) - siteU;
                double ov = cv + jitter(cu, 0, cv, seed, 2) - siteV;
                double length = Math.sqrt(ou * ou + ov * ov);
                if (length < 1.0E-9) {
                    continue;
                }
                double distance = ((siteU + ou * 0.5 - u) * ou + (siteV + ov * 0.5 - v) * ov)
                        / (Math.abs(ou) + Math.abs(ov));
                edge = Math.min(edge, distance);
            }
        }
        return Math.max(0.0, edge);
    }

    /** Posición del sitio dentro de su celda, en [0,1; 0,9] para que no se amontonen en los bordes. */
    private static double jitter(int x, int y, int z, long seed, int axis) {
        return 0.1 + 0.8 * ModelFields.unit(x, y, z, seed + axis * 0x2545F4914F6CDD1DL);
    }

    private static double square(double value) {
        return value * value;
    }
}
