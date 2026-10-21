package com.komainu.moxia.fluid;

import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.utility.BlockKinds;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

/**
 * Quitar los flujos sueltos: las corrientes de agua o lava que no salen de ninguna fuente, sin Minecraft.
 *
 * <p>Desde cada fuente (también lo anegado, las algas y las columnas de burbujas) se reparte el nivel como lo
 * recalcula el juego: debajo de un líquido hay una cascada (nivel lleno) y de lado se pierde un nivel por bloque (la
 * lava del mundo normal, dos). La corriente que no recibe nada se secaría en cuanto el juego la mirase: esa es la
 * suelta y se quita. Las corrientes del borde de la zona leída cuentan como alimentadas, porque su fuente puede
 * quedar fuera.</p>
 */
public final class LooseFlows {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private LooseFlows() {
    }

    /** Qué líquido se limpia. */
    public enum Target {
        WATER, LAVA, ALL;

        boolean includes(FluidKind fluid) {
            return this == ALL || (this == WATER) == (fluid == FluidKind.WATER);
        }
    }

    /** Filtro (la selección y la máscara); {@code null} acepta todo. */
    @FunctionalInterface
    public interface Filter {
        boolean test(int x, int y, int z);
    }

    public record Result(FluidWrites writes, int water, int lava) {
        public int removed() {
            return water + lava;
        }
    }

    public static Result clean(RegionSnapshot snapshot, BlockKinds kinds, Target target, boolean ultrawarm,
                               Filter filter, Runnable checkCancelled) {
        FluidWrites writes = new FluidWrites(FluidKind.WATER);
        int water = target.includes(FluidKind.WATER)
                ? clean(snapshot, kinds, FluidKind.WATER, FlowSim.Rules.of(FluidKind.WATER, ultrawarm), filter,
                writes, checkCancelled) : 0;
        int lava = target.includes(FluidKind.LAVA)
                ? clean(snapshot, kinds, FluidKind.LAVA, FlowSim.Rules.of(FluidKind.LAVA, ultrawarm), filter,
                writes, checkCancelled) : 0;
        return new Result(writes, water, lava);
    }

    private static int clean(RegionSnapshot snapshot, BlockKinds kinds, FluidKind fluid, FlowSim.Rules rules,
                             Filter filter, FluidWrites writes, Runnable checkCancelled) {
        FluidCells cells = new FluidCells(snapshot, kinds, fluid, false);
        int volume = cells.volume();
        byte[] state = new byte[volume];
        byte[] fed = new byte[volume];
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (int index = 0; index < volume; index++) {
            if ((index & 4_095) == 0) {
                checkCancelled.run();
            }
            int x = cells.x(index);
            int y = cells.y(index);
            int z = cells.z(index);
            int rawId = snapshot.rawId(x, y, z);
            boolean matches = fluid == FluidKind.WATER
                    ? kinds.water(rawId) || kinds.waterOnly(rawId) || kinds.waterlogged(rawId) : kinds.lava(rawId);
            if (!matches) {
                continue;
            }
            int value = FlowSim.fromLevel(kinds.fluidLevel(rawId));
            state[index] = (byte) value;
            boolean edge = x == cells.minX || z == cells.minZ || y == cells.minY + cells.sizeY - 1
                    || x == cells.minX + cells.sizeX - 1 || z == cells.minZ + cells.sizeZ - 1;
            if ((value & FlowSim.SOURCE) != 0 || edge) {
                fed[index] = (byte) FlowSim.amount(value);
                queue.enqueue(index);
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.dequeueInt();
            int amount = fed[index];
            int x = cells.x(index);
            int y = cells.y(index);
            int z = cells.z(index);
            // Debajo de un líquido, una cascada.
            if (cells.contains(x, y - 1, z)) {
                feed(cells, state, fed, queue, cells.index(x, y - 1, z), 8);
            }
            int side = amount - rules.decrease();
            if (side > 0) {
                for (int[] offset : SIDES) {
                    if (cells.contains(x + offset[0], y, z + offset[1])) {
                        feed(cells, state, fed, queue, cells.index(x + offset[0], y, z + offset[1]), side);
                    }
                }
            }
        }
        int removed = 0;
        for (int index = 0; index < volume; index++) {
            int value = state[index];
            if (value == FlowSim.NONE || (value & FlowSim.SOURCE) != 0 || fed[index] > 0) {
                continue;
            }
            int x = cells.x(index);
            int y = cells.y(index);
            int z = cells.z(index);
            if (snapshot.selected(x, y, z) && (filter == null || filter.test(x, y, z))) {
                writes.air(x, y, z);
                removed++;
            }
        }
        return removed;
    }

    /** Lleva nivel a una corriente si le llega más del que ya tenía. */
    private static void feed(FluidCells cells, byte[] state, byte[] fed, IntArrayFIFOQueue queue, int index,
                             int amount) {
        int value = state[index];
        if (value == FlowSim.NONE || (value & FlowSim.SOURCE) != 0 || fed[index] >= amount) {
            return;
        }
        fed[index] = (byte) amount;
        queue.enqueue(index);
    }
}
