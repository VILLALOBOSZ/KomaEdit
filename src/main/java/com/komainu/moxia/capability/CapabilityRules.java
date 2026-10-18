package com.komainu.moxia.capability;

import java.util.Optional;

/**
 * Reglas del servidor para las capacidades, sin Minecraft: quién puede usarlas, hasta dónde llega y cuántas
 * acciones admite por tick.
 */
public final class CapabilityRules {
    /** Ticks mínimos entre dos acciones de varios bloques del mismo jugador. */
    public static final int MULTI_BLOCK_GAP_TICKS = 4;

    private CapabilityRules() {
    }

    /**
     * Motivo para no dejar usar la capacidad, o vacío si se puede.
     *
     * @param creative   está en creativo
     * @param anyMode    tiene el permiso para usarlas fuera de creativo
     * @param permitted  tiene el permiso de esa capacidad
     */
    public static Optional<String> denial(BuildCapability capability, boolean creative, boolean anyMode,
                                          boolean permitted) {
        if (!permitted) {
            return Optional.of("Te falta el permiso " + capability.permission());
        }
        if (!creative && !anyMode) {
            return Optional.of(capability.displayName() + " solo funciona en creativo");
        }
        return Optional.empty();
    }

    /** Alcance de «Alcance largo»: el elegido, sin pasar del límite del servidor. */
    public static int reach(int requested, CapabilityLimits limits) {
        return Math.clamp(requested, 5, limits.maxReach());
    }

    /**
     * Lo que hay que sumar al alcance base del jugador para llegar al de «Alcance largo». Nunca resta: si el
     * alcance base ya es mayor, se queda como está.
     */
    public static double reachBonus(int requested, CapabilityLimits limits, double baseRange) {
        return Math.max(0.0, reach(requested, limits) - baseRange);
    }

    /** Distancia de «Colocar en el aire»: la elegida, sin pasar del alcance del jugador ni del límite. */
    public static int angelDistance(int requested, double range, CapabilityLimits limits) {
        int ceiling = (int) Math.floor(Math.min(range, limits.maxReach()));
        return Math.clamp(requested, 1, Math.max(1, ceiling));
    }

    /**
     * ¿Llega a la posición? Como Minecraft: la distancia de los ojos al punto más cercano del bloque, con un bloque
     * de margen.
     */
    public static boolean withinReach(double eyeX, double eyeY, double eyeZ, Cell cell, double range) {
        double dx = distanceToSpan(eyeX, cell.x());
        double dy = distanceToSpan(eyeY, cell.y());
        double dz = distanceToSpan(eyeZ, cell.z());
        double limit = range + 1.0;
        return dx * dx + dy * dy + dz * dz < limit * limit;
    }

    /** Velocidad de vuelo (en décimas de la normal) dentro del límite del servidor. */
    public static int flySpeed(int requested, CapabilityLimits limits) {
        return Math.clamp(requested, 1, limits.maxFlySpeed());
    }

    private static double distanceToSpan(double coordinate, int block) {
        if (coordinate < block) {
            return block - coordinate;
        }
        if (coordinate > block + 1.0) {
            return coordinate - (block + 1.0);
        }
        return 0.0;
    }

    /**
     * Freno por jugador: como mucho {@code perTick} acciones de un bloque por tick y una de varios bloques cada
     * {@link #MULTI_BLOCK_GAP_TICKS}. Con la colocación rápida el cliente manda una por tick, así que solo frena a
     * quien manda de más.
     */
    public static final class Throttle {
        private long tick = Long.MIN_VALUE;
        private int count;
        private long lastMulti = Long.MIN_VALUE / 2;

        public boolean allow(long now, boolean multiBlock, int perTick) {
            if (multiBlock) {
                if (now - lastMulti < MULTI_BLOCK_GAP_TICKS) {
                    return false;
                }
                lastMulti = now;
                return true;
            }
            if (now != tick) {
                tick = now;
                count = 0;
            }
            if (count >= perTick) {
                return false;
            }
            count++;
            return true;
        }
    }
}
