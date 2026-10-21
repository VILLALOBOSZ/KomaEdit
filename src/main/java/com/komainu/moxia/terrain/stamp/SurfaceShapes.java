package com.komainu.moxia.terrain.stamp;

import com.komainu.moxia.terrain.stamp.StampRecipe.Bake;

import java.util.ArrayList;
import java.util.List;

import static com.komainu.moxia.terrain.stamp.StampMath.angle;
import static com.komainu.moxia.terrain.stamp.StampMath.angleDelta;
import static com.komainu.moxia.terrain.stamp.StampMath.around;
import static com.komainu.moxia.terrain.stamp.StampMath.clamp01;
import static com.komainu.moxia.terrain.stamp.StampMath.cone;
import static com.komainu.moxia.terrain.stamp.StampMath.gauss;
import static com.komainu.moxia.terrain.stamp.StampMath.lerp;
import static com.komainu.moxia.terrain.stamp.StampMath.pow;
import static com.komainu.moxia.terrain.stamp.StampMath.smoothMax;
import static com.komainu.moxia.terrain.stamp.StampMath.smoothstep;
import static com.komainu.moxia.terrain.stamp.StampMath.terrace;
import static com.komainu.moxia.terrain.stamp.StampMath.warp;
import static com.komainu.moxia.terrain.stamp.StampNoise.fbm;
import static com.komainu.moxia.terrain.stamp.StampNoise.ridged;
import static com.komainu.moxia.terrain.stamp.StampNoise.unit;

/**
 * Sellos de formas sueltas: dunas (con la cara de sotavento a 33°, el ángulo de reposo de la arena, que pone la
 * erosión térmica), islas y atolones, y rocas y peñascos.
 */
final class SurfaceShapes {
    /** Ángulo de reposo de la arena seca. */
    private static final double SAND = 33.0;

    private SurfaceShapes() {
    }

    static List<StampRecipe> all() {
        List<StampRecipe> list = new ArrayList<>();
        dunes(list);
        islands(list);
        rocks(list);
        return list;
    }

    private static StampRecipe raise(String name, StampCategory category, String description, double edge,
                                     StampRecipe.Shape shape, Bake bake) {
        return new StampRecipe(name, category, description, 0.0, edge, StampRecipe.points(shape), bake);
    }

    // ── Dunas ────────────────────────────────────────────────────────────────────────────────────────

    private static void dunes(List<StampRecipe> list) {
        StampCategory c = StampCategory.DUNES;
        list.add(raise("duna", c, "Duna en media luna (barján) con la cara empinada hacia la mirada", 0.82,
                SurfaceShapes::barchan, Bake.slide(18, 40, SAND)));
        list.add(raise("dunas", c, "Campo de dunas transversales con crestas sinuosas", 0.7,
                SurfaceShapes::transverseDunes, Bake.slide(14, 30, SAND)));
        list.add(raise("duna_estrella", c, "Duna estrella: brazos de cresta afilada alrededor de un pico", 0.8,
                SurfaceShapes::starDune, Bake.slide(22, 30, SAND)));
        list.add(raise("duna_lineal", c, "Duna lineal (seif): una cresta larga y sinuosa", 0.8,
                SurfaceShapes::linearDune, Bake.slide(22, 30, SAND)));
        list.add(raise("duna_parabolica", c, "Duna parabólica: herradura con los brazos hacia ti", 0.82,
                SurfaceShapes::parabolicDune, Bake.slide(18, 25, SAND)));
        list.add(raise("barjanes", c, "Varios barjanes pequeños en fila", 0.8,
                SurfaceShapes::barchanField, Bake.slide(14, 30, SAND)));
    }

    /** Barján en coordenadas locales: brazos hacia {@code -y} y sotavento por delante de la cresta. */
    private static double barchanAt(double x, double y) {
        double brink = 0.12 - 1.05 * x * x;
        double dy = y - brink;
        double profile = dy >= 0.0 ? smoothstep(0.78, 0.0, dy) : smoothstep(-0.16, 0.0, dy);
        return StrictMath.exp(-StrictMath.pow(x / 0.48, 2.0)) * profile;
    }

    private static double barchan(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.04);
        return barchanAt(w[0], w[1] - 0.05);
    }

    private static double transverseDunes(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.2, 0.05);
        double phase = w[1] * 3.4 + 0.5 * fbm(w[0] * 1.3, w[1] * 0.6, s + 1, 3);
        double t = phase - Math.floor(phase);
        double wave = t < 0.8 ? t / 0.8 : (1.0 - t) / 0.2;
        double crest = smoothstep(0.0, 1.0, 1.0 - wave);
        return crest * (0.65 + 0.35 * (0.5 + 0.5 * fbm(w[0] * 1.8, w[1] * 0.9, s + 2, 2)));
    }

    private static double starDune(double x, double y, double r, long s) {
        double rr = Math.hypot(x, y);
        double a = angle(x, y);
        double first = unit(s, 1) * 2.0 * Math.PI;
        double arms = 0.0;
        for (int arm = 0; arm < 4; arm++) {
            double direction = first + arm * Math.PI / 2.0 + (unit(s, arm + 2) - 0.5) * 0.7;
            double curl = 0.45 * rr * (arm % 2 == 0 ? 1.0 : -1.0);
            double across = Math.abs(angleDelta(a + curl, direction)) * rr;
            double length = 0.7 + 0.2 * unit(s, arm + 7);
            arms = Math.max(arms, StrictMath.exp(-StrictMath.pow(across / 0.06, 2.0)) * cone(rr, length, 0.9));
        }
        return Math.max(cone(rr, 0.35, 1.2), 0.75 * arms);
    }

    private static double linearDune(double x, double y, double r, long s) {
        double center = 0.14 * StrictMath.sin(4.2 * y + 0.5) + 0.04 * fbm(y * 2.0, 0.0, s + 1, 2);
        double crest = pow(smoothstep(0.2, 0.0, Math.abs(x - center)), 1.3);
        double peaks = 0.7 + 0.3 * (0.5 + 0.5 * StrictMath.sin(y * 7.5 + 1.0));
        return crest * peaks * smoothstep(0.98, 0.6, Math.abs(y));
    }

    private static double parabolicDune(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.04);
        double curve = -0.45 + 2.4 * w[0] * w[0];
        double distance = Math.abs(w[1] - curve) / Math.sqrt(1.0 + StrictMath.pow(4.8 * w[0], 2.0));
        double width = w[1] < curve ? 0.06 : 0.12;
        double height = lerp(1.0, 0.35, clamp01((w[1] + 0.45) / 1.0));
        return StrictMath.exp(-StrictMath.pow(distance / width, 2.0)) * height * smoothstep(0.75, 0.55, w[1]);
    }

    private static double barchanField(double x, double y, double r, long s) {
        double[][] dunes = {{-0.4, 0.3, 0.42}, {0.1, 0.45, 0.32}, {0.38, -0.05, 0.38}, {-0.12, -0.25, 0.48},
                {-0.45, -0.38, 0.26}, {0.32, -0.5, 0.24}};
        double h = 0.0;
        for (int index = 0; index < dunes.length; index++) {
            double[] dune = dunes[index];
            double cx = dune[0] + (unit(s, index) - 0.5) * 0.08;
            double cy = dune[1] + (unit(s, index + 11) - 0.5) * 0.08;
            double scale = dune[2];
            h = Math.max(h, scale * barchanAt((x - cx) / scale, (y - cy) / scale));
        }
        return h;
    }

    // ── Islas y atolones ─────────────────────────────────────────────────────────────────────────────

    private static void islands(List<StampRecipe> list) {
        StampCategory c = StampCategory.ISLANDS;
        list.add(raise("isla", c, "Isla con playa baja, costa recortada y un monte en medio", 0.78,
                SurfaceShapes::island, Bake.water(40, 2.5, 1.2)));
        list.add(raise("archipielago", c, "Archipiélago: islotes de distintos tamaños", 0.82,
                SurfaceShapes::archipelago, Bake.water(16, 1.5, 0.8)).withRatio(0.3));
        list.add(raise("atolon", c, "Atolón: anillo bajo con islotes y pasos al mar", 0.82,
                SurfaceShapes::atoll, null).withRatio(0.2));
        list.add(raise("cayo", c, "Cayo: islote bajo de arena con una flecha curva", 0.8,
                SurfaceShapes::cay, Bake.slide(10, 10, 30)).withRatio(0.22));
        list.add(raise("isla_media_luna", c, "Isla en media luna: el borde de una caldera abierta al mar", 0.8,
                SurfaceShapes::crescentIsland, Bake.water(40, 2.0, 1.0)));
        list.add(raise("tombolo", c, "Tómbolo: dos islotes unidos por una barra de arena", 0.82,
                SurfaceShapes::tombolo, Bake.water(20, 1.5, 0.8)).withRatio(0.32));
    }

    private static double island(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.18);
        double rr = Math.hypot(w[0], w[1]);
        double shelf = 0.12 * smoothstep(0.82, 0.55, rr);
        double hill = cone(Math.hypot(w[0] - 0.1, w[1] + 0.05), 0.6, 1.2) * smoothstep(0.8, 0.4, rr);
        return shelf + 0.88 * hill * (0.75 + 0.25 * ridged(w[0] * 3.0, w[1] * 3.0, s + 1, 5));
    }

    private static double islet(double distance, double radius) {
        return 0.15 * smoothstep(radius, radius * 0.7, distance) + 0.85 * cone(distance, radius * 0.8, 1.7);
    }

    private static double archipelago(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.08);
        double[][] points = StampMath.scatter(s, 8, 0.62, 0.22);
        double h = 0.0;
        for (double[] point : points) {
            double radius = 0.1 + 0.15 * point[2];
            double height = 0.4 + 0.6 * point[2];
            h = Math.max(h, height * islet(Math.hypot(w[0] - point[0], w[1] - point[1]), radius));
        }
        return h * (0.8 + 0.2 * ridged(w[0] * 4.0, w[1] * 4.0, s + 1, 4));
    }

    private static double atoll(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.6, 0.04);
        double rr = Math.hypot(w[0], w[1]);
        double a = angle(w[0], w[1]);
        double ring = StrictMath.exp(-StrictMath.pow((rr - 0.58) / 0.07, 2.0));
        double passes = smoothstep(-0.55, -0.25, around(a, 2.0, s + 1, 3));
        double motus = smoothstep(0.1, 0.45, around(a, 3.0, s + 2, 2));
        return ring * passes * (0.45 + 0.55 * motus);
    }

    private static double cay(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.05);
        double body = pow(smoothstep(1.0, 0.0, Math.hypot(w[0] / 0.48, w[1] / 0.2)), 0.7);
        double spit = 0.0;
        for (int index = 0; index <= 12; index++) {
            double t = index / 12.0;
            double px = 0.3 + 0.28 * t;
            double py = 0.36 * t * t;
            spit = Math.max(spit, (0.45 - 0.2 * t) * smoothstep(0.065, 0.02, Math.hypot(w[0] - px, w[1] - py)));
        }
        double dune = 0.4 * gauss(w[0], w[1], -0.12, -0.02, 0.14, 0.08);
        return Math.max(0.6 * body + dune, spit);
    }

    private static double crescentIsland(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.6, 0.06);
        double rr = Math.hypot(w[0], w[1]);
        double delta = Math.abs(angleDelta(angle(w[0], w[1]), Math.PI / 2.0));
        double width = rr < 0.5 ? 0.07 : 0.2;
        double ring = StrictMath.exp(-StrictMath.pow((rr - 0.5) / width, 2.0));
        return ring * smoothstep(0.55, 0.9, delta) * (0.75 + 0.25 * ridged(w[0] * 4.0, w[1] * 4.0, s + 1, 4));
    }

    private static double tombolo(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.06);
        double big = islet(Math.hypot(w[0] + 0.3, w[1] + 0.25), 0.34);
        double small = 0.7 * islet(Math.hypot(w[0] - 0.36, w[1] - 0.3), 0.22);
        double bow = 0.06 * StrictMath.sin((w[0] + 0.3) / 0.66 * Math.PI);
        double bar = 0.16 * smoothstep(0.06, 0.025, StampMath.segment(w[0], w[1] - bow, -0.3, -0.25, 0.36, 0.3));
        return Math.max(Math.max(big, small), bar) * (0.85 + 0.15 * ridged(w[0] * 4.0, w[1] * 4.0, s + 1, 4));
    }

    // ── Rocas y peñascos ─────────────────────────────────────────────────────────────────────────────

    private static void rocks(List<StampRecipe> list) {
        StampCategory c = StampCategory.ROCKS;
        list.add(raise("aguja", c, "Aguja de roca alta y fina sobre un montículo", 0.85,
                SurfaceShapes::needle, Bake.slide(80, 2, 70)));
        list.add(raise("agujas", c, "Chimeneas de hada: varias columnas de alturas distintas", 0.85,
                SurfaceShapes::hoodoos, null).withRatio(0.8));
        list.add(raise("domo", c, "Domo de granito redondeado, con lajas como capas de cebolla", 0.85,
                SurfaceShapes::dome, null));
        list.add(raise("penasco", c, "Peñasco de roca partida en caras y bloques", 0.85,
                SurfaceShapes::crag, Bake.slide(50, 2, 65)));
        list.add(raise("tor", c, "Tor: bloques redondeados apilados", 0.85,
                SurfaceShapes::tor, null).withRatio(0.45));
        list.add(raise("bloques", c, "Bloques sueltos de varios tamaños", 0.85,
                SurfaceShapes::boulders, null).withRatio(0.25));
        list.add(raise("laja", c, "Laja inclinada en triángulo (flatiron)", 0.85,
                SurfaceShapes::flatiron, Bake.slide(50, 2, 60)));
    }

    private static double needle(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 4.0, 0.015);
        double rr = Math.hypot(w[0], w[1]);
        double needle = smoothstep(0.13, 0.09, rr) * (0.75 + 0.25 * smoothstep(0.1, 0.0, rr));
        double mound = 0.25 * StrictMath.exp(-rr * rr * 9.0);
        return Math.max(needle, mound) * (0.94 + 0.06 * ridged(w[0] * 8.0, w[1] * 8.0, s + 1, 3));
    }

    private static double hoodoos(double x, double y, double r, long s) {
        double rr = Math.hypot(x, y);
        double h = 0.18 * StrictMath.exp(-rr * rr * 4.0);
        double[][] points = StampMath.scatter(s, 9, 0.55, 0.16);
        for (int index = 0; index < points.length; index++) {
            double[] point = points[index];
            double radius = 0.045 + 0.04 * point[2];
            double height = 0.45 + 0.55 * unit(s, index + 40);
            double distance = Math.hypot(x - point[0], y - point[1]);
            double cap = 1.0 - 0.15 * pow(distance / radius, 2.0);
            h = Math.max(h, height * cap * smoothstep(radius + 0.012, radius - 0.008, distance));
        }
        return h;
    }

    private static double dome(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.04);
        double rr = Math.hypot(w[0], w[1]) / 0.86;
        double dome = rr >= 1.0 ? 0.0 : Math.sqrt(1.0 - rr * rr);
        return lerp(dome, terrace(dome, 7.0, 0.4), 0.18) * (0.98 + 0.02 * fbm(w[0] * 6.0, w[1] * 6.0, s + 1, 2));
    }

    private static double crag(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.08);
        double base = cone(Math.hypot(w[0], w[1]), 0.75, 0.9);
        double fx = w[0] * 4.0 + 20.0;
        double fy = w[1] * 4.0 + 20.0;
        int cx = (int) Math.floor(fx);
        int cy = (int) Math.floor(fy);
        double best = Double.POSITIVE_INFINITY;
        double facet = 0.0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = cx + dx;
                int gy = cy + dy;
                int index = gx * 7_919 + gy * 104_729;
                double px = gx + unit(s, index);
                double py = gy + unit(s + 1, index);
                double distance = Math.hypot(fx - px, fy - py);
                if (distance < best) {
                    best = distance;
                    double tiltX = (unit(s + 2, index) - 0.5) * 0.5;
                    double tiltY = (unit(s + 3, index) - 0.5) * 0.5;
                    facet = 0.75 + 0.25 * unit(s + 4, index) + tiltX * (fx - px) + tiltY * (fy - py);
                }
            }
        }
        return base * clamp01(facet);
    }

    private static double tor(double x, double y, double r, long s) {
        double[][] blocks = {{0.0, 0.05, 0.42, 0.3, 0.0, 0.42}, {0.06, -0.02, 0.3, 0.22, 0.42, 0.3},
                {-0.04, 0.02, 0.18, 0.15, 0.72, 0.28}};
        double rr = Math.hypot(x, y);
        double h = 0.12 * StrictMath.exp(-rr * rr * 5.0);
        for (int index = 0; index < blocks.length; index++) {
            double[] b = blocks[index];
            double angle = (unit(s, index) - 0.5) * 0.6;
            double cos = StrictMath.cos(angle);
            double sin = StrictMath.sin(angle);
            double dx = (x - b[0]) * cos - (y - b[1]) * sin;
            double dy = (x - b[0]) * sin + (y - b[1]) * cos;
            double d = StrictMath.pow(StrictMath.pow(Math.abs(dx) / b[2], 4.0)
                    + StrictMath.pow(Math.abs(dy) / b[3], 4.0), 0.25);
            if (d < 1.0) {
                h = Math.max(h, b[4] + b[5] * Math.sqrt(Math.max(0.0, 1.0 - StrictMath.pow(d, 8.0))));
            }
        }
        return h;
    }

    private static double boulders(double x, double y, double r, long s) {
        double[][] points = StampMath.scatter(s, 14, 0.62, 0.12);
        double h = 0.0;
        for (double[] point : points) {
            double radius = 0.05 + 0.06 * point[2];
            double distance = Math.hypot(x - point[0], (y - point[1]) * 1.15) / radius;
            if (distance < 1.0) {
                h = Math.max(h, radius * 4.0 * Math.sqrt(1.0 - distance * distance)
                        * (0.9 + 0.1 * fbm(x * 12.0, y * 12.0, s + 1, 2)));
            }
        }
        return h;
    }

    private static double flatiron(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.03);
        double halfWidth = 0.42 * (w[1] + 0.62) / 1.07;
        if (halfWidth <= 0.0 || w[1] > 0.45) {
            return 0.0;
        }
        double across = Math.abs(w[0]) / halfWidth;
        double slab = lerp(0.35, 1.0, smoothstep(0.45, -0.62, w[1]));
        return slab * smoothstep(1.0, 0.88, across) * smoothstep(0.45, 0.4, w[1])
                * (0.95 + 0.05 * fbm(w[0] * 6.0, w[1] * 6.0, s + 1, 2));
    }
}
