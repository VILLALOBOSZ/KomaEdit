package com.komainu.moxia.selection;

import java.util.Locale;

/**
 * Lo que se elige del lazo: hasta dónde entra desde la superficie que se ve y si cuenta lo que no es sólido.
 *
 * @param depth    solo la superficie, {@code layers} bloques o hasta el fondo
 * @param layers   bloques de profundidad con {@link Depth#LAYERS} (contando el de la superficie)
 * @param nonSolid si las plantas, el agua y lo demás que se atraviesa cuentan como bloque (si no, el rayo pasa)
 */
public record LassoSettings(Depth depth, int layers, boolean nonSolid) {
    public static final int MAX_LAYERS = 64;
    /** Lo más que baja «hasta el fondo»: la altura entera de un mundo normal y algo más. */
    public static final int MAX_THROUGH = 512;
    public static final LassoSettings DEFAULT = new LassoSettings(Depth.SURFACE, 3, false);

    public LassoSettings {
        if (depth == null) {
            depth = Depth.SURFACE;
        }
        if (layers < 1 || layers > MAX_LAYERS) {
            throw new IllegalArgumentException("La profundidad va de 1 a " + MAX_LAYERS + " bloques");
        }
    }

    /** Cuántos bloques entra el lazo desde la superficie (el de la superficie incluido). */
    public int reach() {
        return switch (depth) {
            case SURFACE -> 1;
            case LAYERS -> layers;
            case THROUGH -> MAX_THROUGH;
        };
    }

    public enum Depth {
        /** Solo el bloque que se ve. */
        SURFACE,
        /** Unos cuantos bloques hacia dentro desde la cara que se ve. */
        LAYERS,
        /** Hacia dentro hasta salir de la materia (un hueco, el aire del otro lado). */
        THROUGH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Depth byId(String id) {
            return switch (id.toLowerCase(Locale.ROOT)) {
                case "surface", "superficie" -> SURFACE;
                case "layers", "capas" -> LAYERS;
                case "through", "fondo" -> THROUGH;
                default -> throw new IllegalArgumentException("Profundidad desconocida: " + id);
            };
        }
    }
}
