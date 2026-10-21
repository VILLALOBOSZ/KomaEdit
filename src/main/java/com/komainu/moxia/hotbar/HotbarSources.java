package com.komainu.moxia.hotbar;

import com.google.gson.JsonObject;
import com.komainu.moxia.capability.BlockFamilyIndex;
import com.komainu.moxia.gradient.BlockSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Barras hechas a partir de bloques: de una paleta, de un degradado de color, de los bloques más usados de la
 * selección o de la familia de un material (un tejado). Trabaja con ids en texto; el servidor le pasa qué objeto
 * corresponde a cada bloque.
 *
 * <p>Un bloque con estado ({@code oak_log[axis=x]}) da su objeto con el componente {@code minecraft:block_state}, que
 * hace que se coloque con ese estado, siempre que el objeto coloque ese mismo bloque (la antorcha de pared da una
 * antorcha normal, sin estado).</p>
 */
public final class HotbarSources {
    /** Orden de las piezas de una familia en la barra: lo que más se usa en un tejado y un muro, primero. */
    private static final List<String> FAMILY_ORDER = List.of("stairs", "slab", "wall", "fence", "fence_gate",
            "trapdoor", "door", "button", "pressure_plate", "sign", "wall_sign", "polished", "chiseled", "cut",
            "cracked", "mosaic");

    /**
     * Objeto de un bloque.
     *
     * @param item      id del objeto
     * @param sameBlock el objeto coloca exactamente ese bloque (y admite su estado)
     */
    public record ItemRef(String item, boolean sameBlock) {
    }

    /** La barra y los bloques que no se pudieron poner (sin objeto o que no eran un bloque). */
    public record Built(List<HotbarItem> slots, List<String> skipped) {
        public boolean isEmpty() {
            return slots.stream().allMatch(HotbarItem::isEmpty);
        }
    }

    private HotbarSources() {
    }

    /**
     * Una barra con los bloques de la lista, sin repetir objeto.
     *
     * @param itemOf objeto de cada bloque (por id con espacio de nombres), o {@code null} si no tiene
     * @param spread si hay más de nueve, se reparten de la primera a la última (una paleta o un degradado); si no,
     *               se toman los nueve primeros (los más usados)
     */
    public static Built fromBlocks(List<String> entries, Function<String, ItemRef> itemOf, boolean spread) {
        List<HotbarItem> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        for (String entry : entries) {
            BlockSpec spec;
            try {
                spec = BlockSpec.parse(entry);
            } catch (IllegalArgumentException exception) {
                skipped.add(entry);
                continue;
            }
            if (spec.isAir()) {
                continue;
            }
            ItemRef ref = itemOf.apply(spec.id());
            if (ref == null || ref.item() == null || ref.item().isBlank()) {
                skipped.add(spec.shortName());
                continue;
            }
            HotbarItem item = HotbarItem.of(ref.item(), 1, ref.sameBlock() ? stateComponent(spec) : "");
            if (seen.add(item.id() + item.components())) {
                unique.add(item);
            }
        }
        List<HotbarItem> chosen = spread ? sample(unique, SavedHotbar.SLOTS)
                : unique.subList(0, Math.min(unique.size(), SavedHotbar.SLOTS));
        List<HotbarItem> slots = new ArrayList<>(chosen);
        while (slots.size() < SavedHotbar.SLOTS) {
            slots.add(HotbarItem.EMPTY);
        }
        return new Built(List.copyOf(slots), List.copyOf(skipped));
    }

    /** Los bloques de más a menos usados (a igual cuenta, por id). */
    public static List<String> mostUsed(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Las piezas de la familia de un material, en el orden de una barra de tejado: el bloque, sus escaleras y losas,
     * y después muro, valla, trampilla… Sin familia, solo el material.
     */
    public static List<String> family(String material, BlockFamilyIndex.Family family) {
        List<String> blocks = new ArrayList<>();
        if (family == null) {
            blocks.add(material);
            return blocks;
        }
        blocks.add(family.base());
        for (String variant : FAMILY_ORDER) {
            String block = family.variants().get(variant);
            if (block != null && !blocks.contains(block)) {
                blocks.add(block);
            }
        }
        family.variants().values().stream().sorted().filter(block -> !blocks.contains(block)).forEach(blocks::add);
        // El material que se pidió va primero aunque sea una variante (cut_copper en la familia del cobre).
        if (!material.equals(family.base()) && blocks.remove(material)) {
            blocks.addFirst(material);
        }
        return blocks;
    }

    /** Como mucho {@code limit} elementos repartidos por igual, con el primero y el último. */
    static <T> List<T> sample(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }
        List<T> result = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            result.add(values.get((int) Math.round(index * (values.size() - 1) / (double) (limit - 1))));
        }
        return result;
    }

    private static String stateComponent(BlockSpec spec) {
        if (spec.properties().isEmpty()) {
            return "";
        }
        JsonObject state = new JsonObject();
        spec.properties().forEach(state::addProperty);
        JsonObject components = new JsonObject();
        components.add("minecraft:block_state", state);
        return HotbarJson.canonical(components);
    }
}
