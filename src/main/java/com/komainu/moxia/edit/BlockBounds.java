package com.komainu.moxia.edit;

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
}
