package com.komainu.moxia.shape.smooth;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Materiales fijos por tipo de pieza ({@code -w SlabsAndStairs(Slab:acacia,Stair:spruce)}). Sin entrada, la pieza
 * sale del bloque que pone el patrón ({@link SmoothMaterials}). Guarda el texto tal cual; el servidor lo convierte
 * en bloque al validar el comando. Es inmutable.
 */
public final class PieceMaterials {
    public static final PieceMaterials NONE = new PieceMaterials(new EnumMap<>(PieceVariant.class));

    private final EnumMap<PieceVariant, String> materials;

    private PieceMaterials(EnumMap<PieceVariant, String> materials) {
        this.materials = materials;
    }

    public static PieceMaterials of(Map<PieceVariant, String> materials) {
        if (materials.isEmpty()) {
            return NONE;
        }
        EnumMap<PieceVariant, String> copy = new EnumMap<>(PieceVariant.class);
        materials.forEach((variant, material) -> {
            String text = material == null ? "" : material.trim();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Falta el material de " + variant.key());
            }
            copy.put(variant, text);
        });
        return new PieceMaterials(copy);
    }

    /** @return el material fijado para la variante, o {@code null} */
    public String get(PieceVariant variant) {
        return variant == null ? null : materials.get(variant);
    }

    public boolean isEmpty() {
        return materials.isEmpty();
    }

    public Map<PieceVariant, String> asMap() {
        return Map.copyOf(materials);
    }

    /** {@code Slab:acacia,Stair:spruce}, en el orden de las variantes; vacío si no hay ninguno. */
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",");
        materials.forEach((variant, material) -> joiner.add(variant.key() + ":" + material));
        return joiner.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PieceMaterials that && materials.equals(that.materials);
    }

    @Override
    public int hashCode() {
        return materials.hashCode();
    }
}
