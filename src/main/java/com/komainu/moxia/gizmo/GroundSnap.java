package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.OptionalInt;

/**
 * «Apoyar en el suelo»: cuánto hay que bajar (o subir) el objeto para que descanse sobre el terreno. Por cada columna
 * de su huella se busca el suelo bajo su bloque más bajo; manda la columna con el apoyo más alto, así ninguna se
 * hunde. Si el objeto está medio enterrado, sube hasta asomar (como mucho su altura). Lógica pura: el cliente le
 * pasa su mundo.
 */
public final class GroundSnap {
    /** Cuánto se busca hacia abajo. */
    public static final int REACH = 256;
    /** Columnas como mucho (si hay más, una muestra repartida). */
    public static final int MAX_COLUMNS = 2_048;

    private GroundSnap() {
    }

    /** Si hay un bloque con el que se choca (el suelo) en esa posición. */
    @FunctionalInterface
    public interface Terrain {
        boolean solid(int x, int y, int z);
    }

    /**
     * La huella de un objeto ya girado: el bloque más bajo de cada columna, relativo al pivote.
     *
     * @param x   columna
     * @param z   columna
     * @param low altura local del bloque más bajo de la columna
     */
    public record Footprint(int[] x, int[] z, int[] low, int height) {
        public int columns() {
            return x.length;
        }
    }

    /** La huella de unas posiciones locales ya giradas (las que no son aire). */
    public static Footprint footprint(long[] oriented) {
        Long2IntOpenHashMap lowest = new Long2IntOpenHashMap();
        lowest.defaultReturnValue(Integer.MAX_VALUE);
        int top = Integer.MIN_VALUE;
        int bottom = Integer.MAX_VALUE;
        for (long packed : oriented) {
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            long column = (long) x << 32 | (z & 0xFFFFFFFFL);
            if (y < lowest.get(column)) {
                lowest.put(column, y);
            }
            top = Math.max(top, y);
            bottom = Math.min(bottom, y);
        }
        int count = lowest.size();
        int stride = Math.max(1, (count + MAX_COLUMNS - 1) / MAX_COLUMNS);
        int kept = (count + stride - 1) / stride;
        int[] xs = new int[kept];
        int[] zs = new int[kept];
        int[] lows = new int[kept];
        long[] keys = lowest.keySet().toLongArray();
        java.util.Arrays.sort(keys);
        int slot = 0;
        for (int index = 0; index < keys.length && slot < kept; index += stride) {
            long key = keys[index];
            xs[slot] = (int) (key >> 32);
            zs[slot] = (int) key;
            lows[slot] = lowest.get(key);
            slot++;
        }
        return new Footprint(xs, zs, lows, count == 0 ? 0 : top - bottom + 1);
    }

    /**
     * Cuánto mover en Y el objeto colocado con {@code transform} para que descanse en el suelo, o vacío si no hay
     * suelo bajo ninguna columna.
     */
    public static OptionalInt rest(Footprint footprint, GhostTransform transform, Terrain terrain) {
        int best = Integer.MIN_VALUE;
        int lift = Math.max(1, footprint.height());
        for (int column = 0; column < footprint.columns(); column++) {
            int x = transform.x() + footprint.x()[column];
            int z = transform.z() + footprint.z()[column];
            int bottom = transform.y() + footprint.low()[column];
            int y = bottom - 1;
            Integer top = null;
            if (terrain.solid(x, y, z)) {
                // Enterrado: sube hasta el primer hueco, sin pasar de su altura.
                while (y + 1 <= bottom - 1 + lift && terrain.solid(x, y + 1, z)) {
                    y++;
                }
                top = y;
            } else {
                for (int depth = 0; depth < REACH; depth++) {
                    y--;
                    if (terrain.solid(x, y, z)) {
                        top = y;
                        break;
                    }
                }
            }
            if (top != null) {
                best = Math.max(best, top + 1 - bottom);
            }
        }
        return best == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(best);
    }
}
