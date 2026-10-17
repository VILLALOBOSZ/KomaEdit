package com.komainu.moxia.glass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Argumentos de {@code /komaedit glassgradient <inicio> [fin] <capas> [longitud] [calidad] [dirección]
 * [-c fondo] [-b] [-s]}, los mismos que {@code //ezstainedglassgradient} de ezEdits.
 *
 * @param direction dirección del degradado: {@code up}, {@code down}, {@code left}, {@code right},
 *                  {@code forward}… o un punto cardinal
 */
public record GlassGradientRequest(
        double[] start,
        double[] end,
        int layers,
        int length,
        int quality,
        String direction,
        double[] background,
        boolean findBackground,
        boolean unordered
) {
    public static final int DEFAULT_QUALITY = 6;
    private static final Set<String> DIRECTIONS = Set.of("up", "down", "left", "right", "north", "south", "east",
            "west", "arriba", "abajo", "izquierda", "derecha", "norte", "sur", "este", "oeste");

    public static GlassGradientRequest parse(String input) {
        List<String> positional = new ArrayList<>();
        double[] background = {0, 0, 0};
        boolean find = false;
        boolean unordered = false;
        String[] words = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
        for (int index = 0; index < words.length; index++) {
            String word = words[index];
            String lower = word.toLowerCase(Locale.ROOT);
            if (lower.equals("-c")) {
                if (index + 1 >= words.length) {
                    throw new IllegalArgumentException("Falta el color de fondo de -c, por ejemplo -c #202020");
                }
                background = GlassColors.hex(words[++index]);
            } else if (lower.matches("-[bs]+")) {
                find |= lower.contains("b");
                unordered |= lower.contains("s");
            } else if (lower.startsWith("-") && !lower.matches("-?\\d.*")) {
                throw new IllegalArgumentException("Bandera desconocida: " + word + " (hay -c, -b y -s)");
            } else {
                positional.add(word);
            }
        }
        if (positional.isEmpty()) {
            throw new IllegalArgumentException("Falta el color inicial, por ejemplo #3A7FD2");
        }
        int next = 0;
        double[] start = GlassColors.hex(positional.get(next++));
        double[] end = start;
        // Un número corto sin # son las capas, no un color (#123 sí es un color).
        if (next < positional.size() && GlassColors.isHex(positional.get(next))
                && !positional.get(next).matches("\\d{1,5}")) {
            end = GlassColors.hex(positional.get(next++));
        }
        if (next >= positional.size()) {
            throw new IllegalArgumentException("Faltan las capas de cristal, por ejemplo 4");
        }
        int layers = integer(positional.get(next++), "las capas");
        int length = next < positional.size() && isInteger(positional.get(next))
                ? integer(positional.get(next++), "la longitud") : 1;
        int quality = next < positional.size() && isInteger(positional.get(next))
                ? integer(positional.get(next++), "la calidad") : DEFAULT_QUALITY;
        String direction = "up";
        if (next < positional.size()) {
            direction = positional.get(next++).toLowerCase(Locale.ROOT);
            if (!DIRECTIONS.contains(direction)) {
                throw new IllegalArgumentException("Dirección desconocida: " + direction
                        + " (up, down, left, right, north, south, east o west)");
            }
        }
        if (next < positional.size()) {
            throw new IllegalArgumentException("Sobra «" + String.join(" ", positional.subList(next, positional.size()))
                    + "»");
        }
        if (layers < 1 || layers > GlassGradientSolver.MAX_LAYERS) {
            throw new IllegalArgumentException("Las capas van de 1 a " + GlassGradientSolver.MAX_LAYERS);
        }
        if (length < 1 || length > GlassGradientSolver.MAX_LENGTH) {
            throw new IllegalArgumentException("La longitud va de 1 a " + GlassGradientSolver.MAX_LENGTH);
        }
        if (quality < 1 || quality > GlassGradientSolver.MAX_QUALITY) {
            throw new IllegalArgumentException("La calidad va de 1 a " + GlassGradientSolver.MAX_QUALITY);
        }
        return new GlassGradientRequest(start, end, layers, length, quality, direction, background, find, unordered);
    }

    private static boolean isInteger(String text) {
        return text.matches("-?\\d+");
    }

    private static int integer(String text, String what) {
        if (!isInteger(text) || text.length() > 6) {
            throw new IllegalArgumentException("«" + text + "» no es un número entero válido para " + what);
        }
        return Integer.parseInt(text);
    }
}
