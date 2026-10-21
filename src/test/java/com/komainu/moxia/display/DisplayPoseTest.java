package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.komainu.moxia.display.QuatTest.assertVec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayPoseTest {
    private static final Vec3 BLOCK_CENTER = new Vec3(0.5, 0.5, 0.5);

    @Test
    void aCenteredBlockFillsItsCell() {
        DisplayPose pose = DisplayPose.centered(new Vec3(10.5, 64.5, -3.5), Quat.IDENTITY, DisplayPose.ONE,
                BLOCK_CENTER);
        assertVec(new Vec3(10.5, 64.5, -3.5), pose.world(BLOCK_CENTER), 1.0E-12);
        assertVec(new Vec3(10, 64, -4), pose.world(new Vec3(0, 0, 0)), 1.0E-12);
        assertVec(new Vec3(11, 65, -3), pose.world(new Vec3(1, 1, 1)), 1.0E-12);
    }

    @Test
    void rotatingAroundItsCenterLeavesTheCenterWhereItWas() {
        DisplayPose pose = DisplayPose.centered(new Vec3(0.5, 70.5, 0.5), Quat.IDENTITY, new Vec3(0.5, 0.5, 0.5),
                BLOCK_CENTER);
        Vec3 center = pose.world(BLOCK_CENTER);
        DisplayPose turned = pose.rotated(Quat.axis(1, Math.toRadians(45)), center);
        assertVec(center, turned.world(BLOCK_CENTER), 1.0E-12);
        // La esquina de abajo al norte-oeste gira 45° en el plano: queda a la misma distancia del eje.
        Vec3 corner = turned.world(new Vec3(0, 0, 0));
        assertEquals(Math.hypot(0.25, 0.25), Math.hypot(corner.x() - 0.5, corner.z() - 0.5), 1.0E-12);
        assertEquals(70.25, corner.y(), 1.0E-12);
        // Con la entidad en el centro, la entidad no se mueve.
        assertVec(pose.position(), turned.position(), 1.0E-12);
    }

    @Test
    void foldingTheEntityRotationLooksTheSame() {
        Random random = new Random(3);
        for (int trial = 0; trial < 50; trial++) {
            DisplayPose pose = new DisplayPose(new Vec3(random.nextDouble() * 10, 60, 0), random.nextDouble() * 360,
                    random.nextDouble() * 180 - 90, new Vec3(random.nextGaussian(), random.nextGaussian(), 0),
                    QuatTest.random(random), new Vec3(0.5 + random.nextDouble(), 1, 2), QuatTest.random(random),
                    DisplayPose.Billboard.FIXED);
            DisplayPose folded = pose.folded();
            assertEquals(0.0, folded.yaw());
            assertEquals(0.0, folded.pitch());
            assertTrue(pose.sameAs(folded, 1.0E-9));
        }
        DisplayPose facing = DisplayPose.at(new Vec3(0, 0, 0)).withBillboard(DisplayPose.Billboard.CENTER);
        DisplayPose spun = new DisplayPose(facing.position(), 90, 0, facing.translation(), facing.left(),
                facing.scale(), facing.right(), DisplayPose.Billboard.CENTER);
        assertEquals(spun, spun.folded(), "con billboard la guiñada no se ve: no se dobla");
    }

    @Test
    void groupsRotateAsOnePiece() {
        Vec3 pivot = new Vec3(0.5, 64.5, 0.5);
        DisplayPose pot = DisplayPose.centered(pivot, Quat.IDENTITY, new Vec3(0.5, 0.5, 0.5), BLOCK_CENTER);
        DisplayPose plant = DisplayPose.centered(new Vec3(0.5, 65.0, 1.5), Quat.IDENTITY, DisplayPose.ONE,
                BLOCK_CENTER);
        Quat quarter = Quat.axis(1, Math.PI / 2);
        DisplayPose turnedPot = pot.rotated(quarter, pivot);
        DisplayPose turnedPlant = plant.rotated(quarter, pivot);
        // El de al sur pasa al este (+90° en Y lleva +Z a +X).
        assertVec(new Vec3(1.5, 65.0, 0.5), turnedPlant.world(BLOCK_CENTER), 1.0E-12);
        // Las distancias entre esquinas no cambian: es un giro rígido.
        Vec3[] before = {pot.world(new Vec3(0, 0, 0)), plant.world(new Vec3(1, 1, 1))};
        Vec3[] after = {turnedPot.world(new Vec3(0, 0, 0)), turnedPlant.world(new Vec3(1, 1, 1))};
        assertEquals(before[0].distance(before[1]), after[0].distance(after[1]), 1.0E-12);
    }

    @Test
    void scalingInItsAxesKeepsTheCenter() {
        DisplayPose pose = DisplayPose.centered(new Vec3(5.5, 64.5, 5.5), Quat.axis(1, 0.6), DisplayPose.ONE,
                BLOCK_CENTER);
        DisplayPose scaled = pose.scaledLocal(new Vec3(1, 0.5, 2), BLOCK_CENTER);
        assertVec(pose.world(BLOCK_CENTER), scaled.world(BLOCK_CENTER), 1.0E-12);
        assertVec(new Vec3(1, 0.5, 2), scaled.scale(), 1.0E-12);
        Vec3 size = DisplayVolume.block().size(scaled);
        assertVec(new Vec3(1, 0.5, 2), size, 1.0E-12);
    }

    @Test
    void scalingAroundAPointSpreadsAGroup() {
        Vec3 pivot = new Vec3(0, 64, 0);
        DisplayPose pose = DisplayPose.centered(new Vec3(2, 64, 0), Quat.IDENTITY, DisplayPose.ONE, BLOCK_CENTER);
        DisplayPose grown = pose.scaledAround(2.0, pivot);
        assertVec(new Vec3(4, 64, 0), grown.world(BLOCK_CENTER), 1.0E-12);
        assertVec(new Vec3(2, 2, 2), grown.scale(), 1.0E-12);
    }

    @Test
    void pastingRotationAndScaleDoesNotMoveTheCenter() {
        DisplayPose pose = DisplayPose.centered(new Vec3(3.5, 70.5, 3.5), Quat.IDENTITY, DisplayPose.ONE,
                BLOCK_CENTER);
        Quat tilt = Quat.axis(0, Math.toRadians(30)).multiply(Quat.axis(1, Math.toRadians(15)));
        DisplayPose pasted = pose.withRotationScale(tilt, Quat.IDENTITY, new Vec3(0.25, 0.5, 0.25), BLOCK_CENTER);
        assertVec(pose.world(BLOCK_CENTER), pasted.world(BLOCK_CENTER), 1.0E-12);
        assertEquals(0.0, pasted.left().angleTo(tilt), 1.0E-9);
    }

    @Test
    void notFiniteIsNotFinite() {
        DisplayPose pose = DisplayPose.at(new Vec3(0, 0, 0));
        assertTrue(pose.finite());
        assertFalse(pose.moved(new Vec3(Double.NaN, 0, 0)).finite());
        assertFalse(new DisplayPose(pose.position(), 0, 0, pose.translation(), new Quat(0, 0, 0, 0), pose.scale(),
                pose.right(), pose.billboard()).finite());
    }

    @Test
    void billboardsFaceTheCamera() {
        DisplayPose pose = DisplayPose.at(new Vec3(0, 0, 0)).withBillboard(DisplayPose.Billboard.CENTER);
        // Una cámara al sur mirando al norte (guiñada 180): el frente del display (+Z) mira hacia ella.
        Vec3 front = pose.model(180, 0).applyVector(new Vec3(0, 0, 1));
        assertVec(new Vec3(0, 0, 1), front, 1.0E-9);
        // Cámara al este mirando al oeste (guiñada 90): el frente mira al este.
        assertVec(new Vec3(1, 0, 0), pose.model(90, 0).applyVector(new Vec3(0, 0, 1)), 1.0E-9);
        // Vertical solo gira en Y: con la cámara mirando abajo, el frente sigue horizontal.
        DisplayPose vertical = pose.withBillboard(DisplayPose.Billboard.VERTICAL);
        assertEquals(0.0, vertical.model(90, 60).applyVector(new Vec3(0, 0, 1)).y(), 1.0E-9);
    }
}
