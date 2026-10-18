package com.komainu.moxia.color;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Paleta del sombreado sacada de la propia selección: los materiales de su superficie, de oscuro a claro.
 *
 * <p>Solo entran los materiales que cubren al menos {@link #MINIMUM_SHARE} de la superficie: los detalles sueltos
 * (unos ojos de piedra luminosa, un ribete de oro) no se reparten por la luz y se quedan donde están. Si queda un
 * único material, la paleta es una rampa de su mismo tono ({@link ColorGradient#ramp}) y solo se recolorea ese
 * material.</p>
 *
 * @param blocks  la paleta, de oscuro a claro
 * @param targets materiales enteros (sin forma) que se recolorean
 * @param ramp    si la paleta es una rampa generada alrededor de un material
 */
public record ShadePalette(List<BlockColor> blocks, Set<String> targets, boolean ramp) {
    /** Parte mínima de la superficie para que un material entre en la paleta. */
    public static final double MINIMUM_SHARE = 0.02;
    /** Materiales como mucho. */
    public static final int MAXIMUM = 16;

    public ShadePalette {
        blocks = List.copyOf(blocks);
        targets = Set.copyOf(targets);
    }

    /**
     * @param counts   bloques de superficie de cada material entero
     * @param rampSize bloques de la rampa si sale un solo material (o si se pide con {@code forceRamp})
     */
    public static ShadePalette fromCounts(Map<String, Integer> counts, BlockColorCatalog catalog, int rampSize,
                                          boolean forceRamp) {
        long total = 0;
        for (int count : counts.values()) {
            total += count;
        }
        if (total == 0) {
            throw new IllegalArgumentException("La selección no tiene bloques de superficie que sombrear: "
                    + "amplíala para que incluya el aire de alrededor");
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey));
        List<BlockColor> materials = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            BlockColor block = catalog.get(entry.getKey());
            if (block == null || entry.getValue() < MINIMUM_SHARE * total) {
                continue;
            }
            materials.add(block);
            if (materials.size() == MAXIMUM) {
                break;
            }
        }
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("No conozco el color de los materiales de la selección");
        }
        if (forceRamp || distinctColors(materials) < 2) {
            BlockColor dominant = materials.getFirst();
            List<BlockColor> ramp = ColorGradient.ramp(catalog, dominant, rampSize, ColorFilter.BUILDING);
            return new ShadePalette(ramp, Set.of(dominant.id()), true);
        }
        Set<String> targets = new LinkedHashSet<>();
        for (BlockColor block : materials) {
            targets.add(block.id());
        }
        return new ShadePalette(BlockColorCatalog.byLightness(materials), targets, false);
    }

    public List<String> ids() {
        return blocks.stream().map(BlockColor::id).toList();
    }

    /** «a, b, c» con los nombres cortos, de oscuro a claro. */
    public String describe() {
        return String.join(", ", blocks.stream().map(BlockColor::shortName).toList());
    }

    private static int distinctColors(List<BlockColor> blocks) {
        return (int) blocks.stream().map(BlockColor::rgb).distinct().count();
    }
}
