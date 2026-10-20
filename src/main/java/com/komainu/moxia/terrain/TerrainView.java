package com.komainu.moxia.terrain;

/**
 * Lo que ven del mundo las herramientas de terreno: una caja capturada, qué hay en cada posición y dónde se puede
 * escribir. En el juego la implementan la foto de la selección y la del pincel; en las pruebas, un mundo de texto.
 */
public interface TerrainView {
    int minimumX();

    int maximumX();

    int minimumY();

    int maximumY();

    int minimumZ();

    int maximumZ();

    /** Id con espacio de nombres ({@code minecraft:stone}); aire fuera de la captura. */
    String blockId(int x, int y, int z);

    TerrainCell cell(int x, int y, int z);

    /** Si se puede escribir en esa posición (dentro de la selección o de la zona del pincel). */
    boolean writable(int x, int y, int z);

    /** Máscara {@code -m}: se pregunta por el bloque de superficie de cada columna. Sin máscara, todo vale. */
    default boolean maskAllows(int x, int y, int z) {
        return true;
    }

    default boolean contains(int x, int y, int z) {
        return x >= minimumX() && x <= maximumX() && y >= minimumY() && y <= maximumY()
                && z >= minimumZ() && z <= maximumZ();
    }
}
