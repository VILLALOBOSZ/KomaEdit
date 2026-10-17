package com.komainu.moxia.recipe;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.tube.TubeCurve;

public record RecipeExtensions(
        int startScalePercent,
        int endScalePercent,
        int startSpacingPercent,
        int endSpacingPercent,
        int twistDegrees,
        CurveEasing easing,
        boolean rhythmEnabled,
        PathShape accentShape,
        int rhythmInterval,
        SurfaceMode surfaceMode,
        int conformRadius,
        int surfaceOffset,
        MaterialMetric materialMetric,
        boolean socketsEnabled,
        SocketPlacement socketPlacement,
        int socketInterval,
        boolean closeMicroGaps,
        boolean removeIsolated
) {
    public static final RecipeExtensions DEFAULT = new RecipeExtensions(
            100,
            100,
            100,
            100,
            0,
            CurveEasing.SMOOTH,
            false,
            PathShape.RINGS,
            4,
            SurfaceMode.NONE,
            12,
            0,
            MaterialMetric.AXIS,
            false,
            SocketPlacement.ENDPOINTS,
            12,
            false,
            false
    );

    public RecipeExtensions {
        if (startScalePercent < 25 || startScalePercent > 300
                || endScalePercent < 25 || endScalePercent > 300) {
            throw new IllegalArgumentException("La escala de curva debe estar entre 25% y 300%");
        }
        if (startSpacingPercent < 50 || startSpacingPercent > 200
                || endSpacingPercent < 50 || endSpacingPercent > 200) {
            throw new IllegalArgumentException("El espaciado de curva debe estar entre 50% y 200%");
        }
        if (twistDegrees < -1440 || twistDegrees > 1440) {
            throw new IllegalArgumentException("La torsión debe estar entre -1440 y 1440 grados");
        }
        easing = easing == null ? CurveEasing.SMOOTH : easing;
        accentShape = accentShape == null ? PathShape.RINGS : accentShape;
        if (rhythmInterval < 2 || rhythmInterval > 32) {
            throw new IllegalArgumentException("El intervalo de ritmo debe estar entre 2 y 32");
        }
        surfaceMode = surfaceMode == null ? SurfaceMode.NONE : surfaceMode;
        if (conformRadius < 2 || conformRadius > 32) {
            throw new IllegalArgumentException("El alcance de conformado debe estar entre 2 y 32");
        }
        if (surfaceOffset < -8 || surfaceOffset > 8) {
            throw new IllegalArgumentException("El offset de superficie debe estar entre -8 y 8");
        }
        materialMetric = materialMetric == null ? MaterialMetric.AXIS : materialMetric;
        socketPlacement = socketPlacement == null ? SocketPlacement.ENDPOINTS : socketPlacement;
        if (socketInterval < 2 || socketInterval > 64) {
            throw new IllegalArgumentException("El intervalo de sockets debe estar entre 2 y 64");
        }
    }

    public boolean usesCurve() {
        return startScalePercent != 100
                || endScalePercent != 100
                || startSpacingPercent != 100
                || endSpacingPercent != 100
                || twistDegrees != 0;
    }

    /** Curva de escala, espaciado y torsión expresada para el espacio de tubo (se aplica sin deformar bloques). */
    public TubeCurve tubeCurve() {
        CurveEasing curveEasing = easing;
        return new TubeCurve(
                startScalePercent / 100.0,
                endScalePercent / 100.0,
                startSpacingPercent / 100.0,
                endSpacingPercent / 100.0,
                twistDegrees,
                curveEasing::apply
        );
    }

    public boolean needsContinuousModifier() {
        return usesCurve() || rhythmEnabled;
    }

    public RecipeExtensions withCurve(
            int nextStartScale,
            int nextEndScale,
            int nextStartSpacing,
            int nextEndSpacing,
            int nextTwist,
            CurveEasing nextEasing
    ) {
        return new RecipeExtensions(nextStartScale, nextEndScale, nextStartSpacing, nextEndSpacing, nextTwist,
                nextEasing, rhythmEnabled, accentShape, rhythmInterval, surfaceMode, conformRadius, surfaceOffset,
                materialMetric, socketsEnabled, socketPlacement, socketInterval, closeMicroGaps, removeIsolated);
    }

    public RecipeExtensions withRhythm(boolean enabled, PathShape shape, int interval) {
        return new RecipeExtensions(startScalePercent, endScalePercent, startSpacingPercent, endSpacingPercent,
                twistDegrees, easing, enabled, shape, interval, surfaceMode, conformRadius, surfaceOffset,
                materialMetric, socketsEnabled, socketPlacement, socketInterval, closeMicroGaps, removeIsolated);
    }

    public RecipeExtensions withSurface(SurfaceMode mode, int radius, int offset) {
        return new RecipeExtensions(startScalePercent, endScalePercent, startSpacingPercent, endSpacingPercent,
                twistDegrees, easing, rhythmEnabled, accentShape, rhythmInterval, mode, radius, offset,
                materialMetric, socketsEnabled, socketPlacement, socketInterval, closeMicroGaps, removeIsolated);
    }

    public RecipeExtensions withMaterialMetric(MaterialMetric metric) {
        return new RecipeExtensions(startScalePercent, endScalePercent, startSpacingPercent, endSpacingPercent,
                twistDegrees, easing, rhythmEnabled, accentShape, rhythmInterval, surfaceMode, conformRadius,
                surfaceOffset, metric, socketsEnabled, socketPlacement, socketInterval, closeMicroGaps,
                removeIsolated);
    }

    public RecipeExtensions withSockets(boolean enabled, SocketPlacement placement, int interval) {
        return new RecipeExtensions(startScalePercent, endScalePercent, startSpacingPercent, endSpacingPercent,
                twistDegrees, easing, rhythmEnabled, accentShape, rhythmInterval, surfaceMode, conformRadius,
                surfaceOffset, materialMetric, enabled, placement, interval, closeMicroGaps, removeIsolated);
    }

    public RecipeExtensions withQa(boolean closeGaps, boolean removeSmallComponents) {
        return new RecipeExtensions(startScalePercent, endScalePercent, startSpacingPercent, endSpacingPercent,
                twistDegrees, easing, rhythmEnabled, accentShape, rhythmInterval, surfaceMode, conformRadius,
                surfaceOffset, materialMetric, socketsEnabled, socketPlacement, socketInterval, closeGaps,
                removeSmallComponents);
    }
}
