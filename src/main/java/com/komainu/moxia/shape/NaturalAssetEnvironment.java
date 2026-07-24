package com.komainu.moxia.shape;

import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NaturalAssetEnvironment {
    private static final int SECTORS = 16;

    private NaturalAssetEnvironment() {
    }

    public static ProceduralRecipe adapt(World world, PathSnapshot path, ProceduralRecipe recipe) {
        if (recipe.shape() != PathShape.EPIC_TREE || path.points().isEmpty()) {
            return recipe;
        }

        BlockVector3 anchor = path.points().getFirst();
        double preferredAngle = preferredAngle(path, recipe.rollDegrees());
        int scanRadius = Math.clamp((int) Math.round(recipe.radius() * 2.15), 12, 48);
        int scanHeight = Math.clamp(recipe.height(), 16, 112);
        int verticalStep = Math.max(3, scanHeight / 10);
        double[] pressure = new double[SECTORS];

        for (int sector = 0; sector < SECTORS; sector++) {
            double angle = sector * Math.PI * 2.0 / SECTORS;
            double directionX = Math.cos(angle);
            double directionZ = Math.sin(angle);
            double sideX = -directionZ;
            double sideZ = directionX;
            double score = 0.0;
            for (int distance = 4; distance <= scanRadius; distance += 3) {
                double distanceWeight = 1.0 + (scanRadius - distance) / (double) scanRadius;
                for (int y = 3; y <= scanHeight; y += verticalStep) {
                    double verticalWeight = 0.65 + y / (double) scanHeight;
                    for (int lateral = -1; lateral <= 1; lateral++) {
                        BlockPos position = new BlockPos(
                                anchor.x() + (int) Math.round(directionX * distance + sideX * lateral * 2.0),
                                anchor.y() + y,
                                anchor.z() + (int) Math.round(directionZ * distance + sideZ * lateral * 2.0)
                        );
                        if (!world.isChunkLoaded(position)) {
                            score += 0.35;
                            continue;
                        }
                        var state = world.getBlockState(position);
                        if (!state.isAir() && state.getFluidState().isEmpty()) {
                            score += distanceWeight * verticalWeight;
                        }
                    }
                }
            }
            double preferenceDistance = Math.abs(wrappedAngle(angle - preferredAngle)) / Math.PI;
            pressure[sector] = score + preferenceDistance * 0.35;
        }

        int openSector = 0;
        double minimum = pressure[0];
        double total = pressure[0];
        for (int sector = 1; sector < SECTORS; sector++) {
            total += pressure[sector];
            if (pressure[sector] < minimum) {
                minimum = pressure[sector];
                openSector = sector;
            }
        }
        double average = total / SECTORS;
        double strength = average <= 0.01
                ? 0.0
                : Math.clamp((average - minimum) / (average * 0.65 + 1.0), 0.0, 1.0);
        double openAngle = strength < 0.08
                ? preferredAngle
                : openSector * Math.PI * 2.0 / SECTORS;

        Map<String, Double> options = new LinkedHashMap<>(recipe.options());
        options.put("assetopenangle", Math.toDegrees(openAngle));
        options.put("assetopenstrength", strength);
        return recipe.withOptions(options);
    }

    private static double preferredAngle(PathSnapshot path, int rollDegrees) {
        if (path.points().size() >= 2) {
            BlockVector3 first = path.points().get(0);
            BlockVector3 second = path.points().get(1);
            double x = second.x() - first.x();
            double z = second.z() - first.z();
            if (x * x + z * z > 0.25) {
                return Math.atan2(z, x);
            }
        }
        return Math.toRadians(rollDegrees);
    }

    private static double wrappedAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}
