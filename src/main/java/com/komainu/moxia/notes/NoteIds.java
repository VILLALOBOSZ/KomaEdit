package com.komainu.moxia.notes;

import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.regex.Pattern;

/** Ids de nota: seis letras minúsculas o números, sin las que se confunden ({@code 0 o 1 l i}). */
public final class NoteIds {
    public static final int LENGTH = 6;
    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final Pattern VALID = Pattern.compile("[a-z0-9]{" + LENGTH + "}");

    private NoteIds() {
    }

    public static boolean valid(String id) {
        return id != null && VALID.matcher(id).matches();
    }

    /**
     * Un id que no esté en {@code taken}.
     *
     * @param random un número al azar entre 0 y el argumento (sin incluirlo); las pruebas pasan uno fijo
     */
    public static String next(Set<String> taken, IntUnaryOperator random) {
        for (int attempt = 0; attempt < 1_000; attempt++) {
            StringBuilder id = new StringBuilder(LENGTH);
            for (int index = 0; index < LENGTH; index++) {
                id.append(ALPHABET.charAt(random.applyAsInt(ALPHABET.length())));
            }
            if (!taken.contains(id.toString())) {
                return id.toString();
            }
        }
        throw new IllegalStateException("No queda ningún id de nota libre");
    }
}
