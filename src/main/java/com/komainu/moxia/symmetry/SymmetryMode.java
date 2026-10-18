package com.komainu.moxia.symmetry;

import java.util.Locale;

/**
 * Tipo de simetría: espejos en X, Z e Y en cualquier combinación, o radial de {@code arms} brazos alrededor de un eje
 * vertical (con espejo en Y si se quiere).
 *
 * @param arms brazos del radial; en los espejos se guarda para cuando se vuelva al radial
 */
public record SymmetryMode(boolean radial, boolean mirrorX, boolean mirrorZ, boolean mirrorY, int arms) {
    public static final int MIN_ARMS = 2;
    public static final int MAX_ARMS = 16;
    public static final int DEFAULT_ARMS = 4;
    public static final SymmetryMode DEFAULT = new SymmetryMode(false, true, false, false, DEFAULT_ARMS);

    public SymmetryMode {
        if (arms < MIN_ARMS || arms > MAX_ARMS) {
            throw new IllegalArgumentException("El radial va de " + MIN_ARMS + " a " + MAX_ARMS + " brazos");
        }
        if (radial && (mirrorX || mirrorZ)) {
            throw new IllegalArgumentException("El radial no se combina con los espejos en X o Z; sí con el de Y");
        }
        if (!radial && !mirrorX && !mirrorZ && !mirrorY) {
            throw new IllegalArgumentException("Elige al menos un espejo: x, z o y");
        }
    }

    public static SymmetryMode mirror(boolean x, boolean z, boolean y, int arms) {
        return new SymmetryMode(false, x, z, y, arms);
    }

    public static SymmetryMode radial(int arms, boolean y) {
        return new SymmetryMode(true, false, false, y, arms);
    }

    /**
     * Lee el modo escrito en la orden: letras de los espejos ({@code x}, {@code z}, {@code xz}, {@code y},
     * {@code xzy}…, también {@code x+z}) o {@code radial [brazos] [y]}.
     *
     * @param arms brazos que se conservan si el texto no los dice
     */
    public static SymmetryMode parse(String text, int arms) {
        String[] words = text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            throw new IllegalArgumentException("Falta el modo: x, z, xz, y o radial");
        }
        String first = words[0];
        if (first.equals("radial") || first.equals("rotational") || first.equals("rotacional")) {
            int count = arms;
            boolean y = false;
            for (int index = 1; index < words.length; index++) {
                String word = words[index];
                if (word.equals("y") || word.equals("+y")) {
                    y = true;
                } else if (word.chars().allMatch(Character::isDigit)) {
                    count = parseArms(word);
                } else {
                    throw new IllegalArgumentException("Palabra desconocida tras radial: " + word
                            + " (usa radial [brazos] [y])");
                }
            }
            return radial(count, y);
        }
        StringBuilder letters = new StringBuilder();
        for (String word : words) {
            letters.append(word.replace("+", ""));
        }
        boolean x = false;
        boolean y = false;
        boolean z = false;
        for (char letter : letters.toString().toCharArray()) {
            switch (letter) {
                case 'x' -> x = flag(x, letter);
                case 'y' -> y = flag(y, letter);
                case 'z' -> z = flag(z, letter);
                default -> throw new IllegalArgumentException("Modo de simetría desconocido: " + text.trim()
                        + " (usa x, z, xz, y, xzy o radial)");
            }
        }
        return mirror(x, z, y, arms);
    }

    public static int parseArms(String text) {
        int count;
        try {
            count = Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Brazos no válidos: " + text);
        }
        if (count < MIN_ARMS || count > MAX_ARMS) {
            throw new IllegalArgumentException("El radial va de " + MIN_ARMS + " a " + MAX_ARMS + " brazos");
        }
        return count;
    }

    private static boolean flag(boolean already, char letter) {
        if (already) {
            throw new IllegalArgumentException("Eje repetido en el modo: " + letter);
        }
        return true;
    }

    public SymmetryMode withArms(int count) {
        return new SymmetryMode(true, false, false, mirrorY, count);
    }

    /** Cuántas veces sale cada bloque, contando el original. */
    public int images() {
        int vertical = mirrorY ? 2 : 1;
        if (radial) {
            return arms * vertical;
        }
        return (mirrorX ? 2 : 1) * (mirrorZ ? 2 : 1) * vertical;
    }

    /** Lo que se escribe en la orden para volver a este modo: {@code xz}, {@code y}, {@code radial 8 y}. */
    public String id() {
        if (radial) {
            return "radial " + arms + (mirrorY ? " y" : "");
        }
        return (mirrorX ? "x" : "") + (mirrorZ ? "z" : "") + (mirrorY ? "y" : "");
    }

    /** Los ejes de los espejos como {@code X+Z+Y}; en el radial, solo {@code Y} si lo tiene. */
    public String axes() {
        StringBuilder text = new StringBuilder();
        if (mirrorX) {
            text.append("X");
        }
        if (mirrorZ) {
            text.append(text.isEmpty() ? "" : "+").append("Z");
        }
        if (mirrorY) {
            text.append(text.isEmpty() ? "" : "+").append("Y");
        }
        return text.toString();
    }
}
