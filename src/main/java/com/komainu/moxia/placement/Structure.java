package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

/**
 * Lo que se coloca: un conjunto de bloques en su propio espacio local. El espacio local tiene +y arriba y +x
 * delante; el origen (0, 0, 0) es el punto que cae sobre la posición de colocación.
 *
 * <p>No sabe nada de Minecraft: devuelve índices de su paleta, que el servicio traduce a estados (girados según la
 * orientación de cada colocación) o al patrón de la forma.</p>
 */
public interface Structure {
    /** Fuera de la estructura: no se escribe nada. */
    int EMPTY = -1;
    /** Aire de la estructura: solo se escribe con {@code -a}. */
    int AIR = -2;

    /** Esquina mínima de la caja local. */
    Vec3 min();

    /** Esquina máxima de la caja local. */
    Vec3 max();

    /** Tamaño natural en bloques: el del portapapeles o 20×20×20 para las procedurales (o el de {@code -s}). */
    Vec3 naturalSize();

    /** Bloques del mundo por unidad local con el tamaño natural. */
    Vec3 blocksPerUnit();

    /** Evaluador para una colocación; {@code seed} cambia las formas con azar (pelo, icosfera, {@code seed}). */
    Sampler sampler(long seed);

    /** La forma se pinta con un único patrón de WorldEdit en vez de con una paleta de estados. */
    boolean usesPattern();

    /** Pegado suavizado (PasteMethod:SMOOTHED) con su sesgo de relleno; solo portapapeles y schematics. */
    default boolean smoothPaste() {
        return false;
    }

    default double fillBias() {
        return 1.0;
    }

    /** Giro de entrada alrededor de +y, en grados ({@code Rotation}). */
    default double inputRotation() {
        return 0.0;
    }

    String describe();

    @FunctionalInterface
    interface Sampler {
        /** Índice de paleta, {@link #AIR} o {@link #EMPTY} en el punto local (x, y, z). */
        int sample(double x, double y, double z);
    }
}
