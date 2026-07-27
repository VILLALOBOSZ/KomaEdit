package com.komainu.moxia.recipe;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.Vec3;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

final class ContinuousModifierEngine {
    private static final int MAX_BLOCKS = 1_500_000;

    private ContinuousModifierEngine() {
    }

    static Set<BlockVector3> generate(PathSnapshot snapshot, ProceduralRecipe recipe) {
        SampledPath path = createPath(snapshot, recipe.shape());
        ShapeParameters mainParameters = recipe.shapeParameters();
        Set<BlockVector3> main = ShapeGenerator.generate(path, mainParameters);
        PathField field = new PathField(path, recipe.radius());
        Set<BlockVector3> modifiedMain = recipe.extensions().usesCurve()
                ? deform(path, field, main, recipe.extensions())
                : main;
        if (!recipe.extensions().rhythmEnabled()) {
            return modifiedMain;
        }
        Set<BlockVector3> accent = rhythmAccent(path, field, recipe);
        Set<BlockVector3> modifiedAccent = recipe.extensions().usesCurve()
                ? deform(path, field, accent, recipe.extensions())
                : accent;
        Set<BlockVector3> result = new HashSet<>(Math.min(
                MAX_BLOCKS,
                modifiedMain.size() + modifiedAccent.size()
        ));
        int merged = 0;
        for (BlockVector3 block : modifiedMain) {
            checkCancelled(merged++);
            add(result, block);
        }
        for (BlockVector3 block : modifiedAccent) {
            checkCancelled(merged++);
            add(result, block);
        }
        return result;
    }

    private static SampledPath createPath(PathSnapshot snapshot, PathShape shape) {
        return shape == PathShape.SCALES || shape == PathShape.SPIKED_SCALES
                ? SampledPath.create(snapshot.points(), snapshot.closed())
                : SampledPath.createSmooth(snapshot.points(), snapshot.closed());
    }

    private static Set<BlockVector3> rhythmAccent(
            SampledPath path,
            PathField field,
            ProceduralRecipe recipe
    ) {
        RecipeExtensions extensions = recipe.extensions();
        if (extensions.accentShape() == recipe.shape()) {
            return Set.of();
        }
        ShapeParameters accentParameters = new ShapeParameters(
                extensions.accentShape(),
                recipe.radius(),
                recipe.spacing(),
                recipe.thickness(),
                recipe.rollDegrees(),
                extensions.accentShape().defaults().options()
        );
        Set<BlockVector3> accent = ShapeGenerator.generate(path, accentParameters);
        double windowLength = Math.max(4.0, recipe.radius() * 1.6);
        Set<BlockVector3> result = new HashSet<>(Math.min(MAX_BLOCKS, accent.size() / 3));
        int processed = 0;
        for (BlockVector3 block : accent) {
            checkCancelled(processed++);
            Window window = window(field.progress(center(block)), path.length(), windowLength,
                    extensions.rhythmInterval());
            if (window.accent() || window.transitionToAccent()) {
                add(result, block);
            }
        }
        return result;
    }

    private static Window window(double progress, double pathLength, double windowLength, int interval) {
        double position = progress * pathLength / windowLength;
        int index = Math.max(0, (int) Math.floor(position));
        double local = position - index;
        boolean accent = (index + 1) % interval == 0;
        boolean previousAccent = index > 0 && index % interval == 0;
        boolean nextAccent = (index + 2) % interval == 0;
        boolean transitionToAccent = previousAccent && local < 0.12 || nextAccent && local > 0.88;
        return new Window(accent, transitionToAccent);
    }

    private static Set<BlockVector3> deform(
            SampledPath path,
            PathField field,
            Set<BlockVector3> source,
            RecipeExtensions extensions
    ) {
        LongitudinalCurve longitudinal = new LongitudinalCurve(extensions);
        Long2LongOpenHashMap transformed = new Long2LongOpenHashMap(source.size());
        transformed.defaultReturnValue(Long.MIN_VALUE);
        Set<BlockVector3> result = new HashSet<>(source.size());
        int processed = 0;
        for (BlockVector3 block : source) {
            checkCancelled(processed++);
            BlockVector3 target = transform(path, field, longitudinal, block, extensions);
            transformed.put(pack(block), pack(target));
            add(result, target);
        }

        double maximumScale = Math.max(extensions.startScalePercent(), extensions.endScalePercent()) / 100.0;
        int bridgeLimit = Math.clamp((int) Math.ceil(
                Math.max(longitudinal.maximumSpacingRatio(), maximumScale) * 4.0
        ), 4, 16);
        for (BlockVector3 block : source) {
            checkCancelled(processed++);
            long firstPacked = transformed.get(pack(block));
            BlockVector3 first = unpack(firstPacked);
            connectNeighbor(result, transformed, first, block.x() + 1, block.y(), block.z(), bridgeLimit);
            connectNeighbor(result, transformed, first, block.x(), block.y() + 1, block.z(), bridgeLimit);
            connectNeighbor(result, transformed, first, block.x(), block.y(), block.z() + 1, bridgeLimit);
        }
        return result;
    }

    private static BlockVector3 transform(
            SampledPath path,
            PathField field,
            LongitudinalCurve longitudinal,
            BlockVector3 block,
            RecipeExtensions extensions
    ) {
        Vec3 point = center(block);
        SampledPath.Frame sourceFrame = field.nearest(point);
        double progress = sourceFrame.distance() / path.length();
        double eased = extensions.easing().apply(progress);
        double scale = interpolate(
                extensions.startScalePercent(),
                extensions.endScalePercent(),
                eased
        ) / 100.0;
        double targetProgress = longitudinal.map(progress);
        SampledPath.Frame targetFrame = path.at(targetProgress * path.length());
        Vec3 delta = point.subtract(sourceFrame.position());
        double tangent = delta.dot(sourceFrame.tangent()) * longitudinal.localRatio(progress);
        double normal = delta.dot(sourceFrame.normal());
        double binormal = delta.dot(sourceFrame.binormal());
        double twist = Math.toRadians(extensions.twistDegrees() * eased);
        double cosine = Math.cos(twist);
        double sine = Math.sin(twist);
        double rotatedNormal = (normal * cosine - binormal * sine) * scale;
        double rotatedBinormal = (normal * sine + binormal * cosine) * scale;
        Vec3 target = targetFrame.position()
                .add(targetFrame.tangent().multiply(tangent))
                .add(targetFrame.normal().multiply(rotatedNormal))
                .add(targetFrame.binormal().multiply(rotatedBinormal));
        return block(target);
    }

    private static void connectNeighbor(
            Set<BlockVector3> result,
            Long2LongOpenHashMap transformed,
            BlockVector3 first,
            int x,
            int y,
            int z,
            int bridgeLimit
    ) {
        if (y < -2048 || y > 2047) {
            return;
        }
        long sourceNeighbor = PackedBlockPlan.pack(x, y, z);
        if (!transformed.containsKey(sourceNeighbor)) {
            return;
        }
        BlockVector3 second = unpack(transformed.get(sourceNeighbor));
        int deltaX = second.x() - first.x();
        int deltaY = second.y() - first.y();
        int deltaZ = second.z() - first.z();
        int steps = Math.max(Math.max(Math.abs(deltaX), Math.abs(deltaY)), Math.abs(deltaZ));
        if (steps <= 1 || steps > bridgeLimit) {
            return;
        }
        for (int step = 1; step < steps; step++) {
            double amount = step / (double) steps;
            add(result, BlockVector3.at(
                    (int) Math.round(first.x() + deltaX * amount),
                    (int) Math.round(first.y() + deltaY * amount),
                    (int) Math.round(first.z() + deltaZ * amount)
            ));
        }
    }

    private static void add(Set<BlockVector3> blocks, BlockVector3 block) {
        if (blocks.add(block) && blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Los modificadores superan 1 500 000 bloques");
        }
    }

    private static void checkCancelled(int processed) {
        if ((processed & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    private static double interpolate(double first, double second, double progress) {
        return first + (second - first) * progress;
    }

    private static Vec3 center(BlockVector3 block) {
        return new Vec3(block.x() + 0.5, block.y() + 0.5, block.z() + 0.5);
    }

    private static BlockVector3 block(Vec3 point) {
        return BlockVector3.at(
                (int) Math.floor(point.x()),
                (int) Math.floor(point.y()),
                (int) Math.floor(point.z())
        );
    }

    private static long pack(BlockVector3 block) {
        return PackedBlockPlan.pack(block.x(), block.y(), block.z());
    }

    private static BlockVector3 unpack(long packed) {
        return BlockVector3.at(
                PackedBlockPlan.unpackX(packed),
                PackedBlockPlan.unpackY(packed),
                PackedBlockPlan.unpackZ(packed)
        );
    }

    private record Window(boolean accent, boolean transitionToAccent) {
    }

    private static final class LongitudinalCurve {
        private static final int STEPS = 256;
        private final RecipeExtensions extensions;
        private final double[] cumulative = new double[STEPS + 1];
        private final double average;
        private final double maximumSpacingRatio;

        private LongitudinalCurve(RecipeExtensions extensions) {
            this.extensions = extensions;
            double maximum = 1.0;
            for (int step = 1; step <= STEPS; step++) {
                double progress = (step - 0.5) / STEPS;
                double ratio = spacingRatio(progress);
                cumulative[step] = cumulative[step - 1] + ratio;
                maximum = Math.max(maximum, ratio);
            }
            average = cumulative[STEPS] / STEPS;
            maximumSpacingRatio = maximum / average;
            for (int step = 1; step <= STEPS; step++) {
                cumulative[step] /= cumulative[STEPS];
            }
        }

        private double map(double progress) {
            double scaled = Math.clamp(progress, 0.0, 1.0) * STEPS;
            int lower = Math.min(STEPS - 1, (int) Math.floor(scaled));
            double fraction = scaled - lower;
            return interpolate(cumulative[lower], cumulative[lower + 1], fraction);
        }

        private double localRatio(double progress) {
            return spacingRatio(progress) / average;
        }

        private double maximumSpacingRatio() {
            return maximumSpacingRatio;
        }

        private double spacingRatio(double progress) {
            double eased = extensions.easing().apply(progress);
            return interpolate(
                    extensions.startSpacingPercent(),
                    extensions.endSpacingPercent(),
                    eased
            ) / 100.0;
        }
    }

    private static final class PathField {
        private final SampledPath path;
        private final int cellSize;
        private final List<SampledPath.Frame> samples;
        private final Long2ObjectOpenHashMap<IntArrayList> buckets = new Long2ObjectOpenHashMap<>();

        private PathField(SampledPath path, int radius) {
            this.path = path;
            cellSize = Math.max(8, Math.min(64, radius * 2 + 8));
            double step = 0.75;
            int count = Math.max(2, (int) Math.ceil(path.length() / step) + 1);
            samples = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                SampledPath.Frame frame = path.at(path.length() * index / (count - 1.0));
                samples.add(frame);
                long key = cell(frame.position());
                IntArrayList bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new IntArrayList();
                    buckets.put(key, bucket);
                }
                bucket.add(index);
            }
        }

        private double progress(Vec3 point) {
            return nearest(point).distance() / path.length();
        }

        private SampledPath.Frame nearest(Vec3 point) {
            int cellX = coordinate(point.x());
            int cellY = coordinate(point.y());
            int cellZ = coordinate(point.z());
            int nearestIndex = -1;
            double nearestDistance = Double.MAX_VALUE;
            for (int radius = 0; radius <= (nearestIndex < 0 ? 2 : 1); radius++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            if (radius > 0
                                    && Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) != radius) {
                                continue;
                            }
                            IntArrayList bucket = buckets.get(PackedBlockPlan.pack(
                                    cellX + x,
                                    cellY + y,
                                    cellZ + z
                            ));
                            if (bucket == null) {
                                continue;
                            }
                            for (int offset = 0; offset < bucket.size(); offset++) {
                                int index = bucket.getInt(offset);
                                double distance = squaredDistance(point, samples.get(index).position());
                                if (distance < nearestDistance) {
                                    nearestDistance = distance;
                                    nearestIndex = index;
                                }
                            }
                        }
                    }
                }
            }
            if (nearestIndex >= 0) {
                return samples.get(nearestIndex);
            }
            for (int index = 0; index < samples.size(); index++) {
                double distance = squaredDistance(point, samples.get(index).position());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestIndex = index;
                }
            }
            return samples.get(Math.max(0, nearestIndex));
        }

        private long cell(Vec3 point) {
            return PackedBlockPlan.pack(coordinate(point.x()), coordinate(point.y()), coordinate(point.z()));
        }

        private int coordinate(double value) {
            return (int) Math.floor(value / cellSize);
        }

        private static double squaredDistance(Vec3 first, Vec3 second) {
            double x = first.x() - second.x();
            double y = first.y() - second.y();
            double z = first.z() - second.z();
            return x * x + y * y + z * z;
        }
    }
}
