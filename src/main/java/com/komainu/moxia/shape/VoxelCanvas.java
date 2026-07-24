package com.komainu.moxia.shape;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CancellationException;

final class VoxelCanvas {
    private final int limit;
    private final LongOpenHashSet blocks = new LongOpenHashSet();
    private int cancellationCounter;

    VoxelCanvas(int limit) {
        this.limit = limit;
    }

    Set<BlockVector3> blocks() {
        Set<BlockVector3> resolved = new HashSet<>(blocks.size());
        blocks.forEach((long packed) -> resolved.add(BlockVector3.at(
                PackedBlockPlan.unpackX(packed),
                PackedBlockPlan.unpackY(packed),
                PackedBlockPlan.unpackZ(packed)
        )));
        return Collections.unmodifiableSet(resolved);
    }

    void add(Vec3 point) {
        add(
                (int) Math.floor(point.x()),
                (int) Math.floor(point.y()),
                (int) Math.floor(point.z())
        );
    }

    void brush(Vec3 point, int thickness) {
        double radius = Math.max(0.0, thickness - 1.0);
        if (radius < 0.25) {
            add(point);
            return;
        }
        int minimumX = (int) Math.floor(point.x() - radius);
        int maximumX = (int) Math.floor(point.x() + radius);
        int minimumY = (int) Math.floor(point.y() - radius);
        int maximumY = (int) Math.floor(point.y() + radius);
        int minimumZ = (int) Math.floor(point.z() - radius);
        int maximumZ = (int) Math.floor(point.z() + radius);
        double maximumDistance = radius + 0.35;
        double maximumDistanceSquared = maximumDistance * maximumDistance;
        for (int x = minimumX; x <= maximumX; x++) {
            double xDistance = x + 0.5 - point.x();
            double xDistanceSquared = xDistance * xDistance;
            for (int y = minimumY; y <= maximumY; y++) {
                double yDistance = y + 0.5 - point.y();
                double horizontalDistanceSquared = xDistanceSquared + yDistance * yDistance;
                if (horizontalDistanceSquared > maximumDistanceSquared) {
                    continue;
                }
                for (int z = minimumZ; z <= maximumZ; z++) {
                    double zDistance = z + 0.5 - point.z();
                    if (horizontalDistanceSquared + zDistance * zDistance <= maximumDistanceSquared) {
                        add(x, y, z);
                    }
                }
            }
        }
    }

    void line(Vec3 start, Vec3 end, int thickness) {
        double distance = start.distance(end);
        int steps = Math.max(1, (int) Math.ceil(distance * 2.0));
        for (int step = 0; step <= steps; step++) {
            brush(start.lerp(end, step / (double) steps), thickness);
        }
    }

    void rectangle(
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            double firstRadius,
            double secondRadius,
            int thickness
    ) {
        int firstSteps = Math.max(1, (int) Math.ceil(firstRadius * 1.25));
        int secondSteps = Math.max(1, (int) Math.ceil(secondRadius * 1.25));
        for (int firstStep = -firstSteps; firstStep <= firstSteps; firstStep++) {
            double firstOffset = firstStep * firstRadius / firstSteps;
            for (int secondStep = -secondSteps; secondStep <= secondSteps; secondStep++) {
                double secondOffset = secondStep * secondRadius / secondSteps;
                brush(center
                        .add(firstAxis.multiply(firstOffset))
                        .add(secondAxis.multiply(secondOffset)), thickness);
            }
        }
    }

    void filledDisc(
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            double radius,
            int thickness
    ) {
        int steps = Math.max(2, (int) Math.ceil(radius * 1.35));
        Vec3 normal = firstAxis.cross(secondAxis).normalized();
        for (int firstStep = -steps; firstStep <= steps; firstStep++) {
            double first = radius * firstStep / steps;
            for (int secondStep = -steps; secondStep <= steps; secondStep++) {
                double second = radius * secondStep / steps;
                if (first * first + second * second > radius * radius) {
                    continue;
                }
                Vec3 point = center
                        .add(firstAxis.multiply(first))
                        .add(secondAxis.multiply(second));
                for (int depth = 0; depth < thickness; depth++) {
                    double offset = depth - (thickness - 1.0) * 0.5;
                    add(point.add(normal.multiply(offset)));
                }
            }
        }
    }

    void sphereShell(Vec3 center, double radius, int thickness) {
        double outer = radius + 0.55;
        double inner = Math.max(0.0, radius - thickness + 0.1);
        int minimumX = (int) Math.floor(center.x() - outer);
        int maximumX = (int) Math.floor(center.x() + outer);
        int minimumY = (int) Math.floor(center.y() - outer);
        int maximumY = (int) Math.floor(center.y() + outer);
        double outerSquared = outer * outer;
        double innerSquared = inner * inner;
        for (int x = minimumX; x <= maximumX; x++) {
            double xOffset = x + 0.5 - center.x();
            for (int y = minimumY; y <= maximumY; y++) {
                double yOffset = y + 0.5 - center.y();
                double horizontalSquared = xOffset * xOffset + yOffset * yOffset;
                if (horizontalSquared > outerSquared) {
                    continue;
                }
                double outerZ = Math.sqrt(outerSquared - horizontalSquared);
                double innerZ = horizontalSquared < innerSquared
                        ? Math.sqrt(innerSquared - horizontalSquared)
                        : -1.0;
                int minimumZ = (int) Math.ceil(center.z() - outerZ - 0.5);
                int maximumZ = (int) Math.floor(center.z() + outerZ - 0.5);
                for (int z = minimumZ; z <= maximumZ; z++) {
                    double zDistance = Math.abs(z + 0.5 - center.z());
                    if (innerZ < 0.0 || zDistance >= innerZ) {
                        add(x, y, z);
                    }
                }
            }
        }
    }

    private void add(int x, int y, int z) {
        if ((cancellationCounter++ & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
        if (blocks.add(PackedBlockPlan.pack(x, y, z)) && blocks.size() > limit) {
            throw new GeometryLimitException(limit);
        }
    }
}
