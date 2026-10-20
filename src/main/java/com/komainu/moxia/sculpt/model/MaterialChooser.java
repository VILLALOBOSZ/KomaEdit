package com.komainu.moxia.sculpt.model;

/**
 * Qué material pone un pincel de modelado en una celda: el de alrededor ({@code inherited}) o el de una paleta.
 * Devolver {@link ModelGrid#FREE} deja la celda libre (aire, o el relleno de aire de las grietas).
 */
@FunctionalInterface
public interface MaterialChooser {
    /** El material de alrededor tal cual. */
    MaterialChooser INHERIT = (x, y, z, inherited, covered) -> inherited;
    /** Aire. */
    MaterialChooser AIR = (x, y, z, inherited, covered) -> ModelGrid.FREE;

    /**
     * @param inherited el material más repetido entre los vecinos con masa de antes del pincel (o el del más
     *                  cercano si no tiene ninguno); en las grietas, el que había en la celda
     * @param covered   si la celda queda tapada por arriba: la hierba enterrada pasa a tierra, por ejemplo
     */
    int choose(int x, int y, int z, int inherited, boolean covered);
}
