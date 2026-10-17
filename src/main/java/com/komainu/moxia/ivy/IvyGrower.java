package com.komainu.moxia.ivy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Hiedra viva: varias puntas avanzan por las celdas vacías pegadas a lo construido, con adherencia a la superficie,
 * querencia a trepar, deriva aleatoria y ramas; en los bordes de abajo cuelgan con gravedad y de los techos caen
 * tiras de bayas. Al final cada celda se viste según los apoyos que tiene: enredadera con sus caras, alfombra de
 * musgo en lo horizontal, matas de hojas por ruido y tiras colgantes.
 *
 * <p>Todo es Java puro sobre {@link IvyTerrain} y depende solo de la semilla: el mismo mundo y la misma semilla dan
 * la misma hiedra.</p>
 */
public final class IvyGrower {
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;
    public static final String[] FACE_NAMES = {"down", "up", "north", "south", "west", "east"};
    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};
    public static final int SIDE_FACES = (1 << NORTH) | (1 << SOUTH) | (1 << WEST) | (1 << EAST);
    /** Bit del apoyo: el bloque de abajo sostiene una alfombra aunque su cara de arriba no sea entera. */
    static final int REST = 1 << 6;
    /** Radio en el que una semilla busca una superficie donde agarrarse. */
    public static final int SEED_SEARCH = 4;
    private static final int MAX_TIPS = 4_096;
    private static final int MAX_DEPTH = 5;
    private static final int MAX_BRIDGE = 2;
    private static final int[][] MOVES = moves();

    private final IvyTerrain terrain;
    private final IvySettings settings;
    private final IvyStyle style;
    private final long seed;
    private final int maxBlocks;
    private final BooleanSupplier cancelled;
    private final SplittableRandom random;
    private final Map<Long, Cell> cells = new LinkedHashMap<>();
    private final Map<Long, Integer> supports = new HashMap<>();
    private final List<Tip> tips = new ArrayList<>();
    private boolean truncated;
    private int grownTips;

    private IvyGrower(IvyTerrain terrain, IvySettings settings, long seed, int maxBlocks, BooleanSupplier cancelled) {
        this.terrain = terrain;
        this.settings = settings;
        this.style = settings.style();
        this.seed = seed;
        this.maxBlocks = maxBlocks;
        this.cancelled = cancelled;
        this.random = new SplittableRandom(seed);
    }

    /** Punto semilla. */
    public record Point(int x, int y, int z) {
    }

    /**
     * @param seeds     celdas de partida (la celda vacía delante de la cara que se miró, por ejemplo); cada una busca
     *                  la superficie más cercana en {@value #SEED_SEARCH} bloques
     * @param maxBlocks tope de bloques: al llegar se para y el resultado sale truncado
     * @param cancelled se consulta a menudo; si devuelve {@code true} se lanza {@link CancellationException}
     */
    public static IvyResult grow(
            IvyTerrain terrain,
            List<Point> seeds,
            IvySettings settings,
            long seed,
            int maxBlocks,
            BooleanSupplier cancelled
    ) {
        if (maxBlocks < 1) {
            throw new IllegalArgumentException("El límite de bloques debe ser positivo");
        }
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("Hace falta al menos una semilla");
        }
        return new IvyGrower(terrain, settings, seed, maxBlocks, cancelled).run(seeds);
    }

    private IvyResult run(List<Point> seeds) {
        int used = 0;
        Set<Long> starts = new HashSet<>();
        for (Point point : seeds) {
            Point start = findStart(point);
            if (start == null || !starts.add(key(start.x, start.y, start.z))) {
                continue;
            }
            used++;
            if (visit(start.x, start.y, start.z, Cell.STEM, 0.0) == null) {
                break;
            }
            double offset = random.nextDouble() * Math.PI * 2.0;
            for (int stem = 0; stem < settings.stems(); stem++) {
                double angle = offset + Math.PI * 2.0 * stem / settings.stems() + (random.nextDouble() - 0.5) * 0.6;
                Tip tip = new Tip(start.x, start.y, start.z, 0);
                tip.setHeading(Math.cos(angle) * 0.7, 1.0, Math.sin(angle) * 0.7);
                tip.energy = settings.length() * (0.85 + 0.3 * random.nextDouble());
                tips.add(tip);
            }
        }
        int rounds = 0;
        while (!tips.isEmpty() && !truncated) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("Crecimiento de la hiedra cancelado");
            }
            int count = tips.size();
            for (int index = 0; index < count && !truncated; index++) {
                Tip tip = tips.get(index);
                if (tip.energy > 0.0) {
                    step(tip);
                }
            }
            tips.removeIf(tip -> tip.energy <= 0.0);
            if (++rounds > 1_000_000) {
                break;
            }
        }
        return new IvyResult(dress(), grownTips, used, seeds.size() - used, truncated);
    }

    /** Celda vacía con apoyo más cercana a la semilla (búsqueda en anchura; a igual distancia, mejor un muro). */
    private Point findStart(Point seedPoint) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        if (terrain.free(seedPoint.x, seedPoint.y, seedPoint.z)) {
            queue.add(new int[]{seedPoint.x, seedPoint.y, seedPoint.z, 0});
            seen.add(key(seedPoint.x, seedPoint.y, seedPoint.z));
        } else {
            for (int face = 0; face < 6; face++) {
                int x = seedPoint.x + DX[face];
                int y = seedPoint.y + DY[face];
                int z = seedPoint.z + DZ[face];
                if (terrain.free(x, y, z) && seen.add(key(x, y, z))) {
                    queue.add(new int[]{x, y, z, 1});
                }
            }
        }
        int level = -1;
        Point fallback = null;
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if (cell[3] != level) {
                if (fallback != null) {
                    return fallback;
                }
                level = cell[3];
            }
            int support = support(cell[0], cell[1], cell[2]);
            if ((support & (SIDE_FACES | (1 << UP))) != 0) {
                return new Point(cell[0], cell[1], cell[2]);
            }
            if (support != 0 && fallback == null) {
                fallback = new Point(cell[0], cell[1], cell[2]);
            }
            if (cell[3] >= SEED_SEARCH) {
                continue;
            }
            for (int face = 0; face < 6; face++) {
                int x = cell[0] + DX[face];
                int y = cell[1] + DY[face];
                int z = cell[2] + DZ[face];
                if (terrain.free(x, y, z) && seen.add(key(x, y, z))) {
                    queue.add(new int[]{x, y, z, cell[3] + 1});
                }
            }
        }
        return fallback;
    }

    private void step(Tip tip) {
        grownTips += tip.steps == 0 ? 1 : 0;
        tip.steps++;
        // Deriva del rumbo y querencia a trepar (menos cuanto más cuelga).
        double wander = 0.35;
        tip.setHeading(
                tip.hx + random.nextGaussian() * wander,
                tip.hy + random.nextGaussian() * wander * 0.6 + 0.12 - 0.08 * settings.hang(),
                tip.hz + random.nextGaussian() * wander
        );
        int[] best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] move : MOVES) {
            int x = tip.x + move[0];
            int y = tip.y + move[1];
            int z = tip.z + move[2];
            if (!terrain.free(x, y, z) || !passable(tip, move)) {
                continue;
            }
            int support = support(x, y, z);
            if (support == 0) {
                continue;
            }
            double length = move[3] == 1 ? 1.0 : Math.sqrt(2.0);
            double score = (tip.hx * move[0] + tip.hy * move[1] + tip.hz * move[2]) / length;
            if ((support & (SIDE_FACES | (1 << UP))) != 0) {
                score += 0.15;
            } else {
                // Solo suelo: la hiedra busca muros, no alfombra el terreno.
                score -= 0.4;
            }
            long key = key(x, y, z);
            Cell existing = cells.get(key);
            if (existing != null) {
                score -= 0.45 * Math.min(existing.visits, 3);
            }
            if (key == tip.previous || key == tip.previous2) {
                score -= 1.5;
            }
            score += (random.nextDouble() - 0.5) * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        if (best != null && bestScore > -0.25) {
            moveTo(tip, best, Cell.STEM);
            tip.floating = 0;
            tip.stuck = 0;
            return;
        }
        fallOrBridge(tip);
    }

    /** Un paso en diagonal no puede colarse entre dos bloques que se tocan por la arista. */
    private boolean passable(Tip tip, int[] move) {
        if (move[3] == 1) {
            return true;
        }
        boolean first = true;
        boolean anyFree = false;
        for (int axis = 0; axis < 3; axis++) {
            if (move[axis] == 0) {
                continue;
            }
            int x = tip.x + (axis == 0 ? move[0] : 0);
            int y = tip.y + (axis == 1 ? move[1] : 0);
            int z = tip.z + (axis == 2 ? move[2] : 0);
            anyFree |= terrain.free(x, y, z);
            first = false;
        }
        return !first && anyFree;
    }

    private void moveTo(Tip tip, int[] move, int kind) {
        double length = move[3] == 1 ? 1.0 : Math.sqrt(2.0);
        tip.previous2 = tip.previous;
        tip.previous = key(tip.x, tip.y, tip.z);
        tip.x += move[0];
        tip.y += move[1];
        tip.z += move[2];
        // Por el suelo se cansa antes: así no extiende alfombras por todo el terreno.
        boolean floorOnly = (support(tip.x, tip.y, tip.z) & (SIDE_FACES | (1 << UP))) == 0;
        tip.energy -= floorOnly ? length * 2.0 : length;
        tip.distance += length;
        tip.setHeading(
                tip.hx * 0.75 + move[0] / length * 0.25,
                tip.hy * 0.75 + move[1] / length * 0.25,
                tip.hz * 0.75 + move[2] / length * 0.25
        );
        Cell cell = visit(tip.x, tip.y, tip.z, kind, tip.distance);
        if (cell == null || kind != Cell.STEM) {
            return;
        }
        maybeHang(cell);
        maybeCeilingStrand(cell);
        maybeBranch(tip, cell);
    }

    /** Sin sitio donde agarrarse: cuelga lo que le queda, cruza un hueco corto por el aire o da la vuelta. */
    private void fallOrBridge(Tip tip) {
        Cell here = cells.get(key(tip.x, tip.y, tip.z));
        int sides = support(tip.x, tip.y, tip.z) & SIDE_FACES;
        if (here != null && here.kind == Cell.STEM && !here.hangTried && sides != 0 && settings.hang() > 0.0
                && bottomEdge(here, sides)) {
            here.hangTried = true;
            int length = Math.min(settings.maxHang(), 1 + (int) (tip.energy * 0.5));
            hangStrand(here, sides, length);
            tip.energy = 0.0;
            return;
        }
        if (tip.floating < MAX_BRIDGE) {
            int face = dominantSide(tip);
            if (face >= 0 && landingAhead(tip, face, MAX_BRIDGE + 1 - tip.floating)) {
                tip.floating++;
                moveTo(tip, new int[]{DX[face], DY[face], DZ[face], 1}, Cell.BRIDGE);
                return;
            }
        }
        tip.setHeading(-tip.hx, Math.abs(tip.hy) + 0.2, -tip.hz);
        tip.energy -= 1.0;
        if (++tip.stuck > 4) {
            tip.energy = 0.0;
        }
    }

    /** Al otro lado del hueco hay, a {@code reach} bloques como mucho, una celda con apoyo: el puente llega a algo. */
    private boolean landingAhead(Tip tip, int face, int reach) {
        for (int distance = 1; distance <= reach; distance++) {
            int x = tip.x + DX[face] * distance;
            int z = tip.z + DZ[face] * distance;
            if (!terrain.free(x, tip.y, z)) {
                return false;
            }
            if (distance > 1 && support(x, tip.y, z) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Lado horizontal hacia el que apunta el rumbo; los puentes solo cruzan huecos de lado, nunca hacia arriba. */
    private int dominantSide(Tip tip) {
        double ax = Math.abs(tip.hx);
        double az = Math.abs(tip.hz);
        if (Math.max(ax, az) < 0.25) {
            return -1;
        }
        if (ax >= az) {
            return tip.hx >= 0 ? EAST : WEST;
        }
        return tip.hz >= 0 ? SOUTH : NORTH;
    }

    /** Debajo de la celda hay hueco y lo de debajo ya no se agarra a las mismas caras: es un borde inferior. */
    private boolean bottomEdge(Cell cell, int sides) {
        return terrain.free(cell.x, cell.y - 1, cell.z) && (support(cell.x, cell.y - 1, cell.z) & sides) == 0;
    }

    private void maybeHang(Cell cell) {
        if (cell.hangTried || settings.hang() <= 0.0) {
            return;
        }
        int sides = cell.support & SIDE_FACES;
        if (sides == 0 || !bottomEdge(cell, sides)) {
            return;
        }
        cell.hangTried = true;
        if (random.nextDouble() < 0.2 + 0.6 * settings.hang()) {
            hangStrand(cell, sides, 1 + random.nextInt(settings.maxHang()));
        }
    }

    /**
     * Tira de enredadera que cuelga de {@code anchor}: cada tramo hereda las caras del de encima, como hace la
     * enredadera de Minecraft al crecer hacia abajo, así que el de arriba tiene que seguir siendo enredadera.
     */
    private void hangStrand(Cell anchor, int faces, int length) {
        anchor.anchor = true;
        for (int index = 1; index <= length; index++) {
            int y = anchor.y - index;
            if (!terrain.free(anchor.x, y, anchor.z) || cells.containsKey(key(anchor.x, y, anchor.z))) {
                return;
            }
            int support = support(anchor.x, y, anchor.z);
            if ((support & SIDE_FACES) != 0) {
                return;
            }
            Cell cell = visit(anchor.x, y, anchor.z, Cell.HANG, anchor.distance + index);
            if (cell == null) {
                return;
            }
            cell.strandFaces = faces;
            cell.anchor = true;
            if ((support & ((1 << DOWN) | REST)) != 0) {
                return;
            }
        }
    }

    /** Del techo cae una tira de bayas brillantes (si el estilo las tiene). */
    private void maybeCeilingStrand(Cell cell) {
        if (cell.hangTried || !style.ceilingStrands() || settings.hang() <= 0.0
                || (cell.support & (1 << UP)) == 0 || (cell.support & SIDE_FACES) != 0
                || !terrain.free(cell.x, cell.y - 1, cell.z)) {
            return;
        }
        cell.hangTried = true;
        if (random.nextDouble() >= 0.1 + 0.3 * settings.hang()) {
            return;
        }
        int length = 1 + random.nextInt(settings.maxHang());
        cell.kind = Cell.CAVE;
        cell.anchor = true;
        Cell last = cell;
        for (int index = 1; index < length; index++) {
            int y = cell.y - index;
            if (!terrain.free(cell.x, y, cell.z) || cells.containsKey(key(cell.x, y, cell.z))
                    || (support(cell.x, y, cell.z) & SIDE_FACES) != 0) {
                break;
            }
            Cell next = visit(cell.x, y, cell.z, Cell.CAVE, cell.distance + index);
            if (next == null) {
                break;
            }
            next.anchor = true;
            last = next;
        }
        last.strandEnd = true;
    }

    private void maybeBranch(Tip tip, Cell cell) {
        if (tip.depth >= MAX_DEPTH || tip.energy <= 3.0 || tips.size() >= MAX_TIPS
                || random.nextDouble() >= 0.03 + 0.15 * settings.branching()) {
            return;
        }
        // Normal de la superficie: hacia los bloques que la sostienen.
        double nx = 0.0;
        double ny = 0.0;
        double nz = 0.0;
        for (int face = 0; face < 6; face++) {
            if ((cell.support & (1 << face)) != 0) {
                nx += DX[face];
                ny += DY[face];
                nz += DZ[face];
            }
        }
        double px = tip.hy * nz - tip.hz * ny;
        double py = tip.hz * nx - tip.hx * nz;
        double pz = tip.hx * ny - tip.hy * nx;
        if (px * px + py * py + pz * pz < 0.04) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            px = Math.cos(angle);
            py = 0.0;
            pz = Math.sin(angle);
        }
        double side = random.nextBoolean() ? 1.0 : -1.0;
        Tip branch = new Tip(tip.x, tip.y, tip.z, tip.depth + 1);
        branch.setHeading(px * side + tip.hx * 0.35, py * side + tip.hy * 0.35 + 0.2, pz * side + tip.hz * 0.35);
        branch.energy = tip.energy * (0.45 + 0.35 * random.nextDouble());
        branch.distance = tip.distance;
        branch.previous = tip.previous;
        tips.add(branch);
    }

    private Cell visit(int x, int y, int z, int kind, double distance) {
        long key = key(x, y, z);
        Cell cell = cells.get(key);
        if (cell != null) {
            cell.visits++;
            return cell;
        }
        if (cells.size() >= maxBlocks) {
            truncated = true;
            return null;
        }
        cell = new Cell(x, y, z, support(x, y, z), kind, distance);
        cells.put(key, cell);
        return cell;
    }

    /** Caras de los vecinos que sostienen la celda (bit {@code 1 << cara}) y {@link #REST}. */
    int support(int x, int y, int z) {
        long key = key(x, y, z);
        Integer cached = supports.get(key);
        if (cached != null) {
            return cached;
        }
        int mask = 0;
        for (int face = 0; face < 6; face++) {
            if (terrain.sturdy(x + DX[face], y + DY[face], z + DZ[face], face ^ 1)) {
                mask |= 1 << face;
            }
        }
        if ((mask & (1 << DOWN)) == 0 && terrain.rests(x, y - 1, z)) {
            mask |= REST;
        }
        supports.put(key, mask);
        return mask;
    }

    private List<IvyBlock> dress() {
        List<IvyBlock> blocks = new ArrayList<>(cells.size());
        for (Cell cell : cells.values()) {
            IvyBlock block = dress(cell);
            if (block != null) {
                blocks.add(block);
            }
        }
        blocks.sort(Comparator.comparingInt(IvyBlock::y).reversed());
        return blocks;
    }

    private IvyBlock dress(Cell cell) {
        int climbable = cell.support & (SIDE_FACES | (1 << UP));
        return switch (cell.kind) {
            case Cell.HANG -> block(cell, IvyMaterial.VINE, cell.strandFaces | climbable, false);
            case Cell.CAVE -> block(cell, cell.strandEnd ? IvyMaterial.CAVE_VINES : IvyMaterial.CAVE_VINES_PLANT, 0,
                    hash(cell, 3) < style.berries());
            case Cell.BRIDGE -> block(cell, leaves(cell), 0, false);
            default -> {
                if (!cell.anchor && leafy(cell)) {
                    yield block(cell, leaves(cell), 0, false);
                }
                if (climbable != 0) {
                    if (!cell.anchor && (cell.support & SIDE_FACES) == 0 && hash(cell, 5) < style.sporeBlossoms()) {
                        yield block(cell, IvyMaterial.SPORE_BLOSSOM, 0, false);
                    }
                    if (!cell.anchor && noise(cell.x, cell.y, cell.z, 3.0, 7) < style.lichenShare()) {
                        yield block(cell, IvyMaterial.GLOW_LICHEN, cell.support & 0x3F, false);
                    }
                    yield block(cell, IvyMaterial.VINE, climbable, false);
                }
                if ((cell.support & (1 << DOWN)) != 0 && hash(cell, 9) < style.floorLichenShare()) {
                    yield block(cell, IvyMaterial.GLOW_LICHEN, 1 << DOWN, false);
                }
                yield block(cell, IvyMaterial.MOSS_CARPET, 0, false);
            }
        };
    }

    /** Matas de hojas por ruido, más tupidas cerca de la raíz y donde se cruzan varias puntas. */
    private boolean leafy(Cell cell) {
        if (style.leafiness() <= 0.0) {
            return false;
        }
        double youth = Math.min(1.0, cell.distance / settings.length());
        double threshold = style.leafiness() * (0.55 + 0.45 * (1.0 - youth)) + 0.06 * Math.min(cell.visits - 1, 3);
        return noise(cell.x, cell.y, cell.z, 2.6, 1) * 0.75 + hash(cell, 2) * 0.25 < threshold;
    }

    private IvyMaterial leaves(Cell cell) {
        if (hash(cell, 4) < style.flowerShare()) {
            return IvyMaterial.FLOWERING_AZALEA_LEAVES;
        }
        return hash(cell, 6) < style.secondaryShare() ? style.secondaryLeaves() : style.leaves();
    }

    private static IvyBlock block(Cell cell, IvyMaterial material, int faces, boolean berries) {
        return new IvyBlock(cell.x, cell.y, cell.z, material, faces, berries);
    }

    private double hash(Cell cell, int salt) {
        return hash(cell.x, cell.y, cell.z, salt);
    }

    private double hash(int x, int y, int z, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= x * 0x165667B19E3779F9L;
        h = Long.rotateLeft(h, 23) * 0x9E3779B97F4A7C15L;
        h ^= y * 0x27D4EB2F165667C5L;
        h = Long.rotateLeft(h, 29) * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Ruido de valor en 3D, suave, de 0 a 1. */
    private double noise(double x, double y, double z, double scale, int salt) {
        double fx = x / scale;
        double fy = y / scale;
        double fz = z / scale;
        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        int iz = (int) Math.floor(fz);
        double tx = smooth(fx - ix);
        double ty = smooth(fy - iy);
        double tz = smooth(fz - iz);
        double result = 0.0;
        for (int corner = 0; corner < 8; corner++) {
            int cx = corner & 1;
            int cy = (corner >> 1) & 1;
            int cz = (corner >> 2) & 1;
            double weight = (cx == 1 ? tx : 1.0 - tx) * (cy == 1 ? ty : 1.0 - ty) * (cz == 1 ? tz : 1.0 - tz);
            result += weight * hash(ix + cx, iy + cy, iz + cz, salt);
        }
        return result;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    /** Las 6 caras y las 12 aristas; el cuarto valor es 1 en las caras y 2 en las aristas. */
    private static int[][] moves() {
        List<int[]> moves = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    int axes = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (axes == 1 || axes == 2) {
                        moves.add(new int[]{x, y, z, axes});
                    }
                }
            }
        }
        return moves.toArray(int[][]::new);
    }

    private static final class Cell {
        static final int STEM = 0;
        static final int BRIDGE = 1;
        static final int HANG = 2;
        static final int CAVE = 3;

        final int x;
        final int y;
        final int z;
        final int support;
        final double distance;
        int kind;
        int visits = 1;
        boolean anchor;
        boolean hangTried;
        boolean strandEnd;
        int strandFaces;

        Cell(int x, int y, int z, int support, int kind, double distance) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.support = support;
            this.kind = kind;
            this.distance = distance;
        }
    }

    private static final class Tip {
        int x;
        int y;
        int z;
        final int depth;
        double hx;
        double hy;
        double hz;
        double energy;
        double distance;
        int floating;
        int stuck;
        int steps;
        long previous = Long.MIN_VALUE;
        long previous2 = Long.MIN_VALUE;

        Tip(int x, int y, int z, int depth) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.depth = depth;
        }

        void setHeading(double x, double y, double z) {
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length < 1.0E-9) {
                hx = 0.0;
                hy = 1.0;
                hz = 0.0;
                return;
            }
            hx = x / length;
            hy = y / length;
            hz = z / length;
        }
    }
}
