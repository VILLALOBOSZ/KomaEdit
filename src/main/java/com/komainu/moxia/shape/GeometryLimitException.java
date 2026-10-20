package com.komainu.moxia.shape;

import com.komainu.moxia.progress.ProgressLine;
public final class GeometryLimitException extends RuntimeException {
    public GeometryLimitException(int limit) {
        super("La forma supera el límite de seguridad de " + ProgressLine.group(limit) + " bloques; reduce el radio o la trayectoria");
    }
}
