package com.komainu.moxia.selection;

/**
 * Lo que la selección necesita saber del mundo: de qué es cada bloque. El servidor lo lee de su mundo (sin cargar
 * trozos) y las pruebas, de un terreno inventado.
 */
public interface Voxels {
    /** Un trozo sin cargar: el rayo se para ahí y no cuenta como bloque. */
    int UNLOADED = -1;
    int AIR = 0;
    /** Lo que se atraviesa andando: plantas, flores, antorchas, agua, lava, nieve fina… */
    int NON_SOLID = 1;
    int SOLID = 2;

    /** {@link #AIR}, {@link #NON_SOLID}, {@link #SOLID} o {@link #UNLOADED}. */
    int kind(int x, int y, int z);

    /** La altura más baja del mundo (incluida). */
    int minY();

    /** La altura más alta del mundo (incluida). */
    int maxY();

    /** Si un bloque de esa clase cuenta: lo sólido siempre y lo que no es sólido solo si se pide. */
    static boolean counts(int kind, boolean nonSolid) {
        return kind == SOLID || nonSolid && kind == NON_SOLID;
    }

    /** Si el bloque cuenta, mirando el mundo. */
    default boolean counts(int x, int y, int z, boolean nonSolid) {
        return y >= minY() && y <= maxY() && counts(kind(x, y, z), nonSolid);
    }
}
