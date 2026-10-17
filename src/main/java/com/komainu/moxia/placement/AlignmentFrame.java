package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Convierte la primaria y la secundaria en la orientación final: la primaria es el +y de la estructura y el +x es,
 * de las direcciones perpendiculares a la primaria, la más parecida a la secundaria. Así la estructura nunca se
 * deforma aunque las dos direcciones no sean perpendiculares.
 */
public final class AlignmentFrame {
    private static final double COLLINEAR = 1.0E-6;

    private AlignmentFrame() {
    }

    /**
     * @param perturb {@code -x}: si las direcciones son colineales, empuja un poco la secundaria en vez de fallar
     * @return la matriz cuyas columnas son el +x, +y y +z de la estructura en el mundo
     */
    public static Mat3 frame(Vec3 primary, Vec3 secondary, Snap snap, boolean perturb) {
        Vec3 up = snap.apply(normalize(primary, "La dirección primaria es nula"));
        Vec3 hint = secondary.length() < 1.0E-9 ? null : snap.apply(secondary.normalized());
        Vec3 side = hint == null ? null : hint.subtract(up.multiply(hint.dot(up)));
        if (side == null || side.length() < COLLINEAR) {
            if (!perturb) {
                throw new IllegalArgumentException("La primaria y la secundaria son la misma recta; cambia una o usa -x");
            }
            // Cualquier perpendicular estable: la del eje del mundo menos parecido a la primaria.
            Vec3 axis = Math.abs(up.x()) < 0.9 ? Alignment.EAST : new Vec3(0, 0, 1);
            Vec3 nudge = hint == null ? axis : hint.add(axis.multiply(1.0E-3));
            side = nudge.subtract(up.multiply(nudge.dot(up)));
            if (side.length() < COLLINEAR) {
                side = axis.subtract(up.multiply(axis.dot(up)));
            }
        }
        Vec3 x = side.normalized();
        Vec3 z = x.cross(up).normalized();
        return Mat3.columns(x, up, z);
    }

    private static Vec3 normalize(Vec3 vector, String message) {
        if (vector.length() < 1.0E-9) {
            throw new IllegalArgumentException(message);
        }
        return vector.normalized();
    }

    /** {@code -j}: solo deja usar ciertas direcciones. */
    public enum Snap {
        NONE(0, 0),
        MULTIPLES_90(0, 1),
        MULTIPLES_45(0, 1),
        MULTIPLES_22_5(22.5, 0),
        MULTIPLES_15(15.0, 0),
        DIAGONALS_1_1(0, 1),
        DIAGONALS_2_1(0, 2),
        DIAGONALS_3_1(0, 3),
        DIAGONALS_4_1(0, 4),
        DIAGONALS_5_1(0, 5);

        private final double step;
        private final int ratio;
        private volatile Vec3[] candidates;

        Snap(double step, int ratio) {
            this.step = step;
            this.ratio = ratio;
        }

        public static Snap byId(String input) {
            String normalized = ParamText.normalize(input).replace(".", "");
            for (Snap snap : values()) {
                if (ParamText.normalize(snap.name()).equals(normalized)) {
                    return snap;
                }
            }
            return switch (normalized) {
                case "90" -> MULTIPLES_90;
                case "45" -> MULTIPLES_45;
                case "225" -> MULTIPLES_22_5;
                case "15" -> MULTIPLES_15;
                case "none", "no", "ninguno" -> NONE;
                default -> throw new IllegalArgumentException("Ajuste desconocido: " + input + " (MULTIPLES_90, "
                        + "MULTIPLES_45, MULTIPLES_22_5, MULTIPLES_15, DIAGONALS_1_1 … DIAGONALS_5_1)");
            };
        }

        public Vec3 apply(Vec3 direction) {
            if (this == NONE) {
                return direction;
            }
            if (step > 0) {
                double yaw = Math.atan2(direction.z(), direction.x());
                double pitch = Math.asin(Math.clamp(direction.y(), -1.0, 1.0));
                double radians = Math.toRadians(step);
                yaw = Math.rint(yaw / radians) * radians;
                pitch = Math.rint(pitch / radians) * radians;
                double horizontal = Math.cos(pitch);
                return new Vec3(clean(horizontal * Math.cos(yaw)), clean(Math.sin(pitch)),
                        clean(horizontal * Math.sin(yaw)));
            }
            Vec3 best = direction;
            double bestDot = Double.NEGATIVE_INFINITY;
            for (Vec3 candidate : candidates()) {
                double dot = candidate.dot(direction);
                if (dot > bestDot + 1.0E-12) {
                    bestDot = dot;
                    best = candidate;
                }
            }
            return best;
        }

        private static double clean(double value) {
            return Math.abs(value) < 1.0E-12 ? 0.0 : value;
        }

        private Vec3[] candidates() {
            Vec3[] cached = candidates;
            if (cached != null) {
                return cached;
            }
            Set<Vec3> unique = new LinkedHashSet<>();
            int limit = this == MULTIPLES_90 ? 1 : ratio;
            for (int x = -limit; x <= limit; x++) {
                for (int y = -limit; y <= limit; y++) {
                    for (int z = -limit; z <= limit; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        int nonZero = (x != 0 ? 1 : 0) + (y != 0 ? 1 : 0) + (z != 0 ? 1 : 0);
                        if (this == MULTIPLES_90 && nonZero != 1) {
                            continue;
                        }
                        if (gcd(gcd(Math.abs(x), Math.abs(y)), Math.abs(z)) != 1) {
                            continue;
                        }
                        unique.add(new Vec3(x, y, z).normalized());
                    }
                }
            }
            List<Vec3> list = new ArrayList<>(unique);
            cached = list.toArray(Vec3[]::new);
            candidates = cached;
            return cached;
        }

        private static int gcd(int first, int second) {
            return second == 0 ? first : gcd(second, first % second);
        }
    }
}
