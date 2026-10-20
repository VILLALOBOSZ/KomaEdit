package com.komainu.moxia.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Huella de un plano para apoyarlo en el suelo: por cada columna (x, z) local, la altura de su bloque más bajo.
 * Las coordenadas son las del plano con el origen en el centro de su base.
 */
public record StampShape(List<Column> columns, int lowest, int height, double radius) {
    public record Column(int x, int z, int bottom) {
    }

    public StampShape {
        columns = List.copyOf(columns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("El plano no tiene bloques");
        }
    }

    /** La huella de unos bloques sólidos dados por sus coordenadas locales. */
    public static StampShape of(int[] xs, int[] ys, int[] zs, int count) {
        Map<Long, Integer> bottoms = new TreeMap<>();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        double radius = 0;
        for (int index = 0; index < count; index++) {
            long key = ((long) xs[index] << 32) | (zs[index] & 0xFFFFFFFFL);
            bottoms.merge(key, ys[index], Math::min);
            lowest = Math.min(lowest, ys[index]);
            highest = Math.max(highest, ys[index]);
            radius = Math.max(radius, Math.hypot(xs[index], zs[index]));
        }
        List<Column> columns = new ArrayList<>(bottoms.size());
        for (Map.Entry<Long, Integer> entry : bottoms.entrySet()) {
            long key = entry.getKey();
            columns.add(new Column((int) (key >> 32), (int) key, entry.getValue()));
        }
        return new StampShape(columns, lowest, highest - lowest + 1, radius);
    }
}
