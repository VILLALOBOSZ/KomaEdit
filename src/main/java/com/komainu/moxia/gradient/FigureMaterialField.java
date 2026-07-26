package com.komainu.moxia.gradient;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.recipe.MaterialMetric;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.Vec3;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

final class FigureMaterialField {
    private static final double SCALE_INCLINATION_DEGREES = 18.0;
    private final ProceduralRecipe recipe;
    private final SampledPath path;
    private final PathLookup lookup;
    private final LongitudinalMapping longitudinal;
    private final double pitch;
    private final double roll;

    FigureMaterialField(ProceduralRecipe recipe, PathSnapshot snapshot) {
        this.recipe = recipe;
        path = recipe.shape() == PathShape.SCALES || recipe.shape() == PathShape.SPIKED_SCALES
                ? SampledPath.create(snapshot.points(), snapshot.closed())
                : SampledPath.createSmooth(snapshot.points(), snapshot.closed());
        double maximumScale = Math.max(
                recipe.extensions().startScalePercent(),
                recipe.extensions().endScalePercent()
        ) / 100.0;
        lookup = new PathLookup(path, recipe.radius(), maximumScale);
        longitudinal = new LongitudinalMapping(recipe);
        pitch = motifPitch(recipe);
        roll = Math.toRadians(recipe.rollDegrees());
    }

    double value(MaterialMetric metric, BlockVector3 block, double axisValue) {
        Vec3 center = new Vec3(block.x() + 0.5, block.y() + 0.5, block.z() + 0.5);
        SampledPath.Frame frame = lookup.nearest(center);
        Vec3 delta = center.subtract(frame.position());
        double normal = delta.dot(frame.normal());
        double binormal = delta.dot(frame.binormal());
        double radial = Math.sqrt(normal * normal + binormal * binormal);
        double targetProgress = frame.distance() / path.length();
        double progress = longitudinal.sourceProgress(targetProgress);
        double eased = recipe.extensions().easing().apply(progress);
        double scale = interpolate(
                recipe.extensions().startScalePercent(),
                recipe.extensions().endScalePercent(),
                eased
        ) / 100.0;
        double sourceDistance = progress * path.length();
        double sourceRadial = radial / Math.max(0.25, scale);
        double twist = Math.toRadians(recipe.extensions().twistDegrees() * eased);
        double sourceAngle = Math.atan2(binormal, normal) - twist;
        double relief = relief(sourceRadial, 1.0);
        double motif = fraction(sourceDistance / pitch + 0.5);
        double angle = fraction((sourceAngle - roll) / (Math.PI * 2.0));
        ScaleCoordinates scaleCoordinates = recipe.shape() == PathShape.SCALES
                ? scaleCoordinates(sourceDistance, sourceAngle, sourceRadial)
                : null;
        if (recipe.shape() == PathShape.SCALES) {
            return switch (metric) {
                case FIGURE -> scaleCoordinates == null
                        ? 0.025 + relief * 0.075
                        : scaleFigureValue(scaleCoordinates);
                case MOTIF -> scaleCoordinates == null
                        ? 0.05
                        : 0.05 + smooth(scaleCoordinates.progression()) * 0.90;
                case RELIEF -> scaleCoordinates == null
                        ? relief * 0.12
                        : 0.08 + scaleCoordinates.relief() * 0.92;
                case ALTERNATE -> scaleCoordinates == null
                        ? 0.035 + relief * 0.08
                        : scaleAlternateValue(scaleCoordinates);
                default -> axisValue;
            };
        }
        return switch (metric) {
            case FIGURE -> figureValue(recipe.shape(), motif, relief, angle, axisValue);
            case MOTIF -> smooth(motif);
            case RELIEF -> relief;
            case ALTERNATE -> alternate(sourceDistance, motif);
            default -> axisValue;
        };
    }

    private ScaleCoordinates scaleCoordinates(double distance, double angle, double radial) {
        double radius = recipe.radius();
        int columns = Math.clamp(recipe.spacing(), 4, 48);
        double angularPitch = Math.PI * 2.0 / columns;
        double angularWidth = angularPitch * 1.04;
        double tileWidth = radius * angularWidth;
        double tileLength = Math.clamp(tileWidth * 1.05, 5.0, radius * 1.45);
        double rowPitch = tileLength * 0.62;
        double inclinedRise = Math.tan(Math.toRadians(SCALE_INCLINATION_DEGREES)) * tileLength;
        double scaleRelief = Math.clamp(
                Math.max(recipe.thickness() + 2.5, Math.max(tileWidth * 0.30, inclinedRise)),
                3.5,
                Math.max(4.0, radius * 0.32)
        );
        double baseRadius = Math.max(1.0, radius - scaleRelief);
        double maximum = path.closed() ? path.length() - 1.0E-6 : path.length() + tileLength * 0.35;
        int maximumRow = Math.max(0, (int) Math.floor(maximum / rowPitch));
        int nearestRow = (int) Math.round(distance / rowPitch);
        ScaleCoordinates best = null;
        double bestScore = Double.MAX_VALUE;
        for (int offset = -2; offset <= 2; offset++) {
            int row = nearestRow + offset;
            if (row < 0 || row > maximumRow) {
                continue;
            }
            double centerDistance = row * rowPitch;
            double longitudinalDistance = distance - centerDistance;
            if (path.closed()) {
                longitudinalDistance = wrapDistance(longitudinalDistance, path.length());
            }
            double localLength = longitudinalDistance / (tileLength * 0.5);
            if (Math.abs(localLength) > 1.04) {
                continue;
            }
            double rowOffset = (row & 1) == 0 ? 0.0 : angularPitch * 0.5;
            double firstCenter = roll + rowOffset;
            int column = Math.floorMod((int) Math.round((angle - firstCenter) / angularPitch), columns);
            double centerAngle = firstCenter + column * angularPitch;
            double localWidth = wrapAngle(angle - centerAngle) / (angularWidth * 0.5);
            double halfWidth = scaleHalfWidth(localLength);
            if (Math.abs(localWidth) > halfWidth + 0.04) {
                continue;
            }
            double edge = Math.clamp(Math.abs(localWidth) / Math.max(0.05, halfWidth), 0.0, 1.0);
            double incline = scaleIncline(localLength);
            double lateralCrown = 0.84 + 0.16 * Math.sqrt(Math.max(0.0, 1.0 - edge * edge));
            double surfaceRelief = scaleRelief * incline * lateralCrown;
            double outerRadius = baseRadius + surfaceRelief;
            boolean sideWall = edge >= 0.90;
            boolean raisedLip = localLength >= 0.80;
            double fillDepth = Math.max(Math.max(1.25, recipe.thickness() + 0.35), surfaceRelief + 1.25);
            if (sideWall || raisedLip) {
                fillDepth += 0.65;
            }
            double depth = outerRadius - radial;
            if (depth < -1.35 || depth > fillDepth + 1.35) {
                continue;
            }
            double score = -outerRadius + Math.abs(depth) * 0.0005;
            if (score < bestScore) {
                bestScore = score;
                double sideDistance = Math.max(0.0, halfWidth - Math.abs(localWidth)) * tileWidth * 0.5;
                double frontDistance = Math.max(0.0, 1.0 - localLength) * tileLength * 0.5;
                best = new ScaleCoordinates(
                        Math.clamp((localLength + 1.0) * 0.5, 0.0, 1.0),
                        edge,
                        Math.clamp(surfaceRelief / Math.max(1.0, scaleRelief), 0.0, 1.0),
                        1.0 - Math.clamp(Math.max(0.0, depth) / Math.max(1.0, fillDepth), 0.0, 1.0),
                        sideDistance,
                        frontDistance,
                        row
                );
            }
        }
        return best;
    }

    private double scaleFigureValue(ScaleCoordinates coordinates) {
        double progression = smooth(coordinates.progression());
        double body = 0.025 + progression * 0.16 + coordinates.relief() * 0.025;
        double rimWidth = Math.clamp(recipe.thickness() + 1.25, 1.75, 4.25);
        double frontRim = 1.0 - smoothRange(
                coordinates.frontDistance(),
                rimWidth * 0.30,
                rimWidth * 1.05
        );
        double sideRim = (1.0 - smoothRange(
                coordinates.sideDistance(),
                rimWidth * 0.25,
                rimWidth
        )) * 0.90;
        double rim = Math.max(frontRim, sideRim) * (0.82 + coordinates.surface() * 0.18);
        double contour = Math.pow(Math.clamp(rim, 0.0, 1.0), 2.15);
        return Math.clamp(interpolate(body, 0.94, contour), 0.0, 1.0);
    }

    private double scaleAlternateValue(ScaleCoordinates coordinates) {
        double local = scaleFigureValue(coordinates);
        return (coordinates.row() & 1) == 0 ? local * 0.48 : 0.52 + local * 0.48;
    }

    private double relief(double radial, double scale) {
        double radius = Math.max(1.0, recipe.radius() * scale);
        if (recipe.shape() == PathShape.SCALES || recipe.shape() == PathShape.SPIKED_SCALES) {
            double inner = radius * 0.66;
            return Math.clamp((radial - inner) / Math.max(1.0, radius - inner), 0.0, 1.0);
        }
        return Math.clamp(radial / radius, 0.0, 1.0);
    }

    private double alternate(double distance, double motif) {
        long row = (long) Math.floor(distance / pitch);
        return (row & 1L) == 0L ? motif * 0.48 : 0.52 + motif * 0.48;
    }

    private static double figureValue(
            PathShape shape,
            double motif,
            double relief,
            double angle,
            double axis
    ) {
        double angularWave = 0.5 - Math.cos(angle * Math.PI * 2.0) * 0.5;
        double motifWave = 1.0 - Math.abs(motif * 2.0 - 1.0);
        return Math.clamp(switch (shape) {
            case SCALES, SPIKED_SCALES -> smooth(motif) * 0.72 + relief * 0.28;
            case TILES, HEX_ARMOR, VORONOI -> motifWave * 0.50 + angularWave * 0.30 + relief * 0.20;
            case CHAINLINK, RINGS, KNOTS -> angularWave * 0.52 + motifWave * 0.30 + relief * 0.18;
            case BEADS, CROWNS, RIB_LOOPS -> relief * 0.56 + angularWave * 0.29 + motifWave * 0.15;
            case COIL, OSCILLATE, NOODLES -> angularWave * 0.50 + smooth(motif) * 0.35 + relief * 0.15;
            case HONEYCOMB -> motifWave * 0.42 + angularWave * 0.42 + relief * 0.16;
            case MANDALA -> motifWave * 0.34 + angularWave * 0.38 + relief * 0.28;
            case CLOUDS -> relief * 0.48 + angularWave * 0.30 + smooth(motif) * 0.22;
            case PILLAR -> axis * 0.46 + relief * 0.34 + motifWave * 0.20;
            case PILLAR_ARCHES -> axis * 0.38 + relief * 0.42 + motifWave * 0.20;
            case TORII -> axis * 0.44 + angularWave * 0.32 + relief * 0.24;
            case ARCH_BRIDGE -> axis * 0.58 + relief * 0.42;
        }, 0.0, 1.0);
    }

    private static double motifPitch(ProceduralRecipe recipe) {
        double radius = recipe.radius();
        double spacing = spacingProgress(recipe.spacing());
        double gap = recipe.options().getOrDefault("gap", 0.0);
        return Math.max(1.0, switch (recipe.shape()) {
            case SCALES -> {
                int columns = Math.clamp(recipe.spacing(), 4, 48);
                double tileWidth = radius * (Math.PI * 2.0 / columns) * 1.04;
                double tileLength = Math.clamp(tileWidth * 1.05, 5.0, radius * 1.45);
                yield tileLength * 0.62;
            }
            case SPIKED_SCALES -> {
                int columns = surfaceColumns(radius, 6, 30);
                double tileWidth = radius * (Math.PI * 2.0 / columns) * 0.66;
                double tileLength = Math.clamp(tileWidth * 1.38, 6.0, radius * 1.35);
                yield tileLength * interpolate(0.58, 1.35, spacing);
            }
            case BEADS -> radius * interpolate(1.55, 4.60, spacing) + gap * radius;
            case COIL -> radius * interpolate(0.90, 3.40, spacing);
            case CROWNS -> radius * interpolate(0.80, 2.30, spacing);
            case HONEYCOMB -> radius * interpolate(0.45, 1.50, spacing);
            case OSCILLATE -> radius * interpolate(1.10, 4.50, spacing);
            case RIB_LOOPS -> radius * interpolate(0.55, 1.75, spacing);
            case CHAINLINK -> radius * interpolate(1.10, 3.20, spacing) + gap * radius;
            case RINGS -> radius * interpolate(0.32, 1.60, spacing)
                    + gap * Math.max(1.0, radius * 0.35);
            case TILES -> radius * interpolate(0.56, 1.48, spacing);
            case HEX_ARMOR -> radius * interpolate(0.55, 1.42, spacing);
            case VORONOI -> radius * interpolate(0.34, 1.18, spacing);
            case KNOTS -> radius * interpolate(0.95, 2.65, spacing);
            case NOODLES -> radius * interpolate(0.85, 2.20, spacing);
            case MANDALA -> radius * interpolate(0.16, 0.30, spacing);
            case CLOUDS -> radius * interpolate(0.42, 0.82, spacing);
            case PILLAR -> radius * interpolate(0.55, 1.20, spacing);
            case PILLAR_ARCHES -> radius * interpolate(0.65, 1.35, spacing);
            case TORII -> radius * interpolate(0.70, 1.45, spacing);
            case ARCH_BRIDGE -> Math.max(recipe.spacing(), radius * 1.55 * (1.0 + gap));
        });
    }

    private static int surfaceColumns(double radius, int minimum, int maximum) {
        return Math.clamp((int) Math.round(Math.PI * 2.0 * radius / 8.5), minimum, maximum);
    }

    private static double spacingProgress(int spacing) {
        return (spacing - 2.0) / 6.0;
    }

    private static double interpolate(double first, double second, double progress) {
        return first + (second - first) * progress;
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    private static double smooth(double value) {
        double clamped = Math.clamp(value, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double smoothRange(double value, double minimum, double maximum) {
        return smooth((value - minimum) / Math.max(1.0E-6, maximum - minimum));
    }

    private static double wrapAngle(double angle) {
        return angle - Math.floor((angle + Math.PI) / (Math.PI * 2.0)) * Math.PI * 2.0;
    }

    private static double wrapDistance(double distance, double length) {
        return distance - Math.floor((distance + length * 0.5) / length) * length;
    }

    private static double scaleHalfWidth(double longitudinal) {
        double progression = (longitudinal + 1.0) * 0.5;
        if (progression <= 0.18) {
            return 0.82 + progression;
        }
        if (progression <= 0.62) {
            return 1.0;
        }
        double taper = smooth((progression - 0.62) / 0.38);
        return 1.0 - taper * 0.72;
    }

    private static double scaleIncline(double longitudinal) {
        double progression = Math.clamp((longitudinal + 1.0) * 0.5, 0.0, 1.0);
        double rise = smooth(Math.clamp((progression - 0.04) / 0.78, 0.0, 1.0));
        double roundedLip = progression <= 0.82
                ? 1.0
                : 1.0 - smooth((progression - 0.82) / 0.18) * 0.10;
        return (0.08 + rise * 0.92) * roundedLip;
    }

    private record ScaleCoordinates(
            double progression,
            double edge,
            double relief,
            double surface,
            double sideDistance,
            double frontDistance,
            int row
    ) {
    }

    private static final class LongitudinalMapping {
        private static final int STEPS = 256;
        private final double[] cumulative = new double[STEPS + 1];

        private LongitudinalMapping(ProceduralRecipe recipe) {
            double total = 0.0;
            for (int step = 1; step <= STEPS; step++) {
                double progress = (step - 0.5) / STEPS;
                double eased = recipe.extensions().easing().apply(progress);
                double ratio = interpolate(
                        recipe.extensions().startSpacingPercent(),
                        recipe.extensions().endSpacingPercent(),
                        eased
                ) / 100.0;
                total += ratio;
                cumulative[step] = total;
            }
            for (int step = 1; step <= STEPS; step++) {
                cumulative[step] /= total;
            }
        }

        private double sourceProgress(double targetProgress) {
            double target = Math.clamp(targetProgress, 0.0, 1.0);
            int low = 0;
            int high = STEPS;
            while (low + 1 < high) {
                int middle = (low + high) >>> 1;
                if (cumulative[middle] <= target) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            double span = cumulative[high] - cumulative[low];
            double fraction = span <= 1.0E-9 ? 0.0 : (target - cumulative[low]) / span;
            return (low + fraction) / STEPS;
        }
    }

    private static final class PathLookup {
        private final SampledPath path;
        private final int cellSize;
        private final int maximumShell;
        private final List<SampledPath.Frame> samples;
        private final Long2ObjectOpenHashMap<IntArrayList> buckets = new Long2ObjectOpenHashMap<>();

        private PathLookup(SampledPath path, int radius, double maximumScale) {
            this.path = path;
            cellSize = Math.max(8, Math.min(64, radius * 2 + 8));
            maximumShell = Math.clamp((int) Math.ceil(radius * maximumScale / cellSize) + 1, 2, 8);
            int count = Math.max(2, (int) Math.ceil(path.length()) + 1);
            samples = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                SampledPath.Frame frame = path.at(path.length() * index / (count - 1.0));
                samples.add(frame);
                buckets.computeIfAbsent(cell(frame.position()), ignored -> new IntArrayList()).add(index);
            }
        }

        private SampledPath.Frame nearest(Vec3 point) {
            int cellX = coordinate(point.x());
            int cellY = coordinate(point.y());
            int cellZ = coordinate(point.z());
            int nearestIndex = -1;
            double nearestDistance = Double.MAX_VALUE;
            for (int shell = 0; shell <= maximumShell; shell++) {
                for (int x = -shell; x <= shell; x++) {
                    for (int y = -shell; y <= shell; y++) {
                        for (int z = -shell; z <= shell; z++) {
                            if (shell > 0 && Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) != shell) {
                                continue;
                            }
                            IntArrayList bucket = buckets.get(PackedBlockPlan.pack(cellX + x, cellY + y, cellZ + z));
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
                if (nearestIndex >= 0) {
                    break;
                }
            }
            return nearestIndex >= 0 ? samples.get(nearestIndex) : path.at(0.0);
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
