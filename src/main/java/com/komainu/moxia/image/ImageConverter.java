package com.komainu.moxia.image;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * El cálculo entero de {@code /komaedit image} sobre los bytes del archivo, sin tocar Minecraft: cabecera y límites,
 * medida de la obra, decodificación (saltando píxeles si sobran), reescalado, brillo y contraste, y bloques. Corre
 * en un worker.
 */
public final class ImageConverter {
    /** Conexión de un panel hacia la derecha de la obra. */
    public static final int CONNECT_RIGHT = 1;
    /** Conexión de un panel hacia la izquierda de la obra. */
    public static final int CONNECT_LEFT = 2;

    private ImageConverter() {
    }

    /**
     * @param header  formato y medidas de la imagen original
     * @param size    medida de la obra en bloques
     * @param scaled  la imagen ya a esa medida y ajustada
     * @param blocks  el bloque de cada píxel
     */
    public record Converted(ImageDecoder.Header header, ImageSize size, RgbaImage scaled,
                            ImageQuantizer.Result blocks) {
    }

    public static Converted convert(byte[] data, long maxPixels, Function<ImageDecoder.Header, ImageSize> sizing,
                                    int brightness, int contrast, BlockPalette palette, Dithering dithering,
                                    int alphaThreshold, BooleanSupplier cancelled) {
        ImageDecoder.Header header = ImageDecoder.probe(data, maxPixels);
        ImageSize size = sizing.apply(header);
        int subsampling = ImageSize.subsampling(header.width(), header.height(), size);
        RgbaImage image = ImageDecoder.decode(data, maxPixels, subsampling);
        checkCancelled(cancelled);
        RgbaImage scaled = ImageFilters.adjust(ImageResampler.resample(image, size.width(), size.height(),
                cancelled), brightness, contrast);
        checkCancelled(cancelled);
        ImageQuantizer.Result blocks = ImageQuantizer.quantize(scaled, palette, dithering, alphaThreshold, cancelled);
        if (blocks.opaque() == 0) {
            throw new IllegalArgumentException("La imagen es transparente entera con ese umbral (-u)");
        }
        return new Converted(header, size, scaled, blocks);
    }

    /**
     * Para cada píxel, {@code -1} si es transparente o {@code índice × 4 + conexiones}. Un panel se une a los vecinos
     * opacos de su fila ({@link #CONNECT_RIGHT}, {@link #CONNECT_LEFT}); los demás bloques no llevan conexiones.
     */
    public static int[] cells(ImageQuantizer.Result blocks, boolean[] panes) {
        int width = blocks.width();
        int[] indices = blocks.indices();
        int[] cells = new int[indices.length];
        for (int index = 0; index < indices.length; index++) {
            int chosen = indices[index];
            if (chosen < 0) {
                cells[index] = -1;
                continue;
            }
            int mask = 0;
            if (panes[chosen]) {
                int x = index % width;
                if (x + 1 < width && indices[index + 1] >= 0) {
                    mask |= CONNECT_RIGHT;
                }
                if (x > 0 && indices[index - 1] >= 0) {
                    mask |= CONNECT_LEFT;
                }
            }
            cells[index] = chosen * 4 + mask;
        }
        return cells;
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException();
        }
    }
}
