package com.komainu.moxia.shape.smooth;

/**
 * Elige la pieza que mejor encaja con la ocupación de un bloque de superficie.
 *
 * <p>La ocupación llega como la fracción de cada octante que queda dentro de la figura. El error de una pieza
 * es la suma de diferencias entre su máscara y esa ocupación, en octantes (0 = encaje perfecto, 8 = nada en
 * común). Una pieza sustituye al bloque entero, o rellena un hueco vacío, cuando reduce el error en más que el
 * umbral de la cobertura.</p>
 */
public final class PieceFit {
    public static final double DEFAULT_COVERAGE = 0.5;
    private static final double EPSILON = 1.0E-9;
    /** Mucho menor que la resolución de la medida (1/5 de octante), así solo decide entre empates. */
    private static final double TIE_BREAK = 1.0E-3;
    private static final byte[] SLABS = {SmoothPiece.slab(false), SmoothPiece.slab(true)};
    /** Losas primero: ante un empate gana la pieza más sencilla. Las escaleras se eligen rectas. */
    private static final byte[] SLABS_AND_STAIRS = {
            SmoothPiece.slab(false), SmoothPiece.slab(true),
            SmoothPiece.stairs(SmoothPiece.NORTH, false, SmoothPiece.STRAIGHT),
            SmoothPiece.stairs(SmoothPiece.EAST, false, SmoothPiece.STRAIGHT),
            SmoothPiece.stairs(SmoothPiece.SOUTH, false, SmoothPiece.STRAIGHT),
            SmoothPiece.stairs(SmoothPiece.WEST, false, SmoothPiece.STRAIGHT),
            SmoothPiece.stairs(SmoothPiece.NORTH, true, SmoothPiece.STRAIGHT),
            SmoothPiece.stairs(SmoothPiece.EAST, true, SmoothPiece.STRAIGHT),
            SmoothPiece.stairs(SmoothPiece.SOUTH, true, SmoothPiece.STRAIGHT),
            SmoothPiece.stairs(SmoothPiece.WEST, true, SmoothPiece.STRAIGHT)
    };

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
        double originalError = error(occupancy, SmoothPiece.mask(original));
        byte best = original;
        double bestError = Double.POSITIVE_INFINITY;
        double bestRank = Double.POSITIVE_INFINITY;
        for (byte candidate : mode == SmoothMode.SLABS ? SLABS : SLABS_AND_STAIRS) {
            double candidateError = error(occupancy, SmoothPiece.mask(candidate));
            // En paredes casi verticales la escalera apoyada y la colgada empatan; gana la que apoya su mitad
            // maciza en la mitad más llena. El desempate solo ordena candidatas, no cuenta para el umbral.
            double rank = candidateError - TIE_BREAK * solidHalf(occupancy, candidate);
            if (rank < bestRank - EPSILON) {
                best = candidate;
                bestError = candidateError;
                bestRank = rank;
            }
        }
        if (originalError - bestError > threshold(coverage) + EPSILON) {
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
        double total = 0.0;
        int half = SmoothPiece.isTop(piece) ? 1 : 0;
        for (int octant = 0; octant < 8; octant++) {
            if ((octant >> 1 & 1) == half) {
                total += occupancy[octant];
            }
        }
        return total;
    }

    static double error(double[] occupancy, int mask) {
        double total = 0.0;
        for (int octant = 0; octant < 8; octant++) {
            double target = (mask & 1 << octant) != 0 ? 1.0 : 0.0;
            total += Math.abs(occupancy[octant] - target);
        }
        return total;
    }
}
