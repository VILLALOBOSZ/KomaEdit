package com.komainu.moxia.gizmo;

import com.komainu.moxia.preview.EditRequest;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Qué se coloca con el fantasma y el gizmo: lo que es un objeto que no depende del sitio. Se calcula una vez (de unos
 * parámetros, del portapapeles, de un plano o de la trayectoria que dibujó el jugador) y da igual dónde acabe:
 *
 * <ul>
 *     <li>{@code paste}: el portapapeles.</li>
 *     <li>{@code place} y {@code blueprint place}: estructuras, schematics, formas y planos.</li>
 *     <li>Figuras y estructuras sobre la trayectoria: {@code build}, {@code bridge}, {@code pillar}, {@code torii},
 *     {@code cloud}, {@code web}, {@code noodles}, {@code lightning}, {@code braid},
 *     {@code noisetube}, {@code expr}, {@code sweep}, {@code ezspline}, {@code loft} y {@code bend}.</li>
 *     <li>Árboles y rocas ({@code tree}, {@code rock}), tejados ({@code roof}), texto ({@code text}) e imágenes
 *     ({@code image}).</li>
 * </ul>
 *
 * Lo que lee el terreno o la selección para decidir qué cambia se queda con su vista previa de siempre: terraformar,
 * envejecer, texturizar, deformar, suavizar, esculpir, pintar, la gravedad y la nieve, los degradados, los biomas, la
 * hiedra (crece sobre lo que hay), {@code scatter} (busca superficies), {@code array} (sigue el terreno con
 * {@code -b}) y los generadores que rellenan la selección. Tampoco los pinceles: cada clic coloca al momento.
 *
 * <p>Lógica pura sobre la orden escrita.</p>
 */
public final class GhostScope {
    private static final Set<String> ROOTS = Set.of("komaedit", "ke", "moxia", "mx");
    private static final Set<String> MOVABLE = Set.of(
            "paste", "pegar",
            "place", "colocar",
            "build", "bridge", "pillar", "pillararches", "torii", "cloud", "nube", "web", "noodles",
            "lightning", "rayos", "lightningcircle", "circulorayos", "braid", "trenza", "noisetube", "erosion",
            "expr", "expresion", "sweep", "perfil", "ezspline", "ezsp", "spline", "loft", "lona", "bend", "doblar",
            "tree", "arbol", "rock", "roca", "roof", "tejado", "text", "texto", "image", "imagen");
    /** Familias donde solo una suborden coloca un objeto. */
    private static final Map<String, Set<String>> MOVABLE_MODES = Map.of(
            "blueprint", Set.of("place", "colocar"),
            "blueprints", Set.of("place", "colocar"),
            "planos", Set.of("place", "colocar"),
            "plano", Set.of("place", "colocar"));

    private GhostScope() {
    }

    /** Si el plan de esta petición se coloca con el fantasma (cuando el jugador lo tiene activado). */
    public static boolean movable(EditRequest request) {
        return request != null && movable(request.command());
    }

    /** Lo mismo con la orden escrita ({@code komaedit place …}, sin barra). */
    public static boolean movable(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        List<String> words = List.of(command.strip().toLowerCase(Locale.ROOT).split("\\s+"));
        int first = ROOTS.contains(words.getFirst().replace("/", "")) ? 1 : 0;
        if (first >= words.size()) {
            return false;
        }
        String family = words.get(first);
        if (MOVABLE.contains(family)) {
            return true;
        }
        Set<String> modes = MOVABLE_MODES.get(family);
        return modes != null && first + 1 < words.size() && modes.contains(words.get(first + 1));
    }

    /**
     * Si con este modo y esta petición el plan va al fantasma: «siempre» con la orden normal y con {@code -v};
     * «con -v» solo con {@code -v}; ya confirmado o desde un pincel, nunca.
     */
    public static boolean applies(GizmoSettings.Mode mode, EditRequest request) {
        if (mode == null || mode == GizmoSettings.Mode.OFF || request == null) {
            return false;
        }
        return switch (request.kind()) {
            case DEFAULT -> mode == GizmoSettings.Mode.ALWAYS;
            case PREVIEW -> true;
            case CONFIRMED, TOOL -> false;
        };
    }
}
