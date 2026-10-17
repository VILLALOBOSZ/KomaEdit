package com.komainu.moxia.shape.smooth;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * Forma de cada escalera según la regla de Minecraft ({@code StairsBlock.getStairShape}).
 *
 * <p>Al colocar bloques, WorldEdit avisa a los vecinos y cada escalera vuelve a calcular su forma con las
 * escaleras de alrededor. Si el plan ya trae la forma que calcularía Minecraft, el mundo queda igual que el
 * holograma y nada cambia después. La regla solo mira orientación y mitad de los vecinos, no su forma, así que
 * una pasada basta.</p>
 */
public final class StairShapes {
    private StairShapes() {
    }

    public static void resolve(Long2ByteOpenHashMap pieces) {
        long[] keys = new long[pieces.size()];
        byte[] shaped = new byte[pieces.size()];
        int count = 0;
        ObjectIterator<Long2ByteMap.Entry> entries = pieces.long2ByteEntrySet().fastIterator();
        while (entries.hasNext()) {
            Long2ByteMap.Entry entry = entries.next();
            byte piece = entry.getByteValue();
            if (!SmoothPiece.isStairs(piece)) {
                continue;
            }
            long key = entry.getLongKey();
            keys[count] = key;
            shaped[count] = SmoothPiece.withShape(piece, shape(pieces, key, piece));
            count++;
        }
        for (int index = 0; index < count; index++) {
            pieces.put(keys[index], shaped[index]);
        }
    }

    static int shape(Long2ByteMap pieces, long key, byte piece) {
        int x = PackedBlockPlan.unpackX(key);
        int y = PackedBlockPlan.unpackY(key);
        int z = PackedBlockPlan.unpackZ(key);
        int facing = SmoothPiece.facing(piece);
        boolean top = SmoothPiece.isTop(piece);

        byte front = neighbor(pieces, x, y, z, facing);
        if (SmoothPiece.isStairs(front) && SmoothPiece.isTop(front) == top) {
            int frontFacing = SmoothPiece.facing(front);
            if ((frontFacing & 1) != (facing & 1)
                    && differentOrientation(pieces, x, y, z, SmoothPiece.opposite(frontFacing), facing, top)) {
                return frontFacing == SmoothPiece.counterclockwise(facing)
                        ? SmoothPiece.OUTER_LEFT
                        : SmoothPiece.OUTER_RIGHT;
            }
        }
        byte back = neighbor(pieces, x, y, z, SmoothPiece.opposite(facing));
        if (SmoothPiece.isStairs(back) && SmoothPiece.isTop(back) == top) {
            int backFacing = SmoothPiece.facing(back);
            if ((backFacing & 1) != (facing & 1)
                    && differentOrientation(pieces, x, y, z, backFacing, facing, top)) {
                return backFacing == SmoothPiece.counterclockwise(facing)
                        ? SmoothPiece.INNER_LEFT
                        : SmoothPiece.INNER_RIGHT;
            }
        }
        return SmoothPiece.STRAIGHT;
    }

    private static boolean differentOrientation(
            Long2ByteMap pieces,
            int x,
            int y,
            int z,
            int direction,
            int facing,
            boolean top
    ) {
        byte other = neighbor(pieces, x, y, z, direction);
        return !SmoothPiece.isStairs(other)
                || SmoothPiece.facing(other) != facing
                || SmoothPiece.isTop(other) != top;
    }

    private static byte neighbor(Long2ByteMap pieces, int x, int y, int z, int direction) {
        long key = PackedBlockPlan.pack(x + SmoothPiece.offsetX(direction), y, z + SmoothPiece.offsetZ(direction));
        return pieces.containsKey(key) ? pieces.get(key) : SmoothPiece.FULL;
    }
}
