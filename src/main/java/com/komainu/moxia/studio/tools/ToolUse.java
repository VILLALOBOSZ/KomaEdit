package com.komainu.moxia.studio.tools;

import java.util.Locale;

/**
 * Cómo se usa una herramienta que vale de dos maneras: el interruptor de arriba de su tarjeta (ajuste {@code use}).
 * Los mismos ajustes sirven para las dos; lo que solo tiene sentido en una (el radio del pincel) se ve solo en esa.
 * Textos: {@code gui.komaedit.tools.option.use.<id>}.
 */
public enum ToolUse {
    /** Una vez, donde haces clic (o en cada punto de la trayectoria). */
    CLICK("click"),
    /** Una vez, sobre toda la selección de WorldEdit. */
    SELECTION("selection"),
    /** Queda en un pincel en la mano: cada clic en el mundo lo aplica ahí. */
    BRUSH("brush");

    /** Id del ajuste del interruptor. */
    public static final String PARAM = "use";

    private final String id;

    ToolUse(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ToolUse byId(String id) {
        for (ToolUse use : values()) {
            if (use.id.equals(id.toLowerCase(Locale.ROOT))) {
                return use;
            }
        }
        throw new IllegalArgumentException("uso desconocido: " + id);
    }
}
