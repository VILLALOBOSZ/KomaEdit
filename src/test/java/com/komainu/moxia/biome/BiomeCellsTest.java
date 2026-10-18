package com.komainu.moxia.biome;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeCellsTest {
    private static final Runnable NOTHING = () -> {
    };

    @Test
    void aBoxTouchesEveryCellItOverlaps() {
        // De x=-2 a 5 (celdas -1, 0 y 1), y de 64 a 67 (una), z de 0 a 3 (una).
        long[] cells = BiomeCells.cells(new int[]{-2, 64, 0, 5, 67, 3}, null, false, -64, 319, NOTHING);
        assertEquals(3, cells.length);
        Set<String> origins = new HashSet<>();
        for (long cell : cells) {
            origins.add(PackedBlockPlan.unpackX(cell) + "," + PackedBlockPlan.unpackY(cell) + ","
                    + PackedBlockPlan.unpackZ(cell));
        }
        assertEquals(Set.of("-4,64,0", "0,64,0", "4,64,0"), origins);
        // La misma caja recorrida bloque a bloque da lo mismo.
        long[] walked = BiomeCells.cells(new int[]{-2, 64, 0, 5, 67, 3}, (x, y, z) -> true, false, -64, 319, NOTHING);
        assertEquals(sorted(cells), sorted(walked));
    }

    @Test
    void theMaskAndTheVerticalFill() {
        // Solo un bloque pasa la máscara: una celda; con relleno vertical, toda su columna del mundo.
        long[] one = BiomeCells.cells(new int[]{0, 60, 0, 15, 70, 15}, (x, y, z) -> x == 9 && y == 62 && z == 1,
                false, -64, 319, NOTHING);
        assertEquals(1, one.length);
        assertEquals(8, PackedBlockPlan.unpackX(one[0]));
        assertEquals(60, PackedBlockPlan.unpackY(one[0]));
        long[] column = BiomeCells.cells(new int[]{0, 60, 0, 15, 70, 15}, (x, y, z) -> x == 9 && y == 62 && z == 1,
                true, -64, 319, NOTHING);
        assertEquals((319 + 64 + 1) / 4, column.length, "de -64 a 319 hay 96 celdas");
        for (long cell : column) {
            assertEquals(8, PackedBlockPlan.unpackX(cell));
            assertEquals(0, PackedBlockPlan.unpackZ(cell));
        }
    }

    @Test
    void theBrushPaintsASphereOfCells() {
        long[] sphere = BiomeCells.sphere(0, 64, 0, 6, false, -64, 319, null);
        long[] cube = BiomeCells.cells(new int[]{-6, 58, -6, 6, 70, 6}, null, false, -64, 319, NOTHING);
        assertTrue(sphere.length < cube.length, "una esfera toca menos celdas que su caja");
        for (long cell : sphere) {
            int x = PackedBlockPlan.unpackX(cell);
            int y = PackedBlockPlan.unpackY(cell);
            int z = PackedBlockPlan.unpackZ(cell);
            // Algún bloque de la celda está dentro del radio.
            double nearestX = Math.clamp(0, x, x + 3) - 0;
            double nearestY = Math.clamp(64, y, y + 3) - 64;
            double nearestZ = Math.clamp(0, z, z + 3) - 0;
            assertTrue(nearestX * nearestX + nearestY * nearestY + nearestZ * nearestZ <= 36.0);
        }
        long[] masked = BiomeCells.sphere(0, 64, 0, 6, false, -64, 319, (x, y, z) -> y < 60);
        assertTrue(masked.length > 0 && masked.length < sphere.length);
    }

    private static Set<Long> sorted(long[] cells) {
        Set<Long> set = new HashSet<>();
        Arrays.stream(cells).forEach(set::add);
        return set;
    }
}
