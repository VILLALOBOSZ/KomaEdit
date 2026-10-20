package com.komainu.moxia.roof;

import com.komainu.moxia.shape.smooth.PieceSolver;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.StairShapes;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * De octantes a piezas de Minecraft. Cada bloque del tejado llega como una máscara de 8 octantes (los que llena la
 * superficie); aquí se convierte en bloque entero, losa o escalera. Las máscaras que no son ninguna pieza toman la
 * más parecida (ante un empate, la que tapa más, para no abrir agujeros).
 *
 * <p>La forma de las escaleras (recta, esquina interior o exterior) no se elige: Minecraft la calcula con la
 * orientación de las escaleras vecinas. Por eso las esquinas se resuelven con {@link PieceSolver} del perfil
 * {@code SlabsAndStairs}, que prueba orientaciones y se queda con la que, con las formas que pondría Minecraft,
 * reproduce mejor los octantes; y {@link com.komainu.moxia.shape.smooth.StairShapes} deja cada escalera con la forma
 * definitiva.</p>
 */
final class RoofPieces {
    private static final byte[] BEST = new byte[256];
    private static final boolean[] EXACT = new boolean[256];

    static {
        List<Byte> candidates = new ArrayList<>();
        candidates.add(SmoothPiece.FULL);
        candidates.add(SmoothPiece.slab(false));
        candidates.add(SmoothPiece.slab(true));
        for (int shape = SmoothPiece.STRAIGHT; shape <= SmoothPiece.OUTER_RIGHT; shape++) {
            for (boolean top : new boolean[]{false, true}) {
                for (int facing = 0; facing < 4; facing++) {
                    candidates.add(SmoothPiece.stairs(facing, top, shape));
                }
            }
        }
        for (int mask = 0; mask < 256; mask++) {
            byte best = SmoothPiece.EMPTY;
            int bestError = Integer.bitCount(mask);
            int bestFill = 0;
            for (byte candidate : candidates) {
                int candidateMask = SmoothPiece.mask(candidate);
                int error = Integer.bitCount(candidateMask ^ mask);
                int fill = Integer.bitCount(candidateMask);
                if (error < bestError || error == bestError && fill > bestFill) {
                    best = candidate;
                    bestError = error;
                    bestFill = fill;
                }
            }
            BEST[mask] = best;
            EXACT[mask] = bestError == 0;
        }
    }

    private RoofPieces() {
    }

    /** Pieza para una máscara de octantes; {@link SmoothPiece#EMPTY} si es mejor dejar el bloque vacío. */
    static byte piece(int mask) {
        return BEST[mask & 0xFF];
    }

    static boolean exact(int mask) {
        return EXACT[mask & 0xFF];
    }

    static double[] occupancy(int mask) {
        double[] occupancy = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            occupancy[octant] = (mask & 1 << octant) != 0 ? 1.0 : 0.0;
        }
        return occupancy;
    }

    /**
     * @param masks máscara deseada de cada posición (sin las vacías)
     * @return la pieza final de cada posición y cuántas no reproducen su máscara
     */
    static Solved solve(Long2IntOpenHashMap masks) {
        PieceSolver solver = new PieceSolver(SmoothMode.STAIRS, 0.5);
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap(masks.size());
        for (Long2IntMap.Entry entry : masks.long2IntEntrySet()) {
            byte piece = piece(entry.getIntValue());
            if (piece == SmoothPiece.EMPTY) {
                continue;
            }
            if (piece == SmoothPiece.FULL) {
                pieces.put(entry.getLongKey(), piece);
            } else {
                solver.put(entry.getLongKey(), piece, occupancy(entry.getIntValue()));
            }
        }
        pieces.putAll(solver.finish());
        int mismatches = 0;
        for (Long2IntMap.Entry entry : masks.long2IntEntrySet()) {
            byte piece = pieces.containsKey(entry.getLongKey()) ? pieces.get(entry.getLongKey()) : SmoothPiece.EMPTY;
            if (SmoothPiece.mask(piece) != (entry.getIntValue() & 0xFF)) {
                mismatches++;
            }
        }
        return new Solved(pieces, mismatches);
    }

    /** Piezas ya elegidas (anillos de las figuras redondas): solo se ponen las esquinas que pondría Minecraft. */
    static Solved resolve(Long2ByteOpenHashMap chosen) {
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap(chosen);
        StairShapes.resolve(pieces);
        return new Solved(pieces, 0);
    }

    record Solved(Long2ByteOpenHashMap pieces, int mismatches) {
    }
}
