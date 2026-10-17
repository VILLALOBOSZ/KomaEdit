package com.komainu.moxia.edit;

import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.SmoothPieces;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class PackedBlockPlan implements Iterable<BlockVector3> {
    public static final int NO_EXPECTED_STATE = Integer.MIN_VALUE;
    public static final int NO_CAPTURE = -1;
    private static final long X_MASK = 0x3FFFFFFL;
    private static final long Y_MASK = 0xFFFL;
    private static final long Z_MASK = 0x3FFFFFFL;

    private final long[] positions;
    private final BlockState[] overrides;
    private final int[] expectedStateIds;
    private final int[] captureIndices;
    /** Losa o escalera que sustituye al bloque del patrón; {@code null} si todo el plan son bloques enteros. */
    private final byte[] pieces;
    private final int captureSize;
    private final BlockBounds bounds;

    private PackedBlockPlan(
            long[] positions,
            BlockState[] overrides,
            int[] expectedStateIds,
            int[] captureIndices,
            byte[] pieces,
            int captureSize,
            BlockBounds bounds
    ) {
        this.positions = positions;
        this.overrides = overrides;
        this.expectedStateIds = expectedStateIds;
        this.captureIndices = captureIndices;
        this.pieces = pieces;
        this.captureSize = captureSize;
        this.bounds = bounds;
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
        return new Builder(expectedSize);
    }

    public int size() {
        return positions.length;
    }

    public BlockVector3 position(int index) {
        long packed = positions[index];
        return BlockVector3.at(unpackX(packed), unpackY(packed), unpackZ(packed));
    }

    public BlockState override(int index) {
        return overrides == null ? null : overrides[index];
    }

    public boolean usesOnlyPattern() {
        return overrides == null;
    }

    public long packedPosition(int index) {
        return positions[index];
    }

    public int expectedStateId(int index) {
        return expectedStateIds == null ? NO_EXPECTED_STATE : expectedStateIds[index];
    }

    /** Pieza de la posición: {@link SmoothPiece#FULL} salvo que el plan sustituya el bloque por una pieza. */
    public byte piece(int index) {
        return pieces == null ? SmoothPiece.FULL : pieces[index];
    }

    public boolean hasPieces() {
        return pieces != null;
    }

    /** Posiciones que llevan losa o escalera en vez del bloque entero. */
    public int pieceCount() {
        if (pieces == null) {
            return 0;
        }
        int count = 0;
        for (byte piece : pieces) {
            if (piece != SmoothPiece.FULL) {
                count++;
            }
        }
        return count;
    }

    public int captureIndex(int index) {
        return captureIndices == null ? NO_CAPTURE : captureIndices[index];
    }

    public int captureSize() {
        return captureSize;
    }

    public BlockBounds bounds() {
        if (positions.length == 0) {
            throw new IllegalStateException("El plan vacío no tiene límites");
        }
        return bounds;
    }

    @Override
    public Iterator<BlockVector3> iterator() {
        return new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < positions.length;
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

    public static final class Builder {
        private long[] positions;
        private BlockState[] overrides;
        private int[] expectedStateIds;
        private int[] captureIndices;
        private byte[] pieces;
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

        private Builder(int expectedSize) {
            int capacity = Math.max(16, expectedSize);
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
            ensureCapacity();
            positions[size] = pack(x, y, z);
            overrides[size] = block;
            expectedStateIds[size] = expectedStateId;
            captureIndices[size] = captureIndex;
            hasOverrides |= block != null;
            hasExpectedStates |= expectedStateId != NO_EXPECTED_STATE;
            hasCaptures |= captureIndex != NO_CAPTURE;
            if (captureIndex != NO_CAPTURE) {
                captureSize = Math.max(captureSize, captureIndex + 1);
            }
            include(x, y, z);
            size++;
        }

        /**
         * Sustituye el bloque del patrón de una entrada ya añadida por una losa o escalera del mismo material.
         * Solo tiene sentido en entradas sin bloque fijo.
         */
        public void setPiece(int index, byte piece) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }
            if (!SmoothPiece.isValid(piece)) {
                throw new IllegalArgumentException("Pieza no válida: " + piece);
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
            long[] finalPositions = Arrays.copyOf(positions, size);
            BlockState[] finalOverrides = hasOverrides ? Arrays.copyOf(overrides, size) : null;
            int[] finalExpectedStates = hasExpectedStates ? Arrays.copyOf(expectedStateIds, size) : null;
            int[] finalCaptureIndices = hasCaptures ? Arrays.copyOf(captureIndices, size) : null;
            byte[] finalPieces = pieces == null ? null : Arrays.copyOf(pieces, size);
            BlockBounds finalBounds = size == 0 ? null : new BlockBounds(
                    minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ
            );
            return new PackedBlockPlan(
                    finalPositions,
                    finalOverrides,
                    finalExpectedStates,
                    finalCaptureIndices,
                    finalPieces,
                    captureSize,
                    finalBounds
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
    }
}
