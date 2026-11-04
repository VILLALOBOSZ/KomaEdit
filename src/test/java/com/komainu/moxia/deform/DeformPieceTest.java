package com.komainu.moxia.deform;

import com.komainu.moxia.shape.smooth.SmoothPiece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code smoothblocks Slabs 2 0} sobre una cúpula de cristal u hojas: sin losa de ese material, la pieza que se añade
 * en un hueco de aire salía como bloque entero y la forma engordaba.
 */
class DeformPieceTest {
    @Test
    void addedPiecesWithoutAShapeAreLeftOut() {
        byte slab = SmoothPiece.slab(false);
        assertEquals("losa", DeformOperation.piece("cristal", slab, "losa"));
        assertEquals("cristal", DeformOperation.piece("cristal", slab, null), "donde ya había bloque se queda entero");
        assertNull(DeformOperation.piece("cristal", SmoothPiece.added(slab), null), "en el hueco no se pone nada");
        assertEquals("losa", DeformOperation.piece("cristal", SmoothPiece.added(slab), "losa"));
    }
}
