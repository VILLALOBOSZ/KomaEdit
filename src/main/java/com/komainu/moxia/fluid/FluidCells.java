package com.komainu.moxia.fluid;

import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.utility.BlockKinds;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * Qué es cada bloque de la foto para un líquido: pared, hueco que se llena, líquido que ya está… Lo comparten el
 * relleno, la bola y la cascada. También numera las posiciones de la foto ({@link #index}), que es como se guardan
 * las celdas en los motores.
 */
final class FluidCells {
    /** No deja pasar el líquido: roca, puertas, carteles, lo que se anega (si no se anega)… */
    static final int WALL = 0;
    /** Aire o algo que el líquido se lleva (hierba, flores, antorchas): se pone una fuente. */
    static final int EMPTY = 1;
    /** Corriente del mismo líquido: pasa a ser fuente. */
    static final int FLOW = 2;
    /** Ya está lleno: una fuente, una columna de burbujas, algas o un bloque ya anegado. */
    static final int FULL = 3;
    /** Se anega (solo agua y si se pide). */
    static final int LOGGABLE = 4;
    /** El otro líquido: hace de pared, pero al tocarse saldría piedra u obsidiana. */
    static final int OTHER = 5;

    final RegionSnapshot snapshot;
    final BlockKinds kinds;
    final FluidKind fluid;
    final boolean waterlog;
    final int minX;
    final int minY;
    final int minZ;
    final int sizeX;
    final int sizeY;
    final int sizeZ;
    private final Int2IntOpenHashMap cache = new Int2IntOpenHashMap();

    FluidCells(RegionSnapshot snapshot, BlockKinds kinds, FluidKind fluid, boolean waterlog) {
        this.snapshot = snapshot;
        this.kinds = kinds;
        this.fluid = fluid;
        this.waterlog = waterlog && fluid == FluidKind.WATER;
        this.minX = snapshot.minimumX();
        this.minY = snapshot.minimumY();
        this.minZ = snapshot.minimumZ();
        this.sizeX = snapshot.maximumX() - minX + 1;
        this.sizeY = snapshot.maximumY() - minY + 1;
        this.sizeZ = snapshot.maximumZ() - minZ + 1;
        cache.defaultReturnValue(-1);
    }

    int volume() {
        return sizeX * sizeY * sizeZ;
    }

    boolean contains(int x, int y, int z) {
        return x >= minX && y >= minY && z >= minZ && x < minX + sizeX && y < minY + sizeY && z < minZ + sizeZ;
    }

    int index(int x, int y, int z) {
        return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
    }

    int x(int index) {
        return index % sizeX + minX;
    }

    int y(int index) {
        return index / (sizeX * sizeZ) + minY;
    }

    int z(int index) {
        return index / sizeX % sizeZ + minZ;
    }

    /** Clase de la posición (fuera de la foto, {@link #WALL}). */
    int at(int x, int y, int z) {
        return contains(x, y, z) ? of(snapshot.rawId(x, y, z)) : WALL;
    }

    int at(int index) {
        return at(x(index), y(index), z(index));
    }

    /** Si el líquido puede ocupar la posición (hueco, corriente, ya lleno o anegable). */
    static boolean open(int kind) {
        return kind != WALL && kind != OTHER;
    }

    /** Si hay que escribir algo en una posición de esta clase para llenarla. */
    static boolean needsWrite(int kind) {
        return kind == EMPTY || kind == FLOW || kind == LOGGABLE;
    }

    int of(int rawId) {
        int cached = cache.get(rawId);
        if (cached >= 0) {
            return cached;
        }
        int computed = classify(rawId);
        cache.put(rawId, computed);
        return computed;
    }

    private int classify(int rawId) {
        boolean water = kinds.water(rawId) || kinds.waterOnly(rawId);
        boolean lava = kinds.lava(rawId);
        switch (fluid) {
            case WATER -> {
                if (water) {
                    return kinds.water(rawId) && !kinds.source(rawId) ? FLOW : FULL;
                }
                if (lava) {
                    return OTHER;
                }
                if (kinds.waterloggable(rawId)) {
                    return !waterlog ? WALL : kinds.waterlogged(rawId) ? FULL : LOGGABLE;
                }
            }
            case LAVA -> {
                if (lava) {
                    return kinds.source(rawId) ? FULL : FLOW;
                }
                if (water || kinds.waterlogged(rawId)) {
                    return OTHER;
                }
                if (kinds.waterloggable(rawId)) {
                    return WALL;
                }
            }
            case POWDER_SNOW -> {
                if (snapshot.stateInfo(rawId).blockId().equals(FluidKind.POWDER_SNOW.blockId())) {
                    return FULL;
                }
                if (water || lava || kinds.waterloggable(rawId)) {
                    return WALL;
                }
            }
        }
        return kinds.washable(rawId) ? EMPTY : WALL;
    }
}
