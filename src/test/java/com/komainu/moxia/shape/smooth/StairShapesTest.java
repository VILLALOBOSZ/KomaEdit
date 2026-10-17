package com.komainu.moxia.shape.smooth;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Casos de {@code StairsBlock.getStairShape} de Minecraft 1.21.1. */
class StairShapesTest {
    @Test
    void lonelyStairStaysStraight() {
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap();
        put(pieces, 0, 0, 0, SmoothPiece.NORTH, false);
        put(pieces, 0, 1, 0, SmoothPiece.EAST, false);
        StairShapes.resolve(pieces);
        assertEquals(SmoothPiece.STRAIGHT, shape(pieces, 0, 0, 0));
        assertEquals(SmoothPiece.STRAIGHT, shape(pieces, 0, 1, 0), "las escaleras solo se unen en horizontal");
    }

    @Test
    void perpendicularStairInFrontMakesAnOuterCorner() {
        Long2ByteOpenHashMap right = new Long2ByteOpenHashMap();
        put(right, 0, 0, 0, SmoothPiece.NORTH, false);
        put(right, 0, 0, -1, SmoothPiece.EAST, false);
        StairShapes.resolve(right);
        assertEquals(SmoothPiece.OUTER_RIGHT, shape(right, 0, 0, 0));
        assertEquals(SmoothPiece.STRAIGHT, shape(right, 0, 0, -1));

        Long2ByteOpenHashMap left = new Long2ByteOpenHashMap();
        put(left, 0, 0, 0, SmoothPiece.NORTH, false);
        put(left, 0, 0, -1, SmoothPiece.WEST, false);
        StairShapes.resolve(left);
        assertEquals(SmoothPiece.OUTER_LEFT, shape(left, 0, 0, 0));
    }

    @Test
    void perpendicularStairBehindMakesAnInnerCorner() {
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap();
        put(pieces, 0, 0, 0, SmoothPiece.NORTH, false);
        put(pieces, 0, 0, 1, SmoothPiece.EAST, false);
        StairShapes.resolve(pieces);
        assertEquals(SmoothPiece.INNER_RIGHT, shape(pieces, 0, 0, 0));
        assertEquals(Integer.bitCount(SmoothPiece.mask(pieces.get(key(0, 0, 0)))), 7);
    }

    @Test
    void halvesAndContinuingRowsDoNotBend() {
        Long2ByteOpenHashMap halves = new Long2ByteOpenHashMap();
        put(halves, 0, 0, 0, SmoothPiece.NORTH, false);
        put(halves, 0, 0, -1, SmoothPiece.EAST, true);
        StairShapes.resolve(halves);
        assertEquals(SmoothPiece.STRAIGHT, shape(halves, 0, 0, 0), "una escalera colgada no dobla a una apoyada");

        // Si la fila sigue hacia el oeste con la misma orientación, Minecraft no hace esquina.
        Long2ByteOpenHashMap row = new Long2ByteOpenHashMap();
        put(row, 0, 0, 0, SmoothPiece.NORTH, false);
        put(row, 0, 0, -1, SmoothPiece.EAST, false);
        put(row, -1, 0, 0, SmoothPiece.NORTH, false);
        StairShapes.resolve(row);
        assertEquals(SmoothPiece.STRAIGHT, shape(row, 0, 0, 0));
    }

    @Test
    void resolvingKeepsSlabsAndTheAddedMark() {
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap();
        pieces.put(key(5, 0, 5), SmoothPiece.added(SmoothPiece.slab(false)));
        pieces.put(key(0, 0, 0), SmoothPiece.added(SmoothPiece.stairs(SmoothPiece.NORTH, false,
                SmoothPiece.INNER_LEFT)));
        put(pieces, 0, 0, -1, SmoothPiece.EAST, false);
        StairShapes.resolve(pieces);
        byte corner = pieces.get(key(0, 0, 0));
        assertTrue(SmoothPiece.isAdded(corner));
        assertEquals(SmoothPiece.OUTER_RIGHT, SmoothPiece.shape(corner));
        assertEquals(SmoothPiece.added(SmoothPiece.slab(false)), pieces.get(key(5, 0, 5)));
    }

    private static void put(Long2ByteOpenHashMap pieces, int x, int y, int z, int facing, boolean top) {
        pieces.put(key(x, y, z), SmoothPiece.stairs(facing, top, SmoothPiece.STRAIGHT));
    }

    private static int shape(Long2ByteOpenHashMap pieces, int x, int y, int z) {
        return SmoothPiece.shape(pieces.get(key(x, y, z)));
    }

    private static long key(int x, int y, int z) {
        return PackedBlockPlan.pack(x, y, z);
    }
}
