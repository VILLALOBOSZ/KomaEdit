package com.komainu.moxia.studio;

import java.util.Locale;
import java.util.function.Function;

/**
 * El nombre que el Studio enseña de un bloque: el traducido si el juego (o el mod) lo tiene y, si no, uno legible
 * sacado del id («examplemod:mossy_slate_tiles» → «Mossy Slate Tiles»), nunca el id crudo ni la clave de traducción.
 */
public final class BlockNames {
    private BlockNames() {
    }

    /**
     * @param id        el id del bloque, con o sin espacio de nombres
     * @param translate la traducción de una clave, o {@code null} (o la propia clave) si no la hay
     */
    public static String name(String id, Function<String, String> translate) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String full = id.contains(":") ? id : "minecraft:" + id;
        int colon = full.indexOf(':');
        String key = full.substring(0, colon) + "." + full.substring(colon + 1).replace('/', '.');
        for (String prefix : new String[]{"block.", "item."}) {
            String translated = translate.apply(prefix + key);
            if (translated != null && !translated.isBlank() && !translated.equals(prefix + key)) {
                return translated;
            }
        }
        return readable(full);
    }

    /** «minecraft:spruce_planks» → «Spruce Planks»: la última parte del id, por palabras y con mayúscula. */
    public static String readable(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_\\-.\\s]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.isEmpty() ? id : out.toString();
    }
}
