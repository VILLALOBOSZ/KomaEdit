package com.komainu.moxia.fluid;

import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.utility.BlockKinds;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Cómo corren el agua o la lava desde unas fuentes hasta quedarse quietas, con las reglas de
 * {@code FlowableFluid} de Minecraft y sin el juego: cae si puede; si no (o si es una fuente) se extiende de lado
 * perdiendo un nivel por bloque (la lava del mundo normal, dos) y prefiere los lados que llevan a un desnivel cerca
 * (a 4 bloques el agua, a 2 la lava); un bloque bajo otro líquido es una cascada (nivel 8) y dos fuentes de agua
 * junto a un hueco con suelo forman otra.
 *
 * <p>Se simula por turnos sobre la foto, como los ticks del juego, y solo donde algo cambia: lo que ya estaba lejos
 * no se toca. Si el líquido saldría de la zona leída o crecería más de la cuenta, se para y lo dice: es un
 * derrame.</p>
 */
public final class FlowSim {
    static final int NONE = 0;
    private static final int AMOUNT = 15;
    static final int FALLING = 16;
    static final int SOURCE = 32;
    /** Fuente que no se toca: lo anegado, las algas y las columnas de burbujas. */
    static final int FIXED = 64;
    private static final byte CUT = 1;
    private static final byte BANK = 2;
    /** Norte, este, sur y oeste, el orden de Minecraft. */
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /**
     * @param decrease  niveles que pierde por bloque de lado
     * @param holeReach hasta dónde busca un desnivel hacia el que correr
     * @param infinite  si dos fuentes forman otra (el agua)
     */
    public record Rules(int decrease, int holeReach, boolean infinite) {
        /** Las del juego para ese líquido; {@code ultrawarm} es el Nether, donde la lava corre como el agua. */
        public static Rules of(FluidKind fluid, boolean ultrawarm) {
            if (fluid == FluidKind.LAVA) {
                return ultrawarm ? new Rules(1, 4, false) : new Rules(2, 2, false);
            }
            return new Rules(1, 4, true);
        }
    }

    private final FluidCells cells;
    private final RegionSnapshot snapshot;
    private final BlockKinds kinds;
    private final Rules rules;
    private final Runnable checkCancelled;
    private final byte[] states;
    private final BitSet loaded = new BitSet();
    private final Int2ByteOpenHashMap terrain = new Int2ByteOpenHashMap();
    private final Int2ByteOpenHashMap initial = new Int2ByteOpenHashMap();
    private final IntOpenHashSet scheduled = new IntOpenHashSet();
    private final IntOpenHashSet flowing = new IntOpenHashSet();
    private int maxFlows = Integer.MAX_VALUE;
    private boolean capped;
    private int exit = -1;

    public FlowSim(RegionSnapshot snapshot, BlockKinds kinds, FluidKind fluid, Rules rules, Runnable checkCancelled) {
        if (!fluid.flows()) {
            throw new IllegalArgumentException("La nieve en polvo no corre");
        }
        this.cells = new FluidCells(snapshot, kinds, fluid, false);
        this.snapshot = snapshot;
        this.kinds = kinds;
        this.rules = rules;
        this.checkCancelled = checkCancelled;
        this.states = new byte[cells.volume()];
    }

    FluidCells cells() {
        return cells;
    }

    /** Pone una fuente nueva (también donde había un bloque: se excava). */
    public void source(int x, int y, int z) {
        int index = cells.index(x, y, z);
        terrain.remove(index);
        put(index, SOURCE | 8);
        scheduleAround(index);
    }

    /** Deja aire donde había un bloque (un corte en el terreno). */
    public void cut(int x, int y, int z) {
        int index = cells.index(x, y, z);
        terrain.put(index, CUT);
        put(index, NONE);
        scheduleAround(index);
    }

    /** Pone una pared (una orilla) donde había hueco. */
    public void bank(int x, int y, int z) {
        int index = cells.index(x, y, z);
        terrain.put(index, BANK);
        put(index, NONE);
        scheduleAround(index);
    }

    /** Corre hasta quedarse quieto, salir de la zona o pasar de {@code maxFlows} bloques cambiados. */
    public Result run(int maxFlows) {
        this.maxFlows = maxFlows;
        int maxRounds = 4 * (cells.sizeX + cells.sizeY + cells.sizeZ) + 64;
        for (int round = 0; round < maxRounds && !scheduled.isEmpty() && !stopped(); round++) {
            int[] current = scheduled.toIntArray();
            Arrays.sort(current);
            scheduled.clear();
            for (int index : current) {
                tick(index);
                if (stopped()) {
                    break;
                }
            }
            checkCancelled.run();
        }
        IntArrayList changed = new IntArrayList();
        for (var entry : initial.int2ByteEntrySet()) {
            if (states[entry.getIntKey()] != entry.getByteValue()) {
                changed.add(entry.getIntKey());
            }
        }
        int[] sorted = changed.toIntArray();
        Arrays.sort(sorted);
        byte[] finals = new byte[sorted.length];
        for (int index = 0; index < sorted.length; index++) {
            finals[index] = states[sorted[index]];
        }
        return new Result(cells, sorted, finals, exit, capped);
    }

    private boolean stopped() {
        return exit >= 0 || capped;
    }

    // ---------------------------------------------------------------- reglas del juego

    /** {@code FlowableFluid.onScheduledTick}: recalcula si no es fuente y después intenta correr. */
    private void tick(int index) {
        int state = state(index);
        if (state == NONE) {
            return;
        }
        if ((state & SOURCE) == 0) {
            int updated = updated(index);
            if (updated != state) {
                put(index, updated);
                scheduleAround(index);
                state = updated;
            }
            if (state == NONE) {
                return;
            }
        }
        flow(index, state);
    }

    /** {@code FlowableFluid.tryFlow}. */
    private void flow(int index, int state) {
        int x = cells.x(index);
        int y = cells.y(index);
        int z = cells.z(index);
        if (!cells.contains(x, y - 1, z)) {
            // Caería fuera de lo leído.
            exit = index;
            return;
        }
        int below = cells.index(x, y - 1, z);
        if (state(below) == NONE && enterable(below)) {
            spill(below, updated(below));
            if (neighborSources(x, y, z) >= 3) {
                toSides(index, state);
            }
            return;
        }
        if ((state & SOURCE) != 0 || !hole(x, y - 1, z)) {
            toSides(index, state);
        }
    }

    /** {@code FlowableFluid.flowToSides} con {@code getSpread}: hacia el desnivel más cercano o a todos lados. */
    private void toSides(int index, int state) {
        int amount = (state & FALLING) != 0 ? 7 : amount(state) - rules.decrease();
        if (amount <= 0) {
            return;
        }
        int x = cells.x(index);
        int y = cells.y(index);
        int z = cells.z(index);
        for (int[] side : SIDES) {
            if (!cells.contains(x + side[0], y, z + side[1])) {
                // Correría fuera de lo leído: no se sabe qué hay, es un derrame.
                exit = index;
                return;
            }
        }
        int best = 1_000;
        int[] targets = new int[4];
        int[] values = new int[4];
        int count = 0;
        for (int side = 0; side < 4; side++) {
            int nx = x + SIDES[side][0];
            int nz = z + SIDES[side][1];
            int target = cells.index(nx, y, nz);
            if (!enterable(target)) {
                continue;
            }
            int value = updated(target);
            if (value == NONE) {
                continue;
            }
            int distance = hole(nx, y - 1, nz) ? 0 : downDistance(nx, y, nz, 1, (side + 2) % 4);
            if (distance < best) {
                best = distance;
                count = 0;
            }
            if (distance <= best) {
                targets[count] = target;
                values[count] = value;
                count++;
            }
        }
        for (int target = 0; target < count; target++) {
            spill(targets[target], values[target]);
        }
    }

    /** {@code getMinFlowDownDistance}: a cuántos bloques de lado hay un desnivel (1 000 si ninguno a su alcance). */
    private int downDistance(int x, int y, int z, int depth, int from) {
        int best = 1_000;
        for (int side = 0; side < 4; side++) {
            if (side == from) {
                continue;
            }
            int nx = x + SIDES[side][0];
            int nz = z + SIDES[side][1];
            if (!cells.contains(nx, y, nz) || !enterable(cells.index(nx, y, nz))) {
                continue;
            }
            if (hole(nx, y - 1, nz)) {
                return depth;
            }
            if (depth < rules.holeReach()) {
                best = Math.min(best, downDistance(nx, y, nz, depth + 1, (side + 2) % 4));
            }
        }
        return best;
    }

    /** {@code getUpdatedState}: el estado que tendría una posición según sus vecinos. */
    private int updated(int index) {
        int x = cells.x(index);
        int y = cells.y(index);
        int z = cells.z(index);
        int highest = 0;
        int sources = 0;
        for (int[] side : SIDES) {
            if (!cells.contains(x + side[0], y, z + side[1])) {
                continue;
            }
            int neighbor = state(cells.index(x + side[0], y, z + side[1]));
            if (neighbor != NONE) {
                if ((neighbor & SOURCE) != 0) {
                    sources++;
                }
                highest = Math.max(highest, amount(neighbor));
            }
        }
        if (rules.infinite() && sources >= 2 && cells.contains(x, y - 1, z)) {
            int below = cells.index(x, y - 1, z);
            if (solid(below) || (state(below) & SOURCE) != 0) {
                return SOURCE | 8;
            }
        }
        if (cells.contains(x, y + 1, z) && state(cells.index(x, y + 1, z)) != NONE) {
            return FALLING | 8;
        }
        int level = highest - rules.decrease();
        return level <= 0 ? NONE : level;
    }

    /** Si debajo hay a dónde caer: el mismo líquido o un hueco (fuera de la zona, también). */
    private boolean hole(int x, int y, int z) {
        if (!cells.contains(x, y, z)) {
            return true;
        }
        int index = cells.index(x, y, z);
        return state(index) != NONE || enterable(index);
    }

    private int neighborSources(int x, int y, int z) {
        int sources = 0;
        for (int[] side : SIDES) {
            if (cells.contains(x + side[0], y, z + side[1])
                    && (state(cells.index(x + side[0], y, z + side[1])) & SOURCE) != 0) {
                sources++;
            }
        }
        return sources;
    }

    // ---------------------------------------------------------------- celdas

    /** Si el líquido puede entrar: un hueco o una corriente suya, nunca una fuente ni una pared. */
    private boolean enterable(int index) {
        byte override = terrain.get(index);
        if (override == BANK) {
            return false;
        }
        int state = state(index);
        if ((state & SOURCE) != 0) {
            return false;
        }
        if (state != NONE || override == CUT) {
            return true;
        }
        int kind = cells.at(index);
        return kind == FluidCells.EMPTY || kind == FluidCells.FLOW;
    }

    private boolean solid(int index) {
        byte override = terrain.get(index);
        if (override != 0) {
            return override == BANK;
        }
        return state(index) == NONE && snapshot.isSolid(cells.x(index), cells.y(index), cells.z(index));
    }

    private void spill(int index, int value) {
        if (value == NONE || state(index) == value) {
            return;
        }
        put(index, value);
        scheduleAround(index);
    }

    private void put(int index, int value) {
        int before = state(index);
        if (!initial.containsKey(index)) {
            initial.put(index, (byte) before);
        }
        states[index] = (byte) value;
        if (value != NONE && (value & SOURCE) == 0) {
            if (flowing.add(index) && flowing.size() > maxFlows) {
                capped = true;
            }
        }
    }

    private void scheduleAround(int index) {
        int x = cells.x(index);
        int y = cells.y(index);
        int z = cells.z(index);
        scheduled.add(index);
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : around) {
            if (cells.contains(x + offset[0], y + offset[1], z + offset[2])) {
                scheduled.add(cells.index(x + offset[0], y + offset[1], z + offset[2]));
            }
        }
    }

    private int state(int index) {
        if (!loaded.get(index)) {
            loaded.set(index);
            states[index] = (byte) start(index);
        }
        return states[index];
    }

    /** El líquido que había en la foto. */
    private int start(int index) {
        int rawId = snapshot.rawId(cells.x(index), cells.y(index), cells.z(index));
        boolean matches = cells.fluid == FluidKind.WATER
                ? kinds.water(rawId) || kinds.waterOnly(rawId) || kinds.waterlogged(rawId)
                : kinds.lava(rawId);
        if (!matches) {
            return NONE;
        }
        return fromLevel(kinds.fluidLevel(rawId));
    }

    /** Estado a partir de la propiedad {@code level} (-1: fuente fija sin nivel). */
    static int fromLevel(int level) {
        if (level < 0) {
            return SOURCE | FIXED | 8;
        }
        if (level == 0) {
            return SOURCE | 8;
        }
        return level >= 8 ? FALLING | 8 : 8 - level;
    }

    /** La propiedad {@code level} de un estado con líquido. */
    static int toLevel(int state) {
        if ((state & SOURCE) != 0) {
            return 0;
        }
        return (state & FALLING) != 0 ? 8 : 8 - amount(state);
    }

    static int amount(int state) {
        return state & AMOUNT;
    }

    /**
     * El líquido que cambió (los cortes y orillas los escribe quien los pidió).
     *
     * @param exit   posición desde la que el líquido saldría de la zona leída, o -1
     * @param capped si se paró por pasar del límite de bloques
     */
    public record Result(FluidCells cells, int[] positions, byte[] states, int exit, boolean capped) {
        /** Si se derrama fuera de lo leído o crece más de la cuenta. */
        public boolean overflow() {
            return exit >= 0 || capped;
        }

        public int size() {
            return positions.length;
        }

        public int x(int entry) {
            return cells.x(positions[entry]);
        }

        public int y(int entry) {
            return cells.y(positions[entry]);
        }

        public int z(int entry) {
            return cells.z(positions[entry]);
        }

        /** Estado final ({@link #NONE} si se secó o se cortó). */
        public int state(int entry) {
            return states[entry];
        }

        public boolean flowing(int entry) {
            return states[entry] != NONE && (states[entry] & SOURCE) == 0;
        }

        public boolean falling(int entry) {
            return (states[entry] & FALLING) != 0;
        }

        /** La propiedad {@code level} final, o -1 si no queda líquido. */
        public int level(int entry) {
            return states[entry] == NONE ? -1 : toLevel(states[entry]);
        }

        /** Corrientes que quedan (no fuentes). */
        public int flows() {
            int count = 0;
            for (int entry = 0; entry < states.length; entry++) {
                if (flowing(entry)) {
                    count++;
                }
            }
            return count;
        }
    }
}
