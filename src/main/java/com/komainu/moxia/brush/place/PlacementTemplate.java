package com.komainu.moxia.brush.place;

import com.sk89q.worldedit.world.block.BlockState;

/**
 * Bloques de una estructura en coordenadas locales: el origen de la colocación es (0, 0, 0), +y es su «arriba» y
 * +x su «delante». Es inmutable y se recorre desde el worker, así que no puede tocar el mundo ni WorldEdit.
 */
public interface PlacementTemplate {
    /** Número de bloques que entrega {@link #forEach}, para estimar el plan. */
    int size();

    void forEach(Visitor visitor);

    @FunctionalInterface
    interface Visitor {
        void accept(int x, int y, int z, BlockState state);
    }
}
