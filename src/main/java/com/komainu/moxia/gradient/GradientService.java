package com.komainu.moxia.gradient;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.recipe.MaterialMetric;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.List;

public final class GradientService {
    private GradientService() {
    }

    public static int apply(
            ServerPlayerEntity player,
            GradientAxis axis,
            String paletteInput,
            int ditherPercent,
            boolean reverse
    ) throws IncompleteRegionException, InputParseException {
        if (ditherPercent < 0 || ditherPercent > 100) {
            throw new IllegalArgumentException("La mezcla debe estar entre 0 y 100");
        }

        List<String> entries = paletteEntries(paletteInput);

        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Region selection = context.session().getSelection(context.world()).clone();
        List<Pattern> patterns = entries.stream()
                .map(entry -> WorldEditService.parsePatternUnchecked(context, entry))
                .toList();
        BlockVector3 minimum = selection.getMinimumPoint();
        BlockVector3 maximum = selection.getMaximumPoint();
        double dither = ditherPercent / 100.0;

        return BatchEditService.submitRegion(
                player,
                selection,
                block -> {
                    var existing = player.getServerWorld().getBlockState(new BlockPos(
                            block.x(), block.y(), block.z()
                    ));
                    if (existing.isAir() || !existing.getFluidState().isEmpty()) {
                        return null;
                    }
                    double normalized = axis.normalized(block, minimum, maximum);
                    double organicWarp = (organicNoise(block) - 0.5) * 0.15 * dither;
                    normalized = Math.clamp(normalized + organicWarp, 0.0, 1.0);
                    if (reverse) {
                        normalized = 1.0 - normalized;
                    }
                    int paletteIndex = paletteIndex(block, normalized, patterns.size(), dither);
                    return patterns.get(paletteIndex);
                },
                "Degradado profesional " + axis.id()
        );
    }

    public static BatchEditService.PositionPattern compile(
            ServerPlayerEntity player,
            GradientAxis axis,
            String paletteInput,
            int ditherPercent,
            boolean reverse,
            BlockBounds bounds
    ) {
        return compile(player, axis, paletteInput, ditherPercent, reverse, bounds,
                MaterialMetric.AXIS, null, null, null);
    }

    public static BatchEditService.PositionPattern compile(
            ServerPlayerEntity player,
            GradientAxis axis,
            String paletteInput,
            int ditherPercent,
            boolean reverse,
            BlockBounds bounds,
            MaterialMetric metric,
            PackedBlockPlan geometry
    ) {
        return compile(player, axis, paletteInput, ditherPercent, reverse, bounds,
                metric, geometry, null, null);
    }

    public static BatchEditService.PositionPattern compile(
            ServerPlayerEntity player,
            GradientAxis axis,
            String paletteInput,
            int ditherPercent,
            boolean reverse,
            BlockBounds bounds,
            MaterialMetric metric,
            PackedBlockPlan geometry,
            ProceduralRecipe recipe,
            PathSnapshot path
    ) {
        if (ditherPercent < 0 || ditherPercent > 100) {
            throw new IllegalArgumentException("La mezcla debe estar entre 0 y 100");
        }
        List<String> entries = paletteEntries(paletteInput);
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        List<Pattern> patterns = entries.stream()
                .map(entry -> WorldEditService.parsePatternUnchecked(context, entry))
                .toList();
        BlockVector3 minimum = BlockVector3.at(bounds.minimumX(), bounds.minimumY(), bounds.minimumZ());
        BlockVector3 maximum = BlockVector3.at(bounds.maximumX(), bounds.maximumY(), bounds.maximumZ());
        double dither = ditherPercent / 100.0;
        GeometryField field = geometry == null || !metric.usesGeometryContext()
                ? null
                : new GeometryField(geometry);
        FigureMaterialField figureField = recipe == null || path == null || !metric.usesFigureContext()
                ? null
                : new FigureMaterialField(recipe, path);
        return block -> {
            double axisValue = axis.normalized(block, minimum, maximum);
            double normalized = metricValue(metric, field, figureField, block, axisValue);
            double organicWarp = (organicNoise(block) - 0.5) * 0.15 * dither;
            normalized = Math.clamp(normalized + organicWarp, 0.0, 1.0);
            if (reverse) {
                normalized = 1.0 - normalized;
            }
            int paletteIndex = paletteIndex(block, normalized, patterns.size(), dither);
            return patterns.get(paletteIndex);
        };
    }

    /**
     * Lee la paleta con la notación de ezEdits ({@code ##Magma}, {@code -##Grayscale(1:8)}, {@code oro*3,diamante})
     * o como la lista {@code a;b;c} de siempre.
     */
    static List<String> paletteEntries(String paletteInput) {
        List<String> entries = PaletteSpec.parse(paletteInput);
        if (entries.size() < 2) {
            throw new IllegalArgumentException("La paleta necesita al menos dos bloques, por ejemplo stone;andesite o ##Grayscale");
        }
        return entries;
    }

    private static double metricValue(
            MaterialMetric metric,
            GeometryField field,
            FigureMaterialField figureField,
            BlockVector3 block,
            double axisValue
    ) {
        if (metric == MaterialMetric.AXIS) {
            return axisValue;
        }
        if (figureField != null && metric.usesFigureContext()) {
            return figureField.value(metric, block, axisValue);
        }
        if (field == null) {
            return axisValue;
        }
        int missing = field.missingNeighbors(block);
        return switch (metric) {
            case AXIS -> axisValue;
            case EXPOSURE -> missing / 6.0;
            case CAVITY -> 1.0 - missing / 6.0;
            case EDGES -> 1.0 - Math.abs(missing - 3.0) / 3.0;
            case CURVATURE -> field.directionalVariation(block);
            case ORGANIC -> Math.clamp(axisValue * 0.62 + organicNoise(block) * 0.38, 0.0, 1.0);
            case FIGURE, MOTIF, RELIEF, ALTERNATE -> axisValue;
        };
    }

    private static int paletteIndex(BlockVector3 block, double normalized, int paletteSize, double dither) {
        double scaled = Math.clamp(normalized, 0.0, 1.0) * (paletteSize - 1);
        int lower = Math.min(paletteSize - 1, (int) Math.floor(scaled));
        int upper = Math.min(paletteSize - 1, lower + 1);
        if (lower == upper) {
            return lower;
        }
        double fraction = scaled - lower;
        double threshold = 0.5 + (blueNoise(block) - 0.5) * dither;
        return fraction >= threshold ? upper : lower;
    }

    private static double organicNoise(BlockVector3 block) {
        double first = interpolatedNoise(block.x(), block.y(), block.z(), 11, 0x51A7L);
        double second = interpolatedNoise(block.x(), block.y(), block.z(), 29, 0xC0FFEE12L);
        return first * 0.68 + second * 0.32;
    }

    private static double interpolatedNoise(int x, int y, int z, int scale, long salt) {
        int cellX = Math.floorDiv(x, scale);
        int cellY = Math.floorDiv(y, scale);
        int cellZ = Math.floorDiv(z, scale);
        double localX = smooth(Math.floorMod(x, scale) / (double) scale);
        double localZ = smooth(Math.floorMod(z, scale) / (double) scale);
        double first = lerp(
                lattice(cellX, cellY, cellZ, salt),
                lattice(cellX + 1, cellY, cellZ, salt),
                localX
        );
        double second = lerp(
                lattice(cellX, cellY, cellZ + 1, salt),
                lattice(cellX + 1, cellY, cellZ + 1, salt),
                localX
        );
        return lerp(first, second, localZ);
    }

    private static double blueNoise(BlockVector3 block) {
        return unit(mix(
                block.x() * 0x9E3779B97F4A7C15L
                        ^ block.y() * 0xC2B2AE3D27D4EB4FL
                        ^ block.z() * 0x165667B19E3779F9L
        ));
    }

    private static double lattice(int x, int y, int z, long salt) {
        return unit(mix(
                x * 0x9E3779B97F4A7C15L
                        ^ y * 0xC2B2AE3D27D4EB4FL
                        ^ z * 0x165667B19E3779F9L
                        ^ salt
        ));
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double first, double second, double progress) {
        return first + (second - first) * progress;
    }

    private static final class GeometryField {
        private final LongOpenHashSet positions;

        private GeometryField(PackedBlockPlan geometry) {
            positions = new LongOpenHashSet(geometry.size());
            for (int index = 0; index < geometry.size(); index++) {
                positions.add(geometry.packedPosition(index));
            }
        }

        private int missingNeighbors(BlockVector3 block) {
            int present = 0;
            present += contains(block.x() + 1, block.y(), block.z()) ? 1 : 0;
            present += contains(block.x() - 1, block.y(), block.z()) ? 1 : 0;
            present += contains(block.x(), block.y() + 1, block.z()) ? 1 : 0;
            present += contains(block.x(), block.y() - 1, block.z()) ? 1 : 0;
            present += contains(block.x(), block.y(), block.z() + 1) ? 1 : 0;
            present += contains(block.x(), block.y(), block.z() - 1) ? 1 : 0;
            return 6 - present;
        }

        private double directionalVariation(BlockVector3 block) {
            int horizontal = 0;
            horizontal += contains(block.x() + 1, block.y(), block.z()) ? 1 : 0;
            horizontal += contains(block.x() - 1, block.y(), block.z()) ? 1 : 0;
            horizontal += contains(block.x(), block.y(), block.z() + 1) ? 1 : 0;
            horizontal += contains(block.x(), block.y(), block.z() - 1) ? 1 : 0;
            int vertical = 0;
            vertical += contains(block.x(), block.y() + 1, block.z()) ? 1 : 0;
            vertical += contains(block.x(), block.y() - 1, block.z()) ? 1 : 0;
            return Math.clamp(Math.abs(horizontal / 4.0 - vertical / 2.0), 0.0, 1.0);
        }

        private boolean contains(int x, int y, int z) {
            if (y < -2048 || y > 2047) {
                return false;
            }
            return positions.contains(PackedBlockPlan.pack(x, y, z));
        }
    }
}
