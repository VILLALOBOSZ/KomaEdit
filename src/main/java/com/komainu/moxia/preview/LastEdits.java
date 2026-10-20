package com.komainu.moxia.preview;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La última orden de cada jugador que llegó a colocar (o a enseñar) un plan, para {@code /komaedit repeat}: se
 * vuelve a ejecutar tal cual, así las que trabajan donde está o donde mira el jugador caen en su sitio de ahora y las
 * de la selección usan la selección de ese momento. Los clics de herramienta y las confirmaciones no cuentan.
 */
public final class LastEdits {
    private static final Set<String> REPEAT = Set.of("repeat", "repetir");
    private static final Map<UUID, Entry> LAST = new ConcurrentHashMap<>();

    private LastEdits() {
    }

    /**
     * @param command la orden sin la barra ni la bandera de vista previa
     * @param preview si se pidió con vista previa
     */
    public record Entry(String command, boolean preview) {
        /** La orden que repite esta entrada: con {@code --preview} si se pidió con vista previa o ahora se pide. */
        public String commandFor(boolean forcePreview) {
            return preview || forcePreview ? command + " " + PreviewFlags.LONG : command;
        }
    }

    /** Apunta la orden de {@code request} si viene de una orden escrita (no de un pincel ni de confirmar). */
    public static void remember(UUID player, EditRequest request) {
        if (player == null || request == null || request.command() == null || request.command().isBlank()) {
            return;
        }
        if (request.kind() != EditRequest.Kind.DEFAULT && request.kind() != EditRequest.Kind.PREVIEW) {
            return;
        }
        String label = request.label() == null ? "" : request.label().toLowerCase(Locale.ROOT);
        if (REPEAT.contains(label)) {
            return;
        }
        LAST.put(player, new Entry(request.command().strip(), request.preview()));
    }

    public static Optional<Entry> last(UUID player) {
        return Optional.ofNullable(LAST.get(player));
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }

    public static void clearAll() {
        LAST.clear();
    }
}
