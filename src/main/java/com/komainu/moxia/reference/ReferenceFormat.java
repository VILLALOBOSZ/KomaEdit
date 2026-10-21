package com.komainu.moxia.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cómo se cuenta una referencia en el chat, la action bar y la lista: medidas en bloques con los decimales justos y
 * su postura en palabras (de pie, tumbada, inclinada) con hacia dónde se lee. Devuelve sufijos de clave de traducción;
 * el cliente pone el texto.
 */
public final class ReferenceFormat {
    private ReferenceFormat() {
    }

    /** Bloques con dos decimales como mucho y sin ceros de más: «32», «12.5», «0.06». */
    public static String blocks(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        String text = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString();
        return text.equals("-0") ? "0" : text;
    }

    /** {@code standing}, {@code flat} (boca arriba), {@code upside_down} (boca abajo) o {@code tilted}. */
    public static String pose(ReferenceRotation rotation) {
        double tilt = rotation.tilt();
        if (Math.abs(tilt) < 0.5) {
            return "standing";
        }
        if (tilt > 89.5) {
            return "flat";
        }
        if (tilt < -89.5) {
            return "upside_down";
        }
        return "tilted";
    }

    /**
     * Hacia dónde mira quien la lee de frente: {@code north}, {@code south}, {@code east} o {@code west} si va recta
     * (a menos de medio grado), o {@code null} si va en diagonal ({@link ReferenceRotation#readerYaw} da los grados).
     */
    public static String cardinal(ReferenceRotation rotation) {
        double yaw = rotation.readerYaw();
        long quarter = Math.round(yaw / 90.0);
        if (Math.abs(yaw - quarter * 90.0) >= 0.5) {
            return null;
        }
        return switch ((int) Math.floorMod(quarter, 4L)) {
            case 0 -> "south";
            case 1 -> "west";
            case 2 -> "north";
            default -> "east";
        };
    }
}
