package com.komainu.moxia.ruler;

import com.komainu.moxia.shape.Vec3;

/**
 * Un punto de la regla. Casi siempre es un bloque y se mide desde su centro, como se cuenta al construir: de un bloque
 * al de al lado hay 1. {@code exact} es un punto suelto (con decimales) y se mide tal cual.
 */
public record RulerPoint(int x, int y, int z, Vec3 exact) {
    public static RulerPoint block(int x, int y, int z) {
        return new RulerPoint(x, y, z, null);
    }

    /** Un punto con decimales; {@code x/y/z} quedan en el bloque que lo contiene. */
    public static RulerPoint exact(Vec3 point) {
        return new RulerPoint((int) Math.floor(point.x()), (int) Math.floor(point.y()), (int) Math.floor(point.z()),
                point);
    }

    public boolean isBlock() {
        return exact == null;
    }

    /** Desde dónde se mide: el centro del bloque o el punto exacto. */
    public Vec3 center() {
        return exact != null ? exact : new Vec3(x + 0.5, y + 0.5, z + 0.5);
    }

    /** {@code (x, y, z)} del bloque, o con un decimal si es un punto suelto. */
    public String coordinates() {
        if (exact == null) {
            return "(" + x + ", " + y + ", " + z + ")";
        }
        return "(" + RulerFormat.number(exact.x(), '.') + ", " + RulerFormat.number(exact.y(), '.') + ", "
                + RulerFormat.number(exact.z(), '.') + ")";
    }
}
