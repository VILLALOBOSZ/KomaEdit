package com.komainu.moxia.shape.smooth;

import java.util.List;
import java.util.Locale;

/**
 * Tipo de pieza de un perfil de smoothblocks. Es la clave de las sobreescrituras de material:
 * {@code -w SlabsAndStairs(Slab:acacia,Stair:spruce)}.
 */
public enum PieceVariant {
    SLAB("Slab", "_slab", "losa", "slab", "slabs", "losas"),
    STAIR("Stair", "_stairs", "escalera", "stair", "stairs", "escaleras"),
    WALL("Wall", "_wall", "muro", "wall", "walls", "muros", "valla", "fence"),
    LAYER("Layer", "_layer", "capa", "layer", "layers", "capas", "snow", "nieve"),
    /** Panel de vidrio o barrotes de hierro, unido a sus vecinos. */
    PANE("Pane", "_pane", "panel", "pane", "panes", "paneles", "bars", "barrotes", "glass_pane");

    private final String key;
    private final String suffix;
    private final String displayName;
    private final List<String> aliases;

    PieceVariant(String key, String suffix, String displayName, String... aliases) {
        this.key = key;
        this.suffix = suffix;
        this.displayName = displayName;
        java.util.List<String> names = new java.util.ArrayList<>(List.of(aliases));
        names.add(displayName);
        names.add(key.toLowerCase(Locale.ROOT));
        this.aliases = List.copyOf(names);
    }

    /** Clave de ezEdits en los paréntesis del perfil ({@code Slab}, {@code Stair}…). */
    public String key() {
        return key;
    }

    /** Sufijo del identificador de la pieza en Minecraft ({@code stone} → {@code stone_slab}). */
    public String suffix() {
        return suffix;
    }

    public String displayName() {
        return displayName;
    }

    /** @return la variante, o {@code null} si el texto no nombra ninguna */
    public static PieceVariant find(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (PieceVariant variant : values()) {
            if (variant.aliases.contains(normalized)) {
                return variant;
            }
        }
        return null;
    }
}
