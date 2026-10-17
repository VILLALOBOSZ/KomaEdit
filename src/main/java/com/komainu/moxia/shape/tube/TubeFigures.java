package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.SmoothPieces;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Punto de entrada de las figuras de espacio de tubo ({@code sweep}, {@code braid} y {@code noisetube}).
 *
 * <p>Mismo contrato que el resto de {@code ShapeGenerator}: recibe la trayectoria y los parámetros y devuelve
 * bloques. La geometría se calcula entera fuera del hilo del servidor y respeta la cancelación cooperativa.</p>
 */
public final class TubeFigures {
    private TubeFigures() {
    }

    public static boolean supports(PathShape shape) {
        return TubeShapes.supports(shape);
    }

    public static Set<BlockVector3> generate(SampledPath path, ShapeParameters parameters, int limit) {
        return generate(path, parameters, TubeCurve.NONE, limit);
    }

    public static Set<BlockVector3> generate(
            SampledPath path,
            ShapeParameters parameters,
            TubeCurve curve,
            int limit
    ) {
        TubeSettings settings = TubeSettings.from(parameters);
        TubeCenterline line = TubeCenterline.create(path, settings, curve);
        TubeShapes.TubeShape shape = TubeShapes.create(settings, pathSeed(path));
        LongOpenHashSet blocks = TubeVoxelizer.voxelize(line, settings, shape, limit);
        if (settings.shape() == PathShape.NOISE_TUBE) {
            // El ruido deja islas sueltas en la corteza; solo se conserva la pieza unida al núcleo.
            blocks = largestComponent(blocks);
        }
        return toBlocks(blocks);
    }

    /** Si la figura pide losas o escaleras en la superficie ({@code smooth} distinto de cero y cobertura > 0). */
    public static boolean usesPieces(ShapeParameters parameters) {
        return supports(parameters.shape()) && TubeSmoother.enabled(TubeSettings.from(parameters));
    }

    /**
     * Sustituye por losas o escaleras los bloques de superficie que la figura solo ocupa en parte y añade piezas
     * en los huecos que casi llena. Recibe la geometría final (después de reparar huecos o fusionar acentos) y
     * devuelve la misma con las posiciones añadidas; sin piezas activas la devuelve tal cual.
     */
    public static Smoothed smooth(
            SampledPath path,
            ShapeParameters parameters,
            TubeCurve curve,
            Set<BlockVector3> blocks,
            int limit
    ) {
        TubeSettings settings = TubeSettings.from(parameters);
        if (!supports(parameters.shape()) || !TubeSmoother.enabled(settings) || blocks.isEmpty()) {
            return new Smoothed(blocks, SmoothPieces.NONE);
        }
        TubeCenterline line = TubeCenterline.create(path, settings, curve);
        TubeShapes.TubeShape shape = TubeShapes.create(settings, pathSeed(path));
        LongOpenHashSet packed = new LongOpenHashSet(blocks.size());
        for (BlockVector3 block : blocks) {
            packed.add(PackedBlockPlan.pack(block.x(), block.y(), block.z()));
        }
        int before = packed.size();
        SmoothPieces pieces = TubeSmoother.smooth(new TubeVoxelizer.Evaluator(line, settings, shape), settings,
                packed, limit);
        return new Smoothed(packed.size() == before ? blocks : toBlocks(packed), pieces);
    }

    public record Smoothed(Set<BlockVector3> blocks, SmoothPieces pieces) {
    }

    /**
     * Sonda de material para el modo "Patrón figura": devuelve el valor implícito de la figura en cada bloque
     * (núcleo → borde en perfiles, una franja de paleta por hebra en cuerdas y trenzas).
     */
    public static MaterialProbe materialProbe(SampledPath path, ShapeParameters parameters, TubeCurve curve) {
        TubeSettings settings = TubeSettings.from(parameters);
        TubeCenterline line = TubeCenterline.create(path, settings, curve);
        TubeShapes.TubeShape shape = TubeShapes.create(settings, pathSeed(path));
        return new MaterialProbe(new TubeVoxelizer.Evaluator(line, settings, shape));
    }

    public static final class MaterialProbe {
        private static final double[][] NUDGES = {
                {0.3, 0, 0}, {-0.3, 0, 0}, {0, 0.3, 0}, {0, -0.3, 0}, {0, 0, 0.3}, {0, 0, -0.3}
        };
        private final TubeVoxelizer.Evaluator evaluator;

        private MaterialProbe(TubeVoxelizer.Evaluator evaluator) {
            this.evaluator = evaluator;
        }

        /** Material en [0, 1]; si el centro del bloque cae fuera (supermuestreo) prueba muestras cercanas. */
        public synchronized double value(BlockVector3 block, double fallback) {
            double x = block.x() + 0.5;
            double y = block.y() + 0.5;
            double z = block.z() + 0.5;
            double value = evaluator.sample(x, y, z, true);
            if (value >= 0.0) {
                return Math.clamp(value, 0.0, 1.0);
            }
            for (double[] nudge : NUDGES) {
                value = evaluator.sample(x + nudge[0], y + nudge[1], z + nudge[2], true);
                if (value >= 0.0) {
                    return Math.clamp(value, 0.0, 1.0);
                }
            }
            return fallback;
        }
    }

    /** Semilla estable derivada del recorrido: mismo recorrido, mismo holograma y mismo Build. */
    static long pathSeed(SampledPath path) {
        long seed = 0x9E3779B97F4A7C15L;
        for (Vec3 point : path.controlPoints()) {
            seed = mix(seed ^ (long) Math.floor(point.x()));
            seed = mix(seed ^ (long) Math.floor(point.y()) * 31L);
            seed = mix(seed ^ (long) Math.floor(point.z()) * 131L);
        }
        return seed == 0L ? 1L : seed;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }

    static LongOpenHashSet largestComponent(LongOpenHashSet blocks) {
        LongOpenHashSet visited = new LongOpenHashSet(blocks.size());
        LongOpenHashSet best = new LongOpenHashSet();
        LongArrayList queue = new LongArrayList();
        int processed = 0;
        LongIterator seeds = blocks.iterator();
        while (seeds.hasNext()) {
            long seed = seeds.nextLong();
            if (!visited.add(seed)) {
                continue;
            }
            LongOpenHashSet component = new LongOpenHashSet();
            queue.clear();
            queue.add(seed);
            component.add(seed);
            for (int head = 0; head < queue.size(); head++) {
                if ((processed++ & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
                    throw new CancellationException();
                }
                long current = queue.getLong(head);
                int x = PackedBlockPlan.unpackX(current);
                int y = PackedBlockPlan.unpackY(current);
                int z = PackedBlockPlan.unpackZ(current);
                for (int axis = 0; axis < 6; axis++) {
                    int sign = (axis & 1) == 0 ? 1 : -1;
                    int nextX = axis >> 1 == 0 ? x + sign : x;
                    int nextY = axis >> 1 == 1 ? y + sign : y;
                    int nextZ = axis >> 1 == 2 ? z + sign : z;
                    if (nextY < -2048 || nextY > 2047) {
                        continue;
                    }
                    long neighbor = PackedBlockPlan.pack(nextX, nextY, nextZ);
                    if (blocks.contains(neighbor) && visited.add(neighbor)) {
                        component.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
            if (component.size() > best.size()) {
                best = component;
            }
        }
        return best;
    }

    private static Set<BlockVector3> toBlocks(LongOpenHashSet packed) {
        Set<BlockVector3> resolved = new HashSet<>(Math.max(16, (int) (packed.size() / 0.75f) + 1));
        packed.forEach((long value) -> resolved.add(BlockVector3.at(
                PackedBlockPlan.unpackX(value),
                PackedBlockPlan.unpackY(value),
                PackedBlockPlan.unpackZ(value)
        )));
        return Collections.unmodifiableSet(resolved);
    }
}
