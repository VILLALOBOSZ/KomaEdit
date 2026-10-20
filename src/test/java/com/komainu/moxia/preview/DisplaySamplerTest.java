package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplaySamplerTest {
    private static long[] cube(int size) {
        LongArrayList positions = new LongArrayList();
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    positions.add(PackedBlockPlan.pack(x, y + 60, z));
                }
            }
        }
        return positions.toLongArray();
    }

    private static long[] sheet(int size) {
        LongArrayList positions = new LongArrayList();
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                positions.add(PackedBlockPlan.pack(x - size / 2, 70, z - size / 2));
            }
        }
        return positions.toLongArray();
    }

    private static void assertValid(DisplaySampler.Choice choice, int count, int limit) {
        assertTrue(choice.shown() <= Math.max(0, limit), "respeta el límite: " + choice.shown() + " > " + limit);
        int previous = -1;
        for (int index : choice.indices()) {
            assertTrue(index > previous && index < count, "índices crecientes, sin repetir y dentro del plan");
            previous = index;
        }
    }

    @Test
    void everythingFitsUnderTheLimit() {
        long[] positions = cube(10);
        DisplaySampler.Choice choice = DisplaySampler.choose(positions, positions.length, 1_000);
        assertEquals(DisplaySampler.Mode.ALL, choice.mode());
        assertEquals(1_000, choice.shown());
        assertValid(choice, positions.length, 1_000);
    }

    @Test
    void solidShapeShowsItsShell() {
        long[] positions = cube(20);
        DisplaySampler.Choice choice = DisplaySampler.choose(positions, positions.length, 3_000);
        assertEquals(DisplaySampler.Mode.SURFACE, choice.mode());
        assertEquals(20 * 20 * 20 - 18 * 18 * 18, choice.shown(), "la cáscara de un cubo de 20");
        assertValid(choice, positions.length, 3_000);
        for (int index : choice.indices()) {
            long packed = positions[index];
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed) - 60;
            int z = PackedBlockPlan.unpackZ(packed);
            assertTrue(x == 0 || x == 19 || y == 0 || y == 19 || z == 0 || z == 19, "solo caras exteriores");
        }
    }

    @Test
    void hugeSurfaceIsSampledAcrossTheWholeArea() {
        long[] positions = sheet(200);
        for (int limit : new int[]{1, 7, 500, 1_500, 4_000}) {
            DisplaySampler.Choice choice = DisplaySampler.choose(positions, positions.length, limit);
            assertEquals(DisplaySampler.Mode.SAMPLE, choice.mode());
            assertValid(choice, positions.length, limit);
            assertEquals(positions.length, choice.surface());
            assertTrue(choice.shown() >= limit * 9 / 10, "aprovecha el presupuesto: " + choice.shown() + "/" + limit);
        }
        DisplaySampler.Choice choice = DisplaySampler.choose(positions, positions.length, 1_500);
        boolean[] quadrants = new boolean[4];
        for (int index : choice.indices()) {
            long packed = positions[index];
            int quadrant = (PackedBlockPlan.unpackX(packed) < 0 ? 0 : 1) + (PackedBlockPlan.unpackZ(packed) < 0 ? 0 : 2);
            quadrants[quadrant] = true;
        }
        assertArrayEquals(new boolean[]{true, true, true, true}, quadrants, "la muestra cubre toda la zona");
        assertArrayEquals(choice.indices(), DisplaySampler.choose(positions, positions.length, 1_500).indices(),
                "es determinista");
    }

    @Test
    void zeroLimitLeavesOnlyTheBox() {
        long[] positions = sheet(10);
        DisplaySampler.Choice choice = DisplaySampler.choose(positions, positions.length, 0);
        assertEquals(0, choice.shown());
        assertEquals(DisplaySampler.Mode.SAMPLE, choice.mode());
        assertEquals(0, DisplaySampler.choose(positions, 0, 0).shown(), "sin cambios no hay nada");
        assertThrows(IllegalArgumentException.class, () -> DisplaySampler.choose(positions, positions.length + 1, 5));
        assertEquals(Arrays.toString(new int[]{0, 1}), Arrays.toString(DisplaySampler.choose(positions, 2, 5)
                .indices()), "solo se miran los count primeros");
    }
}
