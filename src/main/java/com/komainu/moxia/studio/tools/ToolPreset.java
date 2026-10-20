package com.komainu.moxia.studio.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ajuste rápido de una tarjeta: unos pocos valores que se ponen de golpe. Texto: {@code gui.komaedit.tools.preset.<id>}
 * y {@code ….help}.
 */
public record ToolPreset(String id, Map<String, Object> values) {
    public ToolPreset {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static ToolPreset of(String id, Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return new ToolPreset(id, values);
    }

    public String labelKey() {
        return "gui.komaedit.tools.preset." + id;
    }

    public String helpKey() {
        return labelKey() + ".help";
    }
}
