package com.komainu.moxia.color;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkLabTest {
    @Test
    void oklabMatchesTheReferenceValues() {
        // Valores de referencia de Björn Ottosson para sRGB puro.
        assertArrayEquals(new double[]{1.0, 0.0, 0.0}, OkLab.of(0xFFFFFF), 1.0E-4);
        assertArrayEquals(new double[]{0.0, 0.0, 0.0}, OkLab.of(0x000000), 1.0E-9);
        assertArrayEquals(new double[]{0.62796, 0.22486, 0.12585}, OkLab.of(0xFF0000), 1.0E-4);
        assertArrayEquals(new double[]{0.86644, -0.23389, 0.17950}, OkLab.of(0x00FF00), 1.0E-4);
        assertArrayEquals(new double[]{0.45201, -0.03246, -0.31153}, OkLab.of(0x0000FF), 1.0E-4);
    }

    @Test
    void cielabMatchesTheReferenceValues() {
        assertArrayEquals(new double[]{100.0, 0.0, 0.0}, OkLab.cielab(0xFFFFFF), 0.01);
        assertArrayEquals(new double[]{53.2408, 80.0925, 67.2032}, OkLab.cielab(0xFF0000), 0.01);
        assertArrayEquals(new double[]{32.3026, 79.1967, -107.8637}, OkLab.cielab(0x0000FF), 0.01);
        double[] gray = OkLab.cielab(0x777777);
        assertEquals(0.0, gray[1], 1.0E-3);
        assertEquals(0.0, gray[2], 1.0E-3);
    }

    @Test
    void roundTripKeepsTheColor() {
        SplittableRandom random = new SplittableRandom(4L);
        for (int sample = 0; sample < 2_000; sample++) {
            int rgb = random.nextInt(0x1000000);
            assertEquals(rgb, OkLab.rgb(OkLab.of(rgb)), OkLab.hex(rgb));
        }
    }

    @Test
    void lightnessFollowsPerceptionAndGraysHaveNoChroma() {
        assertTrue(OkLab.lightness(0x101010) < OkLab.lightness(0x808080));
        assertTrue(OkLab.lightness(0x808080) < OkLab.lightness(0xF0F0F0));
        // El amarillo puro se ve mucho más claro que el azul puro.
        assertTrue(OkLab.lightness(0xFFFF00) > OkLab.lightness(0x0000FF) + 0.4);
        assertEquals(0.0, OkLab.chroma(OkLab.of(0x777777)), 1.0E-4);
        assertTrue(OkLab.chroma(OkLab.of(0xFF0000)) > 0.2);
    }

    @Test
    void midpointOfAStraightGradientDoesNotGoGray() {
        double[] blue = OkLab.of(0x0000FF);
        double[] yellow = OkLab.of(0xFFFF00);
        double[] middle = OkLab.lerp(blue, yellow, 0.5);
        assertEquals((blue[0] + yellow[0]) / 2.0, middle[0], 1.0E-12);
        assertEquals(OkLab.distance(blue, middle), OkLab.distance(middle, yellow), 1.0E-12);
    }

    @Test
    void parsesHexOnlyWithAPrefix() {
        assertEquals(0x3A7FD2, OkLab.parseHex("#3A7FD2"));
        assertEquals(0xAABBCC, OkLab.parseHex("#abc"));
        assertEquals("#3A7FD2", OkLab.hex(0x3A7FD2));
        assertTrue(OkLab.isHex("#3a7fd2"));
        assertTrue(OkLab.isHex("0x3A7FD2"));
        // «bed» también es hexadecimal de tres cifras: sin # es un bloque.
        assertFalse(OkLab.isHex("bed"));
        assertFalse(OkLab.isHex("#existing"));
        assertThrows(IllegalArgumentException.class, () -> OkLab.parseHex("#12"));
    }
}
