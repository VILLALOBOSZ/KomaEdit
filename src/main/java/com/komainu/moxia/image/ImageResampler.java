package com.komainu.moxia.image;

import java.util.function.BooleanSupplier;

/**
 * Cambia el tamaño de una imagen al de la obra en bloques.
 *
 * <p>Al reducir, cada bloque es la media de los píxeles que cubre (por área, con alfa premultiplicado: un borde
 * transparente no oscurece el color). Al ampliar, cada bloque toma el píxel que tiene debajo, así el pixel art sigue
 * nítido. Cada eje se decide por separado.</p>
 */
public final class ImageResampler {
    private ImageResampler() {
    }

    public static RgbaImage resample(RgbaImage source, int width, int height) {
        return resample(source, width, height, () -> false);
    }

    public static RgbaImage resample(RgbaImage source, int width, int height, BooleanSupplier cancelled) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("La obra tiene que medir al menos 1×1 bloques");
        }
        if (width == source.width() && height == source.height()) {
            return new RgbaImage(width, height, source.argb().clone());
        }
        Weights columns = Weights.of(source.width(), width);
        Weights rows = Weights.of(source.height(), height);
        // Paso horizontal: source.height() filas de «width» columnas, premultiplicado.
        int sourceHeight = source.height();
        float[] red = new float[width * sourceHeight];
        float[] green = new float[red.length];
        float[] blue = new float[red.length];
        float[] alpha = new float[red.length];
        for (int y = 0; y < sourceHeight; y++) {
            if ((y & 63) == 0 && cancelled.getAsBoolean()) {
                throw new java.util.concurrent.CancellationException();
            }
            int row = y * source.width();
            for (int x = 0; x < width; x++) {
                float r = 0;
                float g = 0;
                float b = 0;
                float a = 0;
                for (int k = columns.start[x]; k < columns.start[x + 1]; k++) {
                    int pixel = source.argb()[row + columns.index[k]];
                    float weight = columns.weight[k] * (pixel >>> 24) / 255.0F;
                    r += weight * (pixel >> 16 & 0xFF);
                    g += weight * (pixel >> 8 & 0xFF);
                    b += weight * (pixel & 0xFF);
                    a += weight;
                }
                int index = y * width + x;
                red[index] = r;
                green[index] = g;
                blue[index] = b;
                alpha[index] = a;
            }
        }
        RgbaImage result = RgbaImage.blank(width, height);
        for (int y = 0; y < height; y++) {
            if ((y & 63) == 0 && cancelled.getAsBoolean()) {
                throw new java.util.concurrent.CancellationException();
            }
            for (int x = 0; x < width; x++) {
                float r = 0;
                float g = 0;
                float b = 0;
                float a = 0;
                for (int k = rows.start[y]; k < rows.start[y + 1]; k++) {
                    int index = rows.index[k] * width + x;
                    float weight = rows.weight[k];
                    r += weight * red[index];
                    g += weight * green[index];
                    b += weight * blue[index];
                    a += weight * alpha[index];
                }
                result.set(x, y, a <= 1.0E-6F ? 0 : RgbaImage.argb(channel(a * 255.0F), channel(r / a),
                        channel(g / a), channel(b / a)));
            }
        }
        return result;
    }

    private static int channel(float value) {
        return Math.clamp(Math.round(value), 0, 255);
    }

    /** Pesos de un eje: para cada píxel de destino, los de origen que cubre y cuánto (suman 1). */
    static final class Weights {
        final int[] start;
        final int[] index;
        final float[] weight;

        private Weights(int[] start, int[] index, float[] weight) {
            this.start = start;
            this.index = index;
            this.weight = weight;
        }

        static Weights of(int sourceSize, int targetSize) {
            double scale = sourceSize / (double) targetSize;
            int[] start = new int[targetSize + 1];
            if (scale <= 1.0) {
                // Ampliar (o igual): el píxel de debajo del centro del bloque.
                int[] index = new int[targetSize];
                float[] weight = new float[targetSize];
                for (int target = 0; target < targetSize; target++) {
                    start[target] = target;
                    index[target] = Math.min(sourceSize - 1, (int) Math.floor((target + 0.5) * scale));
                    weight[target] = 1.0F;
                }
                start[targetSize] = targetSize;
                return new Weights(start, index, weight);
            }
            int capacity = targetSize * ((int) Math.ceil(scale) + 2);
            int[] index = new int[capacity];
            float[] weight = new float[capacity];
            int count = 0;
            for (int target = 0; target < targetSize; target++) {
                start[target] = count;
                double from = target * scale;
                double to = Math.min(sourceSize, (target + 1) * scale);
                for (int source = (int) Math.floor(from); source < to; source++) {
                    double overlap = Math.min(to, source + 1) - Math.max(from, source);
                    if (overlap > 1.0E-9) {
                        index[count] = source;
                        weight[count] = (float) (overlap / scale);
                        count++;
                    }
                }
            }
            start[targetSize] = count;
            return new Weights(start, index, weight);
        }
    }
}
