package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewSurfaceTest {
    static long[] cube(int size, int originX, int originY, int originZ) {
        LongArrayList positions = new LongArrayList();
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    positions.add(PackedBlockPlan.pack(originX + x, originY + y, originZ + z));
                }
            }
        }
        return positions.toLongArray();
    }

    @Test
    void solidCubeSendsOnlyItsShellWithTheFacesThatShow() {
        long[] positions = cube(4, 10, 64, -3);
        PreviewSurface.Selection selection = PreviewSurface.select(positions, positions.length, 10_000);
        assertEquals(DisplaySampler.Mode.SURFACE, selection.mode());
        assertEquals(64, selection.unique());
        assertEquals(64 - 8, selection.shown(), "el interior de 2×2×2 no se ve");
        int faces = 0;
        for (int slot = 0; slot < selection.shown(); slot++) {
            long packed = positions[selection.indices()[slot]];
            int mask = selection.faces()[slot];
            faces += Integer.bitCount(mask);
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            assertEquals(x == 10, (mask & 1) != 0, "cara oeste solo en el borde oeste");
            assertEquals(x == 13, (mask & 2) != 0, "cara este solo en el borde este");
            assertEquals(y == 64, (mask & 4) != 0, "cara de abajo solo abajo");
            assertEquals(y == 67, (mask & 8) != 0, "cara de arriba solo arriba");
        }
        assertEquals(6 * 16, faces, "seis caras de 4×4");
    }

    @Test
    void aThinShapeIsSentWhole() {
        LongArrayList positions = new LongArrayList();
        for (int x = 0; x < 30; x++) {
            positions.add(PackedBlockPlan.pack(x, 70, 0));
        }
        PreviewSurface.Selection selection = PreviewSurface.select(positions.toLongArray(), positions.size(), 100);
        assertEquals(DisplaySampler.Mode.ALL, selection.mode());
        assertEquals(30, selection.shown());
    }

    @Test
    void aRepeatedPositionKeepsItsLastEntry() {
        long[] positions = {
                PackedBlockPlan.pack(0, 64, 0),
                PackedBlockPlan.pack(1, 64, 0),
                PackedBlockPlan.pack(0, 64, 0)
        };
        PreviewSurface.Selection selection = PreviewSurface.select(positions, positions.length, 10);
        assertEquals(2, selection.unique());
        assertEquals(2, selection.shown());
        assertEquals(1, selection.indices()[0]);
        assertEquals(2, selection.indices()[1], "gana la última escritura, como al aplicar");
        assertEquals(0b111101, selection.faces()[1], "el bloque 0 tiene al vecino del este");
    }

    @Test
    void aSurfaceOverTheLimitIsSampledEvenly() {
        long[] positions = cube(40, -20, 0, -20);
        PreviewSurface.Selection selection = PreviewSurface.select(positions, positions.length, 2_000);
        assertEquals(DisplaySampler.Mode.SAMPLE, selection.mode());
        assertTrue(selection.shown() <= 2_000);
        assertTrue(selection.shown() > 1_500, "aprovecha el presupuesto: " + selection.shown());
        int previous = -1;
        for (int slot = 0; slot < selection.shown(); slot++) {
            int index = selection.indices()[slot];
            assertTrue(index > previous, "índices crecientes");
            previous = index;
            assertTrue(selection.faces()[slot] != 0, "solo superficie");
        }
    }
}
