package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;

/**
 * Transformación afín de 3D (matriz 3×3 y traslación), por filas: {@code m[0..2] tx}, {@code m[3..5] ty},
 * {@code m[6..8] tz}. Es inmutable. Sirve para llevar el modelo de un display al mundo y para el rayo al revés.
 */
public final class Affine {
    public static final Affine IDENTITY = new Affine(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, 0, 0, 0);
    private final double[] m;
    private final double tx;
    private final double ty;
    private final double tz;

    private Affine(double[] m, double tx, double ty, double tz) {
        this.m = m;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
    }

    public static Affine translation(Vec3 offset) {
        return new Affine(IDENTITY.m, offset.x(), offset.y(), offset.z());
    }

    public static Affine rotation(Quat rotation) {
        return new Affine(rotation.matrix(), 0, 0, 0);
    }

    public static Affine scale(Vec3 scale) {
        return new Affine(new double[]{scale.x(), 0, 0, 0, scale.y(), 0, 0, 0, scale.z()}, 0, 0, 0);
    }

    /** {@code this · other}: primero {@code other}, después {@code this}. */
    public Affine multiply(Affine other) {
        double[] a = m;
        double[] b = other.m;
        double[] r = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                r[row * 3 + column] = a[row * 3] * b[column] + a[row * 3 + 1] * b[3 + column]
                        + a[row * 3 + 2] * b[6 + column];
            }
        }
        Vec3 moved = apply(new Vec3(other.tx, other.ty, other.tz));
        return new Affine(r, moved.x(), moved.y(), moved.z());
    }

    public Vec3 apply(Vec3 point) {
        return new Vec3(m[0] * point.x() + m[1] * point.y() + m[2] * point.z() + tx,
                m[3] * point.x() + m[4] * point.y() + m[5] * point.z() + ty,
                m[6] * point.x() + m[7] * point.y() + m[8] * point.z() + tz);
    }

    /** Un vector (sin la traslación). */
    public Vec3 applyVector(Vec3 vector) {
        return new Vec3(m[0] * vector.x() + m[1] * vector.y() + m[2] * vector.z(),
                m[3] * vector.x() + m[4] * vector.y() + m[5] * vector.z(),
                m[6] * vector.x() + m[7] * vector.y() + m[8] * vector.z());
    }

    public Vec3 translationPart() {
        return new Vec3(tx, ty, tz);
    }

    public double determinant() {
        return m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6])
                + m[2] * (m[3] * m[7] - m[4] * m[6]);
    }

    /** La inversa, o {@code null} si la matriz no tiene (una escala 0 en algún eje). */
    public Affine inverse() {
        double determinant = determinant();
        if (Math.abs(determinant) < 1.0E-12 || !Double.isFinite(determinant)) {
            return null;
        }
        double inverse = 1.0 / determinant;
        double[] r = {
                (m[4] * m[8] - m[5] * m[7]) * inverse, (m[2] * m[7] - m[1] * m[8]) * inverse,
                (m[1] * m[5] - m[2] * m[4]) * inverse,
                (m[5] * m[6] - m[3] * m[8]) * inverse, (m[0] * m[8] - m[2] * m[6]) * inverse,
                (m[2] * m[3] - m[0] * m[5]) * inverse,
                (m[3] * m[7] - m[4] * m[6]) * inverse, (m[1] * m[6] - m[0] * m[7]) * inverse,
                (m[0] * m[4] - m[1] * m[3]) * inverse};
        Affine rotation = new Affine(r, 0, 0, 0);
        Vec3 back = rotation.applyVector(new Vec3(-tx, -ty, -tz));
        return new Affine(r, back.x(), back.y(), back.z());
    }

    /** Las ocho esquinas del cubo unidad [0, 1]³ llevadas por la transformación. */
    public Vec3[] unitCorners() {
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < 8; index++) {
            corners[index] = apply(new Vec3(index & 1, index >> 1 & 1, index >> 2 & 1));
        }
        return corners;
    }

    /** Lo que mide cada eje de la matriz (la escala de cada columna). */
    public Vec3 columnLengths() {
        return new Vec3(Math.sqrt(m[0] * m[0] + m[3] * m[3] + m[6] * m[6]),
                Math.sqrt(m[1] * m[1] + m[4] * m[4] + m[7] * m[7]),
                Math.sqrt(m[2] * m[2] + m[5] * m[5] + m[8] * m[8]));
    }
}
