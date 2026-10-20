package com.komainu.moxia.blueprint;

import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Color de cada bloque en la miniatura: el color medio de la tabla de colores ({@code BlockColorCatalog}), salvo los
 * pocos que la tabla deja grises porque Minecraft los tiñe por bioma (agua, hierba alta, nenúfar) o que se ven sobre
 * todo por arriba (el césped). Los bloques invisibles (barrera, luz, vacío de estructura) no se pintan.
 */
public final class ThumbnailColors {
    private static final Map<String, Integer> OVERRIDES = Map.of(
            "minecraft:water", 0x3F76E4,
            "minecraft:bubble_column", 0x3F76E4,
            "minecraft:grass_block", 0x6E9A3E,
            "minecraft:tall_grass", 0x5D8A37,
            "minecraft:large_fern", 0x58833A,
            "minecraft:lily_pad", 0x2E7D32
    );
    private static final Set<String> HIDDEN = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:barrier", "minecraft:light", "minecraft:structure_void");

    private ThumbnailColors() {
    }

    /**
     * @param catalog color de la tabla para un id, o {@code -1} si no lo tiene
     * @return {@code 0xRRGGBB}, o {@code -1} si el bloque no se pinta
     */
    public static int color(String id, ToIntFunction<String> catalog) {
        if (HIDDEN.contains(id)) {
            return -1;
        }
        Integer override = OVERRIDES.get(id);
        return override != null ? override : catalog.applyAsInt(id);
    }
}
