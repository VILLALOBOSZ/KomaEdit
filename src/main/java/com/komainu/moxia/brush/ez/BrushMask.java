package com.komainu.moxia.brush.ez;

import com.komainu.moxia.sculpt.WorldSnapshot;

/**
 * Máscara de un pincel ({@code -m}): la lista sencilla de siempre ({@link BlockFilter}) o una máscara de WorldEdit
 * que lee la foto de la pasada ({@link BrushMasks}).
 */
public interface BrushMask {
    boolean matches(BrushWorld world, int x, int y, int z);

    /** Conecta la foto de la pasada antes de evaluar; las listas no la necesitan. */
    default void bind(WorldSnapshot snapshot) {
    }
}
