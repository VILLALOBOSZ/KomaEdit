package com.komainu.moxia.deform;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Máscara de bloques de la bandera {@code -m} de superficie: una lista separada por comas de ids
 * ({@code stone}, {@code minecraft:grass_block}) y etiquetas ({@code #minecraft:logs}). Con {@code !} delante, la
 * lista se invierte. Las etiquetas se resuelven en el hilo del servidor y la máscara ya resuelta se usa en el
 * worker.
 */
public final class BlockMatcher {
    private final Set<String> ids;
    private final boolean inverted;
    private final String source;

    private BlockMatcher(Set<String> ids, boolean inverted, String source) {
        this.ids = Set.copyOf(ids);
        this.inverted = inverted;
        this.source = source;
    }

    /**
     * @param tags devuelve los ids de bloque de una etiqueta (sin {@code #}, con espacio de nombres) o lanza si no
     *             existe
     */
    public static BlockMatcher parse(String input, Function<String, Set<String>> tags) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta la máscara, por ejemplo stone,dirt o #minecraft:logs");
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        boolean inverted = text.startsWith("!");
        if (inverted) {
            text = text.substring(1);
        }
        Set<String> ids = new HashSet<>();
        for (String entry : text.split(",")) {
            String token = entry.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("#")) {
                ids.addAll(tags.apply(namespaced(token.substring(1))));
            } else {
                ids.add(namespaced(token));
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("La máscara no contiene ningún bloque: " + input);
        }
        return new BlockMatcher(ids, inverted, input.trim());
    }

    /** Ids de bloque de la lista, ya con las etiquetas resueltas. */
    public Set<String> ids() {
        return ids;
    }

    public boolean inverted() {
        return inverted;
    }

    public boolean test(String blockId) {
        return ids.contains(namespaced(blockId)) != inverted;
    }

    static String namespaced(String id) {
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        int bracket = trimmed.indexOf('[');
        if (bracket >= 0) {
            trimmed = trimmed.substring(0, bracket);
        }
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    @Override
    public String toString() {
        return source;
    }
}
