package com.komainu.moxia.edit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El aviso de límite antes de empezar: tamaño, límite, trozos y cómo partir la selección. */
class EditLimitsTest {
    @Test
    void withinTheLimitThereIsNoWarning() {
        assertTrue(EditLimits.check(EditLimits.Kind.SERVER, 1_500_000, 1_500_000).isEmpty());
        assertTrue(EditLimits.check(EditLimits.Kind.WORLDEDIT, 9_000_000, -1).isEmpty(), "//limit -1: sin límite");
    }

    @Test
    void theWarningSaysSizeLimitAndHowToSplit() {
        EditLimits.Excess excess = EditLimits.check(EditLimits.Kind.SERVER, 3_200_000, 1_500_000).orElseThrow();
        assertEquals(3, excess.parts());
        String message = excess.message("La selección", true);
        assertEquals("La selección tiene 3 200 000 bloques y el límite del servidor es 1 500 000 bloques por"
                + " edición: pártela en 3 trozos de unos 1 066 667 bloques (/komaedit trozo 1 selecciona el"
                + " primero) y repite la orden en cada uno", message);
        String worldEdit = EditLimits.check(EditLimits.Kind.WORLDEDIT, 250_000, 100_000).orElseThrow()
                .message("La edición", false);
        assertTrue(worldEdit.contains("tu //limit de WorldEdit es 100 000 bloques"), worldEdit);
        assertTrue(worldEdit.contains("súbelo con //limit 250000 o pártela en 3 trozos"), worldEdit);
        assertTrue(EditLimits.check(EditLimits.Kind.CAPTURE, 20_000_000, 16_000_000).orElseThrow()
                .message("la selección", false).contains("KomaEdit lee como mucho 16 000 000 bloques"));
    }

    @Test
    void aBoxSplitsAlongItsLongestSideAtChunkBorders() {
        BlockBounds box = new BlockBounds(0, -64, 0, 299, -1, 99);
        List<BlockBounds> parts = EditLimits.split(box, 600_000);
        assertEquals(4, parts.size());
        int expectedX = 0;
        long total = 0;
        for (BlockBounds part : parts) {
            assertEquals(expectedX, part.minimumX(), "sin huecos ni solapes");
            assertEquals(box.minimumY(), part.minimumY());
            assertEquals(box.maximumZ(), part.maximumZ());
            long volume = volume(part);
            assertTrue(volume <= 600_000, "cada trozo cabe: " + volume);
            total += volume;
            expectedX = part.maximumX() + 1;
            if (part.maximumX() != box.maximumX()) {
                assertEquals(0, (part.maximumX() + 1) % 16, "corta en borde de chunk");
            }
        }
        assertEquals(box.maximumX() + 1, expectedX);
        assertEquals(volume(box), total);
    }

    @Test
    void deepBoxesSplitAlongZAndNegativeCoordinatesWork() {
        BlockBounds box = new BlockBounds(-1_000, 0, -5_000, -951, 99, -4_201);
        List<BlockBounds> parts = EditLimits.split(box, 1_000_000);
        long total = 0;
        int expectedZ = box.minimumZ();
        for (BlockBounds part : parts) {
            assertEquals(box.minimumX(), part.minimumX());
            assertEquals(expectedZ, part.minimumZ());
            assertTrue(volume(part) <= 1_000_000);
            total += volume(part);
            expectedZ = part.maximumZ() + 1;
        }
        assertEquals(volume(box), total);
        assertEquals(EditLimits.parts(volume(box), 1_000_000), parts.size());
    }

    @Test
    void aSliceThatDoesNotFitCannotBeSplit() {
        assertThrows(IllegalArgumentException.class,
                () -> EditLimits.split(new BlockBounds(0, 0, 0, 999, 383, 999), 100_000));
    }

    private static long volume(BlockBounds box) {
        return (long) (box.maximumX() - box.minimumX() + 1) * (box.maximumY() - box.minimumY() + 1)
                * (box.maximumZ() - box.minimumZ() + 1);
    }
}
