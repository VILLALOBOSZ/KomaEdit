package com.komainu.moxia.gizmo;

import com.komainu.moxia.symmetry.SymmetryTransform;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientationTest {
    @Test
    void thereAreFortyEightDistinctOrientations() {
        Set<String> matrices = new HashSet<>();
        for (int index = 0; index < Orientation.COUNT; index++) {
            Orientation orientation = Orientation.of(index);
            assertEquals(index, orientation.index());
            matrices.add(java.util.Arrays.toString(orientation.apply(1, 0, 0))
                    + java.util.Arrays.toString(orientation.apply(0, 1, 0))
                    + java.util.Arrays.toString(orientation.apply(0, 0, 1)));
        }
        assertEquals(48, matrices.size());
        assertArrayEquals(new int[]{3, -2, 5}, Orientation.IDENTITY.apply(3, -2, 5));
        assertThrows(IllegalArgumentException.class, () -> Orientation.of(48));
    }

    @Test
    void quarterTurnsFollowTheDocumentedSense() {
        // Y: de este a sur (como BlockRotation.CLOCKWISE_90 y la simetría).
        assertArrayEquals(new int[]{0, 0, 1}, Orientation.turn(Orientation.Y, 1).apply(1, 0, 0));
        assertArrayEquals(new int[]{-1, 0, 0}, Orientation.turn(Orientation.Y, 1).apply(0, 0, 1));
        // X: de arriba a sur.
        assertArrayEquals(new int[]{0, 0, 1}, Orientation.turn(Orientation.X, 1).apply(0, 1, 0));
        // Z: de este a arriba.
        assertArrayEquals(new int[]{0, 1, 0}, Orientation.turn(Orientation.Z, 1).apply(1, 0, 0));
        // Negativos, al revés.
        assertArrayEquals(new int[]{0, 0, -1}, Orientation.turn(Orientation.Y, -1).apply(1, 0, 0));
    }

    @Test
    void fourQuarterTurnsAndTwoMirrorsAreTheIdentity() {
        for (int axis = 0; axis < 3; axis++) {
            assertSame(Orientation.IDENTITY, Orientation.turn(axis, 4));
            assertSame(Orientation.IDENTITY, Orientation.turn(axis, 1).then(Orientation.turn(axis, -1)));
            assertSame(Orientation.turn(axis, 2), Orientation.turn(axis, 1).then(Orientation.turn(axis, 1)));
            assertSame(Orientation.IDENTITY, Orientation.mirror(axis).then(Orientation.mirror(axis)));
            Orientation step = Orientation.turn(axis, 1);
            Orientation result = Orientation.IDENTITY;
            for (int turn = 0; turn < 4; turn++) {
                assertTrue(turn == 0 || result != Orientation.IDENTITY);
                result = result.then(step);
            }
            assertSame(Orientation.IDENTITY, result);
        }
    }

    @Test
    void everyOrientationHasAnInverseAndCompositionIsAssociative() {
        for (int a = 0; a < Orientation.COUNT; a++) {
            Orientation first = Orientation.of(a);
            assertSame(Orientation.IDENTITY, first.then(first.inverse()));
            assertSame(Orientation.IDENTITY, first.inverse().then(first));
            for (int b = 0; b < Orientation.COUNT; b++) {
                Orientation second = Orientation.of(b);
                int[] composed = first.then(second).apply(2, 3, 5);
                int[] once = first.apply(2, 3, 5);
                assertArrayEquals(second.apply(once[0], once[1], once[2]), composed);
                for (int c = 0; c < Orientation.COUNT; c += 7) {
                    Orientation third = Orientation.of(c);
                    assertSame(first.then(second).then(third), first.then(second.then(third)));
                }
            }
        }
    }

    @Test
    void turnsGenerateTwentyFourRotationsAndMirrorsTheOtherHalf() {
        Set<Orientation> reached = new HashSet<>();
        reached.add(Orientation.IDENTITY);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Orientation orientation : Set.copyOf(reached)) {
                for (int axis = 0; axis < 3; axis++) {
                    grew |= reached.add(orientation.then(Orientation.turn(axis, 1)));
                }
            }
        }
        assertEquals(24, reached.size());
        reached.forEach(orientation -> assertFalse(orientation.mirrored(), orientation.toString()));
        int mirrored = 0;
        for (int index = 0; index < Orientation.COUNT; index++) {
            if (Orientation.of(index).mirrored()) {
                mirrored++;
                assertFalse(reached.contains(Orientation.of(index)));
            }
        }
        assertEquals(24, mirrored);
        assertTrue(Orientation.mirror(Orientation.X).mirrored());
        assertFalse(Orientation.mirror(Orientation.X).then(Orientation.mirror(Orientation.Z)).mirrored());
    }

    @Test
    void facesFollowThePositions() {
        int[][] normals = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
        for (int index = 0; index < Orientation.COUNT; index++) {
            Orientation orientation = Orientation.of(index);
            int mask = 0;
            for (int face = 0; face < 6; face++) {
                int[] moved = orientation.apply(normals[face][0], normals[face][1], normals[face][2]);
                assertArrayEquals(normals[orientation.face(face)], moved);
                mask |= 1 << orientation.face(face);
            }
            assertEquals(0b111111, mask);
            assertEquals(0b111111, orientation.faces(0b111111));
            assertEquals(1 << orientation.face(3), orientation.faces(1 << 3));
        }
    }

    @Test
    void uprightOrientationsMatchTheSymmetryTransform() {
        int upright = 0;
        for (int index = 0; index < Orientation.COUNT; index++) {
            Orientation orientation = Orientation.of(index);
            if (!orientation.upright()) {
                assertThrows(IllegalStateException.class, orientation::symmetry);
                continue;
            }
            upright++;
            SymmetryTransform transform = orientation.symmetry();
            for (int[] vector : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                int[] expected = {orientation.x(vector[0], 0, vector[1]), orientation.z(vector[0], 0, vector[1])};
                assertArrayEquals(expected, transform.direction(vector[0], vector[1]), orientation.toString());
            }
            assertEquals(orientation.upsideDown(), transform.mirrorY());
            assertEquals(orientation.mirrored(), transform.horizontalFlip() ^ transform.mirrorY());
        }
        assertEquals(16, upright);
    }

    @Test
    void tellsWhereUpPointsAndHowItLooksFromAbove() {
        assertEquals(3, Orientation.IDENTITY.upFace());
        assertTrue(Orientation.turn(Orientation.X, 2).upsideDown());
        assertFalse(Orientation.turn(Orientation.X, 1).upright());
        // Arriba pasa a mirar al sur.
        assertEquals(5, Orientation.turn(Orientation.X, 1).upFace());
        for (int turn = 0; turn < 4; turn++) {
            assertEquals(turn, Orientation.turn(Orientation.Y, turn).yawQuarters());
        }
        // Un volteo se lee como tal, en X o en Z; con un cuarto de vuelta más, en diagonal.
        assertEquals(0, Orientation.mirror(Orientation.X).yawQuarters());
        assertEquals(0, Orientation.mirror(Orientation.Z).yawQuarters());
        assertEquals(1, Orientation.mirror(Orientation.X).then(Orientation.turn(Orientation.Y, 1)).yawQuarters());
        assertEquals(1, Orientation.mirror(Orientation.Z).then(Orientation.turn(Orientation.Y, 3)).yawQuarters());
    }
}
