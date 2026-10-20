package com.komainu.moxia.roof;

import java.util.Arrays;

/**
 * Rejilla de trabajo del tejado: la planta, la planta con el alero ({@code F'}, la planta ensanchada {@code alero}
 * bloques en cuadrado) y una subrejilla de medio bloque (cada columna, 2×2 subceldas). Las subceldas son las
 * columnas de octantes de las piezas: la altura del tejado se mide en sus centros.
 *
 * <p>Los campos de distancia son la base de los tejados de faldones: la distancia de ajedrez (8 vecinos) desde el
 * borde da justo la altura de un tejado a cuatro aguas sobre plantas rectilíneas (limatesas a 45° en las esquinas
 * de fuera y limahoyas en las de dentro, como el «straight skeleton»). Si los bordes de hastial no emiten
 * distancia, los faldones resbalan a lo largo de ellos y sale el de dos aguas, también en plantas en L, T o U:
 * hastial al final de cada ala, limatesa en la esquina de fuera y limahoya en la de dentro.</p>
 */
final class RoofGrid {
    static final byte EAVE = 0;
    static final byte GABLE = 1;
    /** +x, -x, +z, -z. */
    static final int[] DX = {1, -1, 0, 0};
    static final int[] DZ = {0, 0, 1, -1};
    private static final int MIN_GABLE_LENGTH = 3;

    final Footprint footprint;
    final int overhang;
    /** Coordenada del mundo de la columna 0 de la rejilla. */
    final int originX;
    final int originZ;
    final int cellsX;
    final int cellsZ;
    final int subX;
    final int subZ;
    private final boolean[] plan;
    private final boolean[] extended;
    /** Tipo de borde de cada columna de la planta hacia cada dirección (solo cuenta si ahí acaba la planta). */
    private final byte[] edgeTypes;

    RoofGrid(Footprint footprint, int overhang) {
        this.footprint = footprint;
        this.overhang = overhang;
        this.originX = footprint.minX() - overhang - 1;
        this.originZ = footprint.minZ() - overhang - 1;
        this.cellsX = footprint.sizeX() + 2 * overhang + 2;
        this.cellsZ = footprint.sizeZ() + 2 * overhang + 2;
        this.subX = cellsX * 2;
        this.subZ = cellsZ * 2;
        this.plan = new boolean[cellsX * cellsZ];
        for (int cz = 0; cz < cellsZ; cz++) {
            for (int cx = 0; cx < cellsX; cx++) {
                plan[cz * cellsX + cx] = footprint.contains(originX + cx, originZ + cz);
            }
        }
        this.extended = dilate(plan, cellsX, cellsZ, overhang);
        this.edgeTypes = new byte[cellsX * cellsZ * 4];
    }

    boolean inPlan(int cx, int cz) {
        return cx >= 0 && cz >= 0 && cx < cellsX && cz < cellsZ && plan[cz * cellsX + cx];
    }

    boolean inExtended(int cx, int cz) {
        return cx >= 0 && cz >= 0 && cx < cellsX && cz < cellsZ && extended[cz * cellsX + cx];
    }

    /** Columna de la planta en el borde: alguno de sus cuatro vecinos queda fuera. */
    boolean isWall(int cx, int cz) {
        if (!inPlan(cx, cz)) {
            return false;
        }
        for (int direction = 0; direction < 4; direction++) {
            if (!inPlan(cx + DX[direction], cz + DZ[direction])) {
                return true;
            }
        }
        return false;
    }

    /** Columna de {@code F'} en su borde exterior (donde va el pretil del tejado plano). */
    boolean isExtendedBorder(int cx, int cz) {
        if (!inExtended(cx, cz)) {
            return false;
        }
        for (int direction = 0; direction < 4; direction++) {
            if (!inExtended(cx + DX[direction], cz + DZ[direction])) {
                return true;
            }
        }
        return false;
    }

    int worldX(int cx) {
        return originX + cx;
    }

    int worldZ(int cz) {
        return originZ + cz;
    }

    /** Centro de la subcelda en coordenadas del mundo. */
    double subCenterX(int i) {
        return originX + (i + 0.5) * 0.5;
    }

    double subCenterZ(int k) {
        return originZ + (k + 0.5) * 0.5;
    }

    byte edgeType(int cx, int cz, int direction) {
        return edgeTypes[(cz * cellsX + cx) * 4 + direction];
    }

    /**
     * Clasifica cada borde de la planta. En los de dos aguas y holandés, un borde es hastial si sus dos extremos
     * son esquinas salientes, mide al menos tres bloques y es más corto que el fondo de planta que tiene detrás
     * (el remate de un ala); con {@code -eje} lo son los bordes perpendiculares al caballete. En el de un agua
     * todo lo que no es el lado bajo se trata como hastial (se cierra con muro).
     */
    void classify(RoofSettings settings, RoofSettings.Side lowSide) {
        Arrays.fill(edgeTypes, EAVE);
        RoofType type = settings.type();
        if (type != RoofType.GABLE && type != RoofType.GABLET && type != RoofType.SHED) {
            return;
        }
        RoofSettings.Axis preferred = settings.axis() != null ? settings.axis()
                : footprint.sizeX() >= footprint.sizeZ() ? RoofSettings.Axis.X : RoofSettings.Axis.Z;
        for (int direction = 0; direction < 4; direction++) {
            int nx = DX[direction];
            int nz = DZ[direction];
            int lx = Math.abs(nz);
            int lz = Math.abs(nx);
            RoofSettings.Axis normalAxis = nx != 0 ? RoofSettings.Axis.X : RoofSettings.Axis.Z;
            for (int cz = 0; cz < cellsZ; cz++) {
                for (int cx = 0; cx < cellsX; cx++) {
                    if (!crossing(cx, cz, direction) || crossing(cx - lx, cz - lz, direction)) {
                        continue;
                    }
                    int length = 0;
                    int minimumDepth = Integer.MAX_VALUE;
                    while (crossing(cx + length * lx, cz + length * lz, direction)) {
                        minimumDepth = Math.min(minimumDepth, depth(cx + length * lx, cz + length * lz, nx, nz));
                        length++;
                    }
                    boolean convexStart = !inPlan(cx - lx, cz - lz);
                    boolean convexEnd = !inPlan(cx + length * lx, cz + length * lz);
                    byte edge;
                    if (type == RoofType.SHED) {
                        edge = lowSide.dx() == nx && lowSide.dz() == nz ? EAVE : GABLE;
                    } else {
                        boolean cap = convexStart && convexEnd && length >= MIN_GABLE_LENGTH;
                        boolean gable;
                        if (settings.axis() != null) {
                            gable = cap && normalAxis == settings.axis();
                        } else {
                            gable = cap && (minimumDepth > length
                                    || minimumDepth == length && normalAxis == preferred);
                        }
                        edge = gable ? GABLE : EAVE;
                    }
                    for (int step = 0; step < length; step++) {
                        edgeTypes[((cz + step * lz) * cellsX + cx + step * lx) * 4 + direction] = edge;
                    }
                }
            }
        }
    }

    private boolean crossing(int cx, int cz, int direction) {
        return inPlan(cx, cz) && !inPlan(cx + DX[direction], cz + DZ[direction]);
    }

    /** Columnas seguidas de la planta desde esta hacia dentro (contando esta). */
    private int depth(int cx, int cz, int nx, int nz) {
        int depth = 0;
        while (inPlan(cx - depth * nx, cz - depth * nz)) {
            depth++;
        }
        return depth;
    }

    /**
     * Tipo del borde de {@code F'} que deja la columna {@code (cx, cz)} hacia {@code direction}: el del borde de la
     * planta que tiene detrás. En las esquinas del alero se busca de lado el borde más cercano con esa misma
     * orientación; si no hay ninguno, es alero.
     */
    byte extendedEdgeType(int cx, int cz, int direction) {
        int nx = DX[direction];
        int nz = DZ[direction];
        int lx = Math.abs(nz);
        int lz = Math.abs(nx);
        for (int lateral = 0; lateral <= overhang; lateral++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int startX = cx + sign * lateral * lx;
                int startZ = cz + sign * lateral * lz;
                for (int step = 0; step <= overhang + 1; step++) {
                    int x = startX - step * nx;
                    int z = startZ - step * nz;
                    if (inPlan(x, z)) {
                        return crossing(x, z, direction) ? edgeType(x, z, direction) : EAVE;
                    }
                }
                if (lateral == 0) {
                    break;
                }
            }
        }
        return EAVE;
    }

    /**
     * Distancia de ajedrez desde el borde de {@code F'}, en bloques y respecto a la cara de fuera del muro (0 en el
     * muro, negativa en el alero), en el centro de cada subcelda; {@code NaN} fuera de {@code F'}.
     *
     * @param eavesOnly si solo emiten distancia los bordes de alero (los hastiales no)
     */
    double[] distance(boolean eavesOnly) {
        int[] steps = new int[subX * subZ];
        Arrays.fill(steps, -1);
        int[] queue = new int[subX * subZ];
        int head = 0;
        int tail = 0;
        for (int k = 0; k < subZ; k++) {
            for (int i = 0; i < subX; i++) {
                if (inExtended(i >> 1, k >> 1)) {
                    continue;
                }
                for (int direction = 0; direction < 4; direction++) {
                    // La subcelda vecina hacia dentro y la dirección hacia esta (de dentro hacia fuera).
                    int ni = i - DX[direction];
                    int nk = k - DZ[direction];
                    if (ni < 0 || nk < 0 || ni >= subX || nk >= subZ || !inExtended(ni >> 1, nk >> 1)) {
                        continue;
                    }
                    if (!eavesOnly || extendedEdgeType(ni >> 1, nk >> 1, direction) == EAVE) {
                        steps[k * subX + i] = 0;
                        queue[tail++] = k * subX + i;
                        break;
                    }
                }
            }
        }
        while (head < tail) {
            int index = queue[head++];
            int i = index % subX;
            int k = index / subX;
            for (int dk = -1; dk <= 1; dk++) {
                for (int di = -1; di <= 1; di++) {
                    int ni = i + di;
                    int nk = k + dk;
                    if (ni < 0 || nk < 0 || ni >= subX || nk >= subZ) {
                        continue;
                    }
                    int next = nk * subX + ni;
                    if (steps[next] < 0 && inExtended(ni >> 1, nk >> 1)) {
                        steps[next] = steps[index] + 1;
                        queue[tail++] = next;
                    }
                }
            }
        }
        double[] distance = new double[subX * subZ];
        for (int index = 0; index < distance.length; index++) {
            int i = index % subX;
            int k = index / subX;
            if (!inExtended(i >> 1, k >> 1) || steps[index] < 0) {
                distance[index] = Double.NaN;
            } else {
                distance[index] = (steps[index] - 0.5) * 0.5 - overhang;
            }
        }
        return distance;
    }

    /**
     * Distancia en línea recta hasta salir de {@code F'} a lo largo de x (o de z) desde cada subcelda, en bloques:
     * el mínimo de los dos sentidos. Sirve para reconocer las esquinas salientes (ambas pequeñas).
     */
    double[] axisDistance(boolean alongX) {
        double[] result = new double[subX * subZ];
        Arrays.fill(result, Double.NaN);
        int lines = alongX ? subZ : subX;
        int length = alongX ? subX : subZ;
        int[] forward = new int[length];
        for (int line = 0; line < lines; line++) {
            int run = 0;
            for (int position = 0; position < length; position++) {
                int i = alongX ? position : line;
                int k = alongX ? line : position;
                run = inExtended(i >> 1, k >> 1) ? run + 1 : 0;
                forward[position] = run;
            }
            run = 0;
            for (int position = length - 1; position >= 0; position--) {
                int i = alongX ? position : line;
                int k = alongX ? line : position;
                run = inExtended(i >> 1, k >> 1) ? run + 1 : 0;
                if (run > 0) {
                    result[k * subX + i] = (Math.min(run, forward[position]) - 0.5) * 0.5;
                }
            }
        }
        return result;
    }

    private static boolean[] dilate(boolean[] cells, int sizeX, int sizeZ, int radius) {
        if (radius == 0) {
            return cells.clone();
        }
        boolean[] alongX = new boolean[cells.length];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                boolean any = false;
                for (int dx = -radius; dx <= radius && !any; dx++) {
                    int nx = x + dx;
                    any = nx >= 0 && nx < sizeX && cells[z * sizeX + nx];
                }
                alongX[z * sizeX + x] = any;
            }
        }
        boolean[] result = new boolean[cells.length];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                boolean any = false;
                for (int dz = -radius; dz <= radius && !any; dz++) {
                    int nz = z + dz;
                    any = nz >= 0 && nz < sizeZ && alongX[nz * sizeX + x];
                }
                result[z * sizeX + x] = any;
            }
        }
        return result;
    }
}
