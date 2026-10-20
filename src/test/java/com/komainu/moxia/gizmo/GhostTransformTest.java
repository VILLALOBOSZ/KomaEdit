package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostTransformTest {
    private static final BlockBounds LOCAL = new BlockBounds(-2, -1, -3, 3, 4, 2);

    @Test
    void placesLocalPositionsAroundThePivot() {
        GhostTransform transform = GhostTransform.at(100, 64, -20);
        assertEquals(PackedBlockPlan.pack(101, 66, -23), transform.apply(PackedBlockPlan.pack(1, 2, -3)));
        GhostTransform turned = transform.turned(Orientation.Y, 1);
        // De este a sur: el bloque a 1 al este del pivote acaba a 1 al sur.
        assertEquals(PackedBlockPlan.pack(100, 64, -19), turned.apply(PackedBlockPlan.pack(1, 0, 0)));
        assertEquals(PackedBlockPlan.pack(100, 64, -20), turned.apply(PackedBlockPlan.pack(0, 0, 0)));
    }

    @Test
    void localUndoesApply() {
        for (int index = 0; index < Orientation.COUNT; index++) {
            GhostTransform transform = new GhostTransform(Orientation.of(index), 7, -3, 11);
            int[] world = {transform.worldX(2, -1, 4), transform.worldY(2, -1, 4), transform.worldZ(2, -1, 4)};
            assertArrayEquals(new int[]{2, -1, 4}, transform.local(world[0], world[1], world[2]));
        }
    }

    @Test
    void boundsTurnWithTheObject() {
        GhostTransform transform = GhostTransform.at(0, 70, 0);
        assertEquals(new BlockBounds(-2, 69, -3, 3, 74, 2), transform.bounds(LOCAL));
        // Un cuarto de vuelta en Y: el ancho X pasa a Z y el largo Z, del revés, a X.
        assertEquals(new BlockBounds(-2, 69, -2, 3, 74, 3), transform.turned(Orientation.Y, 1).bounds(LOCAL));
        // Tumbado en X, la altura pasa a Z.
        BlockBounds tipped = transform.turned(Orientation.X, 1).oriented(LOCAL);
        assertEquals(6, tipped.maximumZ() - tipped.minimumZ() + 1);
        assertEquals(6, tipped.maximumY() - tipped.minimumY() + 1);
    }

    @Test
    void turningFourTimesOrFlippingTwiceLeavesItAsItWas() {
        GhostTransform start = new GhostTransform(Orientation.of(13), 5, 6, 7);
        for (int axis = 0; axis < 3; axis++) {
            GhostTransform turned = start;
            for (int turn = 0; turn < 4; turn++) {
                turned = turned.turned(axis, 1);
            }
            assertEquals(start, turned);
            assertEquals(start, start.mirrored(axis).mirrored(axis));
            assertEquals(start, start.turned(axis, 1).turned(axis, -1));
        }
        assertEquals(start.moved(3, -2, 1), start.movedTo(8, 4, 8));
    }
}
