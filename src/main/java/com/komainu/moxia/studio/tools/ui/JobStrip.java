package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.ui.StudioTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lo que el pie del Studio cuenta mientras algo está en marcha (en cualquier etapa): la fase con su porcentaje,
 * ritmo y tiempo restante, o la vista previa con sus cambios y cuándo caduca. El pie lo pinta en una línea que
 * siempre cabe: si no hay sitio se quitan primero los datos menos importantes, nunca se corta a media palabra.
 * Los botones mandan {@code /komaedit confirm} y {@code /komaedit cancel}, las mismas órdenes que los del chat.
 */
public final class JobStrip {
    public static final String CONFIRM = "komaedit confirm";
    public static final String CANCEL = "komaedit cancel";

    /** Lo que hacen los botones; la pantalla manda las órdenes. */
    public interface Actions {
        String tr(String key, Object... arguments);

        void send(String command);

        void status(String text);
    }

    private JobStrip() {
    }

    /** Clave del nombre de la fase: {@code gui.komaedit.job.phase.<fase>}. */
    public static String phaseKey(ProgressLine.Phase phase) {
        return "gui.komaedit.job.phase." + phase.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Los trozos de la línea, de más a menos importante: «Colocando 45 %», «~8 s», «12 300 bl/s», «Tejado». En la
     * espera de la vista previa: «3 412 cambios», «caduca en 1 min 14 s», «Tejado a dos aguas».
     */
    public static List<String> segments(ProgressLine line, long changes, Actions actions) {
        List<String> segments = new ArrayList<>();
        if (line.phase() == ProgressLine.Phase.WAITING) {
            // En la espera, done es lo que queda y total el plazo, en milisegundos.
            segments.add(actions.tr("gui.komaedit.job.changes", ProgressLine.group(changes)));
            segments.add(actions.tr("gui.komaedit.job.expires", ProgressLine.duration(line.done() / 1_000.0)));
            segments.add(line.title());
            return segments;
        }
        String phase = capitalize(actions.tr(phaseKey(line.phase())));
        if (line.measured()) {
            segments.add(phase + " " + line.percent() + " %");
            double remaining = line.remainingSeconds();
            if (remaining >= 1.0) {
                segments.add("~" + ProgressLine.duration(remaining));
            }
            double rate = line.rate();
            if (rate > 0.0 && line.phase() == ProgressLine.Phase.PLACING) {
                segments.add(actions.tr("gui.komaedit.job.rate", ProgressLine.group(Math.round(rate))));
            }
        } else {
            segments.add(phase);
            segments.add(ProgressLine.duration(line.elapsedNanos() / 1_000_000_000.0));
        }
        segments.add(line.title());
        return segments;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /** La línea entera, con todos los trozos (para el tooltip). */
    public static String text(ProgressLine line, long changes, Actions actions) {
        return String.join(" · ", segments(line, changes, actions));
    }

    /** Fracción de la barra: la medida o, sin medida, un pulso que va y viene con el tiempo. */
    public static double fraction(ProgressLine line, long millis) {
        if (line.phase() == ProgressLine.Phase.WAITING) {
            return line.total() <= 0 ? 0.0 : Math.clamp((double) line.done() / line.total(), 0.0, 1.0);
        }
        if (line.measured()) {
            return line.fraction();
        }
        return Math.abs(Math.sin(millis / 600.0));
    }

    /** Color de la línea según la fase. */
    public static int color(ProgressLine.Phase phase) {
        return switch (phase) {
            case WAITING -> StudioTheme.WARNING;
            case PLACING -> StudioTheme.SUCCESS;
            default -> StudioTheme.ACCENT_HOVER;
        };
    }
}
