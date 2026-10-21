package com.komainu.moxia.views;

import java.util.Locale;
import java.util.regex.Pattern;

/** Nombres de vista (y de dimensión) que se pueden escribir en una orden y guardar sin sorpresas. */
public final class ViewNames {
    public static final int MAX_LENGTH = 32;
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _.'\\-]{1," + MAX_LENGTH + "}");
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");

    private ViewNames() {
    }

    /** Sin espacios de más a los lados ni dobles. */
    public static String clean(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ");
    }

    /** Lanza con un mensaje para el jugador si el nombre no vale. */
    public static String validate(String name) {
        String clean = clean(name);
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Ponle un nombre a la vista");
        }
        if (clean.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("El nombre de la vista tiene " + clean.length()
                    + " caracteres; el máximo es " + MAX_LENGTH);
        }
        if (!NAME.matcher(clean).matches()) {
            throw new IllegalArgumentException("El nombre de la vista solo puede llevar letras, números, espacios y . _ - '");
        }
        return clean;
    }

    public static boolean valid(String name) {
        try {
            validate(name);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static String validateDimension(String dimension) {
        if (dimension == null || dimension.length() > 128 || !DIMENSION.matcher(dimension).matches()) {
            throw new IllegalArgumentException("Dimensión no válida: " + dimension);
        }
        return dimension;
    }

    /** Para comparar nombres: sin mayúsculas. */
    public static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }
}
