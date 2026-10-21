package com.komainu.moxia.ruler;

import com.komainu.moxia.progress.ProgressLine;

import java.util.Locale;

/** Números de la regla: enteros sin decimales y agrupados por miles; el resto, con un decimal. */
public final class RulerFormat {
    private RulerFormat() {
    }

    /** {@code 12}, {@code 12,4} o {@code 1 250} con el separador decimal del idioma. */
    public static String number(double value, char decimal) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 1.0E-9) {
            return ProgressLine.group(Math.round(rounded));
        }
        String text = String.format(Locale.ROOT, "%.1f", Math.abs(rounded));
        int dot = text.indexOf('.');
        String sign = rounded < 0 ? "-" : "";
        return sign + ProgressLine.group(Long.parseLong(text.substring(0, dot))) + decimal + text.substring(dot + 1);
    }
}
