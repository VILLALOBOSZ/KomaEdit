package com.komainu.moxia.edit;

import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.sk89q.worldedit.world.block.BlockState;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entradas de un {@link PackedBlockPlan} grande, comprimidas en páginas de {@value #PAGE} como las secciones de un
 * chunk: cada página guarda una paleta propia de lo que se repite (el bloque fijo, el estado esperado y la pieza) y un
 * índice de pocos bits por entrada; las posiciones van en tramos (una fila seguida en X es un tramo). Los estados de
 * WorldEdit están en una paleta común del plan, así las páginas solo llevan números y se pueden volcar a disco
 * ({@link PlanSpill}) cuando el plan pasa del umbral de memoria.
 *
 * <p>El orden de las entradas no cambia. Leer en orden es casi tan rápido como con arrays: cada página se descomprime
 * una vez (la última leída se guarda) y lo demás se lee directamente de los bits. Es inmutable y se puede leer desde
 * varios hilos.</p>
 */
final class PlanPages implements PackedBlockPlan.Store {
    static final int SHIFT = 12;
    static final int PAGE = 1 << SHIFT;
    private static final int MASK = PAGE - 1;

    private final int size;
    /** {@link Page} en memoria o {@link PlanSpill.Slot} en disco. */
    private final Object[] pages;
    /** Paleta común de bloques fijos; el 0 es «sin bloque fijo». */
    private final BlockState[] palette;
    private final boolean overrides;
    private final boolean pieces;
    private final PlanSpill spill;
    private final long memoryBytes;
    private final long spilledBytes;
    /** La última página leída, ya descomprimida. */
    private volatile Decoded last;

    private PlanPages(int size, Object[] pages, BlockState[] palette, boolean overrides, boolean pieces,
                      PlanSpill spill, long memoryBytes, long spilledBytes) {
        this.size = size;
        this.pages = pages;
        this.palette = palette;
        this.overrides = overrides;
        this.pieces = pieces;
        this.spill = spill;
        this.memoryBytes = memoryBytes;
        this.spilledBytes = spilledBytes;
    }

    private record Decoded(int index, Page page, long[] positions) {
    }

    private Decoded decoded(int pageIndex) {
        Decoded current = last;
        if (current != null && current.index == pageIndex) {
            return current;
        }
        Object stored = pages[pageIndex];
        Page page = stored instanceof Page memory ? memory : spill.read((PlanSpill.Slot) stored);
        Decoded fresh = new Decoded(pageIndex, page, page.positions());
        last = fresh;
        return fresh;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public long position(int index) {
        return decoded(index >>> SHIFT).positions[index & MASK];
    }

    @Override
    public BlockState override(int index) {
        return overrides ? palette[decoded(index >>> SHIFT).page.overrides.get(index & MASK)] : null;
    }

    @Override
    public int expected(int index) {
        return decoded(index >>> SHIFT).page.expected.get(index & MASK);
    }

    @Override
    public int capture(int index) {
        return decoded(index >>> SHIFT).page.capture(index & MASK);
    }

    @Override
    public byte piece(int index) {
        return pieces ? (byte) decoded(index >>> SHIFT).page.pieces.get(index & MASK) : SmoothPiece.FULL;
    }

    @Override
    public boolean hasOverrides() {
        return overrides;
    }

    @Override
    public boolean hasPieces() {
        return pieces;
    }

    @Override
    public long footprintBytes() {
        return memoryBytes + 16L + 4L * palette.length + 16L + 4L * pages.length;
    }

    @Override
    public long spilledBytes() {
        return spilledBytes;
    }

    /** Montaje página a página; se sigue desde las entradas que ya llevaba el plan sin comprimir. */
    static final class Builder {
        private final long spillAbove;
        private final java.nio.file.Path spillDirectory;
        private final List<Object> sealed = new ArrayList<>();
        private final Map<BlockState, Integer> paletteIndex = new IdentityHashMap<>();
        private final List<BlockState> palette = new ArrayList<>();
        private final long[] positions = new long[PAGE];
        private final int[] overrides = new int[PAGE];
        private final int[] expected = new int[PAGE];
        private final int[] captures = new int[PAGE];
        private final byte[] pieces = new byte[PAGE];
        private int open;
        private int sealedEntries;
        private boolean hasOverrides;
        private boolean hasPieces;
        private PlanSpill spill;
        private long memoryBytes;
        private long spilledBytes;

        Builder(long spillAbove, java.nio.file.Path spillDirectory) {
            this.spillAbove = spillAbove;
            this.spillDirectory = spillDirectory;
            palette.add(null);
            paletteIndex.put(null, 0);
        }

        int size() {
            return sealedEntries + open;
        }

        /** Primera entrada de la página abierta: solo esas se pueden retocar ({@link #setPiece}). */
        int openStart() {
            return sealedEntries;
        }

        void add(long position, BlockState override, int expectedState, int captureIndex, byte piece) {
            // La página llena se sella al llegar la siguiente: así la última entrada aún admite su pieza.
            if (open == PAGE) {
                seal();
            }
            positions[open] = position;
            overrides[open] = paletteIndex(override);
            expected[open] = expectedState;
            captures[open] = captureIndex;
            pieces[open] = piece;
            hasOverrides |= override != null;
            hasPieces |= piece != SmoothPiece.FULL;
            open++;
        }

        boolean overrideAt(int index) {
            return overrides[index - sealedEntries] != 0;
        }

        void setPiece(int index, byte piece) {
            pieces[index - sealedEntries] = piece;
            hasPieces |= piece != SmoothPiece.FULL;
        }

        private int paletteIndex(BlockState state) {
            Integer known = paletteIndex.get(state);
            if (known != null) {
                return known;
            }
            int index = palette.size();
            palette.add(state);
            paletteIndex.put(state, index);
            return index;
        }

        private void seal() {
            if (open == 0) {
                return;
            }
            Page page = Page.seal(positions, overrides, expected, captures, pieces, open);
            long bytes = page.bytes();
            if (spillAbove >= 0 && memoryBytes + bytes > spillAbove) {
                try {
                    if (spill == null) {
                        spill = PlanSpill.create(spillDirectory);
                    }
                    PlanSpill.Slot slot = spill.append(page.serialize());
                    sealed.add(slot);
                    spilledBytes += slot.length();
                } catch (IOException exception) {
                    // Sin disco el plan sigue entero en memoria: más memoria, pero no se pierde la edición.
                    sealed.add(page);
                    memoryBytes += bytes;
                }
            } else {
                sealed.add(page);
                memoryBytes += bytes;
            }
            sealedEntries += open;
            open = 0;
        }

        PlanPages build() {
            seal();
            if (spill != null) {
                spill.finish();
            }
            return new PlanPages(sealedEntries, sealed.toArray(), palette.toArray(new BlockState[0]), hasOverrides,
                    hasPieces, spill, memoryBytes, spilledBytes);
        }
    }

    /** Una página: hasta {@value #PAGE} entradas seguidas del plan. */
    static final class Page {
        private static final byte CAPTURE_NONE = 0;
        private static final byte CAPTURE_SEQUENCE = 1;
        private static final byte CAPTURE_RAW = 2;

        private final int count;
        /** Posiciones tal cual, si los tramos no ahorran. */
        private final long[] raw;
        /** Posiciones en tramos ({@link #runs}). */
        private final byte[] runs;
        private final Packed overrides;
        private final Packed expected;
        private final Packed pieces;
        private final byte captureMode;
        private final int captureStart;
        private final int[] captures;

        private Page(int count, long[] raw, byte[] runs, Packed overrides, Packed expected, Packed pieces,
                     byte captureMode, int captureStart, int[] captures) {
            this.count = count;
            this.raw = raw;
            this.runs = runs;
            this.overrides = overrides;
            this.expected = expected;
            this.pieces = pieces;
            this.captureMode = captureMode;
            this.captureStart = captureStart;
            this.captures = captures;
        }

        static Page seal(long[] positions, int[] overrides, int[] expected, int[] captures, byte[] pieces,
                         int count) {
            byte[] runs = runs(positions, count);
            long[] raw = runs.length > count * 6L ? Arrays.copyOf(positions, count) : null;
            int[] pieceValues = new int[count];
            for (int index = 0; index < count; index++) {
                pieceValues[index] = pieces[index];
            }
            byte mode = CAPTURE_NONE;
            int start = PackedBlockPlan.NO_CAPTURE;
            int[] rawCaptures = null;
            boolean none = true;
            boolean sequence = true;
            for (int index = 0; index < count; index++) {
                none &= captures[index] == PackedBlockPlan.NO_CAPTURE;
                sequence &= captures[index] != PackedBlockPlan.NO_CAPTURE && captures[index] == captures[0] + index;
            }
            if (!none) {
                if (sequence) {
                    mode = CAPTURE_SEQUENCE;
                    start = captures[0];
                } else {
                    mode = CAPTURE_RAW;
                    rawCaptures = Arrays.copyOf(captures, count);
                }
            }
            return new Page(count, raw, raw == null ? runs : null, Packed.of(overrides, count),
                    Packed.of(expected, count), Packed.of(pieceValues, count), mode, start, rawCaptures);
        }

        long[] positions() {
            return raw != null ? raw : decodeRuns(runs, count);
        }

        int capture(int offset) {
            return switch (captureMode) {
                case CAPTURE_SEQUENCE -> captureStart + offset;
                case CAPTURE_RAW -> captures[offset];
                default -> PackedBlockPlan.NO_CAPTURE;
            };
        }

        /** Memoria aproximada de la página (cabeceras de objeto incluidas). */
        long bytes() {
            long bytes = 48L;
            bytes += raw != null ? 16L + 8L * raw.length : 16L + runs.length;
            bytes += overrides.bytes() + expected.bytes() + pieces.bytes();
            bytes += captures == null ? 0L : 16L + 4L * captures.length;
            return bytes;
        }

        byte[] serialize() {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(Integer.MAX_VALUE - 8, bytes()));
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeShort(count);
                if (raw != null) {
                    out.writeByte(0);
                    for (int index = 0; index < count; index++) {
                        out.writeLong(raw[index]);
                    }
                } else {
                    out.writeByte(1);
                    out.writeInt(runs.length);
                    out.write(runs);
                }
                overrides.write(out);
                expected.write(out);
                pieces.write(out);
                out.writeByte(captureMode);
                out.writeInt(captureStart);
                if (captures != null) {
                    for (int index = 0; index < count; index++) {
                        out.writeInt(captures[index]);
                    }
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return bytes.toByteArray();
        }

        static Page read(DataInputStream in) throws IOException {
            int count = in.readUnsignedShort();
            long[] raw = null;
            byte[] runs = null;
            if (in.readByte() == 0) {
                raw = new long[count];
                for (int index = 0; index < count; index++) {
                    raw[index] = in.readLong();
                }
            } else {
                runs = new byte[in.readInt()];
                in.readFully(runs);
            }
            Packed overrides = Packed.read(in);
            Packed expected = Packed.read(in);
            Packed pieces = Packed.read(in);
            byte mode = in.readByte();
            int start = in.readInt();
            int[] captures = null;
            if (mode == CAPTURE_RAW) {
                captures = new int[count];
                for (int index = 0; index < count; index++) {
                    captures[index] = in.readInt();
                }
            }
            return new Page(count, raw, runs, overrides, expected, pieces, mode, start, captures);
        }
    }

    /**
     * Tramos de posiciones: cada tramo son entradas seguidas que avanzan de una en una en X. Se escribe la distancia
     * en X, Y y Z desde la última posición del tramo anterior y cuántas entradas lleva, en enteros de longitud
     * variable: una fila de 100 bloques ocupa unos 5 bytes en vez de 800.
     */
    static byte[] runs(long[] positions, int count) {
        ByteSink out = new ByteSink(Math.max(16, count / 4));
        int previousX = 0;
        int previousY = 0;
        int previousZ = 0;
        int index = 0;
        while (index < count) {
            long packed = positions[index];
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            int length = 1;
            while (index + length < count) {
                long next = positions[index + length];
                if (PackedBlockPlan.unpackX(next) != x + length || PackedBlockPlan.unpackY(next) != y
                        || PackedBlockPlan.unpackZ(next) != z) {
                    break;
                }
                length++;
            }
            out.varInt(zigZag(x - previousX));
            out.varInt(zigZag(y - previousY));
            out.varInt(zigZag(z - previousZ));
            out.varInt(length - 1);
            previousX = x + length - 1;
            previousY = y;
            previousZ = z;
            index += length;
        }
        return out.toArray();
    }

    static long[] decodeRuns(byte[] runs, int count) {
        long[] positions = new long[count];
        int[] cursor = {0};
        int previousX = 0;
        int previousY = 0;
        int previousZ = 0;
        int index = 0;
        while (index < count) {
            int x = previousX + unZigZag(readVarInt(runs, cursor));
            int y = previousY + unZigZag(readVarInt(runs, cursor));
            int z = previousZ + unZigZag(readVarInt(runs, cursor));
            int length = readVarInt(runs, cursor) + 1;
            for (int step = 0; step < length; step++) {
                positions[index++] = PackedBlockPlan.pack(x + step, y, z);
            }
            previousX = x + length - 1;
            previousY = y;
            previousZ = z;
        }
        return positions;
    }

    private static int zigZag(int value) {
        return (value << 1) ^ (value >> 31);
    }

    private static int unZigZag(int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    private static int readVarInt(byte[] bytes, int[] cursor) {
        int value = 0;
        int shift = 0;
        while (true) {
            byte current = bytes[cursor[0]++];
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
    }

    /** Bytes que crecen, sin las comprobaciones de {@link ByteArrayOutputStream}. */
    private static final class ByteSink {
        private byte[] bytes;
        private int size;

        ByteSink(int capacity) {
            bytes = new byte[capacity];
        }

        void varInt(int value) {
            if (size + 5 > bytes.length) {
                bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + 5));
            }
            while ((value & ~0x7F) != 0) {
                bytes[size++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            bytes[size++] = (byte) value;
        }

        byte[] toArray() {
            return Arrays.copyOf(bytes, size);
        }
    }

    /**
     * Valores de una página con su paleta: cada entrada guarda el índice en la paleta con los bits justos (0 si toda
     * la página tiene el mismo valor), varios por {@code long}, sin partir ninguno entre dos.
     */
    static final class Packed {
        private final int[] palette;
        private final int bits;
        private final int perWord;
        private final long[] words;

        private Packed(int[] palette, int bits, long[] words) {
            this.palette = palette;
            this.bits = bits;
            this.perWord = bits == 0 ? 0 : 64 / bits;
            this.words = words;
        }

        static Packed of(int[] values, int count) {
            Int2IntOpenHashMap indices = new Int2IntOpenHashMap();
            indices.defaultReturnValue(-1);
            int[] palette = new int[4];
            int distinct = 0;
            int[] local = new int[count];
            for (int index = 0; index < count; index++) {
                int value = values[index];
                int found = indices.get(value);
                if (found < 0) {
                    found = distinct++;
                    indices.put(value, found);
                    if (found == palette.length) {
                        palette = Arrays.copyOf(palette, palette.length * 2);
                    }
                    palette[found] = value;
                }
                local[index] = found;
            }
            palette = Arrays.copyOf(palette, Math.max(1, distinct));
            if (distinct <= 1) {
                return new Packed(palette, 0, null);
            }
            int bits = 32 - Integer.numberOfLeadingZeros(distinct - 1);
            int perWord = 64 / bits;
            long[] words = new long[(count + perWord - 1) / perWord];
            for (int index = 0; index < count; index++) {
                words[index / perWord] |= (long) local[index] << ((index % perWord) * bits);
            }
            return new Packed(palette, bits, words);
        }

        int get(int offset) {
            if (bits == 0) {
                return palette[0];
            }
            long word = words[offset / perWord];
            int shift = (offset % perWord) * bits;
            return palette[(int) ((word >>> shift) & ((1L << bits) - 1))];
        }

        long bytes() {
            return 32L + 16L + 4L * palette.length + (words == null ? 0L : 16L + 8L * words.length);
        }

        void write(DataOutputStream out) throws IOException {
            out.writeInt(palette.length);
            for (int value : palette) {
                out.writeInt(value);
            }
            out.writeByte(bits);
            if (words != null) {
                out.writeInt(words.length);
                for (long word : words) {
                    out.writeLong(word);
                }
            }
        }

        static Packed read(DataInputStream in) throws IOException {
            int[] palette = new int[in.readInt()];
            for (int index = 0; index < palette.length; index++) {
                palette[index] = in.readInt();
            }
            int bits = in.readByte();
            long[] words = null;
            if (bits > 0) {
                words = new long[in.readInt()];
                for (int index = 0; index < words.length; index++) {
                    words[index] = in.readLong();
                }
            }
            return new Packed(palette, bits, words);
        }
    }
}
