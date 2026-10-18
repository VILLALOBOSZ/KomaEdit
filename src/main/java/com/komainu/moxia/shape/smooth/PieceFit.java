package com.komainu.moxia.shape.smooth;

/**
 * Elige la pieza que mejor encaja con la ocupación de un bloque de superficie.
 *
 * <p>La ocupación llega como la fracción de cada octante que queda dentro de la figura. El error de una pieza
 * es la suma de diferencias entre lo que llena la pieza en cada octante ({@link SmoothPiece#fill}) y esa
 * ocupación, en octantes (0 = encaje perfecto, 8 = nada en común). Las piezas que se prueban son las del perfil
 * ({@link SmoothMode}). Una pieza sustituye al bloque entero, o rellena un hueco vacío, cuando reduce el error en más que el
 * umbral de la cobertura.</p>
 */
public final class PieceFit {
    public static final double DEFAULT_COVERAGE = 0.5;
    private static final double EPSILON = 1.0E-9;
    /** Mucho menor que la resolución de la medida (1/5 de octante), así solo decide entre empates. */
    private static final double TIE_BREAK = 1.0E-3;
    /**
     * Con cobertura alta se aceptan piezas peores que el bloque original, pero solo en la superficie de verdad: si
     * el original falla por menos de medio octante (una esquina rozada, un bloque casi lleno) se queda como está.
     */
    private static final double MIN_SURFACE_ERROR = 0.5;

    private PieceFit() {
    }

    /**
     * @param occupancy fracción [0, 1] de cada octante dentro de la figura, indexada con
     *                  {@link SmoothPiece#octant(int, int, int)}
     * @param existing  si la figura ya colocaba un bloque entero en esta posición
     * @return la pieza elegida, {@link SmoothPiece#FULL} o {@link SmoothPiece#EMPTY}; las piezas en posiciones
     * vacías vuelven marcadas con {@link SmoothPiece#added(byte)}
     */
    public static byte choose(double[] occupancy, boolean existing, SmoothMode mode, double coverage) {
        if (occupancy.length != 8) {
            throw new IllegalArgumentException("La ocupación necesita 8 octantes");
        }
        byte original = existing ? SmoothPiece.FULL : SmoothPiece.EMPTY;
        if (mode == SmoothMode.NONE || !(coverage > 0.0)) {
            return original;
        }
        double originalError = error(occupancy, original);
        double threshold = threshold(coverage);
        if (threshold < 0.0 && originalError <= MIN_SURFACE_ERROR) {
            return original;
        }
        byte best = original;
        double bestError = Double.POSITIVE_INFINITY;
        double bestRank = Double.POSITIVE_INFINITY;
        for (byte candidate : mode.candidates()) {
            double candidateError = error(occupancy, candidate);
            // Los muros solo entran si encajan mejor que el original: con cobertura alta no se levantan postes
            // sueltos en cada borde, que es lo que hace la cobertura con losas y escaleras.
            if (SmoothPiece.isWall(candidate)
                    && !(originalError - candidateError > Math.max(threshold, 0.0) + EPSILON)) {
                continue;
            }
            // En paredes casi verticales la escalera apoyada y la colgada empatan; gana la que apoya su mitad
            // maciza en la mitad más llena. El desempate solo ordena candidatas, no cuenta para el umbral.
            double rank = candidateError - TIE_BREAK * solidHalf(occupancy, candidate);
            if (rank < bestRank - EPSILON) {
                best = candidate;
                bestError = candidateError;
                bestRank = rank;
            }
        }
        if (originalError - bestError > threshold + EPSILON) {
            return existing ? best : SmoothPiece.added(best);
        }
        return original;
    }

    /**
     * Ventaja mínima, en octantes, que necesita una pieza para usarse. Con cobertura 0.5 basta con encajar mejor
     * que el bloque entero (o el vacío); hacia 0 hace falta tanta ventaja que ninguna pieza la alcanza (una
     * escalera mejora como mucho 6 octantes) y hacia 1 se aceptan piezas hasta 2 octantes peores, así toda la
     * superficie que no esté entera queda cubierta con piezas.
     */
    public static double threshold(double coverage) {
        double clamped = Math.clamp(coverage, 0.0, 1.0);
        return clamped <= 0.5 ? 6.0 * (1.0 - 2.0 * clamped) : -4.0 * (clamped - 0.5);
    }

    private static double solidHalf(double[] occupancy, byte piece) {
        if (!SmoothPiece.isSlab(piece) && !SmoothPiece.isStairs(piece)) {
            return 0.0;
        }
        double total = 0.0;
        int half = SmoothPiece.isTop(piece) ? 1 : 0;
        for (int octant = 0; octant < 8; octant++) {
            if ((octant >> 1 & 1) == half) {
                total += occupancy[octant];
            }
        }
        return total;
    }

    /** Error de encaje de una pieza (o de {@link SmoothPiece#FULL}/{@link SmoothPiece#EMPTY}), en octantes. */
    public static double error(double[] occupancy, byte piece) {
        double total = 0.0;
        for (int octant = 0; octant < 8; octant++) {
            total += Math.abs(occupancy[octant] - SmoothPiece.fill(piece, octant));
        }
        return total;
    }
}
