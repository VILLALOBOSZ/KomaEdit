package com.komainu.moxia.terrain;

import com.komainu.moxia.noise.NoiseEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandformPlannerTest {
    private static final Runnable NO_CANCEL = FakeTerrain.NO_CANCEL;

    private static LandformPlanner.Settings settings(LandformPlanner.Kind kind, double halfWidth, double height,
                                                     double erosion) {
        return new LandformPlanner.Settings(kind, halfWidth, height, LandformPlanner.Profile.BELL, 0.7, 5L,
                LandformPlanner.Side.RIGHT, LandformPlanner.Form.V, kind == LandformPlanner.Kind.VALLEY
                ? LandformPlanner.Blend.MAX : LandformPlanner.Blend.ADD, erosion, false, List.of());
    }

    private static HeightField hills(int size, long seed) {
        HeightField field = new HeightField(0, 0, size, size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                double height = Math.round(40 + 12 * NoiseEngine.fractalBrownianMotion2D(x * 0.018, z * 0.018, seed,
                        4, 2.0, 0.5));
                field.define(x, z, height, true, 0.0, 200.0);
            }
        }
        return field;
    }

    private static double distanceToPath(List<double[]> path, double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] point : path) {
            best = Math.min(best, Math.hypot(point[0] - x, point[2] - z));
        }
        return best;
    }

    @Test
    void nothingChangesBeyondTheReachAndNothingShootsUp() {
        for (LandformPlanner.Kind kind : LandformPlanner.Kind.values()) {
            for (double erosion : new double[]{0.0, 0.6}) {
                HeightField field = hills(260, 53L);
                HeightField before = field.copy();
                List<double[]> path = TerrainScenes.curve(new double[][]{{20, 40, 130}, {100, 40, 80},
                        {170, 40, 150}, {240, 40, 110}});
                LandformPlanner.Settings settings = settings(kind, 44, 44, erosion);
                LandformPlanner.plan(field, path, false, settings, NO_CANCEL);
                for (int index = 0; index < field.columns(); index++) {
                    double x = field.xOf(index) + 0.5;
                    double z = field.zOf(index) + 0.5;
                    double change = field.at(index) - before.at(index);
                    if (distanceToPath(path, x, z) > settings.reach() + 8.0) {
                        assertEquals(0.0, change, 1e-9, kind + " con erosión " + erosion + " cambia " + x + ", " + z
                                + " fuera de su alcance");
                    }
                    // Al pie, lo que arrastran las gotas se reparte: nada de columnas que se disparen.
                    if (distanceToPath(path, x, z) > settings.halfWidth() * 1.1) {
                        assertTrue(change < 4.0 + settings.height() * 0.3, kind + " con erosión " + erosion
                                + " amontona " + change + " al pie en " + x + ", " + z);
                    }
                    assertTrue(change < settings.height() * 1.4 + 2.0 && change > -(settings.height() * 1.4 + 2.0),
                            kind + " con erosión " + erosion + " salta " + change + " en " + x + ", " + z);
                }
            }
        }
    }
}
