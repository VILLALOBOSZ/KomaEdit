package com.komainu.moxia.blueprint;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Etiquetas de los planos ({@code rocas}, {@code bosque}, {@code ruinas}…): sin mayúsculas ni tildes, con letras,
 * números, {@code _} y {@code -}. Se escriben separadas por espacios o comas, con o sin {@code #} delante.
 */
public final class BlueprintTags {
    public static final int MAX_TAGS = 8;
    public static final int MAX_LENGTH = 24;
    /** Empieza por letra o número: así {@code -v} o {@code -p} no se toman por etiquetas. */
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_-]{0," + (MAX_LENGTH - 1) + "}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,;|]+");

    private BlueprintTags() {
    }

    /** Una etiqueta escrita a mano en su forma guardada: {@code «#Árboles»} pasa a {@code arboles}. */
    public static String normalize(String raw) {
        String value = fold(raw == null ? "" : raw.trim());
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Etiqueta no válida: «" + raw + "» (de 1 a " + MAX_LENGTH
                    + " letras, números, _ o -, empezando por letra o número)");
        }
        return value;
    }

    /** Lista de etiquetas sin repetir, en el orden en que se escribieron. Vacía si no hay texto. */
    public static List<String> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String part : SEPARATORS.split(text.trim())) {
            if (!part.isEmpty() && !part.equals("#")) {
                tags.add(normalize(part));
            }
        }
        if (tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException("Como mucho " + MAX_TAGS + " etiquetas por plano");
        }
        return List.copyOf(new ArrayList<>(tags));
    }

    /** Minúsculas y sin tildes; la ñ queda como n. */
    public static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
