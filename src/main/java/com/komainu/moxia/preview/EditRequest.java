package com.komainu.moxia.preview;

import java.util.Locale;
import java.util.Set;

/**
 * Cómo quiere aplicarse la edición que lanza una orden: directamente, con vista previa ({@code -v}), ya confirmada o
 * desde una herramienta. Viaja desde la orden hasta {@code BatchEditService} pasando por la captura repartida entre
 * ticks y por el worker; ver {@link EditRequests}.
 *
 * @param command la orden tal como se escribió, sin la bandera de vista previa ni la barra; sirve para recalcular
 *                si el mundo cambia antes de confirmar ({@code null} si no viene de una orden)
 * @param label   nombre corto para la barra de progreso ({@code texture}, {@code deform}…)
 */
public record EditRequest(Kind kind, String command, String label) {
    public enum Kind {
        /** Orden normal: se aplica sin preguntar, salvo que el jugador previsualice siempre a partir de N bloques. */
        DEFAULT,
        /** {@code -v} o {@code --preview}: se calcula, se enseña y espera a {@code /komaedit confirm}. */
        PREVIEW,
        /** Ya confirmada: no vuelve a pedir vista previa ni confirmación. */
        CONFIRMED,
        /** Pinceles y herramientas, clic a clic: sin vista previa ni preferencia. */
        TOOL
    }

    private static final Set<String> ROOTS = Set.of("komaedit", "ke", "moxia", "mx");

    public static final EditRequest DEFAULT = new EditRequest(Kind.DEFAULT, null, null);
    public static final EditRequest CONFIRMED = new EditRequest(Kind.CONFIRMED, null, null);
    public static final EditRequest TOOL = new EditRequest(Kind.TOOL, null, null);

    public EditRequest {
        if (kind == null) {
            throw new IllegalArgumentException("Falta el tipo de petición");
        }
    }

    /** Petición de una orden escrita por el jugador; {@code preview} si llevaba {@code -v}. */
    public static EditRequest command(String input, boolean preview) {
        String command = input == null ? null : input.strip();
        if (command != null && command.startsWith("/")) {
            command = command.substring(1);
        }
        return new EditRequest(preview ? Kind.PREVIEW : Kind.DEFAULT, command, labelOf(command));
    }

    public boolean preview() {
        return kind == Kind.PREVIEW;
    }

    /** La misma orden, pero pidiendo vista previa (para recalcular tras un cambio del mundo). */
    public EditRequest asPreview() {
        return kind == Kind.PREVIEW ? this : new EditRequest(Kind.PREVIEW, command, label);
    }

    /** Primera palabra de la orden sin la raíz: «komaedit texture rockify» → «texture». */
    static String labelOf(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        String[] words = command.strip().split("\\s+");
        int index = ROOTS.contains(words[0].toLowerCase(Locale.ROOT)) ? 1 : 0;
        return index < words.length ? words[index] : words[0];
    }
}
