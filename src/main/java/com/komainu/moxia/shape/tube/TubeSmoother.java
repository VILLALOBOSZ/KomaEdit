package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.SmoothPieces;
import com.komainu.moxia.shape.smooth.StairShapes;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.concurrent.CancellationException;

/**
 * Losas y escaleras en la superficie ("smoothblocks"): cada bloque del borde, y cada hueco vacío pegado a él,
 * se mide por octantes con la misma función implícita que decidió la figura y {@link PieceFit} elige la pieza.
 *
 * <p>Solo se tocan bloques que la propia figura coloca: los que añade otro paso (acento de ritmo, cierre de
 * huecos) se quedan enteros. En tubos huecos la capa que toca el interior tampoco cambia, así una losa nunca
 * abre la pared, y no se añaden piezas fuera para no rellenar la boca de la tubería.</p>
 *
 * <p>Localizar el eje es lo caro de cada muestra. Dentro de un bloque las coordenadas de tubo cambian casi en
 * línea recta, así que se localizan el centro y tres puntos a un cuarto de bloque y el resto de muestras se
 * interpola (el error es de milésimas de bloque). Cerca de los extremos, en la costura de un lazo o donde el
 * punto más cercano del eje salta, se vuelve a localizar cada muestra.</p>
 */
final class TubeSmoother {
    private static final double[] COARSE = {0.25, 0.75};
    /** Cuatro puntos en damero alrededor del centro de cada octante: con el centro, cinco muestras por octante. */
    private static final double[][] CHECKER = {
            {-0.125, -0.125, -0.125}, {0.125, 0.125, -0.125}, {0.125, -0.125, 0.125}, {-0.125, 0.125, 0.125}
    };

    private static final double PROBE = 0.25;
    /** Avance máximo por el eje entre el centro y una sonda; más es un salto (costura o pliegue). */
    private static final double MAX_DISTANCE_STEP = 0.75;
    /** Los extremos cortan el bloque en seco: ahí no se interpola. */
    private static final double END_MARGIN = 2.0;
    private static final int FIELDS = 6;

    private final TubeVoxelizer.Evaluator evaluator;
    private final TubeSettings settings;
    private final boolean interpolate;
    private final double[] occupancy = new double[8];
    private final TubeCenterline.TubePoint base = new TubeCenterline.TubePoint();
    private final TubeCenterline.TubePoint probe = new TubeCenterline.TubePoint();
    private final TubeCenterline.TubePoint interpolated = new TubeCenterline.TubePoint();
    /** Derivadas de localX, localY, distancia, radio, longitudinal y giro respecto a x, y, z. */
    private final double[] gradient = new double[FIELDS * 3];
    private boolean linear;
    private double centerX;
    private double centerY;
    private double centerZ;
    private int processed;

    private TubeSmoother(TubeVoxelizer.Evaluator evaluator, TubeSettings settings, boolean interpolate) {
        this.evaluator = evaluator;
        this.settings = settings;
        this.interpolate = interpolate;
    }

    static boolean enabled(TubeSettings settings) {
        return settings.smoothMode() != SmoothMode.NONE && settings.coverage() > 0.0;
    }

    /**
     * @param blocks geometría final; se le añaden las posiciones nuevas que reciben pieza
     * @return piezas por posición, con las formas de escalera ya resueltas como lo haría Minecraft
     */
    static SmoothPieces smooth(
            TubeVoxelizer.Evaluator evaluator,
            TubeSettings settings,
            LongOpenHashSet blocks,
            int limit
    ) {
        return smooth(evaluator, settings, blocks, limit, true);
    }

    /** @param interpolate {@code false} localiza cada muestra (referencia exacta para pruebas) */
    static SmoothPieces smooth(
            TubeVoxelizer.Evaluator evaluator,
            TubeSettings settings,
            LongOpenHashSet blocks,
            int limit,
            boolean interpolate
    ) {
        if (!enabled(settings)) {
            return SmoothPieces.NONE;
        }
        return new TubeSmoother(evaluator, settings, interpolate).run(blocks, limit);
    }

    private SmoothPieces run(LongOpenHashSet blocks, int limit) {
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap();
        LongOpenHashSet candidates = new LongOpenHashSet();
        boolean hollow = settings.hollow();
        LongIterator iterator = blocks.iterator();
        while (iterator.hasNext()) {
            checkCancelled();
            long packed = iterator.nextLong();
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            if (!onSurface(blocks, x, y, z)) {
                continue;
            }
            if (!hollow) {
                collectEmptyNeighbors(blocks, candidates, x, y, z);
            }
            measure(x, y, z, true);
            byte piece = PieceFit.choose(occupancy, true, settings.smoothMode(), settings.coverage());
            // Las comprobaciones caras solo cuando el bloque cambia: que sea de la figura y no abra una tubería.
            if (piece != SmoothPiece.FULL
                    && evaluator.place(x + 0.5, y + 0.5, z + 0.5)
                    && !(hollow && touchesCavity(blocks, x, y, z))) {
                pieces.put(packed, piece);
            }
        }

        LongArrayList additions = new LongArrayList();
        LongIterator candidateIterator = candidates.iterator();
        while (candidateIterator.hasNext()) {
            checkCancelled();
            long packed = candidateIterator.nextLong();
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            if (!measure(x, y, z, false)) {
                continue;
            }
            byte piece = PieceFit.choose(occupancy, false, settings.smoothMode(), settings.coverage());
            // Si la figura sí quería un bloque aquí y otro paso lo quitó (islas sueltas), se respeta.
            if (piece != SmoothPiece.EMPTY && !evaluator.place(x + 0.5, y + 0.5, z + 0.5)) {
                pieces.put(packed, piece);
                additions.add(packed);
            }
        }
        if ((long) blocks.size() + additions.size() > limit) {
            throw new GeometryLimitException(limit);
        }
        blocks.addAll(additions);
        StairShapes.resolve(pieces);
        return new SmoothPieces(pieces);
    }

    /**
     * Rellena {@link #occupancy} con la fracción de cada octante dentro de la figura. Primero mira el centro de
     * cada octante; solo si el bloque es mixto (o es un detalle fino rescatado por el supermuestreo) afina con
     * cuatro muestras más por octante.
     *
     * @return {@code false} si ningún octante toca la figura (no hace falta afinar ni elegir)
     */
    private boolean measure(int x, int y, int z, boolean existing) {
        centerX = x + 0.5;
        centerY = y + 0.5;
        centerZ = z + 0.5;
        linear = interpolate && linearize();
        int inside = 0;
        for (int octant = 0; octant < 8; octant++) {
            boolean hit = inside(x + COARSE[octant & 1], y + COARSE[octant >> 1 & 1], z + COARSE[octant >> 2]);
            occupancy[octant] = hit ? 1.0 : 0.0;
            if (hit) {
                inside++;
            }
        }
        boolean mixed = inside > 0 && inside < 8;
        if (settings.quality() == TubeSettings.Quality.FAST || !(mixed || existing && inside == 0)) {
            return inside > 0 || existing;
        }
        boolean any = false;
        for (int octant = 0; octant < 8; octant++) {
            double octantX = x + COARSE[octant & 1];
            double octantY = y + COARSE[octant >> 1 & 1];
            double octantZ = z + COARSE[octant >> 2];
            int hits = occupancy[octant] > 0.0 ? 1 : 0;
            for (double[] offset : CHECKER) {
                if (inside(octantX + offset[0], octantY + offset[1], octantZ + offset[2])) {
                    hits++;
                }
            }
            occupancy[octant] = hits / 5.0;
            any |= hits > 0;
        }
        return any || existing;
    }

    private boolean inside(double x, double y, double z) {
        if (!linear) {
            return evaluator.sample(x, y, z, false) >= 0.0;
        }
        double dx = x - centerX;
        double dy = y - centerY;
        double dz = z - centerZ;
        interpolated.localX = field(0, base.localX, dx, dy, dz);
        interpolated.localY = field(1, base.localY, dx, dy, dz);
        interpolated.distance = field(2, base.distance, dx, dy, dz);
        interpolated.radius = field(3, base.radius, dx, dy, dz);
        interpolated.longitudinal = field(4, base.longitudinal, dx, dy, dz);
        interpolated.roll = field(5, base.roll, dx, dy, dz);
        interpolated.beyond = 0.0;
        return evaluator.evaluate(interpolated, false) >= 0.0;
    }

    private double field(int field, double value, double dx, double dy, double dz) {
        int offset = field * 3;
        return value + gradient[offset] * dx + gradient[offset + 1] * dy + gradient[offset + 2] * dz;
    }

    /** Prepara la interpolación del bloque actual; {@code false} si hay que localizar cada muestra. */
    private boolean linearize() {
        evaluator.locate(centerX, centerY, centerZ, base);
        if (base.beyond > 0.0 || !evaluator.closed()
                && (base.distance < END_MARGIN || base.distance > evaluator.length() - END_MARGIN)) {
            return false;
        }
        for (int axis = 0; axis < 3; axis++) {
            evaluator.locate(
                    centerX + (axis == 0 ? PROBE : 0.0),
                    centerY + (axis == 1 ? PROBE : 0.0),
                    centerZ + (axis == 2 ? PROBE : 0.0),
                    probe
            );
            if (probe.beyond > 0.0 || Math.abs(probe.distance - base.distance) > MAX_DISTANCE_STEP) {
                return false;
            }
            gradient[axis] = (probe.localX - base.localX) / PROBE;
            gradient[3 + axis] = (probe.localY - base.localY) / PROBE;
            gradient[6 + axis] = (probe.distance - base.distance) / PROBE;
            gradient[9 + axis] = (probe.radius - base.radius) / PROBE;
            gradient[12 + axis] = (probe.longitudinal - base.longitudinal) / PROBE;
            gradient[15 + axis] = (probe.roll - base.roll) / PROBE;
        }
        return true;
    }

    private static boolean onSurface(LongOpenHashSet blocks, int x, int y, int z) {
        for (int direction = 0; direction < 6; direction++) {
            int nextY = y + offsetY(direction);
            if (nextY < -2048 || nextY > 2047
                    || !blocks.contains(PackedBlockPlan.pack(x + offsetX(direction), nextY, z + offsetZ(direction)))) {
                return true;
            }
        }
        return false;
    }

    /** En un tubo hueco, un vecino vacío que sigue dentro de la figura maciza es el interior de la tubería. */
    private boolean touchesCavity(LongOpenHashSet blocks, int x, int y, int z) {
        for (int direction = 0; direction < 6; direction++) {
            int nextX = x + offsetX(direction);
            int nextY = y + offsetY(direction);
            int nextZ = z + offsetZ(direction);
            if (nextY < -2048 || nextY > 2047 || blocks.contains(PackedBlockPlan.pack(nextX, nextY, nextZ))) {
                continue;
            }
            if (evaluator.sample(nextX + 0.5, nextY + 0.5, nextZ + 0.5, false) >= 0.0) {
                return true;
            }
        }
        return false;
    }

    private static void collectEmptyNeighbors(LongOpenHashSet blocks, LongOpenHashSet candidates, int x, int y, int z) {
        for (int direction = 0; direction < 6; direction++) {
            int nextY = y + offsetY(direction);
            if (nextY < -2048 || nextY > 2047) {
                continue;
            }
            long neighbor = PackedBlockPlan.pack(x + offsetX(direction), nextY, z + offsetZ(direction));
            if (!blocks.contains(neighbor)) {
                candidates.add(neighbor);
            }
        }
    }

    private void checkCancelled() {
        if ((processed++ & 1_023) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    private static int offsetX(int direction) {
        return direction == 0 ? 1 : direction == 1 ? -1 : 0;
    }

    private static int offsetY(int direction) {
        return direction == 2 ? 1 : direction == 3 ? -1 : 0;
    }

    private static int offsetZ(int direction) {
        return direction == 4 ? 1 : direction == 5 ? -1 : 0;
    }
}
