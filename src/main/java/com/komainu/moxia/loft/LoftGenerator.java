package com.komainu.moxia.loft;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.PieceSolver;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Superficie tendida entre curvas ({@code /komaedit loft}). Es Java puro y se calcula en un worker.
 *
 * <ol>
 *     <li>Cada curva se muestrea cada medio bloque (Catmull–Rom por sus puntos, o tramos rectos en el modo lineal)
 *     y se orienta como la anterior, para que la lona no salga retorcida si una curva se marcó al revés.</li>
 *     <li>Entre curvas, cada muestra se une con la de la curva siguiente: en recta, con un Catmull–Rom centrípeto
 *     que pasa por todas, o colgando como una catenaria.</li>
 *     <li>La malla resultante se voxeliza al revés: entra cada bloque cuyo centro está a menos de medio grosor
 *     (y como mínimo medio bloque) de algún triángulo. Con medio bloque basta para que sea estanca: dos bloques con
 *     una cara en común a uno y otro lado de la superficie están a 1 bloque, así que uno de los dos queda a medio
 *     bloque o menos y entra; ningún camino de caras atraviesa la lona.</li>
 *     <li>Con {@code -w}, las mismas distancias miden la ocupación por octantes y {@link PieceSolver} elige losas y
 *     escaleras, como en los tubos.</li>
 * </ol>
 */
public final class LoftGenerator {
    public static final int MAX_BLOCKS = 1_500_000;
    static final double STEP = 0.5;
    private static final int MAX_SAMPLES = 4_097;
    private static final int MAX_ROWS = 8_192;
    /** Desplazamiento minúsculo de la malla: evita empates exactos a medio bloque (y capas dobles en planos). */
    private static final Vec3 NUDGE = new Vec3(1.7E-4, 3.1E-4, 2.3E-4);
    /** Centro y cuatro puntos en damero de cada octante, como en el suavizado de los tubos. */
    private static final double[][] SAMPLES;

    static {
        double[][] checker = {{0, 0, 0}, {-0.125, -0.125, -0.125}, {0.125, 0.125, -0.125}, {0.125, -0.125, 0.125},
                {-0.125, 0.125, 0.125}};
        SAMPLES = new double[40][];
        for (int octant = 0; octant < 8; octant++) {
            double cx = 0.25 + 0.5 * (octant & 1);
            double cy = 0.25 + 0.5 * (octant >> 1 & 1);
            double cz = 0.25 + 0.5 * (octant >> 2 & 1);
            for (int sample = 0; sample < 5; sample++) {
                SAMPLES[octant * 5 + sample] = new double[]{cx + checker[sample][0], cy + checker[sample][1],
                        cz + checker[sample][2]};
            }
        }
    }

    private LoftGenerator() {
    }

    /**
     * @param curves puntos de control de cada curva (de 2 a 6 curvas, de 2 puntos o más cada una)
     */
    public static LoftResult generate(List<List<BlockVector3>> curves, LoftSettings settings) {
        Vec3[][] grid = surface(curves, settings);
        return voxelize(grid, settings);
    }

    /** Reparte una lista de puntos en {@code count} curvas seguidas del mismo número de puntos. */
    public static List<List<BlockVector3>> split(List<BlockVector3> points, int count) {
        if (points.size() < 2 * count) {
            throw new IllegalArgumentException("Hacen falta al menos " + 2 * count + " puntos para " + count
                    + " curvas (hay " + points.size() + "): marca dos o más por curva o guarda curvas con nombre");
        }
        if (points.size() % count != 0) {
            throw new IllegalArgumentException("Los " + points.size() + " puntos no se reparten en " + count
                    + " curvas iguales: marca el mismo número de puntos en cada una");
        }
        int each = points.size() / count;
        List<List<BlockVector3>> result = new ArrayList<>(count);
        for (int curve = 0; curve < count; curve++) {
            result.add(List.copyOf(points.subList(curve * each, (curve + 1) * each)));
        }
        return result;
    }

    // ---------------------------------------------------------------- superficie

    /** Malla de puntos {@code [muestra a lo largo][fila entre curvas]}. */
    static Vec3[][] surface(List<List<BlockVector3>> controls, LoftSettings settings) {
        if (controls.size() < 2 || controls.size() > LoftSettings.MAX_CURVES) {
            throw new IllegalArgumentException("El loft se tiende entre 2 y " + LoftSettings.MAX_CURVES + " curvas");
        }
        boolean linear = settings.interpolation() == LoftSettings.Interpolation.LINEAR;
        List<List<BlockVector3>> aligned = new ArrayList<>();
        for (List<BlockVector3> curve : controls) {
            if (curve.size() < 2) {
                throw new IllegalArgumentException("Cada curva necesita al menos dos puntos");
            }
            List<BlockVector3> copy = new ArrayList<>(curve);
            if (!aligned.isEmpty() && reversed(aligned.getLast(), copy)) {
                Collections.reverse(copy);
            }
            aligned.add(copy);
        }
        List<SampledPath> paths = new ArrayList<>();
        double longest = 0.0;
        for (List<BlockVector3> curve : aligned) {
            SampledPath path = linear ? SampledPath.create(curve, false) : SampledPath.createSmooth(curve, false);
            paths.add(path);
            longest = Math.max(longest, path.length());
        }
        int samples = (int) Math.clamp(Math.ceil(longest / STEP) + 1, 2, MAX_SAMPLES);
        int count = paths.size();
        Vec3[][] along = new Vec3[samples][count];
        for (int sample = 0; sample < samples; sample++) {
            double fraction = (double) sample / (samples - 1);
            for (int curve = 0; curve < count; curve++) {
                SampledPath path = paths.get(curve);
                along[sample][curve] = path.at(fraction * path.length()).position();
            }
        }
        boolean closed = settings.closed() && count >= 3;
        int spans = closed ? count : count - 1;
        int[] rows = new int[spans];
        int total = 0;
        for (int span = 0; span < spans; span++) {
            double widest = 0.0;
            for (Vec3[] row : along) {
                double width = row[span].distance(row[(span + 1) % count]);
                if (settings.interpolation() == LoftSettings.Interpolation.CATENARY) {
                    width += 2.0 * settings.sagFor(width);
                } else if (settings.interpolation() == LoftSettings.Interpolation.CATMULL_ROM) {
                    width *= 1.25;
                }
                widest = Math.max(widest, width);
            }
            rows[span] = (int) Math.clamp(Math.ceil(widest / STEP), 1, MAX_ROWS);
            total += rows[span];
        }
        if (total > MAX_ROWS) {
            throw new IllegalArgumentException("Las curvas están demasiado separadas para un loft");
        }
        int rowCount = total + 1;
        Vec3[][] grid = new Vec3[samples][rowCount];
        for (int sample = 0; sample < samples; sample++) {
            Vec3[] row = along[sample];
            int index = 0;
            for (int span = 0; span < spans; span++) {
                for (int step = 0; step < rows[span]; step++) {
                    grid[sample][index++] = cross(row, span, (double) step / rows[span], settings, closed).add(NUDGE);
                }
            }
            grid[sample][index] = (closed ? row[0] : row[count - 1]).add(NUDGE);
        }
        return grid;
    }

    /** Si la curva {@code next} va al revés que {@code previous} (sus extremos casan mejor cruzados). */
    private static boolean reversed(List<BlockVector3> previous, List<BlockVector3> next) {
        BlockVector3 a0 = previous.getFirst();
        BlockVector3 a1 = previous.getLast();
        BlockVector3 b0 = next.getFirst();
        BlockVector3 b1 = next.getLast();
        double same = Math.sqrt(a0.distanceSq(b0)) + Math.sqrt(a1.distanceSq(b1));
        double crossed = Math.sqrt(a0.distanceSq(b1)) + Math.sqrt(a1.distanceSq(b0));
        return crossed < same - 1.0E-6;
    }

    /** Punto entre la curva {@code span} y la siguiente, a la fracción {@code t}. */
    static Vec3 cross(Vec3[] points, int span, double t, LoftSettings settings, boolean closed) {
        int count = points.length;
        Vec3 from = points[span];
        Vec3 to = points[(span + 1) % count];
        return switch (settings.interpolation()) {
            case LINEAR -> from.lerp(to, t);
            case CATENARY -> {
                double chord = from.distance(to);
                double sag = settings.sagFor(chord);
                Vec3 straight = from.lerp(to, t);
                yield chord < 1.0E-9 || sag <= 0.0 ? straight
                        : straight.add(new Vec3(0.0, -sag * hang(sag / chord, t), 0.0));
            }
            case CATMULL_ROM -> {
                Vec3 before = span > 0 ? points[span - 1]
                        : closed ? points[count - 1] : from.multiply(2.0).subtract(to);
                Vec3 after = span + 2 < count ? points[span + 2]
                        : closed ? points[(span + 2) % count] : to.multiply(2.0).subtract(from);
                yield centripetal(before, from, to, after, t);
            }
        };
    }

    /**
     * Forma normalizada de una catenaria entre dos apoyos a la misma altura: 0 en los extremos, 1 en el centro.
     * El parámetro sale de la comba relativa ({@code (cosh a − 1) / 2a = comba / vano}); con combas pequeñas tiende
     * a la parábola.
     */
    static double hang(double ratio, double t) {
        double a = catenaryParameter(ratio);
        if (a < 1.0E-4) {
            return 4.0 * t * (1.0 - t);
        }
        return (Math.cosh(a) - Math.cosh(a * (2.0 * t - 1.0))) / (Math.cosh(a) - 1.0);
    }

    static double catenaryParameter(double ratio) {
        double target = Math.clamp(ratio, 0.0, 5.0);
        double low = 0.0;
        double high = 30.0;
        for (int iteration = 0; iteration < 80; iteration++) {
            double middle = (low + high) * 0.5;
            double value = middle < 1.0E-9 ? 0.0 : (Math.cosh(middle) - 1.0) / (2.0 * middle);
            if (value < target) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) * 0.5;
    }

    /** Catmull–Rom centrípeto (Barry–Goldman) entre {@code p1} y {@code p2}. */
    private static Vec3 centripetal(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t0 = 0.0;
        double t1 = t0 + knot(p0, p1);
        double t2 = t1 + knot(p1, p2);
        double t3 = t2 + knot(p2, p3);
        double u = t1 + (t2 - t1) * t;
        Vec3 a1 = blend(p0, p1, t0, t1, u);
        Vec3 a2 = blend(p1, p2, t1, t2, u);
        Vec3 a3 = blend(p2, p3, t2, t3, u);
        Vec3 b1 = blend(a1, a2, t0, t2, u);
        Vec3 b2 = blend(a2, a3, t1, t3, u);
        return blend(b1, b2, t1, t2, u);
    }

    private static double knot(Vec3 a, Vec3 b) {
        return Math.max(Math.sqrt(a.distance(b)), 1.0E-4);
    }

    private static Vec3 blend(Vec3 a, Vec3 b, double ta, double tb, double u) {
        double span = tb - ta;
        return span < 1.0E-12 ? a : a.multiply((tb - u) / span).add(b.multiply((u - ta) / span));
    }

    // ---------------------------------------------------------------- voxelizado

    private static LoftResult voxelize(Vec3[][] grid, LoftSettings settings) {
        double reach = Math.max(0.5, settings.thickness() / 2.0);
        double reachSquared = reach * reach;
        boolean smooth = settings.smooth().active();
        int samples = grid.length;
        int rows = grid[0].length;
        Long2FloatOpenHashMap distance = new Long2FloatOpenHashMap();
        distance.defaultReturnValue(Float.POSITIVE_INFINITY);
        Long2FloatOpenHashMap fractions = new Long2FloatOpenHashMap();
        Long2LongOpenHashMap occupancy = new Long2LongOpenHashMap();
        int margin = smooth ? 1 : 0;
        int checked = 0;
        double[] triangle = new double[9];
        for (int sample = 0; sample + 1 < samples; sample++) {
            for (int row = 0; row + 1 < rows; row++) {
                float across = (float) ((row + 0.5) / (rows - 1));
                for (int half = 0; half < 2; half++) {
                    Vec3 a = grid[sample][row];
                    Vec3 b = half == 0 ? grid[sample + 1][row] : grid[sample + 1][row + 1];
                    Vec3 c = half == 0 ? grid[sample + 1][row + 1] : grid[sample][row + 1];
                    set(triangle, a, b, c);
                    int minX = (int) Math.ceil(Math.min(a.x(), Math.min(b.x(), c.x())) - reach - 0.5) - margin;
                    int maxX = (int) Math.floor(Math.max(a.x(), Math.max(b.x(), c.x())) + reach - 0.5) + margin;
                    int minY = (int) Math.ceil(Math.min(a.y(), Math.min(b.y(), c.y())) - reach - 0.5) - margin;
                    int maxY = (int) Math.floor(Math.max(a.y(), Math.max(b.y(), c.y())) + reach - 0.5) + margin;
                    int minZ = (int) Math.ceil(Math.min(a.z(), Math.min(b.z(), c.z())) - reach - 0.5) - margin;
                    int maxZ = (int) Math.floor(Math.max(a.z(), Math.max(b.z(), c.z())) + reach - 0.5) + margin;
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            if (y < -2048 || y > 2047) {
                                continue;
                            }
                            for (int z = minZ; z <= maxZ; z++) {
                                if ((checked++ & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
                                    throw new CancellationException();
                                }
                                long key = PackedBlockPlan.pack(x, y, z);
                                double squared = distanceSquared(x + 0.5, y + 0.5, z + 0.5, triangle);
                                if (squared <= reachSquared && squared < distance.get(key)) {
                                    if (!distance.containsKey(key) && distance.size() >= MAX_BLOCKS) {
                                        throw new GeometryLimitException(MAX_BLOCKS);
                                    }
                                    distance.put(key, (float) squared);
                                    fractions.put(key, across);
                                }
                                if (smooth) {
                                    long bits = occupancy.get(key);
                                    for (int index = 0; index < SAMPLES.length; index++) {
                                        if ((bits & 1L << index) == 0 && distanceSquared(x + SAMPLES[index][0],
                                                y + SAMPLES[index][1], z + SAMPLES[index][2], triangle)
                                                <= reachSquared) {
                                            bits |= 1L << index;
                                        }
                                    }
                                    if (bits != 0L) {
                                        occupancy.put(key, bits);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LongOpenHashSet blocks = new LongOpenHashSet(distance.keySet());
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap();
        if (smooth) {
            smooth(settings, blocks, occupancy, pieces, fractions);
        }
        long[] positions = blocks.toLongArray();
        Arrays.sort(positions);
        byte[] pieceArray = new byte[positions.length];
        float[] across = new float[positions.length];
        for (int index = 0; index < positions.length; index++) {
            long key = positions[index];
            pieceArray[index] = pieces.containsKey(key) ? pieces.get(key) : SmoothPiece.FULL;
            across[index] = fractions.containsKey(key) ? fractions.get(key) : 0.0F;
        }
        return new LoftResult(positions, pieceArray, across);
    }

    /** Losas y escaleras con la ocupación de cada octante; las piezas en huecos se añaden a {@code blocks}. */
    private static void smooth(LoftSettings settings, LongOpenHashSet blocks, Long2LongOpenHashMap occupancy,
                               Long2ByteOpenHashMap pieces, Long2FloatOpenHashMap fractions) {
        PieceSolver solver = new PieceSolver(settings.smooth().mode(), settings.smooth().coverage());
        double[] fill = new double[8];
        LongArrayList keys = new LongArrayList(occupancy.keySet());
        keys.sort(null);
        for (int index = 0; index < keys.size(); index++) {
            long key = keys.getLong(index);
            long bits = occupancy.get(key);
            for (int octant = 0; octant < 8; octant++) {
                fill[octant] = Long.bitCount(bits >>> (octant * 5) & 0x1FL) / 5.0;
            }
            boolean existing = blocks.contains(key);
            byte piece = solver.choose(fill, existing);
            if (piece != SmoothPiece.FULL && piece != SmoothPiece.EMPTY) {
                solver.put(key, piece, fill);
            }
        }
        Long2ByteOpenHashMap solved = solver.finish(blocks::contains);
        for (var entry : solved.long2ByteEntrySet()) {
            long key = entry.getLongKey();
            if (!blocks.contains(key)) {
                blocks.add(key);
                fractions.put(key, neighbourFraction(key, fractions));
            }
            pieces.put(key, entry.getByteValue());
        }
        for (long dropped : solver.dropped()) {
            blocks.remove(dropped);
        }
    }

    private static float neighbourFraction(long key, Long2FloatOpenHashMap fractions) {
        int x = PackedBlockPlan.unpackX(key);
        int y = PackedBlockPlan.unpackY(key);
        int z = PackedBlockPlan.unpackZ(key);
        int[][] steps = {{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] step : steps) {
            long neighbour = PackedBlockPlan.pack(x + step[0], y + step[1], z + step[2]);
            if (fractions.containsKey(neighbour)) {
                return fractions.get(neighbour);
            }
        }
        return 0.0F;
    }

    private static void set(double[] triangle, Vec3 a, Vec3 b, Vec3 c) {
        triangle[0] = a.x();
        triangle[1] = a.y();
        triangle[2] = a.z();
        triangle[3] = b.x();
        triangle[4] = b.y();
        triangle[5] = b.z();
        triangle[6] = c.x();
        triangle[7] = c.y();
        triangle[8] = c.z();
    }

    /** Distancia al cuadrado de un punto a un triángulo (punto más cercano de Ericson). */
    static double distanceSquared(double px, double py, double pz, double[] t) {
        double abx = t[3] - t[0];
        double aby = t[4] - t[1];
        double abz = t[5] - t[2];
        double acx = t[6] - t[0];
        double acy = t[7] - t[1];
        double acz = t[8] - t[2];
        double apx = px - t[0];
        double apy = py - t[1];
        double apz = pz - t[2];
        double d1 = abx * apx + aby * apy + abz * apz;
        double d2 = acx * apx + acy * apy + acz * apz;
        if (d1 <= 0.0 && d2 <= 0.0) {
            return apx * apx + apy * apy + apz * apz;
        }
        double bpx = px - t[3];
        double bpy = py - t[4];
        double bpz = pz - t[5];
        double d3 = abx * bpx + aby * bpy + abz * bpz;
        double d4 = acx * bpx + acy * bpy + acz * bpz;
        if (d3 >= 0.0 && d4 <= d3) {
            return bpx * bpx + bpy * bpy + bpz * bpz;
        }
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0.0 && d1 >= 0.0 && d3 <= 0.0) {
            double v = d1 / (d1 - d3);
            return squared(apx - v * abx, apy - v * aby, apz - v * abz);
        }
        double cpx = px - t[6];
        double cpy = py - t[7];
        double cpz = pz - t[8];
        double d5 = abx * cpx + aby * cpy + abz * cpz;
        double d6 = acx * cpx + acy * cpy + acz * cpz;
        if (d6 >= 0.0 && d5 <= d6) {
            return cpx * cpx + cpy * cpy + cpz * cpz;
        }
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0.0 && d2 >= 0.0 && d6 <= 0.0) {
            double w = d2 / (d2 - d6);
            return squared(apx - w * acx, apy - w * acy, apz - w * acz);
        }
        double va = d3 * d6 - d5 * d4;
        if (va <= 0.0 && d4 - d3 >= 0.0 && d5 - d6 >= 0.0) {
            double w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            double qx = t[3] + w * (t[6] - t[3]) - px;
            double qy = t[4] + w * (t[7] - t[4]) - py;
            double qz = t[5] + w * (t[8] - t[5]) - pz;
            return squared(qx, qy, qz);
        }
        double denominator = 1.0 / (va + vb + vc);
        double v = vb * denominator;
        double w = vc * denominator;
        double qx = t[0] + abx * v + acx * w - px;
        double qy = t[1] + aby * v + acy * w - py;
        double qz = t[2] + abz * v + acz * w - pz;
        return squared(qx, qy, qz);
    }

    private static double squared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }
}
