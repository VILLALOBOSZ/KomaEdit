package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.utility.BlockKinds;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Rellenar un hueco con agua o lava hasta un nivel, como el Floodfill de Axiom, sin Minecraft.
 *
 * <p>Desde el clic el líquido se extiende como en el juego: de lado y hacia abajo (nunca hacia arriba) y, si se
 * pide, también en diagonal. Solo se llena si el hueco está cerrado por los lados y por debajo hasta ese nivel: si
 * el líquido llegara al borde de la zona leída, al fondo del mundo o a más bloques que el límite, no se toca nada y
 * el resultado dice por dónde se escapa (lo más estrecho del camino de salida, comprobado tapándolo) y hasta qué
 * altura sí cerraría.</p>
 */
public final class FloodFill {
    /** Radio de la sección con que se mide lo estrecho de cada paso del camino de salida. */
    private static final int SLICE_RADIUS = 3;
    /** Sitios estrechos que se prueban a tapar, como mucho. */
    private static final int PLUG_TRIES = 8;
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] CORNERS = {{1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}};

    private FloodFill() {
    }

    /** Hacia dónde se extiende el relleno. Lo que no se extiende tiene que estar cerrado igual. */
    public record Directions(boolean horizontal, boolean down, boolean corners) {
        public static final Directions DEFAULT = new Directions(true, true, false);

        public Directions {
            if (!horizontal && !down) {
                throw new IllegalArgumentException("El relleno tiene que ir de lado, hacia abajo o las dos cosas");
            }
        }

        /**
         * {@code h} de lado, {@code b} hacia abajo y {@code e} en las esquinas, juntas ({@code hbe}) o como palabras
         * separadas por comas ({@code horizontal,abajo,esquinas}).
         */
        public static Directions parse(String text) {
            boolean horizontal = false;
            boolean down = false;
            boolean corners = false;
            for (String token : text.toLowerCase(Locale.ROOT).split(",")) {
                String word = token.strip();
                switch (word) {
                    case "horizontal", "lados", "lado", "sides" -> horizontal = true;
                    case "abajo", "down", "bajar" -> down = true;
                    case "esquinas", "diagonal", "diagonales", "corners" -> corners = true;
                    default -> {
                        for (char letter : word.toCharArray()) {
                            switch (letter) {
                                case 'h' -> horizontal = true;
                                case 'b', 'a' -> down = true;
                                case 'e', 'd' -> corners = true;
                                default -> throw new IllegalArgumentException("Las direcciones son h (de lado), b"
                                        + " (hacia abajo) y e (esquinas), no «" + text + "»");
                            }
                        }
                    }
                }
            }
            return new Directions(horizontal, down, corners);
        }

        /** Las letras de {@link #parse}. */
        public String letters() {
            return (horizontal ? "h" : "") + (down ? "b" : "") + (corners ? "e" : "");
        }
    }

    /** Hasta dónde se llena. */
    public enum LevelMode {
        /** La altura del clic. */
        CLICK,
        /** Una altura dada. */
        FIXED,
        /** La más alta a la que el hueco sigue cerrado. */
        BRIM
    }

    public record Level(LevelMode mode, int y) {
        public static final Level CLICK = new Level(LevelMode.CLICK, 0);
        public static final Level BRIM = new Level(LevelMode.BRIM, 0);

        public static Level fixed(int y) {
            return new Level(LevelMode.FIXED, y);
        }
    }

    /**
     * @param limit       bloques que puede tener el hueco como mucho
     * @param waterlog    anegar lo que lo admite (solo agua)
     * @param worldBottom la altura más baja del mundo, para decir «se va por el fondo del mundo»
     */
    public record Settings(FluidKind fluid, Level level, Directions directions, int limit, boolean waterlog,
                           int worldBottom) {
        public Settings {
            if (!fluid.flows()) {
                throw new IllegalArgumentException("El relleno es de agua o de lava");
            }
            if (limit < 1) {
                throw new IllegalArgumentException("El límite tiene que ser de al menos un bloque");
            }
        }
    }

    /** Por qué no cierra el hueco. */
    public enum Reason {
        /** Llega al borde de la zona leída. */
        BOUNDS,
        /** Baja más de lo que se leyó. */
        DEEP,
        /** Llega al fondo del mundo. */
        BOTTOM,
        /** Tiene más bloques que el límite. */
        LIMIT,
        /** Hay hueco debajo y el relleno no baja. */
        BELOW,
        /** Hay hueco a un lado y el relleno no va de lado. */
        SIDE
    }

    /**
     * Una fuga.
     *
     * @param exit          donde se perdió el líquido (empaquetado con {@link PackedBlockPlan#pack})
     * @param hole          los bloques por donde se escapa: lo más estrecho del camino de salida (vacío si no hay
     *                      ningún sitio estrecho)
     * @param pluggable     si tapando {@code hole} el hueco quedaría cerrado
     * @param spillLevel    altura con la que se buscó la fuga: la primera que se escapa
     * @param highestClosed la altura más alta a la que sí cierra, o {@link Integer#MIN_VALUE} si ninguna
     * @param path          el camino del líquido del clic a la salida, con pocos puntos (para dibujarlo)
     */
    public record Leak(Reason reason, long exit, long[] hole, boolean pluggable, int spillLevel, int highestClosed,
                       long[] path) {
        public boolean closesSomewhere() {
            return highestClosed != Integer.MIN_VALUE;
        }
    }

    /** Destino de las escrituras. */
    public interface Sink {
        /** Pone una fuente del líquido. */
        void source(int x, int y, int z);

        /** Anega el bloque que hay. */
        void waterlog(int x, int y, int z);
    }

    /**
     * @param filled      huecos y corrientes que pasan a ser fuente
     * @param waterlogged bloques que se anegan
     * @param already     posiciones que ya estaban llenas
     * @param contacts    bloques del otro líquido que toca (saldría piedra u obsidiana)
     */
    public record Result(int level, int filled, int waterlogged, int already, int contacts, Leak leak,
                         int[] region, FluidCells cells) {
        public boolean closed() {
            return leak == null;
        }

        public int size() {
            return region == null ? 0 : region.length;
        }

        /** Escribe el relleno (nada si se escapa). */
        public void write(Sink sink) {
            if (region == null) {
                return;
            }
            for (int index : region) {
                int x = cells.x(index);
                int y = cells.y(index);
                int z = cells.z(index);
                switch (cells.at(x, y, z)) {
                    case FluidCells.EMPTY, FluidCells.FLOW -> sink.source(x, y, z);
                    case FluidCells.LOGGABLE -> sink.waterlog(x, y, z);
                    default -> {
                    }
                }
            }
        }
    }

    /**
     * Calcula el relleno desde el hueco {@code (x, y, z)}.
     *
     * @throws IllegalArgumentException si el punto no es un hueco o el nivel no se alcanza desde él
     */
    public static Result plan(RegionSnapshot snapshot, BlockKinds kinds, int x, int y, int z, Settings settings,
                              Runnable checkCancelled) {
        FluidCells cells = new FluidCells(snapshot, kinds, settings.fluid(), settings.waterlog());
        if (!cells.contains(x, y, z)) {
            throw new IllegalArgumentException("El punto queda fuera de la zona leída");
        }
        if (!FluidCells.open(cells.at(x, y, z))) {
            throw new IllegalArgumentException("En " + x + ", " + y + ", " + z + " hay un bloque: haz clic en el hueco"
                    + " que quieres llenar");
        }
        int bottom = y;
        while (cells.contains(x, bottom - 1, z) && FluidCells.open(cells.at(x, bottom - 1, z))) {
            bottom--;
        }
        int top = y;
        while (cells.contains(x, top + 1, z) && FluidCells.open(cells.at(x, top + 1, z))) {
            top++;
        }
        Search search = new Search(cells, settings, checkCancelled);
        int level = switch (settings.level().mode()) {
            case CLICK -> y;
            case FIXED -> settings.level().y();
            case BRIM -> {
                int highest = search.highestClosed(x, z, bottom, top);
                yield highest == Integer.MIN_VALUE ? bottom : highest;
            }
        };
        if (level > top) {
            throw new IllegalArgumentException("Encima del clic hay un bloque en y=" + (top + 1) + ": desde ahí no se"
                    + " puede llenar hasta y=" + level);
        }
        if (level < bottom) {
            throw new IllegalArgumentException("El nivel y=" + level + " queda por debajo del fondo del hueco (y="
                    + bottom + ")");
        }
        Outcome outcome = search.run(x, level, z, null);
        if (outcome.closed()) {
            int filled = 0;
            int waterlogged = 0;
            int already = 0;
            for (int index : outcome.order) {
                switch (cells.at(index)) {
                    case FluidCells.EMPTY, FluidCells.FLOW -> filled++;
                    case FluidCells.LOGGABLE -> waterlogged++;
                    default -> already++;
                }
            }
            return new Result(level, filled, waterlogged, already, outcome.contacts, null,
                    outcome.order.toIntArray(), cells);
        }
        // Se escapa: hasta dónde cerraría y, con la primera altura que se escapa, por dónde. Sin bajar, cada capa va
        // por su cuenta: la fuga es la de la altura pedida.
        int highest = level > bottom ? search.highestClosed(x, z, bottom, level - 1) : Integer.MIN_VALUE;
        int spillLevel = highest == Integer.MIN_VALUE || !settings.directions().down() ? level : highest + 1;
        Outcome spill = spillLevel == level ? outcome : search.run(x, spillLevel, z, null);
        Leak leak = search.locate(x, spillLevel, z, spill, highest);
        return new Result(level, 0, 0, 0, outcome.contacts, leak, null, cells);
    }

    /** Un recorrido del líquido: en qué orden se llenó cada posición y de dónde vino. */
    private static final class Outcome {
        final IntArrayList order = new IntArrayList();
        final Int2IntOpenHashMap parent = new Int2IntOpenHashMap();
        int contacts;
        Reason reason;
        /** La posición donde se perdió el líquido. */
        int exit = -1;

        Outcome() {
            parent.defaultReturnValue(-2);
        }

        boolean closed() {
            return reason == null;
        }
    }

    /** Un tramo estrecho del camino de salida: sus posiciones en la sección y dónde está en el camino. */
    private record Narrow(int step, int[] cells) {
    }

    private static final class Search {
        private final FluidCells cells;
        private final Settings settings;
        private final Directions directions;
        private final Runnable checkCancelled;

        Search(FluidCells cells, Settings settings, Runnable checkCancelled) {
            this.cells = cells;
            this.settings = settings;
            this.directions = settings.directions();
            this.checkCancelled = checkCancelled;
        }

        /** Llena desde {@code (x, level, z)}; {@code walls} son posiciones que se tratan como tapadas. */
        Outcome run(int x, int level, int z, IntOpenHashSet walls) {
            Outcome outcome = new Outcome();
            IntOpenHashSet touched = new IntOpenHashSet();
            int seed = cells.index(x, level, z);
            outcome.parent.put(seed, -1);
            outcome.order.add(seed);
            IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
            queue.enqueue(seed);
            while (!queue.isEmpty()) {
                if ((outcome.order.size() & 1_023) == 0) {
                    checkCancelled.run();
                }
                int current = queue.dequeueInt();
                int cx = cells.x(current);
                int cy = cells.y(current);
                int cz = cells.z(current);
                if (step(outcome, queue, walls, touched, current, cx, cy - 1, cz, true, directions.down())) {
                    break;
                }
                boolean stop = false;
                for (int[] side : SIDES) {
                    if (step(outcome, queue, walls, touched, current, cx + side[0], cy, cz + side[2], false,
                            directions.horizontal())) {
                        stop = true;
                        break;
                    }
                }
                if (!stop && directions.corners()) {
                    for (int[] corner : CORNERS) {
                        if (step(outcome, queue, walls, touched, current, cx + corner[0], cy, cz + corner[2], false,
                                directions.horizontal())) {
                            stop = true;
                            break;
                        }
                    }
                }
                if (stop) {
                    break;
                }
            }
            outcome.contacts = touched.size();
            return outcome;
        }

        /** Mira un vecino. Devuelve {@code true} si el líquido se escapa por ahí (y lo apunta en {@code outcome}). */
        private boolean step(Outcome outcome, IntArrayFIFOQueue queue, IntOpenHashSet walls, IntOpenHashSet touched,
                             int current, int x, int y, int z, boolean below, boolean spreads) {
            if (!cells.contains(x, y, z)) {
                outcome.exit = current;
                outcome.reason = y < cells.minY ? (cells.minY <= settings.worldBottom() ? Reason.BOTTOM : Reason.DEEP)
                        : Reason.BOUNDS;
                return true;
            }
            int next = cells.index(x, y, z);
            if (walls != null && walls.contains(next)) {
                return false;
            }
            int kind = cells.at(x, y, z);
            if (!FluidCells.open(kind)) {
                if (kind == FluidCells.OTHER) {
                    touched.add(next);
                }
                return false;
            }
            if (outcome.parent.containsKey(next)) {
                return false;
            }
            outcome.parent.put(next, current);
            if (!spreads) {
                outcome.exit = next;
                outcome.reason = below ? Reason.BELOW : Reason.SIDE;
                return true;
            }
            outcome.order.add(next);
            if (outcome.order.size() > settings.limit()) {
                outcome.exit = next;
                outcome.reason = Reason.LIMIT;
                return true;
            }
            queue.enqueue(next);
            return false;
        }

        /**
         * La altura más alta entre {@code low} y {@code high} a la que el hueco cierra, o
         * {@link Integer#MIN_VALUE}. Bajando, una altura que se escapa hace escaparse a todas las de encima (lo de
         * más abajo queda dentro de lo de más arriba), así que basta una búsqueda binaria; sin bajar cada capa va
         * por su cuenta.
         */
        int highestClosed(int x, int z, int low, int high) {
            if (high < low) {
                return Integer.MIN_VALUE;
            }
            if (!directions.down()) {
                for (int level = high; level >= low; level--) {
                    if (run(x, level, z, null).closed()) {
                        return level;
                    }
                }
                return Integer.MIN_VALUE;
            }
            if (!run(x, low, z, null).closed()) {
                return Integer.MIN_VALUE;
            }
            int closed = low;
            int open = high + 1;
            while (open - closed > 1) {
                int middle = (closed + open) >>> 1;
                if (run(x, middle, z, null).closed()) {
                    closed = middle;
                } else {
                    open = middle;
                }
            }
            return closed;
        }

        /** Busca por dónde se escapa: lo más estrecho del camino de salida que, tapado, cierra el hueco. */
        Leak locate(int x, int level, int z, Outcome spill, int highest) {
            IntArrayList path = new IntArrayList();
            for (int index = spill.exit; index >= 0; index = spill.parent.get(index)) {
                path.add(index);
            }
            java.util.Collections.reverse(path);
            List<Narrow> narrows = new ArrayList<>();
            for (int step = 1; step < path.size(); step++) {
                int[] slice = slice(path.getInt(step), path.getInt(step - 1), level);
                if (slice != null) {
                    narrows.add(new Narrow(step, slice));
                }
            }
            narrows.sort(Comparator.comparingInt((Narrow narrow) -> narrow.cells().length)
                    .thenComparingInt(Narrow::step));
            List<Narrow> tries = new ArrayList<>();
            IntOpenHashSet covered = new IntOpenHashSet();
            for (Narrow narrow : narrows) {
                if (tries.size() >= PLUG_TRIES) {
                    break;
                }
                if (covered.contains(path.getInt(narrow.step()))) {
                    continue;
                }
                tries.add(narrow);
                covered.addAll(IntArrayList.wrap(narrow.cells()));
            }
            // Tapar cada sitio estrecho: el que deja cerrado casi todo lo que cerraría el mejor y queda antes en el
            // camino es el hueco (lo que hay detrás de él ya es «fuera»).
            Narrow best = null;
            int bestVolume = -1;
            int[] volumes = new int[tries.size()];
            for (int index = 0; index < tries.size(); index++) {
                Outcome plugged = run(x, level, z, new IntOpenHashSet(tries.get(index).cells()));
                volumes[index] = plugged.closed() ? plugged.order.size() : -1;
                bestVolume = Math.max(bestVolume, volumes[index]);
            }
            if (bestVolume > 0) {
                for (int index = 0; index < tries.size(); index++) {
                    if (volumes[index] >= bestVolume * 0.9
                            && (best == null || tries.get(index).step() < best.step())) {
                        best = tries.get(index);
                    }
                }
            }
            boolean pluggable = best != null;
            if (best == null && !tries.isEmpty()) {
                best = tries.getFirst();
            }
            long[] hole = best == null ? new long[0] : pack(best.cells());
            return new Leak(spill.reason, pack(spill.exit), hole, pluggable, level, highest, sample(path, 64));
        }

        /**
         * La sección del camino en {@code index}, perpendicular al paso desde {@code previous}: las posiciones
         * abiertas unidas a ella hasta {@value #SLICE_RADIUS} bloques. {@code null} si llega a ese radio (es ancha,
         * no es un hueco).
         */
        private int[] slice(int index, int previous, int level) {
            int x = cells.x(index);
            int y = cells.y(index);
            int z = cells.z(index);
            int dx = x - cells.x(previous);
            int dy = y - cells.y(previous);
            int dz = z - cells.z(previous);
            // Ejes de la sección: horizontal si se baja; si no, vertical y de lado al paso.
            int[] u;
            int[] v;
            if (dy != 0) {
                u = new int[]{1, 0, 0};
                v = new int[]{0, 0, 1};
            } else if (Math.abs(dx) >= Math.abs(dz)) {
                u = new int[]{0, 0, 1};
                v = new int[]{0, 1, 0};
            } else {
                u = new int[]{1, 0, 0};
                v = new int[]{0, 1, 0};
            }
            IntArrayList found = new IntArrayList();
            IntOpenHashSet seen = new IntOpenHashSet();
            IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
            // Coordenadas de la sección (a, b) desde el centro, guardadas en un entero.
            queue.enqueue(0);
            seen.add(0);
            while (!queue.isEmpty()) {
                int key = queue.dequeueInt();
                int a = (byte) (key >> 8);
                int b = (byte) key;
                int px = x + u[0] * a + v[0] * b;
                int py = y + u[1] * a + v[1] * b;
                int pz = z + u[2] * a + v[2] * b;
                if (py > level || !cells.contains(px, py, pz) || !FluidCells.open(cells.at(px, py, pz))) {
                    continue;
                }
                if (Math.abs(a) >= SLICE_RADIUS || Math.abs(b) >= SLICE_RADIUS) {
                    return null;
                }
                found.add(cells.index(px, py, pz));
                int[][] around = {{a + 1, b}, {a - 1, b}, {a, b + 1}, {a, b - 1}};
                for (int[] next : around) {
                    int nextKey = (next[0] & 0xFF) << 8 | (next[1] & 0xFF);
                    if (seen.add(nextKey)) {
                        queue.enqueue(nextKey);
                    }
                }
            }
            return found.toIntArray();
        }

        private long pack(int index) {
            return PackedBlockPlan.pack(cells.x(index), cells.y(index), cells.z(index));
        }

        private long[] pack(int[] indices) {
            long[] packed = new long[indices.length];
            for (int index = 0; index < indices.length; index++) {
                packed[index] = pack(indices[index]);
            }
            return packed;
        }

        /** Como mucho {@code points} puntos del camino, repartidos y con el primero y el último. */
        private long[] sample(IntArrayList path, int points) {
            int count = Math.min(points, path.size());
            long[] sampled = new long[count];
            for (int index = 0; index < count; index++) {
                int at = count == 1 ? 0 : (int) Math.round(index * (path.size() - 1) / (double) (count - 1));
                sampled[index] = pack(path.getInt(at));
            }
            return sampled;
        }
    }
}
