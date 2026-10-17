package com.komainu.moxia.shape.smooth;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * Piezas de una geometría por posición empaquetada ({@link PackedBlockPlan#pack(int, int, int)}). Las posiciones
 * que no aparecen son bloques enteros, así una figura sin piezas no ocupa memoria extra.
 */
public final class SmoothPieces {
    public static final SmoothPieces NONE = new SmoothPieces(new Long2ByteOpenHashMap(0));

    private final Long2ByteOpenHashMap pieces;
    private final int slabs;
    private final int stairs;
    private final int added;

    /** Toma posesión del mapa: quien lo crea no debe modificarlo después. */
    public SmoothPieces(Long2ByteOpenHashMap pieces) {
        this.pieces = pieces;
        pieces.defaultReturnValue(SmoothPiece.FULL);
        int slabCount = 0;
        int stairCount = 0;
        int addedCount = 0;
        ObjectIterator<Long2ByteMap.Entry> entries = pieces.long2ByteEntrySet().fastIterator();
        while (entries.hasNext()) {
            byte piece = entries.next().getByteValue();
            if (!SmoothPiece.isValid(piece) || piece == SmoothPiece.FULL) {
                throw new IllegalArgumentException("Pieza no válida en la geometría: " + piece);
            }
            if (SmoothPiece.isSlab(piece)) {
                slabCount++;
            } else {
                stairCount++;
            }
            if (SmoothPiece.isAdded(piece)) {
                addedCount++;
            }
        }
        slabs = slabCount;
        stairs = stairCount;
        added = addedCount;
    }

    public byte at(long packed) {
        return pieces.get(packed);
    }

    public byte at(int x, int y, int z) {
        return y < -2048 || y > 2047 ? SmoothPiece.FULL : pieces.get(PackedBlockPlan.pack(x, y, z));
    }

    public boolean isEmpty() {
        return pieces.isEmpty();
    }

    public int size() {
        return pieces.size();
    }

    public int slabs() {
        return slabs;
    }

    public int stairs() {
        return stairs;
    }

    /** Piezas colocadas donde la figura no tenía bloque. */
    public int added() {
        return added;
    }

    /** Resumen para los mensajes de construcción, p. ej. {@code 120 losas · 85 escaleras}. */
    public String summary() {
        if (isEmpty()) {
            return "sin piezas";
        }
        return slabs + (slabs == 1 ? " losa" : " losas") + " · " + stairs
                + (stairs == 1 ? " escalera" : " escaleras");
    }
}
