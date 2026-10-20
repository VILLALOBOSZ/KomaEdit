package com.komainu.moxia.studio;

/**
 * Lo que explica un control del Studio a quien lo ve por primera vez: qué le pasa a lo que se construye al moverlo
 * («Más alto el tronco»), no qué parámetro es, y, cuando ayuda, sus extremos en palabras («de fino a grueso»). Los
 * extremos salen de un texto {@code bajo|alto}; sin barra no hay extremos.
 *
 * @param effect qué cambia en el objeto
 * @param low    el extremo del valor más bajo, o vacío
 * @param high   el del valor más alto, o vacío
 */
public record ControlHelp(String effect, String low, String high) {
    public ControlHelp {
        effect = effect == null ? "" : effect.strip();
        low = low == null ? "" : low.strip();
        high = high == null ? "" : high.strip();
        if (low.isEmpty() || high.isEmpty()) {
            low = "";
            high = "";
        }
    }

    /** {@code ends} es {@code bajo|alto} (o vacío, o una clave que no se encontró y por tanto no lleva barra). */
    public static ControlHelp of(String effect, String ends) {
        if (ends == null || ends.indexOf('|') < 0) {
            return new ControlHelp(effect, "", "");
        }
        int bar = ends.indexOf('|');
        return new ControlHelp(effect, ends.substring(0, bar), ends.substring(bar + 1));
    }

    public boolean hasEnds() {
        return !low.isEmpty();
    }

    /** Los extremos con {@code format} («de %s a %s»: «de fino a grueso»), o vacío. */
    public String ends(String format) {
        return hasEnds() ? String.format(java.util.Locale.ROOT, format, low, high) : "";
    }

    /** Lo corto: qué cambia y, debajo, los extremos con {@code format}. */
    public String brief(String format) {
        return hasEnds() ? effect + "\n" + ends(format) : effect;
    }
}
