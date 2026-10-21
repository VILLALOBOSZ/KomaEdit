package com.komainu.moxia.selection;

import com.komainu.moxia.shape.Vec3;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.function.BooleanSupplier;

/**
 * Lleva el lazo dibujado en la pantalla al mundo: los bloques cuya cara visible cae dentro del contorno y, si se pide,
 * los que hay detrás de ella hacia dentro.
 *
 * <p>Se lanzan rayos desde la cámara por una rejilla de la pantalla. Cada rayo se para en el primer bloque que cuenta
 * ({@link Voxels#counts}): sin «no sólido», atraviesa plantas y agua y se queda con lo que hay debajo. La rejilla
 * empieza gruesa ({@value #COARSE} unidades) y solo se afina en las celdas cuyas esquinas dan en bloques que no se
 * tocan: ahí cabe algo entre medias. El paso fino depende de lo lejos que esté lo que se ve, para que un bloque lejano
 * reciba al menos un par de rayos, y del número de bloques que hay entre las esquinas (mirando de canto, el suelo
 * se aplasta en la pantalla); así lo cercano cuesta poco y lo lejano no se queda con huecos. Lo que ocupa menos de
 * {@value #FINEST} unidades de pantalla (muy lejos y de canto) puede quedarse fuera: tampoco se distingue.</p>
 *
 * <p>Después, la profundidad: desde cada bloque visible se entra en línea recta por la cara que se veía (hacia abajo
 * si se veía la de arriba), tantos bloques como pida {@link LassoSettings} y sin pasar de la materia.</p>
 *
 * <p>Va por tramos ({@link #step}): el servidor le da un rato en cada tick y sigue en el siguiente. Lógica pura: el
 * mundo llega por {@link Voxels} y las pruebas usan una cámara y un terreno inventados.</p>
 */
public final class LassoProjection {
    /** Paso de la rejilla gruesa, en unidades de pantalla. */
    public static final double COARSE = 8.0;
    /** Paso más fino al afinar una celda. */
    public static final double FINEST = 0.5;
    /** Rayos que como mucho se lanzan en un lazo. */
    public static final int MAX_RAYS = 250_000;
    private static final int[][] INWARD = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final long NONE = Long.MIN_VALUE;

    private final LassoCamera camera;
    private final LassoPolygon polygon;
    private final LassoSettings settings;
    private final Voxels voxels;
    private final double reach;
    private final int maxBlocks;
    private final double originX;
    private final double originY;
    private final int columns;
    private final int rows;
    /** Bloque visible → cara por la que se ve (0 −X, 1 +X, 2 −Y, 3 +Y, 4 −Z, 5 +Z). */
    private final Long2ByteOpenHashMap surface = new Long2ByteOpenHashMap();
    private long[] previousRow;
    private double[] previousDistance;
    private int row;
    private int rays;
    private long steps;
    private BlockSet result;
    private final int[] cell = new int[3];
    private double lastDistance;

    /**
     * @param reach     hasta dónde llega un rayo, en bloques
     * @param maxBlocks bloques que como mucho puede tener lo que salga
     */
    public LassoProjection(LassoCamera camera, LassoPolygon polygon, LassoSettings settings, Voxels voxels,
                           double reach, int maxBlocks) {
        if (polygon.area() < LassoPolygon.MIN_AREA) {
            throw new IllegalArgumentException("El lazo es demasiado pequeño: rodea algo con el ratón");
        }
        this.camera = camera;
        this.polygon = polygon;
        this.settings = settings;
        this.voxels = voxels;
        this.reach = reach;
        this.maxBlocks = maxBlocks;
        double[] bounds = polygon.bounds();
        double minX = Math.max(0.0, bounds[0]);
        double minY = Math.max(0.0, bounds[1]);
        double maxX = Math.min(camera.width(), bounds[2]);
        double maxY = Math.min(camera.height(), bounds[3]);
        originX = Math.floor(minX / COARSE) * COARSE;
        originY = Math.floor(minY / COARSE) * COARSE;
        columns = Math.max(1, (int) Math.ceil((maxX - originX) / COARSE)) + 1;
        rows = Math.max(1, (int) Math.ceil((maxY - originY) / COARSE)) + 1;
    }

    /** Lo hace todo de una vez (las pruebas y las órdenes pequeñas). */
    public BlockSet run() {
        while (step(() -> false)) {
            // Sigue.
        }
        return result;
    }

    /**
     * Avanza hasta que {@code expired} diga basta (se mira después de cada fila de la rejilla).
     *
     * @return {@code true} si aún queda
     */
    public boolean step(BooleanSupplier expired) {
        while (result == null) {
            if (row < rows) {
                sampleRow();
            } else {
                result = deepen();
            }
            if (expired.getAsBoolean()) {
                break;
            }
        }
        return result == null;
    }

    public boolean done() {
        return result != null;
    }

    /** Lo seleccionado; {@code null} hasta que acaba. */
    public BlockSet result() {
        return result;
    }

    /** Cuánto lleva, de 0 a 1. */
    public double progress() {
        return result != null ? 1.0 : Math.min(0.99, row / (double) (rows + 1));
    }

    public int rays() {
        return rays;
    }

    /** Bloques recorridos por los rayos (para medir lo que cuesta). */
    public long steps() {
        return steps;
    }

    /** Los rayos de una fila de la rejilla gruesa y, con la anterior, el afinado de las celdas entre las dos. */
    private void sampleRow() {
        long[] blocks = new long[columns];
        double[] distances = new double[columns];
        double screenY = originY + row * COARSE;
        for (int column = 0; column < columns; column++) {
            double screenX = originX + column * COARSE;
            long hit = cast(screenX, screenY);
            blocks[column] = hit;
            distances[column] = hit == NONE ? Double.POSITIVE_INFINITY : lastDistance;
        }
        if (previousRow != null) {
            for (int column = 0; column + 1 < columns; column++) {
                refine(column, previousRow[column], previousRow[column + 1], blocks[column], blocks[column + 1],
                        max(previousDistance[column], previousDistance[column + 1], distances[column],
                                distances[column + 1]));
            }
        }
        previousRow = blocks;
        previousDistance = distances;
        row++;
    }

    /**
     * Una celda de la rejilla gruesa: si sus cuatro esquinas dan en el mismo bloque o en bloques que se tocan, no
     * hace falta nada más; si no, se lanzan rayos por dentro. El paso sale de lo más lejano que se ve en ella (un
     * bloque lejano es pequeño en la pantalla) y de cuántos bloques hay entre sus esquinas (mirando de lado, los
     * bloques del suelo se aplastan): como mucho {@value #FINEST} unidades.
     */
    private void refine(int column, long topLeft, long topRight, long bottomLeft, long bottomRight, double farthest) {
        if (topLeft == NONE && topRight == NONE && bottomLeft == NONE && bottomRight == NONE) {
            return;
        }
        if (touching(topLeft, topRight) && touching(topLeft, bottomLeft) && touching(topLeft, bottomRight)
                && touching(topRight, bottomLeft) && touching(topRight, bottomRight)
                && touching(bottomLeft, bottomRight)) {
            return;
        }
        double distance = Double.isFinite(farthest) ? farthest : reach;
        double step = Math.clamp(camera.pixelsPerBlock(distance) * 0.5, FINEST, COARSE);
        int span = span(new long[]{topLeft, topRight, bottomLeft, bottomRight});
        int divisions = Math.min((int) Math.round(COARSE / FINEST),
                Math.max((int) Math.ceil(COARSE / step), 2 * span + 1));
        if (divisions <= 1) {
            return;
        }
        double left = originX + column * COARSE;
        double top = originY + (row - 1) * COARSE;
        for (int v = 0; v <= divisions; v++) {
            for (int u = 0; u <= divisions; u++) {
                double screenX = left + COARSE * u / divisions;
                double screenY = top + COARSE * v / divisions;
                // Las esquinas ya tienen su rayo, y fuera del lazo no hace falta afinar.
                if ((u == 0 || u == divisions) && (v == 0 || v == divisions) || !polygon.contains(screenX, screenY)) {
                    continue;
                }
                cast(screenX, screenY);
            }
        }
    }

    /** Los bloques que separan a las esquinas que dieron en algo, como mucho (0 si son el mismo o solo hay uno). */
    private static int span(long[] corners) {
        int widest = 0;
        for (int first = 0; first < corners.length; first++) {
            for (int second = first + 1; second < corners.length; second++) {
                if (corners[first] == NONE || corners[second] == NONE) {
                    continue;
                }
                widest = Math.max(widest, Math.max(Math.abs(BlockSet.x(corners[first]) - BlockSet.x(corners[second])),
                        Math.max(Math.abs(BlockSet.y(corners[first]) - BlockSet.y(corners[second])),
                                Math.abs(BlockSet.z(corners[first]) - BlockSet.z(corners[second])))));
            }
        }
        return widest;
    }

    private static boolean touching(long first, long second) {
        if (first == NONE || second == NONE) {
            return first == second;
        }
        return Math.abs(BlockSet.x(first) - BlockSet.x(second)) <= 1
                && Math.abs(BlockSet.y(first) - BlockSet.y(second)) <= 1
                && Math.abs(BlockSet.z(first) - BlockSet.z(second)) <= 1;
    }

    private static double max(double a, double b, double c, double d) {
        double result = Double.NEGATIVE_INFINITY;
        for (double value : new double[]{a, b, c, d}) {
            if (Double.isFinite(value)) {
                result = Math.max(result, value);
            }
        }
        return result == Double.NEGATIVE_INFINITY ? Double.POSITIVE_INFINITY : result;
    }

    /**
     * El rayo por el punto {@code (screenX, screenY)}: el bloque en que se para (o {@link #NONE}). Si el punto cae
     * dentro del lazo, el bloque queda como visible.
     */
    private long cast(double screenX, double screenY) {
        if (++rays > MAX_RAYS) {
            throw new IllegalArgumentException("El lazo es demasiado grande o mira demasiado lejos: rodea menos o "
                    + "acércate");
        }
        Vec3 direction = camera.ray(screenX, screenY);
        int face = trace(camera.position(), direction);
        if (face < 0) {
            return NONE;
        }
        long block = BlockSet.pack(cell[0], cell[1], cell[2]);
        if (polygon.contains(screenX, screenY) && !surface.containsKey(block)) {
            if (surface.size() >= maxBlocks) {
                throw tooMany();
            }
            surface.put(block, (byte) face);
        }
        return block;
    }

    /**
     * Recorre el rayo bloque a bloque (Amanatides–Woo) hasta el primero que cuenta. Deja el bloque en {@link #cell}
     * y la distancia en {@link #lastDistance}.
     *
     * @return la cara por la que entró, o -1 si no dio en nada antes de {@link #reach}
     */
    private int trace(Vec3 origin, Vec3 direction) {
        double[] start = {origin.x(), origin.y(), origin.z()};
        double[] heading = {direction.x(), direction.y(), direction.z()};
        int[] position = {(int) Math.floor(start[0]), (int) Math.floor(start[1]), (int) Math.floor(start[2])};
        int[] stepSign = new int[3];
        double[] next = new double[3];
        double[] delta = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(heading[axis]) < 1.0E-12) {
                stepSign[axis] = 0;
                next[axis] = Double.POSITIVE_INFINITY;
                delta[axis] = Double.POSITIVE_INFINITY;
            } else {
                stepSign[axis] = heading[axis] > 0 ? 1 : -1;
                double boundary = heading[axis] > 0 ? position[axis] + 1.0 - start[axis] : start[axis] - position[axis];
                delta[axis] = 1.0 / Math.abs(heading[axis]);
                next[axis] = boundary * delta[axis];
            }
        }
        int minY = voxels.minY();
        int maxY = voxels.maxY();
        while (true) {
            int axis = next[0] < next[1] ? (next[0] < next[2] ? 0 : 2) : (next[1] < next[2] ? 1 : 2);
            double distance = next[axis];
            if (distance > reach) {
                return -1;
            }
            position[axis] += stepSign[axis];
            next[axis] += delta[axis];
            steps++;
            int y = position[1];
            if (y < minY && stepSign[1] <= 0 || y > maxY && stepSign[1] >= 0) {
                return -1;
            }
            if (y < minY || y > maxY) {
                continue;
            }
            int kind = voxels.kind(position[0], y, position[2]);
            if (kind == Voxels.UNLOADED) {
                return -1;
            }
            if (Voxels.counts(kind, settings.nonSolid())) {
                cell[0] = position[0];
                cell[1] = y;
                cell[2] = position[2];
                lastDistance = distance;
                // Se entra por la cara contraria al sentido del paso: yendo hacia +X se ve la cara −X.
                return axis * 2 + (stepSign[axis] > 0 ? 0 : 1);
            }
        }
    }

    /** Lo visible y, si se pide, lo que hay detrás hacia dentro. */
    private BlockSet deepen() {
        BlockSet blocks = new BlockSet();
        int depth = settings.reach();
        ObjectIterator<Long2ByteOpenHashMap.Entry> iterator = surface.long2ByteEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ByteOpenHashMap.Entry entry = iterator.next();
            long block = entry.getLongKey();
            blocks.addPacked(block);
            int[] inward = INWARD[entry.getByteValue()];
            int x = BlockSet.x(block);
            int y = BlockSet.y(block);
            int z = BlockSet.z(block);
            for (int layer = 1; layer < depth; layer++) {
                x += inward[0];
                y += inward[1];
                z += inward[2];
                if (!BlockSet.storable(y) || !voxels.counts(x, y, z, settings.nonSolid())) {
                    break;
                }
                blocks.add(x, y, z);
            }
            if (blocks.size() > maxBlocks) {
                throw tooMany();
            }
        }
        return blocks;
    }

    private IllegalArgumentException tooMany() {
        return new IllegalArgumentException("El lazo pasa de " + maxBlocks + " bloques: rodea menos o usa menos "
                + "profundidad");
    }
}
