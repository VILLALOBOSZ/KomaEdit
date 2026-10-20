package com.komainu.moxia.blueprint;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;

/**
 * Un plano listo para estampar: sus bloques en coordenadas locales con el origen en el centro de su base (el bloque
 * más bajo que no es aire) y su huella para apoyarlo. Es inmutable y se recorre desde el worker.
 */
public final class BlueprintTemplate {
    /** Bloques de caja como mucho para estampar: el pincel es para rocas, arbustos y ruinas, no para ciudades. */
    public static final int MAX_VOLUME = 262_144;

    private final String name;
    private final int[] xs;
    private final int[] ys;
    private final int[] zs;
    private final BlockState[] states;
    private final boolean[] air;
    private final int size;
    private final StampShape shape;

    private BlueprintTemplate(String name, int[] xs, int[] ys, int[] zs, BlockState[] states, boolean[] air, int size,
                              StampShape shape) {
        this.name = name;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.states = states;
        this.air = air;
        this.size = size;
        this.shape = shape;
    }

    public static BlueprintTemplate of(String name, Clipboard clipboard) {
        Region region = clipboard.getRegion();
        long volume = region.getVolume();
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException(name + " es demasiado grande para estamparlo (" + volume
                    + " bloques de caja; máximo " + MAX_VOLUME + "): colócalo con /komaedit blueprint place");
        }
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        int count = (int) volume;
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        BlockState[] states = new BlockState[count];
        boolean[] air = new boolean[count];
        int lowest = Integer.MAX_VALUE;
        int solid = 0;
        int index = 0;
        for (BlockVector3 position : region) {
            BlockState state = clipboard.getBlock(position);
            boolean empty = state.getBlockType().getMaterial().isAir();
            xs[index] = position.x() - Math.floorDiv(minimum.x() + maximum.x(), 2);
            ys[index] = position.y();
            zs[index] = position.z() - Math.floorDiv(minimum.z() + maximum.z(), 2);
            states[index] = state;
            air[index] = empty;
            if (!empty) {
                lowest = Math.min(lowest, position.y());
                solid++;
            }
            index++;
        }
        if (solid == 0) {
            throw new IllegalArgumentException(name + " solo tiene aire");
        }
        int[] solidX = new int[solid];
        int[] solidY = new int[solid];
        int[] solidZ = new int[solid];
        int next = 0;
        for (int position = 0; position < index; position++) {
            ys[position] -= lowest;
            if (!air[position]) {
                solidX[next] = xs[position];
                solidY[next] = ys[position];
                solidZ[next] = zs[position];
                next++;
            }
        }
        return new BlueprintTemplate(name, xs, ys, zs, states, air, index, StampShape.of(solidX, solidY, solidZ, solid));
    }

    public String name() {
        return name;
    }

    public StampShape shape() {
        return shape;
    }

    public int size() {
        return size;
    }

    public void forEach(boolean includeAir, Visitor visitor) {
        for (int index = 0; index < size; index++) {
            if (includeAir || !air[index]) {
                visitor.accept(xs[index], ys[index], zs[index], states[index]);
            }
        }
    }

    @FunctionalInterface
    public interface Visitor {
        void accept(int x, int y, int z, BlockState state);
    }
}
