package com.komainu.moxia.color;

import com.komainu.moxia.texture.PaletteRemap;

import java.util.List;
import java.util.function.Predicate;

/**
 * El material entero de una pieza con forma: {@code stone_brick_stairs} → {@code stone_bricks},
 * {@code oak_slab} → {@code oak_planks}, {@code quartz_stairs} → {@code quartz_block}. Es la inversa por nombre de
 * {@code SmoothMaterials}: sirve para contar de qué materiales está hecha una selección aunque tenga escaleras y
 * losas.
 */
public final class ShapeFamilies {
    /** Sufijos que se prueban tras quitar la forma, por orden. */
    private static final List<String> ENDINGS = List.of("", "s", "_planks", "_block", "_bricks");

    private ShapeFamilies() {
    }

    /**
     * El bloque entero de la familia, o el propio id si no tiene forma o no se encuentra.
     *
     * @param exists si un id es un bloque entero registrado (así {@code bamboo_slab} no acaba en la caña de bambú)
     */
    public static String base(String id, Predicate<String> exists) {
        String shape = PaletteRemap.shapeOf(id);
        if (shape == null) {
            return id;
        }
        String stem = id.substring(0, id.length() - shape.length());
        for (String ending : ENDINGS) {
            String candidate = stem + ending;
            if (exists.test(candidate) && PaletteRemap.shapeOf(candidate) == null) {
                return candidate;
            }
        }
        return id;
    }

    /** ¿Es una pieza con forma (escalera, losa, muro, valla…)? */
    public static boolean shaped(String id) {
        return PaletteRemap.shapeOf(id) != null;
    }
}
