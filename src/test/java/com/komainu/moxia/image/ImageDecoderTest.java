package com.komainu.moxia.image;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageDecoderTest {
    @Test
    void readsPngWithItsTransparency() {
        RgbaImage image = TestImages.scene(40, 30);
        byte[] png = TestImages.encode(image, "png");
        assertEquals(ImageDecoder.Format.PNG, ImageDecoder.sniff(png));
        RgbaImage decoded = ImageDecoder.decode(png, 10_000);
        assertEquals(40, decoded.width());
        assertEquals(30, decoded.height());
        for (int index = 0; index < image.argb().length; index++) {
            int expected = image.argb()[index];
            int actual = decoded.argb()[index];
            assertEquals(RgbaImage.alpha(expected), RgbaImage.alpha(actual));
            if (RgbaImage.alpha(expected) == 255) {
                assertEquals(expected, actual);
            }
        }
        assertEquals(0, RgbaImage.alpha(decoded.get(0, 0)), "esquina redondeada transparente");
    }

    @Test
    void readsJpegApproximately() {
        RgbaImage image = ImageQuantizerTest.flat(24, 16, 0xFF3366CC);
        byte[] jpeg = TestImages.encode(image, "jpg");
        assertEquals(ImageDecoder.Format.JPEG, ImageDecoder.sniff(jpeg));
        RgbaImage decoded = ImageDecoder.decode(jpeg, 10_000);
        int pixel = decoded.get(12, 8);
        assertEquals(255, RgbaImage.alpha(pixel));
        assertEquals(0x33, pixel >> 16 & 0xFF, 6);
        assertEquals(0x66, pixel >> 8 & 0xFF, 6);
        assertEquals(0xCC, pixel & 0xFF, 6);
    }

    @Test
    void readsTheFirstFrameOfAGif() {
        RgbaImage image = TestImages.icon(17);
        byte[] gif = TestImages.encode(image, "gif");
        assertEquals(ImageDecoder.Format.GIF, ImageDecoder.sniff(gif));
        RgbaImage decoded = ImageDecoder.decode(gif, 10_000);
        assertEquals(17, decoded.width());
        assertEquals(RgbaImage.rgb(image.get(8, 8)), RgbaImage.rgb(decoded.get(8, 8)));
    }

    @Test
    void recognisesTheFormatByItsBytesNotItsName() {
        assertNull(ImageDecoder.sniff("<html>no soy una imagen</html>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageDecoder.sniff(new byte[0]));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ImageDecoder.decode("GIF8 casi".getBytes(StandardCharsets.UTF_8), 10_000));
        assertTrue(error.getMessage().contains("no es una imagen"), error.getMessage());
    }

    @Test
    void refusesHugeImagesFromTheHeaderAlone() {
        // Una cabecera PNG que dice 60 000 × 60 000 sin ningún dato detrás: se rechaza sin decodificar.
        byte[] bomb = pngHeader(60_000, 60_000);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ImageDecoder.probe(bomb, 2048L * 2048));
        assertTrue(error.getMessage().contains("lado máximo"), error.getMessage());
        byte[] large = pngHeader(3000, 3000);
        error = assertThrows(IllegalArgumentException.class, () -> ImageDecoder.decode(large, 2048L * 2048));
        assertTrue(error.getMessage().contains("9000000 píxeles"), error.getMessage());
        ImageDecoder.Header header = ImageDecoder.probe(large, 3000L * 3000);
        assertEquals(3000, header.width());
    }

    @Test
    void truncatedFilesFailCleanly() {
        byte[] png = TestImages.encode(TestImages.scene(64, 64), "png");
        byte[] cut = java.util.Arrays.copyOf(png, png.length / 3);
        assertThrows(IllegalArgumentException.class, () -> ImageDecoder.decode(cut, 10_000));
    }

    @Test
    void subsamplingReadsFewerPixels() {
        byte[] png = TestImages.encode(TestImages.scene(120, 80), "png");
        RgbaImage decoded = ImageDecoder.decode(png, 100_000, 4);
        assertEquals(30, decoded.width());
        assertEquals(20, decoded.height());
    }

    /** Firma PNG, IHDR con las medidas dadas (RGBA de 8 bits) e IEND, con CRC correctos. */
    static byte[] pngHeader(int width, int height) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 6)
                .put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(output, "IHDR", ihdr.array());
        chunk(output, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream output, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        output.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        output.writeBytes(name);
        output.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        output.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
