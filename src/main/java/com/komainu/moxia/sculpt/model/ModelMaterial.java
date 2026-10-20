package com.komainu.moxia.sculpt.model;

import com.sk89q.worldedit.world.block.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * De dónde sale el material de un pincel de modelado: el de alrededor, una paleta de bloques (repartida al azar
 * fijo por posición), un patrón de WorldEdit (el de {@code /komaedit sculpt}, que pone {@code BatchEditService}) o
 * aire (el relleno de las grietas).
 */
public record ModelMaterial(List<BlockState> palette, String pattern, boolean air) {
    public static final ModelMaterial SURROUNDING = new ModelMaterial(List.of(), null, false);
    public static final ModelMaterial AIR = new ModelMaterial(List.of(), null, true);

    public ModelMaterial {
        palette = List.copyOf(palette);
    }

    public static ModelMaterial palette(List<BlockState> states) {
        if (states.isEmpty()) {
            throw new IllegalArgumentException("La paleta está vacía");
        }
        return new ModelMaterial(states, null, false);
    }

    public static ModelMaterial pattern(String pattern) {
        return new ModelMaterial(List.of(), pattern, false);
    }

    /** ¿Pide el texto el material de alrededor? ({@code auto}, {@code alrededor}, {@code surrounding}). */
    public static boolean surrounding(String input) {
        return input != null && switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "auto", "alrededor", "surrounding", "entorno" -> true;
            default -> false;
        };
    }

    /** ¿Pide el texto aire? ({@code air}, {@code aire}). */
    public static boolean air(String input) {
        return input != null && switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "air", "aire", "minecraft:air" -> true;
            default -> false;
        };
    }

    public String describe() {
        if (air) {
            return "aire";
        }
        if (pattern != null) {
            return pattern;
        }
        return palette.isEmpty() ? "el de alrededor" : palette.size() + " bloque(s) de paleta";
    }
}
