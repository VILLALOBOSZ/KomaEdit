package com.komainu.moxia.generator;

import java.util.List;
import java.util.Locale;

/**
 * Lo que un generador necesita saber de cada entrada de la paleta sin tocar Minecraft: cuántas hay y cuáles son
 * nieve en capas o líquidos (para el modo suave {@code -t}).
 */
public record PaletteTraits(int size, boolean[] snow, boolean[] liquid) {
    public PaletteTraits {
        if (size < 1 || snow.length != size || liquid.length != size) {
            throw new IllegalArgumentException("La paleta está vacía");
        }
    }

    /** A partir de los ids de bloque ({@code minecraft:snow}, {@code water}…). */
    public static PaletteTraits of(List<String> blockIds) {
        boolean[] snow = new boolean[blockIds.size()];
        boolean[] liquid = new boolean[blockIds.size()];
        for (int index = 0; index < blockIds.size(); index++) {
            String id = blockIds.get(index).toLowerCase(Locale.ROOT);
            int bracket = id.indexOf('[');
            if (bracket >= 0) {
                id = id.substring(0, bracket);
            }
            if (!id.contains(":")) {
                id = "minecraft:" + id;
            }
            snow[index] = id.equals("minecraft:snow");
            liquid[index] = id.equals("minecraft:water") || id.equals("minecraft:lava");
        }
        return new PaletteTraits(blockIds.size(), snow, liquid);
    }

    /** Entrada de la paleta para una fracción de 0 a 1. */
    public int index(double fraction) {
        return Math.clamp((int) Math.floor(fraction * size), 0, size - 1);
    }
}
