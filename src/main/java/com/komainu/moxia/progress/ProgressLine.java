package com.komainu.moxia.progress;

import java.util.Locale;

/**
 * Una línea de progreso de una operación larga: en qué fase va, cuánto lleva, a qué ritmo y cuánto le falta. La
 * pinta {@link ProgressBars} en la bossbar del jugador; aquí solo están las cuentas y el texto.
 *
 * @param done  hecho en la fase (bloques leídos, colocados, comprobados…); con {@code total} 0 la fase no tiene
 *              medida y solo se cuenta el tiempo
 * @param extra información de la fase (cambios de la vista previa…), o {@code null}
 */
public record ProgressLine(String title, Phase phase, long done, long total, long elapsedNanos, String extra) {
    public enum Phase {
        READING("leyendo"),
        CALCULATING("calculando"),
        PREPARING("preparando la vista previa"),
        WAITING("vista previa"),
        CHECKING("comprobando la zona"),
        PLACING("colocando");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public ProgressLine {
        if (phase == null) {
            throw new IllegalArgumentException("Falta la fase");
        }
        title = title == null || title.isBlank() ? "Operación" : title;
        done = Math.max(0L, done);
        total = Math.max(0L, total);
        elapsedNanos = Math.max(0L, elapsedNanos);
    }

    public boolean measured() {
        return total > 0;
    }

    /** De 0 a 1; sin medida, 0. */
    public float fraction() {
        return measured() ? (float) Math.min(1.0, (double) done / total) : 0.0f;
    }

    /** Porcentaje entero, sin redondear hacia arriba (99 % hasta terminar). */
    public int percent() {
        return measured() ? (int) Math.min(100L, done * 100L / total) : 0;
    }

    /** Unidades por segundo desde que empezó la fase; 0 si aún no hay tiempo para medir. */
    public double rate() {
        return elapsedNanos < 50_000_000L ? 0.0 : done / (elapsedNanos / 1_000_000_000.0);
    }

    /** Segundos que faltan al ritmo actual, o -1 si no se puede saber. */
    public double remainingSeconds() {
        double rate = rate();
        return measured() && rate > 0.0 ? Math.max(0.0, (total - done) / rate) : -1.0;
    }

    /** «Texturizar · colocando 45 % · 12 300 bl/s · ~8 s». */
    public String text() {
        StringBuilder builder = new StringBuilder(title).append(" · ").append(phase.label());
        if (phase == Phase.WAITING) {
            if (extra != null) {
                builder.append(" · ").append(extra);
            }
            return builder.toString();
        }
        if (measured()) {
            builder.append(' ').append(percent()).append(" %");
            double rate = rate();
            if (rate > 0.0) {
                builder.append(" · ").append(group(Math.round(rate))).append(" bl/s");
            }
            double remaining = remainingSeconds();
            if (remaining >= 1.0) {
                builder.append(" · ~").append(duration(remaining));
            }
        } else {
            builder.append(" · ").append(duration(elapsedNanos / 1_000_000_000.0));
        }
        if (extra != null) {
            builder.append(" · ").append(extra);
        }
        return builder.toString();
    }

    public static String group(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < digits.length(); index++) {
            if (index > 0 && (digits.length() - index) % 3 == 0) {
                builder.append(' ');
            }
            builder.append(digits.charAt(index));
        }
        return (value < 0 ? "-" : "") + builder;
    }

    public static String duration(double seconds) {
        long total = Math.max(0L, Math.round(seconds));
        if (total < 60) {
            return total + " s";
        }
        return String.format(Locale.ROOT, "%d min %02d s", total / 60, total % 60);
    }
}
