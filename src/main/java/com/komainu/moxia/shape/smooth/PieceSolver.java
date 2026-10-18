package com.komainu.moxia.shape.smooth;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.concurrent.CancellationException;
import java.util.function.LongPredicate;

/**
 * Reúne las piezas de una superficie y, al terminar, las deja como quedarán en el mundo.
 *
 * <p>Cada bloque se elige primero solo, con {@link PieceFit}. En los perfiles 3D ({@link SmoothMode#solvesCorners})
 * se repasan después las escaleras con sus vecinos: Minecraft decide la esquina de cada escalera (interior,
 * exterior o recta) por la orientación de las de al lado, así que elegirlas una a una no basta. El repaso prueba
 * para cada pieza las demás del perfil y se queda con la que deja menos error entre ella y sus cuatro vecinas
 * horizontales, ya con las esquinas que pondría Minecraft. Los bloques que cambian vuelven a la cola de sus
 * vecinos, hasta {@value #MAX_PASSES} pasadas.</p>
 *
 * <p>Las capas de nieve necesitan apoyo, como en Minecraft (si no, se caen al primer aviso de un vecino): al
 * terminar, de abajo arriba, una capa sin bloque entero debajo (o una pieza con la cara de arriba entera) vuelve a
 * ser bloque entero si lo era, y si era una pieza añadida en un hueco se quita ({@link #dropped}).</p>
 *
 * <p>Uso: {@link #choose} en cada bloque de la superficie, {@link #put} con las piezas que se aceptan y
 * {@link #finish} una vez. No es seguro entre hilos.</p>
 */
public final class PieceSolver {
    static final int MAX_PASSES = 4;
    private static final double EPSILON = 1.0E-9;
    /** Octantes de arriba: una pieza que los llena todos tiene la cara superior entera. */
    private static final int TOP_FACE = 0xCC;
    private static final int[] HORIZONTAL = {SmoothPiece.SOUTH, SmoothPiece.WEST, SmoothPiece.NORTH, SmoothPiece.EAST};

    private final SmoothMode mode;
    private final double coverage;
    private final Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap();
    /** Ocupación de cada pieza en 8 bytes (0 a 255 por octante); solo en los perfiles 3D. */
    private final Long2LongOpenHashMap occupancies = new Long2LongOpenHashMap();
    private final LongOpenHashSet dropped = new LongOpenHashSet();
    private final double[] scratch = new double[8];
    private int checks;

    public PieceSolver(SmoothMode mode, double coverage) {
        this.mode = mode == null ? SmoothMode.NONE : mode;
        this.coverage = coverage;
    }

    public boolean active() {
        return mode != SmoothMode.NONE && coverage > 0.0;
    }

    public SmoothMode mode() {
        return mode;
    }

    /** {@link PieceFit#choose} con el perfil y la cobertura de este solver. */
    public byte choose(double[] occupancy, boolean existing) {
        return PieceFit.choose(occupancy, existing, mode, coverage);
    }

    /** Guarda la pieza de una posición ({@link PackedBlockPlan#pack}); {@code FULL} y {@code EMPTY} se ignoran. */
    public void put(long key, byte piece, double[] occupancy) {
        if (piece == SmoothPiece.FULL || piece == SmoothPiece.EMPTY) {
            return;
        }
        pieces.put(key, piece);
        if (mode.solvesCorners()) {
            occupancies.put(key, pack(occupancy));
        }
    }

    public boolean isEmpty() {
        return pieces.isEmpty();
    }

    /**
     * Repasa las esquinas (perfiles 3D) y pone a cada escalera la forma que calcularía Minecraft.
     *
     * @return las piezas por posición; el mapa es del que llama
     */
    public Long2ByteOpenHashMap finish() {
        return finish(null);
    }

    /**
     * @param solid si la figura deja un bloque entero en esa posición (sin contar las piezas); con {@code null} no
     *              se comprueba el apoyo de las capas
     */
    public Long2ByteOpenHashMap finish(LongPredicate solid) {
        if (mode.solvesCorners() && !pieces.isEmpty()) {
            refine();
        }
        if (solid != null && mode.uses(PieceVariant.LAYER)) {
            supportLayers(solid);
        }
        StairShapes.resolve(pieces);
        return pieces;
    }

    /** Piezas añadidas en huecos que {@link #finish} quitó por no tener apoyo; esas posiciones quedan vacías. */
    public LongOpenHashSet dropped() {
        return dropped;
    }

    private void supportLayers(LongPredicate solid) {
        LongArrayList layers = new LongArrayList();
        for (Long2ByteMap.Entry entry : pieces.long2ByteEntrySet()) {
            if (SmoothPiece.isLayers(entry.getByteValue())) {
                layers.add(entry.getLongKey());
            }
        }
        long[] keys = layers.toLongArray();
        // De abajo arriba: el apoyo de cada capa ya está decidido cuando le toca.
        LongArrays.quickSort(keys, (first, second) -> {
            int byY = Integer.compare(PackedBlockPlan.unpackY(first), PackedBlockPlan.unpackY(second));
            return byY != 0 ? byY : Long.compare(first, second);
        });
        for (long key : keys) {
            int y = PackedBlockPlan.unpackY(key);
            if (y <= -2048) {
                continue;
            }
            long below = PackedBlockPlan.pack(PackedBlockPlan.unpackX(key), y - 1, PackedBlockPlan.unpackZ(key));
            boolean supported;
            if (pieces.containsKey(below)) {
                supported = (SmoothPiece.mask(pieces.get(below)) & TOP_FACE) == TOP_FACE;
            } else {
                supported = !dropped.contains(below) && solid.test(below);
            }
            if (supported) {
                continue;
            }
            byte piece = pieces.remove(key);
            if (SmoothPiece.isAdded(piece)) {
                dropped.add(key);
            }
        }
    }

    private void refine() {
        byte[] candidates = mode.candidates();
        LongArrayList queue = new LongArrayList(pieces.keySet());
        for (int pass = 0; pass < MAX_PASSES && !queue.isEmpty(); pass++) {
            LongOpenHashSet next = new LongOpenHashSet();
            for (int index = 0; index < queue.size(); index++) {
                checkCancelled();
                long key = queue.getLong(index);
                byte current = pieces.get(key);
                if (!SmoothPiece.isStairs(current) && !touchesStairs(key)) {
                    continue;
                }
                double currentCost = localCost(key);
                if (currentCost <= EPSILON) {
                    continue;
                }
                boolean added = SmoothPiece.isAdded(current);
                byte best = current;
                double bestCost = currentCost;
                for (byte candidate : candidates) {
                    byte option = added ? SmoothPiece.added(candidate) : candidate;
                    if (option == current) {
                        continue;
                    }
                    pieces.put(key, option);
                    double cost = localCost(key);
                    if (cost < bestCost - EPSILON) {
                        best = option;
                        bestCost = cost;
                    }
                }
                pieces.put(key, best);
                if (best != current) {
                    int x = PackedBlockPlan.unpackX(key);
                    int y = PackedBlockPlan.unpackY(key);
                    int z = PackedBlockPlan.unpackZ(key);
                    for (int direction : HORIZONTAL) {
                        long neighbor = PackedBlockPlan.pack(x + SmoothPiece.offsetX(direction), y,
                                z + SmoothPiece.offsetZ(direction));
                        if (pieces.containsKey(neighbor)) {
                            next.add(neighbor);
                        }
                    }
                    next.add(key);
                }
            }
            queue = new LongArrayList(next);
        }
    }

    /** Error de la pieza y de sus vecinas horizontales, con la forma que Minecraft daría a cada escalera. */
    private double localCost(long key) {
        double total = cost(key);
        int x = PackedBlockPlan.unpackX(key);
        int y = PackedBlockPlan.unpackY(key);
        int z = PackedBlockPlan.unpackZ(key);
        for (int direction : HORIZONTAL) {
            long neighbor = PackedBlockPlan.pack(x + SmoothPiece.offsetX(direction), y,
                    z + SmoothPiece.offsetZ(direction));
            if (pieces.containsKey(neighbor) && SmoothPiece.isStairs(pieces.get(neighbor))) {
                total += cost(neighbor);
            }
        }
        return total;
    }

    private double cost(long key) {
        byte piece = pieces.get(key);
        if (SmoothPiece.isStairs(piece)) {
            piece = SmoothPiece.withShape(piece, StairShapes.shape(pieces, key, piece));
        }
        unpack(occupancies.get(key), scratch);
        return PieceFit.error(scratch, piece);
    }

    private boolean touchesStairs(long key) {
        int x = PackedBlockPlan.unpackX(key);
        int y = PackedBlockPlan.unpackY(key);
        int z = PackedBlockPlan.unpackZ(key);
        for (int direction : HORIZONTAL) {
            long neighbor = PackedBlockPlan.pack(x + SmoothPiece.offsetX(direction), y,
                    z + SmoothPiece.offsetZ(direction));
            if (pieces.containsKey(neighbor) && SmoothPiece.isStairs(pieces.get(neighbor))) {
                return true;
            }
        }
        return false;
    }

    static long pack(double[] occupancy) {
        long packed = 0L;
        for (int octant = 0; octant < 8; octant++) {
            long value = Math.round(Math.clamp(occupancy[octant], 0.0, 1.0) * 255.0);
            packed |= value << (octant * 8);
        }
        return packed;
    }

    static void unpack(long packed, double[] occupancy) {
        for (int octant = 0; octant < 8; octant++) {
            occupancy[octant] = (packed >>> (octant * 8) & 0xFF) / 255.0;
        }
    }

    private void checkCancelled() {
        if ((++checks & 1_023) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }
}
