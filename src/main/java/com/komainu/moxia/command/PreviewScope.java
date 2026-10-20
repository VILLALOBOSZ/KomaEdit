package com.komainu.moxia.command;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué órdenes de {@code /komaedit} admiten la bandera de vista previa y dónde {@code -v} ya significa otra cosa.
 * Las rutas son los nombres bajo la raíz en minúsculas ({@code ["deform", "noise", …]}).
 *
 * <p>Una orden nueva que edite el mundo la tiene sin hacer nada; solo hay que tocar esto si es de gestión (no
 * coloca bloques) o si ya usa {@code -v} para otra cosa.
 */
final class PreviewScope {
    /** Familias sin vista previa: ayuda, ajustes, trayectorias, pinceles (que tienen la suya) y gestión. */
    private static final Set<String> NO_PREVIEW = Set.of("menu", "quick", "gui", "shapes", "estimate", "estimar",
            "confirm", "confirmar", "cancel", "cancelar", "preview", "vistaprevia", "path", "ruta", "sculpt", "brush",
            "pincel", "ezbrush", "superwand", "supervarita", "job", "project", "help", "ayuda", "info", "reload",
            "recargar", "permissions", "permisos", "noise", "ruido", "sel", "seleccion");
    private static final Set<String> PALETTE = Set.of("palette", "paleta");
    /** De las paletas solo editan el mundo estas. */
    private static final Set<String> PALETTE_EDITS = Set.of("swap", "place");
    private static final Set<String> BLUEPRINT = Set.of("blueprint", "blueprints", "planos", "plano");
    /** De la biblioteca de planos solo coloca bloques {@code place}. */
    private static final Set<String> BLUEPRINT_EDITS = Set.of("place", "colocar");
    /** Familias donde ezEdits ya usa {@code -v} en todos los modos: ahí la vista previa es {@code --preview}. */
    private static final Set<String> SHORT_FLAG_FAMILIES = Set.of("weather", "envejecer");
    /** Y familias donde lo usan solo algunos modos (la segunda palabra). */
    private static final Map<String, Set<String>> SHORT_FLAG_MODES = Map.of(
            // vertical
            "deform", Set.of("noise", "ruido", "voxelize", "voxelise", "vóxeles", "voxeles"),
            "deformar", Set.of("noise", "ruido", "voxelize", "voxelise", "vóxeles", "voxeles"),
            // variación de la mezcla y de la luz
            "texture", Set.of("blend", "mezclar", "blocklight", "luzbloque"),
            "textura", Set.of("blend", "mezclar", "blocklight", "luzbloque"),
            // patrón de las aristas
            "shape", Set.of("polydome", "polidomo"),
            "forma", Set.of("polydome", "polidomo"));

    private PreviewScope() {
    }

    static boolean eligible(List<String> path) {
        if (path.isEmpty() || NO_PREVIEW.contains(path.getFirst())) {
            return false;
        }
        if (PALETTE.contains(path.getFirst())) {
            return path.size() > 1 && PALETTE_EDITS.contains(path.get(1));
        }
        if (BLUEPRINT.contains(path.getFirst())) {
            return path.size() > 1 && BLUEPRINT_EDITS.contains(path.get(1));
        }
        return true;
    }

    static boolean shortFlagTaken(List<String> path) {
        if (path.isEmpty()) {
            return false;
        }
        if (SHORT_FLAG_FAMILIES.contains(path.getFirst())) {
            return true;
        }
        Set<String> modes = SHORT_FLAG_MODES.get(path.getFirst());
        return modes != null && path.size() > 1 && modes.contains(path.get(1));
    }
}
