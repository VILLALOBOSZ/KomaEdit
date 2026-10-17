package com.komainu.moxia.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Trocea el final libre de una orden en argumentos sueltos y banderas al estilo de ezEdits: {@code -f} (sin valor) y
 * {@code -d north} (con valor). Las banderas pueden ir en cualquier sitio; un {@code -} seguido de una cifra o de
 * {@code #} es un argumento (un número negativo o una paleta invertida), no una bandera.
 */
public record CommandTokens(List<String> positional, Set<String> flags, Map<String, String> options) {
    public CommandTokens {
        positional = List.copyOf(positional);
        flags = Set.copyOf(flags);
        options = Map.copyOf(options);
    }

    /**
     * @param withValue banderas que llevan un valor detrás, sin el guion (por ejemplo {@code "d"})
     */
    public static CommandTokens parse(String input, Set<String> withValue) {
        List<String> positional = new ArrayList<>();
        Set<String> flags = new HashSet<>();
        Map<String, String> options = new HashMap<>();
        String[] parts = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (isFlag(part)) {
                String name = part.substring(1).toLowerCase(Locale.ROOT);
                if (withValue.contains(name)) {
                    if (index + 1 >= parts.length) {
                        throw new IllegalArgumentException("Falta el valor de -" + name);
                    }
                    options.put(name, parts[++index]);
                } else {
                    flags.add(name);
                }
            } else {
                positional.add(part);
            }
        }
        return new CommandTokens(positional, flags, options);
    }

    public boolean flag(String name) {
        return flags.contains(name);
    }

    public String option(String name, String fallback) {
        return options.getOrDefault(name, fallback);
    }

    public String at(int index, String fallback) {
        return index < positional.size() ? positional.get(index) : fallback;
    }

    public String require(int index, String what) {
        if (index >= positional.size()) {
            throw new IllegalArgumentException("Falta " + what);
        }
        return positional.get(index);
    }

    public CommandTokens atMost(int count) {
        if (positional.size() > count) {
            throw new IllegalArgumentException("Sobra «" + String.join(" ", positional.subList(count, positional.size()))
                    + "»");
        }
        return this;
    }

    /** Comprueba que no se ha escrito ninguna bandera desconocida. */
    public CommandTokens allow(String... names) {
        Set<String> allowed = Set.of(names);
        for (String flag : flags) {
            if (!allowed.contains(flag)) {
                throw new IllegalArgumentException("Bandera desconocida: -" + flag);
            }
        }
        for (String option : options.keySet()) {
            if (!allowed.contains(option)) {
                throw new IllegalArgumentException("Bandera desconocida: -" + option);
            }
        }
        return this;
    }

    private static boolean isFlag(String part) {
        return part.length() >= 2 && part.charAt(0) == '-' && Character.isLetter(part.charAt(1))
                && part.chars().skip(1).allMatch(Character::isLetter);
    }
}
