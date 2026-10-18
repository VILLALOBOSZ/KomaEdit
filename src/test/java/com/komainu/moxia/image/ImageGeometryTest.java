package com.komainu.moxia.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Medidas, proporciones, reescalado y colocación en el plano. */
class ImageGeometryTest {
    private static final int[] EAST = {1, 0, 0};
    private static final int[] SOUTH = {0, 0, 1};
    private static final int[] WEST = {-1, 0, 0};
    private static final int[] NORTH = {0, 0, -1};
    private static final int[] UP = {0, 1, 0};

    @Test
    void theOtherSideKeepsTheProportion() {
        assertEquals(new ImageSize(64, 32), ImageSize.of(200, 100, 64, null, 128, 512));
        assertEquals(new ImageSize(40, 20), ImageSize.of(200, 100, null, 20, 128, 512));
        assertEquals(new ImageSize(48, 64), ImageSize.of(300, 400, null, 64, 128, 512));
        assertEquals(new ImageSize(10, 7), ImageSize.of(1920, 1280, 10, null, 128, 512), "6,67 redondea a 7");
        assertEquals(new ImageSize(30, 1), ImageSize.of(3000, 10, 30, null, 128, 512), "nunca menos de 1");
        assertEquals(new ImageSize(50, 40), ImageSize.of(200, 100, 50, 40, 128, 512), "con los dos, se estira");
    }

    @Test
    void withoutSizeTheImageKeepsItsPixelsUpToTheDefault() {
        assertEquals(new ImageSize(16, 16), ImageSize.of(16, 16, null, null, 128, 512));
        assertEquals(new ImageSize(128, 72), ImageSize.of(1920, 1080, null, null, 128, 512));
        assertEquals(new ImageSize(96, 128), ImageSize.of(600, 800, null, null, 128, 512));
    }

    @Test
    void fitsInsideTheSelectionWithoutDeforming() {
        assertEquals(new ImageSize(60, 30), ImageSize.fit(200, 100, 100, 30, false, 512));
        assertEquals(new ImageSize(40, 20), ImageSize.fit(200, 100, 40, 40, false, 512));
        assertEquals(new ImageSize(40, 40), ImageSize.fit(200, 100, 40, 40, true, 512));
        assertEquals(new ImageSize(33, 33), ImageSize.fit(500, 500, 33, 60, false, 512));
    }

    @Test
    void refusesWorksLargerThanTheServerAllows() {
        assertThrows(IllegalArgumentException.class, () -> ImageSize.of(100, 100, 600, null, 128, 512));
        assertThrows(IllegalArgumentException.class, () -> ImageSize.of(100, 1000, 100, null, 128, 512));
        assertThrows(IllegalArgumentException.class, () -> ImageSize.fit(100, 100, 600, 600, false, 512));
    }

    @Test
    void subsamplingLeavesAtLeastFourPixelsPerBlock() {
        assertEquals(1, ImageSize.subsampling(256, 256, new ImageSize(64, 64)));
        assertEquals(4, ImageSize.subsampling(4096, 4096, new ImageSize(256, 256)));
        assertEquals(7, ImageSize.subsampling(4000, 3000, new ImageSize(128, 96)));
        assertEquals(1, ImageSize.subsampling(32, 32, new ImageSize(64, 64)));
    }

    @Test
    void reducingAveragesByArea() {
        RgbaImage checker = RgbaImage.blank(4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                checker.set(x, y, (x + y) % 2 == 0 ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        int pixel = ImageResampler.resample(checker, 1, 1).get(0, 0);
        assertEquals(255, RgbaImage.alpha(pixel));
        assertEquals(0x808080, RgbaImage.rgb(pixel));
        // 3 → 2: cada bloque es 1,5 píxeles; un color uniforme sigue uniforme.
        RgbaImage flat = ImageQuantizerTest.flat(3, 3, 0xFF336699);
        RgbaImage reduced = ImageResampler.resample(flat, 2, 2);
        for (int value : reduced.argb()) {
            assertEquals(0xFF336699, value);
        }
    }

    @Test
    void transparentPixelsDoNotDarkenTheAverage() {
        RgbaImage image = RgbaImage.blank(2, 1);
        image.set(0, 0, 0xFFFF0000);
        image.set(1, 0, 0x00000000);
        int pixel = ImageResampler.resample(image, 1, 1).get(0, 0);
        assertEquals(0xFF0000, RgbaImage.rgb(pixel), "rojo puro, no rojo oscuro");
        assertEquals(128, RgbaImage.alpha(pixel), 1);
    }

    @Test
    void enlargingKeepsPixelArtSharp() {
        RgbaImage image = RgbaImage.blank(2, 2);
        image.set(0, 0, 0xFFFF0000);
        image.set(1, 0, 0xFF00FF00);
        image.set(0, 1, 0xFF0000FF);
        image.set(1, 1, 0xFFFFFFFF);
        RgbaImage large = ImageResampler.resample(image, 6, 6);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                assertEquals(image.get(x / 3, y / 3), large.get(x, y), x + "," + y);
            }
        }
    }

    @Test
    void anchoredPlacesTheChosenPointOnTheTarget() {
        int[] target = {10, 64, -5};
        ImageSize size = new ImageSize(5, 3);
        // De pie mirando al sur (la pared mira al norte): derecha = oeste.
        ImageLayout bottom = ImageLayout.anchored(target, WEST, UP, size, Anchor.BOTTOM);
        assertArrayEquals(target, position(bottom, 2, 2), "centro de la fila de abajo");
        assertArrayEquals(new int[]{12, 66, -5}, position(bottom, 0, 0), "arriba a la izquierda");
        assertArrayEquals(new int[]{8, 64, -5}, position(bottom, 4, 2), "abajo a la derecha");
        ImageLayout corner = ImageLayout.anchored(target, EAST, UP, size, Anchor.BOTTOM_LEFT);
        assertArrayEquals(target, position(corner, 0, 2));
        ImageLayout topRight = ImageLayout.anchored(target, EAST, UP, size, Anchor.TOP_RIGHT);
        assertArrayEquals(target, position(topRight, 4, 0));
        // En el suelo mirando al norte: la parte de arriba de la imagen queda al norte, la derecha al este.
        ImageLayout floor = ImageLayout.anchored(target, EAST, NORTH, size, Anchor.CENTER);
        assertTrue(floor.horizontal());
        assertArrayEquals(target, position(floor, 2, 1));
        assertArrayEquals(new int[]{8, 64, -6}, position(floor, 0, 0));
        assertArrayEquals(new int[]{12, 64, -4}, position(floor, 4, 2));
    }

    @Test
    void insideTheSelectionItIsCentredAndFlat() {
        int[] min = {0, 60, 5};
        int[] max = {9, 69, 5};
        assertEquals(2, ImageLayout.flatAxis(min, max));
        // Mirando al sur hacia el plano z = 5: derecha = oeste.
        assertArrayEquals(new int[]{10, 10}, ImageLayout.boxSize(min, max, WEST, UP));
        ImageLayout layout = ImageLayout.inBox(min, max, WEST, UP, new ImageSize(10, 4), Anchor.CENTER);
        assertArrayEquals(new int[]{9, 66, 5}, position(layout, 0, 0), "arriba a la izquierda, vista desde el norte");
        assertArrayEquals(new int[]{0, 63, 5}, position(layout, 9, 3));
        int[] bounds = layout.bounds();
        assertTrue(bounds[0] >= min[0] && bounds[3] <= max[0] && bounds[1] >= min[1] && bounds[4] <= max[1]);
        ImageLayout bottom = ImageLayout.inBox(min, max, WEST, UP, new ImageSize(10, 4), Anchor.BOTTOM);
        assertEquals(60, position(bottom, 0, 3)[1]);
        // Horizontal en y = 60, mirando al este: derecha = sur, arriba = este.
        int[] floorMin = {0, 60, 0};
        int[] floorMax = {7, 60, 3};
        assertEquals(1, ImageLayout.flatAxis(floorMin, floorMax));
        ImageLayout floor = ImageLayout.inBox(floorMin, floorMax, SOUTH, EAST, new ImageSize(4, 8), Anchor.CENTER);
        assertArrayEquals(new int[]{7, 60, 0}, position(floor, 0, 0));
        assertArrayEquals(new int[]{0, 60, 3}, position(floor, 3, 7));
    }

    @Test
    void theSelectionHasToBeAFlatRectangle() {
        assertThrows(IllegalArgumentException.class, () -> ImageLayout.flatAxis(new int[]{0, 0, 0},
                new int[]{4, 4, 4}));
        assertThrows(IllegalArgumentException.class, () -> ImageLayout.flatAxis(new int[]{0, 0, 0},
                new int[]{4, 0, 0}));
        assertThrows(IllegalArgumentException.class, () -> ImageLayout.inBox(new int[]{0, 0, 0},
                new int[]{3, 3, 0}, EAST, UP, new ImageSize(5, 2), Anchor.CENTER));
    }

    @Test
    void parsesAnchors() {
        assertEquals(Anchor.BOTTOM_LEFT, Anchor.parse("abajo-izquierda"));
        assertEquals(Anchor.TOP_RIGHT, Anchor.parse("TopRight"));
        assertEquals(Anchor.CENTER, Anchor.parse("centro"));
        assertThrows(IllegalArgumentException.class, () -> Anchor.parse("diagonal"));
    }

    private static int[] position(ImageLayout layout, int u, int v) {
        return new int[]{layout.worldX(u, v), layout.worldY(u, v), layout.worldZ(u, v)};
    }
}
