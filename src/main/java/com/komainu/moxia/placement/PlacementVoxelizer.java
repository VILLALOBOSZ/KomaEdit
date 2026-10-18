package com.komainu.moxia.placement;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.PieceSolver;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Pasa las colocaciones a bloques. Para cada bloque de la caja de una colocación se lleva su centro al espacio
 * local y se pregunta a la estructura qué hay ahí (muestreo inverso: ni con giros raros ni al ampliar quedan
 * agujeros).
 *
 * <ul>
 *     <li>FAST: una muestra por bloque, el vecino más cercano. Con giros de 90° y escala 1 es una copia exacta.</li>
 *     <li>SMOOTHED: 27 muestras por bloque; se coloca el bloque mayoritario si la fracción llena, multiplicada por el
 *     sesgo de relleno, llega a la mitad. Da bordes más limpios en giros libres.</li>
 * </ul>
 *
 * <p>Donde se solapan colocaciones gana la última, salvo que lo que llega sea aire de la estructura: el aire nunca
 * borra un bloque que otra colocación acaba de poner.</p>
 */
public final class PlacementVoxelizer {
    private static final double[] THIRDS = {-1.0 / 3.0, 0.0, 1.0 / 3.0};
    private static final double[][] CHECKER = {
            {0.0, 0.0, 0.0},
            {-0.125, -0.125, -0.125}, {0.125, 0.125, -0.125}, {0.125, -0.125, 0.125}, {-0.125, 0.125, 0.125}
    };
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private PlacementVoxelizer() {
    }

    /** Qué hacer con los bloques: aire, recorte y losas/escaleras. */
    public record Options(
            boolean placeAir,
            SmoothMode smoothMode,
            double coverage,
            Clip clip,
            int maxBlocks,
            long maxSamples
    ) {
        public static Options defaults() {
            return new Options(false, SmoothMode.NONE, PieceFit.DEFAULT_COVERAGE, null, 1_500_000, 400_000_000L);
        }

        public Options withPlaceAir(boolean value) {
            return new Options(value, smoothMode, coverage, clip, maxBlocks, maxSamples);
        }

        public Options withSmooth(SmoothMode mode, double value) {
            return new Options(placeAir, mode, value, clip, maxBlocks, maxSamples);
        }

        public Options withClip(Clip value) {
            return new Options(placeAir, smoothMode, coverage, value, maxBlocks, maxSamples);
        }
    }

    /** Solo se escriben los bloques que pasan este filtro ({@code -t}). */
    @FunctionalInterface
    public interface Clip {
        boolean allows(int x, int y, int z);
    }

    /**
     * @param cells  posición empaquetada → {@link #code(int, int)}
     * @param pieces losas y escaleras por posición; las añadidas fuera de la forma llevan la marca de añadida
     */
    public record Result(Long2LongOpenHashMap cells, Long2ByteOpenHashMap pieces) {
        public int size() {
            return cells.size();
        }

        public static int placement(long code) {
            return (int) (code >>> 32);
        }

        public static int palette(long code) {
            return (int) code;
        }
    }

    public static long code(int placement, int palette) {
        return ((long) placement << 32) | (palette & 0xFFFF_FFFFL);
    }

    public static Result voxelize(List<Placement> placements, Options options) {
        Long2LongOpenHashMap cells = new Long2LongOpenHashMap();
        Structure.Sampler[] samplers = new Structure.Sampler[placements.size()];
        long samples = 0;
        int checks = 0;
        for (int index = 0; index < placements.size(); index++) {
            Placement placement = placements.get(index);
            Structure.Sampler sampler = placement.structure().sampler(placement.seed());
            samplers[index] = sampler;
            int[] bounds = placement.worldBounds();
            long volume = (long) (bounds[3] - bounds[0] + 1) * (bounds[4] - bounds[1] + 1) * (bounds[5] - bounds[2] + 1);
            if (volume <= 0) {
                continue;
            }
            boolean smooth = placement.structure().smoothPaste();
            samples += volume * (smooth ? 27 : 1);
            if (samples > options.maxSamples()) {
                throw new IllegalArgumentException("Demasiado trabajo de una vez (" + samples
                        + " muestras); coloca menos estructuras o más pequeñas");
            }
            Mat3 inverse = Mat3.diagonal(1.0 / placement.scale().x(), 1.0 / placement.scale().y(),
                    1.0 / placement.scale().z()).multiply(placement.rotation().transpose());
            Vec3 stepX = inverse.column(0);
            Vec3 stepY = inverse.column(1);
            Vec3 stepZ = inverse.column(2);
            double fill = placement.structure().fillBias();
            for (int y = bounds[1]; y <= bounds[4]; y++) {
                if (y < -2048 || y > 2047) {
                    continue;
                }
                for (int z = bounds[2]; z <= bounds[5]; z++) {
                    Vec3 row = placement.toLocal(bounds[0] + 0.5, y + 0.5, z + 0.5);
                    double localX = row.x();
                    double localY = row.y();
                    double localZ = row.z();
                    for (int x = bounds[0]; x <= bounds[3]; x++, localX += stepX.x(), localY += stepX.y(),
                            localZ += stepX.z()) {
                        if ((++checks & 4_095) == 0) {
                            checkCancelled();
                        }
                        int value = smooth
                                ? smoothSample(sampler, localX, localY, localZ, stepX, stepY, stepZ, fill)
                                : sampler.sample(localX, localY, localZ);
                        if (value == Structure.EMPTY || value == Structure.AIR && !options.placeAir()) {
                            continue;
                        }
                        if (options.clip() != null && !options.clip().allows(x, y, z)) {
                            continue;
                        }
                        long key = PackedBlockPlan.pack(x, y, z);
                        if (value == Structure.AIR && cells.containsKey(key)
                                && Result.palette(cells.get(key)) != Structure.AIR) {
                            continue;
                        }
                        cells.put(key, code(index, value));
                        if (cells.size() > options.maxBlocks()) {
                            throw new GeometryLimitException(options.maxBlocks());
                        }
                    }
                }
            }
        }
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap();
        if (options.smoothMode() != SmoothMode.NONE && options.coverage() > 0.0) {
            pieces = smooth(placements, samplers, cells, options);
        }
        return new Result(cells, pieces);
    }

    /** 27 muestras repartidas por el bloque: el bloque mayoritario si hay bastante lleno. */
    private static int smoothSample(
            Structure.Sampler sampler,
            double x,
            double y,
            double z,
            Vec3 stepX,
            Vec3 stepY,
            Vec3 stepZ,
            double fillBias
    ) {
        int[] found = new int[27];
        int solid = 0;
        boolean air = false;
        for (double dy : THIRDS) {
            for (double dz : THIRDS) {
                for (double dx : THIRDS) {
                    int value = sampler.sample(
                            x + stepX.x() * dx + stepY.x() * dy + stepZ.x() * dz,
                            y + stepX.y() * dx + stepY.y() * dy + stepZ.y() * dz,
                            z + stepX.z() * dx + stepY.z() * dy + stepZ.z() * dz
                    );
                    if (value >= 0) {
                        found[solid++] = value;
                    } else if (value == Structure.AIR) {
                        air = true;
                    }
                }
            }
        }
        if (solid > 0 && solid * fillBias / 27.0 >= 0.5) {
            return majority(found, solid);
        }
        return air || solid > 0 ? Structure.AIR : Structure.EMPTY;
    }

    private static int majority(int[] values, int count) {
        java.util.Arrays.sort(values, 0, count);
        int best = values[0];
        int bestCount = 0;
        int run = 0;
        for (int index = 0; index < count; index++) {
            run = index > 0 && values[index] == values[index - 1] ? run + 1 : 1;
            if (run > bestCount) {
                bestCount = run;
                best = values[index];
            }
        }
        return best;
    }

    /**
     * Piezas del perfil {@code -w}: cada bloque del borde y cada hueco pegado a él se mide por octantes con la
     * estructura que lo puso y {@link PieceFit} elige la pieza; {@link PieceSolver} ajusta las esquinas de las
     * escaleras en los perfiles 3D y las deja como en Minecraft.
     */
    private static Long2ByteOpenHashMap smooth(
            List<Placement> placements,
            Structure.Sampler[] samplers,
            Long2LongOpenHashMap cells,
            Options options
    ) {
        PieceSolver solver = new PieceSolver(options.smoothMode(), options.coverage());
        double[] occupancy = new double[8];
        Long2LongOpenHashMap additions = new Long2LongOpenHashMap();
        int checks = 0;
        for (Long2LongMap.Entry entry : Long2LongMaps.fastIterable(cells)) {
            if ((++checks & 1_023) == 0) {
                checkCancelled();
            }
            long code = entry.getLongValue();
            if (Result.palette(code) < 0) {
                continue;
            }
            long key = entry.getLongKey();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            int owner = Result.placement(code);
            boolean surface = false;
            for (int[] face : FACES) {
                int nx = x + face[0];
                int ny = y + face[1];
                int nz = z + face[2];
                if (ny < -2048 || ny > 2047) {
                    continue;
                }
                long neighbor = PackedBlockPlan.pack(nx, ny, nz);
                if (solid(cells, neighbor)) {
                    continue;
                }
                surface = true;
                if (!additions.containsKey(neighbor)
                        && (options.clip() == null || options.clip().allows(nx, ny, nz))) {
                    measure(placements.get(owner), samplers[owner], nx, ny, nz, occupancy);
                    byte piece = solver.choose(occupancy, false);
                    if (piece != SmoothPiece.EMPTY) {
                        solver.put(neighbor, piece, occupancy);
                        additions.put(neighbor, code);
                    } else {
                        additions.put(neighbor, Long.MIN_VALUE);
                    }
                }
            }
            if (!surface) {
                continue;
            }
            measure(placements.get(owner), samplers[owner], x, y, z, occupancy);
            byte piece = solver.choose(occupancy, true);
            solver.put(key, piece, occupancy);
        }
        for (Long2LongMap.Entry entry : Long2LongMaps.fastIterable(additions)) {
            if (entry.getLongValue() != Long.MIN_VALUE) {
                cells.put(entry.getLongKey(), entry.getLongValue());
            }
        }
        if (cells.size() > options.maxBlocks()) {
            throw new GeometryLimitException(options.maxBlocks());
        }
        Long2ByteOpenHashMap pieces = solver.finish(key -> solid(cells, key));
        solver.dropped().forEach(cells::remove);
        return pieces;
    }

    private static boolean solid(Long2LongOpenHashMap cells, long key) {
        return cells.containsKey(key) && Result.palette(cells.get(key)) >= 0;
    }

    /** Fracción de cada octante del bloque dentro de la estructura, con cinco muestras por octante. */
    private static void measure(Placement placement, Structure.Sampler sampler, int x, int y, int z,
                                double[] occupancy) {
        for (int octantY = 0; octantY < 2; octantY++) {
            for (int octantZ = 0; octantZ < 2; octantZ++) {
                for (int octantX = 0; octantX < 2; octantX++) {
                    int inside = 0;
                    for (double[] offset : CHECKER) {
                        Vec3 local = placement.toLocal(
                                x + 0.25 + 0.5 * octantX + offset[0],
                                y + 0.25 + 0.5 * octantY + offset[1],
                                z + 0.25 + 0.5 * octantZ + offset[2]
                        );
                        if (sampler.sample(local.x(), local.y(), local.z()) >= 0) {
                            inside++;
                        }
                    }
                    occupancy[SmoothPiece.octant(octantX, octantY, octantZ)] = inside / (double) CHECKER.length;
                }
            }
        }
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }
}
