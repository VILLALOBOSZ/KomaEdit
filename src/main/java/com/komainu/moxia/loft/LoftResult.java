package com.komainu.moxia.loft;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.SmoothPiece;

import java.util.Arrays;

/**
 * Loft calculado: posiciones ordenadas, la pieza de cada una y su posición entre la primera curva (0) y la
 * última (1), que es la que usa el degradado de las paletas.
 */
public record LoftResult(long[] positions, byte[] pieces, float[] across) {
    public int size() {
        return positions.length;
    }

    public boolean contains(int x, int y, int z) {
        return Arrays.binarySearch(positions, PackedBlockPlan.pack(x, y, z)) >= 0;
    }

    public int pieceCount() {
        int count = 0;
        for (byte piece : pieces) {
            if (piece != SmoothPiece.FULL) {
                count++;
            }
        }
        return count;
    }

    /** Fracción entre curvas de una posición del loft (0 si no está). */
    public float across(long packed) {
        int index = Arrays.binarySearch(positions, packed);
        return index < 0 ? 0.0F : across[index];
    }

    public PackedBlockPlan plan() {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(positions.length);
        for (int index = 0; index < positions.length; index++) {
            long packed = positions[index];
            builder.addPattern(PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                    PackedBlockPlan.unpackZ(packed));
            builder.setPiece(index, pieces[index]);
        }
        return builder.build();
    }
}
