package com.komainu.moxia.gradient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Paletas ordenadas con la notación de ezEdits, compatible con las listas {@code a;b;c} que ya usaba KomaEdit.
 *
 * <ul>
 *     <li>{@code ##Nombre}: paleta de serie ({@link GradientPalette}); {@code #nombre}: paleta guardada del jugador.</li>
 *     <li>{@code ,} o {@code ;}: concatena. {@code -X}: invierte. {@code X(a:b)}: tramo de la posición a a la b, con
 *     las dos incluidas y contando desde 1. {@code X*n}: repite n veces. {@code [ ... ]}: agrupa.</li>
 *     <li>Cualquier otra cosa es un bloque o patrón de WorldEdit, incluidos estados como
 *     {@code oak_stairs[facing=east]}. Un {@code =} final se ignora.</li>
 * </ul>
 */
public final class PaletteSpec {
    public static final int MAX_ENTRIES = 256;
    private static final int MAX_LENGTH = 1_024;

    private PaletteSpec() {
    }

    public static List<String> parse(String input) {
        return parse(input, null);
    }

    /**
     * @param userPalettes busca las paletas guardadas ({@code #nombre}); {@code null} si no hay almacén disponible
     */
    public static List<String> parse(String input, Function<String, String> userPalettes) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("La paleta está vacía");
        }
        String text = input.trim();
        if (text.endsWith("=")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("La paleta admite como mucho " + MAX_LENGTH + " caracteres");
        }
        Parser parser = new Parser(text, userPalettes, 0);
        List<String> result = parser.list();
        if (parser.position < text.length()) {
            throw new IllegalArgumentException("Sobra «" + text.substring(parser.position) + "» en la paleta");
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("La paleta no contiene bloques");
        }
        return List.copyOf(result);
    }

    /** Une una paleta como la lista {@code a;b;c} que entienden los degradados. */
    public static String join(List<String> entries) {
        return String.join(";", entries);
    }

    private static final class Parser {
        private final String text;
        private final Function<String, String> userPalettes;
        private final int depth;
        private int position;

        private Parser(String text, Function<String, String> userPalettes, int depth) {
            if (depth > 8) {
                throw new IllegalArgumentException("La paleta anida demasiadas paletas guardadas");
            }
            this.text = text;
            this.userPalettes = userPalettes;
            this.depth = depth;
        }

        List<String> list() {
            List<String> result = new ArrayList<>();
            skipSpaces();
            if (position >= text.length() || peek() == ']') {
                return result;
            }
            result.addAll(item());
            while (true) {
                skipSpaces();
                if (position < text.length() && (peek() == ',' || peek() == ';')) {
                    position++;
                    result.addAll(item());
                    check(result);
                } else {
                    return result;
                }
            }
        }

        private List<String> item() {
            skipSpaces();
            boolean invert = false;
            if (position < text.length() && peek() == '-'
                    && position + 1 < text.length() && !Character.isDigit(text.charAt(position + 1))) {
                invert = true;
                position++;
            }
            List<String> value = atom();
            while (true) {
                skipSpaces();
                if (position < text.length() && peek() == '(') {
                    value = slice(value);
                } else if (position < text.length() && peek() == '*') {
                    position++;
                    int times = integer();
                    if (times < 1 || times > MAX_ENTRIES) {
                        throw new IllegalArgumentException("La repetición debe estar entre 1 y " + MAX_ENTRIES);
                    }
                    List<String> repeated = new ArrayList<>(value.size() * times);
                    for (int copy = 0; copy < times; copy++) {
                        repeated.addAll(value);
                    }
                    check(repeated);
                    value = repeated;
                } else {
                    break;
                }
            }
            if (invert) {
                value = new ArrayList<>(value);
                Collections.reverse(value);
            }
            return value;
        }

        private List<String> atom() {
            skipSpaces();
            if (position >= text.length()) {
                throw new IllegalArgumentException("Falta un bloque o una paleta al final de la paleta");
            }
            if (peek() == '[') {
                position++;
                List<String> group = list();
                skipSpaces();
                if (position >= text.length() || peek() != ']') {
                    throw new IllegalArgumentException("Falta el ] de cierre en la paleta");
                }
                position++;
                return group;
            }
            if (text.startsWith("##", position)) {
                position += 2;
                String name = name();
                return preset(name);
            }
            if (peek() == '#') {
                position++;
                String name = name();
                return user(name);
            }
            return List.of(block());
        }

        private List<String> slice(List<String> value) {
            position++;
            int start = integer();
            skipSpaces();
            if (position >= text.length() || peek() != ':') {
                throw new IllegalArgumentException("El tramo de paleta se escribe (inicio:fin), por ejemplo (1:8)");
            }
            position++;
            int end = integer();
            skipSpaces();
            if (position >= text.length() || peek() != ')') {
                throw new IllegalArgumentException("Falta el ) de cierre del tramo de paleta");
            }
            position++;
            if (start < 1 || end < start || end > value.size()) {
                throw new IllegalArgumentException("El tramo (" + start + ":" + end + ") no cabe en una paleta de "
                        + value.size() + " bloques");
            }
            return new ArrayList<>(value.subList(start - 1, end));
        }

        private List<String> preset(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (GradientPalette palette : GradientPalette.values()) {
                if (palette.id().equals(normalized) || palette.displayName().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return List.of(palette.patterns().split(";"));
                }
            }
            try {
                return List.of(GradientPalette.byId(name).patterns().split(";"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("No existe la paleta de serie ##" + name);
            }
        }

        private List<String> user(String name) {
            if (userPalettes == null) {
                throw new IllegalArgumentException("Aquí no se pueden usar paletas guardadas (#" + name + ")");
            }
            String definition = userPalettes.apply(name.toLowerCase(Locale.ROOT));
            if (definition == null) {
                throw new IllegalArgumentException("No tienes ninguna paleta guardada llamada #" + name);
            }
            Parser nested = new Parser(definition, userPalettes, depth + 1);
            List<String> result = nested.list();
            if (nested.position < definition.length()) {
                throw new IllegalArgumentException("La paleta guardada #" + name + " no es válida");
            }
            return result;
        }

        private String name() {
            int start = position;
            while (position < text.length()
                    && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '-')) {
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("Falta el nombre de la paleta tras #");
            }
            return text.substring(start, position);
        }

        /** Un bloque o patrón: llega hasta un separador; los [] pegados al nombre son estados del bloque. */
        private String block() {
            int start = position;
            int brackets = 0;
            while (position < text.length()) {
                char current = peek();
                if (current == '[' && position > start) {
                    brackets++;
                } else if (current == ']') {
                    if (brackets == 0) {
                        break;
                    }
                    brackets--;
                } else if (brackets == 0 && (current == ',' || current == ';' || current == '('
                        || current == '*' || Character.isWhitespace(current))) {
                    break;
                }
                position++;
            }
            String block = text.substring(start, position).trim();
            if (block.isEmpty()) {
                throw new IllegalArgumentException("Hay un hueco vacío en la paleta");
            }
            return block;
        }

        private int integer() {
            skipSpaces();
            int start = position;
            while (position < text.length() && Character.isDigit(peek())) {
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("Se esperaba un número en la paleta");
            }
            return Integer.parseInt(text.substring(start, Math.min(position, start + 6)));
        }

        private void skipSpaces() {
            while (position < text.length() && Character.isWhitespace(peek())) {
                position++;
            }
        }

        private char peek() {
            return text.charAt(position);
        }

        private static void check(List<String> entries) {
            if (entries.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("La paleta supera " + MAX_ENTRIES + " entradas");
            }
        }
    }
}
