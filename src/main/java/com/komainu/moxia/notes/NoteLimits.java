package com.komainu.moxia.notes;

/**
 * Límites de las notas, en {@code config/komaedit/utilities.json}. Quien tiene {@code komaedit.admin.notes} se salta
 * los de número (no el de longitud, que protege la red y el chat).
 *
 * @param maxPerPlayer notas de un jugador (privadas y del equipo) que siguen abiertas o resueltas
 * @param maxTeam      notas del equipo en todo el mundo
 * @param maxLength    caracteres del texto
 * @param maxDistance  bloques como mucho desde los que se ve una nota
 * @param allowTeam    si se pueden poner notas para el equipo; además hace falta {@code komaedit.notes.share}
 */
public record NoteLimits(int maxPerPlayer, int maxTeam, int maxLength, int maxDistance, boolean allowTeam) {
    public static final int MIN_DISTANCE = 4;
    public static final int DEFAULT_DISTANCE = 48;
    /** Ni la configuración puede pasar de aquí: es lo que cabe en un paquete y se lee en el mundo. */
    public static final int HARD_MAX_LENGTH = 256;
    public static final int HARD_MAX_DISTANCE = 512;
    public static final NoteLimits DEFAULT = new NoteLimits(64, 1_000, 200, 256, true);

    public NoteLimits {
        if (maxPerPlayer < 0 || maxTeam < 0 || maxLength < 1 || maxLength > HARD_MAX_LENGTH
                || maxDistance < MIN_DISTANCE || maxDistance > HARD_MAX_DISTANCE) {
            throw new IllegalArgumentException("Límites de notas no válidos");
        }
    }

    /** El texto, ya limpio; lanza si está vacío, si es largo o si lleva caracteres que el chat no admite. */
    public String checkText(String text) {
        String clean = text == null ? "" : text.strip().replaceAll("[\\t\\r\\n]+", " ");
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("La nota no tiene texto");
        }
        if (clean.codePointCount(0, clean.length()) > maxLength) {
            throw new IllegalArgumentException("La nota tiene " + clean.codePointCount(0, clean.length())
                    + " caracteres; el máximo es " + maxLength);
        }
        for (int index = 0; index < clean.length(); index++) {
            char character = clean.charAt(index);
            if (character == '§' || character < ' ' || character == 127) {
                throw new IllegalArgumentException("La nota lleva un carácter que no se admite");
            }
        }
        return clean;
    }

    public int checkDistance(int distance) {
        if (distance < MIN_DISTANCE || distance > maxDistance) {
            throw new IllegalArgumentException("La distancia de una nota va de " + MIN_DISTANCE + " a " + maxDistance
                    + " bloques");
        }
        return distance;
    }
}
