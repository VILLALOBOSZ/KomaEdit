package com.komainu.moxia.schematic;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El empaquetado de los bloques de Litematica, comparado con una referencia que escribe bit a bit (sin trucos de
 * desplazamientos): así se comprueba de verdad que las entradas que cruzan de un long al siguiente quedan bien.
 */
class PackedStatesTest {
    private static final int[] PALETTES = {1, 2, 3, 4, 17, 300};

    @Test
    void bitsPerEntryAreAtLeastTwoAndTheCeilingOfLog2() {
        assertEquals(2, PackedStates.bits(1));
        assertEquals(2, PackedStates.bits(2));
        assertEquals(2, PackedStates.bits(3));
        assertEquals(2, PackedStates.bits(4));
        assertEquals(3, PackedStates.bits(5));
        assertEquals(4, PackedStates.bits(16));
        assertEquals(5, PackedStates.bits(17));
        assertEquals(9, PackedStates.bits(300));
        assertEquals(9, PackedStates.bits(512));
        assertEquals(10, PackedStates.bits(513));
    }

    @Test
    void packsLikeABitByBitReferenceForEveryPaletteSize() {
        for (int palette : PALETTES) {
            int bits = PackedStates.bits(palette);
            // 1000 entradas: con 3, 5 y 9 bits muchas cruzan de un long a otro.
            int[] values = values(1000, palette, palette);
            long[] packed = PackedStates.pack(values, bits);
            assertArrayEquals(reference(values, bits), packed, "paleta de " + palette);
            assertEquals(PackedStates.longs(values.length, bits), packed.length);
            assertArrayEquals(values, PackedStates.unpack(packed, values.length, bits, palette), "paleta de " + palette);
        }
    }

    @Test
    void entriesThatCrossTwoLongsAreRead() {
        // 5 bits: la entrada 12 empieza en el bit 60 y acaba en el 64 del siguiente long.
        int[] values = new int[26];
        for (int index = 0; index < values.length; index++) {
            values[index] = (index * 7 + 3) % 17;
        }
        values[12] = 0b10110;
        long[] packed = PackedStates.pack(values, 5);
        assertEquals(3, packed.length);
        assertEquals(0b0110L, packed[0] >>> 60, "los 4 bits bajos de la entrada 12 al final del primer long");
        assertEquals(1L, packed[1] & 1L, "y el bit alto al principio del segundo");
        assertArrayEquals(values, PackedStates.unpack(packed, values.length, 5, 32));
        int crossing = 0;
        for (int index = 0; index < values.length; index++) {
            if ((index * 5) % 64 + 5 > 64) {
                crossing++;
            }
        }
        assertTrue(crossing >= 2, "la prueba tiene que tener entradas que cruzan");
    }

    @Test
    void wrongLengthOrIndexOutsideThePaletteIsRejected() {
        int[] values = values(100, 5, 1);
        long[] packed = PackedStates.pack(values, 3);
        IllegalArgumentException length = assertThrows(IllegalArgumentException.class,
                () -> PackedStates.unpack(Arrays.copyOf(packed, packed.length - 1), 100, 3, 5));
        assertTrue(length.getMessage().contains("no cuadran"), length.getMessage());
        // 3 bits dan para 8 valores: con una paleta de 5, el 7 apunta fuera.
        long[] outside = PackedStates.pack(new int[]{7, 0, 0}, 3);
        IllegalArgumentException index = assertThrows(IllegalArgumentException.class,
                () -> PackedStates.unpack(outside, 3, 3, 5));
        assertTrue(index.getMessage().contains("fuera de la paleta"), index.getMessage());
    }

    @Test
    void detectsArraysWrittenWithMoreBitsThanNeeded() {
        int[] values = values(500, 3, 9);
        assertEquals(2, PackedStates.detectBits(PackedStates.pack(values, 2).length, 500, 3));
        assertEquals(4, PackedStates.detectBits(PackedStates.pack(values, 4).length, 500, 3));
        assertEquals(-1, PackedStates.detectBits(3, 500, 3));
    }

    private static int[] values(int count, int palette, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = index < palette ? index : random.nextInt(palette);
        }
        return values;
    }

    /** Bit a bit: el bit {@code b} de la entrada {@code i} va al bit {@code i·bits + b} del flujo, de menor a mayor. */
    private static long[] reference(int[] values, int bits) {
        long total = (long) values.length * bits;
        long[] data = new long[(int) ((total + 63) / 64)];
        for (int index = 0; index < values.length; index++) {
            for (int bit = 0; bit < bits; bit++) {
                if ((values[index] >> bit & 1) != 0) {
                    long position = (long) index * bits + bit;
                    data[(int) (position / 64)] |= 1L << (position % 64);
                }
            }
        }
        return data;
    }
}
