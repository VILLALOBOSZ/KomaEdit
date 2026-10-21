package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuatTest {
    private static final double EPSILON = 1.0E-9;

    static void assertVec(Vec3 expected, Vec3 actual, double tolerance) {
        assertVec(expected, actual, tolerance, "");
    }

    static void assertVec(Vec3 expected, Vec3 actual, double tolerance, String message) {
        assertTrue(expected.distance(actual) <= tolerance, message + " · esperado " + expected + " y salió "
                + actual);
    }

    @Test
    void quarterTurnAroundYTakesSouthToEast() {
        // Regla de la mano derecha: +90° en Y lleva +Z a +X.
        assertVec(new Vec3(1, 0, 0), Quat.axis(1, Math.PI / 2).rotate(new Vec3(0, 0, 1)), EPSILON);
        assertVec(new Vec3(0, 0, -1), Quat.axis(1, Math.PI / 2).rotate(new Vec3(1, 0, 0)), EPSILON);
        assertVec(new Vec3(0, 1, 0), Quat.axis(0, Math.PI / 2).rotate(new Vec3(0, 0, -1)), EPSILON);
        assertVec(new Vec3(0, 1, 0), Quat.axis(2, Math.PI / 2).rotate(new Vec3(1, 0, 0)), EPSILON);
    }

    @Test
    void axisAngleMatchesTheAxisShortcut() {
        Quat general = Quat.axisAngle(new Vec3(0, 3, 0), 0.7);
        Quat shortcut = Quat.axis(1, 0.7);
        assertEquals(0.0, general.angleTo(shortcut), 1.0E-9);
        assertEquals(Quat.IDENTITY, Quat.axisAngle(new Vec3(0, 0, 0), 1.0), "eje nulo: sin giro");
    }

    @Test
    void multiplyAppliesTheRightFactorFirst() {
        Random random = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            Quat a = random(random);
            Quat b = random(random);
            Vec3 vector = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            assertVec(a.rotate(b.rotate(vector)), a.multiply(b).rotate(vector), 1.0E-9);
        }
    }

    @Test
    void matrixRotatesLikeTheQuaternion() {
        Random random = new Random(11);
        for (int trial = 0; trial < 100; trial++) {
            Quat quat = random(random);
            Vec3 vector = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            assertVec(quat.rotate(vector), Affine.rotation(quat).apply(vector), 1.0E-9);
        }
    }

    @Test
    void entityYawTurnsTheFrontLikeMinecraft() {
        // Guiñada 90 mira al oeste: el frente (+Z) del display fijo pasa a −X.
        assertVec(new Vec3(-1, 0, 0), Quat.entity(90, 0).rotate(new Vec3(0, 0, 1)), 1.0E-9);
        // Cabeceo 90 (mirando abajo): el frente baja.
        assertVec(new Vec3(0, -1, 0), Quat.entity(0, 90).rotate(new Vec3(0, 0, 1)), 1.0E-9);
        // yxz es Ry·Rx·Rz, como rotationYXZ de JOML.
        Quat composed = Quat.axis(1, 0.4).multiply(Quat.axis(0, -0.3)).multiply(Quat.axis(2, 1.1));
        assertEquals(0.0, Quat.yxz(0.4, -0.3, 1.1).angleTo(composed), 1.0E-12);
    }

    @Test
    void eulerAnglesComeBackToTheSameRotation() {
        Random random = new Random(5);
        for (int trial = 0; trial < 100; trial++) {
            Quat quat = random(random);
            double[] euler = quat.eulerDegrees();
            Quat back = Quat.xyz(Math.toRadians(euler[0]), Math.toRadians(euler[1]), Math.toRadians(euler[2]));
            assertEquals(0.0, quat.angleTo(back), 1.0E-6, "giro " + quat);
        }
        assertArrayEquals(new double[]{0, 45, 0}, Quat.axis(1, Math.toRadians(45)).eulerDegrees(), 1.0E-9);
    }

    @Test
    void anglesBetweenRotations() {
        assertEquals(Math.toRadians(30), Quat.axis(1, 0).angleTo(Quat.axis(1, Math.toRadians(30))), 1.0E-9);
        Quat quat = Quat.axis(0, 1.0);
        Quat negated = new Quat(-quat.x(), -quat.y(), -quat.z(), -quat.w());
        assertEquals(0.0, quat.angleTo(negated), 1.0E-6, "q y −q son el mismo giro");
        assertEquals(Quat.IDENTITY, new Quat(0, 0, 0, 0).normalized(), "un cuaternión nulo no gira");
        assertEquals(Quat.IDENTITY, new Quat(Double.NaN, 0, 0, 1).normalized());
    }

    static Quat random(Random random) {
        return new Quat(random.nextGaussian(), random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                .normalized();
    }
}
