package com.komainu.moxia.nature;

import java.util.List;

/** Tipos de roca de {@code /komaedit rock}, con sus proporciones por defecto. */
public enum RockType {
    /** Roca redondeada y facetada. */
    BOULDER("boulder", "Roca", 4.0, 0.75, 1.3, 9, 0.45, 0.4, 0.25, "roca", "piedra", "pedrusco", "canto"),
    /** Peñasco ancho con estratos y grietas. */
    CRAG("crag", "Peñasco", 7.0, 0.78, 1.45, 12, 0.5, 0.6, 0.2, "penasco", "risco", "pena", "acantilado"),
    /** Aguja alta que se estrecha hacia arriba. */
    SPIRE("spire", "Aguja", 3.0, 3.2, 1.15, 8, 0.4, 0.5, 0.15, "aguja", "pinaculo", "pinnacle", "monolito",
            "menhir");

    private final String id;
    private final String displayName;
    private final double size;
    private final double aspect;
    private final double elongation;
    private final int facets;
    private final double roughness;
    private final double cracks;
    private final double sink;
    private final List<String> aliases;

    RockType(String id, String displayName, double size, double aspect, double elongation, int facets,
             double roughness, double cracks, double sink, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.size = size;
        this.aspect = aspect;
        this.elongation = elongation;
        this.facets = facets;
        this.roughness = roughness;
        this.cracks = cracks;
        this.sink = sink;
        this.aliases = List.of(aliases);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Radio por defecto en bloques. */
    public double size() {
        return size;
    }

    /** Alto respecto al ancho. */
    public double aspect() {
        return aspect;
    }

    /** Cuánto más larga que ancha es la planta. */
    public double elongation() {
        return elongation;
    }

    public int facets() {
        return facets;
    }

    public double roughness() {
        return roughness;
    }

    public double cracks() {
        return cracks;
    }

    /** Parte del alto que queda enterrada. */
    public double sink() {
        return sink;
    }

    public List<String> aliases() {
        return aliases;
    }

    /** El tipo con ese id o alias, o {@code null}. */
    public static RockType find(String input) {
        String normalized = TreeSpecies.normalize(input);
        for (RockType type : values()) {
            if (TreeSpecies.normalize(type.id).equals(normalized)
                    || TreeSpecies.normalize(type.displayName).equals(normalized)) {
                return type;
            }
            for (String alias : type.aliases) {
                if (TreeSpecies.normalize(alias).equals(normalized)) {
                    return type;
                }
            }
        }
        return null;
    }

    public static RockType byName(String input) {
        RockType type = find(input);
        if (type == null) {
            throw new IllegalArgumentException("Tipo de roca desconocido: " + input + " (boulder, crag, spire)");
        }
        return type;
    }
}
