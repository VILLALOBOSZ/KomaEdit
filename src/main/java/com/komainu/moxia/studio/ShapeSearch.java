package com.komainu.moxia.studio;

import com.komainu.moxia.shape.PathShape;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Buscador de figuras del Studio: sin mayúsculas ni tildes, por nombre, id, alias y descripción.
 * Cada palabra de la búsqueda tiene que aparecer en algún sitio; el nombre pesa más que la descripción.
 */
public final class ShapeSearch {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_\\-]+");

    private ShapeSearch() {
    }

    /** Minúsculas, sin tildes y con los separadores reducidos a un espacio. */
    public static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String plain = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
        return SEPARATORS.matcher(plain).replaceAll(" ").trim();
    }

    /**
     * Puntuación de una figura para la búsqueda: 0 si alguna palabra no aparece. Una búsqueda vacía
     * puntúa 1 en todas para conservar el orden de la lista.
     */
    public static int score(PathShape shape, String query) {
        return score(shape, query, "");
    }

    /** Igual, contando además el nombre traducido que enseña el Studio con el mismo peso que el nombre. */
    public static int score(PathShape shape, String query, String localizedName) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return 1;
        }
        String name = normalize(shape.displayName());
        String localized = normalize(localizedName);
        String id = normalize(shape.id());
        List<String> aliases = shape.aliases().stream().map(ShapeSearch::normalize).toList();
        String description = normalize(shape.description());
        int total = 0;
        for (String token : normalized.split(" ")) {
            int best = 0;
            best = Math.max(best, fieldScore(name, token, 100));
            if (!localized.isEmpty()) {
                best = Math.max(best, fieldScore(localized, token, 100));
            }
            best = Math.max(best, fieldScore(id, token, 90));
            for (String alias : aliases) {
                best = Math.max(best, fieldScore(alias, token, 70));
            }
            best = Math.max(best, fieldScore(description, token, 20));
            if (best == 0) {
                return 0;
            }
            total += best;
        }
        return total;
    }

    /** Las figuras que encajan, de mejor a peor; a igual puntuación, en el orden recibido. */
    public static List<PathShape> search(List<PathShape> shapes, String query) {
        return search(shapes, query, shape -> "");
    }

    /** Búsqueda que también reconoce el nombre de la figura en el idioma del cliente. */
    public static List<PathShape> search(
            List<PathShape> shapes,
            String query,
            Function<PathShape, String> localizedName
    ) {
        List<Scored> scored = new ArrayList<>();
        for (int index = 0; index < shapes.size(); index++) {
            int value = score(shapes.get(index), query, localizedName.apply(shapes.get(index)));
            if (value > 0) {
                scored.add(new Scored(shapes.get(index), value, index));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::order));
        return scored.stream().map(Scored::shape).toList();
    }

    /** Palabra completa al principio, inicio de palabra o contenida, de más a menos. */
    private static int fieldScore(String field, String token, int weight) {
        if (field.equals(token)) {
            return weight + 30;
        }
        if (field.startsWith(token)) {
            return weight + 20;
        }
        if (field.contains(" " + token)) {
            return weight + 10;
        }
        return field.contains(token) ? weight : 0;
    }

    private record Scored(PathShape shape, int score, int order) {
    }
}
