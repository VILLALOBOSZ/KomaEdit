package com.komainu.moxia.studio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * La unidad con la que el Studio enseña un número: «18 bloques», «120 %», «45°», «×1.5». El valor guardado no
 * cambia (el que va en la orden); solo cambia cómo se lee y cómo se escribe a mano.
 *
 * <p>Con {@link #PERCENT} el valor real es una fracción (1.0 se ve «100 %») y lo escrito se lee en tanto por
 * ciento; con {@link #PERCENT_POINTS} el valor ya está en tanto por ciento. Las demás solo añaden la palabra.</p>
 */
public enum ValueUnit {
    /** Sin unidad: cuentas («Buhardillas 2») o niveles sin medida. */
    NONE("", false),
    BLOCKS("gui.komaedit.unit.blocks", false),
    /** Fracción que se lee en tanto por ciento: 1.25 es «125 %». */
    PERCENT("gui.komaedit.unit.percent", true),
    /** Ya en tanto por ciento: 40 es «40 %». */
    PERCENT_POINTS("gui.komaedit.unit.percent", false),
    DEGREES("gui.komaedit.unit.degrees", false),
    /** Veces que se repite algo (pasadas de la erosión). */
    TIMES("gui.komaedit.unit.times", false),
    /** Capas de nieve. */
    LAYERS("gui.komaedit.unit.layers", false),
    /** Tramos del camino («cada 4 tramos»). */
    SEGMENTS("gui.komaedit.unit.segments", false),
    /** Un factor sobre lo normal: «×1.5». */
    MULTIPLIER("gui.komaedit.unit.multiplier", false),
    /** Pendiente: bloques que sube por cada bloque, «1.5:1». */
    RATIO("gui.komaedit.unit.ratio", false),
    /** Una altura absoluta del mundo: «y 62». */
    Y("gui.komaedit.unit.y", false),
    /** Tiempo: «1.5 s». */
    SECONDS("gui.komaedit.unit.seconds", false),
    /** Ritmo de la reconstrucción animada: «4 bloques por tick». */
    BLOCKS_PER_TICK("gui.komaedit.unit.blocks_per_tick", false);

    private final String key;
    private final boolean fraction;

    ValueUnit(String key, boolean fraction) {
        this.key = key;
        this.fraction = fraction;
    }

    /** Clave de la unidad con {@code %s} para el número; vacía sin unidad. */
    public String key() {
        return key;
    }

    /** Clave de la forma corta («18 bl.»), para cuando la larga no cabe. */
    public String shortKey() {
        return key.isEmpty() ? "" : key + ".short";
    }

    /**
     * El número que se ve, sin unidad: con {@link #PERCENT}, en tanto por ciento.
     *
     * @param scaled   el valor guardado (entero escalado)
     * @param decimals decimales del valor guardado
     */
    public String number(int scaled, int decimals) {
        BigDecimal value = BigDecimal.valueOf(scaled, decimals);
        if (fraction) {
            value = value.movePointRight(2);
        }
        String text = value.stripTrailingZeros().toPlainString();
        return text.equals("-0") ? "0" : text;
    }

    /** El número con su unidad, traducido con {@code tr} (clave y argumentos). */
    public String format(int scaled, int decimals, BiFunction<String, Object[], String> tr) {
        return withUnit(number(scaled, decimals), key, scaled, decimals, tr);
    }

    /** Como {@link #format}, con la forma corta de la unidad. */
    public String formatShort(int scaled, int decimals, BiFunction<String, Object[], String> tr) {
        return withUnit(number(scaled, decimals), shortKey(), scaled, decimals, tr);
    }

    private String withUnit(String number, String unitKey, int scaled, int decimals,
                            BiFunction<String, Object[], String> tr) {
        if (unitKey.isEmpty()) {
            return number;
        }
        // «1 bloque» y no «1 bloques»: la forma en singular, si el idioma la tiene, va en .one.
        boolean one = BigDecimal.valueOf(scaled, decimals).compareTo(BigDecimal.ONE) == 0 && !fraction;
        if (one && this != PERCENT_POINTS) {
            String singular = tr.apply(unitKey + ".one", new Object[]{number});
            if (!singular.equals(unitKey + ".one")) {
                return singular;
            }
        }
        return tr.apply(unitKey, new Object[]{number});
    }

    /**
     * Lee un número escrito en esta unidad y lo devuelve como valor guardado (sin ajustar al rango). Acepta coma
     * decimal, el signo menos tipográfico y la unidad escrita detrás («120 %», «45°»).
     *
     * @return el valor escalado, o {@code null} si no es un número
     */
    public Integer parse(String input, int decimals) {
        String typed = input.strip().replace(',', '.').replace('−', '-').toLowerCase(Locale.ROOT);
        typed = typed.replaceAll("[^0-9.+\\-]+$", "").replaceAll("^[^0-9.+\\-]+", "").strip();
        if (typed.isEmpty() || typed.equals("-") || typed.equals(".") || typed.length() > 14) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(typed);
            if (fraction) {
                value = value.movePointLeft(2);
            }
            BigDecimal scaled = value.movePointRight(decimals).setScale(0, RoundingMode.HALF_UP);
            if (scaled.abs().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                return null;
            }
            return scaled.intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }
}
