package com.komainu.moxia.image;

/**
 * Brillo y contraste, en porcentaje de −100 a 100, aplicados en sRGB antes de buscar los bloques. El contraste
 * escala la distancia a 128 por {@code ((100 + contraste) / 100)²} (−100 deja un gris, 100 la multiplica por
 * cuatro) y el brillo suma {@code brillo × 2,55}. El alfa no cambia.
 */
public final class ImageFilters {
    public static final int MIN = -100;
    public static final int MAX = 100;

    private ImageFilters() {
    }

    public static RgbaImage adjust(RgbaImage image, int brightness, int contrast) {
        if (brightness < MIN || brightness > MAX || contrast < MIN || contrast > MAX) {
            throw new IllegalArgumentException("El brillo y el contraste van de " + MIN + " a " + MAX);
        }
        if (brightness == 0 && contrast == 0) {
            return image;
        }
        int[] lookup = lookup(brightness, contrast);
        int[] source = image.argb();
        int[] result = new int[source.length];
        for (int index = 0; index < source.length; index++) {
            int pixel = source[index];
            result[index] = pixel & 0xFF000000 | lookup[pixel >> 16 & 0xFF] << 16 | lookup[pixel >> 8 & 0xFF] << 8
                    | lookup[pixel & 0xFF];
        }
        return new RgbaImage(image.width(), image.height(), result);
    }

    static int[] lookup(int brightness, int contrast) {
        double factor = Math.pow((100.0 + contrast) / 100.0, 2.0);
        int[] lookup = new int[256];
        for (int value = 0; value < 256; value++) {
            double adjusted = (value - 128.0) * factor + 128.0 + brightness * 2.55;
            lookup[value] = (int) Math.round(Math.clamp(adjusted, 0.0, 255.0));
        }
        return lookup;
    }
}
