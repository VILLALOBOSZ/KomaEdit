package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * El plan de una vista previa en bytes, para el cliente con KomaEdit que la dibuja él mismo en lugar de recibir
 * block displays. Va troceado en partes de pocos KiB que el servidor reparte entre ticks.
 *
 * <p>Formato (versión 1). La parte 0 empieza por la cabecera: {@code 'K' 'P'}, la versión, el id, la caja (seis
 * enteros con zigzag), los cambios totales, los puestos y los quitados, el modo ({@link DisplaySampler.Mode}) y
 * cuántas entradas siguen. Después, en todas las partes, bloques de sección: la sección (tres enteros con zigzag) y
 * cuántas entradas trae; cada entrada son dos bytes con la posición dentro de la sección (12 bits) y el tipo
 * ({@link PreviewDisplays.Kind}, 2 bits), un byte con las caras al descubierto ({@link PreviewSurface}) y el estado
 * de bloque (su id crudo, el mismo que usa Minecraft en los chunks). Una sección puede repartirse entre dos
 * partes. Los enteros van en varint.</p>
 *
 * <p>Lógica pura: se prueba sin Minecraft.</p>
 */
public final class PreviewPlanCodec {
    public static final int VERSION = 1;
    public static final int DEFAULT_PART_BYTES = 32 * 1024;
    public static final int MAX_PART_BYTES = 256 * 1024;
    public static final int MAX_PARTS = 4096;
    public static final int MAX_ENTRIES = 1 << 21;
    private static final int MAGIC_K = 'K';
    private static final int MAGIC_P = 'P';
    private static final PreviewDisplays.Kind[] KINDS = PreviewDisplays.Kind.values();

    private PreviewPlanCodec() {
    }

    /**
     * Lo que acompaña a las entradas.
     *
     * @param changes cambios del plan entero (lo enviado puede ser solo su superficie o una muestra)
     * @param entries entradas enviadas
     */
    public record Header(int id, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, long changes,
                         long added, long removed, DisplaySampler.Mode mode, int entries) {
        public Header {
            if (mode == null) {
                throw new IllegalArgumentException("Falta el modo");
            }
            if (entries < 0 || entries > MAX_ENTRIES) {
                throw new IllegalArgumentException("Entradas fuera de rango: " + entries);
            }
        }

        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }
    }

    /** Un plan leído, en arrays paralelos ordenados por sección. */
    public record Plan(Header header, long[] positions, int[] states, byte[] kinds, byte[] faces) {
        public int size() {
            return positions.length;
        }

        public PreviewDisplays.Kind kind(int index) {
            return KINDS[kinds[index]];
        }
    }

    /**
     * Trocea el plan en partes de como mucho {@code partBytes} bytes.
     *
     * @param positions posiciones empaquetadas ({@link PackedBlockPlan#pack})
     * @param states    id crudo del estado de cada entrada
     * @param kinds     ordinal de {@link PreviewDisplays.Kind}
     * @param faces     caras al descubierto (6 bits)
     */
    public static List<byte[]> encode(Header header, long[] positions, int[] states, byte[] kinds, byte[] faces,
                                      int partBytes) {
        int count = positions.length;
        if (states.length != count || kinds.length != count || faces.length != count) {
            throw new IllegalArgumentException("Los arrays del plan no tienen la misma longitud");
        }
        if (header.entries() != count) {
            throw new IllegalArgumentException("La cabecera dice " + header.entries() + " entradas y hay " + count);
        }
        if (partBytes < 256 || partBytes > MAX_PART_BYTES) {
            throw new IllegalArgumentException("Tamaño de parte fuera de rango: " + partBytes);
        }
        long[] keys = new long[count];
        int[] order = new int[count];
        for (int index = 0; index < count; index++) {
            keys[index] = sortKey(positions[index]);
            order[index] = index;
            if (kinds[index] < 0 || kinds[index] >= KINDS.length) {
                throw new IllegalArgumentException("Tipo de cambio desconocido: " + kinds[index]);
            }
        }
        IntArrays.quickSort(order, (first, second) -> Long.compare(keys[first], keys[second]));

        List<byte[]> parts = new ArrayList<>();
        Bytes part = new Bytes(partBytes);
        writeHeader(part, header);
        int blocks = 0;
        int cursor = 0;
        while (cursor < count) {
            long first = positions[order[cursor]];
            int sectionX = PackedBlockPlan.unpackX(first) >> 4;
            int sectionY = PackedBlockPlan.unpackY(first) >> 4;
            int sectionZ = PackedBlockPlan.unpackZ(first) >> 4;
            int end = cursor;
            while (end < count && sameSection(positions[order[end]], sectionX, sectionY, sectionZ)) {
                end++;
            }
            while (cursor < end) {
                int headerSize = zigzagSize(sectionX) + zigzagSize(sectionY) + zigzagSize(sectionZ) + 3;
                int room = partBytes - part.size() - headerSize;
                int fits = 0;
                int used = 0;
                while (cursor + fits < end) {
                    int entry = 3 + varIntSize(states[order[cursor + fits]]);
                    if (used + entry > room) {
                        break;
                    }
                    used += entry;
                    fits++;
                }
                if (fits == 0) {
                    if (blocks == 0) {
                        throw new IllegalArgumentException("Una entrada no cabe en una parte de " + partBytes
                                + " bytes");
                    }
                    parts.add(part.toArray());
                    part = new Bytes(partBytes);
                    blocks = 0;
                    continue;
                }
                blocks++;
                part.zigzag(sectionX);
                part.zigzag(sectionY);
                part.zigzag(sectionZ);
                part.fixedVarInt3(fits);
                for (int slot = cursor; slot < cursor + fits; slot++) {
                    int index = order[slot];
                    long packed = positions[index];
                    int local = (PackedBlockPlan.unpackX(packed) & 15)
                            | (PackedBlockPlan.unpackY(packed) & 15) << 4
                            | (PackedBlockPlan.unpackZ(packed) & 15) << 8;
                    int word = local | kinds[index] << 12;
                    part.raw(word & 0xFF);
                    part.raw(word >>> 8 & 0xFF);
                    part.raw(faces[index] & PreviewSurface.ALL_FACES);
                    part.varInt(states[index]);
                }
                cursor += fits;
            }
        }
        parts.add(part.toArray());
        if (parts.size() > MAX_PARTS) {
            throw new IllegalArgumentException("El plan necesita " + parts.size() + " partes (máximo " + MAX_PARTS
                    + ")");
        }
        return parts;
    }

    /** Lee las partes, en orden. Falla con {@link IllegalArgumentException} si están rotas o incompletas. */
    public static Plan decode(List<byte[]> parts) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Sin partes");
        }
        Reader first = new Reader(parts.getFirst());
        if (first.raw() != MAGIC_K || first.raw() != MAGIC_P) {
            throw new IllegalArgumentException("No es un plan de vista previa");
        }
        int version = first.raw();
        if (version != VERSION) {
            throw new IllegalArgumentException("Versión de plan desconocida: " + version);
        }
        int id = first.varInt();
        int minX = first.zigzag();
        int minY = first.zigzag();
        int minZ = first.zigzag();
        int maxX = first.zigzag();
        int maxY = first.zigzag();
        int maxZ = first.zigzag();
        long changes = first.varLong();
        long added = first.varLong();
        long removed = first.varLong();
        int modeOrdinal = first.raw();
        DisplaySampler.Mode[] modes = DisplaySampler.Mode.values();
        if (modeOrdinal >= modes.length) {
            throw new IllegalArgumentException("Modo desconocido: " + modeOrdinal);
        }
        int entries = first.varInt();
        Header header = new Header(id, minX, minY, minZ, maxX, maxY, maxZ, changes, added, removed,
                modes[modeOrdinal], entries);
        long[] positions = new long[entries];
        int[] states = new int[entries];
        byte[] kinds = new byte[entries];
        byte[] faces = new byte[entries];
        int filled = 0;
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            Reader reader = partIndex == 0 ? first : new Reader(parts.get(partIndex));
            while (reader.remaining() > 0) {
                int sectionX = reader.zigzag();
                int sectionY = reader.zigzag();
                int sectionZ = reader.zigzag();
                int count = reader.varInt();
                if (count < 0 || filled + count > entries) {
                    throw new IllegalArgumentException("Más entradas de las anunciadas");
                }
                for (int slot = 0; slot < count; slot++) {
                    int low = reader.raw();
                    int high = reader.raw();
                    int word = low | high << 8;
                    int kind = word >>> 12 & 3;
                    if (kind >= KINDS.length) {
                        throw new IllegalArgumentException("Tipo de cambio desconocido: " + kind);
                    }
                    int local = word & 0xFFF;
                    int x = sectionX << 4 | (local & 15);
                    int y = sectionY << 4 | (local >> 4 & 15);
                    int z = sectionZ << 4 | (local >> 8 & 15);
                    positions[filled] = PackedBlockPlan.pack(x, y, z);
                    kinds[filled] = (byte) kind;
                    faces[filled] = (byte) (reader.raw() & PreviewSurface.ALL_FACES);
                    states[filled] = reader.varInt();
                    filled++;
                }
            }
        }
        if (filled != entries) {
            throw new IllegalArgumentException("Faltan entradas: " + filled + " de " + entries);
        }
        return new Plan(header, positions, states, kinds, faces);
    }

    /**
     * Junta las partes de un plan en el cliente. Las de otro plan (uno anterior, ya sustituido) descartan lo que
     * hubiera a medias.
     */
    public static final class Assembler {
        private int id = Integer.MIN_VALUE;
        private byte[][] parts;
        private int received;
        private long bytes;

        /** @return el plan cuando llega su última parte */
        public Optional<Plan> accept(int planId, int part, int total, byte[] data) {
            if (total <= 0 || total > MAX_PARTS || part < 0 || part >= total || data == null) {
                throw new IllegalArgumentException("Parte fuera de rango: " + part + " de " + total);
            }
            if (planId != id || parts == null || parts.length != total) {
                id = planId;
                parts = new byte[total][];
                received = 0;
                bytes = 0;
            }
            if (parts[part] == null) {
                received++;
                bytes += data.length;
                if (bytes > (long) MAX_PARTS * MAX_PART_BYTES) {
                    clear();
                    throw new IllegalArgumentException("Plan demasiado grande");
                }
            }
            parts[part] = data;
            if (received < total) {
                return Optional.empty();
            }
            List<byte[]> complete = Arrays.asList(parts);
            clear();
            Plan plan = decode(complete);
            if (plan.header().id() != planId) {
                throw new IllegalArgumentException("El plan " + plan.header().id() + " llegó como " + planId);
            }
            return Optional.of(plan);
        }

        /** Partes recibidas del plan en curso, de {@link #expected()}. */
        public int received() {
            return parts == null ? 0 : received;
        }

        public int expected() {
            return parts == null ? 0 : parts.length;
        }

        public void clear() {
            id = Integer.MIN_VALUE;
            parts = null;
            received = 0;
            bytes = 0;
        }
    }

    // ---------------------------------------------------------------- bytes

    private static void writeHeader(Bytes out, Header header) {
        out.raw(MAGIC_K);
        out.raw(MAGIC_P);
        out.raw(VERSION);
        out.varInt(header.id());
        out.zigzag(header.minX());
        out.zigzag(header.minY());
        out.zigzag(header.minZ());
        out.zigzag(header.maxX());
        out.zigzag(header.maxY());
        out.zigzag(header.maxZ());
        out.varLong(Math.max(0L, header.changes()));
        out.varLong(Math.max(0L, header.added()));
        out.varLong(Math.max(0L, header.removed()));
        out.raw(header.mode().ordinal());
        out.varInt(header.entries());
    }

    private static boolean sameSection(long packed, int sectionX, int sectionY, int sectionZ) {
        return PackedBlockPlan.unpackX(packed) >> 4 == sectionX
                && PackedBlockPlan.unpackY(packed) >> 4 == sectionY
                && PackedBlockPlan.unpackZ(packed) >> 4 == sectionZ;
    }

    private static long sortKey(long packed) {
        long sectionX = PackedBlockPlan.unpackX(packed) >> 4 & 0x3FFFFFL;
        long sectionY = PackedBlockPlan.unpackY(packed) >> 4 & 0xFFL;
        long sectionZ = PackedBlockPlan.unpackZ(packed) >> 4 & 0x3FFFFFL;
        long local = (PackedBlockPlan.unpackX(packed) & 15)
                | (PackedBlockPlan.unpackY(packed) & 15) << 4
                | (PackedBlockPlan.unpackZ(packed) & 15) << 8;
        return sectionX << 42 | sectionZ << 20 | sectionY << 12 | local;
    }

    static int varIntSize(int value) {
        int size = 1;
        int rest = value >>> 7;
        while (rest != 0) {
            size++;
            rest >>>= 7;
        }
        return size;
    }

    private static int zigzagSize(int value) {
        return varIntSize(value << 1 ^ value >> 31);
    }

    private static final class Bytes {
        private byte[] data;
        private int size;

        private Bytes(int capacity) {
            data = new byte[Math.max(16, capacity)];
        }

        private int size() {
            return size;
        }

        private void raw(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = (byte) value;
        }

        private void varInt(int value) {
            int rest = value;
            while ((rest & ~0x7F) != 0) {
                raw(rest & 0x7F | 0x80);
                rest >>>= 7;
            }
            raw(rest);
        }

        /** Varint de tres bytes siempre, para saber el tamaño de la cabecera de sección antes de contar. */
        private void fixedVarInt3(int value) {
            if (value < 0 || value >= 1 << 21) {
                throw new IllegalArgumentException("Demasiadas entradas en una sección: " + value);
            }
            raw(value & 0x7F | 0x80);
            raw(value >>> 7 & 0x7F | 0x80);
            raw(value >>> 14 & 0x7F);
        }

        private void varLong(long value) {
            long rest = value;
            while ((rest & ~0x7FL) != 0) {
                raw((int) (rest & 0x7F) | 0x80);
                rest >>>= 7;
            }
            raw((int) rest);
        }

        private void zigzag(int value) {
            varInt(value << 1 ^ value >> 31);
        }

        private byte[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    private static final class Reader {
        private final byte[] data;
        private int cursor;

        private Reader(byte[] data) {
            this.data = data;
        }

        private int remaining() {
            return data.length - cursor;
        }

        private int raw() {
            if (cursor >= data.length) {
                throw new IllegalArgumentException("Parte cortada");
            }
            return data[cursor++] & 0xFF;
        }

        private int varInt() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int part = raw();
                value |= (part & 0x7F) << shift;
                if ((part & 0x80) == 0) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Varint demasiado largo");
        }

        private long varLong() {
            long value = 0;
            for (int shift = 0; shift < 70; shift += 7) {
                int part = raw();
                value |= (long) (part & 0x7F) << shift;
                if ((part & 0x80) == 0) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Varlong demasiado largo");
        }

        private int zigzag() {
            int value = varInt();
            return value >>> 1 ^ -(value & 1);
        }
    }
}
