package com.komainu.moxia.share;

import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Los bloques escritos dentro de un patrón o una máscara de WorldEdit ({@code 60%stone,40%andesite},
 * {@code !air oak_stairs[facing=east]}), para cambiar los que no existen en este servidor sin tocar el resto. Lo que
 * empieza por {@code #}, {@code $} o {@code %} (paletas guardadas, el portapapeles, biomas, azar) se deja tal cual.
 * Las expresiones de WorldEdit ({@code =y<64}) no se aceptan en lo que llega de otro servidor.
 */
public final class PatternBlocks {
    private static final Pattern STATE = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?(\\[[^\\]\\[]*])?");
    private static final Pattern WEIGHT = Pattern.compile("[0-9]+(\\.[0-9]+)?%");

    private PatternBlocks() {
    }

    /**
     * Cambia cada bloque por lo que devuelva {@code block} (que recibe {@code id[propiedades]} tal y como está
     * escrito, en minúsculas).
     */
    public static String rewrite(String text, UnaryOperator<String> block) {
        StringBuilder result = new StringBuilder(text.length());
        int depth = 0;
        int start = 0;
        for (int index = 0; index <= text.length(); index++) {
            char character = index < text.length() ? text.charAt(index) : ',';
            if (character == '[' || character == '(' || character == '{') {
                depth++;
            } else if ((character == ']' || character == ')' || character == '}') && depth > 0) {
                depth--;
            }
            boolean separator = depth == 0 && (character == ',' || Character.isWhitespace(character));
            if (!separator && index < text.length()) {
                continue;
            }
            result.append(word(text.substring(start, index), block));
            if (index < text.length()) {
                result.append(character);
            }
            start = index + 1;
        }
        return result.toString();
    }

    private static String word(String word, UnaryOperator<String> block) {
        int position = 0;
        // Prefijos de máscara y de patrón: no, encima, debajo, cualquier estado, aplicar tipo.
        while (position < word.length() && "!<>*^".indexOf(word.charAt(position)) >= 0) {
            position++;
        }
        var weight = WEIGHT.matcher(word).region(position, word.length());
        if (weight.lookingAt()) {
            position = weight.end();
        }
        String rest = word.substring(position);
        if (rest.isEmpty() || "#$%[".indexOf(rest.charAt(0)) >= 0) {
            return word;
        }
        if (rest.charAt(0) == '=') {
            throw new IllegalArgumentException("Las expresiones de WorldEdit (=…) no se pueden importar");
        }
        String lower = rest.toLowerCase(Locale.ROOT);
        if (!STATE.matcher(lower).matches() || Character.isDigit(lower.charAt(0))) {
            return word;
        }
        return word.substring(0, position) + block.apply(lower);
    }
}
