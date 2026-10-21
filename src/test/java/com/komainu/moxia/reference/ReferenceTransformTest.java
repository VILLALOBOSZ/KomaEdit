package com.komainu.moxia.reference;

import com.komainu.moxia.gizmo.Orientation;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El giro (cuaternión) y el plano de una referencia en el mundo. */
class ReferenceTransformTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void withoutRotationTheImageStandsFacingSouth() {
        ReferenceRotation rotation = ReferenceRotation.IDENTITY;
        assertVector(new Vec3(1, 0, 0), rotation.right());
        assertVector(new Vec3(0, 1, 0), rotation.up());
        assertVector(new Vec3(0, 0, 1), rotation.normal());
        // Se lee mirando al norte.
        assertEquals(180.0, rotation.readerYaw(), EPSILON);
        assertEquals(0.0, rotation.tilt(), EPSILON);
    }

    @Test
    void facingThePlayerIsReadableFromWhereTheyLook() {
        for (double yaw : new double[]{0.0, 90.0, 180.0, -90.0, 37.5, -142.0}) {
            ReferenceRotation rotation = ReferenceRotation.facing(yaw);
            double radians = Math.toRadians(yaw);
            Vec3 look = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
            // La cara mira contra el jugador y su derecha es la derecha del jugador.
            assertEquals(-1.0, rotation.normal().dot(look), 1.0E-9, "guiñada " + yaw);
            assertEquals(0.0, rotation.right().dot(look), 1.0E-9);
            assertEquals(1.0, rotation.up().y(), 1.0E-9);
            Vec3 playerRight = look.cross(new Vec3(0, 1, 0));
            assertEquals(1.0, rotation.right().dot(playerRight), 1.0E-9);
            assertEquals(yaw == -180.0 ? 180.0 : yaw, rotation.readerYaw(), 0.05);
        }
    }

    @Test
    void lyingFlatFacesUpWithItsTopAwayFromThePlayer() {
        for (double yaw : new double[]{0.0, 90.0, -45.0, 180.0}) {
            ReferenceRotation rotation = ReferenceRotation.flat(yaw);
            double radians = Math.toRadians(yaw);
            Vec3 look = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
            assertVector(new Vec3(0, 1, 0), rotation.normal());
            assertEquals(1.0, rotation.up().dot(look), 1.0E-9);
            assertEquals(90.0, rotation.tilt(), 1.0E-9);
            assertEquals(yaw, rotation.readerYaw(), 0.05);
        }
    }

    @Test
    void turnsChainAroundTheWorldAxes() {
        // Un cuarto de vuelta en Y (la derecha pasa a mirar al norte) y después otro en X: el orden importa y cada
        // giro va alrededor del eje del mundo, no del de la imagen.
        ReferenceRotation rotation = ReferenceRotation.IDENTITY.turned(Orientation.Y, 90.0).turned(Orientation.X,
                90.0);
        assertVector(new Vec3(0, 1, 0), rotation.right());
        assertVector(new Vec3(0, 0, 1), rotation.up());
        assertVector(new Vec3(1, 0, 0), rotation.normal());
        ReferenceRotation other = ReferenceRotation.IDENTITY.turned(Orientation.X, 90.0).turned(Orientation.Y,
                90.0);
        assertTrue(!rotation.sameAs(other, 1.0E-6));
        // Cuatro cuartos de vuelta vuelven al principio (el cuaternión opuesto gira igual).
        ReferenceRotation full = ReferenceRotation.IDENTITY;
        for (int turn = 0; turn < 4; turn++) {
            full = full.turned(Orientation.Z, 90.0);
        }
        assertTrue(full.sameAs(ReferenceRotation.IDENTITY, 1.0E-9));
        assertTrue(new ReferenceRotation(0, 0, 0, -1).sameAs(ReferenceRotation.IDENTITY, 1.0E-12));
    }

    @Test
    void brokenRotationsBecomeTheIdentity() {
        assertEquals(ReferenceRotation.IDENTITY, new ReferenceRotation(0, 0, 0, 0));
        assertEquals(ReferenceRotation.IDENTITY, new ReferenceRotation(Double.NaN, 0, 0, 1));
        ReferenceRotation scaled = new ReferenceRotation(0, 2, 0, 2);
        assertEquals(1.0, scaled.x() * scaled.x() + scaled.y() * scaled.y() + scaled.z() * scaled.z()
                + scaled.w() * scaled.w(), EPSILON);
    }

    @Test
    void cornersAndPixelsOfAPlane() {
        WorldFrame frame = new WorldFrame(new Vec3(0, 64, 0), ReferenceRotation.IDENTITY, 1.0, false);
        Vec3[] corners = frame.corners(32, 16);
        assertVector(new Vec3(-16, 72, 0), corners[0]);
        assertVector(new Vec3(16, 72, 0), corners[1]);
        assertVector(new Vec3(16, 56, 0), corners[2]);
        assertVector(new Vec3(-16, 56, 0), corners[3]);
        assertEquals(32.0, frame.widthBlocks(32), EPSILON);
        // Con 4 píxeles por bloque mide la cuarta parte.
        WorldFrame small = frame.withPixelsPerBlock(4.0);
        assertEquals(8.0, small.widthBlocks(32), EPSILON);
        assertEquals(4.0, small.heightBlocks(16), EPSILON);
        assertVector(new Vec3(-4, 66, 0), small.corners(32, 16)[0]);
    }

    @Test
    void pixelIsTheInverseOfPoint() {
        WorldFrame frame = new WorldFrame(new Vec3(10.5, 70.25, -3.0),
                ReferenceRotation.facing(33.0).turned(Orientation.X, 20.0), 3.5, true);
        for (double[] uv : new double[][]{{0, 0}, {120, 45}, {7.25, 89.5}, {-10, 200}}) {
            Vec3 point = frame.point(uv[0], uv[1], 120, 90);
            double[] back = frame.pixel(point, 120, 90);
            assertEquals(uv[0], back[0], 1.0E-9);
            assertEquals(uv[1], back[1], 1.0E-9);
        }
        // El centro de la imagen es el centro del plano.
        assertVector(frame.center(), frame.point(60, 45, 120, 90));
    }

    @Test
    void facingWestTheRightOfTheImageIsNorth() {
        WorldFrame frame = new WorldFrame(new Vec3(0, 64, 0), ReferenceRotation.facing(90.0), 1.0, false);
        assertVector(new Vec3(0, 0, -1), frame.right());
        assertVector(new Vec3(1, 0, 0), frame.normal());
        assertVector(new Vec3(0, 72, -16), frame.point(32, 0, 32, 16));
    }

    @Test
    void sizeLimits() {
        // Una foto sale de unos 32 bloques por su lado largo; el pixel art, a un píxel por bloque.
        WorldFrame frame = WorldFrame.at(new Vec3(0, 64, 0), 400, 200);
        assertEquals(400.0 / 32.0, frame.pixelsPerBlock(), EPSILON);
        assertEquals(32.0, frame.widthBlocks(400), EPSILON);
        assertEquals(1.0, WorldFrame.defaultPixelsPerBlock(64, 128), EPSILON);
        // Anchura pedida en bloques; ningún lado baja de 1/16 de bloque ni pasa de 2 048.
        assertEquals(40.0, frame.withWidthBlocks(40.0, 400, 200).widthBlocks(400), EPSILON);
        assertEquals(WorldFrame.MAX_SIDE_BLOCKS, frame.withWidthBlocks(1.0E9, 400, 200).widthBlocks(400), 1.0E-6);
        WorldFrame tiny = frame.withWidthBlocks(0.0, 400, 200);
        assertEquals(WorldFrame.MIN_SIDE_BLOCKS, tiny.heightBlocks(200), 1.0E-9);
        // Lo que no tiene sentido se recorta o vuelve a lo de serie.
        WorldFrame broken = new WorldFrame(new Vec3(Double.NaN, 1.0E12, 5), null, Double.POSITIVE_INFINITY, false);
        assertEquals(0.0, broken.center().x(), EPSILON);
        assertEquals(30_000_000.0, broken.center().y(), EPSILON);
        assertEquals(ReferenceRotation.IDENTITY, broken.rotation());
        assertEquals(1.0, broken.pixelsPerBlock(), EPSILON);
        assertEquals(WorldFrame.MAX_PIXELS_PER_BLOCK, frame.withPixelsPerBlock(1.0E9).pixelsPerBlock(), EPSILON);
    }

    @Test
    void sizesAndPosesInWords() {
        assertEquals("32", ReferenceFormat.blocks(32.0));
        assertEquals("12.5", ReferenceFormat.blocks(12.5));
        assertEquals("48.31", ReferenceFormat.blocks(48.3125));
        assertEquals("0.06", ReferenceFormat.blocks(1.0 / 16.0));
        assertEquals("0", ReferenceFormat.blocks(-0.001));
        assertEquals("0", ReferenceFormat.blocks(Double.NaN));
        assertEquals("standing", ReferenceFormat.pose(ReferenceRotation.facing(30.0)));
        assertEquals("flat", ReferenceFormat.pose(ReferenceRotation.flat(0.0)));
        assertEquals("upside_down", ReferenceFormat.pose(ReferenceRotation.flat(0.0).turned(Orientation.X, 180.0)));
        assertEquals("tilted", ReferenceFormat.pose(ReferenceRotation.IDENTITY.turned(Orientation.X, -30.0)));
        assertEquals("north", ReferenceFormat.cardinal(ReferenceRotation.IDENTITY));
        assertEquals("south", ReferenceFormat.cardinal(ReferenceRotation.facing(0.0)));
        assertEquals("west", ReferenceFormat.cardinal(ReferenceRotation.facing(90.0)));
        assertEquals("east", ReferenceFormat.cardinal(ReferenceRotation.flat(-90.0)));
        assertEquals(null, ReferenceFormat.cardinal(ReferenceRotation.facing(45.0)));
    }

    static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9, "x de " + actual);
        assertEquals(expected.y(), actual.y(), 1.0E-9, "y de " + actual);
        assertEquals(expected.z(), actual.z(), 1.0E-9, "z de " + actual);
    }
}
