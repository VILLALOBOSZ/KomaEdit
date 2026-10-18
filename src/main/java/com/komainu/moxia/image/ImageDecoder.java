package com.komainu.moxia.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Lee PNG, JPG y GIF (de un GIF animado, el primer fotograma) con {@code javax.imageio}, sin caché en disco.
 *
 * <p>El formato se reconoce por los primeros bytes, no por la extensión. Antes de decodificar se leen solo las
 * medidas de la cabecera ({@link #probe}): una imagen que declare más píxeles de los permitidos se rechaza sin
 * reservar memoria para ella. Si sobra resolución, se decodifica saltando píxeles ({@link #decode} con
 * {@code subsampling}), así una foto grande para una vidriera pequeña cuesta poco.</p>
 */
public final class ImageDecoder {
    /** Lado máximo que se acepta aunque el total de píxeles quepa (evita imágenes de 1×4 000 000). */
    public static final int MAX_SIDE = 16_384;

    static {
        ImageIO.setUseCache(false);
    }

    private ImageDecoder() {
    }

    public enum Format {
        PNG("png"),
        JPEG("jpeg"),
        GIF("gif");

        private final String reader;

        Format(String reader) {
            this.reader = reader;
        }

        public String displayName() {
            return name();
        }
    }

    /** Formato y medidas de la cabecera. */
    public record Header(Format format, int width, int height) {
        public long pixels() {
            return (long) width * height;
        }
    }

    /** Reconoce el formato por su firma; {@code null} si no es PNG, JPG ni GIF. */
    public static Format sniff(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return Format.PNG;
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
                && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
            return Format.GIF;
        }
        return null;
    }

    /** Lee solo la cabecera y comprueba los límites. */
    public static Header probe(byte[] data, long maxPixels) {
        Format format = sniff(data);
        if (format == null) {
            throw new IllegalArgumentException("El archivo no es una imagen PNG, JPG ni GIF");
        }
        Header header;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = reader(format, input);
            try {
                header = new Header(format, reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("No se pudo leer la cabecera de la imagen " + format.displayName()
                    + ": " + reason(exception));
        }
        check(header, maxPixels);
        return header;
    }

    static void check(Header header, long maxPixels) {
        if (header.width() < 1 || header.height() < 1) {
            throw new IllegalArgumentException("La imagen no tiene píxeles");
        }
        if (header.width() > MAX_SIDE || header.height() > MAX_SIDE) {
            throw new IllegalArgumentException("La imagen mide " + header.width() + "×" + header.height()
                    + " píxeles; el lado máximo es " + MAX_SIDE);
        }
        if (header.pixels() > maxPixels) {
            throw new IllegalArgumentException("La imagen tiene " + header.pixels() + " píxeles ("
                    + header.width() + "×" + header.height() + "); el máximo del servidor es " + maxPixels);
        }
    }

    /** Decodifica la primera imagen con los límites de {@link #probe}. */
    public static RgbaImage decode(byte[] data, long maxPixels) {
        return decode(data, maxPixels, 1);
    }

    /**
     * Decodifica tomando uno de cada {@code subsampling} píxeles en cada eje (1 = todos). Las medidas se comprueban
     * antes con la cabecera.
     */
    public static RgbaImage decode(byte[] data, long maxPixels, int subsampling) {
        Header header = probe(data, maxPixels);
        int step = Math.max(1, subsampling);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = reader(header.format(), input);
            try {
                ImageReadParam param = reader.getDefaultReadParam();
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                BufferedImage image = reader.read(0, param);
                int width = image.getWidth();
                int height = image.getHeight();
                int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
                return new RgbaImage(width, height, argb);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("No se pudo decodificar la imagen " + header.format().displayName()
                    + ": " + reason(exception));
        }
    }

    private static ImageReader reader(Format format, ImageInputStream input) throws IOException {
        if (input == null) {
            throw new IOException("sin datos");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.reader);
        if (!readers.hasNext()) {
            throw new IOException("Java no trae lector de " + format.displayName().toLowerCase(Locale.ROOT));
        }
        ImageReader reader = readers.next();
        reader.setInput(input, true, true);
        return reader;
    }

    private static String reason(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
