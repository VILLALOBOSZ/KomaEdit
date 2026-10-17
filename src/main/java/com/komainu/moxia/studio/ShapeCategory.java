package com.komainu.moxia.studio;

import com.komainu.moxia.shape.PathShape;

/**
 * Familias de figuras del buscador del Studio. Solo ordenan la lista: no cambian ninguna figura.
 * Una figura nueva sin familia asignada cae en {@link #ORGANIC}, así que nunca desaparece de la lista.
 */
public enum ShapeCategory {
    ALL("gui.komaedit.category.all"),
    FAVORITES("gui.komaedit.category.favorites"),
    TUBE("gui.komaedit.category.tube"),
    LINKS("gui.komaedit.category.links"),
    SKINS("gui.komaedit.category.skins"),
    ORGANIC("gui.komaedit.category.organic");

    private final String translationKey;

    ShapeCategory(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    /** La familia de una figura; {@link #ALL} y {@link #FAVORITES} no son familias. */
    public static ShapeCategory of(PathShape shape) {
        return switch (shape) {
            case SWEEP, BRAID, NOISE_TUBE, EXPRESSION -> TUBE;
            case CHAINLINK, BEADS, RINGS, KNOTS, COIL, NOODLES -> LINKS;
            case SCALES, SPIKED_SCALES, TILES, HEX_ARMOR, VORONOI, HONEYCOMB, FISHNET -> SKINS;
            default -> ORGANIC;
        };
    }

    /** Si la figura entra en esta categoría; las favoritas se consultan en las preferencias. */
    public boolean contains(PathShape shape, StudioPreferences preferences) {
        return switch (this) {
            case ALL -> true;
            case FAVORITES -> preferences.isFavorite(shape.id());
            default -> of(shape) == this;
        };
    }
}
