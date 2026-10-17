package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectionShapeTest {
    private static final SelectionShape CUBE = SelectionShape.cuboid(BlockVector3.at(10, 60, 10),
            BlockVector3.at(14, 62, 11), "minecraft:overworld");

    @Test
    void nextShiftsByItsOwnSizePlusTheGap() {
        assertEquals(BlockVector3.at(5, 3, 2), CUBE.size());
        SelectionShape east = CUBE.next(BlockVector3.UNIT_X, 0);
        assertEquals(BlockVector3.at(15, 60, 10), east.minimum());
        SelectionShape up = CUBE.next(BlockVector3.UNIT_Y, 2);
        assertEquals(BlockVector3.at(10, 65, 10), up.minimum());
        SelectionShape north = CUBE.next(BlockVector3.UNIT_MINUS_Z, 1);
        assertEquals(BlockVector3.at(10, 60, 7), north.minimum());
    }

    @Test
    void moveToPutsTheChosenPointOnTheTarget() {
        BlockVector3 target = BlockVector3.at(0, 100, 0);
        assertEquals(target, CUBE.moveTo(SelectionShape.Anchor.POS1, target).points().getFirst());
        assertEquals(target, CUBE.moveTo(SelectionShape.Anchor.POS2, target).points().getLast());
        assertEquals(target, CUBE.moveTo(SelectionShape.Anchor.CENTER, target).center());
        assertEquals(SelectionShape.Anchor.CENTER, SelectionShape.Anchor.byName("centro"));
    }

    @Test
    void invertReversesPointOrder() {
        SelectionShape convex = new SelectionShape(SelectionShape.Kind.CONVEX, List.of(BlockVector3.at(0, 0, 0),
                BlockVector3.at(5, 0, 0), BlockVector3.at(5, 5, 0)), 0, 0, 0, 0, 0, null);

        assertEquals(List.of(BlockVector3.at(14, 62, 11), BlockVector3.at(10, 60, 10)), CUBE.inverted().points());
        assertEquals(List.of(BlockVector3.at(5, 5, 0), BlockVector3.at(5, 0, 0), BlockVector3.at(0, 0, 0)),
                convex.inverted().points());
        SelectionShape sphere = new SelectionShape(SelectionShape.Kind.SPHERE, List.of(BlockVector3.ZERO), 3, 3, 3,
                0, 0, null);
        assertSame(sphere, sphere.inverted());
    }

    @Test
    void deletingTheLastPointOnlyWorksOnPointLists() {
        SelectionShape polygon = new SelectionShape(SelectionShape.Kind.POLYGON, List.of(BlockVector3.at(0, 64, 0),
                BlockVector3.at(8, 64, 0), BlockVector3.at(8, 64, 8)), 0, 0, 0, 64, 70, null);

        assertEquals(2, polygon.withoutLastPoint().points().size());
        assertThrows(IllegalArgumentException.class, CUBE::withoutLastPoint);
        SelectionShape single = new SelectionShape(SelectionShape.Kind.CONVEX, List.of(BlockVector3.ZERO), 0, 0, 0,
                0, 0, null);
        assertThrows(IllegalArgumentException.class, single::withoutLastPoint);
    }

    @Test
    void roundShapesUseTheirRadiusForBounds() {
        SelectionShape cylinder = new SelectionShape(SelectionShape.Kind.CYLINDER, List.of(BlockVector3.at(0, 0, 0)),
                4, 0, 2, 70, 60, null);
        assertEquals(BlockVector3.at(-4, 60, -2), cylinder.minimum());
        assertEquals(BlockVector3.at(4, 70, 2), cylinder.maximum());
        SelectionShape moved = cylinder.translate(BlockVector3.at(1, 5, 1));
        assertEquals(65, moved.minY());
        assertEquals(BlockVector3.at(5, 75, 3), moved.maximum());

        SelectionShape ellipsoid = new SelectionShape(SelectionShape.Kind.ELLIPSOID, List.of(BlockVector3.at(0, 64, 0)),
                2, 3, 4, 0, 0, null);
        assertEquals(BlockVector3.at(5, 7, 9), ellipsoid.size());
        assertEquals(BlockVector3.at(0, 64, 0), ellipsoid.anchor(SelectionShape.Anchor.POS2));
    }

    @Test
    void jsonRoundTripKeepsEverything() {
        SelectionShape polygon = new SelectionShape(SelectionShape.Kind.POLYGON, List.of(BlockVector3.at(0, 64, 0),
                BlockVector3.at(8, 64, 0), BlockVector3.at(8, 64, 8)), 0, 0, 0, 64, 70, "minecraft:the_nether");
        SelectionShape ellipsoid = new SelectionShape(SelectionShape.Kind.ELLIPSOID, List.of(BlockVector3.at(1, 2, 3)),
                2.5, 3, 4, 0, 0, null);

        for (SelectionShape shape : List.of(CUBE, polygon, ellipsoid)) {
            assertEquals(shape, SelectionShape.fromJson(shape.toJson().toString()));
        }
        assertThrows(IllegalArgumentException.class, () -> SelectionShape.fromJson("{\"version\":1}"));
        assertThrows(IllegalArgumentException.class, () -> SelectionShape.fromJson("{\"version\":9,\"type\":\"cuboid\"}"));
    }
}
