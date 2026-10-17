package com.komainu.moxia.generator;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.concurrent.CancellationException;

/**
 * Resultado de un generador: posiciones con el índice de su material (la entrada de la paleta) y, para la nieve
 * del modo suave, el número de capas. Ocupa 12 bytes por bloque y no conoce Minecraft, así que se prueba sin él.
 */
public final class GeneratedBlocks {
    /** Material {@code 0} con 0 capas: bloque normal de la primera entrada. */
    private static final int LAYER_SHIFT = 16;

    private final int limit;
    private final LongArrayList positions = new LongArrayList();
    private final IntArrayList data = new IntArrayList();
    private int checks;

    public GeneratedBlocks(int limit) {
        this.limit = limit;
    }

    public void add(int x, int y, int z, int material) {
        add(x, y, z, material, 0);
    }

    /** @param layers capas de nieve (1–8) o 0 para el bloque tal cual */
    public void add(int x, int y, int z, int material, int layers) {
        if (material < 0 || material > 0xFFFF || layers < 0 || layers > 8) {
            throw new IllegalArgumentException("Material fuera de rango");
        }
        positions.add(PackedBlockPlan.pack(x, y, z));
        data.add(material | layers << LAYER_SHIFT);
        if (positions.size() > limit) {
            throw new GeometryLimitException(limit);
        }
    }

    public int size() {
        return positions.size();
    }

    public long packed(int index) {
        return positions.getLong(index);
    }

    public int x(int index) {
        return PackedBlockPlan.unpackX(positions.getLong(index));
    }

    public int y(int index) {
        return PackedBlockPlan.unpackY(positions.getLong(index));
    }

    public int z(int index) {
        return PackedBlockPlan.unpackZ(positions.getLong(index));
    }

    public int material(int index) {
        return data.getInt(index) & 0xFFFF;
    }

    public int layers(int index) {
        return data.getInt(index) >>> LAYER_SHIFT;
    }

    public int limit() {
        return limit;
    }

    /** Llamar dentro de bucles largos: corta el trabajo si el jugador cancela o se desconecta. */
    public void checkCancelled() {
        if ((checks++ & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    /** Caja que envuelve los bloques: {@code {minX, minY, minZ, maxX, maxY, maxZ}} o {@code null} si está vacío. */
    public int[] bounds() {
        if (positions.isEmpty()) {
            return null;
        }
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int index = 0; index < positions.size(); index++) {
            int x = x(index);
            int y = y(index);
            int z = z(index);
            bounds[0] = Math.min(bounds[0], x);
            bounds[1] = Math.min(bounds[1], y);
            bounds[2] = Math.min(bounds[2], z);
            bounds[3] = Math.max(bounds[3], x);
            bounds[4] = Math.max(bounds[4], y);
            bounds[5] = Math.max(bounds[5], z);
        }
        return bounds;
    }
}
