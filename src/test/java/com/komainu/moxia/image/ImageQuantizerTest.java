package com.komainu.moxia.image;

import com.komainu.moxia.shape.smooth.ColorMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageQuantizerTest {
    private static final BlockPalette BLACK_WHITE = new BlockPalette(List.of(
            new BlockPalette.Entry("minecraft:black_concrete", 0x000000),
            new BlockPalette.Entry("minecraft:white_concrete", 0xFFFFFF)));

    @Test
    void eachBlockColourMapsToItsOwnBlock() {
        for (ImagePalettes.Preset preset : ImagePalettes.Preset.values()) {
            BlockPalette palette = ImagePalettes.preset(preset);
            for (int index = 0; index < palette.size(); index++) {
                int chosen = palette.nearest(palette.rgb(index));
                assertEquals(palette.rgb(index), palette.rgb(chosen), preset + ": " + palette.get(index).key());
            }
        }
    }

    @Test
    void nearestIsTheSmallestCielabDistance() {
        BlockPalette palette = ImagePalettes.preset(ImagePalettes.Preset.SOLID);
        SplittableRandom random = new SplittableRandom(7);
        for (int sample = 0; sample < 400; sample++) {
            int color = random.nextInt(0x1000000);
            int chosen = palette.nearest(color);
            double best = ColorMatch.distanceSquared(color, palette.rgb(chosen));
            for (int index = 0; index < palette.size(); index++) {
                assertTrue(best <= ColorMatch.distanceSquared(color, palette.rgb(index)) + 1.0E-9,
                        String.format("#%06X: %s no es el más cercano", color, palette.get(chosen).key()));
            }
        }
    }

    @Test
    void cielabPicksWhatTheEyeSeesNotTheRgbDistance() {
        // Un azul oscuro saturado: en RGB está más cerca del gris oscuro, pero a la vista es azul.
        BlockPalette palette = new BlockPalette(List.of(
                new BlockPalette.Entry("gris", 0x404040),
                new BlockPalette.Entry("azul", 0x2020C0)));
        int color = 0x181870;
        assertTrue(BlockPalette.rgbDistance(color, 0x404040) < BlockPalette.rgbDistance(color, 0x2020C0));
        assertEquals("azul", palette.get(palette.nearest(color)).key());
    }

    @Test
    void withoutDitheringAFlatGreyIsOneBlock() {
        RgbaImage grey = flat(32, 32, 0xFF808080);
        ImageQuantizer.Result result = ImageQuantizer.quantize(grey, BLACK_WHITE, Dithering.NONE, 128);
        assertEquals(1, result.distinct());
        assertEquals(32 * 32, result.opaque());
    }

    @Test
    void floydSteinbergKeepsTheMeanColour() {
        for (int level : new int[]{40, 96, 128, 180, 230}) {
            RgbaImage grey = flat(48, 48, RgbaImage.argb(255, level, level, level));
            ImageQuantizer.Result result = ImageQuantizer.quantize(grey, BLACK_WHITE, Dithering.FLOYD_STEINBERG,
                    128);
            double white = result.counts()[1] / (double) result.opaque();
            assertEquals(level / 255.0, white, 0.02, "gris " + level);
        }
    }

    @Test
    void bayerIsRegularAndKeepsTheMeanColour() {
        RgbaImage grey = flat(64, 64, 0xFF808080);
        ImageQuantizer.Result result = ImageQuantizer.quantize(grey, BLACK_WHITE, Dithering.BAYER, 128);
        double white = result.counts()[1] / (double) result.opaque();
        assertEquals(0.5, white, 0.05);
        // La trama se repite cada 8 píxeles en los dos ejes.
        for (int y = 0; y < 56; y++) {
            for (int x = 0; x < 56; x++) {
                assertEquals(result.index(x, y), result.index(x + 8, y));
                assertEquals(result.index(x, y), result.index(x, y + 8));
            }
        }
    }

    @Test
    void ditheringReducesTheLocalErrorOfAGradient() {
        BlockPalette palette = ImagePalettes.preset(ImagePalettes.Preset.CONCRETE);
        RgbaImage gradient = RgbaImage.blank(96, 24);
        for (int y = 0; y < gradient.height(); y++) {
            for (int x = 0; x < gradient.width(); x++) {
                int t = x * 255 / (gradient.width() - 1);
                gradient.set(x, y, RgbaImage.argb(255, t, 255 - t / 2, 64 + t / 3));
            }
        }
        double none = blurredError(gradient, ImageQuantizer.quantize(gradient, palette, Dithering.NONE, 128), palette);
        double fs = blurredError(gradient, ImageQuantizer.quantize(gradient, palette, Dithering.FLOYD_STEINBERG, 128),
                palette);
        double bayer = blurredError(gradient, ImageQuantizer.quantize(gradient, palette, Dithering.BAYER, 128),
                palette);
        assertTrue(fs < none, "Floyd–Steinberg " + fs + " frente a " + none);
        assertTrue(bayer < none, "Bayer " + bayer + " frente a " + none);
    }

    @Test
    void transparentPixelsStayEmptyAndReceiveNoError() {
        RgbaImage image = flat(16, 16, 0xFF808080);
        for (int y = 0; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                image.set(x, y, RgbaImage.argb(x == 8 ? 127 : 0, 255, 255, 255));
            }
        }
        for (Dithering dithering : Dithering.values()) {
            ImageQuantizer.Result result = ImageQuantizer.quantize(image, BLACK_WHITE, dithering, 128);
            assertEquals(16 * 8, result.transparent(), dithering.id());
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    assertEquals(x >= 8, result.index(x, y) < 0, dithering + " en " + x + "," + y);
                }
            }
        }
        // Con el umbral bajo la columna de alfa 127 cuenta como opaca.
        assertEquals(16 * 7, ImageQuantizer.quantize(image, BLACK_WHITE, Dithering.NONE, 100).transparent());
    }

    @Test
    void sameInputSameBlocks() {
        RgbaImage image = TestImages.scene(80, 60);
        BlockPalette palette = ImagePalettes.preset(ImagePalettes.Preset.GLASS);
        for (Dithering dithering : Dithering.values()) {
            assertArrayEquals(ImageQuantizer.quantize(image, palette, dithering, 128).indices(),
                    ImageQuantizer.quantize(image, palette, dithering, 128).indices(), dithering.id());
        }
    }

    @Test
    void meanErrorIsTheAverageDeltaE() {
        RgbaImage image = flat(4, 4, 0xFF808080);
        ImageQuantizer.Result result = ImageQuantizer.quantize(image, BLACK_WHITE, Dithering.NONE, 128);
        double expected = Math.sqrt(ColorMatch.distanceSquared(0x808080, BLACK_WHITE.rgb(result.index(0, 0))));
        assertEquals(expected, result.meanError(), 1.0E-9);
    }

    @Test
    void parsesDitheringNames() {
        assertEquals(Dithering.NONE, Dithering.parse("ninguno"));
        assertEquals(Dithering.FLOYD_STEINBERG, Dithering.parse("Floyd-Steinberg"));
        assertEquals(Dithering.BAYER, Dithering.parse("ordenado"));
    }

    /** Error ΔE medio tras promediar bloques de 4×4: lo que se ve desde lejos. */
    private static double blurredError(RgbaImage image, ImageQuantizer.Result result, BlockPalette palette) {
        double total = 0.0;
        int windows = 0;
        for (int y0 = 0; y0 + 4 <= image.height(); y0 += 4) {
            for (int x0 = 0; x0 + 4 <= image.width(); x0 += 4) {
                double[] original = new double[3];
                double[] blocks = new double[3];
                for (int y = y0; y < y0 + 4; y++) {
                    for (int x = x0; x < x0 + 4; x++) {
                        add(original, image.get(x, y));
                        add(blocks, palette.rgb(result.index(x, y)));
                    }
                }
                total += Math.sqrt(ColorMatch.distanceSquared(rgb(original), rgb(blocks)));
                windows++;
            }
        }
        return total / windows;
    }

    private static void add(double[] sum, int color) {
        sum[0] += color >> 16 & 0xFF;
        sum[1] += color >> 8 & 0xFF;
        sum[2] += color & 0xFF;
    }

    private static int rgb(double[] sum) {
        return ColorMatch.rgb((int) Math.round(sum[0] / 16), (int) Math.round(sum[1] / 16),
                (int) Math.round(sum[2] / 16));
    }

    static RgbaImage flat(int width, int height, int argb) {
        RgbaImage image = RgbaImage.blank(width, height);
        java.util.Arrays.fill(image.argb(), argb);
        return image;
    }
}
