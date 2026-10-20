package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.NumericRange;
import com.komainu.moxia.studio.ValueUnit;

import java.util.function.BiFunction;

/**
 * Cómo se enseña un número del Studio: el valor con su unidad (y en corto), el rango escrito, dónde caen el valor y
 * el de serie en la barra, si ya no es el de serie y la ayuda con todo eso. Lo usan la etapa Herramientas y las
 * proporciones de las figuras, así que todos los números se leen igual.
 *
 * @param value      «18 bloques», o el automático («auto» o el que diga la herramienta)
 * @param shortValue lo mismo con la unidad en corto
 * @param range      «3–64 bloques»
 * @param fraction   dónde cae el valor en el rango (0–1)
 * @param defaultFraction dónde cae el de serie, o {@code NaN} en los automáticos
 * @param modified   si el valor ya no es el de serie
 * @param base       el número desde el que se mueve (el suyo, el automático o el de arranque)
 */
public record NumberView(String value, String shortValue, String range, double fraction, double defaultFraction,
                         boolean modified, int base) {
    /**
     * @param value     el valor guardado, o {@code null} en automático
     * @param autoHint  lo que vale en automático según la herramienta (en unidades de la orden), o {@code null}
     * @param start     desde dónde arranca si no se sabe el automático
     * @param automatic si el ajuste admite automático (entonces no hay «de serie» que marcar)
     */
    public static NumberView of(Integer value, String autoHint, int start, int defaultValue, boolean automatic,
                                NumericRange range, int decimals, ValueUnit unit,
                                BiFunction<String, Object[], String> tr) {
        Integer hinted = autoHint == null ? null : parseHint(autoHint, decimals);
        int base = value != null ? value : hinted != null ? range.clamp(hinted) : start;
        String autoWord = tr.apply("gui.komaedit.tools.auto", new Object[0]);
        String shown;
        String compact;
        if (value != null) {
            shown = unit.format(value, decimals, tr);
            compact = unit.formatShort(value, decimals, tr);
        } else if (hinted != null) {
            shown = tr.apply("gui.komaedit.unit.auto_value", new Object[]{unit.format(hinted, decimals, tr)});
            compact = tr.apply("gui.komaedit.unit.auto_value", new Object[]{unit.formatShort(hinted, decimals, tr)});
        } else {
            shown = autoHint != null && !autoHint.isBlank() ? autoHint : autoWord;
            compact = shown;
        }
        String low = unit.number(range.min(), decimals);
        String high = unit.format(range.max(), decimals, tr);
        String written = tr.apply("gui.komaedit.unit.range", new Object[]{low, high});
        double defaultFraction = automatic ? Double.NaN : range.fraction(defaultValue);
        boolean modified = automatic ? value != null : value != null && value != defaultValue;
        return new NumberView(shown, compact, written, range.fraction(base), defaultFraction, modified, base);
    }

    /** El automático que dice la herramienta («18», «1.3»), escalado; {@code null} si no es un número suelto. */
    static Integer parseHint(String hint, int decimals) {
        if (hint.contains("%")) {
            // «15%» del vano y parecidos: no son de la unidad del ajuste, se enseñan tal cual.
            return null;
        }
        String cleaned = hint.strip().replace(',', '.');
        try {
            double parsed = Double.parseDouble(cleaned);
            return (int) Math.round(parsed * Math.pow(10, decimals));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** La ayuda de una fila numérica: qué cambia, el rango con el de serie y, en una línea, los gestos. */
    public String help(String what, String defaultText, BiFunction<String, Object[], String> tr) {
        return what + "\n" + tr.apply("gui.komaedit.unit.help", new Object[]{range, defaultText});
    }
}
