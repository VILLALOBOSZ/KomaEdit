package com.komainu.moxia.deform;

/**
 * Resultado de una operación de deformación, superficie o suavizado antes de escribirlo: qué material queda en
 * cada bloque y, para las losas y escaleras de {@code -w}, qué parte de cada bloque queda ocupada.
 */
public interface DeformField {
    /** No tocar el bloque. */
    int KEEP = -1;
    /** Dejar aire (o el líquido que hubiera). */
    int AIR = 0;

    /** Id crudo del material que queda en el bloque, {@link #AIR} o {@link #KEEP}. */
    int material(int x, int y, int z);

    /** Si el punto continuo queda dentro de la forma; el centro del bloque {@code x} es {@code x + 0.5}. */
    boolean solid(double x, double y, double z);
}
