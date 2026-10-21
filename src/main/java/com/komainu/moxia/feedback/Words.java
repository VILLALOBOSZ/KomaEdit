package com.komainu.moxia.feedback;

/**
 * Traduce una clave de idioma con sus argumentos: {@code I18n.translate} en el cliente y los archivos de idioma en
 * las pruebas. Lo usan los textos del mundo que arma Java puro (la regla, las notas).
 */
@FunctionalInterface
public interface Words {
    String tr(String key, Object... arguments);

    /** El separador decimal del idioma ({@code gui.komaedit.ruler.decimal}): coma en español, punto en inglés. */
    default char decimal() {
        String value = tr("gui.komaedit.ruler.decimal");
        return value.length() == 1 ? value.charAt(0) : '.';
    }
}
