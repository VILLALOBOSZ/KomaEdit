package com.komainu.moxia.terrain.stamp;

/**
 * Lo que se sabe de un sello de serie sin cargarlo: para listas, galerías y otras herramientas.
 *
 * @param neutral     gris que no cambia nada (0 levanta, 1 hunde, 0,5 sube y baja)
 * @param heightRatio altura natural dividida por el radio
 * @param palette     paleta de superficie de su categoría
 */
public record StampInfo(String name, StampCategory category, String description, double neutral, double heightRatio,
                        String palette) {
    public static StampInfo of(StampRecipe recipe) {
        return new StampInfo(recipe.name(), recipe.category(), recipe.description(), recipe.neutral(),
                recipe.heightRatio(), recipe.category().palette());
    }

    /** Altura natural para un radio, en bloques enteros (de 1 a {@code maximum}). */
    public int naturalHeight(double radius, int maximum) {
        return (int) Math.clamp(Math.round(radius * heightRatio), 1L, maximum);
    }
}
