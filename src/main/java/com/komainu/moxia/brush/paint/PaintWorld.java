package com.komainu.moxia.brush.paint;

/**
 * Lo que ve del mundo el pincel de pintura: qué clase de bloque hay en cada sitio, qué tono de la paleta es y si la
 * máscara deja tocarlo. En el juego lo da la foto del clic ({@link SnapshotPaintWorld}); en las pruebas, un mundo
 * falso. Fuera de lo leído todo es {@link Kind#BLOCKED}.
 */
public interface PaintWorld {
    /** Clase de bloque para el pincel. */
    enum Kind {
        /** Aire, líquido o algo que deja ver lo de detrás (plantas, hojas, troncos, nieve en capa…). */
        OPEN,
        /** Cubo entero que se puede pintar. */
        GROUND,
        /** Escalera o losa: se pinta con su forma. */
        SHAPED,
        /** Sólido que no se toca, o fuera de lo leído. */
        BLOCKED;

        /** Tapa lo de detrás: lo que no es {@link #OPEN}. */
        public boolean covers() {
            return this != OPEN;
        }
    }

    Kind kind(int x, int y, int z);

    /** Entrada de la paleta del bloque que hay (contando su escalera y su losa), o -1 si no es de la paleta. */
    int paletteIndex(int x, int y, int z);

    /** Si {@code -b}, {@code -m} o, sin ellas, el terreno natural, dejan pintar este bloque. */
    default boolean allowed(int x, int y, int z) {
        return true;
    }

    /** Y más baja que se puede mirar. */
    int minimumY();

    /** Y más alta que se puede mirar. */
    int maximumY();
}
