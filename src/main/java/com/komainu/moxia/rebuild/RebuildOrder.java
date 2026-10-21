package com.komainu.moxia.rebuild;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** En qué orden aparecen los bloques de una reconstrucción animada. */
public enum RebuildOrder {
    /** De abajo arriba, capa a capa; cada capa en zigzag, como una impresora 3D. */
    LAYERS("capas", "de abajo arriba por capas", "layers", "capa"),
    /** Desde un punto hacia fuera, en ondas. */
    WAVES("ondas", "desde un punto hacia fuera", "waves", "onda", "centro"),
    /** Barrido por un eje ({@code -e}). */
    AXIS("eje", "barrido por un eje", "axis"),
    /** Al azar, con una semilla. */
    RANDOM("azar", "al azar", "random", "aleatorio"),
    /** Como un constructor: cimientos, estructura, paredes, tejado, detalles y vegetación. */
    BUILDER("constructor", "como un constructor", "builder", "obra"),
    /** En el orden en que se colocó cada bloque, si está apuntado. */
    HISTORY("historial", "en el orden en que se colocó", "history", "orden");

    private final String id;
    private final String description;
    private final List<String> aliases;

    RebuildOrder(String id, String description, String... aliases) {
        this.id = id;
        this.description = description;
        this.aliases = List.of(aliases);
    }

    /** El nombre que se escribe en la orden. */
    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    /** El orden por su nombre o un alias, sin mayúsculas ni tildes. */
    public static Optional<RebuildOrder> parse(String text) {
        String wanted = fold(text);
        for (RebuildOrder order : values()) {
            if (order.id.equals(wanted) || order.aliases.contains(wanted)) {
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }

    /** Todos los nombres, para el autocompletado. */
    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (RebuildOrder order : values()) {
            names.add(order.id);
        }
        return names;
    }

    static String fold(String text) {
        String lower = Normalizer.normalize(text == null ? "" : text.trim(), Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT);
        return lower.replaceAll("\\p{M}", "");
    }
}
