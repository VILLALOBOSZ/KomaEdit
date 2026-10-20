package com.komainu.moxia.nature;

/**
 * Lo que los generadores de árboles y rocas necesitan saber del mundo: qué hay en cada celda. Se implementa sobre la
 * foto capturada (en el worker) y, en las pruebas, sobre un terreno de mentira.
 */
public interface NatureTerrain {
    /** Clase de una celda para decidir qué se puede poner encima o en su lugar. */
    enum Kind {
        /** Aire o una planta que se reemplaza al construir (hierba, flores, nieve en capa…). */
        FREE,
        /** Hojas: la madera las atraviesa, las hojas nuevas las respetan. */
        LEAVES,
        /** Agua o lava. */
        LIQUID,
        /** Suelo blando (tierra, hierba, arena, grava, barro, nieve…): raíces, la base del tronco y las rocas. */
        SOIL,
        /** Piedra natural: solo las rocas la pueden ocupar. */
        STONE,
        /** Cualquier otro bloque sólido (construcciones, troncos, minerales…): no se toca. */
        HARD;

        /** ¿Sirve para apoyar un árbol o una roca? */
        public boolean ground() {
            return this == SOIL || this == STONE || this == HARD;
        }
    }

    Kind kind(int x, int y, int z);

    /**
     * Altura del bloque de suelo más alto de la columna entre {@code top} y {@code bottom} (incluidos), o
     * {@link Integer#MIN_VALUE} si no hay ninguno.
     */
    default int surface(int x, int z, int top, int bottom) {
        for (int y = top; y >= bottom; y--) {
            if (kind(x, y, z).ground()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
