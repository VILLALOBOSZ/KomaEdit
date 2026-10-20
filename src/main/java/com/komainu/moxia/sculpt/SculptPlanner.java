package com.komainu.moxia.sculpt;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.noise.NoiseEngine;
import com.komainu.moxia.progress.ProgressLine;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SculptPlanner {
    public static final int MAX_CHANGES = 2_000_000;
    private static final BlockState AIR = Objects.requireNonNull(BlockTypes.AIR).getDefaultState();
    private static final long NOISE_SEED = 0x4B4F4D4145444954L;

    private SculptPlanner() {
    }

    public static PackedBlockPlan plan(
            SculptSettings settings,
            BlockVector3 center,
            Direction face,
            WorldSnapshot snapshot,
            List<BlockVector3> linePoints
    ) {
        return switch (settings.type().category()) {
            case LINE -> planLine(settings, linePoints);
            case HEIGHT -> planHeight(settings, center, snapshot);
            case SHAPE, VOLUME -> planVolume(settings, center, face, snapshot);
            case MODEL -> throw new IllegalStateException("Los pinceles de modelado van por SculptManager.run");
        };
    }

    private static PackedBlockPlan planVolume(
            SculptSettings settings,
            BlockVector3 center,
            Direction face,
            WorldSnapshot snapshot
    ) {
        int horizontal = settings.radius();
        int vertical = switch (settings.type()) {
            case CYLINDER, CONE -> Math.max(settings.radius(), settings.height());
            case DISC -> settings.radius();
            default -> settings.radius();
        };
        PackedBlockPlan.Builder plan = PackedBlockPlan.builder(estimatedVolume(horizontal));
        for (int y = center.y() - vertical; y <= center.y() + vertical; y++) {
            checkInterrupted();
            for (int z = center.z() - horizontal; z <= center.z() + horizontal; z++) {
                for (int x = center.x() - horizontal; x <= center.x() + horizontal; x++) {
                    if (!snapshot.contains(x, y, z)
                            || !inside(settings, center, face, x, y, z)
                            || !randomlyIncluded(settings, x, y, z)
                            || !acceptsOperation(settings, snapshot, x, y, z)) {
                        continue;
                    }
                    plan.addPattern(x, y, z);
                    enforceLimit(plan.size());
                }
            }
        }
        return plan.build();
    }

    private static PackedBlockPlan planLine(SculptSettings settings, List<BlockVector3> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("La brocha line necesita al menos dos puntos en /komaedit path");
        }
        Set<Long> positions = new HashSet<>();
        int radius = settings.radius();
        double radiusSquared = Math.pow(radius + 0.35, 2.0);
        for (int segment = 1; segment < points.size(); segment++) {
            BlockVector3 start = points.get(segment - 1);
            BlockVector3 end = points.get(segment);
            double deltaX = end.x() - start.x();
            double deltaY = end.y() - start.y();
            double deltaZ = end.z() - start.z();
            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(
                    deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
            ) * 2.0));
            for (int step = 0; step <= steps; step++) {
                checkInterrupted();
                double amount = step / (double) steps;
                int centerX = (int) Math.round(start.x() + deltaX * amount);
                int centerY = (int) Math.round(start.y() + deltaY * amount);
                int centerZ = (int) Math.round(start.z() + deltaZ * amount);
                for (int offsetY = -radius; offsetY <= radius; offsetY++) {
                    for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                            double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
                            boolean inside = distanceSquared <= radiusSquared;
                            if (settings.hollow()) {
                                double inner = Math.max(0.0, radius - 1.0);
                                inside &= distanceSquared >= inner * inner;
                            }
                            if (inside) {
                                positions.add(PackedBlockPlan.pack(
                                        centerX + offsetX,
                                        centerY + offsetY,
                                        centerZ + offsetZ
                                ));
                                enforceLimit(positions.size());
                            }
                        }
                    }
                }
            }
        }
        PackedBlockPlan.Builder plan = PackedBlockPlan.builder(positions.size());
        positions.stream().sorted().forEach(packed -> plan.addPattern(
                PackedBlockPlan.unpackX(packed),
                PackedBlockPlan.unpackY(packed),
                PackedBlockPlan.unpackZ(packed)
        ));
        return plan.build();
    }

    private static PackedBlockPlan planHeight(
            SculptSettings settings,
            BlockVector3 center,
            WorldSnapshot snapshot
    ) {
        int radius = settings.radius();
        int diameter = radius * 2 + 3;
        int offset = radius + 1;
        int[][] heights = new int[diameter][diameter];
        for (int localX = -radius - 1; localX <= radius + 1; localX++) {
            for (int localZ = -radius - 1; localZ <= radius + 1; localZ++) {
                heights[localX + offset][localZ + offset] = surfaceY(
                        snapshot,
                        center.x() + localX,
                        center.z() + localZ
                );
            }
        }

        PackedBlockPlan.Builder plan = PackedBlockPlan.builder(Math.max(64, radius * radius * settings.intensity()));
        for (int localZ = -radius; localZ <= radius; localZ++) {
            checkInterrupted();
            for (int localX = -radius; localX <= radius; localX++) {
                double distance = Math.sqrt(localX * localX + localZ * localZ) / radius;
                if (distance > 1.0) {
                    continue;
                }
                int x = center.x() + localX;
                int z = center.z() + localZ;
                int current = heights[localX + offset][localZ + offset];
                if (current == Integer.MIN_VALUE
                        || !settings.mask().allows(snapshot.get(x, current, z), false)
                        || !randomlyIncluded(settings, x, current, z)) {
                    continue;
                }
                double falloff = settings.falloff().apply(distance);
                int target = targetHeight(settings, center, heights, localX + offset, localZ + offset, current, falloff, x, z);
                addColumn(plan, snapshot, x, z, current, target);
                enforceLimit(plan.size());
            }
        }
        return plan.build();
    }

    private static int targetHeight(
            SculptSettings settings,
            BlockVector3 center,
            int[][] heights,
            int heightX,
            int heightZ,
            int current,
            double falloff,
            int worldX,
            int worldZ
    ) {
        int maximumDelta = Math.max(0, (int) Math.round(settings.intensity() * falloff));
        if (maximumDelta == 0) {
            return current;
        }
        return switch (settings.type()) {
            case RAISE -> current + maximumDelta;
            case LOWER -> current - maximumDelta;
            case FLATTEN -> current + Math.clamp(center.y() - current, -maximumDelta, maximumDelta);
            case SMOOTH -> current + Math.clamp(
                    (int) Math.round(neighborAverage(heights, heightX, heightZ) - current),
                    -maximumDelta,
                    maximumDelta
            );
            case EROSION -> {
                int average = (int) Math.round(neighborAverage(heights, heightX, heightZ));
                int difference = average - current;
                yield current + Math.clamp(difference, -maximumDelta, maximumDelta);
            }
            case NOISE -> {
                double frequency = 0.035 + settings.noise() / 100.0 * 0.18;
                double noise = NoiseEngine.fractalBrownianMotion2D(
                        worldX * frequency,
                        worldZ * frequency,
                        NOISE_SEED,
                        4,
                        2.0,
                        0.5
                );
                yield current + (int) Math.round(noise * maximumDelta);
            }
            default -> current;
        };
    }

    private static void addColumn(
            PackedBlockPlan.Builder plan,
            WorldSnapshot snapshot,
            int x,
            int z,
            int current,
            int target
    ) {
        target = Math.clamp(target, snapshot.minimumY(), snapshot.maximumY());
        if (target > current) {
            for (int y = current + 1; y <= target; y++) {
                plan.addPattern(x, y, z);
            }
        } else if (target < current) {
            for (int y = current; y > target; y--) {
                plan.addOverride(x, y, z, AIR);
            }
        }
    }

    private static boolean inside(
            SculptSettings settings,
            BlockVector3 center,
            Direction face,
            int x,
            int y,
            int z
    ) {
        double deltaX = x - center.x();
        double deltaY = y - center.y();
        double deltaZ = z - center.z();
        double radius = noisyRadius(settings, x, y, z);
        if (radius <= 0.0) {
            return false;
        }
        boolean inside;
        double normalized;
        switch (settings.type()) {
            case CUBE -> {
                normalized = Math.max(Math.abs(deltaX), Math.max(Math.abs(deltaY), Math.abs(deltaZ))) / radius;
                inside = normalized <= 1.0;
            }
            case CYLINDER, DISC -> {
                double axial = switch (face.getAxis()) {
                    case X -> deltaX;
                    case Y -> deltaY;
                    case Z -> deltaZ;
                };
                double radialSquared = switch (face.getAxis()) {
                    case X -> deltaY * deltaY + deltaZ * deltaZ;
                    case Y -> deltaX * deltaX + deltaZ * deltaZ;
                    case Z -> deltaX * deltaX + deltaY * deltaY;
                };
                double halfHeight = settings.type() == SculptBrushType.DISC
                        ? 0.25
                        : Math.max(0.5, settings.height() / 2.0);
                normalized = Math.max(Math.sqrt(radialSquared) / radius, Math.abs(axial) / halfHeight);
                inside = radialSquared <= radius * radius && Math.abs(axial) <= halfHeight;
            }
            case DOME -> {
                normalized = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) / radius;
                inside = deltaY >= 0.0 && normalized <= 1.0;
            }
            case CONE -> {
                if (deltaY < 0.0 || deltaY > settings.height()) {
                    return false;
                }
                double layerRadius = radius * (1.0 - deltaY / settings.height());
                normalized = layerRadius <= 0.0 ? 1.0 : Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) / layerRadius;
                inside = deltaX * deltaX + deltaZ * deltaZ <= layerRadius * layerRadius;
            }
            default -> {
                normalized = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) / radius;
                inside = normalized <= 1.0;
            }
        }
        if (!inside || !settings.hollow()) {
            return inside;
        }
        return normalized >= Math.max(0.0, 1.0 - 1.25 / radius);
    }

    private static boolean acceptsOperation(
            SculptSettings settings,
            WorldSnapshot snapshot,
            int x,
            int y,
            int z
    ) {
        BlockState current = snapshot.get(x, y, z);
        boolean air = current.getBlockType().getMaterial().isAir();
        boolean solid = current.getBlockType().getMaterial().isSolid();
        return switch (settings.type()) {
            case FILL -> air && settings.mask().allows(current, true);
            case REPLACE -> !air && settings.mask().allows(current, false);
            case OVERLAY -> air
                    && snapshot.get(x, y - 1, z).getBlockType().getMaterial().isSolid()
                    && settings.mask().allows(current, true);
            case SURFACE, PAINT, BLEND -> solid
                    && exposed(snapshot, x, y, z)
                    && settings.mask().allows(current, false);
            default -> settings.mask().allows(current, false);
        };
    }

    private static boolean exposed(WorldSnapshot snapshot, int x, int y, int z) {
        return snapshot.get(x + 1, y, z).getBlockType().getMaterial().isAir()
                || snapshot.get(x - 1, y, z).getBlockType().getMaterial().isAir()
                || snapshot.get(x, y + 1, z).getBlockType().getMaterial().isAir()
                || snapshot.get(x, y - 1, z).getBlockType().getMaterial().isAir()
                || snapshot.get(x, y, z + 1).getBlockType().getMaterial().isAir()
                || snapshot.get(x, y, z - 1).getBlockType().getMaterial().isAir();
    }

    private static int surfaceY(WorldSnapshot snapshot, int x, int z) {
        for (int y = snapshot.maximumY() - 1; y >= snapshot.minimumY(); y--) {
            BlockState current = snapshot.get(x, y, z);
            BlockState above = snapshot.get(x, y + 1, z);
            if (current.getBlockType().getMaterial().isSolid()
                    && !current.getBlockType().getMaterial().isLiquid()
                    && !above.getBlockType().getMaterial().isSolid()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static double neighborAverage(int[][] heights, int x, int z) {
        long sum = 0;
        int count = 0;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                int value = heights[x + offsetX][z + offsetZ];
                if (value != Integer.MIN_VALUE) {
                    sum += value;
                    count++;
                }
            }
        }
        return count == 0 ? heights[x][z] : sum / (double) count;
    }

    private static double noisyRadius(SculptSettings settings, int x, int y, int z) {
        if (settings.noise() == 0) {
            return settings.radius();
        }
        double amount = settings.noise() / 100.0 * Math.max(1.0, settings.radius() * 0.3);
        double noise = NoiseEngine.fractalBrownianMotion2D(x * 0.11 + y * 0.037, z * 0.11, NOISE_SEED, 3, 2.0, 0.5);
        return settings.radius() + noise * amount;
    }

    private static boolean randomlyIncluded(SculptSettings settings, int x, int y, int z) {
        if (settings.randomness() == 0) {
            return true;
        }
        double keepProbability = 1.0 - settings.randomness() / 200.0;
        return NoiseEngine.deterministicUnit(x, y, z, NOISE_SEED) <= keepProbability;
    }

    private static int estimatedVolume(int radius) {
        long diameter = radius * 2L + 1L;
        return (int) Math.min(250_000L, diameter * diameter * diameter);
    }

    private static void enforceLimit(int size) {
        if (size > MAX_CHANGES) {
            throw new IllegalArgumentException("La brocha supera el límite seguro de " + ProgressLine.group(MAX_CHANGES) + " bloques");
        }
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Cálculo cancelado");
        }
    }
}
