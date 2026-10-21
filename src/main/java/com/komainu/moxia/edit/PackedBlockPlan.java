package com.komainu.moxia.edit;

import com.komainu.moxia.shape.smooth.PieceMaterials;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.SmoothPieces;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Plan compacto de escrituras: posición, bloque fijo o patrón, estado esperado (escritura condicional), índice de
 * captura y pieza de cada entrada, en el orden en que se añadieron.
 *
 * <p>Los planes pequeños guardan arrays. Por encima de {@link Storage#compressAbove()} entradas el plan se comprime
 * por páginas con paleta, como las secciones de un chunk ({@link PlanPages}), y si aun así pasa de
 * {@link Storage#spillAbove()} bytes, el resto de páginas va a un archivo temporal ({@link PlanSpill}). La API es la
 * misma en los tres casos y el plan es inmutable.</p>
 */
public final class PackedBlockPlan implements Iterable<BlockVector3> {
    public static final int NO_EXPECTED_STATE = Integer.MIN_VALUE;
    public static final int NO_CAPTURE = -1;
    private static final long X_MASK = 0x3FFFFFFL;
    private static final long Y_MASK = 0xFFFL;
    private static final long Z_MASK = 0x3FFFFFFL;
    private static volatile Storage defaults = Storage.DEFAULT;

    private final Store store;
    private final int captureSize;
    private final BlockBounds bounds;
    /** Materiales fijos de las piezas ({@code -w Slabs(Slab:acacia)}); sin ellos salen del bloque del patrón. */
    private final PieceMaterials pieceMaterials;

    private PackedBlockPlan(Store store, int captureSize, BlockBounds bounds, PieceMaterials pieceMaterials) {
        this.pieceMaterials = pieceMaterials == null ? PieceMaterials.NONE : pieceMaterials;
        this.store = store;
        this.captureSize = captureSize;
        this.bounds = bounds;
    }

    /**
     * Dónde se guardan los planes grandes.
     *
     * @param compressAbove a partir de cuántas entradas se comprime (0: siempre; negativo: nunca)
     * @param spillAbove    bytes comprimidos que puede ocupar un plan en memoria antes de volcar el resto a disco
     *                      (negativo: nunca)
     * @param spillDirectory carpeta de los archivos temporales
     */
    public record Storage(int compressAbove, long spillAbove, Path spillDirectory) {
        public static final int DEFAULT_COMPRESS_ABOVE = 131_072;
        public static final long DEFAULT_SPILL_ABOVE = 128L * 1024 * 1024;
        public static final Storage DEFAULT = new Storage(DEFAULT_COMPRESS_ABOVE, DEFAULT_SPILL_ABOVE,
                temporaryDirectory());
        /** Siempre arrays, como antes de la compresión. */
        public static final Storage FLAT = new Storage(-1, -1L, temporaryDirectory());

        public Storage {
            spillDirectory = spillDirectory == null ? temporaryDirectory() : spillDirectory;
        }

        /** {@code <temporal del sistema>/komaedit-plans}. */
        public static Path temporaryDirectory() {
            return Path.of(System.getProperty("java.io.tmpdir"), "komaedit-plans");
        }
    }

    /** Lo que usan los builders sin {@link Storage} explícito; lo fija la configuración del servidor. */
    public static void configure(Storage storage) {
        defaults = storage == null ? Storage.DEFAULT : storage;
    }

    public static Storage storage() {
        return defaults;
    }

    /** Borra los archivos temporales de planes que dejó un servidor parado de golpe. */
    public static int sweepSpills() {
        return PlanSpill.sweep(defaults.spillDirectory());
    }

    public static PackedBlockPlan pattern(Collection<BlockVector3> blocks) {
        Builder builder = builder(blocks.size());
        for (BlockVector3 block : blocks) {
            builder.addPattern(block.x(), block.y(), block.z());
        }
        return builder.build();
    }

    /** Plan de patrón con la pieza de cada posición (las ausentes en {@code pieces} son bloques enteros). */
    public static PackedBlockPlan pattern(Collection<BlockVector3> blocks, SmoothPieces pieces) {
        if (pieces.isEmpty()) {
            return pattern(blocks);
        }
        Builder builder = builder(blocks.size());
        for (BlockVector3 block : blocks) {
            builder.addPattern(block.x(), block.y(), block.z());
            builder.setPiece(builder.size() - 1, pieces.at(block.x(), block.y(), block.z()));
        }
        return builder.build();
    }

    public static Builder builder(int expectedSize) {
        return new Builder(expectedSize, defaults);
    }

    public static Builder builder(int expectedSize, Storage storage) {
        return new Builder(expectedSize, storage == null ? defaults : storage);
    }

    public int size() {
        return store.size();
    }

    public BlockVector3 position(int index) {
        long packed = store.position(index);
        return BlockVector3.at(unpackX(packed), unpackY(packed), unpackZ(packed));
    }

    public BlockState override(int index) {
        return store.override(index);
    }

    public boolean usesOnlyPattern() {
        return !store.hasOverrides();
    }

    public long packedPosition(int index) {
        return store.position(index);
    }

    public int expectedStateId(int index) {
        return store.expected(index);
    }

    /** Pieza de la posición: {@link SmoothPiece#FULL} salvo que el plan sustituya el bloque por una pieza. */
    public byte piece(int index) {
        return store.piece(index);
    }

    public boolean hasPieces() {
        return store.hasPieces();
    }

    public PieceMaterials pieceMaterials() {
        return pieceMaterials;
    }

    /** El mismo plan (comparte los datos) con otros materiales fijos para sus piezas. */
    public PackedBlockPlan withPieceMaterials(PieceMaterials materials) {
        return new PackedBlockPlan(store, captureSize, bounds, materials);
    }

    /** Posiciones que llevan losa o escalera en vez del bloque entero. */
    public int pieceCount() {
        if (!store.hasPieces()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < store.size(); index++) {
            if (store.piece(index) != SmoothPiece.FULL) {
                count++;
            }
        }
        return count;
    }

    public int captureIndex(int index) {
        return store.capture(index);
    }

    public int captureSize() {
        return captureSize;
    }

    public BlockBounds bounds() {
        if (store.size() == 0) {
            throw new IllegalStateException("El plan vacío no tiene límites");
        }
        return bounds;
    }

    /** Si el plan va comprimido por páginas (los grandes). */
    public boolean compressed() {
        return store instanceof PlanPages;
    }

    /** Memoria aproximada que ocupa el plan en el heap, en bytes. */
    public long footprintBytes() {
        return 48L + store.footprintBytes();
    }

    /** Bytes del plan que esperan en un archivo temporal en vez de en memoria. */
    public long spilledBytes() {
        return store.spilledBytes();
    }

    @Override
    public Iterator<BlockVector3> iterator() {
        return new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < store.size();
            }

            @Override
            public BlockVector3 next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return position(index++);
            }
        };
    }

    public static long pack(int x, int y, int z) {
        if (y < -2048 || y > 2047) {
            throw new IllegalArgumentException("La coordenada Y está fuera del rango compatible: " + y);
        }
        return ((x & X_MASK) << 38) | ((z & Z_MASK) << 12) | (y & Y_MASK);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    /** Dónde viven las entradas: arrays ({@link Flat}) o páginas comprimidas ({@link PlanPages}). */
    interface Store {
        int size();

        long position(int index);

        BlockState override(int index);

        int expected(int index);

        int capture(int index);

        byte piece(int index);

        boolean hasOverrides();

        boolean hasPieces();

        long footprintBytes();

        default long spilledBytes() {
            return 0L;
        }
    }

    /** Arrays de las entradas; los que no hacen falta (sin bloques fijos, sin capturas…) son {@code null}. */
    private record Flat(long[] positions, BlockState[] overrides, int[] expected, int[] captures, byte[] pieces)
            implements Store {
        @Override
        public int size() {
            return positions.length;
        }

        @Override
        public long position(int index) {
            return positions[index];
        }

        @Override
        public BlockState override(int index) {
            return overrides == null ? null : overrides[index];
        }

        @Override
        public int expected(int index) {
            return expected == null ? NO_EXPECTED_STATE : expected[index];
        }

        @Override
        public int capture(int index) {
            return captures == null ? NO_CAPTURE : captures[index];
        }

        @Override
        public byte piece(int index) {
            return pieces == null ? SmoothPiece.FULL : pieces[index];
        }

        @Override
        public boolean hasOverrides() {
            return overrides != null;
        }

        @Override
        public boolean hasPieces() {
            return pieces != null;
        }

        @Override
        public long footprintBytes() {
            long bytes = 32L + 16L + 8L * positions.length;
            bytes += overrides == null ? 0L : 16L + 4L * overrides.length;
            bytes += expected == null ? 0L : 16L + 4L * expected.length;
            bytes += captures == null ? 0L : 16L + 4L * captures.length;
            bytes += pieces == null ? 0L : 16L + pieces.length;
            return bytes;
        }
    }

    public static final class Builder {
        private final Storage storage;
        private long[] positions;
        private BlockState[] overrides;
        private int[] expectedStateIds;
        private int[] captureIndices;
        private byte[] pieces;
        /** Desde que el plan pasa de {@link Storage#compressAbove()}: las entradas van directas a páginas. */
        private PlanPages.Builder paged;
        private int size;
        private boolean hasOverrides;
        private boolean hasExpectedStates;
        private boolean hasCaptures;
        private int captureSize;
        private int minimumX = Integer.MAX_VALUE;
        private int minimumY = Integer.MAX_VALUE;
        private int minimumZ = Integer.MAX_VALUE;
        private int maximumX = Integer.MIN_VALUE;
        private int maximumY = Integer.MIN_VALUE;
        private int maximumZ = Integer.MIN_VALUE;

        private Builder(int expectedSize, Storage storage) {
            this.storage = storage;
            int capacity = Math.max(16, expectedSize);
            if (storage.compressAbove() >= 0) {
                // No hace falta reservar más de lo que se guarda en arrays antes de pasar a páginas.
                capacity = Math.min(capacity, Math.max(16, storage.compressAbove()));
            }
            positions = new long[capacity];
            overrides = new BlockState[capacity];
            expectedStateIds = new int[capacity];
            captureIndices = new int[capacity];
            Arrays.fill(expectedStateIds, NO_EXPECTED_STATE);
            Arrays.fill(captureIndices, NO_CAPTURE);
        }

        public void addPattern(BlockVector3 position) {
            addPattern(position.x(), position.y(), position.z());
        }

        public void addPattern(int x, int y, int z) {
            add(x, y, z, null, NO_EXPECTED_STATE, NO_CAPTURE);
        }

        public void addCapturedPattern(BlockVector3 position, int captureIndex) {
            add(position.x(), position.y(), position.z(), null, NO_EXPECTED_STATE, captureIndex);
        }

        public void addConditionalPattern(BlockVector3 position, int expectedStateId) {
            add(position.x(), position.y(), position.z(), null, expectedStateId, NO_CAPTURE);
        }

        public void addConditionalCapturedPattern(
                BlockVector3 position,
                int expectedStateId,
                int captureIndex
        ) {
            add(position.x(), position.y(), position.z(), null, expectedStateId, captureIndex);
        }

        public void addOverride(BlockVector3 position, BlockState block) {
            addOverride(position.x(), position.y(), position.z(), block);
        }

        public void addOverride(int x, int y, int z, BlockState block) {
            if (block == null) {
                throw new IllegalArgumentException("El bloque de reemplazo no puede ser null");
            }
            add(x, y, z, block, NO_EXPECTED_STATE, NO_CAPTURE);
        }

        public void addCapturedOverride(BlockVector3 position, BlockState block, int captureIndex) {
            add(position.x(), position.y(), position.z(), block, NO_EXPECTED_STATE, captureIndex);
        }

        public void addConditionalOverride(
                int x,
                int y,
                int z,
                int expectedStateId,
                BlockState block
        ) {
            add(x, y, z, block, expectedStateId, NO_CAPTURE);
        }

        public void addConditionalCapturedOverride(
                BlockVector3 position,
                int expectedStateId,
                BlockState block,
                int captureIndex
        ) {
            add(position.x(), position.y(), position.z(), block, expectedStateId, captureIndex);
        }

        private void add(
                int x,
                int y,
                int z,
                BlockState block,
                int expectedStateId,
                int captureIndex
        ) {
            if (captureIndex < NO_CAPTURE) {
                throw new IllegalArgumentException("El índice de captura no es válido");
            }
            long packed = pack(x, y, z);
            hasOverrides |= block != null;
            hasExpectedStates |= expectedStateId != NO_EXPECTED_STATE;
            hasCaptures |= captureIndex != NO_CAPTURE;
            if (captureIndex != NO_CAPTURE) {
                captureSize = Math.max(captureSize, captureIndex + 1);
            }
            include(x, y, z);
            if (paged != null) {
                paged.add(packed, block, expectedStateId, captureIndex, SmoothPiece.FULL);
                size++;
                return;
            }
            ensureCapacity();
            positions[size] = packed;
            overrides[size] = block;
            expectedStateIds[size] = expectedStateId;
            captureIndices[size] = captureIndex;
            size++;
            if (storage.compressAbove() >= 0 && size >= storage.compressAbove()
                    && size % PlanPages.PAGE == 0) {
                toPages();
            }
        }

        /** Pasa lo que hay en arrays a páginas y suelta los arrays. */
        private void toPages() {
            paged = new PlanPages.Builder(storage.spillAbove(), storage.spillDirectory());
            for (int index = 0; index < size; index++) {
                paged.add(positions[index], overrides[index], expectedStateIds[index], captureIndices[index],
                        pieces == null ? SmoothPiece.FULL : pieces[index]);
            }
            positions = null;
            overrides = null;
            expectedStateIds = null;
            captureIndices = null;
            pieces = null;
        }

        /**
         * Sustituye el bloque del patrón de una entrada ya añadida por una losa o escalera del mismo material.
         * Solo tiene sentido en entradas sin bloque fijo. En un plan grande solo se puede retocar una entrada
         * reciente (la de la página abierta); se hace siempre justo después de añadirla.
         */
        public void setPiece(int index, byte piece) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }
            if (!SmoothPiece.isValid(piece)) {
                throw new IllegalArgumentException("Pieza no válida: " + piece);
            }
            if (paged != null) {
                if (index < paged.openStart()) {
                    throw new IllegalStateException("La pieza de una entrada antigua de un plan grande ya está"
                            + " comprimida; ponla justo al añadirla");
                }
                if (piece != SmoothPiece.FULL && paged.overrideAt(index)) {
                    throw new IllegalArgumentException("Una pieza necesita una entrada de patrón, no un bloque fijo");
                }
                paged.setPiece(index, piece);
                return;
            }
            if (piece != SmoothPiece.FULL && overrides[index] != null) {
                throw new IllegalArgumentException("Una pieza necesita una entrada de patrón, no un bloque fijo");
            }
            if (pieces == null) {
                if (piece == SmoothPiece.FULL) {
                    return;
                }
                pieces = new byte[positions.length];
            }
            pieces[index] = piece;
        }

        public int size() {
            return size;
        }

        public PackedBlockPlan build() {
            BlockBounds finalBounds = size == 0 ? null : new BlockBounds(
                    minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ
            );
            if (paged != null) {
                return new PackedBlockPlan(paged.build(), captureSize, finalBounds, PieceMaterials.NONE);
            }
            long[] finalPositions = Arrays.copyOf(positions, size);
            BlockState[] finalOverrides = hasOverrides ? Arrays.copyOf(overrides, size) : null;
            int[] finalExpectedStates = hasExpectedStates ? Arrays.copyOf(expectedStateIds, size) : null;
            int[] finalCaptureIndices = hasCaptures ? Arrays.copyOf(captureIndices, size) : null;
            byte[] finalPieces = pieces == null ? null : Arrays.copyOf(pieces, size);
            return new PackedBlockPlan(
                    new Flat(finalPositions, finalOverrides, finalExpectedStates, finalCaptureIndices, finalPieces),
                    captureSize,
                    finalBounds,
                    PieceMaterials.NONE
            );
        }

        private void include(int x, int y, int z) {
            minimumX = Math.min(minimumX, x);
            minimumY = Math.min(minimumY, y);
            minimumZ = Math.min(minimumZ, z);
            maximumX = Math.max(maximumX, x);
            maximumY = Math.max(maximumY, y);
            maximumZ = Math.max(maximumZ, z);
        }

        private void ensureCapacity() {
            if (size < positions.length) {
                return;
            }
            int next = Math.min(Integer.MAX_VALUE - 8, positions.length + (positions.length >> 1));
            if (storage.compressAbove() >= 0) {
                // Con compresión, los arrays solo llegan hasta la primera página entera tras el umbral.
                int limit = Math.max(PlanPages.PAGE, roundUp(storage.compressAbove()));
                next = Math.max(positions.length + 1, Math.min(next, limit));
            }
            if (next <= positions.length) {
                throw new IllegalStateException("El plan excede la memoria direccionable");
            }
            positions = Arrays.copyOf(positions, next);
            overrides = Arrays.copyOf(overrides, next);
            int previousLength = expectedStateIds.length;
            expectedStateIds = Arrays.copyOf(expectedStateIds, next);
            captureIndices = Arrays.copyOf(captureIndices, next);
            if (pieces != null) {
                pieces = Arrays.copyOf(pieces, next);
            }
            Arrays.fill(expectedStateIds, previousLength, next, NO_EXPECTED_STATE);
            Arrays.fill(captureIndices, previousLength, next, NO_CAPTURE);
        }

        private static int roundUp(int entries) {
            return (int) Math.min(Integer.MAX_VALUE - 8,
                    ((long) entries + PlanPages.PAGE - 1) / PlanPages.PAGE * PlanPages.PAGE);
        }
    }
}
