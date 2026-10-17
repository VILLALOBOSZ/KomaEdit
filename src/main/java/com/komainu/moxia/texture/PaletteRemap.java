package com.komainu.moxia.texture;

import com.komainu.moxia.shape.smooth.SmoothMaterials;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Posiciones de paleta de los bloques que ya hay y piezas con forma (escaleras, losas, muros…) de cada material.
 * Es Java puro: la existencia de los bloques se pregunta con {@code exists} (WorldEdit en el juego, una lista en
 * los tests).
 */
public final class PaletteRemap {
    /** Formas que conservan su forma al texturizar con {@code -w}; {@code _fence_gate} antes que {@code _fence}. */
    public static final List<String> SHAPES = List.of("_stairs", "_slab", "_wall", "_fence_gate", "_fence",
            "_pressure_plate", "_button", "_trapdoor");

    private final List<String> materials;
    private final Predicate<String> exists;
    private final Map<String, String> variants = new HashMap<>();
    private final Map<String, int[]> positions = new HashMap<>();

    /**
     * @param materials id con espacio de nombres de cada posición de la paleta ({@code minecraft:stone})
     */
    public PaletteRemap(List<String> materials, Predicate<String> exists) {
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("La paleta está vacía");
        }
        this.materials = List.copyOf(materials);
        this.exists = exists;
        for (int index = 0; index < this.materials.size(); index++) {
            add(this.materials.get(index), index);
        }
        for (String shape : SHAPES) {
            for (int index = 0; index < this.materials.size(); index++) {
                String variant = variant(index, shape);
                if (variant != null && !variant.equals(this.materials.get(index))) {
                    add(variant, index);
                }
            }
        }
    }

    public int size() {
        return materials.size();
    }

    public String material(int index) {
        return materials.get(index);
    }

    /** Sufijo de forma de un bloque ({@code _stairs}…) o {@code null} si es un bloque entero. */
    public static String shapeOf(String blockId) {
        for (String shape : SHAPES) {
            if (blockId.endsWith(shape)) {
                return shape;
            }
        }
        return null;
    }

    /** Pieza con forma del material de esa posición, o {@code null} si no existe. */
    public String variant(int index, String shape) {
        String material = materials.get(index);
        if (shape == null) {
            return material;
        }
        if (material.endsWith(shape)) {
            return material;
        }
        String key = index + shape;
        if (variants.containsKey(key)) {
            return variants.get(key);
        }
        String found = null;
        for (String candidate : SmoothMaterials.shapeIds(material, shape)) {
            if (exists.test(candidate)) {
                found = candidate;
                break;
            }
        }
        variants.put(key, found);
        return found;
    }

    /**
     * La posición más cercana a {@code index} cuyo material tiene esa forma (el «material más parecido» de
     * ezEdits), o -1 si ninguno la tiene. A igual distancia gana la del lado de {@code towards} (+1 o -1).
     */
    public int nearestWithShape(int index, String shape, int towards) {
        if (shape == null) {
            return index;
        }
        int direction = towards < 0 ? -1 : 1;
        for (int distance = 0; distance < materials.size(); distance++) {
            for (int side : new int[]{direction, -direction}) {
                int candidate = index + side * distance;
                if (candidate >= 0 && candidate < materials.size() && variant(candidate, shape) != null) {
                    return candidate;
                }
                if (distance == 0) {
                    break;
                }
            }
        }
        return -1;
    }

    /**
     * Posición de un bloque que ya está en el mundo (entero o con forma) o -1. Con el material repetido en la
     * paleta, {@code preference} elige la primera aparición (-1), la del centro (0) o la última (+1).
     */
    public int indexOf(String blockId, int preference) {
        int[] found = positions.get(blockId);
        if (found == null) {
            return -1;
        }
        if (preference < 0) {
            return found[0];
        }
        if (preference > 0) {
            return found[found.length - 1];
        }
        return found[found.length / 2];
    }

    private void add(String blockId, int index) {
        int[] current = positions.get(blockId);
        if (current == null) {
            positions.put(blockId, new int[]{index});
        } else if (current[current.length - 1] != index) {
            int[] grown = java.util.Arrays.copyOf(current, current.length + 1);
            grown[current.length] = index;
            positions.put(blockId, grown);
        }
    }

    /**
     * Posición de la paleta para un valor de 0 a 1. Con {@code dither} &gt; 0 el corte entre dos bloques vecinos de
     * la paleta se reparte con un umbral por posición, así los cambios no salen en franjas rectas.
     */
    public static int index(double value, int size, double dither, int x, int y, int z) {
        double scaled = Math.clamp(value, 0.0, 1.0) * (size - 1);
        int lower = Math.min(size - 1, (int) Math.floor(scaled));
        if (lower >= size - 1) {
            return size - 1;
        }
        double fraction = scaled - lower;
        double threshold = 0.5 + (hash(x, y, z) - 0.5) * dither;
        return fraction >= threshold ? lower + 1 : lower;
    }

    private static double hash(int x, int y, int z) {
        long value = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
