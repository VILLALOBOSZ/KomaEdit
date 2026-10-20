package com.komainu.moxia.studio.tools;

import com.komainu.moxia.studio.NumericRange;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Un ajuste de una tarjeta de herramienta del Studio. La pantalla lo dibuja según su tipo (fila numérica, ciclo,
 * botones, interruptor, bloque o texto) y la tarjeta lo convierte en la bandera de la orden.
 *
 * <p>La etiqueta sale de {@code gui.komaedit.tools.param.<id>}, la ayuda de la tarjeta
 * ({@link ToolCard#helpKey}) y las opciones fijas de {@code gui.komaedit.tools.option.<id>.<opción>}.</p>
 */
public sealed interface ToolParam {
    Predicate<ToolValues> ALWAYS = values -> true;

    String id();

    /** Si el ajuste pinta algo con estos valores (la altura de la cúpula no sale en un tejado a dos aguas). */
    Predicate<ToolValues> shown();

    /** Etiqueta, compartida por las tarjetas que usan el mismo ajuste (la ayuda es de cada tarjeta). */
    default String labelKey() {
        return "gui.komaedit.tools.param." + id();
    }

    default boolean visible(ToolValues values) {
        return shown().test(values);
    }

    /**
     * Número entero o con {@code decimals} decimales, guardado como entero escalado (1,25 con dos decimales es 125).
     * Con {@code auto} el valor puede quedar vacío: la orden no lleva la bandera y vale el de serie de la herramienta.
     */
    record Number(String id, NumericRange range, int defaultValue, int decimals, boolean auto,
                  Predicate<ToolValues> shown) implements ToolParam {
        public Number {
            if (decimals < 0 || decimals > 2) {
                throw new IllegalArgumentException("decimals " + decimals);
            }
            defaultValue = range.clamp(defaultValue);
            shown = shown == null ? ALWAYS : shown;
        }

        /** Valor escalado para la orden y la pantalla: {@code 125} con dos decimales es {@code 1.25}. */
        public String format(int scaled) {
            if (decimals == 0) {
                return Integer.toString(scaled);
            }
            return BigDecimal.valueOf(scaled, decimals).stripTrailingZeros().toPlainString();
        }

        /** Valor para la pantalla: con decimales, al menos uno ({@code 1.0}, {@code 1.25}). */
        public String display(int scaled) {
            String text = format(scaled);
            return decimals > 0 && !text.contains(".") ? text + ".0" : text;
        }

        public double decimal(int scaled) {
            return scaled / Math.pow(10, decimals);
        }

        /** Escala un valor real al entero del control, redondeando al paso más cercano y dentro del rango. */
        public int scale(double value) {
            return range.clamp((int) Math.round(value * Math.pow(10, decimals)));
        }
    }

    /**
     * Una opción de una lista. {@code source} vacío: las opciones son fijas y traducidas. Con {@code source}
     * («fonts», «biomes»…) la pantalla añade las que tenga el servidor y las enseña tal cual.
     */
    record Choice(String id, List<String> options, String defaultValue, Style style, String source,
                  Predicate<ToolValues> shown) implements ToolParam {
        public enum Style {
            /** Fila ‹ valor ›. */
            CYCLE,
            /** Botones en rejilla, para pocas opciones o para verlas todas de un vistazo. */
            BUTTONS,
            /** Rejilla de miniaturas (sellos). */
            GALLERY,
            /** Buscador con lista (biomas). */
            SEARCH
        }

        public Choice {
            options = List.copyOf(options);
            if (options.isEmpty() && (source == null || source.isEmpty())) {
                throw new IllegalArgumentException("choice " + id + " sin opciones");
            }
            defaultValue = defaultValue == null && !options.isEmpty() ? options.getFirst() : defaultValue;
            style = style == null ? Style.CYCLE : style;
            source = source == null ? "" : source;
            shown = shown == null ? ALWAYS : shown;
        }

        public boolean dynamic() {
            return !source.isEmpty();
        }

        /** Clave de la opción fija; las dinámicas (biomas, fuentes propias) se enseñan por su id. */
        public String optionKey(String option) {
            return "gui.komaedit.tools.option." + id + "." + option.toLowerCase(Locale.ROOT).replace(':', '.');
        }
    }

    record Toggle(String id, boolean defaultValue, Predicate<ToolValues> shown) implements ToolParam {
        public Toggle {
            shown = shown == null ? ALWAYS : shown;
        }
    }

    /**
     * Un bloque o lista de bloques ({@code oak_log,stripped_oak_log}), que se elige en la biblioteca de bloques.
     * {@code defaultValue} {@code null}: de serie no lleva bandera (los materiales de la especie, por ejemplo).
     */
    record Block(String id, String defaultValue, Predicate<ToolValues> shown) implements ToolParam {
        public Block {
            shown = shown == null ? ALWAYS : shown;
        }

        public boolean optional() {
            return defaultValue == null;
        }
    }

    /** Texto libre (el del texto en bloques, una máscara, una lista de bloques…). Vacío = sin bandera. */
    record Text(String id, String defaultValue, int maxLength, boolean required, Predicate<ToolValues> shown)
            implements ToolParam {
        public Text {
            defaultValue = defaultValue == null ? "" : defaultValue;
            shown = shown == null ? ALWAYS : shown;
        }

        /** Texto de ejemplo cuando está vacío. */
        public String placeholderKey() {
            return "gui.komaedit.tools.placeholder." + id();
        }
    }
}
