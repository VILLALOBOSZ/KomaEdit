package com.komainu.moxia.terrain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los sellos llegan como PNG dentro de un {@code .kpack}. El lector de imágenes de Java descomprime entero cada texto
 * comprimido del PNG ({@code zTXt}) al leer los metadatos: un PNG de 1×1 y unos KiB podía traer gigas de texto.
 */
class StampImagesBombTest {
    private static final int TEXT_BYTES = 64 * 1024 * 1024;

    private static void chunk(ByteArrayOutputStream png, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        int length = data.length;
        png.write(length >>> 24);
        png.write(length >>> 16);
        png.write(length >>> 8);
        png.write(length);
        png.writeBytes(name);
        png.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        long value = crc.getValue();
        png.write((int) (value >>> 24));
        png.write((int) (value >>> 16));
        png.write((int) (value >>> 8));
        png.write((int) value);
    }

    private static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(output, new Deflater(9))) {
            deflater.write(data);
        }
        return output.toByteArray();
    }

    /** Un PNG gris de 2×2 con los textos que se le den, uno en {@code tEXt} y otro en {@code zTXt}. */
    private static byte[] png(String text, byte[] compressedText) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(png, "IHDR", new byte[]{0, 0, 0, 2, 0, 0, 0, 2, 8, 0, 0, 0, 0});
        if (text != null) {
            chunk(png, "tEXt", text.getBytes(StandardCharsets.ISO_8859_1));
        }
        if (compressedText != null) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.writeBytes("Comment".getBytes(StandardCharsets.ISO_8859_1));
            body.write(0);
            body.write(0);
            body.writeBytes(deflate(compressedText));
            chunk(png, "zTXt", body.toByteArray());
        }
        chunk(png, "IDAT", deflate(new byte[]{0, (byte) 128, (byte) 128, 0, (byte) 128, (byte) 128}));
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static long allocated() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    @Test
    void aCompressedTextBombIsNotDecompressed() throws IOException {
        byte[] bomb = png(null, new byte[TEXT_BYTES]);
        assertTrue(bomb.length < 256 * 1024, bomb.length + " bytes");
        long before = allocated();
        HeightStamp stamp = StampImages.read("bomba", new ByteArrayInputStream(bomb));
        long spent = allocated() - before;
        assertEquals(2, stamp.width());
        assertTrue(spent < TEXT_BYTES / 4, "se reservaron " + spent / (1024 * 1024) + " MiB para leer un 2×2");
    }

    @Test
    void theNeutralGreyIsStillReadFromItsText() throws IOException {
        HeightStamp stamp = StampImages.read("neutro", new ByteArrayInputStream(png(StampImages.NEUTRAL_KEY
                + "\0" + "0.2500", null)));
        assertEquals(0.25, stamp.neutral(), 1e-9);
        assertEquals(0.0, StampImages.read("raro", new ByteArrayInputStream(png(StampImages.NEUTRAL_KEY + "\0NaN",
                null))).neutral(), "lo que no es un número no vale");
        assertEquals(1.0, StampImages.read("alto", new ByteArrayInputStream(png(StampImages.NEUTRAL_KEY + "\0" + "7",
                null))).neutral());
    }
}
