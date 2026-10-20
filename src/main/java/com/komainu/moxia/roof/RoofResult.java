package com.komainu.moxia.roof;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.Arrays;
import java.util.List;

/**
 * Tejado calculado: cada posición con su pieza (losa, escalera o bloque entero) y su material (tejado, caballete,
 * muro o ventana), ordenadas por posición empaquetada para que el resultado sea siempre el mismo.
 *
 * @param mismatches piezas que no han podido quedar exactamente como pide la superficie (0 en los tejados
 *                   normales); las pruebas lo vigilan
 */
public record RoofResult(
        long[] positions,
        byte[] pieces,
        byte[] materials,
        int roofMinY,
        int roofMaxY,
        int dormers,
        int mismatches,
        List<String> notes
) {
    public static final byte ROOF = 0;
    public static final byte RIDGE = 1;
    public static final byte WALL = 2;
    /** Ventana de buhardilla en un muro que va de este a oeste (los paneles se unen por los lados este y oeste). */
    public static final byte WINDOW_X = 3;
    /** Ventana en un muro que va de norte a sur. */
    public static final byte WINDOW_Z = 4;

    public RoofResult {
        notes = List.copyOf(notes);
    }

    public int size() {
        return positions.length;
    }

    public int count(byte material) {
        int count = 0;
        for (byte value : materials) {
            if (value == material) {
                count++;
            }
        }
        return count;
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

    /** @return el índice de la posición, o un número negativo si no está */
    public int indexOf(int x, int y, int z) {
        return Arrays.binarySearch(positions, PackedBlockPlan.pack(x, y, z));
    }

    public byte pieceAt(int x, int y, int z) {
        int index = indexOf(x, y, z);
        return index < 0 ? SmoothPiece.EMPTY : pieces[index];
    }

    /** @return el material de la posición, o -1 si está vacía */
    public byte materialAt(int x, int y, int z) {
        int index = indexOf(x, y, z);
        return index < 0 ? -1 : materials[index];
    }

    public byte material(long packed) {
        int index = Arrays.binarySearch(positions, packed);
        return index < 0 ? ROOF : materials[index];
    }

    /**
     * Plan para la cola de construcción. Las posiciones leídas en la foto son condicionales: si alguien cambia el
     * bloque mientras se construye, se respeta.
     */
    public PackedBlockPlan plan(RoofWorld world) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(positions.length);
        for (int index = 0; index < positions.length; index++) {
            long packed = positions[index];
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            int expected = world.stateId(x, y, z);
            if (expected == PackedBlockPlan.NO_EXPECTED_STATE) {
                builder.addPattern(x, y, z);
            } else {
                builder.addConditionalPattern(BlockVector3.at(x, y, z), expected);
            }
            builder.setPiece(builder.size() - 1, pieces[index]);
        }
        return builder.build();
    }
}
