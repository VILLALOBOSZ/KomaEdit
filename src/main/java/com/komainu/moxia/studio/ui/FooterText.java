package com.komainu.moxia.studio.ui;

import java.util.List;

/**
 * Una línea del pie que siempre cabe: los trozos van de más a menos importante y, si no hay sitio, se quitan desde
 * el final. Solo si ni el primero cabe se corta con «…». Así el pie nunca enseña «Tejado a…» a media palabra.
 */
public final class FooterText {
    public static final String SEPARATOR = " · ";

    private FooterText() {
    }

    /** Los trozos que caben en {@code width}, unidos con « · ». */
    public static String fit(StudioUi ui, List<String> segments, int width) {
        return fit(ui::width, segments, width, ui::fit);
    }

    /** Lo mismo con una medida de texto cualquiera (para las pruebas). */
    public static String fit(java.util.function.ToIntFunction<String> measure, List<String> segments, int width,
                             java.util.function.BiFunction<String, Integer, String> cut) {
        StringBuilder line = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            String candidate = line.isEmpty() ? segment : line + SEPARATOR + segment;
            if (measure.applyAsInt(candidate) > width) {
                if (line.isEmpty()) {
                    return cut.apply(segment, width);
                }
                break;
            }
            line.setLength(0);
            line.append(candidate);
        }
        return line.toString();
    }
}
