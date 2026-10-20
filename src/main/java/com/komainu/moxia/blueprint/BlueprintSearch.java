package com.komainu.moxia.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Búsqueda por nombre y etiqueta, igual en {@code /komaedit blueprint search} y en el buscador del Studio. Cada
 * palabra tiene que casar: {@code #rocas} pide esa etiqueta exacta y una palabra suelta casa con un trozo del
 * nombre o con el principio de una etiqueta. Sin tildes ni mayúsculas.
 */
public final class BlueprintSearch {
    private final List<String> words;
    private final List<String> tags;

    private BlueprintSearch(List<String> words, List<String> tags) {
        this.words = List.copyOf(words);
        this.tags = List.copyOf(tags);
    }

    public static BlueprintSearch parse(String query) {
        List<String> words = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        if (query != null) {
            for (String part : query.trim().split("[\\s,]+")) {
                if (part.isEmpty() || part.equals("#")) {
                    continue;
                }
                if (part.startsWith("#")) {
                    tags.add(BlueprintTags.normalize(part));
                } else {
                    words.add(BlueprintTags.fold(part).replace(' ', '_'));
                }
            }
        }
        return new BlueprintSearch(words, tags);
    }

    public boolean isEmpty() {
        return words.isEmpty() && tags.isEmpty();
    }

    public List<String> tags() {
        return tags;
    }

    public boolean matches(String name, List<String> blueprintTags) {
        for (String tag : tags) {
            if (!blueprintTags.contains(tag)) {
                return false;
            }
        }
        String folded = BlueprintTags.fold(name);
        for (String word : words) {
            if (folded.contains(word)) {
                continue;
            }
            boolean tagged = false;
            for (String tag : blueprintTags) {
                if (tag.startsWith(word)) {
                    tagged = true;
                    break;
                }
            }
            if (!tagged) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(BlueprintMeta meta) {
        return matches(meta.name(), meta.tags());
    }

    /**
     * Filtra y ordena: primero el nombre exacto, después los que empiezan por la búsqueda y luego el resto; dentro
     * de cada grupo, por nombre y con los propios antes que los compartidos.
     */
    public <T> List<T> filter(List<T> items, Function<T, BlueprintMeta> meta) {
        String first = words.isEmpty() ? "" : words.getFirst();
        Comparator<T> order = Comparator.<T>comparingInt(item -> rank(meta.apply(item).name(), first))
                .thenComparing(item -> meta.apply(item).name())
                .thenComparing(item -> meta.apply(item).scope());
        return items.stream().filter(item -> matches(meta.apply(item))).sorted(order).toList();
    }

    private static int rank(String name, String first) {
        if (first.isEmpty()) {
            return 0;
        }
        String folded = BlueprintTags.fold(name);
        return folded.equals(first) ? 0 : folded.startsWith(first) ? 1 : 2;
    }
}
