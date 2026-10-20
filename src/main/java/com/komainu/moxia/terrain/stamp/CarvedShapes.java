package com.komainu.moxia.terrain.stamp;

import com.komainu.moxia.terrain.HeightField;
import com.komainu.moxia.terrain.HydraulicErosion;
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
import static com.komainu.moxia.terrain.stamp.StampMath.smoothMin;
import static com.komainu.moxia.terrain.stamp.StampMath.smoothstep;
import static com.komainu.moxia.terrain.stamp.StampMath.terrace;
import static com.komainu.moxia.terrain.stamp.StampMath.warp;
import static com.komainu.moxia.terrain.stamp.StampNoise.billow;
import static com.komainu.moxia.terrain.stamp.StampNoise.fbm;
import static com.komainu.moxia.terrain.stamp.StampNoise.ridged;
import static com.komainu.moxia.terrain.stamp.StampNoise.unit;

/**
 * Sellos que hunden o tallan: cañones, cráteres y calderas, terrazas, formas glaciares y los de detalle. Los que
 * hunden tienen el gris neutro en 1 (el blanco es el terreno que ya hay) y los de detalle en 0,5 (suben y bajan).
 */
final class CarvedShapes {
    private CarvedShapes() {
    }

    static List<StampRecipe> all() {
        List<StampRecipe> list = new ArrayList<>();
        canyons(list);
        craters(list);
        terraces(list);
        glacial(list);
        detail(list);
        return list;
    }

    private static StampRecipe raise(String name, StampCategory category, String description, double edge,
                                     StampRecipe.Shape shape, Bake bake) {
        return new StampRecipe(name, category, description, 0.0, edge, StampRecipe.points(shape), bake);
    }

    private static StampRecipe sink(String name, StampCategory category, String description, double edge,
                                    StampRecipe.Shape shape, Bake bake) {
        return new StampRecipe(name, category, description, 1.0, edge, StampRecipe.points(shape), bake);
    }

    private static StampRecipe both(String name, StampCategory category, String description, double neutral,
                                    double edge, StampRecipe.Shape shape, Bake bake) {
        return new StampRecipe(name, category, description, neutral, edge, StampRecipe.points(shape), bake);
    }

    // ── Cañones y gargantas ──────────────────────────────────────────────────────────────────────────

    private static void canyons(List<StampRecipe> list) {
        StampCategory c = StampCategory.CANYONS;
        list.add(sink("canon", c, "Cañón serpenteante con paredes escalonadas (hunde)", 0.8,
                CarvedShapes::canyon, Bake.water(38, 1.5, 1.0)));
        list.add(sink("garganta", c, "Garganta estrecha y honda de paredes casi verticales (hunde)", 0.8,
                CarvedShapes::gorge, Bake.slide(50, 2, 70)));
        list.add(sink("valle_fluvial", c, "Valle en V con afluentes que bajan a él (hunde)", 0.78,
                CarvedShapes::riverValley, Bake.water(36, 2.0, 1.2)));
        list.add(sink("hoz", c, "Hoz: el río rodea un espolón en una curva cerrada (hunde)", 0.82,
                CarvedShapes::meanderGorge, Bake.water(40, 1.5, 1.0)));
        list.add(sink("canon_ciego", c, "Cañón ciego que nace en un circo y se abre hacia ti (hunde)", 0.8,
                CarvedShapes::boxCanyon, Bake.water(38, 2.0, 1.3)));
    }

    private static double canyon(double x, double y, double r, long s) {
        double phase = 2.1 * y + 0.6;
        double center = 0.22 * StrictMath.sin(phase) + 0.06 * fbm(y * 2.0, 0.0, s + 1, 3);
        double slope = 0.22 * 2.1 * StrictMath.cos(phase);
        double distance = Math.abs(x - center) / Math.sqrt(1.0 + slope * slope);
        distance += 0.015 * fbm(x * 8.0, y * 8.0, s + 2, 2);
        double wall = smoothstep(0.07, 0.34, distance);
        double stepped = lerp(wall, terrace(wall, 4.0, 0.35), 0.75);
        double along = smoothstep(0.97, 0.7, Math.abs(y));
        return 1.0 - along * (1.0 - stepped);
    }

    private static double gorge(double x, double y, double r, long s) {
        double center = 0.06 * fbm(y * 2.5, 0.0, s + 1, 3);
        double distance = Math.abs(x - center) + 0.008 * fbm(x * 10.0, y * 10.0, s + 2, 2);
        double wall = pow(smoothstep(0.025, 0.1, distance), 0.6);
        double along = smoothstep(0.95, 0.72, Math.abs(y));
        return 1.0 - along * (1.0 - wall);
    }

    private static double riverValley(double x, double y, double r, long s) {
        double center = 0.08 * fbm(y * 1.5, 0.0, s + 1, 3);
        double g = pow(smoothstep(0.0, 0.34, Math.abs(x - center)), 0.85);
        double[][] tributaries = {{-0.5, -1.0}, {-0.12, 1.0}, {0.25, -1.0}, {0.52, 1.0}};
        for (int index = 0; index < tributaries.length; index++) {
            double ty = tributaries[index][0];
            double side = tributaries[index][1];
            double ex = center + side * 0.5;
            double ey = ty - 0.42;
            double distance = StampMath.segment(x, y, center, ty, ex, ey)
                    + 0.02 * fbm(x * 5.0, y * 5.0, s + 3 + index, 2);
            double branch = 1.0 - 0.6 * (1.0 - smoothstep(0.0, 0.18, distance));
            g = smoothMin(g, branch, 0.08);
        }
        double depth = lerp(0.6, 1.0, smoothstep(-0.8, 0.8, y));
        double along = smoothstep(0.98, 0.72, Math.abs(y));
        return 1.0 - along * depth * (1.0 - g);
    }

    private static final StampMath.Path MEANDER = meander();

    private static StampMath.Path meander() {
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{-0.3, 1.1});
        for (int index = 0; index <= 40; index++) {
            double theta = lerp(-2.6, 2.6, index / 40.0);
            points.add(new double[]{0.42 * StrictMath.sin(theta), -0.42 * StrictMath.cos(theta) + 0.12});
        }
        points.add(new double[]{0.3, 1.1});
        double[] xs = new double[points.size()];
        double[] ys = new double[points.size()];
        for (int index = 0; index < points.size(); index++) {
            xs[index] = points.get(index)[0];
            ys[index] = points.get(index)[1];
        }
        return new StampMath.Path(xs, ys);
    }

    private static double meanderGorge(double x, double y, double r, long s) {
        double distance = MEANDER.distance(x, y, null) + 0.012 * fbm(x * 6.0, y * 6.0, s + 1, 2);
        double wall = pow(smoothstep(0.04, 0.17, distance), 0.7);
        return lerp(wall, terrace(wall, 3.0, 0.3), 0.5);
    }

    private static double boxCanyon(double x, double y, double r, long s) {
        double dy = y + 0.45;
        double center = 0.05 * fbm(y * 2.0, 0.0, s + 1, 2);
        double distance = dy < 0.0 ? Math.hypot(x - center, dy) : Math.abs(x - center);
        distance += 0.015 * fbm(x * 7.0, y * 7.0, s + 2, 2);
        double width = 0.12 + 0.16 * clamp01(dy / 1.2);
        double wall = smoothstep(width * 0.4, width + 0.12, distance);
        double stepped = lerp(wall, terrace(wall, 3.0, 0.3), 0.6);
        double depth = lerp(0.75, 1.0, clamp01(dy));
        return 1.0 - depth * (1.0 - stepped);
    }

    // ── Cráteres y calderas ──────────────────────────────────────────────────────────────────────────

    private static final double CRATER_GROUND = 0.35;

    private static void craters(List<StampRecipe> list) {
        StampCategory c = StampCategory.CRATERS;
        list.add(both("crater", c, "Cráter de impacto: borde alto, fondo hundido y rayos de eyecta",
                CRATER_GROUND, 0.82, CarvedShapes::crater, Bake.slide(30, 3, 40)));
        list.add(both("crater_central", c, "Cráter grande de fondo llano con un pico en medio", CRATER_GROUND,
                0.85, CarvedShapes::complexCrater, Bake.slide(30, 3, 40)));
        list.add(sink("caldera", c, "Caldera hundida en escalones con un domo en el fondo (hunde)", 0.82,
                CarvedShapes::caldera, Bake.water(40, 1.0, 0.6)));
        list.add(sink("dolina", c, "Dolina: embudo redondo que hunde el terreno", 0.78,
                CarvedShapes::sinkhole, Bake.water(36, 1.5, 0.8)));
        list.add(new StampRecipe("crateres", c, "Campo de cráteres pequeños que se pisan unos a otros",
                CRATER_GROUND, 0.8, StampRecipe.points(CarvedShapes::craterField), Bake.slide(20, 2, 40)));
        list.add(sink("cuenca", c, "Cuenca poco honda de orillas lobuladas: el sitio de un lago (hunde)", 0.72,
                CarvedShapes::basin, null));
    }

    /** Altura de un cráter respecto al suelo: borde a {@code rim}, cuenco dentro y eyecta fuera. */
    private static double craterRelief(double distance, double rim, double depth, double rimHeight, double rays) {
        if (distance < rim) {
            double t = distance / rim;
            return -depth * (1.0 - pow(t, 2.2)) + rimHeight * smoothstep(0.6, 1.0, t);
        }
        return rimHeight * StrictMath.exp(-pow((distance - rim) / (rim * 0.35), 1.2)) * (0.8 + 0.2 * rays);
    }

    private static double crater(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.03);
        double rr = Math.hypot(w[0], w[1]);
        double a = angle(w[0], w[1]);
        double rim = 0.52 + 0.025 * around(a, 3.0, s + 1, 2);
        double rays = pow(0.5 + 0.5 * around(a, 9.0, s + 3, 2), 3.0);
        return CRATER_GROUND + craterRelief(rr, rim, 0.33, 0.45, rays);
    }

    private static double complexCrater(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.03);
        double rr = Math.hypot(w[0], w[1]);
        double a = angle(w[0], w[1]);
        double rim = 0.62 + 0.03 * around(a, 3.0, s + 1, 2);
        double relief;
        if (rr < rim) {
            double wall = smoothstep(0.4, rim, rr);
            relief = lerp(-0.3, 0.42, terrace(pow(wall, 1.6), 3.0, 0.4));
        } else {
            relief = 0.42 * StrictMath.exp(-pow((rr - rim) / 0.2, 1.2));
        }
        double peak = 0.0;
        for (int index = 0; index < 3; index++) {
            double px = (unit(s, index) - 0.5) * 0.14;
            double py = (unit(s, index + 5) - 0.5) * 0.14;
            peak = Math.max(peak, (0.55 - index * 0.1) * cone(Math.hypot(w[0] - px, w[1] - py), 0.17, 1.2));
        }
        return CRATER_GROUND + Math.max(relief, -0.3 + peak) * (0.97 + 0.03 * fbm(w[0] * 6.0, w[1] * 6.0, s + 4, 2));
    }

    private static double caldera(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.4, 0.1);
        double rr = Math.hypot(w[0], w[1]);
        double a = angle(w[0], w[1]);
        // Una pared principal alta y, dentro, un escalón a medias (un bloque hundido que no dio la vuelta entera).
        double wall = 0.62 + 0.05 * around(a, 2.0, s + 1, 3);
        double bench = 0.44 + 0.04 * around(a, 2.5, s + 2, 2);
        double benchArc = smoothstep(-0.2, 0.3, around(a, 1.2, s + 3, 2));
        double level = 0.7 * smoothstep(wall - 0.015, wall + 0.015, rr)
                + 0.3 * smoothstep(bench - 0.02, bench + 0.02, rr) * benchArc
                + 0.3 * smoothstep(wall - 0.015, wall + 0.015, rr) * (1.0 - benchArc);
        double dome = 0.22 * cone(Math.hypot(w[0] - 0.1, w[1] - 0.06), 0.22, 0.9);
        return Math.min(1.0, level + dome * (1.0 - level));
    }

    private static double sinkhole(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.05);
        double rr = Math.hypot(w[0], w[1]);
        double funnel = pow(smoothstep(0.62, 0.06, rr), 1.4);
        return 1.0 - funnel * (0.95 + 0.05 * fbm(w[0] * 5.0, w[1] * 5.0, s + 1, 2));
    }

    private static double craterField(double x, double y, double r, long s) {
        double relief = 0.0;
        for (double[] crater : CRATER_FIELD) {
            double radius = crater[2];
            double distance = Math.hypot(x - crater[0], y - crater[1]);
            if (distance < radius * 3.0) {
                // Cada cráter se abre sobre lo que ya había: los pequeños pisan el borde de los grandes.
                relief += craterRelief(distance, radius, radius * 0.7, radius * 0.45, 1.0);
            }
        }
        return CRATER_GROUND + relief;
    }

    /** Cráteres del campo, de mayor a menor (x, y, radio), con una semilla fija. */
    private static final double[][] CRATER_FIELD = craterField(0xC4A7E5L);

    private static double[][] craterField(long seed) {
        double[][] points = StampMath.scatter(seed, 11, 0.6, 0.14);
        double[][] craters = new double[points.length][];
        for (int index = 0; index < points.length; index++) {
            craters[index] = new double[]{points[index][0], points[index][1], 0.08 + 0.17 * points[index][2]
                    * points[index][2]};
        }
        java.util.Arrays.sort(craters, (a, b) -> Double.compare(b[2], a[2]));
        return craters;
    }

    private static double basin(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.3, 0.22);
        double rr = Math.hypot(w[0], w[1]);
        double depth = pow(smoothstep(0.72, 0.15, rr), 0.9);
        return 1.0 - depth * (0.9 + 0.1 * fbm(w[0] * 3.0, w[1] * 3.0, s + 1, 3));
    }

    // ── Terrazas y bancales ──────────────────────────────────────────────────────────────────────────

    private static void terraces(List<StampRecipe> list) {
        StampCategory c = StampCategory.TERRACES;
        list.add(raise("terrazas", c, "Colina escalonada en terrazas anchas", 0.82,
                CarvedShapes::terracedHill, null));
        list.add(raise("bancales", c, "Loma alargada con muchos bancales estrechos", 0.82,
                CarvedShapes::terracedRidge, null));
        list.add(sink("anfiteatro", c, "Anfiteatro: hoyo en anillos escalonados (hunde)", 0.82,
                CarvedShapes::amphitheater, null));
        list.add(sink("terrazas_fluviales", c, "Valle con terrazas fluviales a los lados del cauce (hunde)", 0.8,
                CarvedShapes::riverTerraces, Bake.water(30, 1.0, 0.5)));
    }

    private static double terracedHill(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.6, 0.1);
        double rr = Math.hypot(w[0], w[1]);
        return terrace(StrictMath.exp(-rr * rr * 3.0), 6.0, 0.22);
    }

    private static double terracedRidge(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.3, 0.12);
        double bend = 0.12 * fbm(w[0] * 1.2, 0.0, s + 1, 2);
        double ridge = StrictMath.exp(-StrictMath.pow((w[1] - bend) / 0.42, 2.0))
                * smoothstep(1.05, 0.35, Math.abs(w[0]))
                * (0.85 + 0.15 * fbm(w[0] * 1.5, w[1] * 1.5, s + 2, 2));
        return terrace(ridge, 10.0, 0.3);
    }

    private static double amphitheater(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.04);
        double rr = Math.hypot(w[0], w[1]);
        return terrace(smoothstep(0.12, 0.7, rr), 7.0, 0.25);
    }

    private static double riverTerraces(double x, double y, double r, long s) {
        double center = 0.1 * fbm(y * 1.2, 0.0, s + 1, 3);
        double offset = x - center;
        double distance = Math.abs(offset) + 0.01 * fbm(x * 6.0, y * 6.0, s + 2, 2);
        double wide = offset < 0.0 ? 1.0 : 0.7;
        double g = 0.4 * smoothstep(0.08, 0.12, distance) + 0.3 * smoothstep(0.08 + 0.2 * wide, 0.12 + 0.2 * wide,
                distance) + 0.3 * smoothstep(0.12 + 0.36 * wide, 0.17 + 0.36 * wide, distance);
        double along = smoothstep(0.97, 0.7, Math.abs(y));
        return 1.0 - along * (1.0 - g);
    }

    // ── Glaciares y morrenas ─────────────────────────────────────────────────────────────────────────

    private static void glacial(List<StampRecipe> list) {
        StampCategory c = StampCategory.GLACIAL;
        list.add(raise("circo", c, "Circo glaciar: herradura de paredes altas abierta hacia ti", 0.8,
                CarvedShapes::cirque, Bake.full(60, 2.0, 1.0, 5, 48)));
        list.add(sink("valle", c, "Valle glaciar en U: fondo ancho y paredes que se empinan (hunde)", 0.8,
                CarvedShapes::glacialValley, Bake.slide(40, 3, 40)));
        list.add(raise("cuerno", c, "Cuerno: pico piramidal tallado por tres circos", 0.8,
                CarvedShapes::horn, Bake.full(70, 1.5, 0.8, 4, 50)));
        list.add(raise("morrena", c, "Morrena frontal: arco de derrubios que dejó el hielo", 0.82,
                CarvedShapes::moraine, Bake.slide(25, 4, 35)));
        list.add(raise("drumlins", c, "Campo de drumlins: lomas alargadas en la dirección del hielo", 0.82,
                CarvedShapes::drumlinField, null).withRatio(0.18));
        list.add(raise("glaciar", c, "Lengua de glaciar con grietas y morrenas en los bordes", 0.82,
                CarvedShapes::glacierTongue, null).withRatio(0.2));
    }

    private static double cirque(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.06);
        double rb = Math.hypot(w[0], w[1] - 0.1);
        double ring = StrictMath.exp(-StrictMath.pow((rb - 0.55) / 0.22, 2.0));
        double back = lerp(0.4, 1.0, smoothstep(0.5, -0.6, w[1]));
        double open = smoothstep(0.0, 0.55, w[1] - 0.1);
        return ring * back * (1.0 - open) * (0.75 + 0.25 * ridged(w[0] * 4.0, w[1] * 4.0, s + 1, 4));
    }

    private static double glacialValley(double x, double y, double r, long s) {
        double center = 0.06 * fbm(y * 1.2, 0.0, s + 1, 3);
        double distance = Math.abs(x - center);
        // Las paredes se abren un poco hacia ti, donde el hielo bajaba con más fuerza.
        double width = lerp(0.3, 0.42, smoothstep(-0.8, 0.8, y));
        double g = uShape(distance / width);
        double along = smoothstep(0.98, 0.45, Math.abs(y));
        return 1.0 - along * (1.0 - g);
    }

    /** Perfil en U: fondo ancho, paredes que se empinan y hombro redondeado arriba ({@code t} de 0 a 1). */
    private static double uShape(double t) {
        double clamped = Math.min(1.0, t);
        return 1.0 - pow(1.0 - clamped * clamped, 1.5);
    }

    private static double horn(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.8, 0.05);
        double rr = Math.hypot(w[0], w[1]);
        double peak = cone(rr, 0.85, 1.25);
        double first = unit(s, 1) * 2.0 * Math.PI;
        double bowls = 0.0;
        for (int index = 0; index < 3; index++) {
            double direction = first + index * 2.0 * Math.PI / 3.0;
            double cx = 0.42 * StrictMath.cos(direction);
            double cy = 0.42 * StrictMath.sin(direction);
            bowls = Math.max(bowls, StrictMath.exp(-StrictMath.pow(Math.hypot(w[0] - cx, w[1] - cy) / 0.36, 2.0)));
        }
        // Los circos se comen las caras y dejan tres aristas afiladas entre ellos.
        return pow(peak * (1.0 - 0.95 * bowls), 1.1);
    }

    private static double moraine(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.6, 0.05);
        double rb = Math.hypot(w[0], w[1] + 0.55);
        double delta = angleDelta(angle(w[0], w[1] + 0.55), Math.PI / 2.0);
        double extent = smoothstep(1.35, 0.95, Math.abs(delta));
        double arc = StrictMath.exp(-StrictMath.pow((rb - 0.72) / 0.13, 2.0)) * extent;
        double lateral = 0.6 * StrictMath.exp(-StrictMath.pow((rb - 0.68) / 0.08, 2.0))
                * smoothstep(0.9, 1.3, Math.abs(delta))
                * smoothstep(1.9, 1.5, Math.abs(delta));
        return Math.max(arc, lateral) * (0.82 + 0.18 * billow(w[0] * 5.0, w[1] * 5.0, s + 1, 3));
    }

    private static double drumlinField(double x, double y, double r, long s) {
        double[][] points = StampMath.scatter(s, 8, 0.6, 0.3);
        double h = 0.0;
        for (double[] point : points) {
            double dy = y - point[1];
            double sy = dy < 0.0 ? 0.13 : 0.24;
            double height = 0.6 + 0.4 * point[2];
            h = smoothMax(h, height * gauss(x, y, point[0], point[1], 0.09, sy), 0.05);
        }
        return h;
    }

    private static double glacierTongue(double x, double y, double r, long s) {
        double t = clamp01((y + 1.0) / 1.55);
        double center = 0.08 * fbm(y * 1.2, 0.0, s + 1, 2);
        double halfWidth = 0.34 - 0.08 * t;
        double across = Math.abs(x - center) / halfWidth;
        double snout = 0.55 + 0.05 * fbm(x * 3.0, 0.0, s + 2, 2);
        double body = across >= 1.0 ? 0.0 : Math.sqrt(1.0 - across * across) * smoothstep(snout + 0.04, snout - 0.12, y)
                * lerp(1.0, 0.3, t);
        double crevasses = 0.05 * pow(0.5 + 0.5 * StrictMath.sin(y * 24.0 + across * across * 4.0), 4.0);
        double medial = 0.05 * StrictMath.exp(-StrictMath.pow(across / 0.06, 2.0));
        double lateral = 0.22 * StrictMath.exp(-StrictMath.pow((across - 1.0) / 0.12, 2.0))
                * smoothstep(snout + 0.1, snout - 0.1, y);
        return body * (0.9 + crevasses) + (medial * body) + lateral;
    }

    // ── Detalle ──────────────────────────────────────────────────────────────────────────────────────

    private static void detail(List<StampRecipe> list) {
        StampCategory c = StampCategory.DETAIL;
        list.add(both("arrugas", c, "Arrugas: pliegues finos que rompen lo liso (sube y baja)", 0.5, 0.6,
                (x, y, r, s) -> 0.5 + 0.5 * (2.0 * ridged(x * 4.5, y * 4.5, s, 4) - 1.0), null));
        list.add(sink("grietas", c, "Grietas en red, como barro seco o roca agrietada (hunde)", 0.7,
                CarvedShapes::cracks, null));
        list.add(new StampRecipe("carcavas", c, "Cárcavas: surcos ramificados que talla el agua (hunde)", 1.0, 0.72,
                CarvedShapes::rills, null));
        list.add(sink("arroyo", c, "Arroyo que serpentea con su lecho (hunde)", 0.8,
                CarvedShapes::stream, null));
        list.add(raise("barrancos", c, "Ladera cruzada por barrancos que bajan hacia ti", 0.8,
                CarvedShapes::gulliedSlope, Bake.water(40, 4.0, 2.5)));
        list.add(both("rizos", c, "Rizos de arena o de barro (sube y baja)", 0.5, 0.6,
                CarvedShapes::ripples, null));
        list.add(raise("bultos", c, "Bultos: montículos sueltos y bajos", 0.75,
                CarvedShapes::hummocks, null));
        list.add(both("escalones", c, "Escalones de roca en las laderas (sube y baja)", 0.5, 0.6,
                CarvedShapes::ledges, null));
    }

    /** Distancias al punto más cercano y al segundo de una rejilla con puntos movidos al azar. */
    private static double[] worley(double x, double y, long s) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        double first = Double.POSITIVE_INFINITY;
        double second = Double.POSITIVE_INFINITY;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = cx + dx;
                int gy = cy + dy;
                int index = gx * 7_919 + gy * 104_729;
                double px = gx + 0.15 + 0.7 * unit(s, index);
                double py = gy + 0.15 + 0.7 * unit(s + 1, index);
                double distance = Math.hypot(x - px, y - py);
                if (distance < first) {
                    second = first;
                    first = distance;
                } else if (distance < second) {
                    second = distance;
                }
            }
        }
        return new double[]{first, second};
    }

    private static double cracks(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.05);
        double[] cells = worley(w[0] * 3.2 + 10.0, w[1] * 3.2 + 10.0, s + 1);
        double crack = smoothstep(0.06, 0.0, cells[1] - cells[0]);
        double[] fine = worley(w[0] * 7.0 + 30.0, w[1] * 7.0 + 30.0, s + 2);
        double small = 0.45 * smoothstep(0.05, 0.0, fine[1] - fine[0]);
        return 1.0 - Math.max(crack, small) * (0.75 + 0.25 * fbm(w[0] * 4.0, w[1] * 4.0, s + 3, 2));
    }

    /** Un cono suave erosionado con fuerza; se queda solo lo que la erosión ha quitado. */
    private static double[] rills(int size, long seed) {
        double[] base = new double[size * size];
        double scale = 30.0 * size / StampRecipe.REFERENCE_SIZE;
        HeightField field = new HeightField(0, 0, size, size);
        for (int row = 0; row < size; row++) {
            double y = StampRecipe.coordinate(row, size);
            for (int column = 0; column < size; column++) {
                double x = StampRecipe.coordinate(column, size);
                double value = cone(Math.hypot(x, y), 1.2, 1.0) + 0.04 * fbm(x * 6.0, y * 6.0, seed, 3);
                base[row * size + column] = value * scale;
                field.define(column, row, value * scale, true, 0.0, Double.POSITIVE_INFINITY);
            }
        }
        HydraulicErosion.run(field, HydraulicErosion.Settings.of(size * size, 5.0, 2.5, seed), () -> {
        });
        double[] result = new double[size * size];
        for (int index = 0; index < result.length; index++) {
            result[index] = 1.0 + Math.min(0.0, field.at(index) - base[index]) / scale;
        }
        return result;
    }

    private static double stream(double x, double y, double r, long s) {
        double center = 0.25 * StrictMath.sin(3.4 * y + 0.4) + 0.05 * fbm(y * 2.5, 0.0, s + 1, 2);
        double slope = 0.25 * 3.4 * StrictMath.cos(3.4 * y + 0.4);
        double distance = Math.abs(x - center) / Math.sqrt(1.0 + slope * slope);
        double channel = 1.0 - pow(smoothstep(0.1, 0.0, distance), 0.8);
        double floodplain = 0.75 + 0.25 * smoothstep(0.1, 0.24, distance);
        double along = smoothstep(0.98, 0.7, Math.abs(y));
        return 1.0 - along * (1.0 - Math.min(channel, floodplain));
    }

    private static double gulliedSlope(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.3, 0.08);
        // Ladera que sube hacia el fondo, con surcos paralelos que la erosión ahonda y ramifica.
        double slope = smoothstep(0.85, -0.55, w[1]) * smoothstep(1.0, 0.45, Math.abs(w[0]));
        double grooves = 0.06 * StrictMath.sin(w[0] * 26.0 + 2.0 * fbm(w[0] * 2.0, w[1] * 2.0, s + 1, 2));
        return Math.max(0.0, slope + grooves * slope);
    }

    private static double ripples(double x, double y, double r, long s) {
        double phase = y * 14.0 + 0.8 * fbm(x * 1.5, y * 1.5, s + 1, 3);
        double t = phase - Math.floor(phase);
        double wave = t < 0.75 ? t / 0.75 : (1.0 - t) / 0.25;
        return 0.5 + 0.5 * (2.0 * smoothstep(0.0, 1.0, wave) - 1.0) * (0.7 + 0.3 * fbm(x * 2.0, y * 2.0, s + 2, 2));
    }

    private static double hummocks(double x, double y, double r, long s) {
        double[][] points = StampMath.scatter(s, 26, 0.75, 0.11);
        double h = 0.0;
        for (double[] point : points) {
            double radius = 0.07 + 0.07 * point[2];
            double distance = Math.hypot(x - point[0], y - point[1]) / radius;
            h = Math.max(h, (0.5 + 0.5 * point[2]) * StrictMath.exp(-distance * distance * 1.6));
        }
        return h;
    }

    private static double ledges(double x, double y, double r, long s) {
        double t = y * 4.0 + 0.5 * fbm(x * 1.4, y * 1.4, s + 1, 3);
        double stepped = terrace(t - Math.floor(t), 1.0, 0.2) - (t - Math.floor(t));
        return 0.5 + stepped;
    }
}
