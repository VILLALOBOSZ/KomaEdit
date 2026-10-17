package com.komainu.moxia.placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lectura de la notación de ezEdits para estructuras y alineaciones: {@code Nombre(Clave:valor,Clave:valor)}, con
 * valores anidados ({@code Sh(S:Torus(Thickness:0.4),P:stone)}) y comillas para los valores con comas o espacios
 * ({@code P:"dirt,stone"}). Los paréntesis y las comillas protegen los separadores que contienen.
 */
public final class ParamText {
    private ParamText() {
    }

    /** Parte la línea en palabras separadas por espacios fuera de paréntesis y comillas. */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quote != 0) {
                current.append(character);
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '"' || character == '\'') {
                quote = character;
                current.append(character);
            } else if (character == '(' || character == '[') {
                depth++;
                current.append(character);
            } else if (character == ')' || character == ']') {
                depth = Math.max(0, depth - 1);
                current.append(character);
            } else if (Character.isWhitespace(character) && depth == 0) {
                if (!current.isEmpty()) {
                    words.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Faltan las comillas de cierre en «" + current + "»");
        }
        if (!current.isEmpty()) {
            words.add(current.toString());
        }
        return words;
    }

    /** Parte por {@code separator} fuera de paréntesis y comillas. */
    public static List<String> split(String text, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quote != 0) {
                current.append(character);
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '"' || character == '\'') {
                quote = character;
            } else if (character == '(' || character == '[') {
                depth++;
            } else if (character == ')' || character == ']') {
                depth--;
            } else if (character == separator && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        parts.add(current.toString());
        return parts;
    }

    /** Quita unas comillas que envuelvan todo el texto. */
    public static String unquote(String text) {
        String trimmed = text.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            if ((first == '"' || first == '\'') && trimmed.charAt(trimmed.length() - 1) == first) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    /** Lee {@code Nombre} o {@code Nombre(clave:valor,…)}; las claves quedan en minúsculas. */
    public static Call call(String text) {
        String trimmed = unquote(text);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Falta un valor");
        }
        int open = trimmed.indexOf('(');
        if (open < 0) {
            return new Call(trimmed, Map.of(), trimmed);
        }
        if (!trimmed.endsWith(")")) {
            throw new IllegalArgumentException("Falta el paréntesis de cierre en «" + trimmed + "»");
        }
        String name = trimmed.substring(0, open).trim();
        String body = trimmed.substring(open + 1, trimmed.length() - 1).trim();
        Map<String, String> arguments = new LinkedHashMap<>();
        if (!body.isEmpty()) {
            for (String entry : split(body, ',')) {
                int colon = keyColon(entry);
                if (colon < 0) {
                    throw new IllegalArgumentException("Se esperaba clave:valor en «" + entry.trim() + "» (" + name + ")");
                }
                String key = entry.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = unquote(entry.substring(colon + 1));
                if (key.isEmpty() || value.isEmpty()) {
                    throw new IllegalArgumentException("Parámetro vacío en «" + entry.trim() + "» (" + name + ")");
                }
                if (arguments.put(key, value) != null) {
                    throw new IllegalArgumentException("Parámetro repetido: " + key + " (" + name + ")");
                }
            }
        }
        return new Call(name, arguments, trimmed);
    }

    private static int keyColon(String entry) {
        for (int index = 0; index < entry.length(); index++) {
            char character = entry.charAt(index);
            if (character == ':') {
                return index;
            }
            if (!Character.isLetterOrDigit(character) && !Character.isWhitespace(character) && character != '_') {
                return -1;
            }
        }
        return -1;
    }

    public static double number(String value, double minimum, double maximum, String name) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim().replace("%", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valor inválido para " + name + ": " + value);
        }
        if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(name + " debe estar entre " + format(minimum) + " y " + format(maximum));
        }
        return parsed;
    }

    public static boolean bool(String value, String name) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "t", "1", "si", "sí", "yes", "y" -> true;
            case "false", "f", "0", "no", "n" -> false;
            default -> throw new IllegalArgumentException(name + " espera true o false: " + value);
        };
    }

    static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /** Comparación de nombres sin mayúsculas ni guiones bajos. */
    public static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    /**
     * Llamada {@code Nombre(clave:valor)}. {@link #get} busca por cualquiera de los nombres de la clave (el largo y
     * el abreviado de ezEdits, o el español).
     */
    public record Call(String name, Map<String, String> arguments, String source) {
        public String get(String... keys) {
            for (String key : keys) {
                String value = arguments.get(key.toLowerCase(Locale.ROOT));
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        public boolean is(String... names) {
            String normalized = normalize(name);
            for (String candidate : names) {
                if (normalize(candidate).equals(normalized)) {
                    return true;
                }
            }
            return false;
        }

        /** Falla si hay claves que nadie lee: así un error de escritura no pasa desapercibido. */
        public void requireOnly(String... keys) {
            outer:
            for (String key : arguments.keySet()) {
                for (String allowed : keys) {
                    if (allowed.equalsIgnoreCase(key)) {
                        continue outer;
                    }
                }
                throw new IllegalArgumentException("Parámetro desconocido en " + name + ": " + key);
            }
        }
    }
}
