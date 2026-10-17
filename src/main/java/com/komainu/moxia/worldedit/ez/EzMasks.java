package com.komainu.moxia.worldedit.ez;

import com.komainu.moxia.gradient.BlockSpec;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.region.PaletteSwapOperation;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.AbstractMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.InfestedBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Las máscaras de KomaEdit para WorldEdit. Leen los bloques del {@link Extent} que WorldEdit les da (así ven los
 * cambios de la propia edición) y la luz del motor de luz de Minecraft, que no carga chunks y se puede consultar desde
 * el hilo en que WorldEdit evalúa la máscara.
 */
final class EzMasks {
    private EzMasks() {
    }

    /** Máscara que solo mira el estado del bloque; guarda la respuesta por estado. */
    static final class StateMask extends AbstractMask {
        private final Extent extent;
        private final Predicate<BlockState> predicate;
        private final Map<BlockState, Boolean> cache = new ConcurrentHashMap<>();

        StateMask(Extent extent, Predicate<BlockState> predicate) {
            this.extent = extent;
            this.predicate = predicate;
        }

        @Override
        public boolean test(BlockVector3 vector) {
            return cache.computeIfAbsent(extent.getBlock(vector), predicate::test);
        }
    }

    static boolean isAir(BlockState state) {
        return state.getBlockType().getMaterial().isAir();
    }

    static boolean fullBlock(BlockState state) {
        net.minecraft.block.BlockState minecraft = EzContext.minecraft(state);
        return Block.isShapeFullCube(minecraft.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));
    }

    static boolean infested(BlockState state) {
        return EzContext.minecraft(state).getBlock() instanceof InfestedBlock;
    }

    static int luminance(BlockState state) {
        return EzContext.minecraft(state).getLuminance();
    }

    /** {@code #palette}: el bloque está en la paleta; estricto mira además las propiedades escritas. */
    static Predicate<BlockState> palette(List<BlockSpec> entries, boolean strict) {
        return state -> {
            String id = state.getBlockType().id();
            Map<String, String> properties = null;
            for (BlockSpec entry : entries) {
                if (!entry.id().equals(id)) {
                    continue;
                }
                if (!strict || entry.properties().isEmpty()) {
                    return true;
                }
                if (properties == null) {
                    properties = PaletteSwapOperation.properties(state);
                }
                if (entry.matches(id, properties, true)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** {@code #blocklight} y {@code #truelight}. */
    static final class LightMask extends AbstractMask {
        private final World world;
        private final boolean includeSky;
        private final int minimum;
        private final int maximum;

        LightMask(World world, boolean includeSky, int minimum, int maximum) {
            this.world = world;
            this.includeSky = includeSky;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Override
        public boolean test(BlockVector3 vector) {
            BlockPos position = new BlockPos(vector.x(), vector.y(), vector.z());
            int light = includeSky
                    ? world.getLightingProvider().getLight(position, 0)
                    : world.getLightingProvider().get(LightType.BLOCK).getLightLevel(position);
            return light >= minimum && light <= maximum;
        }
    }

    /** {@code #attached}: el bloque toca a un vecino que no es aire por alguno de los lados pedidos. */
    static final class AttachedMask extends AbstractMask {
        private final Extent extent;
        private final List<Direction> directions;
        private final Map<BlockState, Integer> touching = new ConcurrentHashMap<>();

        AttachedMask(Extent extent, List<Direction> directions) {
            this.extent = extent;
            this.directions = directions;
        }

        @Override
        public boolean test(BlockVector3 vector) {
            BlockState state = extent.getBlock(vector);
            if (isAir(state)) {
                return false;
            }
            int faces = touching.computeIfAbsent(state, AttachedMask::touchingFaces);
            for (Direction direction : directions) {
                if ((faces & (1 << direction.ordinal())) == 0) {
                    continue;
                }
                BlockVector3 neighbor = vector.add(direction.getOffsetX(), direction.getOffsetY(),
                        direction.getOffsetZ());
                if (!isAir(extent.getBlock(neighbor))) {
                    return true;
                }
            }
            return false;
        }

        /** Caras por las que el bloque llega al borde de su hueco, más las que indican sus propiedades. */
        static int touchingFaces(BlockState state) {
            net.minecraft.block.BlockState minecraft = EzContext.minecraft(state);
            VoxelShape shape = minecraft.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            int faces = 0;
            if (!shape.isEmpty()) {
                double epsilon = 1.0E-4;
                for (Direction direction : Direction.values()) {
                    Direction.Axis axis = direction.getAxis();
                    boolean reaches = direction.getDirection() == Direction.AxisDirection.POSITIVE
                            ? shape.getMax(axis) >= 1.0 - epsilon
                            : shape.getMin(axis) <= epsilon;
                    if (reaches) {
                        faces |= 1 << direction.ordinal();
                    }
                }
            }
            return faces | propertyFaces(state);
        }

        private static int propertyFaces(BlockState state) {
            Map<String, String> properties = PaletteSwapOperation.properties(state);
            String id = state.getBlockType().id();
            int faces = 0;
            if ("true".equals(properties.get("hanging"))) {
                faces |= 1 << Direction.UP.ordinal();
            }
            String facing = properties.get("facing");
            String face = properties.get("face");
            if (face != null) {
                faces |= switch (face) {
                    case "floor" -> 1 << Direction.DOWN.ordinal();
                    case "ceiling" -> 1 << Direction.UP.ordinal();
                    default -> facing == null ? 0 : opposite(facing);
                };
            } else if (facing != null && (id.contains("wall_") || id.endsWith(":ladder")
                    || id.endsWith("_wall_sign") || id.endsWith("_wall_banner"))) {
                faces |= opposite(facing);
            }
            if (id.endsWith("vine") || id.endsWith("lichen") || id.endsWith("sculk_vein")) {
                for (Direction direction : Direction.values()) {
                    if ("true".equals(properties.get(direction.asString()))) {
                        faces |= 1 << direction.ordinal();
                    }
                }
            }
            return faces;
        }

        private static int opposite(String facing) {
            Direction direction = Direction.byName(facing.toLowerCase(Locale.ROOT));
            return direction == null ? 0 : 1 << direction.getOpposite().ordinal();
        }
    }

    /** {@code #near}: hay un bloque de la máscara interior a esa distancia, pero no el propio bloque. */
    static final class NearMask extends AbstractMask {
        private static final int CACHE_LIMIT = 1 << 20;
        private final Mask inner;
        private final double minimum;
        private final double maximum;
        private final Map<BlockVector3, Boolean> cache = new HashMap<>();

        NearMask(Mask inner, double minimum, double maximum) {
            this.inner = inner;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Override
        public synchronized boolean test(BlockVector3 vector) {
            if (innerTest(vector)) {
                return false;
            }
            int nearest = NearSearch.nearestSquared(vector, maximum, this::innerTest);
            return nearest >= 0 && nearest >= minimum * minimum - 1.0E-9;
        }

        private boolean innerTest(BlockVector3 vector) {
            if (cache.size() > CACHE_LIMIT) {
                cache.clear();
            }
            return cache.computeIfAbsent(vector, inner::test);
        }
    }

    /** Valor de 0 a 1 por bloque: el de un ruido o uno aleatorio fijo por posición. */
    interface Randomness {
        double value(int x, int y, int z);

        static Randomness of(NoiseSpec noise, double scale, long seed) {
            if (noise == null) {
                return (x, y, z) -> GradientMath.hash01(x, y, z, seed);
            }
            NoiseSpec.NoiseField field = noise.build(seed);
            double inverse = 1.0 / scale;
            return (x, y, z) -> Math.clamp(field.sample(x * inverse, y * inverse, z * inverse), 0.0, 1.0);
        }
    }

    /** {@code #eznoisemask}: pasa donde el ruido supera el umbral. */
    static final class NoiseMask extends AbstractMask {
        private final Randomness noise;
        private final double threshold;

        NoiseMask(Randomness noise, double threshold) {
            this.noise = noise;
            this.threshold = threshold;
        }

        @Override
        public boolean test(BlockVector3 vector) {
            return noise.value(vector.x(), vector.y(), vector.z()) > threshold;
        }
    }

    /** {@code #radialgradientmask} y {@code #vectorgradientmask}: cuanto más cerca, más probable que pase. */
    static final class GradientMask extends AbstractMask {
        private final Progress progress;
        private final Randomness randomness;

        GradientMask(Progress progress, Randomness randomness) {
            this.progress = progress;
            this.randomness = randomness;
        }

        @Override
        public boolean test(BlockVector3 vector) {
            return GradientMath.passes(progress.at(vector),
                    randomness.value(vector.x(), vector.y(), vector.z()));
        }
    }

    /** Avance de 0 a 1 de un degradado en cada bloque. */
    interface Progress {
        double at(BlockVector3 vector);

        static Progress radial(Vector3 origin, GradientMath.DistanceMode mode, double offset, double length) {
            return vector -> GradientMath.fraction(mode.distance(vector.x() + 0.5 - origin.x(),
                    vector.y() + 0.5 - origin.y(), vector.z() + 0.5 - origin.z()), offset, length);
        }

        static Progress along(Vector3 origin, Vector3 unit, double length) {
            return vector -> GradientMath.along(origin, unit, vector.x() + 0.5, vector.y() + 0.5, vector.z() + 0.5,
                    length);
        }
    }

    /** Estados (estricto) o tipos de bloque que hay en un portapapeles o un schematic. */
    static Predicate<BlockState> contained(Set<BlockState> states, boolean strict) {
        if (strict) {
            return states::contains;
        }
        Set<String> types = new java.util.HashSet<>();
        for (BlockState state : states) {
            types.add(state.getBlockType().id());
        }
        return state -> types.contains(state.getBlockType().id());
    }
}
