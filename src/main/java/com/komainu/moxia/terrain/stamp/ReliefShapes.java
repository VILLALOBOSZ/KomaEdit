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
import static com.komainu.moxia.terrain.stamp.StampMath.warp;
import static com.komainu.moxia.terrain.stamp.StampNoise.fbm;
import static com.komainu.moxia.terrain.stamp.StampNoise.ridged;
import static com.komainu.moxia.terrain.stamp.StampNoise.unit;

/**
 * Sellos que levantan: montañas, cordilleras, colinas, mesetas, acantilados y volcanes. La mayoría salen de una
 * forma de base con crestas de ruido y se terminan con la erosión horneada.
 */
final class ReliefShapes {
    private ReliefShapes() {
    }

    static List<StampRecipe> all() {
        List<StampRecipe> list = new ArrayList<>();
        mountains(list);
        ranges(list);
        hills(list);
        plateaus(list);
        cliffs(list);
        volcanoes(list);
        return list;
    }

    private static StampRecipe raise(String name, StampCategory category, String description, double edge,
                                     StampRecipe.Shape shape, Bake bake) {
        return new StampRecipe(name, category, description, 0.0, edge, StampRecipe.points(shape), bake);
    }

    // ── Montañas y picos ─────────────────────────────────────────────────────────────────────────────

    private static void mountains(List<StampRecipe> list) {
        StampCategory c = StampCategory.MOUNTAINS;
        list.add(raise("montana", c, "Montaña con crestas y laderas surcadas por la erosión", 0.78,
                ReliefShapes::mountain, Bake.full(60, 3.0, 1.2, 6, 44)));
        list.add(raise("pico", c, "Pico afilado y solitario, de cumbre esbelta y faldas largas", 0.8,
                ReliefShapes::peak, Bake.full(75, 2.0, 0.8, 4, 52)));
        list.add(raise("macizo", c, "Macizo de varias cumbres unidas por collados", 0.8,
                ReliefShapes::massif, Bake.full(60, 3.0, 1.2, 6, 44)));
        list.add(raise("cumbre_doble", c, "Dos cumbres con un collado en medio", 0.8,
                ReliefShapes::twinPeaks, Bake.full(58, 3.0, 1.1, 6, 44)));
        list.add(raise("montana_vieja", c, "Sierra vieja: lomos redondeados y gastados con valles suaves", 0.7,
                ReliefShapes::oldMountain, Bake.full(26, 4.0, 1.5, 12, 24)));
        list.add(raise("espolon", c, "Espolón: la punta de una sierra que baja hacia ti", 0.78,
                ReliefShapes::spur, Bake.full(60, 3.0, 1.2, 5, 44)));
    }

    private static double mountain(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.3, 0.16);
        double rr = Math.hypot(w[0], w[1]);
        double base = cone(rr, 0.92, 1.25);
        double ridges = ridged(w[0] * 2.4 + 3.1, w[1] * 2.4 - 1.7, s + 1, 6);
        return base * (0.35 + 0.65 * ridges) + 0.3 * pow(base, 2.2);
    }

    private static double peak(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.07);
        double rr = Math.hypot(w[0], w[1]);
        // Cumbre esbelta (muy cóncava) sobre faldas anchas; las crestas finas las pone el ruido y la erosión.
        double body = cone(rr, 0.62, 2.2);
        double skirt = 0.35 * cone(rr, 0.95, 1.6);
        double ridges = ridged(w[0] * 3.2 + 1.3, w[1] * 3.2 - 0.4, s + 3, 5);
        return Math.max(body, skirt) * (0.7 + 0.3 * ridges) + 0.1 * skirt * ridges;
    }

    private static double massif(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.4, 0.1);
        double[][] peaks = {{-0.08, -0.1, 1.0, 0.5}, {0.4, 0.14, 0.78, 0.36}, {-0.4, 0.3, 0.72, 0.36},
                {0.14, -0.5, 0.66, 0.32}, {-0.46, -0.34, 0.58, 0.3}};
        double h = 0.0;
        for (int index = 0; index < peaks.length; index++) {
            double[] p = peaks[index];
            double px = p[0] + (unit(s, index * 2) - 0.5) * 0.1;
            double py = p[1] + (unit(s, index * 2 + 1) - 0.5) * 0.1;
            h = smoothMax(h, p[2] * cone(Math.hypot(w[0] - px, w[1] - py), p[3], 1.3), 0.12);
        }
        // Una base común: las cumbres salen de un mismo bloque y no son conos sueltos.
        h = smoothMax(h, 0.42 * cone(Math.hypot(w[0] * 0.95, w[1]), 0.9, 1.1), 0.15);
        double ridges = ridged(w[0] * 3.0, w[1] * 3.0, s + 5, 6);
        return h * (0.55 + 0.45 * ridges) + 0.2 * h * h;
    }

    private static double twinPeaks(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.1);
        double a = cone(Math.hypot(w[0] + 0.34, w[1] * 1.1 + 0.02), 0.62, 1.35);
        double b = 0.86 * cone(Math.hypot(w[0] - 0.36, w[1] * 1.1 - 0.04), 0.58, 1.35);
        double saddle = 0.5 * StrictMath.exp(-StrictMath.pow(w[1] / 0.2, 2.0)) * smoothstep(0.8, 0.2, Math.abs(w[0]));
        double h = smoothMax(smoothMax(a, b, 0.1), saddle, 0.1);
        return h * (0.6 + 0.4 * ridged(w[0] * 2.6, w[1] * 2.6, s + 7, 6));
    }

    private static double oldMountain(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.0, 0.18);
        // Un lomo ancho y alargado en diagonal con tres cumbres romas: la erosión larga lo deja sin aristas.
        double along = (w[0] + w[1]) * 0.7071;
        double across = (w[0] - w[1]) * 0.7071;
        double body = StrictMath.exp(-StrictMath.pow(across / 0.34, 2.0)) * smoothstep(0.95, 0.35, Math.abs(along));
        double summits = 0.0;
        double[] places = {-0.42, 0.05, 0.45};
        for (int index = 0; index < places.length; index++) {
            double height = 0.75 + 0.25 * unit(s, index);
            summits = Math.max(summits, height * gauss(along, across, places[index], 0.04 * (index - 1), 0.3, 0.28));
        }
        double base = smoothMax(0.6 * body, summits, 0.2);
        return base * (0.85 + 0.15 * fbm(w[0] * 2.2, w[1] * 2.2, s + 9, 4));
    }

    private static double spur(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.4, 0.08);
        double t = clamp01((w[1] + 0.85) / 1.6);
        double crestX = 0.1 * fbm(w[1] * 1.5, 0.3, s + 11, 3);
        double width = lerp(0.2, 0.36, t);
        double across = (w[0] - crestX) / width;
        double crest = lerp(1.0, 0.22, pow(t, 0.8));
        double ridge = crest * StrictMath.exp(-across * across) * smoothstep(1.0, 0.72, t);
        double head = 0.95 * gauss(w[0], w[1], 0.0, -0.58, 0.3, 0.26);
        double spurs = ridged(w[0] * 3.5, w[1] * 3.0, s + 12, 5) * StrictMath.exp(-across * across / 3.0) * 0.35
                * crest;
        return smoothMax(ridge, head, 0.1) * (0.75 + 0.25 * ridged(w[0] * 4.0, w[1] * 4.0, s + 13, 4)) + spurs;
    }

    // ── Cordilleras ──────────────────────────────────────────────────────────────────────────────────

    private static void ranges(List<StampRecipe> list) {
        StampCategory c = StampCategory.RANGES;
        list.add(raise("cordillera", c, "Una cresta larga con cumbres y estribaciones a los lados", 0.8,
                ReliefShapes::range, Bake.full(62, 3.0, 1.3, 6, 44)));
        list.add(raise("sierra", c, "Sierra dentada: una fila de picos afilados", 0.82,
                ReliefShapes::sierra, Bake.full(70, 2.0, 0.8, 4, 55)));
        list.add(raise("cordillera_curva", c, "Cordillera en arco que se curva hacia delante", 0.8,
                ReliefShapes::curvedRange, Bake.full(62, 3.0, 1.3, 6, 44)));
        list.add(raise("cordillera_ramificada", c, "Tres cordales que se juntan en una cumbre", 0.8,
                ReliefShapes::branchingRange, Bake.full(62, 3.0, 1.3, 6, 44)));
        list.add(raise("cordones", c, "Sierras paralelas con valles entre medias (relieve plegado)", 0.8,
                ReliefShapes::parallelRidges, Bake.full(45, 2.5, 1.0, 5, 40)));
    }

    private static double range(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.2, 0.1);
        double bend = 0.14 * fbm(w[1] * 1.1, 3.0, s + 1, 3);
        double across = (w[0] - bend) / 0.24;
        double along = smoothstep(1.0, 0.62, Math.abs(w[1]));
        double crest = StrictMath.exp(-across * across);
        double summits = 0.55 + 0.45 * (0.5 + 0.5 * fbm(1.7, w[1] * 3.2, s + 2, 3));
        double spurs = ridged(w[0] * 3.2, w[1] * 3.2, s + 3, 5) * StrictMath.exp(-across * across / 5.0);
        return (crest * summits * (0.7 + 0.3 * ridged(w[0] * 4.0, w[1] * 4.0, s + 4, 4))
                + 0.45 * spurs * pow(crest, 0.3)) * along;
    }

    private static double sierra(double x, double y, double r, long s) {
        double across = (x - 0.05 * fbm(y * 1.5, 0.0, s + 1, 2)) / 0.16;
        double along = smoothstep(0.98, 0.7, Math.abs(y));
        double phase = y * 4.6 + 1.1 * fbm(y * 1.6, 1.0, s + 2, 3);
        double tooth = 1.0 - Math.abs(2.0 * (phase - Math.floor(phase)) - 1.0);
        double teeth = pow(tooth, 1.6) * (0.55 + 0.45 * (0.5 + 0.5 * fbm(y * 2.1, 2.0, s + 3, 2)));
        double crest = StrictMath.exp(-across * across);
        return crest * (0.45 + 0.55 * teeth) * along * (0.8 + 0.2 * ridged(x * 6.0, y * 6.0, s + 4, 4));
    }

    private static double curvedRange(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.2, 0.08);
        double cy = 1.05;
        double radius = 1.1;
        double across = (Math.hypot(w[0], w[1] - cy) - radius) / 0.2;
        double along = angleDelta(angle(w[0], w[1] - cy), -Math.PI / 2.0);
        double envelope = smoothstep(0.78, 0.45, Math.abs(along));
        double crest = StrictMath.exp(-across * across);
        double summits = 0.55 + 0.45 * (0.5 + 0.5 * fbm(along * 3.5, 0.7, s + 2, 3));
        double spurs = ridged(w[0] * 3.2, w[1] * 3.2, s + 3, 5) * StrictMath.exp(-across * across / 5.0);
        return (crest * summits * (0.7 + 0.3 * ridged(w[0] * 4.0, w[1] * 4.0, s + 4, 4))
                + 0.45 * spurs * pow(crest, 0.3)) * envelope;
    }

    private static double branchingRange(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.3, 0.1);
        double h = 0.0;
        double[] angles = {-Math.PI / 2.0, Math.PI / 6.0 + 0.15, 5.0 * Math.PI / 6.0 - 0.1};
        double[] lengths = {0.85, 0.75, 0.8};
        for (int arm = 0; arm < angles.length; arm++) {
            double dx = StrictMath.cos(angles[arm]);
            double dy = StrictMath.sin(angles[arm]);
            double along = w[0] * dx + w[1] * dy;
            double perpendicular = Math.abs(-w[0] * dy + w[1] * dx);
            double bend = 0.05 * fbm(along * 2.0, arm * 3.0, s + 20 + arm, 2);
            double distance = along < 0.0 ? Math.hypot(w[0], w[1]) : Math.abs(perpendicular + bend);
            double height = lerp(1.0, 0.55, clamp01(along / lengths[arm]));
            double ridge = StrictMath.exp(-StrictMath.pow(distance / 0.17, 2.0)) * smoothstep(lengths[arm],
                    lengths[arm] * 0.6, along) * height;
            h = smoothMax(h, ridge, 0.08);
        }
        h = smoothMax(h, cone(Math.hypot(w[0], w[1]), 0.32, 1.1), 0.1);
        return h * (0.6 + 0.4 * ridged(w[0] * 3.5, w[1] * 3.5, s + 5, 5));
    }

    private static double parallelRidges(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.1, 0.07);
        double[][] ridges = {{-0.48, 0.7, 0.85}, {0.0, 1.0, 0.95}, {0.46, 0.8, 0.8}};
        double h = 0.0;
        for (int index = 0; index < ridges.length; index++) {
            double bend = 0.08 * fbm(w[1] * 1.4, index * 2.0, s + index, 2);
            double across = (w[0] - ridges[index][0] - bend) / 0.12;
            double along = smoothstep(ridges[index][2], ridges[index][2] - 0.35, Math.abs(w[1] + 0.05 * index));
            double crest = StrictMath.exp(-across * across) * ridges[index][1] * along;
            h = Math.max(h, crest * (0.75 + 0.25 * (0.5 + 0.5 * fbm(w[1] * 3.0, index, s + 7, 3))));
        }
        return h * (0.75 + 0.25 * ridged(w[0] * 5.0, w[1] * 5.0, s + 9, 4));
    }

    // ── Colinas suaves ───────────────────────────────────────────────────────────────────────────────

    private static void hills(List<StampRecipe> list) {
        StampCategory c = StampCategory.HILLS;
        list.add(raise("colina", c, "Colina suave y redonda", 0.72,
                ReliefShapes::hill, Bake.full(16, 1.5, 0.6, 4, 30)));
        list.add(raise("loma", c, "Loma alargada hacia la mirada, más empinada por delante", 0.75,
                ReliefShapes::drumlin, Bake.full(16, 1.0, 0.5, 3, 28)));
        list.add(raise("lomas", c, "Varias lomas bajas que se encadenan", 0.72,
                ReliefShapes::rollingHills, Bake.full(18, 2.0, 0.8, 4, 30)));
        list.add(raise("cerro", c, "Cerro de cima ancha y casi llana: sitio para un pueblo", 0.75,
                ReliefShapes::flatHill, Bake.full(20, 2.0, 0.8, 5, 30)));
        list.add(new StampRecipe("ondulaciones", c, "Ondulaciones suaves que suben y bajan el terreno", 0.5, 0.55,
                StampRecipe.points((x, y, r, s) -> 0.5 + 0.5 * fbm(x * 1.6, y * 1.6, s, 3)), null).withRatio(0.12));
    }

    private static double hill(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.2, 0.12);
        double rr = Math.hypot(w[0], w[1]);
        return StrictMath.exp(-rr * rr * 3.4) * (0.9 + 0.1 * fbm(w[0] * 3.0, w[1] * 3.0, s + 1, 3));
    }

    private static double drumlin(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.0, 0.06);
        double sy = w[1] < -0.1 ? 0.42 : 0.72;
        return gauss(w[0], w[1], 0.0, -0.1, 0.34, sy);
    }

    private static double rollingHills(double x, double y, double r, long s) {
        double[][] points = StampMath.scatter(s, 6, 0.5, 0.3);
        double h = 0.0;
        for (double[] point : points) {
            double radius = 0.3 + 0.15 * point[2];
            double height = 0.55 + 0.45 * point[2];
            double distance = Math.hypot(x - point[0], y - point[1]) / radius;
            h = smoothMax(h, height * StrictMath.exp(-distance * distance * 1.6), 0.25);
        }
        return h + 0.06 * fbm(x * 3.0, y * 3.0, s + 1, 3);
    }

    private static double flatHill(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.4, 0.1);
        double rr = Math.hypot(w[0], w[1]);
        return pow(smoothstep(0.85, 0.3, rr), 1.3) * (0.95 + 0.05 * fbm(w[0] * 3.0, w[1] * 3.0, s + 1, 3));
    }

    // ── Mesetas y mesas ──────────────────────────────────────────────────────────────────────────────

    private static void plateaus(List<StampRecipe> list) {
        StampCategory c = StampCategory.PLATEAUS;
        list.add(raise("mesa", c, "Mesa de cima plana y bordes cortados, con derrubios al pie", 0.85,
                ReliefShapes::mesa, Bake.water(30, 1.2, 0.8)));
        list.add(raise("meseta", c, "Meseta ancha con el borde mordido por barrancos", 0.82,
                ReliefShapes::plateau, Bake.water(32, 2.5, 1.6)));
        list.add(raise("cerro_testigo", c, "Cerro testigo: torre de cima plana sobre su falda", 0.85,
                ReliefShapes::butte, Bake.water(40, 1.0, 0.6)));
        list.add(raise("mesa_escalonada", c, "Mesa en tres pisos, como capas de roca", 0.85,
                ReliefShapes::steppedMesa, Bake.water(30, 1.0, 0.6)));
        list.add(raise("mesa_partida", c, "Mesa partida en dos por un tajo estrecho", 0.85,
                ReliefShapes::splitMesa, Bake.water(32, 1.5, 1.0)));
    }

    private static double mesa(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.12);
        double rr = Math.hypot(w[0], w[1]);
        double rim = 0.52 + 0.06 * around(angle(w[0], w[1]), 1.5, s + 1, 3);
        double top = smoothstep(rim + 0.02, rim - 0.02, rr) * (0.97 + 0.03 * fbm(w[0] * 6.0, w[1] * 6.0, s + 2, 2));
        double talus = 0.38 * pow(smoothstep(rim + 0.36, rim, rr), 1.4);
        return Math.max(top, talus);
    }

    private static double plateau(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.6, 0.14);
        double rr = Math.hypot(w[0], w[1]);
        double a = angle(w[0], w[1]);
        double notches = pow(clamp01(around(a, 4.0, s + 5, 2) * 1.5), 2.0) * 0.18;
        double rim = 0.64 + 0.08 * around(a, 1.3, s + 1, 3) - notches;
        double top = smoothstep(rim + 0.025, rim - 0.03, rr) * (0.92 + 0.06 * fbm(w[0] * 2.0, w[1] * 2.0, s + 2, 3));
        double talus = 0.3 * pow(smoothstep(rim + 0.25, rim, rr), 1.5);
        return Math.max(top, talus);
    }

    private static double butte(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.5, 0.05);
        double rr = Math.hypot(w[0], w[1]);
        double cap = smoothstep(0.24, 0.215, rr);
        double ledge = 0.72 * smoothstep(0.31, 0.285, rr);
        double skirt = 0.5 * pow(smoothstep(0.85, 0.28, rr), 1.6);
        return Math.max(cap, Math.max(ledge, skirt));
    }

    private static double steppedMesa(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.1);
        double rr = Math.hypot(w[0], w[1]);
        double a = angle(w[0], w[1]);
        double[] radii = {0.66, 0.48, 0.3};
        double[] heights = {0.34, 0.68, 1.0};
        double h = 0.18 * pow(smoothstep(0.9, 0.66, rr), 1.5);
        for (int tier = 0; tier < radii.length; tier++) {
            double radius = radii[tier] + 0.05 * around(a, 1.6, s + tier * 7, 3);
            h = Math.max(h, heights[tier] * smoothstep(radius + 0.015, radius - 0.015, rr));
        }
        return h;
    }

    private static double splitMesa(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.0, 0.08);
        double rr = Math.hypot(w[0], w[1] / 0.65);
        double rim = 0.62 + 0.05 * around(angle(w[0], w[1]), 1.5, s + 1, 3);
        double top = smoothstep(rim + 0.02, rim - 0.02, rr);
        double cleft = smoothstep(0.075, 0.035, Math.abs(w[0] - 0.05 * fbm(w[1] * 2.0, 0.0, s + 2, 2)));
        double talus = 0.3 * pow(smoothstep(rim + 0.3, rim, rr), 1.5);
        return Math.max(top * (1.0 - 0.9 * cleft), talus * (1.0 - 0.5 * cleft));
    }

    // ── Acantilados y escarpes ───────────────────────────────────────────────────────────────────────

    private static void cliffs(List<StampRecipe> list) {
        StampCategory c = StampCategory.CLIFFS;
        list.add(raise("acantilado", c, "Acantilado: una pared que se alza delante de ti", 0.82,
                ReliefShapes::cliff, Bake.full(45, 1.2, 0.7, 3, 58)));
        list.add(raise("ensenada", c, "Ensenada: acantilados que rodean una cala abierta hacia ti", 0.82,
                ReliefShapes::cove, Bake.full(42, 1.2, 0.7, 3, 58)));
        list.add(raise("promontorio", c, "Promontorio: lengua de tierra alta cortada a pico", 0.82,
                ReliefShapes::headland, Bake.full(42, 1.2, 0.7, 3, 58)));
        list.add(raise("farallones", c, "Acantilado con farallones sueltos delante", 0.85,
                ReliefShapes::stacks, Bake.slide(45, 2, 60)));
        list.add(raise("canchal", c, "Canchal: cono de derrubios al pie de una pared", 0.85,
                ReliefShapes::screeCone, Bake.slide(40, 6, 36)));
    }

    private static double cliff(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.6, 0.06);
        double chutes = pow(ridged(w[0] * 4.0, 0.5, s + 3, 3), 3.0);
        double edge = 0.12 + 0.1 * fbm(w[0] * 2.2, 0.0, s + 1, 3) - 0.06 * chutes;
        double face = smoothstep(edge + 0.035, edge - 0.035, w[1]);
        double plateau = 0.9 + 0.1 * fbm(w[0] * 2.5, w[1] * 2.5, s + 2, 3) - 0.25 * smoothstep(-0.2, -1.0, w[1]);
        double talus = 0.22 * pow(smoothstep(edge + 0.3, edge, w[1]), 1.5);
        double sides = smoothstep(0.95, 0.55, Math.abs(w[0]));
        return Math.max(face * plateau, talus) * sides;
    }

    private static double cove(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.8, 0.07);
        double rb = Math.hypot(w[0], (w[1] - 0.35) * 1.1);
        double cliffAt = 0.48 + 0.06 * around(angle(w[0], w[1] - 0.35), 2.0, s + 1, 3);
        double land = smoothstep(cliffAt - 0.03, cliffAt + 0.03, rb);
        double plateau = 0.9 + 0.1 * fbm(w[0] * 2.5, w[1] * 2.5, s + 2, 3);
        double talus = 0.2 * pow(smoothstep(cliffAt - 0.25, cliffAt, rb), 1.5);
        double open = smoothstep(0.35, 0.8, w[1]);
        return Math.max(land * plateau, talus) * (1.0 - open);
    }

    private static double headland(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.8, 0.06);
        double bend = 0.08 * fbm(w[1] * 1.3, 0.0, s + 1, 2);
        double distance = StampMath.segment(w[0] - bend, w[1], 0.0, -1.3, 0.0, 0.22);
        double radius = 0.3 + 0.05 * fbm(w[0] * 3.0, w[1] * 3.0, s + 2, 2);
        double top = smoothstep(radius + 0.03, radius - 0.03, distance)
                * (0.8 + 0.2 * smoothstep(0.4, -0.8, w[1])) * (0.95 + 0.05 * fbm(w[0] * 4.0, w[1] * 4.0, s + 3, 2));
        double talus = 0.18 * pow(smoothstep(radius + 0.2, radius, distance), 1.5);
        double stack = 0.6 * smoothstep(0.075, 0.05, Math.hypot(w[0] - 0.12, w[1] - 0.66));
        return Math.max(Math.max(top, talus), stack);
    }

    private static double stacks(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.6, 0.05);
        double edge = -0.28 + 0.08 * fbm(w[0] * 2.4, 0.0, s + 1, 3);
        double cliff = smoothstep(edge + 0.03, edge - 0.03, w[1]) * (0.9 + 0.1 * fbm(w[0] * 3.0, w[1] * 3.0, s + 2, 2));
        double h = Math.max(cliff, 0.15 * pow(smoothstep(edge + 0.2, edge, w[1]), 1.5));
        double[][] pillars = {{-0.36, 0.12, 0.78, 0.085}, {0.06, 0.28, 0.95, 0.1}, {0.4, 0.06, 0.62, 0.07},
                {-0.06, 0.56, 0.48, 0.065}};
        for (int index = 0; index < pillars.length; index++) {
            double[] p = pillars[index];
            double px = p[0] + (unit(s, index) - 0.5) * 0.08;
            double py = p[1] + (unit(s, index + 9) - 0.5) * 0.08;
            double distance = Math.hypot(w[0] - px, w[1] - py);
            double pillar = p[2] * smoothstep(p[3] + 0.02, p[3] - 0.02, distance)
                    * (0.92 + 0.08 * fbm(w[0] * 6.0, w[1] * 6.0, s + 4, 2));
            double rubble = 0.12 * p[2] * pow(smoothstep(p[3] + 0.12, p[3], distance), 2.0);
            h = Math.max(h, Math.max(pillar, rubble));
        }
        return h;
    }

    private static double screeCone(double x, double y, double r, long s) {
        double dx = x;
        double dy = y + 0.62;
        double distance = Math.hypot(dx, dy);
        double delta = angleDelta(angle(dx, dy), Math.PI / 2.0);
        double fan = smoothstep(0.85, 0.6, Math.abs(delta));
        double profile = pow(clamp01(1.0 - distance / 1.3), 1.8);
        double streaks = 0.05 * fbm(delta * 9.0, distance * 1.5, s + 1, 3);
        double source = 0.95 * gauss(x, y, 0.0, -0.66, 0.16, 0.1);
        return Math.max(fan * profile + streaks * fan * profile, source);
    }

    // ── Volcanes ─────────────────────────────────────────────────────────────────────────────────────

    private static void volcanoes(List<StampRecipe> list) {
        StampCategory c = StampCategory.VOLCANOES;
        list.add(raise("volcan", c, "Volcán con cráter en la cima y barrancos radiales", 0.8,
                ReliefShapes::volcano, Bake.full(70, 3.0, 1.4, 3, 45)));
        list.add(raise("volcan_escudo", c, "Volcán en escudo: ancho, de laderas suaves y coladas en lóbulos", 0.7,
                ReliefShapes::shieldVolcano, Bake.water(18, 1.5, 0.6)));
        list.add(raise("cono_ceniza", c, "Cono de ceniza: pequeño, empinado y con un cráter grande", 0.85,
                ReliefShapes::cinderCone, Bake.slide(40, 20, 33)));
        list.add(raise("volcan_roto", c, "Volcán con un flanco hundido abierto hacia ti", 0.8,
                ReliefShapes::collapsedVolcano, Bake.water(65, 2.5, 1.2)));
        list.add(raise("volcan_doble", c, "Volcán con un cono secundario en la ladera", 0.8,
                ReliefShapes::twinVolcano, Bake.water(65, 3.0, 1.3)));
        list.add(raise("colada", c, "Colada de lava: lengua con bordes altos y arrugas en el frente", 0.85,
                ReliefShapes::lavaFlow, null).withRatio(0.16));
    }

    private static double volcanoCone(double[] w, double cx, double cy, double radius, double crater) {
        double rr = Math.hypot(w[0] - cx, w[1] - cy);
        double cone = cone(rr, radius, 1.55);
        double bowl = 0.55 * pow(smoothstep(crater * 1.25, crater * 0.3, rr), 1.2);
        return Math.max(0.0, cone - bowl * cone(0.0, radius, 1.55));
    }

    private static double volcano(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.05);
        return volcanoCone(w, 0.0, 0.0, 0.95, 0.11);
    }

    private static double shieldVolcano(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.2, 0.1);
        double a = angle(w[0], w[1]);
        // Borde en lóbulos: cada colada llega más o menos lejos.
        double reach = 0.82 + 0.13 * around(a, 3.0, s + 1, 3) + 0.06 * StrictMath.cos(a * 7.0 + 1.3);
        double rr = Math.hypot(w[0], w[1]) / reach;
        double shield = rr >= 1.0 ? 0.0 : pow(1.0 - pow(rr, 1.6), 0.8);
        // Una fisura con una fila de cráteres pequeños cruza la cumbre.
        double pits = 0.0;
        for (int index = -1; index <= 1; index++) {
            double px = 0.13 * index;
            double py = -0.06 * index;
            pits = Math.max(pits, (0.16 - 0.03 * Math.abs(index))
                    * smoothstep(0.07, 0.02, Math.hypot(w[0] - px, w[1] - py)));
        }
        double rift = 0.05 * smoothstep(0.03, 0.0, Math.abs(w[1] + 0.46 * w[0])) * smoothstep(0.45, 0.2, rr);
        double flows = 0.05 * pow(0.5 + 0.5 * StrictMath.cos(a * 11.0 + 3.0 * fbm(rr * 2.0, a, s + 4, 2)), 3.0)
                * rr * shield;
        return Math.max(0.0, shield - pits - rift + flows);
    }

    private static double cinderCone(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 2.5, 0.03);
        double rr = Math.hypot(w[0], w[1]);
        if (rr < 0.3) {
            return lerp(0.4, 1.0, pow(rr / 0.3, 1.5));
        }
        return clamp01((0.82 - rr) / 0.52);
    }

    private static double collapsedVolcano(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.05);
        double h = volcanoCone(w, 0.0, -0.05, 0.95, 0.1);
        double rb = Math.hypot(w[0] / 0.8, (w[1] - 0.25) / 1.2);
        double collapse = smoothstep(0.55, 0.38, rb) * smoothstep(-0.25, 0.0, w[1]);
        double floor = 0.22 * cone(Math.hypot(w[0], w[1]), 1.2, 1.0);
        double hummocks = 0.06 * StampNoise.billow(w[0] * 6.0, w[1] * 6.0, s + 3, 3) * smoothstep(0.3, 0.6, w[1]);
        return lerp(h, floor + hummocks, collapse);
    }

    private static double twinVolcano(double x, double y, double r, long s) {
        double[] w = warp(x, y, s, 1.5, 0.05);
        double main = volcanoCone(w, -0.18, -0.12, 0.8, 0.1);
        double second = 0.55 * volcanoCone(w, 0.38, 0.32, 0.42, 0.13);
        return smoothMax(main, second, 0.06);
    }

    private static double lavaFlow(double x, double y, double r, long s) {
        double t = clamp01((y + 0.8) / 1.5);
        double center = 0.12 * fbm(y * 1.4, 0.0, s + 1, 3);
        double halfWidth = lerp(0.1, 0.36, pow(t, 0.7)) + 0.05 * fbm(y * 3.0, 1.0, s + 2, 2);
        double tip = 0.6 + 0.1 * pow(StrictMath.cos(x * 9.0 + 0.4), 2.0) + 0.05 * fbm(x * 3.0, 2.0, s + 3, 2);
        double across = Math.abs(x - center) / halfWidth;
        double inside = smoothstep(1.0, 0.82, across) * smoothstep(tip + 0.03, tip - 0.06, y)
                * smoothstep(-0.88, -0.74, y);
        double levees = 0.3 * StrictMath.exp(-StrictMath.pow((across - 0.82) / 0.12, 2.0));
        double ogives = 0.14 * pow(0.5 + 0.5 * StrictMath.sin((tip - y) * 28.0 - across * across * 6.0), 3.0)
                * smoothstep(0.2, 0.6, t);
        return inside * (0.62 + levees + ogives) * (0.95 + 0.05 * fbm(x * 8.0, y * 8.0, s + 4, 2));
    }
}
