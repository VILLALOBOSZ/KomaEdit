package com.komainu.moxia.shape;

public final class GeometryLimitException extends RuntimeException {
    public GeometryLimitException(int limit) {
        super("La forma supera el límite de seguridad de " + limit + " bloques; reduce el radio o la trayectoria");
    }
}
