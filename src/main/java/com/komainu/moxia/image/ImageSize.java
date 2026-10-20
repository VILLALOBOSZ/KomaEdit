package com.komainu.moxia.image;

/**
 * Medida de la obra en bloques. Con el ancho o el alto, la otra medida sale de la proporción de la imagen; con los
 * dos se estira; sin ninguno, la imagen conserva su tamaño hasta {@code defaultLongest} bloques en su lado largo.
 */
public record ImageSize(int width, int height) {
    public ImageSize {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("La obra tiene que medir al menos 1×1 bloques");
        }
    }

    public long blocks() {
        return (long) width * height;
    }

    /**
     * @param width  ancho pedido o {@code null}
     * @param height alto pedido o {@code null}
     */
    public static ImageSize of(int imageWidth, int imageHeight, Integer width, Integer height, int defaultLongest,
                               int maxSide) {
        ImageSize size;
        if (width != null && height != null) {
            size = new ImageSize(width, height);
        } else if (width != null) {
            size = new ImageSize(width, proportional(width, imageHeight, imageWidth));
        } else if (height != null) {
            size = new ImageSize(proportional(height, imageWidth, imageHeight), height);
        } else {
            int longest = Math.max(imageWidth, imageHeight);
            if (longest <= defaultLongest) {
                size = new ImageSize(imageWidth, imageHeight);
            } else if (imageWidth >= imageHeight) {
                size = new ImageSize(defaultLongest, proportional(defaultLongest, imageHeight, imageWidth));
            } else {
                size = new ImageSize(proportional(defaultLongest, imageWidth, imageHeight), defaultLongest);
            }
        }
        return size.checked(maxSide);
    }

    /**
     * Lo más grande que cabe en una caja de {@code boxWidth×boxHeight} sin deformar la imagen, o la caja entera con
     * {@code stretch}.
     */
    public static ImageSize fit(int imageWidth, int imageHeight, int boxWidth, int boxHeight, boolean stretch,
                                int maxSide) {
        if (stretch) {
            return new ImageSize(boxWidth, boxHeight).checked(maxSide);
        }
        // Compara las proporciones en enteros para no depender del redondeo.
        ImageSize size = (long) imageWidth * boxHeight >= (long) imageHeight * boxWidth
                ? new ImageSize(boxWidth, Math.min(boxHeight, proportional(boxWidth, imageHeight, imageWidth)))
                : new ImageSize(Math.min(boxWidth, proportional(boxHeight, imageWidth, imageHeight)), boxHeight);
        return size.checked(maxSide);
    }

    /** {@code value × numerator / denominator} redondeado, al menos 1. */
    static int proportional(int value, int numerator, int denominator) {
        return (int) Math.max(1L, Math.round((double) value * numerator / denominator));
    }

    private ImageSize checked(int maxSide) {
        if (width > maxSide || height > maxSide) {
            throw new IllegalArgumentException("La obra saldría de " + width + "×" + height
                    + " bloques; el lado máximo del servidor es " + maxSide);
        }
        return this;
    }

    /**
     * Cuántos píxeles de la imagen se pueden saltar al decodificar sin perder detalle: deja al menos cuatro por
     * bloque en cada eje.
     */
    public static int subsampling(int imageWidth, int imageHeight, ImageSize size) {
        int horizontal = imageWidth / Math.max(1, size.width() * 4);
        int vertical = imageHeight / Math.max(1, size.height() * 4);
        return Math.max(1, Math.min(horizontal, vertical));
    }

    @Override
    public String toString() {
        return width + "×" + height;
    }
}
