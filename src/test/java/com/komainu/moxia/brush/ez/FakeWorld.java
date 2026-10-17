package com.komainu.moxia.brush.ez;

import java.util.HashMap;
import java.util.Map;

/** Terreno de prueba: una caja de aire donde se ponen bloques a mano. */
final class FakeWorld implements BrushWorld {
    private final int size;
    private final Map<Long, String> ids = new HashMap<>();
    private final Map<Long, Cell> cells = new HashMap<>();
    private final Map<Long, Integer> amounts = new HashMap<>();

    FakeWorld(int size) {
        this.size = size;
    }

    FakeWorld set(int x, int y, int z, String id, Cell cell) {
        ids.put(key(x, y, z), PaletteShift.normalize(id));
        cells.put(key(x, y, z), cell);
        return this;
    }

    FakeWorld amount(int x, int y, int z, int amount) {
        amounts.put(key(x, y, z), amount);
        return this;
    }

    /** Suelo de cubos enteros en {@code y} en toda la caja. */
    FakeWorld floor(int y, String id) {
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                set(x, y, z, id, Cell.FULL);
            }
        }
        return this;
    }

    @Override
    public String blockId(int x, int y, int z) {
        return ids.getOrDefault(key(x, y, z), "minecraft:air");
    }

    @Override
    public Cell cell(int x, int y, int z) {
        return cells.getOrDefault(key(x, y, z), Cell.AIR);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return Math.abs(x) <= size && Math.abs(y) <= size && Math.abs(z) <= size;
    }

    @Override
    public int intProperty(int x, int y, int z, String property) {
        return amounts.getOrDefault(key(x, y, z), -1);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}
