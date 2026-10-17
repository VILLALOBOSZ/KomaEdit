package com.komainu.moxia.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Argumentos de los generadores con el estilo de banderas de ezEdits: valores posicionales y banderas de una letra
 * que se pueden agrupar ({@code -dhos}). Una bandera con valor toma la palabra siguiente ({@code -z 2}); si va en
 * un grupo tiene que ser la última letra ({@code -cz 2}).
 *
 * <p>El primer valor posicional nunca se lee como bandera, porque es el patrón o la paleta y una paleta invertida
 * empieza por guion ({@code -##Grayscale}). Tampoco lo es un número negativo ({@code -5}) ni un grupo con alguna
 * letra que el comando no conoce ({@code -stone,dirt}). Los paréntesis y las comillas agrupan palabras:
 * {@code Perlin(F:0.02, fO:3)} o {@code -x "x * 2 + z"} cuentan como una sola.</p>
 */
public final class GeneratorArgs {
    private static final int MAX_TOKENS = 64;

    private final List<String> positional;
    private final Map<Character, String> values;
    private final Set<Character> switches;

    private GeneratorArgs(List<String> positional, Map<Character, String> values, Set<Character> switches) {
        this.positional = positional;
        this.values = values;
        this.switches = switches;
    }

    public static GeneratorArgs parse(String text, String valued, String toggles) {
        return parse(text, valued, toggles, "", value -> false);
    }

    /**
     * @param valued   letras de las banderas que llevan valor
     * @param toggles  letras de las banderas sin valor
     * @param optional letras de las banderas cuyo valor es opcional: lo toman solo si {@code accepts} lo admite
     */
    public static GeneratorArgs parse(
            String text,
            String valued,
            String toggles,
            String optional,
            Predicate<String> accepts
    ) {
        List<String> tokens = tokenize(text);
        List<String> positional = new ArrayList<>();
        Map<Character, String> values = new HashMap<>();
        Set<Character> switches = new HashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (positional.isEmpty() || !isFlag(token, valued + toggles + optional)) {
                positional.add(unquote(token));
                continue;
            }
            for (int letter = 1; letter < token.length(); letter++) {
                char flag = Character.toLowerCase(token.charAt(letter));
                boolean last = letter == token.length() - 1;
                if (values.containsKey(flag) || switches.contains(flag)) {
                    throw new IllegalArgumentException("La bandera -" + flag + " está repetida");
                }
                if (valued.indexOf(flag) >= 0) {
                    if (!last) {
                        throw new IllegalArgumentException("La bandera -" + flag
                                + " lleva valor: ponla la última del grupo o por separado");
                    }
                    if (index + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("Falta el valor de -" + flag);
                    }
                    values.put(flag, unquote(tokens.get(++index)));
                } else if (optional.indexOf(flag) >= 0) {
                    if (last && index + 1 < tokens.size() && accepts.test(tokens.get(index + 1))) {
                        values.put(flag, unquote(tokens.get(++index)));
                    } else {
                        switches.add(flag);
                    }
                } else {
                    switches.add(flag);
                }
            }
        }
        return new GeneratorArgs(List.copyOf(positional), Map.copyOf(values), Set.copyOf(switches));
    }

    /** Parte el texto en palabras respetando comillas y paréntesis. */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                quoted = !quoted;
                current.append(character);
                continue;
            }
            if (!quoted) {
                if (character == '(' || character == '[') {
                    depth++;
                } else if ((character == ')' || character == ']') && depth > 0) {
                    depth--;
                }
                if (Character.isWhitespace(character) && depth == 0) {
                    if (!current.isEmpty()) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    continue;
                }
            }
            current.append(character);
        }
        if (quoted) {
            throw new IllegalArgumentException("Faltan las comillas de cierre");
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        if (tokens.size() > MAX_TOKENS) {
            throw new IllegalArgumentException("Demasiados argumentos (máximo " + MAX_TOKENS + ")");
        }
        return tokens;
    }

    private static boolean isFlag(String token, String known) {
        if (token.length() < 2 || token.charAt(0) != '-') {
            return false;
        }
        for (int index = 1; index < token.length(); index++) {
            char letter = Character.toLowerCase(token.charAt(index));
            if (!Character.isLetter(letter) || known.indexOf(letter) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String unquote(String token) {
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }

    public int count() {
        return positional.size();
    }

    public String positional(int index) {
        return index < positional.size() ? positional.get(index) : null;
    }

    /** Falla si sobran valores posicionales. */
    public GeneratorArgs atMost(int count, String usage) {
        if (positional.size() > count) {
            throw new IllegalArgumentException("Sobra «" + positional.get(count) + "». Uso: " + usage);
        }
        return this;
    }

    public String require(int index, String name, String usage) {
        String value = positional(index);
        if (value == null) {
            throw new IllegalArgumentException("Falta " + name + ". Uso: " + usage);
        }
        return value;
    }

    public boolean has(char flag) {
        return switches.contains(flag) || values.containsKey(flag);
    }

    public String value(char flag) {
        return values.get(flag);
    }

    public static double number(String text, double minimum, double maximum, String name) {
        double value;
        try {
            value = Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " debe ser un número: «" + text + "»");
        }
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " debe estar entre " + format(minimum) + " y "
                    + format(maximum));
        }
        return value;
    }

    public static int integer(String text, int minimum, int maximum, String name) {
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " debe ser un número entero: «" + text + "»");
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " debe estar entre " + minimum + " y " + maximum);
        }
        return value;
    }

    /** Vector {@code x,y,z}; con un solo número vale para los tres ejes. */
    public static double[] vector(String text, double limit, String name) {
        String trimmed = text.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        String[] parts = trimmed.split("[,;]");
        if (parts.length == 1) {
            double value = number(parts[0], -limit, limit, name);
            return new double[]{value, value, value};
        }
        if (parts.length != 3) {
            throw new IllegalArgumentException(name + " se escribe x,y,z, por ejemplo 0,-1,0");
        }
        return new double[]{
                number(parts[0], -limit, limit, name),
                number(parts[1], -limit, limit, name),
                number(parts[2], -limit, limit, name)
        };
    }

    public static String lower(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private static String format(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1.0E9
                ? Long.toString((long) value)
                : Double.toString(value);
    }
}
