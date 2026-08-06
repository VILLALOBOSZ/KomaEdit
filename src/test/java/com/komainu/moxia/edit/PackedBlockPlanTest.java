package com.komainu.moxia.edit;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackedBlockPlanTest {
    @Test
    void packsAndUnpacksSupportedWorldCoordinates() {
        assertRoundTrip(-33_554_432, -2_048, -33_554_432);
        assertRoundTrip(33_554_431, 2_047, 33_554_431);
        assertRoundTrip(-12_345, -64, 67_890);
    }

    @Test
    void rejectsUnsupportedVerticalCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> PackedBlockPlan.pack(0, -2_049, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedBlockPlan.pack(0, 2_048, 0));
    }

    @Test
    void tracksBoundsAndCaptureSlots() {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(2);
        builder.addPattern(-4, 20, 8);
        builder.addCapturedPattern(BlockVector3.at(9, -3, -7), 4);

        PackedBlockPlan plan = builder.build();

        assertEquals(2, plan.size());
        assertEquals(5, plan.captureSize());
        assertEquals(new BlockBounds(-4, -3, -7, 9, 20, 8), plan.bounds());
        assertEquals(PackedBlockPlan.NO_CAPTURE, plan.captureIndex(0));
        assertEquals(4, plan.captureIndex(1));
    }

    private static void assertRoundTrip(int x, int y, int z) {
        long packed = PackedBlockPlan.pack(x, y, z);
        assertEquals(x, PackedBlockPlan.unpackX(packed));
        assertEquals(y, PackedBlockPlan.unpackY(packed));
        assertEquals(z, PackedBlockPlan.unpackZ(packed));
    }
}
