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
    /**
     * Familias sin vista previa: ayuda, ajustes, trayectorias, pinceles (que tienen la suya), gestión y consultas.
     * {@code color} solo enseña bloques en el chat (y ahí {@code -v} es la variante del degradado), {@code biome} pinta
     * biomas sin bloques que enseñar (y ahí {@code -v} es el relleno vertical) y {@code gradient} coloca por regiones
     * sin pasar por el plan que la vista previa necesita. {@code repeat} lleva su propia {@code -v}: la orden que
     * repite se ejecuta con su propia petición.
     */
    private static final Set<String> NO_PREVIEW = Set.of("menu", "quick", "gui", "shapes", "estimate", "estimar",
            "confirm", "confirmar", "cancel", "cancelar", "preview", "vistaprevia", "path", "ruta", "sculpt", "brush",
            "pincel", "ezbrush", "superwand", "supervarita", "job", "project", "help", "ayuda", "info", "reload",
            "recargar", "permissions", "permisos", "noise", "ruido", "sel", "seleccion", "quiet", "silencio", "wiki",
            "symmetry", "simetria", "sym", "capability", "capacidad", "color", "colour", "analyze", "analizar",
            "materiales", "biome", "bioma", "gradient", "clipboard", "portapapeles", "repeat", "repetir", "jobs",
            "trabajos", "trozo", "part", "debug", "debugprint", "rebuild", "reconstruir", "share", "compartir",
            "display", "displays", "decorar", "vista", "vistas", "view", "views", "nota", "notas", "note", "notes");
    private static final Set<String> PALETTE = Set.of("palette", "paleta");
    /** De las paletas solo editan el mundo estas. */
    private static final Set<String> PALETTE_EDITS = Set.of("swap", "place");
    private static final Set<String> BLUEPRINT = Set.of("blueprint", "blueprints", "planos", "plano");
    /** De la biblioteca de planos solo coloca bloques {@code place}. */
    private static final Set<String> BLUEPRINT_EDITS = Set.of("place", "colocar");
    /**
     * Subórdenes sin vista previa dentro de familias que sí editan (la segunda palabra): las de gestión y el bioma de
     * {@code retocar}, que no tiene bloques que enseñar (y ahí {@code -v} es el relleno vertical).
     */
    private static final Map<String, Set<String>> MANAGEMENT_MODES = Map.of(
            "retocar", Set.of("bioma", "biome"),
            "touchup", Set.of("bioma", "biome"),
            "tree", Set.of("save", "guardar", "list", "lista", "delete", "borrar", "cancel", "cancelar"),
            "arbol", Set.of("save", "guardar", "list", "lista", "delete", "borrar", "cancel", "cancelar"),
            "rock", Set.of("save", "guardar", "list", "lista", "delete", "borrar", "cancel", "cancelar"),
            "roca", Set.of("save", "guardar", "list", "lista", "delete", "borrar", "cancel", "cancelar"),
            "text", Set.of("fonts", "fuentes"),
            "texto", Set.of("fonts", "fuentes"));
    /**
     * Familias que ya usan {@code -v} en todos los modos: ahí la vista previa es {@code --preview}. Envejecer (la
     * vida, como en ezEdits), el texto (de pie) y los biomas (relleno vertical).
     */
    private static final Set<String> SHORT_FLAG_FAMILIES = Set.of("weather", "envejecer", "text", "texto", "biome",
            "bioma");
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
        Set<String> management = MANAGEMENT_MODES.get(path.getFirst());
        return management == null || path.size() < 2 || !management.contains(path.get(1));
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
