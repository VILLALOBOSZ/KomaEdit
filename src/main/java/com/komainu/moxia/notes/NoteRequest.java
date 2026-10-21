package com.komainu.moxia.notes;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se escribe detrás de {@code /komaedit nota poner}: el texto (suelto o entre comillas) y las banderas, en
 * cualquier orden. Lo mismo arma la tarjeta del Studio.
 *
 * <pre>
 * -c, --color &lt;color&gt;        amarillo, rojo… o #RRGGBB
 * -i, --icono &lt;icono&gt;        nota, aviso, tarea, idea, pregunta, aqui, estrella, error
 * -d, --distancia &lt;bloques&gt;  desde dónde se ve
 * -e, --equipo               para el equipo (si no, privada)
 * -p, --punto                en tu posición (si no, en el bloque que miras)
 * </pre>
 *
 * @param color    {@code null}: el de serie
 * @param icon     {@code null}: el de serie
 * @param distance {@code null}: la de serie
 */
public record NoteRequest(String text, Integer color, NoteIcon icon, Integer distance, boolean team, boolean point) {
    public int colorOr(int fallback) {
        return color == null ? fallback : color;
    }

    public NoteIcon iconOr(NoteIcon fallback) {
        return icon == null ? fallback : icon;
    }

    public int distanceOr(int fallback) {
        return distance == null ? fallback : distance;
    }

    /** Lee la orden; lanza con un mensaje para el jugador si algo no vale. */
    public static NoteRequest parse(String arguments) {
        List<Token> tokens = tokens(arguments);
        List<String> text = new ArrayList<>();
        Integer color = null;
        NoteIcon icon = null;
        Integer distance = null;
        boolean team = false;
        boolean point = false;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            // Lo que va entre comillas es siempre texto, aunque empiece por un guion.
            switch (token.quoted() ? "" : token.value()) {
                case "-c", "--color" -> color = NoteColor.parse(value(tokens, ++index, token.value()));
                case "-i", "--icono", "--icon" -> icon = NoteIcon.parse(value(tokens, ++index, token.value()));
                case "-d", "--distancia", "--distance" -> {
                    String value = value(tokens, ++index, token.value());
                    try {
                        distance = Integer.parseInt(value);
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("La distancia tiene que ser un número de bloques: "
                                + value);
                    }
                }
                case "-e", "--equipo", "--team" -> team = true;
                case "-p", "--punto", "--point" -> point = true;
                default -> text.add(token.value());
            }
        }
        return new NoteRequest(String.join(" ", text), color, icon, distance, team, point);
    }

    private static String value(List<Token> tokens, int index, String flag) {
        if (index >= tokens.size()) {
            throw new IllegalArgumentException("Falta el valor de " + flag);
        }
        return tokens.get(index).value();
    }

    /** Una palabra de la orden y si iba entre comillas. */
    record Token(String value, boolean quoted) {
    }

    /** Palabras separadas por espacios; lo que va entre comillas es una sola (con {@code \"} dentro). */
    static List<Token> tokens(String arguments) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int index = 0; index < arguments.length(); index++) {
            char character = arguments.charAt(index);
            if (quoted) {
                if (character == '\\' && index + 1 < arguments.length()
                        && (arguments.charAt(index + 1) == '"' || arguments.charAt(index + 1) == '\\')) {
                    current.append(arguments.charAt(++index));
                } else if (character == '"') {
                    quoted = false;
                } else {
                    current.append(character);
                }
            } else if (character == '"' && current.isEmpty()) {
                quoted = true;
                any = true;
            } else if (Character.isWhitespace(character)) {
                if (any || !current.isEmpty()) {
                    tokens.add(new Token(current.toString(), any));
                }
                current.setLength(0);
                any = false;
            } else {
                current.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Faltan las comillas de cierre del texto");
        }
        if (any || !current.isEmpty()) {
            tokens.add(new Token(current.toString(), any));
        }
        return tokens;
    }

    /** El texto entre comillas, con las de dentro escapadas, como lo lee {@link #parse}. */
    public static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
