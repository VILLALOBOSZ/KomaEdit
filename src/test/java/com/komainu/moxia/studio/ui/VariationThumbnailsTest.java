package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintThumbnail;
import com.komainu.moxia.studio.FigureVariation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El dibujo de cada variación sale del generador de verdad y enseña lo que hace. */
class VariationThumbnailsTest {
    private static final int STONE = 0x7F7F7F;

    @BeforeAll
    static void blocking() {
        ChoiceThumbnails.setBlocking(true);
    }

    @Test
    void everyVariationHasItsPicture() {
        for (FigureVariation variation : FigureVariation.values()) {
            StudioImage image = VariationThumbnails.get(variation, "stone");
            assertNotNull(image, variation.id());
            assertTrue(VariationThumbnails.grid(variation, STONE).sizeX() > 20, variation.id() + ": a lo largo");
        }
    }

    @Test
    void theTaperedTubeIsThinnerAtTheEnd() {
        BlueprintThumbnail.Grid grid = VariationThumbnails.grid(FigureVariation.TAPER, STONE);
        int start = column(grid, 2);
        int end = column(grid, grid.sizeX() - 3);
        assertTrue(end < start / 2, "al principio " + start + " bloques de sección, al final " + end);
    }

    @Test
    void theGroundedTubeLiesOnHills() {
        BlueprintThumbnail.Grid grid = VariationThumbnails.grid(FigureVariation.GROUND, STONE);
        boolean grass = false;
        for (int x = 0; x < grid.sizeX(); x++) {
            for (int y = 0; y < grid.sizeY(); y++) {
                for (int z = 0; z < grid.sizeZ(); z++) {
                    grass |= grid.color(x, y, z) == 0x6E9A3E;
                }
            }
        }
        assertTrue(grass, "con su terreno debajo");
        // La loma sube y baja: el tubo, también (no va recto por el aire).
        assertTrue(lowest(grid, 3) != lowest(grid, grid.sizeX() / 3), "sigue el relieve");
    }

    /** Cuántos bloques del tubo hay en el corte {@code x}. */
    private static int column(BlueprintThumbnail.Grid grid, int x) {
        int count = 0;
        for (int y = 0; y < grid.sizeY(); y++) {
            for (int z = 0; z < grid.sizeZ(); z++) {
                count += grid.color(x, y, z) == STONE ? 1 : 0;
            }
        }
        return count;
    }

    /** La altura del bloque de tubo más bajo en el corte {@code x}. */
    private static int lowest(BlueprintThumbnail.Grid grid, int x) {
        for (int y = 0; y < grid.sizeY(); y++) {
            for (int z = 0; z < grid.sizeZ(); z++) {
                if (grid.color(x, y, z) == STONE) {
                    return y;
                }
            }
        }
        return -1;
    }
}
