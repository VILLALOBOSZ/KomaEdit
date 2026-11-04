package com.komainu.moxia.terrain;

import com.komainu.moxia.noise.NoiseEngine;
import com.komainu.moxia.terrain.stamp.StampCategory;

/** Sellos capturados de terrenos de mentira, para las maquetas del Studio: un valle, unas cumbres y una cala. */
public final class OwnStampFixtures {
    private OwnStampFixtures() {
    }

    public static StampCapture.Result valley() {
        int size = 160;
        StampCapture.Columns columns = new StampCapture.Columns(size, 120);
        for (int z = 0; z < 120; z++) {
            for (int x = 0; x < size; x++) {
                double axis = 60 + 14 * Math.sin(x * 0.035);
                double across = Math.abs(z - axis);
                double walls = 34 * Math.min(1.0, Math.pow(across / 46.0, 1.3));
                double noise = 6 * NoiseEngine.ridged2D(x * 0.05, z * 0.05, 7L, 4);
                columns.set(x, z, (int) Math.round(40 + walls + noise * Math.min(1.0, across / 20.0)), false,
                        "minecraft:grass_block");
            }
        }
        return StampCapture.build("valle_del_norte", columns, null, () -> {
        });
    }

    public static StampCapture.Result peaks() {
        int size = 140;
        StampCapture.Columns columns = new StampCapture.Columns(size, size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                double r = Math.hypot(x - 70, z - 70) / 62.0;
                double mass = Math.max(0.0, 1.0 - r * r);
                double ridges = NoiseEngine.ridged2D(x * 0.03, z * 0.03, 11L, 5);
                columns.set(x, z, (int) Math.round(30 + 70 * mass * (0.45 + 0.55 * ridges)), false,
                        mass > 0.5 ? "minecraft:stone" : "minecraft:grass_block");
            }
        }
        return StampCapture.build("cumbres_de_ana", columns, null, () -> {
        });
    }

    public static StampCapture.Result cove() {
        int size = 120;
        StampCapture.Columns columns = new StampCapture.Columns(size, size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                double r = Math.hypot(x - 60, z - 72) / 40.0;
                double land = 26 + 16 * Math.clamp((r - 0.8) * 2.0, -1.0, 1.0)
                        + 4 * NoiseEngine.fractalBrownianMotion2D(x * 0.04, z * 0.04, 3L, 3, 2.0, 0.5);
                int top = (int) Math.round(land);
                columns.set(x, z, top, top < 24, top < 24 ? "minecraft:sand" : "minecraft:grass_block");
            }
        }
        return StampCapture.build("cala_azul", columns, StampCategory.ISLANDS, () -> {
        });
    }

    /** Lo que manda el servidor de cada uno. */
    public static OwnStampSummary summary(StampCapture.Result result, String author, String description,
                                          int sizeX, int sizeZ, boolean mine) {
        HeightStamp stamp = result.stamp();
        return new OwnStampSummary(stamp.name(), author, description, result.category(),
                (float) result.heightRatio(), (float) stamp.neutral(), "fixture-" + stamp.name(), sizeX, sizeZ,
                mine);
    }
}
