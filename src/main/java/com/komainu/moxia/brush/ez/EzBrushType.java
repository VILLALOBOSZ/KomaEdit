package com.komainu.moxia.brush.ez;

import java.util.Locale;
import java.util.Set;

/** Pinceles de paridad con ezEdits ({@code //ezbrush ...}), con sus nombres de allí y alias en español. */
public enum EzBrushType {
    PALETTE_SHIFT("paletteshift", "Desplazar paleta", Set.of('r', 'p'), Set.of(), "desplazar", "ps"),
    GRADIENT("gradient", "Degradado", Set.of('a', 'v', 'w', 'p'), Set.of('n', 'z', 'd'), "degradado", "gr"),
    GRADIENT_STROKE("gradientstroke", "Trazo de degradado", Set.of('a', 'd', 'v', 'w', 'x', 'p'), Set.of('n', 'z'),
            "trazo", "gs"),
    PETAL_PAINTER("petalpainter", "Pintor de pétalos", Set.of('p'), Set.of('n', 'z', 'm'), "petalos", "pp"),
    PLACE("place", "Colocar", Set.of('a', 'x', 'p'), Set.of(), "colocar", "pl"),
    SCATTER("scatter", "Dispersar", Set.of(), Set.of(), "dispersar", "sc"),
    ARRAY("array", "Hilera", Set.of(), Set.of(), "hilera", "ar"),
    TREE("tree", "Árbol", Set.of(), Set.of(), "arbol", "tr"),
    ROCK("rock", "Roca", Set.of(), Set.of(), "roca", "rk"),
    BIOME("biome", "Bioma", Set.of('v'), Set.of('m'), "bioma", "bi"),
    /** Propio de KomaEdit: sello de relieve en escala de grises (paquete {@code terrain}). */
    HEIGHTMAP("heightmap", "Relieve", Set.of('p', 'k'), Set.of('i', 'r', 'm', 'c', 'x', 's'), "relieve", "sello",
            "hm"),
    /** Propio de KomaEdit: planos de la biblioteca al azar (paquete {@code blueprint}). */
    STAMP("stamp", "Estampar", com.komainu.moxia.blueprint.StampSettings.SWITCHES,
            com.komainu.moxia.blueprint.StampSettings.VALUED, "estampar", "planos", "st"),
    /** Propio de KomaEdit: el modelado de Axiom (esculpir, roca, soldar, fundir, rugosidad y grietas). */
    SCULPT("sculpt", "Modelado", Set.of('p', 'n'),
            Set.of('f', 'm', 'b', 's', 'g', 'k', 'z', 'w', 'e', 'd', 'i'), "modelar", "modelado", "sk"),
    /** Propio de KomaEdit: pintar el terreno con un degradado, arrastrando (paquete {@code brush.paint}). */
    PAINT("paint", "Pintar", com.komainu.moxia.brush.paint.PaintConfig.SWITCHES,
            com.komainu.moxia.brush.paint.PaintConfig.VALUED, "pintar", "pt");

    private final String id;
    private final String displayName;
    private final Set<Character> switches;
    private final Set<Character> valued;
    private final String[] aliases;

    EzBrushType(String id, String displayName, Set<Character> switches, Set<Character> valued, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.switches = switches;
        this.valued = valued;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Los pinceles de árboles y rocas leen sus ajustes como {@code /komaedit tree} y {@code /komaedit rock}. */
    public boolean nature() {
        return this == TREE || this == ROCK;
    }

    /** Los pinceles de colocación leen sus banderas como {@code place}, {@code scatter} y {@code array}. */
    public boolean placement() {
        return this == PLACE || this == SCATTER || this == ARRAY;
    }

    public BrushOptions options(String input) {
        return BrushOptions.parse(input, switches, valued);
    }

    public static EzBrushType byId(String input) {
        String name = input.toLowerCase(Locale.ROOT);
        for (EzBrushType type : values()) {
            if (type.id.equals(name)) {
                return type;
            }
            for (String alias : type.aliases) {
                if (alias.equals(name)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("Pincel desconocido: " + input
                + " (paletteshift, gradient, gradientstroke, petalpainter, place, scatter, array, tree, rock,"
                + " biome, heightmap, sculpt, stamp, paint)");
    }
}
