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
    PLACE("place", "Colocar", Set.of('a', 'x', 'p'), Set.of(), "colocar", "pl");

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
                + " (paletteshift, gradient, gradientstroke, petalpainter, place)");
    }
}
