package com.komainu.moxia.worldedit.ez;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lee la forma {@code #nombre[arg1][arg2]...} de las máscaras y patrones con la notación de ezEdits. Los argumentos
 * pueden llevar corchetes dentro (por ejemplo una paleta {@code [##Ice,stone]} o un bloque con estados) mientras estén
 * equilibrados.
 *
 * @param name nombre en minúsculas, sin la almohadilla
 */
public record EzArguments(String name, List<String> arguments) {
    public EzArguments {
        arguments = List.copyOf(arguments);
    }

    /** Devuelve {@code null} si el texto no empieza por una sola {@code #} seguida de un nombre. */
    public static EzArguments parse(String input) {
        if (input == null || input.length() < 2 || input.charAt(0) != '#' || input.charAt(1) == '#') {
            return null;
        }
        int position = 1;
        while (position < input.length()
                && (Character.isLetterOrDigit(input.charAt(position)) || input.charAt(position) == '_')) {
            position++;
        }
        if (position == 1) {
            return null;
        }
        String name = input.substring(1, position).toLowerCase(Locale.ROOT);
        List<String> arguments = new ArrayList<>();
        while (position < input.length()) {
            if (input.charAt(position) != '[') {
                throw new IllegalArgumentException("Tras #" + name + " solo pueden ir argumentos entre corchetes: "
                        + input.substring(position));
            }
            int depth = 0;
            int start = position + 1;
            int end = -1;
            for (int index = position; index < input.length(); index++) {
                char current = input.charAt(index);
                if (current == '[') {
                    depth++;
                } else if (current == ']') {
                    depth--;
                    if (depth == 0) {
                        end = index;
                        break;
                    }
                }
            }
            if (end < 0) {
                throw new IllegalArgumentException("Falta un ] en " + input);
            }
            arguments.add(input.substring(start, end).trim());
            position = end + 1;
        }
        return new EzArguments(name, arguments);
    }

    public int size() {
        return arguments.size();
    }

    public boolean has(int index) {
        return index < arguments.size() && !arguments.get(index).isEmpty();
    }

    public String text(int index, String fallback) {
        return has(index) ? arguments.get(index) : fallback;
    }

    public String require(int index, String what) {
        if (!has(index)) {
            throw new IllegalArgumentException("#" + name + " necesita " + what);
        }
        return arguments.get(index);
    }

    public double number(int index, double fallback) {
        if (!has(index)) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(arguments.get(index));
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("#" + name + ": «" + arguments.get(index) + "» no es un número");
        }
    }

    public boolean bool(int index, boolean fallback) {
        if (!has(index)) {
            return fallback;
        }
        return switch (arguments.get(index).toLowerCase(Locale.ROOT)) {
            case "true", "t", "yes", "y", "si", "sí", "1", "on" -> true;
            case "false", "f", "no", "n", "0", "off" -> false;
            default -> throw new IllegalArgumentException("#" + name + ": «" + arguments.get(index)
                    + "» debe ser true o false");
        };
    }

    /** Falla si hay más argumentos de los que admite. */
    public EzArguments atMost(int count) {
        if (arguments.size() > count) {
            throw new IllegalArgumentException("#" + name + " admite como mucho " + count + " argumentos");
        }
        return this;
    }
}
