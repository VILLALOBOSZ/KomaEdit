package com.komainu.moxia.image;

import com.komainu.moxia.shape.smooth.ColorMatch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Los bloques entre los que se elige cada píxel, con su color medio de textura ({@code 0xRRGGBB}). El más parecido
 * se busca en CIELAB (ΔE de 1976, {@link ColorMatch}).
 */
public final class BlockPalette {
    /** @param key lo que se colocará: un id de bloque o un estado como {@code minecraft:oak_log[axis=x]} */
    public record Entry(String key, int rgb) {
    }

    private final List<Entry> entries;
    private final double[] lab;

    public BlockPalette(List<Entry> entries) {
        List<Entry> unique = new ArrayList<>(entries.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (seen.add(entry.key())) {
                unique.add(entry);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("La paleta no tiene bloques con color conocido");
        }
        this.entries = List.copyOf(unique);
        this.lab = new double[this.entries.size() * 3];
        for (int index = 0; index < this.entries.size(); index++) {
            double[] value = ColorMatch.lab(this.entries.get(index).rgb());
            System.arraycopy(value, 0, lab, index * 3, 3);
        }
    }

    public int size() {
        return entries.size();
    }

    public Entry get(int index) {
        return entries.get(index);
    }

    public List<Entry> entries() {
        return entries;
    }

    public int rgb(int index) {
        return entries.get(index).rgb();
    }

    /** Índice del bloque más parecido a un color {@code 0xRRGGBB}. */
    public int nearest(int rgb) {
        double[] target = ColorMatch.lab(rgb);
        return nearestLab(target[0], target[1], target[2]);
    }

    int nearestLab(double l, double a, double b) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0, offset = 0; index < entries.size(); index++, offset += 3) {
            double dl = lab[offset] - l;
            double da = lab[offset + 1] - a;
            double db = lab[offset + 2] - b;
            double distance = dl * dl + da * da + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    static double rgbDistance(int first, int second) {
        double dr = (first >> 16 & 0xFF) - (second >> 16 & 0xFF);
        double dg = (first >> 8 & 0xFF) - (second >> 8 & 0xFF);
        double db = (first & 0xFF) - (second & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
