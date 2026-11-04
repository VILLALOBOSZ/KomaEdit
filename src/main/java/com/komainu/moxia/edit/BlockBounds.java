package com.komainu.moxia.edit;

import com.sk89q.worldedit.math.BlockVector3;

public record BlockBounds(
        int minimumX,
        int minimumY,
        int minimumZ,
        int maximumX,
        int maximumY,
        int maximumZ
) {
    public BlockBounds {
        if (minimumX > maximumX || minimumY > maximumY || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Los límites de la operación no son válidos");
        }
    }

    /**
     * El bloque del medio de una caja (con un lado par, el de abajo), el mismo esté donde esté: {@code divide(2)} de
     * WorldEdit trunca hacia cero y en coordenadas negativas daba el de arriba.
     */
    public static BlockVector3 middle(BlockVector3 minimum, BlockVector3 maximum) {
        return BlockVector3.at(Math.floorDiv(minimum.x() + maximum.x(), 2),
                Math.floorDiv(minimum.y() + maximum.y(), 2), Math.floorDiv(minimum.z() + maximum.z(), 2));
    }
}
