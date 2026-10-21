package com.komainu.moxia.studio.tools;

import java.util.List;
import java.util.Set;

/**
 * «Calcar imagen», como «Open Reference Image» de Axiom: abre la lista de imágenes de
 * {@code config/komaedit/references/} para calcar fachadas, estatuas o pixel art, en pantalla o en el mundo. Es solo
 * del cliente ({@code /kegui referencias}, que atiende el propio cliente aunque la mande el Studio): no pasa por el
 * servidor, no pide permisos y no cambia el mundo.
 */
public final class ReferenceCard extends ToolCard {
    /** Orden del cliente que abre la lista de referencias. */
    public static final String OPEN = "kegui referencias";
    /** Orden del cliente que enseña u oculta todas las referencias. */
    public static final String TOGGLE = "kegui referencias alternar";

    ReferenceCard() {
        super("reference", ToolTarget.MODE,
                List.of("reference", "referencia", "referencias", "imagen", "image", "calcar", "trace", "plantilla",
                        "foto", "photo", "pixel", "pixelart", "fachada", "plano", "axiom"),
                List.of());
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        return OPEN;
    }

    @Override
    public boolean clientOnly(ToolValues values) {
        return true;
    }

    @Override
    public boolean previewable() {
        return false;
    }

    @Override
    public List<String> permissions(ToolValues values) {
        return List.of();
    }

    @Override
    public Set<String> allPermissions() {
        return Set.of();
    }

    /** Sin permisos que pedir, nunca sale apagada. */
    @Override
    public boolean locked(ToolContext context) {
        return false;
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(new ToolAction("reference_toggle", List.of(), false, values -> TOGGLE));
    }
}
