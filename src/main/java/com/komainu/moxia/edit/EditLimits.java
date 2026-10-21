package com.komainu.moxia.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Límites de tamaño de una edición, comprobados antes de empezar: si pasa del máximo, se dice cuánto ocupa, cuál es
 * el límite y en cuántos trozos partirla, en vez de leer, calcular y fallar a medias. Para una selección en caja da
 * además los trozos (cortes por el eje horizontal más largo, en bordes de chunk cuando se puede).
 *
 * <p>Java puro.</p>
 */
public final class EditLimits {
    private EditLimits() {
    }

    /** Qué límite se pasa. */
    public enum Kind {
        /** {@code maxBlocksPerOperation} de la configuración del servidor. */
        SERVER("el límite del servidor es %s por edición"),
        /** Lo que KomaEdit puede leer de una vez para calcular sobre la selección. */
        CAPTURE("KomaEdit lee como mucho %s de una vez"),
        /** El {@code //limit} de la sesión de WorldEdit del jugador. */
        WORLDEDIT("tu //limit de WorldEdit es %s");

        private final String text;

        Kind(String text) {
            this.text = text;
        }
    }

    /**
     * Una edición que no cabe.
     *
     * @param blocks bloques de la edición (o de la selección)
     * @param limit  el máximo
     * @param parts  trozos que hacen falta para que cada uno quepa
     */
    public record Excess(Kind kind, long blocks, long limit, int parts) {
        /** «La selección tiene 3 200 000 bloques y el límite del servidor es 1 500 000 por edición». */
        public String describe(String subject) {
            return subject + " tiene " + group(blocks) + " bloques y " + kind.text.formatted(group(limit) + " bloques");
        }

        /** El aviso entero, con la propuesta: partirla (y, con //limit, subirlo). */
        public String message(String subject, boolean canSelectParts) {
            StringBuilder text = new StringBuilder(describe(subject)).append(": ");
            if (kind == Kind.WORLDEDIT) {
                text.append("súbelo con //limit ").append(blocks).append(" o ");
            }
            text.append("pártela en ").append(parts).append(" trozos de unos ")
                    .append(group((blocks + parts - 1) / parts)).append(" bloques");
            if (canSelectParts) {
                text.append(" (/komaedit trozo 1 selecciona el primero) y repite la orden en cada uno");
            }
            return text.toString();
        }
    }

    /** Si {@code blocks} pasa de {@code limit} (negativo: sin límite). */
    public static Optional<Excess> check(Kind kind, long blocks, long limit) {
        if (limit < 0 || blocks <= limit) {
            return Optional.empty();
        }
        return Optional.of(new Excess(kind, blocks, limit, parts(blocks, limit)));
    }

    /** Trozos que hacen falta para que ninguno pase de {@code limit}. */
    public static int parts(long blocks, long limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("El límite tiene que ser positivo");
        }
        return (int) Math.min(Integer.MAX_VALUE, (Math.max(0L, blocks) + limit - 1) / limit);
    }

    /**
     * Parte una caja en trozos por su eje horizontal más largo para que ninguno tenga más de {@code limit} bloques.
     * Los cortes caen en bordes de chunk si eso no deja trozos demasiado desiguales.
     */
    public static List<BlockBounds> split(BlockBounds box, long limit) {
        long width = (long) box.maximumX() - box.minimumX() + 1;
        long depth = (long) box.maximumZ() - box.minimumZ() + 1;
        long height = (long) box.maximumY() - box.minimumY() + 1;
        boolean alongX = width >= depth;
        long length = alongX ? width : depth;
        long slice = height * (alongX ? depth : width);
        if (slice > limit) {
            throw new IllegalArgumentException("Ni una sola capa de la selección cabe en el límite");
        }
        long maxLength = limit / slice;
        int parts = (int) Math.max(1L, (length + maxLength - 1) / maxLength);
        int start = alongX ? box.minimumX() : box.minimumZ();
        List<int[]> ranges = cuts(start, length, parts, maxLength);
        List<BlockBounds> pieces = new ArrayList<>(ranges.size());
        for (int[] range : ranges) {
            pieces.add(alongX
                    ? new BlockBounds(range[0], box.minimumY(), box.minimumZ(), range[1], box.maximumY(),
                    box.maximumZ())
                    : new BlockBounds(box.minimumX(), box.minimumY(), range[0], box.maximumX(), box.maximumY(),
                    range[1]));
        }
        return pieces;
    }

    /** Tramos {@code [desde, hasta]} de una recta, de como mucho {@code maxLength}, cortando en múltiplos de 16. */
    private static List<int[]> cuts(int start, long length, int parts, long maxLength) {
        List<int[]> ranges = new ArrayList<>();
        long end = start + length;
        long from = start;
        for (int part = 1; part <= parts; part++) {
            long to = part == parts ? end : start + Math.round(length * (double) part / parts);
            long snapped = Math.floorDiv(to + 8, 16) * 16;
            if (part < parts && snapped > from && snapped - from <= maxLength && snapped < end
                    && end - snapped <= maxLength * (parts - part)) {
                to = snapped;
            }
            to = Math.min(to, from + maxLength);
            ranges.add(new int[]{(int) from, (int) (to - 1)});
            from = to;
        }
        while (from < end) {
            long to = Math.min(end, from + maxLength);
            ranges.add(new int[]{(int) from, (int) (to - 1)});
            from = to;
        }
        return ranges;
    }

    static String group(long value) {
        return BuildEstimate.group(value);
    }
}
