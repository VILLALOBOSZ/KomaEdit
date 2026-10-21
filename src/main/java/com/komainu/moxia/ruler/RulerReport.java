package com.komainu.moxia.ruler;

import com.komainu.moxia.feedback.Words;

import java.util.ArrayList;
import java.util.List;

/**
 * Los textos de la regla: el resumen de la barra de acción y el informe que se copia al portapapeles. Los textos salen
 * de los idiomas ({@code gui.komaedit.ruler.*}) a través de {@link Words}, así que se prueban fuera del juego con los
 * archivos de idioma.
 */
public final class RulerReport {
    private RulerReport() {
    }

    /** Una línea para la barra de acción: el modo y lo que mide, o qué falta. */
    public static String summary(Ruler ruler, Words words) {
        String head = words.tr("gui.komaedit.ruler.title") + " · " + words.tr(ruler.mode().translationKey());
        return head + " · " + switch (ruler.measure()) {
            case RulerMeasure.Pending pending -> pending(pending, words);
            case RulerMeasure.Line line -> words.tr("gui.komaedit.ruler.summary.line", line.points().size(),
                    number(line.length(), words), number(line.manhattan(), words));
            case RulerMeasure.Circle circle -> words.tr("gui.komaedit.ruler.summary.circle",
                    number(circle.radius(), words), number(circle.diameter(), words));
            case RulerMeasure.Box box -> words.tr("gui.komaedit.ruler.summary.box", number(box.width(), words),
                    number(box.height(), words), number(box.length(), words), number(box.volume(), words));
        };
    }

    /** Qué hacer ahora, según cuántos puntos hay. */
    public static String pending(RulerMeasure.Pending pending, Words words) {
        if (pending.points().isEmpty()) {
            return words.tr("gui.komaedit.ruler.pending.none");
        }
        return words.tr("gui.komaedit.ruler.pending.one." + pending.mode().id());
    }

    /** El informe completo para el portapapeles, una línea por dato. */
    public static String text(Ruler ruler, Words words) {
        List<String> lines = new ArrayList<>();
        String head = words.tr("gui.komaedit.ruler.title") + " · " + words.tr(ruler.mode().translationKey());
        switch (ruler.measure()) {
            case RulerMeasure.Pending pending -> {
                lines.add(head);
                lines.add(pending(pending, words));
            }
            case RulerMeasure.Line line -> {
                lines.add(head + " · " + words.tr("gui.komaedit.ruler.points", line.points().size()));
                for (int index = 0; index < line.segments().size(); index++) {
                    RulerMeasure.Segment segment = line.segments().get(index);
                    RulerMeasure.Delta delta = segment.delta();
                    String row = words.tr("gui.komaedit.ruler.segment", index + 1, index + 2,
                            number(delta.euclidean(), words), number(delta.manhattan(), words),
                            number(delta.x(), words), number(delta.y(), words), number(delta.z(), words));
                    if (segment.blocks() > 0) {
                        row += " · " + words.tr("gui.komaedit.ruler.blocks", RulerFormat.number(segment.blocks(),
                                words.decimal()));
                    }
                    lines.add(row);
                }
                RulerMeasure.Delta axes = line.axes();
                lines.add(words.tr("gui.komaedit.ruler.total", number(line.length(), words),
                        number(line.manhattan(), words), number(axes.x(), words), number(axes.y(), words),
                        number(axes.z(), words)));
                if (line.segments().size() > 1) {
                    RulerMeasure.Delta straight = line.straight();
                    lines.add(words.tr("gui.komaedit.ruler.straight", line.points().size(),
                            number(straight.euclidean(), words), number(straight.manhattan(), words),
                            number(straight.x(), words), number(straight.y(), words), number(straight.z(), words)));
                }
                lines.add(words.tr("gui.komaedit.ruler.list", String.join(" ",
                        line.points().stream().map(RulerPoint::coordinates).toList())));
            }
            case RulerMeasure.Circle circle -> {
                lines.add(head);
                lines.add(words.tr("gui.komaedit.ruler.circle.points", circle.center().coordinates(),
                        circle.edge().coordinates(), circle.plane()));
                String size = words.tr("gui.komaedit.ruler.circle.size", number(circle.radius(), words),
                        number(circle.diameter(), words));
                if (circle.blocksAcross() > 0) {
                    size += " · " + words.tr("gui.komaedit.ruler.circle.across",
                            RulerFormat.number(circle.blocksAcross(), words.decimal()));
                }
                lines.add(size);
                lines.add(words.tr("gui.komaedit.ruler.circle.round", number(circle.circumference(), words),
                        number(circle.area(), words)));
                if (circle.offset() > 1.0E-9) {
                    lines.add(words.tr("gui.komaedit.ruler.circle.offset", number(circle.offset(), words),
                            circle.plane()));
                }
            }
            case RulerMeasure.Box box -> {
                lines.add(head);
                lines.add(words.tr("gui.komaedit.ruler.box.points", box.first().coordinates(),
                        box.second().coordinates()));
                lines.add(words.tr("gui.komaedit.ruler.box.size", number(box.width(), words),
                        number(box.height(), words), number(box.length(), words)));
                lines.add(words.tr(box.blocks() ? "gui.komaedit.ruler.box.blocks" : "gui.komaedit.ruler.box.volume",
                        number(box.volume(), words), number(box.diagonal(), words)));
            }
        }
        return String.join("\n", lines);
    }

    static String number(double value, Words words) {
        return RulerFormat.number(value, words.decimal());
    }
}
