package com.komainu.moxia.schematic;

/**
 * Los bloques de una región de Litematica ({@code BlockStates}): índices de la paleta empaquetados uno detrás de otro
 * en un array de longs, con {@code max(2, ⌈log2(tamaño de la paleta)⌉)} bits cada uno, empezando por los bits bajos.
 * Una entrada puede empezar al final de un long y acabar al principio del siguiente: no es el formato de los chunks
 * de Minecraft 1.16+, que deja huecos para que no pase.
 */
public final class PackedStates {
    private PackedStates() {
    }

    /** Bits por entrada para una paleta de ese tamaño: 2 como poco. */
    public static int bits(int paletteSize) {
        if (paletteSize < 1) {
            throw new IllegalArgumentException("Paleta vacía");
        }
        return Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    /** Longs que ocupan {@code count} entradas de {@code bits} bits. */
    public static long longs(long count, int bits) {
        return (count * bits + Long.SIZE - 1) / Long.SIZE;
    }

    public static long[] pack(int[] values, int bits) {
        checkBits(bits);
        long length = longs(values.length, bits);
        if (length > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Demasiados bloques para una región");
        }
        long[] data = new long[(int) length];
        long mask = (1L << bits) - 1L;
        for (int index = 0; index < values.length; index++) {
            long value = values[index] & mask;
            long bit = (long) index * bits;
            int word = (int) (bit >>> 6);
            int offset = (int) (bit & 63);
            data[word] |= value << offset;
            if (offset + bits > Long.SIZE) {
                data[word + 1] |= value >>> (Long.SIZE - offset);
            }
        }
        return data;
    }

    /**
     * Desempaqueta {@code count} entradas y comprueba que ninguna apunta fuera de la paleta.
     *
     * @throws IllegalArgumentException si el array no tiene la longitud que toca o hay índices fuera de la paleta
     */
    public static int[] unpack(long[] data, int count, int bits, int paletteSize) {
        checkBits(bits);
        if (data.length != longs(count, bits)) {
            throw new IllegalArgumentException("Los bloques no cuadran con el tamaño de la región ("
                    + data.length + " longs; tocan " + longs(count, bits) + ")");
        }
        int[] values = new int[count];
        long mask = (1L << bits) - 1L;
        for (int index = 0; index < count; index++) {
            long bit = (long) index * bits;
            int word = (int) (bit >>> 6);
            int offset = (int) (bit & 63);
            long value = data[word] >>> offset;
            if (offset + bits > Long.SIZE) {
                value |= data[word + 1] << (Long.SIZE - offset);
            }
            int entry = (int) (value & mask);
            if (entry >= paletteSize) {
                throw new IllegalArgumentException("Los bloques apuntan fuera de la paleta (" + entry + " de "
                        + paletteSize + ")");
            }
            values[index] = entry;
        }
        return values;
    }

    /**
     * Los bits con que se escribió un array: los de la paleta, o si el array no tiene esa longitud, otros con los que
     * cuadre (algunos programas escriben más bits de los necesarios). {@code -1} si no cuadra ninguno.
     */
    public static int detectBits(int arrayLength, long count, int paletteSize) {
        int expected = bits(paletteSize);
        if (arrayLength == longs(count, expected)) {
            return expected;
        }
        for (int bits = expected + 1; bits <= Integer.SIZE - 1; bits++) {
            if (arrayLength == longs(count, bits)) {
                return bits;
            }
        }
        return -1;
    }

    private static void checkBits(int bits) {
        if (bits < 1 || bits > Integer.SIZE - 1) {
            throw new IllegalArgumentException("Bits por bloque no válidos: " + bits);
        }
    }
}
