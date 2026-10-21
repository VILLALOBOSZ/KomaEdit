package com.komainu.moxia.blueprint;

import com.komainu.moxia.FullTests;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La miniatura isométrica: tamaño, fondo transparente, luz por cara y rapidez con planos grandes. */
class BlueprintThumbnailTest {
    private static int brightness(int argb) {
        return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
    }

    @Test
    void aCubeShowsALightTopAndTwoDarkerSidesOnATransparentBackground() {
        BlueprintThumbnail.Grid cube = new BlueprintThumbnail.Grid(1, 1, 1);
        cube.set(0, 0, 0, BlueprintFixtures.color("stone"));
        BufferedImage image = BlueprintThumbnail.render(cube, BlueprintThumbnail.SIZE);
        assertEquals(BlueprintThumbnail.SIZE, image.getWidth());
        assertEquals(BlueprintThumbnail.SIZE, image.getHeight());
        for (int[] corner : new int[][]{{0, 0}, {127, 0}, {0, 127}, {127, 127}}) {
            assertEquals(0, image.getRGB(corner[0], corner[1]) >>> 24, "esquinas transparentes");
        }
        int top = image.getRGB(64, 33);
        int left = image.getRGB(39, 77);
        int right = image.getRGB(89, 77);
        assertEquals(255, top >>> 24);
        assertTrue(brightness(top) > brightness(left), "arriba más claro que el lado sur");
        assertTrue(brightness(left) > brightness(right), "el sur más claro que el este");
    }

    @Test
    void theSamePlanAlwaysGivesTheSamePng() throws IOException {
        byte[] first = BlueprintThumbnail.png(BlueprintFixtures.house());
        byte[] second = BlueprintThumbnail.png(BlueprintFixtures.house());
        assertArrayEquals(first, second, "misma huella: el cliente no la vuelve a pedir");
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(first));
        assertEquals(BlueprintThumbnail.SIZE, read.getWidth());
        assertTrue(first.length < 32 * 1024, "una miniatura pesa poco: " + first.length);
    }

    @Test
    void anEmptyPlanIsFullyTransparent() {
        BufferedImage image = BlueprintThumbnail.render(new BlueprintThumbnail.Grid(4, 4, 4), 32);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                assertEquals(0, image.getRGB(x, y) >>> 24);
            }
        }
    }

    @Test
    void theContentFillsTheFrameWhateverItsSize() {
        for (BlueprintThumbnail.Grid grid : new BlueprintThumbnail.Grid[]{BlueprintFixtures.bush(),
                BlueprintFixtures.tree(), BlueprintFixtures.plaza()}) {
            BufferedImage image = BlueprintThumbnail.render(grid, BlueprintThumbnail.SIZE);
            int minX = Integer.MAX_VALUE;
            int maxX = -1;
            int minY = Integer.MAX_VALUE;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > 128) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            int span = Math.max(maxX - minX, maxY - minY) + 1;
            assertTrue(span >= 128 * 0.8, "el lado largo llena el encuadre: " + span);
            assertTrue(minX >= 2 && minY >= 2 && maxX <= 125 && maxY <= 125, "con un margen alrededor");
        }
    }

    @Test
    void aLargePlanStillRendersQuickly() {
        BlueprintThumbnail.Grid plaza = BlueprintFixtures.plaza();
        long start = System.nanoTime();
        BlueprintThumbnail.png(plaza);
        long millis = (System.nanoTime() - start) / 1_000_000;
        FullTests.assertFasterThan(millis, 5_000, "la miniatura de 64×40×64 tardó " + millis + " ms");
    }

    @Test
    void tintedAndInvisibleBlocksGetTheirThumbnailColors() {
        assertEquals(0x3F76E4, ThumbnailColors.color("minecraft:water", id -> 0xB1B1B1));
        assertEquals(-1, ThumbnailColors.color("minecraft:barrier", id -> 0x123456));
        assertEquals(-1, ThumbnailColors.color("minecraft:structure_void", id -> 0x123456));
        assertEquals(0x7E7E7E, ThumbnailColors.color("minecraft:stone", id -> 0x7E7E7E));
        assertEquals(-1, ThumbnailColors.color("mod:unknown", id -> -1));
    }
}
