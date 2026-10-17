package com.komainu.moxia.command;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Propone el nombre conocido más parecido a lo que escribió el jugador: «¿quisiste decir…?». Ignora mayúsculas,
 * tildes, guiones y guiones bajos, y acepta un prefijo inequívoco o hasta un error cada cuatro letras
 * (distancia de Damerau–Levenshtein: cambiar, añadir, quitar o intercambiar dos letras vecinas).
 */
public final class DidYouMean {
    private DidYouMean() {
    }

    public static Optional<String> best(String input, Collection<String> candidates) {
        List<String> found = closest(input, candidates, 1);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /** Hasta {@code limit} candidatos aceptables, del más parecido al menos. */
    public static List<String> closest(String input, Collection<String> candidates, int limit) {
        String wanted = fold(input);
        if (wanted.isEmpty()) {
            return List.of();
        }
        record Match(String value, int score) {
        }
        List<Match> matches = new ArrayList<>();
        for (String candidate : new LinkedHashSet<>(candidates)) {
            String folded = fold(candidate);
            if (folded.isEmpty()) {
                continue;
            }
            if (folded.equals(wanted)) {
                return List.of(candidate);
            }
            int distance = distance(wanted, folded);
            int allowed = Math.max(1, Math.min(wanted.length(), folded.length()) / 4);
            if (distance <= allowed) {
                matches.add(new Match(candidate, distance * 10));
            } else if (wanted.length() >= 3 && folded.startsWith(wanted)) {
                // Un prefijo cuenta algo peor que un error suelto, pero vale: «heli» → «helix».
                matches.add(new Match(candidate, 15 + folded.length() - wanted.length()));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(Match::score).thenComparing(Match::value))
                .limit(limit)
                .map(Match::value)
                .toList();
    }

    /** Añade «¿quisiste decir…?» al mensaje si hay un candidato aceptable. */
    public static String hint(String message, String input, Collection<String> candidates) {
        return best(input, candidates)
                .map(found -> message + " · ¿Quisiste decir «" + found + "»?")
                .orElse(message);
    }

    static String fold(String value) {
        String lower = Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if (character != '_' && character != '-' && character != ' ') {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    /** Distancia de Damerau–Levenshtein restringida (alineamiento óptimo de cadenas). */
    static int distance(String first, String second) {
        int[][] table = new int[first.length() + 1][second.length() + 1];
        for (int row = 0; row <= first.length(); row++) {
            table[row][0] = row;
        }
        for (int column = 0; column <= second.length(); column++) {
            table[0][column] = column;
        }
        for (int row = 1; row <= first.length(); row++) {
            for (int column = 1; column <= second.length(); column++) {
                int cost = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                int value = Math.min(
                        Math.min(table[row - 1][column] + 1, table[row][column - 1] + 1),
                        table[row - 1][column - 1] + cost
                );
                if (row > 1 && column > 1
                        && first.charAt(row - 1) == second.charAt(column - 2)
                        && first.charAt(row - 2) == second.charAt(column - 1)) {
                    value = Math.min(value, table[row - 2][column - 2] + 1);
                }
                table[row][column] = value;
            }
        }
        return table[first.length()][second.length()];
    }
}
