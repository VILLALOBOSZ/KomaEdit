package com.komainu.moxia.brush.ez;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Argumentos de un pincel con la forma de ezEdits: posicionales seguidos de banderas. Las banderas sin valor se
 * pueden juntar ({@code -avw}); las que llevan valor van separadas ({@code -n Perlin(F:0.1)}).
 *
 * <p>Los espacios dentro de paréntesis, corchetes o comillas no cortan el argumento, así que
 * {@code Cl(O:C, R:90)} o {@code "##Magma, stone"} llegan enteros. Un {@code -} delante de algo que no sean letras de
 * banderas conocidas (por ejemplo {@code -##Grayscale}, una paleta invertida) se trata como posicional.</p>
 */
public final class BrushOptions {
    private final List<String> positionals;
    private final Map<Character, String> flags;

    private BrushOptions(List<String> positionals, Map<Character, String> flags) {
        this.positionals = positionals;
        this.flags = flags;
    }

    /**
     * @param switches banderas sin valor
     * @param valued   banderas que toman el argumento siguiente
     */
    public static BrushOptions parse(String input, Set<Character> switches, Set<Character> valued) {
        List<String> tokens = tokenize(input == null ? "" : input);
        List<String> positionals = new ArrayList<>();
        Map<Character, String> flags = new LinkedHashMap<>();
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (!isFlag(token, switches, valued)) {
                positionals.add(unquote(token));
                continue;
            }
            String letters = token.substring(1);
            for (int position = 0; position < letters.length(); position++) {
                char flag = Character.toLowerCase(letters.charAt(position));
                if (valued.contains(flag)) {
                    if (letters.length() > 1) {
                        throw new IllegalArgumentException("La bandera -" + flag + " lleva valor: escríbela sola");
                    }
                    if (index + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("Falta el valor de -" + flag);
                    }
                    flags.put(flag, unquote(tokens.get(++index)));
                } else {
                    flags.put(flag, "");
                }
            }
        }
        return new BrushOptions(List.copyOf(positionals), flags);
    }

    static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
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
                } else if (Character.isWhitespace(character) && depth == 0) {
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
        return tokens;
    }

    private static boolean isFlag(String token, Set<Character> switches, Set<Character> valued) {
        if (token.length() < 2 || token.charAt(0) != '-') {
            return false;
        }
        for (int index = 1; index < token.length(); index++) {
            char flag = Character.toLowerCase(token.charAt(index));
            if (!Character.isLetter(flag) || !(switches.contains(flag) || valued.contains(flag))) {
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
        return positionals.size();
    }

    public String positional(int index) {
        return index < positionals.size() ? positionals.get(index) : null;
    }

    public String required(int index, String what) {
        String value = positional(index);
        if (value == null) {
            throw new IllegalArgumentException("Falta " + what);
        }
        return value;
    }

    public int integer(int index, int fallback, int minimum, int maximum, String what) {
        String value = positional(index);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException(what + " debe estar entre " + minimum + " y " + maximum);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(what + " debe ser un número entero: " + value);
        }
    }

    public double decimal(int index, double fallback, double minimum, double maximum, String what) {
        return decimal(positional(index), fallback, minimum, maximum, what);
    }

    public double flagDecimal(char flag, double fallback, double minimum, double maximum, String what) {
        return decimal(flags.get(flag), fallback, minimum, maximum, what);
    }

    private static double decimal(String value, double fallback, double minimum, double maximum, String what) {
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.replace(',', '.'));
            if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException(what + " debe estar entre " + minimum + " y " + maximum);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(what + " debe ser un número: " + value);
        }
    }

    public boolean has(char flag) {
        return flags.containsKey(flag);
    }

    public String flag(char flag) {
        return flags.get(flag);
    }

    /** Si el posicional parece un número, no es una palabra clave (radio frente a modo de interpolación…). */
    public static boolean isNumber(String value) {
        if (value == null) {
            return false;
        }
        try {
            Double.parseDouble(value.replace(',', '.'));
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
