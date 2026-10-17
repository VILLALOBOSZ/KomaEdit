package com.komainu.moxia.shape.tube;

import java.util.function.DoubleUnaryOperator;

/**
 * Curva de modificadores (escala, espaciado y torsión con su easing) aplicada <em>dentro</em> del espacio de
 * tubo. A diferencia de deformar bloques ya generados, aquí no se abren huecos que haya que puentear: cada
 * bloque se evalúa directamente en su posición final.
 */
public record TubeCurve(
        double startScale,
        double endScale,
        double startStretch,
        double endStretch,
        double twistDegrees,
        DoubleUnaryOperator easing
) {
    public static final TubeCurve NONE = new TubeCurve(1.0, 1.0, 1.0, 1.0, 0.0, DoubleUnaryOperator.identity());

    public TubeCurve {
        easing = easing == null ? DoubleUnaryOperator.identity() : easing;
    }

    double ease(double progress) {
        return easing.applyAsDouble(Math.clamp(progress, 0.0, 1.0));
    }

    double scale(double eased) {
        return startScale + (endScale - startScale) * eased;
    }

    double stretch(double eased) {
        return startStretch + (endStretch - startStretch) * eased;
    }
}
