package com.komainu.moxia.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageConverterTest {
    @Test
    void convertsAPngIntoGlassBlocks() {
        byte[] png = TestImages.encode(TestImages.scene(160, 120), "png");
        ImageConverter.Converted converted = ImageConverter.convert(png, 1_000_000,
                header -> ImageSize.of(header.width(), header.height(), 32, null, 128, 512), 0, 0,
                ImagePalettes.preset(ImagePalettes.Preset.GLASS), Dithering.FLOYD_STEINBERG, 128, () -> false);
        assertEquals(new ImageSize(32, 24), converted.size());
        assertEquals(160, converted.header().width());
        ImageQuantizer.Result blocks = converted.blocks();
        assertEquals(32 * 24, blocks.indices().length);
        assertEquals(-1, blocks.index(0, 0), "esquina redondeada");
        assertTrue(blocks.index(16, 12) >= 0);
        assertTrue(blocks.distinct() >= 6, "un atardecer usa muchos tintes: " + blocks.distinct());
        assertTrue(blocks.meanError() < 25.0, "ΔE medio " + blocks.meanError());
    }

    @Test
    void brightnessAndContrastChangeTheChoice() {
        byte[] png = TestImages.encode(ImageQuantizerTest.flat(8, 8, 0xFF808080), "png");
        BlockPalette palette = ImagePalettes.preset(ImagePalettes.Preset.CONCRETE);
        String normal = key(png, palette, 0, 0);
        String bright = key(png, palette, 80, 0);
        String dark = key(png, palette, -80, 0);
        assertEquals("minecraft:white_concrete", bright);
        assertEquals("minecraft:black_concrete", dark);
        assertTrue(!normal.equals(bright) && !normal.equals(dark), normal);
        assertEquals(0, ImageFilters.lookup(0, -100)[0] - 128, "sin contraste todo es gris medio");
        assertEquals(255, ImageFilters.lookup(0, 100)[200]);
    }

    @Test
    void aFullyTransparentImageIsAnError() {
        byte[] png = TestImages.encode(ImageQuantizerTest.flat(8, 8, 0x40FF0000), "png");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ImageConverter.convert(
                png, 1_000, header -> new ImageSize(8, 8), 0, 0, ImagePalettes.preset(ImagePalettes.Preset.WOOL),
                Dithering.NONE, 128, () -> false));
        assertTrue(error.getMessage().contains("transparente"), error.getMessage());
    }

    @Test
    void thePixelLimitAppliesBeforeSizing() {
        byte[] png = TestImages.encode(TestImages.scene(100, 100), "png");
        assertThrows(IllegalArgumentException.class, () -> ImageConverter.convert(png, 9_999,
                header -> new ImageSize(10, 10), 0, 0, ImagePalettes.preset(ImagePalettes.Preset.WOOL),
                Dithering.NONE, 128, () -> false));
    }

    @Test
    void cancellationStopsTheWork() {
        byte[] png = TestImages.encode(TestImages.scene(100, 100), "png");
        assertThrows(java.util.concurrent.CancellationException.class, () -> ImageConverter.convert(png, 100_000,
                header -> new ImageSize(50, 50), 0, 0, ImagePalettes.preset(ImagePalettes.Preset.WOOL),
                Dithering.FLOYD_STEINBERG, 128, () -> true));
    }

    @Test
    void panesJoinTheirOpaqueNeighboursInTheRow() {
        int[] indices = {0, 0, -1, 0, 1, 1};
        ImageQuantizer.Result blocks = new ImageQuantizer.Result(6, 1, indices, new int[2], 1, 0.0);
        int[] cells = ImageConverter.cells(blocks, new boolean[]{true, false});
        assertArrayEquals(new int[]{
                ImageConverter.CONNECT_RIGHT,
                ImageConverter.CONNECT_LEFT,
                -1,
                ImageConverter.CONNECT_RIGHT,
                4,
                4
        }, cells);
    }

    private static String key(byte[] png, BlockPalette palette, int brightness, int contrast) {
        ImageConverter.Converted converted = ImageConverter.convert(png, 1_000, header -> new ImageSize(1, 1),
                brightness, contrast, palette, Dithering.NONE, 128, () -> false);
        return palette.get(converted.blocks().index(0, 0)).key();
    }
}
