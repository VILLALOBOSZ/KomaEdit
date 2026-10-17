package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.concurrent.CancellationException;

/**
 * Voxelizado inverso: en vez de estampar pinceles a lo largo de la curva (y luego tapar los huecos), recorre
 * los bloques cercanos al eje y pregunta a la figura si cada uno está dentro. Así el resultado es estanco por
 * construcción, el coste depende del volumen real y no del número de estampados, y la deformación por
 * escala, torsión o espaciado es exacta.
 */
final class TubeVoxelizer {
    private static final int BRICK_SHIFT = 3;
    private static final int BRICK_SIZE = 1 << BRICK_SHIFT;
    static final long MAX_CANDIDATES = 64_000_000L;
    private static final double BALANCED_OFFSET = 0.3;
    private static final double[] HIGH_OFFSETS = {-1.0 / 3.0, 0.0, 1.0 / 3.0};

    private TubeVoxelizer() {
    }

    static LongOpenHashSet voxelize(
            TubeCenterline line,
            TubeSettings settings,
            TubeShapes.TubeShape shape,
            int limit
    ) {
        Evaluator evaluator = new Evaluator(line, settings, shape);
        LongArrayList bricks = markBricks(line, settings, shape);
        if ((long) bricks.size() * BRICK_SIZE * BRICK_SIZE * BRICK_SIZE > MAX_CANDIDATES) {
            throw new IllegalArgumentException("La figura es demasiado voluminosa para el espacio de tubo; "
                    + "reduce el radio o la longitud del recorrido");
        }
        LongOpenHashSet blocks = new LongOpenHashSet();
        int checked = 0;
        for (int index = 0; index < bricks.size(); index++) {
            long brick = bricks.getLong(index);
            int originX = PackedBlockPlan.unpackX(brick) << BRICK_SHIFT;
            int originY = PackedBlockPlan.unpackY(brick) << BRICK_SHIFT;
            int originZ = PackedBlockPlan.unpackZ(brick) << BRICK_SHIFT;
            for (int x = 0; x < BRICK_SIZE; x++) {
                for (int y = 0; y < BRICK_SIZE; y++) {
                    int blockY = originY + y;
                    if (blockY < -2048 || blockY > 2047) {
                        continue;
                    }
                    for (int z = 0; z < BRICK_SIZE; z++) {
                        if ((checked++ & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
                            throw new CancellationException();
                        }
                        int blockX = originX + x;
                        int blockZ = originZ + z;
                        if (evaluator.place(blockX + 0.5, blockY + 0.5, blockZ + 0.5)
                                && blocks.add(PackedBlockPlan.pack(blockX, blockY, blockZ))
                                && blocks.size() > limit) {
                            throw new GeometryLimitException(limit);
                        }
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * Marca los ladrillos de 8³ bloques que pueden contener la figura. Se recorren en el orden del recorrido,
     * así la consulta del eje casi siempre empieza en el segmento correcto.
     */
    private static LongArrayList markBricks(
            TubeCenterline line,
            TubeSettings settings,
            TubeShapes.TubeShape shape
    ) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList ordered = new LongArrayList();
        double reach = reach(line, settings, shape);
        double spacing = Math.clamp(line.minimumRadius() * 0.5, 0.5, 4.0);
        int stride = Math.max(1, (int) Math.floor(spacing / TubeCenterline.STEP));
        for (int index = 0; index < line.count; index += stride) {
            markSphere(seen, ordered, line.px[index], line.py[index], line.pz[index],
                    line.radius[index] * reach + 1.0 + stride * TubeCenterline.STEP);
        }
        int last = line.count - 1;
        markSphere(seen, ordered, line.px[last], line.py[last], line.pz[last], line.radius[last] * reach + 1.0);
        double extension = line.closed ? 0.0 : settings.endMode().extension();
        if (extension > 0.0) {
            markCap(seen, ordered, line, 0, -1.0, extension, reach);
            markCap(seen, ordered, line, last, 1.0, extension, reach);
        }
        return ordered;
    }

    private static void markCap(
            LongOpenHashSet seen,
            LongArrayList ordered,
            TubeCenterline line,
            int index,
            double direction,
            double extension,
            double reach
    ) {
        double radius = line.radius[index];
        double length = radius * extension;
        for (double along = 0.0; along <= length + 1.0; along += 1.0) {
            markSphere(seen, ordered,
                    line.px[index] + line.tx[index] * along * direction,
                    line.py[index] + line.ty[index] * along * direction,
                    line.pz[index] + line.tz[index] * along * direction,
                    radius * reach + 1.5);
        }
    }

    private static void markSphere(
            LongOpenHashSet seen,
            LongArrayList ordered,
            double x,
            double y,
            double z,
            double radius
    ) {
        int minimumX = (int) Math.floor(x - radius) >> BRICK_SHIFT;
        int maximumX = (int) Math.floor(x + radius) >> BRICK_SHIFT;
        int minimumY = Math.max(-2048, (int) Math.floor(y - radius)) >> BRICK_SHIFT;
        int maximumY = Math.min(2047, (int) Math.floor(y + radius)) >> BRICK_SHIFT;
        int minimumZ = (int) Math.floor(z - radius) >> BRICK_SHIFT;
        int maximumZ = (int) Math.floor(z + radius) >> BRICK_SHIFT;
        double squared = radius * radius;
        for (int brickX = minimumX; brickX <= maximumX; brickX++) {
            double dx = axisGap(x, brickX);
            for (int brickY = minimumY; brickY <= maximumY; brickY++) {
                double dy = axisGap(y, brickY);
                if (dx * dx + dy * dy > squared) {
                    continue;
                }
                for (int brickZ = minimumZ; brickZ <= maximumZ; brickZ++) {
                    double dz = axisGap(z, brickZ);
                    if (dx * dx + dy * dy + dz * dz > squared) {
                        continue;
                    }
                    long key = PackedBlockPlan.pack(brickX, brickY, brickZ);
                    if (seen.add(key)) {
                        ordered.add(key);
                    }
                }
            }
        }
    }

    private static double axisGap(double value, int brick) {
        double minimum = brick << BRICK_SHIFT;
        double maximum = minimum + BRICK_SIZE;
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0.0;
    }

    /**
     * Distancia máxima al eje, en radios, a la que puede haber bloques. En modo vertical la sección se inclina
     * con la pendiente y se aleja más; el extremo en cubo llega a las esquinas (√2).
     */
    static double reach(TubeCenterline line, TubeSettings settings, TubeShapes.TubeShape shape) {
        double extent = shape.extent();
        if (!line.closed && settings.endMode() == TubeSettings.EndMode.CUBE) {
            extent = Math.max(extent, Math.sqrt(2.0));
        }
        return settings.normalMode() == TubeSettings.NormalMode.UPRIGHT ? extent * 1.6 : extent;
    }

    /** Evaluador por hilo: reutiliza el punto de consulta para no crear objetos por bloque. */
    static final class Evaluator {
        private final TubeCenterline line;
        private final TubeSettings settings;
        private final TubeShapes.TubeShape shape;
        private final TubeSettings.EndMode endMode;
        private final double reach;
        final TubeCenterline.TubePoint point = new TubeCenterline.TubePoint();

        Evaluator(TubeCenterline line, TubeSettings settings, TubeShapes.TubeShape shape) {
            this.line = line;
            this.settings = settings;
            this.shape = shape;
            endMode = line.closed ? TubeSettings.EndMode.FLAT : settings.endMode();
            reach = TubeVoxelizer.reach(line, settings, shape);
        }

        boolean place(double x, double y, double z) {
            if (settings.hollow()) {
                return shell(x, y, z);
            }
            double center = sample(x, y, z, false);
            if (center >= 0.0) {
                return true;
            }
            return switch (settings.quality()) {
                case FAST -> false;
                case BALANCED -> balanced(x, y, z);
                case HIGH -> high(x, y, z);
            };
        }

        /**
         * Pared hueca por morfología: el bloque está dentro y algún vecino a {@code wall} bloques está fuera.
         * Funciona con cualquier figura y garantiza una pared cerrada aunque la superficie vaya en diagonal.
         * Con extremos planos el tubo queda abierto como una tubería.
         */
        private boolean shell(double x, double y, double z) {
            if (sample(x, y, z, false) < 0.0) {
                return false;
            }
            boolean open = endMode == TubeSettings.EndMode.FLAT;
            int wall = settings.wall();
            return sample(x + wall, y, z, open) < 0.0
                    || sample(x - wall, y, z, open) < 0.0
                    || sample(x, y + wall, z, open) < 0.0
                    || sample(x, y - wall, z, open) < 0.0
                    || sample(x, y, z + wall, open) < 0.0
                    || sample(x, y, z - wall, open) < 0.0;
        }

        /** Centro o, si no, al menos dos de seis muestras a ±0.3: rescata detalles finos sin engordar volúmenes. */
        private boolean balanced(double x, double y, double z) {
            if (point.distanceSquared > square(point.radius * reach + 1.0) && point.beyond <= 0.0) {
                return false;
            }
            double offset = BALANCED_OFFSET;
            int inside = 0;
            for (int axis = 0; axis < 6; axis++) {
                double sign = (axis & 1) == 0 ? offset : -offset;
                double sampleX = axis >> 1 == 0 ? x + sign : x;
                double sampleY = axis >> 1 == 1 ? y + sign : y;
                double sampleZ = axis >> 1 == 2 ? z + sign : z;
                if (sample(sampleX, sampleY, sampleZ, false) >= 0.0 && ++inside >= 2) {
                    return true;
                }
                if (inside + (5 - axis) < 2) {
                    return false;
                }
            }
            return false;
        }

        private boolean high(double x, double y, double z) {
            if (point.distanceSquared > square(point.radius * reach + 1.0) && point.beyond <= 0.0) {
                return false;
            }
            int inside = 0;
            int remaining = 27;
            for (double offsetX : HIGH_OFFSETS) {
                for (double offsetY : HIGH_OFFSETS) {
                    for (double offsetZ : HIGH_OFFSETS) {
                        remaining--;
                        if (sample(x + offsetX, y + offsetY, z + offsetZ, false) >= 0.0 && ++inside >= 7) {
                            return true;
                        }
                        if (inside + remaining < 7) {
                            return false;
                        }
                    }
                }
            }
            return false;
        }

        /**
         * Valor de material en [0, 1] si el punto está dentro de la figura; negativo si está fuera.
         *
         * @param openEnds trata los extremos planos como si el tubo continuase (para paredes de tubería)
         */
        double sample(double x, double y, double z, boolean openEnds) {
            line.locate(x, y, z, point);
            return evaluate(point, openEnds);
        }

        /** Localiza un punto en el espacio de tubo sin evaluarlo (para quien quiera interpolar entre puntos). */
        void locate(double x, double y, double z, TubeCenterline.TubePoint target) {
            line.locate(x, y, z, target);
        }

        double length() {
            return line.length;
        }

        boolean closed() {
            return line.closed;
        }

        /** Lo mismo que {@link #sample}, para un punto ya localizado en el espacio de tubo. */
        double evaluate(TubeCenterline.TubePoint located, boolean openEnds) {
            double radius = located.radius;
            double limit = radius * reach;
            double radialSquared = located.localX * located.localX + located.localY * located.localY;
            if (radialSquared > limit * limit) {
                return -1.0;
            }
            double contraction = 1.0;
            boolean cube = false;
            if (located.beyond > 0.0) {
                double along = located.beyond / radius;
                if (!(openEnds && endMode == TubeSettings.EndMode.FLAT)) {
                    switch (endMode) {
                        case FLAT, SOFT -> {
                            return -1.0;
                        }
                        case ROUND -> {
                            if (along >= 1.0) {
                                return -1.0;
                            }
                            contraction = Math.sqrt(1.0 - along * along);
                        }
                        case SPIKE -> {
                            if (along >= 2.0) {
                                return -1.0;
                            }
                            contraction = 1.0 - along * 0.5;
                        }
                        case CUBE -> {
                            if (along > 1.0) {
                                return -1.0;
                            }
                            cube = true;
                        }
                    }
                }
            } else if (endMode == TubeSettings.EndMode.SOFT) {
                double fromEnd = Math.min(located.distance, line.length - located.distance) / radius;
                if (fromEnd < 1.0) {
                    double inset = 1.0 - fromEnd;
                    contraction = Math.sqrt(Math.max(0.0, 1.0 - inset * inset));
                }
            }
            if (contraction < 1.0E-3) {
                return -1.0;
            }
            double scale = 1.0 / (radius * contraction);
            double localX = located.localX * scale;
            double localY = located.localY * scale;
            double cosine = Math.cos(located.roll);
            double sine = Math.sin(located.roll);
            double shapeX = localX * cosine + localY * sine;
            double shapeY = -localX * sine + localY * cosine;
            if (cube) {
                double box = Math.max(Math.abs(shapeX), Math.abs(shapeY));
                return box <= 1.0 ? box : -1.0;
            }
            return shape.sample(shapeX, shapeY, located.longitudinal);
        }

        private static double square(double value) {
            return value * value;
        }
    }
}
