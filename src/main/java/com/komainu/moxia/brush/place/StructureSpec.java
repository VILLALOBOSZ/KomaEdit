package com.komainu.moxia.brush.place;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Texto de una estructura o de una alineación con la notación de ezEdits: {@code Nombre} o
 * {@code Nombre(Clave:valor,Clave:valor)}. Las claves no distinguen mayúsculas; los valores pueden llevar
 * paréntesis y comas anidados ({@code C(D:(-1,2,-1))}) o ir entre comillas.
 */
public record StructureSpec(String name, Map<String, String> options) {
    public static StructureSpec parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta la estructura, por ejemplo Clipboard o Cl(O:C)");
        }
        String text = input.trim();
        int open = text.indexOf('(');
        if (open < 0) {
            return new StructureSpec(text, Map.of());
        }
        if (!text.endsWith(")")) {
            throw new IllegalArgumentException("Falta el paréntesis de cierre en " + text);
        }
        String name = text.substring(0, open).trim();
        String body = text.substring(open + 1, text.length() - 1);
        Map<String, String> options = new LinkedHashMap<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int index = 0; index <= body.length(); index++) {
            char character = index < body.length() ? body.charAt(index) : ',';
            if (character == '"') {
                quoted = !quoted;
            } else if (!quoted && (character == '(' || character == '[')) {
                depth++;
            } else if (!quoted && (character == ')' || character == ']')) {
                depth--;
            } else if (!quoted && depth == 0 && character == ',') {
                String entry = body.substring(start, index).trim();
                start = index + 1;
                if (entry.isEmpty()) {
                    continue;
                }
                int colon = entry.indexOf(':');
                if (colon <= 0 || colon == entry.length() - 1) {
                    throw new IllegalArgumentException("Opción inválida en " + text + ": " + entry);
                }
                String value = entry.substring(colon + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                options.put(entry.substring(0, colon).trim().toLowerCase(Locale.ROOT), value);
            }
        }
        if (depth != 0 || quoted) {
            throw new IllegalArgumentException("Paréntesis o comillas sin cerrar en " + text);
        }
        return new StructureSpec(name, Map.copyOf(options));
    }

    public boolean is(String... names) {
        for (String candidate : names) {
            if (candidate.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Valor de la primera clave que exista (nombre largo o abreviado). */
    public String option(String... keys) {
        for (String key : keys) {
            String value = options.get(key.toLowerCase(Locale.ROOT));
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
