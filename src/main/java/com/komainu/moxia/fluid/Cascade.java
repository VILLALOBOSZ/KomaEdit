package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.utility.BlockKinds;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Cascadas y ríos cortos que se ven bien sin inundar alrededor, sin Minecraft.
 *
 * <ul>
 *     <li>Desde un punto: una fuente metida en el bloque del clic (el borde de un saliente o una pared) sale por su
 *     lado abierto, cae y, abajo, entra en una poza: unas fuentes en el suelo, cerradas por el terreno, que se tragan
 *     la caída en lugar de dejar que se extienda. En pendiente la poza se hunde uno o dos bloques.</li>
 *     <li>Por la trayectoria: un cauce de un bloque de hondo a ras del terreno, que nunca sube (si el terreno sube,
 *     se corta), con orillas donde el terreno de al lado queda más bajo. En cada escalón el agua salta al tramo de
 *     abajo, y si pasa por encima de un vacío cae a una poza.</li>
 * </ul>
 *
 * Las corrientes salen de {@link FlowSim}, así que son las que haría el juego. Si alguna llega más allá de
 * {@code spread} bloques del cauce, de la caída o de la poza, el resultado lo dice y no se escribe nada.
 */
public final class Cascade {
    /** Lo más que se corta el terreno para que el cauce no suba. */
    public static final int MAX_CUT = 4;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Cascade() {
    }

    /**
     * @param pool       pozas donde la caída llega al suelo
     * @param poolRadius radio de la poza (0: un solo bloque)
     * @param spread     bloques que puede alejarse una corriente del cauce, la caída o la poza
     * @param width      ancho del cauce por la trayectoria
     * @param banks      orillas donde el terreno de al lado del cauce queda más bajo
     */
    public record Settings(FluidKind fluid, boolean pool, int poolRadius, int spread, int width, boolean banks) {
        public Settings {
            if (!fluid.flows()) {
                throw new IllegalArgumentException("La cascada es de agua o de lava");
            }
            if (poolRadius < 0 || poolRadius > 4) {
                throw new IllegalArgumentException("El radio de la poza va de 0 a 4");
            }
            if (spread < 0 || spread > 16) {
                throw new IllegalArgumentException("El margen va de 0 a 16 bloques");
            }
            if (width < 1 || width > 7) {
                throw new IllegalArgumentException("El ancho del cauce va de 1 a 7");
            }
        }
    }

    /**
     * Un derrame.
     *
     * @param at       la corriente más alejada del cauce (empaquetada) o por donde sale de la zona leída
     * @param outside  corrientes fuera del margen
     * @param escaped  si sale de la zona leída o crece más de la cuenta
     */
    public record Flood(long at, int outside, boolean escaped) {
    }

    /**
     * @param fall  bloques que cae el líquido (la columna más alta)
     * @param pools fuentes de las pozas
     */
    public record Result(FluidWrites writes, int sources, int flows, int fall, int pools, int banks, int cuts,
                         Flood flood) {
        public boolean floods() {
            return flood != null;
        }
    }

    /** Una cascada desde {@code (x, y, z)}: el bloque del clic, que pasa a ser la fuente. */
    public static Result fromPoint(RegionSnapshot snapshot, BlockKinds kinds, int x, int y, int z, Settings settings,
                                   FlowSim.Rules rules, int maxFlows, Runnable checkCancelled) {
        Plan plan = new Plan(snapshot, kinds, settings, rules, checkCancelled);
        FluidCells cells = plan.cells;
        if (!cells.contains(x, y, z)) {
            throw new IllegalArgumentException("El punto queda fuera de la zona leída");
        }
        int kind = cells.at(x, y, z);
        if (kind == FluidCells.OTHER) {
            throw new IllegalArgumentException("En " + x + ", " + y + ", " + z + " hay "
                    + (settings.fluid() == FluidKind.WATER ? "lava" : "agua") + ": saldría piedra");
        }
        if (kind == FluidCells.FULL) {
            throw new IllegalArgumentException("En " + x + ", " + y + ", " + z + " ya hay "
                    + settings.fluid().displayName() + ": haz clic en el borde de donde quieres que salga");
        }
        if (kinds.blockEntity(snapshot.rawId(x, y, z))) {
            throw new IllegalArgumentException("En " + x + ", " + y + ", " + z + " hay un bloque con datos (un cofre…)");
        }
        if (!plan.outlet(x, y, z)) {
            throw new IllegalArgumentException("La fuente en " + x + ", " + y + ", " + z + " no tiene por dónde salir:"
                    + " haz clic en el borde de un saliente o en una pared que dé al vacío");
        }
        plan.source(x, y, z);
        return plan.finish(maxFlows);
    }

    /** Un río corto con sus saltos por los puntos (al menos dos), del más alto al más bajo. */
    public static Result alongPath(RegionSnapshot snapshot, BlockKinds kinds, List<int[]> points, Settings settings,
                                   FlowSim.Rules rules, int maxFlows, Runnable checkCancelled) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("El río necesita al menos dos puntos");
        }
        List<int[]> ordered = new ArrayList<>(points);
        if (ordered.getLast()[1] > ordered.getFirst()[1]) {
            java.util.Collections.reverse(ordered);
        }
        Plan plan = new Plan(snapshot, kinds, settings, rules, checkCancelled);
        FluidCells cells = plan.cells;
        List<int[]> course = rasterize(ordered);
        // El nivel del agua por el cauce: nunca sube.
        int water = Integer.MAX_VALUE;
        int half = (settings.width() - 1) / 2;
        Long2IntLinkedOpenHashMap levels = new Long2IntLinkedOpenHashMap();
        Long2IntLinkedOpenHashMap materials = new Long2IntLinkedOpenHashMap();
        for (int[] column : course) {
            checkCancelled.run();
            int ground = plan.ground(column[0], column[2], column[1]);
            if (ground == Integer.MIN_VALUE) {
                continue;
            }
            if (ground - Math.min(water, ground) > MAX_CUT) {
                throw new IllegalArgumentException("En " + column[0] + ", " + column[2] + " el terreno sube "
                        + (ground - water) + " bloques: el agua no sube; pon un punto más alto o haz antes el cauce"
                        + " con /komaedit terreno arroyo");
            }
            water = Math.min(water, ground);
            int material = plan.material(column[0], water, column[2]);
            for (int dz = -half; dz <= half; dz++) {
                for (int dx = -half; dx <= half; dx++) {
                    if (dx * dx + dz * dz > half * half + half) {
                        continue;
                    }
                    long key = column(column[0] + dx, column[2] + dz);
                    if (!levels.containsKey(key) || levels.get(key) > water) {
                        levels.put(key, water);
                        materials.put(key, material);
                    }
                }
            }
        }
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("No hay suelo bajo la trayectoria");
        }
        for (var entry : levels.long2IntEntrySet()) {
            int x = (int) (entry.getLongKey() >> 32);
            int z = (int) entry.getLongKey();
            int level = entry.getIntValue();
            plan.corridor.add(entry.getLongKey());
            int ground = plan.ground(x, z, level);
            if (ground == Integer.MIN_VALUE || ground < level - 3) {
                continue;
            }
            int bottom = Math.min(level, ground + 1);
            for (int y = bottom; y <= level; y++) {
                plan.source(x, y, z);
            }
            for (int y = level + 1; y <= ground; y++) {
                plan.cut(x, y, z);
            }
            if (cells.contains(x, bottom - 1, z) && plan.open(x, bottom - 1, z)) {
                plan.bank(x, bottom - 1, z, materials.get(entry.getLongKey()));
            }
        }
        if (settings.banks()) {
            for (int index : plan.sources.toIntArray()) {
                int x = cells.x(index);
                int y = cells.y(index);
                int z = cells.z(index);
                for (int[] side : SIDES) {
                    int nx = x + side[0];
                    int nz = z + side[1];
                    long key = column(nx, nz);
                    // Donde el cauce sigue más abajo el agua salta: ahí no va orilla.
                    if (levels.containsKey(key) && levels.get(key) <= y || !cells.contains(nx, y, nz)
                            || plan.taken(nx, y, nz) || !plan.open(nx, y, nz)) {
                        continue;
                    }
                    plan.bank(nx, y, nz, materials.get(column(x, z)));
                }
            }
        }
        return plan.finish(maxFlows);
    }

    /** Las columnas de la trayectoria sin pasos en diagonal (el agua no corre en diagonal), con su altura. */
    static List<int[]> rasterize(List<int[]> points) {
        List<int[]> columns = new ArrayList<>();
        for (int segment = 0; segment + 1 < points.size(); segment++) {
            int[] from = points.get(segment);
            int[] to = points.get(segment + 1);
            int x = from[0];
            int z = from[2];
            int stepX = Integer.signum(to[0] - x);
            int stepZ = Integer.signum(to[2] - z);
            int total = Math.max(1, Math.abs(to[0] - x) + Math.abs(to[2] - z));
            int walked = 0;
            add(columns, x, z, from[1]);
            while (x != to[0] || z != to[2]) {
                // Un paso cada vez, en x o en z: el que deja la columna más cerca de la línea.
                if (x == to[0]) {
                    z += stepZ;
                } else if (z == to[2]) {
                    x += stepX;
                } else {
                    double alongX = offLine(from, to, x + stepX, z);
                    double alongZ = offLine(from, to, x, z + stepZ);
                    if (alongX <= alongZ) {
                        x += stepX;
                    } else {
                        z += stepZ;
                    }
                }
                walked++;
                add(columns, x, z, (int) Math.round(from[1] + (to[1] - from[1]) * (walked / (double) total)));
            }
        }
        return columns;
    }

    /** Lo lejos (sin escala) que queda {@code (x, z)} de la línea entre dos puntos. */
    private static double offLine(int[] from, int[] to, int x, int z) {
        return Math.abs((x - from[0]) * (double) (to[2] - from[2]) - (z - from[2]) * (double) (to[0] - from[0]));
    }

    private static void add(List<int[]> columns, int x, int z, int y) {
        if (!columns.isEmpty() && columns.getLast()[0] == x && columns.getLast()[2] == z) {
            return;
        }
        columns.add(new int[]{x, y, z});
    }

    private static long column(int x, int z) {
        return (long) x << 32 | (z & 0xFFFF_FFFFL);
    }

    /** Lo que se va a escribir y el cálculo de las corrientes. */
    private static final class Plan {
        final RegionSnapshot snapshot;
        final BlockKinds kinds;
        final Settings settings;
        final FlowSim.Rules rules;
        final Runnable checkCancelled;
        final FluidCells cells;
        final IntLinkedOpenHashSet sources = new IntLinkedOpenHashSet();
        final IntLinkedOpenHashSet cuts = new IntLinkedOpenHashSet();
        final Int2IntLinkedOpenHashMap banks = new Int2IntLinkedOpenHashMap();
        final LongOpenHashSet corridor = new LongOpenHashSet();
        int pools;

        Plan(RegionSnapshot snapshot, BlockKinds kinds, Settings settings, FlowSim.Rules rules,
             Runnable checkCancelled) {
            this.snapshot = snapshot;
            this.kinds = kinds;
            this.settings = settings;
            this.rules = rules;
            this.checkCancelled = checkCancelled;
            this.cells = new FluidCells(snapshot, kinds, settings.fluid(), false);
        }

        /** Hueco por donde corre el líquido: aire, lo que se lleva, una corriente suya o un corte. */
        boolean open(int x, int y, int z) {
            if (!cells.contains(x, y, z)) {
                return false;
            }
            int index = cells.index(x, y, z);
            if (banks.containsKey(index) || sources.contains(index)) {
                return false;
            }
            if (cuts.contains(index)) {
                return true;
            }
            int kind = cells.at(index);
            return kind == FluidCells.EMPTY || kind == FluidCells.FLOW;
        }

        /** El primer bloque de pared de la columna desde {@code y} hacia abajo (para las orillas), o -1. */
        int material(int x, int y, int z) {
            for (int below = y; below >= Math.max(cells.minY, y - 4); below--) {
                if (cells.at(x, below, z) == FluidCells.WALL && !kinds.blockEntity(snapshot.rawId(x, below, z))) {
                    return snapshot.rawId(x, below, z);
                }
            }
            return -1;
        }

        boolean taken(int x, int y, int z) {
            int index = cells.index(x, y, z);
            return sources.contains(index) || cuts.contains(index) || banks.containsKey(index);
        }

        /** Si de {@code (x, y, z)} el líquido puede salir de lado o hacia abajo. */
        boolean outlet(int x, int y, int z) {
            if (open(x, y - 1, z)) {
                return true;
            }
            for (int[] side : SIDES) {
                if (open(x + side[0], y, z + side[1])) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Lo alto del suelo en la columna cerca de {@code around}: el primer bloque que no deja pasar el líquido (o
         * el líquido que ya hay) con hueco encima, bajando desde unos bloques más arriba.
         */
        int ground(int x, int z, int around) {
            int top = Math.min(around + MAX_CUT + 1, cells.minY + cells.sizeY - 2);
            int bottom = Math.max(around - 12, cells.minY);
            for (int y = top; y >= bottom; y--) {
                if (!cells.contains(x, y, z)) {
                    return Integer.MIN_VALUE;
                }
                int kind = cells.at(x, y, z);
                boolean floor = kind == FluidCells.WALL || kind == FluidCells.FULL || kind == FluidCells.OTHER;
                int above = cells.at(x, y + 1, z);
                if (floor && (above == FluidCells.EMPTY || above == FluidCells.FLOW)) {
                    return y;
                }
            }
            return Integer.MIN_VALUE;
        }

        void source(int x, int y, int z) {
            if (!cells.contains(x, y, z)) {
                return;
            }
            int kind = cells.at(x, y, z);
            if (kind == FluidCells.FULL || kind == FluidCells.OTHER
                    || kinds.blockEntity(snapshot.rawId(x, y, z))) {
                return;
            }
            int index = cells.index(x, y, z);
            cuts.remove(index);
            banks.remove(index);
            sources.add(index);
            corridor.add(column(x, z));
        }

        void cut(int x, int y, int z) {
            if (!cells.contains(x, y, z) || cells.at(x, y, z) != FluidCells.WALL
                    || kinds.blockEntity(snapshot.rawId(x, y, z))) {
                return;
            }
            int index = cells.index(x, y, z);
            if (!sources.contains(index)) {
                cuts.add(index);
                corridor.add(column(x, z));
            }
        }

        void bank(int x, int y, int z, int rawId) {
            if (rawId < 0 || !cells.contains(x, y, z) || taken(x, y, z)) {
                return;
            }
            banks.put(cells.index(x, y, z), rawId);
        }

        private FlowSim simulation() {
            FlowSim sim = new FlowSim(snapshot, kinds, settings.fluid(), rules, checkCancelled);
            for (int index : cuts) {
                sim.cut(cells.x(index), cells.y(index), cells.z(index));
            }
            for (var entry : banks.int2IntEntrySet()) {
                int index = entry.getIntKey();
                sim.bank(cells.x(index), cells.y(index), cells.z(index));
            }
            for (int index : sources) {
                sim.source(cells.x(index), cells.y(index), cells.z(index));
            }
            return sim;
        }

        Result finish(int maxFlows) {
            FlowSim.Result result = simulation().run(maxFlows);
            if (settings.pool()) {
                // Una poza cada vez, en la caída más alta que toca suelo: abajo puede que el agua ya no llegue.
                LongOpenHashSet tried = new LongOpenHashSet();
                for (int round = 0; round < 6; round++) {
                    int best = -1;
                    for (int entry = 0; entry < result.size(); entry++) {
                        if (result.falling(entry) && landing(result.x(entry), result.y(entry), result.z(entry))
                                && !tried.contains(PackedBlockPlan.pack(result.x(entry), result.y(entry),
                                result.z(entry))) && (best < 0 || result.y(entry) > result.y(best))) {
                            best = entry;
                        }
                    }
                    if (best < 0) {
                        break;
                    }
                    tried.add(PackedBlockPlan.pack(result.x(best), result.y(best), result.z(best)));
                    if (pool(result.x(best), result.y(best), result.z(best))) {
                        result = simulation().run(maxFlows);
                    }
                }
            }
            FluidWrites writes = new FluidWrites(settings.fluid());
            for (int index : cuts) {
                writes.air(cells.x(index), cells.y(index), cells.z(index));
            }
            for (var entry : banks.int2IntEntrySet()) {
                int index = entry.getIntKey();
                writes.block(cells.x(index), cells.y(index), cells.z(index), entry.getIntValue());
            }
            int flows = 0;
            int highest = 0;
            Long2IntLinkedOpenHashMap falls = new Long2IntLinkedOpenHashMap();
            for (int entry = 0; entry < result.size(); entry++) {
                int x = result.x(entry);
                int y = result.y(entry);
                int z = result.z(entry);
                int level = result.level(entry);
                if (level < 0) {
                    writes.air(x, y, z);
                    continue;
                }
                writes.fluid(x, y, z, level);
                if (result.flowing(entry)) {
                    flows++;
                }
                if (result.falling(entry)) {
                    corridor.add(column(x, z));
                    long key = column(x, z);
                    falls.put(key, falls.getOrDefault(key, 0) + 1);
                    highest = Math.max(highest, falls.get(key));
                }
            }
            Flood flood = flood(result);
            return new Result(writes, sources.size() - pools, flows, highest, pools, banks.size(), cuts.size(),
                    flood);
        }

        /** Si la columna que cae llega ahí al suelo (debajo no hay hueco ni líquido). */
        private boolean landing(int x, int y, int z) {
            if (!cells.contains(x, y - 1, z)) {
                return false;
            }
            int below = cells.index(x, y - 1, z);
            int kind = cells.at(below);
            return !sources.contains(below) && !cuts.contains(below)
                    && (kind == FluidCells.WALL || kind == FluidCells.LOGGABLE);
        }

        /**
         * La poza bajo una caída que llega al suelo en {@code (x, y - 1, z)}: el disco más grande (hasta su radio)
         * que queda cerrado por el terreno por los lados y por debajo; en pendiente se hunde uno o dos bloques.
         */
        private boolean pool(int x, int y, int z) {
            int ground = y - 1;
            for (int sink = 0; sink <= 2; sink++) {
                int level = ground - sink;
                for (int radius = settings.poolRadius(); radius >= 0; radius--) {
                    List<int[]> disc = disc(x, level, z, radius);
                    if (disc != null && contained(disc)) {
                        for (int cut = level + 1; cut <= ground; cut++) {
                            cut(x, cut, z);
                        }
                        for (int[] cell : disc) {
                            int index = cells.index(cell[0], cell[1], cell[2]);
                            if (!sources.contains(index)) {
                                source(cell[0], cell[1], cell[2]);
                                if (sources.contains(index)) {
                                    pools++;
                                }
                            }
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        private List<int[]> disc(int x, int y, int z, int radius) {
            List<int[]> disc = new ArrayList<>();
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dz * dz > radius * radius + radius) {
                        continue;
                    }
                    if (!cells.contains(x + dx, y, z + dz)) {
                        return null;
                    }
                    int kind = cells.at(x + dx, y, z + dz);
                    if (kind == FluidCells.OTHER || kinds.blockEntity(snapshot.rawId(x + dx, y, z + dz))) {
                        return null;
                    }
                    disc.add(new int[]{x + dx, y, z + dz});
                }
            }
            return disc;
        }

        /** Si las fuentes de la poza quedan cerradas: a los lados y debajo, nada por donde salir. */
        private boolean contained(List<int[]> disc) {
            LongOpenHashSet inside = new LongOpenHashSet();
            for (int[] cell : disc) {
                inside.add(PackedBlockPlan.pack(cell[0], cell[1], cell[2]));
            }
            int[][] outlets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
            for (int[] cell : disc) {
                for (int[] outlet : outlets) {
                    int nx = cell[0] + outlet[0];
                    int ny = cell[1] + outlet[1];
                    int nz = cell[2] + outlet[2];
                    if (inside.contains(PackedBlockPlan.pack(nx, ny, nz))) {
                        continue;
                    }
                    if (!cells.contains(nx, ny, nz) || open(nx, ny, nz)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Las corrientes que se alejan más de la cuenta del cauce, la caída o la poza. */
        private Flood flood(FlowSim.Result result) {
            if (result.overflow()) {
                int exit = result.exit();
                long at = exit >= 0 ? PackedBlockPlan.pack(cells.x(exit), cells.y(exit), cells.z(exit)) : 0L;
                return new Flood(at, result.flows(), true);
            }
            LongOpenHashSet allowed = new LongOpenHashSet();
            int spread = settings.spread();
            for (long key : corridor) {
                int x = (int) (key >> 32);
                int z = (int) key;
                for (int dz = -spread; dz <= spread; dz++) {
                    for (int dx = -spread; dx <= spread; dx++) {
                        allowed.add(column(x + dx, z + dz));
                    }
                }
            }
            int outside = 0;
            long farthest = 0L;
            for (int entry = 0; entry < result.size(); entry++) {
                if (!result.flowing(entry) || allowed.contains(column(result.x(entry), result.z(entry)))) {
                    continue;
                }
                if (outside++ == 0) {
                    farthest = PackedBlockPlan.pack(result.x(entry), result.y(entry), result.z(entry));
                }
            }
            return outside == 0 ? null : new Flood(farthest, outside, false);
        }
    }
}
