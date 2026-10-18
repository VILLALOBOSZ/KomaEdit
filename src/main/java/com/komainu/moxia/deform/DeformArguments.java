package com.komainu.moxia.deform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Argumentos de los comandos de deformación, superficie y suavizado, con el estilo de banderas de los comandos
 * de tubo y de ezEdits: valores sueltos en orden, banderas con valor ({@code -w Slabs}, {@code -s 42}) y banderas
 * sin valor que se pueden juntar ({@code -afost}). Un número negativo ({@code -0.5}) es un valor, no una bandera.
 */
public final class DeformArguments {
    private final List<String> positional;
    private final Map<String, String> values;
    private final Set<Character> switches;

    private DeformArguments(List<String> positional, Map<String, String> values, Set<Character> switches) {
        this.positional = positional;
        this.values = values;
        this.switches = switches;
    }

    /**
     * @param valued   banderas que llevan valor, sin guion ({@code "w"}, {@code "seed"})
     * @param booleans banderas de una letra sin valor
     */
    public static DeformArguments parse(String text, Set<String> valued, String booleans) {
        List<String> positional = new ArrayList<>();
        Map<String, String> values = new LinkedHashMap<>();
        Set<Character> switches = new HashSet<>();
        String[] tokens = joinQuoted(text == null || text.isBlank() ? new String[0] : text.trim().split("\\s+"));
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            if (!token.startsWith("-") || token.length() == 1 || isNumber(token)) {
                positional.add(token);
                continue;
            }
            String flag = token.substring(1).toLowerCase(Locale.ROOT);
            if (valued.contains(flag)) {
                if (index + 1 >= tokens.length) {
                    throw new IllegalArgumentException("Falta el valor de " + token);
                }
                String value = tokens[++index];
                if (flag.equals("w")) {
                    // Un perfil con espacios tras las comas: SlabsAndStairs(Slab:oak, Stair:spruce).
                    int extra = SmoothProfile.extraWords(tokens, index);
                    for (int join = 0; join < extra; join++) {
                        value += tokens[++index];
                    }
                }
                if (values.put(canonical(flag), value) != null) {
                    throw new IllegalArgumentException("La bandera " + token + " está repetida");
                }
                continue;
            }
            for (char letter : flag.toCharArray()) {
                if (booleans.indexOf(letter) < 0) {
                    throw new IllegalArgumentException("Bandera desconocida: " + token
                            + (valued.isEmpty() && booleans.isEmpty() ? "" : " (admite " + describe(valued, booleans) + ")"));
                }
                switches.add(letter);
            }
        }
        return new DeformArguments(List.copyOf(positional), values, switches);
    }

    /**
     * Junta las palabras entre comillas dobles en un solo valor sin comillas: así una máscara de WorldEdit con
     * espacios cabe en {@code -m "#solid !sand"}.
     */
    static String[] joinQuoted(String[] words) {
        List<String> tokens = new ArrayList<>();
        StringBuilder quoted = null;
        for (String word : words) {
            if (quoted == null && word.startsWith("\"")) {
                if (word.length() > 1 && word.endsWith("\"")) {
                    tokens.add(word.substring(1, word.length() - 1));
                    continue;
                }
                quoted = new StringBuilder(word.substring(1));
            } else if (quoted != null) {
                boolean closes = word.endsWith("\"");
                quoted.append(' ').append(closes ? word.substring(0, word.length() - 1) : word);
                if (closes) {
                    tokens.add(quoted.toString());
                    quoted = null;
                }
            } else {
                tokens.add(word);
            }
        }
        if (quoted != null) {
            throw new IllegalArgumentException("Faltan las comillas de cierre en «\"" + quoted + "»");
        }
        return tokens.toArray(String[]::new);
    }

    public int count() {
        return positional.size();
    }

    public String text(int index) {
        return index < positional.size() ? positional.get(index) : null;
    }

    public double number(int index, double fallback, double minimum, double maximum, String name) {
        String token = text(index);
        return token == null ? fallback : parseNumber(token, minimum, maximum, name);
    }

    public double required(int index, double minimum, double maximum, String name) {
        String token = text(index);
        if (token == null) {
            throw new IllegalArgumentException("Falta " + name);
        }
        return parseNumber(token, minimum, maximum, name);
    }

    public boolean has(char flag) {
        return switches.contains(flag);
    }

    public String value(String flag) {
        return values.get(canonical(flag));
    }

    public double value(String flag, double fallback, double minimum, double maximum, String name) {
        String token = value(flag);
        return token == null ? fallback : parseNumber(token, minimum, maximum, name);
    }

    /** Falla si sobran valores sueltos. */
    public void expectAtMost(int count, String usage) {
        if (positional.size() > count) {
            throw new IllegalArgumentException("Sobra «" + positional.get(count) + "». Uso: " + usage);
        }
    }

    /** {@code -s}/{@code -seed}: -1 o nada es una semilla al azar. */
    public long seed(String flag, long random) {
        String token = value(flag);
        if (token == null) {
            return random;
        }
        try {
            long seed = Long.parseLong(token);
            return seed == -1L ? random : seed;
        } catch (NumberFormatException exception) {
            return token.hashCode();
        }
    }

    public static double parseNumber(String token, double minimum, double maximum, String name) {
        double value;
        try {
            value = Double.parseDouble(token);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " no es un número: " + token);
        }
        if (!(value >= minimum && value <= maximum)) {
            throw new IllegalArgumentException(name + " debe estar entre " + format(minimum) + " y " + format(maximum));
        }
        return value;
    }

    private static String canonical(String flag) {
        return switch (flag) {
            case "semilla" -> "seed";
            default -> flag;
        };
    }

    private static boolean isNumber(String token) {
        char second = token.charAt(1);
        return Character.isDigit(second) || second == '.';
    }

    private static String describe(Set<String> valued, String booleans) {
        List<String> names = new ArrayList<>();
        for (String flag : valued) {
            if (!flag.equals("semilla")) {
                names.add("-" + flag + " <valor>");
            }
        }
        for (char letter : booleans.toCharArray()) {
            names.add("-" + letter);
        }
        return String.join(", ", names);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
