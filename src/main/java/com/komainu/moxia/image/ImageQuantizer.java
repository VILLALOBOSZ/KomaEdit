package com.komainu.moxia.image;

import com.komainu.moxia.shape.smooth.ColorMatch;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.function.BooleanSupplier;

/**
 * Elige un bloque de la paleta para cada píxel, con o sin tramado. Los píxeles con alfa por debajo del umbral quedan
 * transparentes ({@code -1}); el tramado no les pasa error ni lo recibe de ellos.
 *
 * <p>El error de Floyd–Steinberg se reparte en sRGB y recorre las filas en serpentina. El tramado ordenado usa la
 * mezcla de Knoll ({@link #mixingPlan}) y la matriz de Bayer de 8×8. La comparación siempre es en CIELAB.</p>
 */
public final class ImageQuantizer {
    private static final int[] BAYER = {
            0, 32, 8, 40, 2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37,
            63, 31, 55, 23, 61, 29, 53, 21
    };

    /** Candidatos de cada mezcla del tramado ordenado. */
    static final int PLAN_SIZE = 16;

    private ImageQuantizer() {
    }

    /**
     * @param indices    índice de la paleta por píxel, o {@code -1} si es transparente
     * @param counts     cuántos píxeles usan cada bloque
     * @param meanError  ΔE medio «visto de lejos»: entre la media de 3×3 de la imagen y la de los bloques
     *                   elegidos, así el tramado cuenta a favor y no en contra
     */
    public record Result(int width, int height, int[] indices, int[] counts, int transparent, double meanError) {
        public int index(int x, int y) {
            return indices[y * width + x];
        }

        public int opaque() {
            return indices.length - transparent;
        }

        /** Bloques distintos usados. */
        public int distinct() {
            int used = 0;
            for (int count : counts) {
                if (count > 0) {
                    used++;
                }
            }
            return used;
        }
    }

    public static Result quantize(RgbaImage image, BlockPalette palette, Dithering dithering, int alphaThreshold) {
        return quantize(image, palette, dithering, alphaThreshold, () -> false);
    }

    /** @param alphaThreshold alfa mínimo (1–255) para que un píxel cuente como opaco */
    public static Result quantize(RgbaImage image, BlockPalette palette, Dithering dithering, int alphaThreshold,
                                  BooleanSupplier cancelled) {
        int threshold = Math.clamp(alphaThreshold, 1, 255);
        int width = image.width();
        int height = image.height();
        int[] pixels = image.argb();
        int[] indices = new int[pixels.length];
        Nearest nearest = new Nearest(palette);
        switch (dithering) {
            case NONE -> {
                for (int index = 0; index < pixels.length; index++) {
                    if ((index & 4095) == 0) {
                        checkCancelled(cancelled);
                    }
                    int pixel = pixels[index];
                    indices[index] = pixel >>> 24 < threshold ? -1 : nearest.of(pixel & 0xFFFFFF);
                }
            }
            case BAYER -> {
                Int2ObjectOpenHashMap<int[]> plans = new Int2ObjectOpenHashMap<>();
                for (int y = 0; y < height; y++) {
                    checkCancelled(cancelled);
                    for (int x = 0; x < width; x++) {
                        int index = y * width + x;
                        int pixel = pixels[index];
                        if (pixel >>> 24 < threshold) {
                            indices[index] = -1;
                            continue;
                        }
                        int[] plan = plans.computeIfAbsent(pixel & 0xFFFFFF,
                                (int color) -> mixingPlan(color, palette, nearest));
                        indices[index] = plan[BAYER[(y & 7) * 8 + (x & 7)] * PLAN_SIZE / 64];
                    }
                }
            }
            case FLOYD_STEINBERG -> floydSteinberg(pixels, width, height, threshold, palette, nearest, indices,
                    cancelled);
        }
        int[] counts = new int[palette.size()];
        int transparent = 0;
        for (int chosen : indices) {
            if (chosen < 0) {
                transparent++;
            } else {
                counts[chosen]++;
            }
        }
        return new Result(width, height, indices, counts, transparent, viewedError(pixels, indices, palette, width,
                height));
    }

    /** ΔE medio entre las medias de 3×3 (solo píxeles opacos) de la imagen y de los bloques. */
    static double viewedError(int[] pixels, int[] indices, BlockPalette palette, int width, int height) {
        double total = 0.0;
        int counted = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (indices[y * width + x] < 0) {
                    continue;
                }
                int[] original = new int[3];
                int[] blocks = new int[3];
                int samples = 0;
                for (int dy = Math.max(0, y - 1); dy <= Math.min(height - 1, y + 1); dy++) {
                    for (int dx = Math.max(0, x - 1); dx <= Math.min(width - 1, x + 1); dx++) {
                        int index = dy * width + dx;
                        int chosen = indices[index];
                        if (chosen < 0) {
                            continue;
                        }
                        add(original, pixels[index]);
                        add(blocks, palette.rgb(chosen));
                        samples++;
                    }
                }
                total += Math.sqrt(ColorMatch.distanceSquared(average(original, samples), average(blocks, samples)));
                counted++;
            }
        }
        return counted == 0 ? 0.0 : total / counted;
    }

    private static void add(int[] sum, int color) {
        sum[0] += color >> 16 & 0xFF;
        sum[1] += color >> 8 & 0xFF;
        sum[2] += color & 0xFF;
    }

    private static int average(int[] sum, int samples) {
        return ColorMatch.rgb(Math.round(sum[0] / (float) samples), Math.round(sum[1] / (float) samples),
                Math.round(sum[2] / (float) samples));
    }

    /**
     * Mezcla de Knoll para el tramado ordenado: {@link #PLAN_SIZE} bloques cuya media se acerca al color (cada uno
     * corrige el error acumulado de los anteriores), ordenados de oscuro a claro. La matriz de Bayer elige uno según
     * la posición, así un color entre dos tonos se trama con los dos y no solo con más o menos brillo.
     */
    static int[] mixingPlan(int color, BlockPalette palette, Nearest nearest) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int[] plan = new int[PLAN_SIZE];
        double errorRed = 0;
        double errorGreen = 0;
        double errorBlue = 0;
        for (int step = 0; step < PLAN_SIZE; step++) {
            int chosen = nearest.of(rgb(red + errorRed, green + errorGreen, blue + errorBlue));
            plan[step] = chosen;
            int candidate = palette.rgb(chosen);
            errorRed += red - (candidate >> 16 & 0xFF);
            errorGreen += green - (candidate >> 8 & 0xFF);
            errorBlue += blue - (candidate & 0xFF);
        }
        Integer[] sorted = new Integer[PLAN_SIZE];
        for (int step = 0; step < PLAN_SIZE; step++) {
            sorted[step] = plan[step];
        }
        java.util.Arrays.sort(sorted, java.util.Comparator.comparingDouble((Integer index) -> luma(palette.rgb(index)))
                .thenComparingInt(index -> index));
        for (int step = 0; step < PLAN_SIZE; step++) {
            plan[step] = sorted[step];
        }
        return plan;
    }

    private static double luma(int color) {
        return 0.299 * (color >> 16 & 0xFF) + 0.587 * (color >> 8 & 0xFF) + 0.114 * (color & 0xFF);
    }

    private static void floydSteinberg(int[] pixels, int width, int height, int threshold, BlockPalette palette,
                                       Nearest nearest, int[] indices, BooleanSupplier cancelled) {
        float[] red = new float[pixels.length];
        float[] green = new float[pixels.length];
        float[] blue = new float[pixels.length];
        boolean[] opaque = new boolean[pixels.length];
        for (int index = 0; index < pixels.length; index++) {
            int pixel = pixels[index];
            opaque[index] = pixel >>> 24 >= threshold;
            red[index] = pixel >> 16 & 0xFF;
            green[index] = pixel >> 8 & 0xFF;
            blue[index] = pixel & 0xFF;
        }
        for (int y = 0; y < height; y++) {
            checkCancelled(cancelled);
            boolean forward = (y & 1) == 0;
            int direction = forward ? 1 : -1;
            for (int step = 0; step < width; step++) {
                int x = forward ? step : width - 1 - step;
                int index = y * width + x;
                if (!opaque[index]) {
                    indices[index] = -1;
                    continue;
                }
                float r = Math.clamp(red[index], 0.0F, 255.0F);
                float g = Math.clamp(green[index], 0.0F, 255.0F);
                float b = Math.clamp(blue[index], 0.0F, 255.0F);
                int chosen = nearest.of(rgb(r, g, b));
                indices[index] = chosen;
                int color = palette.rgb(chosen);
                float er = r - (color >> 16 & 0xFF);
                float eg = g - (color >> 8 & 0xFF);
                float eb = b - (color & 0xFF);
                spread(red, green, blue, opaque, width, height, x + direction, y, er, eg, eb, 7.0F / 16.0F);
                spread(red, green, blue, opaque, width, height, x - direction, y + 1, er, eg, eb, 3.0F / 16.0F);
                spread(red, green, blue, opaque, width, height, x, y + 1, er, eg, eb, 5.0F / 16.0F);
                spread(red, green, blue, opaque, width, height, x + direction, y + 1, er, eg, eb, 1.0F / 16.0F);
            }
        }
    }

    private static void spread(float[] red, float[] green, float[] blue, boolean[] opaque, int width, int height,
                               int x, int y, float er, float eg, float eb, float weight) {
        if (x < 0 || x >= width || y >= height) {
            return;
        }
        int index = y * width + x;
        if (!opaque[index]) {
            return;
        }
        red[index] += er * weight;
        green[index] += eg * weight;
        blue[index] += eb * weight;
    }

    private static int rgb(double red, double green, double blue) {
        return ColorMatch.rgb(clampChannel(red), clampChannel(green), clampChannel(blue));
    }

    private static int clampChannel(double value) {
        return (int) Math.round(Math.clamp(value, 0.0, 255.0));
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    /** Búsqueda del más cercano con caché por color exacto. */
    static final class Nearest {
        private final BlockPalette palette;
        private final Int2IntOpenHashMap cache = new Int2IntOpenHashMap();

        Nearest(BlockPalette palette) {
            this.palette = palette;
            cache.defaultReturnValue(-1);
        }

        int of(int rgb) {
            int cached = cache.get(rgb);
            if (cached >= 0) {
                return cached;
            }
            int found = palette.nearest(rgb);
            cache.put(rgb, found);
            return found;
        }
    }
}
