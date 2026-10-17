package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Búsqueda del bloque más cercano que cumple una condición, recorriendo una esfera de desplazamientos ordenados de
 * dentro afuera. Lo usa {@code #near}: se para en cuanto encuentra el primero.
 */
public final class NearSearch {
    public static final int MAX_DISTANCE = 16;
    private static final Map<Integer, int[]> OFFSETS = new ConcurrentHashMap<>();

    private NearSearch() {
    }

    /**
     * Desplazamientos dentro de una esfera de radio {@code radius}, sin el centro, ordenados por distancia. Cada uno
     * ocupa cuatro enteros: x, y, z y la distancia al cuadrado.
     */
    public static int[] offsets(int radius) {
        if (radius < 1 || radius > MAX_DISTANCE) {
            throw new IllegalArgumentException("La distancia debe estar entre 1 y " + MAX_DISTANCE);
        }
        return OFFSETS.computeIfAbsent(radius, NearSearch::build);
    }

    /**
     * Distancia al cuadrado del bloque más cercano que cumple {@code match}, o -1 si no hay ninguno dentro de
     * {@code maxDistance}. El propio bloque no cuenta.
     */
    public static int nearestSquared(BlockVector3 center, double maxDistance, Predicate<BlockVector3> match) {
        int radius = (int) Math.ceil(maxDistance);
        int limit = (int) Math.floor(maxDistance * maxDistance + 1.0E-9);
        int[] offsets = offsets(Math.max(1, radius));
        for (int index = 0; index < offsets.length; index += 4) {
            int squared = offsets[index + 3];
            if (squared > limit) {
                break;
            }
            if (match.test(center.add(offsets[index], offsets[index + 1], offsets[index + 2]))) {
                return squared;
            }
        }
        return -1;
    }

    private static int[] build(int radius) {
        List<int[]> entries = new ArrayList<>();
        int limit = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int squared = x * x + y * y + z * z;
                    if (squared > 0 && squared <= limit) {
                        entries.add(new int[]{x, y, z, squared});
                    }
                }
            }
        }
        entries.sort(Comparator.comparingInt(entry -> entry[3]));
        int[] result = new int[entries.size() * 4];
        for (int index = 0; index < entries.size(); index++) {
            System.arraycopy(entries.get(index), 0, result, index * 4, 4);
        }
        return result;
    }
}
