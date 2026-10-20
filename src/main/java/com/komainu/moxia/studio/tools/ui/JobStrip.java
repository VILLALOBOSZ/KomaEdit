package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.layout.StudioLayout;

import java.util.Locale;

/**
 * El pie del Studio mientras algo está en marcha (en cualquier etapa): la fase con su porcentaje, ritmo y tiempo
 * restante, una barra fina y los botones de la vista previa (Confirmar y Descartar) o Cancelar. Los botones mandan
 * {@code /komaedit confirm} y {@code /komaedit cancel}, las mismas órdenes que los botones del chat.
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

    /** «Tejado · colocando 45 % · 12 300 bl/s · ~8 s», o «Vista previa · 1 234 cambios · caduca en 74 s». */
    public static String text(ProgressLine line, long changes, Actions actions) {
        if (line.phase() == ProgressLine.Phase.WAITING) {
            // En la espera, done es lo que queda y total el plazo, en milisegundos.
            return actions.tr("gui.komaedit.job.waiting", line.title(), ProgressLine.group(changes),
                    ProgressLine.duration(line.done() / 1_000.0));
        }
        StringBuilder text = new StringBuilder(line.title()).append(" · ").append(actions.tr(phaseKey(line.phase())));
        if (line.measured()) {
            text.append(' ').append(line.percent()).append(" %");
            double rate = line.rate();
            if (rate > 0.0 && line.phase() == ProgressLine.Phase.PLACING) {
                text.append(" · ").append(actions.tr("gui.komaedit.job.rate", ProgressLine.group(Math.round(rate))));
            }
            double remaining = line.remainingSeconds();
            if (remaining >= 1.0) {
                text.append(" · ~").append(ProgressLine.duration(remaining));
            }
        } else {
            text.append(" · ").append(ProgressLine.duration(line.elapsedNanos() / 1_000_000_000.0));
        }
        return text.toString();
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

    public static ToolsUi.Ink ink(ProgressLine.Phase phase) {
        return switch (phase) {
            case WAITING -> ToolsUi.Ink.WARNING;
            case PLACING -> ToolsUi.Ink.SUCCESS;
            default -> ToolsUi.Ink.ACCENT_HOVER;
        };
    }

    /**
     * Pinta la línea de estado, la barra y los botones sobre el pie. {@code build} es el botón normal de la etapa
     * (Construir), que mientras se coloca algo sigue a la izquierda de Cancelar.
     */
    public static void draw(ToolsUi ui, StudioLayout.Frame frame, ProgressLine line, long changes, long millis,
                            Actions actions, String buildLabel, ToolsUi.Tone buildTone, Runnable build,
                            String buildHelp) {
        Rect status = frame.footerStatus();
        String text = text(line, changes, actions);
        ui.label(text, status.x(), status.y(), status.width(), ink(line.phase()));
        ui.register(status).tooltip(text);
        Rect bar = frame.footerProgress();
        ui.fill(bar, ToolsUi.Ink.TRACK);
        int filled = (int) Math.round(bar.width() * fraction(line, millis));
        ui.fill(new Rect(bar.x(), bar.y(), filled, bar.height()),
                line.phase() == ProgressLine.Phase.WAITING ? ToolsUi.Ink.WARNING : ToolsUi.Ink.ACCENT);
        if (line.phase() == ProgressLine.Phase.WAITING) {
            String discard = actions.tr("gui.komaedit.job.discard");
            Rect[] buttons = frame.footerButtons(ui.width(discard) + 20);
            ui.button(buttons[0], actions.tr("gui.komaedit.job.confirm"), ToolsUi.Tone.PRIMARY)
                    .tooltip(actions.tr("gui.komaedit.job.confirm.help"))
                    .onClick(() -> {
                        actions.send(CONFIRM);
                        actions.status(actions.tr("gui.komaedit.job.confirmed"));
                    });
            ui.button(buttons[1], discard, ToolsUi.Tone.DANGER)
                    .tooltip(actions.tr("gui.komaedit.job.discard.help"))
                    .onClick(() -> {
                        actions.send(CANCEL);
                        actions.status(actions.tr("gui.komaedit.job.discarded"));
                    });
            return;
        }
        String cancel = actions.tr("gui.komaedit.job.cancel");
        Rect[] buttons = frame.footerButtons(ui.width(cancel) + 20);
        ui.button(buttons[0], buildLabel, buildTone).onClick(build).tooltip(buildHelp);
        ui.button(buttons[1], cancel, ToolsUi.Tone.DANGER)
                .tooltip(actions.tr("gui.komaedit.job.cancel.help"))
                .onClick(() -> {
                    actions.send(CANCEL);
                    actions.status(actions.tr("gui.komaedit.job.cancelled"));
                });
    }
}
