package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * De un objeto y su transformación a bloques del mundo. Lo usan los dos lados con el mismo código: el cliente para
 * dibujar el fantasma ({@link #orientPositions}, {@link #orientStates}) y el servidor para colocarlo al confirmar
 * ({@link Planner}). Así lo que se coloca es exactamente lo que se veía. Lógica pura.
 */
public final class GhostPlacement {
    private GhostPlacement() {
    }

    /** Cómo queda un estado de bloque con una orientación ({@code GhostStates} en el juego). */
    @FunctionalInterface
    public interface StateMapper {
        int map(int state, Orientation orientation);
    }

    /** El mundo en el destino, en el hilo del servidor. */
    public interface World {
        int stateAt(int x, int y, int z);

        boolean isAir(int state);

        /** Si el jugador puede construir ahí (protección del spawn, borde del mundo…). */
        boolean canBuild(int x, int y, int z);
    }

    /** Las posiciones locales giradas, aún sin desplazar (el cliente las dibuja y desplaza con una matriz). */
    public static long[] orientPositions(long[] local, Orientation orientation) {
        long[] result = new long[local.length];
        for (int index = 0; index < local.length; index++) {
            long packed = local[index];
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            result[index] = PackedBlockPlan.pack(orientation.x(x, y, z), orientation.y(x, y, z),
                    orientation.z(x, y, z));
        }
        return result;
    }

    /** Los estados girados (cada estado distinto se calcula una vez). */
    public static int[] orientStates(int[] states, Orientation orientation, StateMapper mapper) {
        int[] result = new int[states.length];
        Int2IntOpenHashMap cache = new Int2IntOpenHashMap();
        cache.defaultReturnValue(Integer.MIN_VALUE);
        for (int index = 0; index < states.length; index++) {
            int state = states[index];
            int mapped = cache.get(state);
            if (mapped == Integer.MIN_VALUE) {
                mapped = mapper.map(state, orientation);
                cache.put(state, mapped);
            }
            result[index] = mapped;
        }
        return result;
    }

    /**
     * Lo que se ve del objeto con esa transformación: cada bloque que no es aire en su posición del mundo y con su
     * estado girado.
     */
    public static Blocks shown(GhostModel model, GhostTransform transform, StateMapper mapper) {
        LongArrayList positions = new LongArrayList(model.size());
        IntArrayList states = new IntArrayList(model.size());
        long[] oriented = orientPositions(model.positions(), transform.orientation());
        int[] turned = orientStates(model.states(), transform.orientation(), mapper);
        for (int index = 0; index < model.size(); index++) {
            if (model.isAir(index)) {
                continue;
            }
            long local = oriented[index];
            positions.add(PackedBlockPlan.pack(transform.x() + PackedBlockPlan.unpackX(local),
                    transform.y() + PackedBlockPlan.unpackY(local), transform.z() + PackedBlockPlan.unpackZ(local)));
            states.add(turned[index]);
        }
        return new Blocks(positions.toLongArray(), states.toIntArray());
    }

    /** Posiciones del mundo y estados, en arrays paralelos. */
    public record Blocks(long[] positions, int[] states) {
        public int size() {
            return positions.length;
        }
    }

    /**
     * Las escrituras al confirmar, por tramos (el servidor reparte la lectura del destino entre ticks).
     *
     * @param positions posiciones del mundo
     * @param states    estado que se pone
     * @param expected  estado que había al confirmar: si cambia mientras se coloca, esa posición no se toca
     * @param captures  índice de captura de la instancia del Studio (o {@link PackedBlockPlan#NO_CAPTURE})
     * @param entries   entrada del modelo de la que sale cada escritura
     * @param kept      bloques que no se pisaron por «conservar lo que hay»
     * @param unchanged posiciones que ya tenían ese mismo bloque
     */
    public record Writes(long[] positions, int[] states, int[] expected, int[] captures, int[] entries, int kept,
                         int unchanged) {
        public int size() {
            return positions.length;
        }
    }

    /** Recorre el objeto entrada a entrada y decide qué se escribe en el destino. */
    public static final class Planner {
        private final GhostModel model;
        private final GhostTransform transform;
        private final GhostOptions options;
        private final StateMapper mapper;
        private final World world;
        private final Int2IntOpenHashMap turned = new Int2IntOpenHashMap();
        private final LongArrayList positions;
        private final IntArrayList states;
        private final IntArrayList expected;
        private final IntArrayList captures;
        private final IntArrayList entries;
        private int cursor;
        private int kept;
        private int unchanged;
        private long blockedAt = Long.MIN_VALUE;

        public Planner(GhostModel model, GhostTransform transform, GhostOptions options, StateMapper mapper,
                       World world) {
            this.model = model;
            this.transform = transform;
            this.options = options;
            this.mapper = mapper;
            this.world = world;
            int capacity = Math.min(model.size(), 1 << 20);
            positions = new LongArrayList(capacity);
            states = new IntArrayList(capacity);
            expected = new IntArrayList(capacity);
            captures = new IntArrayList(model.hasCaptures() ? capacity : 0);
            entries = new IntArrayList(capacity);
            turned.defaultReturnValue(Integer.MIN_VALUE);
        }

        /**
         * Avanza hasta {@code budget} entradas.
         *
         * @return {@code true} si ya terminó (o se paró en una posición protegida)
         */
        public boolean step(int budget) {
            int end = Math.min(model.size(), cursor + Math.max(1, budget));
            while (cursor < end && !blocked()) {
                int index = cursor++;
                boolean air = model.isAir(index);
                if (air && !options.pasteAir()) {
                    continue;
                }
                long local = model.position(index);
                int lx = PackedBlockPlan.unpackX(local);
                int ly = PackedBlockPlan.unpackY(local);
                int lz = PackedBlockPlan.unpackZ(local);
                int x = transform.worldX(lx, ly, lz);
                int y = transform.worldY(lx, ly, lz);
                int z = transform.worldZ(lx, ly, lz);
                if (!world.canBuild(x, y, z)) {
                    blockedAt = PackedBlockPlan.pack(x, y, z);
                    break;
                }
                int current = world.stateAt(x, y, z);
                if (options.keepExisting() && !world.isAir(current)) {
                    kept++;
                    continue;
                }
                int state = state(model.state(index));
                int capture = model.capture(index);
                if (state == current && capture == PackedBlockPlan.NO_CAPTURE) {
                    unchanged++;
                    continue;
                }
                positions.add(PackedBlockPlan.pack(x, y, z));
                states.add(state);
                expected.add(current);
                if (model.hasCaptures()) {
                    captures.add(capture);
                }
                entries.add(index);
            }
            return done();
        }

        private int state(int original) {
            int mapped = turned.get(original);
            if (mapped == Integer.MIN_VALUE) {
                mapped = mapper.map(original, transform.orientation());
                turned.put(original, mapped);
            }
            return mapped;
        }

        public boolean done() {
            return blocked() || cursor >= model.size();
        }

        /** Si se paró en una posición donde el jugador no puede construir. */
        public boolean blocked() {
            return blockedAt != Long.MIN_VALUE;
        }

        /** La posición protegida donde se paró (empaquetada). */
        public long blockedAt() {
            return blockedAt;
        }

        public int cursor() {
            return cursor;
        }

        public int total() {
            return model.size();
        }

        public Writes writes() {
            int[] captureArray = model.hasCaptures() ? captures.toIntArray() : new int[positions.size()];
            if (!model.hasCaptures()) {
                java.util.Arrays.fill(captureArray, PackedBlockPlan.NO_CAPTURE);
            }
            return new Writes(positions.toLongArray(), states.toIntArray(), expected.toIntArray(), captureArray,
                    entries.toIntArray(), kept, unchanged);
        }
    }
}
