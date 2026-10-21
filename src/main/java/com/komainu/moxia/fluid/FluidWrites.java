package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;

/**
 * Lo que escribe una herramienta de agua, sin estados de Minecraft: el líquido con su nivel, aire, anegar o un
 * bloque de la foto (una orilla). La operación del servidor lo convierte en estados de WorldEdit. Una posición
 * escrita dos veces se queda con lo último.
 */
public final class FluidWrites {
    /** Aire (un corte o una corriente que se quita). */
    public static final int AIR = -1;
    /** Anegar el bloque que hay. */
    public static final int WATERLOG = -2;
    private static final int BLOCK_BASE = -10;

    private final FluidKind fluid;
    private final Long2IntLinkedOpenHashMap writes = new Long2IntLinkedOpenHashMap();

    public FluidWrites(FluidKind fluid) {
        this.fluid = fluid;
    }

    public FluidKind fluid() {
        return fluid;
    }

    /** El líquido con la propiedad {@code level} (0 fuente; la nieve en polvo no tiene nivel). */
    public void fluid(int x, int y, int z, int level) {
        writes.put(PackedBlockPlan.pack(x, y, z), Math.clamp(level, 0, 15));
    }

    public void air(int x, int y, int z) {
        writes.put(PackedBlockPlan.pack(x, y, z), AIR);
    }

    public void waterlog(int x, int y, int z) {
        writes.put(PackedBlockPlan.pack(x, y, z), WATERLOG);
    }

    /** Un estado de la foto, por su id crudo. */
    public void block(int x, int y, int z, int rawId) {
        writes.put(PackedBlockPlan.pack(x, y, z), BLOCK_BASE - rawId);
    }

    public int size() {
        return writes.size();
    }

    public boolean isEmpty() {
        return writes.isEmpty();
    }

    /** Lo escrito en una posición, o {@code null}. */
    public Integer at(int x, int y, int z) {
        long key = PackedBlockPlan.pack(x, y, z);
        return writes.containsKey(key) ? writes.get(key) : null;
    }

    public interface Visitor {
        void visit(int x, int y, int z, int code);
    }

    public void forEach(Visitor visitor) {
        for (var entry : writes.long2IntEntrySet()) {
            long key = entry.getLongKey();
            visitor.visit(PackedBlockPlan.unpackX(key), PackedBlockPlan.unpackY(key), PackedBlockPlan.unpackZ(key),
                    entry.getIntValue());
        }
    }

    /** Si el código es un bloque de la foto. */
    public static boolean isBlock(int code) {
        return code <= BLOCK_BASE;
    }

    /** El id crudo de un código de bloque. */
    public static int rawId(int code) {
        return BLOCK_BASE - code;
    }

    /** Cuántas escrituras dejan líquido. */
    public int liquids() {
        int count = 0;
        for (int code : writes.values()) {
            if (code >= 0) {
                count++;
            }
        }
        return count;
    }
}
