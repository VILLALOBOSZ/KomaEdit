package com.komainu.moxia.region;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reglas puras de {@code vines}, {@code moss}, {@code slabmerge} y {@code statecycle} (los comandos de región de
 * ezEdits), sin Minecraft para poder probarlas.
 */
public final class RegionRules {
    private static final Map<String, String> SLAB_TABLE = Map.of(
            "petrified_oak", "oak_planks",
            "smooth_stone", "smooth_stone",
            "smooth_quartz", "smooth_quartz",
            "cut_sandstone", "cut_sandstone",
            "cut_red_sandstone", "cut_red_sandstone"
    );
    /** Enredaderas que cuelgan con punta y tallo: el tallo es la versión {@code _plant}. */
    private static final Map<String, String> HANGING_PLANTS = Map.of(
            "minecraft:weeping_vines", "minecraft:weeping_vines_plant",
            "minecraft:cave_vines", "minecraft:cave_vines_plant"
    );

    private RegionRules() {
    }

    /** Bloque entero de una losa ({@code stone_brick_slab} → {@code stone_bricks}) o {@code null} si no se sabe. */
    public static String fullBlock(String slabId, Predicate<String> exists) {
        int colon = slabId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : slabId.substring(0, colon);
        String path = (colon < 0 ? slabId : slabId.substring(colon + 1)).toLowerCase(Locale.ROOT);
        if (!path.endsWith("_slab")) {
            return null;
        }
        String base = path.substring(0, path.length() - "_slab".length());
        List<String> candidates = new ArrayList<>();
        String mapped = SLAB_TABLE.get(base);
        if (mapped != null) {
            candidates.add(mapped);
        }
        candidates.add(base + "s");
        candidates.add(base);
        candidates.add(base + "_planks");
        candidates.add(base + "_block");
        for (String candidate : candidates) {
            String id = namespace + ":" + candidate;
            if (exists.test(id)) {
                return id;
            }
        }
        return null;
    }

    /** Tallo de una enredadera colgante ({@code weeping_vines} → {@code weeping_vines_plant}) o {@code null}. */
    public static String hangingPlant(String tipId) {
        return HANGING_PLANTS.get(tipId);
    }

    /** Siguiente valor de una propiedad tras {@code steps} pasos, dando la vuelta (admite pasos negativos). */
    public static int cycle(int size, int current, int steps) {
        if (size <= 0) {
            throw new IllegalArgumentException("La propiedad no tiene valores");
        }
        return Math.floorMod(current + steps, size);
    }

    /** Porcentaje escrito como {@code 10}, {@code 10%} o {@code 0.1%}: devuelve la fracción de 0 a 1. */
    public static double percentage(String input) {
        String text = input.trim();
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1);
        }
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("«" + input + "» no es un porcentaje");
        }
        if (!(value >= 0.0 && value <= 100.0)) {
            throw new IllegalArgumentException("El porcentaje va de 0 a 100");
        }
        return value / 100.0;
    }

    /** Radios de suavizado del musgo: uno para los tres ejes o tres separados por comas (este/oeste, arriba/abajo, norte/sur). */
    public static int[] radii(String input) {
        String[] parts = input.trim().split("[,;]");
        if (parts.length != 1 && parts.length != 3) {
            throw new IllegalArgumentException("Los radios son uno o tres números separados por comas, por ejemplo 2,1,2");
        }
        int[] radii = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            String part = parts[parts.length == 1 ? 0 : axis].trim();
            try {
                radii[axis] = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("«" + part + "» no es un radio entero");
            }
            if (radii[axis] < 0 || radii[axis] > 8) {
                throw new IllegalArgumentException("Cada radio va de 0 a 8");
            }
        }
        return radii;
    }

    /** Valor de 0 a 1 fijo por posición y semilla. */
    public static double unit(int x, int y, int z, long seed) {
        long value = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ seed;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    /**
     * Manchas de musgo: ruido blanco por posición suavizado {@code iterations} veces con la media de los candidatos
     * vecinos (caja de radios {@code radii}) y cortado para cubrir la parte que pide {@code amount}. Más iteraciones
     * o radios más grandes dan manchas más grandes y redondeadas.
     *
     * @param positions candidatos como {x, y, z}
     * @return qué candidatos llevan musgo
     */
    public static boolean[] moss(int[][] positions, double amount, int[] radii, int iterations, long seed,
                                 Runnable checkCancelled) {
        int count = positions.length;
        boolean[] chosen = new boolean[count];
        if (count == 0 || amount <= 0.0) {
            return chosen;
        }
        Long2IntOpenHashMap index = new Long2IntOpenHashMap(count);
        index.defaultReturnValue(-1);
        for (int candidate = 0; candidate < count; candidate++) {
            index.put(pack(positions[candidate][0], positions[candidate][1], positions[candidate][2]), candidate);
        }
        double[] values = new double[count];
        for (int candidate = 0; candidate < count; candidate++) {
            values[candidate] = unit(positions[candidate][0], positions[candidate][1], positions[candidate][2], seed);
        }
        int rounds = Math.clamp(iterations, 0, 32);
        for (int round = 0; round < rounds; round++) {
            double[] next = new double[count];
            for (int candidate = 0; candidate < count; candidate++) {
                checkCancelled.run();
                int[] position = positions[candidate];
                double total = 0.0;
                int neighbours = 0;
                for (int dy = -radii[1]; dy <= radii[1]; dy++) {
                    for (int dz = -radii[2]; dz <= radii[2]; dz++) {
                        for (int dx = -radii[0]; dx <= radii[0]; dx++) {
                            int other = index.get(pack(position[0] + dx, position[1] + dy, position[2] + dz));
                            if (other >= 0) {
                                total += values[other];
                                neighbours++;
                            }
                        }
                    }
                }
                next[candidate] = total / neighbours;
            }
            values = next;
        }
        double coverage = coverage(amount);
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int cut = (int) Math.round((1.0 - coverage) * count);
        if (cut >= count) {
            return chosen;
        }
        double threshold = sorted[Math.max(0, cut)];
        for (int candidate = 0; candidate < count; candidate++) {
            chosen[candidate] = values[candidate] >= threshold;
        }
        return chosen;
    }

    /** Parte de los candidatos que se cubre según la cantidad de ezEdits: 2 (por defecto) cubre la mitad. */
    public static double coverage(double amount) {
        return Math.clamp(1.0 - Math.exp(-Math.max(0.0, amount) / 2.885), 0.0, 1.0);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
