package com.komainu.moxia.selection;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.function.LongConsumer;

/**
 * Un conjunto cualquiera de bloques: lo que deja el lazo o el pincel de selección. Cada bloque va en un {@code long}
 * ({@link PackedBlockPlan#pack}), así que vale para todo el mundo salvo alturas fuera de -2048…2047. Lógica pura: lo
 * usan el servidor (la región de WorldEdit), el cliente (el contorno) y las pruebas.
 *
 * <p>Las operaciones que devuelven un conjunto nuevo no tocan este; las de nombre en imperativo ({@link #add},
 * {@link #addAll}…) sí.</p>
 */
public final class BlockSet {
    private final LongOpenHashSet blocks;
    private int[] bounds;

    public BlockSet() {
        this(new LongOpenHashSet());
    }

    private BlockSet(LongOpenHashSet blocks) {
        this.blocks = blocks;
    }

    /** Un conjunto con estos bloques ya empaquetados. */
    public static BlockSet ofPacked(long[] packed) {
        LongOpenHashSet set = new LongOpenHashSet(packed.length);
        for (long block : packed) {
            set.add(block);
        }
        return new BlockSet(set);
    }

    /** Todos los bloques de la caja, con las esquinas incluidas. */
    public static BlockSet box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockSet set = new BlockSet();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set.add(x, y, z);
                }
            }
        }
        return set;
    }

    public static long pack(int x, int y, int z) {
        return PackedBlockPlan.pack(x, y, z);
    }

    public static int x(long packed) {
        return PackedBlockPlan.unpackX(packed);
    }

    public static int y(long packed) {
        return PackedBlockPlan.unpackY(packed);
    }

    public static int z(long packed) {
        return PackedBlockPlan.unpackZ(packed);
    }

    /** Si la altura cabe en el empaquetado. */
    public static boolean storable(int y) {
        return y >= -2048 && y <= 2047;
    }

    public boolean add(int x, int y, int z) {
        return addPacked(pack(x, y, z));
    }

    public boolean addPacked(long packed) {
        if (blocks.add(packed)) {
            bounds = null;
            return true;
        }
        return false;
    }

    public boolean remove(int x, int y, int z) {
        return removePacked(pack(x, y, z));
    }

    public boolean removePacked(long packed) {
        if (blocks.remove(packed)) {
            bounds = null;
            return true;
        }
        return false;
    }

    public boolean contains(int x, int y, int z) {
        return storable(y) && blocks.contains(pack(x, y, z));
    }

    public boolean containsPacked(long packed) {
        return blocks.contains(packed);
    }

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public void addAll(BlockSet other) {
        if (blocks.addAll(other.blocks)) {
            bounds = null;
        }
    }

    public void removeAll(BlockSet other) {
        boolean changed;
        if (other.size() < size()) {
            changed = false;
            LongIterator iterator = other.blocks.iterator();
            while (iterator.hasNext()) {
                changed |= blocks.remove(iterator.nextLong());
            }
        } else {
            changed = blocks.removeIf((long block) -> other.blocks.contains(block));
        }
        if (changed) {
            bounds = null;
        }
    }

    public void retainAll(BlockSet other) {
        if (blocks.removeIf((long block) -> !other.blocks.contains(block))) {
            bounds = null;
        }
    }

    public void clear() {
        blocks.clear();
        bounds = null;
    }

    public BlockSet copy() {
        BlockSet copy = new BlockSet(blocks.clone());
        copy.bounds = bounds == null ? null : bounds.clone();
        return copy;
    }

    /** El mismo conjunto movido {@code (dx, dy, dz)}. */
    public BlockSet shifted(int dx, int dy, int dz) {
        LongOpenHashSet moved = new LongOpenHashSet(blocks.size());
        LongIterator iterator = blocks.iterator();
        while (iterator.hasNext()) {
            long block = iterator.nextLong();
            moved.add(pack(x(block) + dx, y(block) + dy, z(block) + dz));
        }
        return new BlockSet(moved);
    }

    public void forEach(LongConsumer action) {
        LongIterator iterator = blocks.iterator();
        while (iterator.hasNext()) {
            action.accept(iterator.nextLong());
        }
    }

    public LongIterator iterator() {
        return blocks.iterator();
    }

    /** Los bloques empaquetados, sin orden (una copia rápida para llevarlos a otro hilo). */
    public long[] toArray() {
        return blocks.toLongArray();
    }

    /** Los bloques empaquetados, ordenados (siempre igual para el mismo conjunto). */
    public long[] toSortedArray() {
        long[] sorted = blocks.toLongArray();
        LongArrays.radixSort(sorted);
        return sorted;
    }

    /**
     * La caja que lo contiene: {@code {minX, minY, minZ, maxX, maxY, maxZ}}, o {@code null} si está vacío. Se
     * calcula una vez hasta el siguiente cambio.
     */
    public int[] bounds() {
        if (blocks.isEmpty()) {
            return null;
        }
        if (bounds == null) {
            int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE};
            LongIterator iterator = blocks.iterator();
            while (iterator.hasNext()) {
                long block = iterator.nextLong();
                int x = x(block);
                int y = y(block);
                int z = z(block);
                box[0] = Math.min(box[0], x);
                box[1] = Math.min(box[1], y);
                box[2] = Math.min(box[2], z);
                box[3] = Math.max(box[3], x);
                box[4] = Math.max(box[4], y);
                box[5] = Math.max(box[5], z);
            }
            bounds = box;
        }
        return bounds.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockSet set && set.blocks.equals(blocks);
    }

    @Override
    public int hashCode() {
        return blocks.hashCode();
    }

    @Override
    public String toString() {
        return "BlockSet[" + blocks.size() + "]";
    }
}
