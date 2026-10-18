package com.komainu.moxia.shape.smooth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de pruebas con los bloques vanilla de la tabla de colores ({@code colors.txt}): la variante sale del
 * sufijo del nombre, como en el juego ({@code _slab}, {@code _stairs}, {@code _wall}/{@code _fence}, y
 * {@code snow} para las capas).
 */
public final class VanillaCatalog implements SmoothMaterials.Catalog {
    public static final VanillaCatalog INSTANCE = new VanillaCatalog();

    private final Map<String, Integer> colors = SmoothMaterials.textureColors();
    private final Map<PieceVariant, List<String>> pieces = new EnumMap<>(PieceVariant.class);

    private VanillaCatalog() {
        for (PieceVariant variant : PieceVariant.values()) {
            pieces.put(variant, new ArrayList<>());
        }
        colors.keySet().stream().sorted().forEach(id -> {
            PieceVariant variant = variantOf(id);
            if (variant != null && !id.contains("petrified_") && !id.contains(":waxed_") && !id.endsWith("_fence")) {
                pieces.get(variant).add(id);
            }
        });
    }

    @Override
    public boolean exists(String id) {
        return colors.containsKey(id);
    }

    @Override
    public boolean is(String id, PieceVariant variant) {
        return exists(id) && variantOf(id) == variant;
    }

    @Override
    public List<String> pieces(PieceVariant variant) {
        return pieces.get(variant);
    }

    @Override
    public int color(String id) {
        return colors.getOrDefault(id, -1);
    }

    private static PieceVariant variantOf(String id) {
        if (id.endsWith("_slab")) {
            return PieceVariant.SLAB;
        }
        if (id.endsWith("_stairs")) {
            return PieceVariant.STAIR;
        }
        if (id.endsWith("_wall") || id.endsWith("_fence")) {
            return PieceVariant.WALL;
        }
        return id.equals("minecraft:snow") ? PieceVariant.LAYER : null;
    }
}
