package com.komainu.moxia.share;

/**
 * Qué texto de un paquete se acepta. Un paquete viene de fuera: sus textos no pueden llevar códigos de formato del
 * chat ({@code §}), saltos de línea ni caracteres invisibles que cambian el orden de lo que se lee (los de dirección
 * de Unicode), que servirían para disfrazar un nombre o un mensaje.
 */
public final class ShareText {
    private ShareText() {
    }

    /** ¿Lleva algún carácter que no puede ir en un paquete? */
    public static boolean unsafe(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (forbidden(text.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Un ajuste del paquete (una paleta, los ajustes de un preset…): sin caracteres prohibidos y como mucho
     * {@code max} caracteres.
     */
    public static String setting(String what, String text, int max) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(what + " está vacío");
        }
        if (text.length() > max) {
            throw new IllegalArgumentException(what + " pasa de " + max + " caracteres");
        }
        if (unsafe(text)) {
            throw new IllegalArgumentException(what + " lleva caracteres no permitidos");
        }
        return text.strip();
    }

    /**
     * Un texto que solo se enseña (nombre del paquete, autor, versión): lo prohibido se quita (los códigos de formato,
     * con su letra), los espacios seguidos se juntan y se corta en {@code max} caracteres.
     */
    public static String display(String text, int max) {
        if (text == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder(Math.min(text.length(), max));
        boolean space = false;
        for (int index = 0; index < text.length() && clean.length() < max; index++) {
            char character = text.charAt(index);
            if (Character.isWhitespace(character)) {
                space = clean.length() > 0;
                continue;
            }
            if (character == '§') {
                // El código de formato es § y la letra que sigue: se van los dos.
                index++;
                continue;
            }
            if (forbidden(character)) {
                continue;
            }
            if (space) {
                clean.append(' ');
                space = false;
                if (clean.length() >= max) {
                    break;
                }
            }
            clean.append(character);
        }
        return clean.toString().strip();
    }

    private static boolean forbidden(char character) {
        return character < 0x20 && character != ' ' || character == 0x7F || character == '§'
                || character >= 0x80 && character <= 0x9F
                || character >= 0x200B && character <= 0x200F
                || character >= 0x202A && character <= 0x202E
                || character >= 0x2066 && character <= 0x2069
                || character == 0xFEFF;
    }
}
