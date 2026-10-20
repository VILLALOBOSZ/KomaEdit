package com.komainu.moxia.terrain;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Locale;

/**
 * Lee y guarda sellos como PNG. Vale cualquier PNG en grises (8 o 16 bits) o en color (se toma el brillo); la
 * transparencia deja el terreno como está. El gris neutro se guarda en un texto del PNG ({@code komaedit-neutral})
 * para los sellos que hunden, y la orden puede cambiarlo con {@code -c}.
 */
public final class StampImages {
    static final String NEUTRAL_KEY = "komaedit-neutral";
    private static final String PNG_FORMAT = "javax_imageio_png_1.0";

    private StampImages() {
    }

    public static HeightStamp read(String name, InputStream input) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) {
                throw new IOException("No se puede leer la imagen");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("El archivo no es una imagen que se pueda leer (usa PNG)");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, false);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > HeightStamp.MAX_SIZE || height > HeightStamp.MAX_SIZE) {
                    throw new IOException("La imagen mide " + width + "×" + height + "; el máximo es "
                            + HeightStamp.MAX_SIZE + "×" + HeightStamp.MAX_SIZE);
                }
                double neutral = neutral(reader.getImageMetadata(0));
                BufferedImage image = reader.read(0);
                return fromImage(name, image, neutral);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Pasa una imagen a sello. */
    public static HeightStamp fromImage(String name, BufferedImage image, double neutral) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[] values = new float[width * height];
        Raster raster = image.getRaster();
        boolean gray = raster.getNumBands() <= 2 && image.getColorModel().getColorSpace().getNumComponents() == 1;
        boolean alpha = image.getColorModel().hasAlpha();
        int bits = image.getColorModel().getComponentSize(0);
        double maximum = (1L << bits) - 1.0;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                double value;
                double opacity = 1.0;
                if (gray) {
                    value = raster.getSample(column, row, 0) / maximum;
                    if (alpha && raster.getNumBands() == 2) {
                        opacity = raster.getSample(column, row, 1) / ((1L << image.getColorModel().getComponentSize(1))
                                - 1.0);
                    }
                } else {
                    int argb = image.getRGB(column, row);
                    double red = (argb >> 16) & 0xFF;
                    double green = (argb >> 8) & 0xFF;
                    double blue = argb & 0xFF;
                    value = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;
                    if (alpha) {
                        opacity = ((argb >>> 24) & 0xFF) / 255.0;
                    }
                }
                value = neutral + (value - neutral) * opacity;
                values[row * width + column] = (float) Math.clamp(value, 0.0, 1.0);
            }
        }
        return new HeightStamp(name, width, height, values, neutral);
    }

    /** Guarda el sello como PNG en grises de 16 bits, con su gris neutro. */
    public static void write(HeightStamp stamp, OutputStream output) throws IOException {
        write(stamp, output, 16);
    }

    /**
     * Guarda el sello como PNG en grises de 16 bits con {@code bits} de precisión (de 8 a 16): los bits que sobran
     * van a cero y el PNG comprime mucho mejor. Con 12 bits la altura se guarda a 1/4096, de sobra para bloques.
     */
    public static void write(HeightStamp stamp, OutputStream output, int bits) throws IOException {
        if (bits < 8 || bits > 16) {
            throw new IllegalArgumentException("La precisión del PNG va de 8 a 16 bits");
        }
        int levels = (1 << bits) - 1;
        int shift = 16 - bits;
        BufferedImage image = new BufferedImage(stamp.width(), stamp.height(), BufferedImage.TYPE_USHORT_GRAY);
        var raster = image.getRaster();
        for (int row = 0; row < stamp.height(); row++) {
            for (int column = 0; column < stamp.width(); column++) {
                int level = (int) Math.round(Math.clamp(stamp.pixel(column, row), 0.0, 1.0) * levels);
                // El blanco se guarda como 65535 para que el gris neutro de los que hunden siga siendo exacto.
                int value = level == levels ? 65_535 : level << shift;
                raster.setSample(column, row, 0, value);
            }
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("Esta Java no sabe escribir PNG");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),
                    parameters);
            if (stamp.neutral() != 0.0) {
                IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
                entry.setAttribute("keyword", NEUTRAL_KEY);
                entry.setAttribute("value", String.format(Locale.ROOT, "%.4f", stamp.neutral()));
                IIOMetadataNode text = new IIOMetadataNode("tEXt");
                text.appendChild(entry);
                IIOMetadataNode root = new IIOMetadataNode(PNG_FORMAT);
                root.appendChild(text);
                metadata.mergeTree(PNG_FORMAT, root);
            }
            writer.write(null, new IIOImage(image, null, metadata), parameters);
        } finally {
            writer.dispose();
        }
    }

    private static double neutral(IIOMetadata metadata) {
        if (metadata == null || !java.util.Arrays.asList(metadata.getMetadataFormatNames()).contains(PNG_FORMAT)) {
            return 0.0;
        }
        Node root = metadata.getAsTree(PNG_FORMAT);
        for (Node chunk = root.getFirstChild(); chunk != null; chunk = chunk.getNextSibling()) {
            if (!chunk.getNodeName().equals("tEXt")) {
                continue;
            }
            for (Node entry = chunk.getFirstChild(); entry != null; entry = entry.getNextSibling()) {
                NamedNodeMap attributes = entry.getAttributes();
                if (attributes == null || attributes.getNamedItem("keyword") == null) {
                    continue;
                }
                if (NEUTRAL_KEY.equals(attributes.getNamedItem("keyword").getNodeValue())) {
                    try {
                        double value = Double.parseDouble(attributes.getNamedItem("value").getNodeValue());
                        return Math.clamp(value, 0.0, 1.0);
                    } catch (NumberFormatException | NullPointerException exception) {
                        return 0.0;
                    }
                }
            }
        }
        return 0.0;
    }
}
