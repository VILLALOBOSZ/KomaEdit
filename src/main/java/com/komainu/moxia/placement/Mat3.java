package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

/**
 * Matriz 3×3 inmutable para orientar las colocaciones. Las columnas son los ejes locales de la estructura
 * expresados en coordenadas del mundo.
 */
public record Mat3(
        double m00, double m01, double m02,
        double m10, double m11, double m12,
        double m20, double m21, double m22
) {
    public static final Mat3 IDENTITY = new Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1);

    public static Mat3 columns(Vec3 x, Vec3 y, Vec3 z) {
        return new Mat3(x.x(), y.x(), z.x(), x.y(), y.y(), z.y(), x.z(), y.z(), z.z());
    }

    public static Mat3 diagonal(double x, double y, double z) {
        return new Mat3(x, 0, 0, 0, y, 0, 0, 0, z);
    }

    /** Giro de {@code degrees} grados alrededor de {@code axis} con la regla de la mano derecha. */
    public static Mat3 rotation(Vec3 axis, double degrees) {
        double length = axis.length();
        if (length < 1.0E-9 || degrees % 360.0 == 0.0) {
            return IDENTITY;
        }
        double x = axis.x() / length;
        double y = axis.y() / length;
        double z = axis.z() / length;
        double radians = Math.toRadians(degrees);
        double cos = exact(Math.cos(radians));
        double sin = exact(Math.sin(radians));
        double t = 1.0 - cos;
        return new Mat3(
                t * x * x + cos, t * x * y - sin * z, t * x * z + sin * y,
                t * x * y + sin * z, t * y * y + cos, t * y * z - sin * x,
                t * x * z - sin * y, t * y * z + sin * x, t * z * z + cos
        );
    }

    /** Cuartos de vuelta alrededor de un eje local: 0 = x, 1 = y, 2 = z. */
    public static Mat3 quarterTurns(int axis, int turns) {
        Vec3 direction = axis == 0 ? new Vec3(1, 0, 0) : axis == 1 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        return rotation(direction, 90.0 * Math.floorMod(turns, 4));
    }

    /** Evita restos como 6e-17 en cos(90°), que romperían la copia exacta de un portapapeles. */
    private static double exact(double value) {
        double rounded = Math.rint(value);
        return Math.abs(value - rounded) < 1.0E-12 ? rounded : value;
    }

    public Vec3 apply(Vec3 vector) {
        return apply(vector.x(), vector.y(), vector.z());
    }

    public Vec3 apply(double x, double y, double z) {
        return new Vec3(
                m00 * x + m01 * y + m02 * z,
                m10 * x + m11 * y + m12 * z,
                m20 * x + m21 * y + m22 * z
        );
    }

    public Mat3 multiply(Mat3 other) {
        return new Mat3(
                m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
                m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
                m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
                m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
                m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
                m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
                m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
                m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
                m20 * other.m02 + m21 * other.m12 + m22 * other.m22
        );
    }

    public Mat3 transpose() {
        return new Mat3(m00, m10, m20, m01, m11, m21, m02, m12, m22);
    }

    public double determinant() {
        return m00 * (m11 * m22 - m12 * m21)
                - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
    }

    public Vec3 column(int index) {
        return switch (index) {
            case 0 -> new Vec3(m00, m10, m20);
            case 1 -> new Vec3(m01, m11, m21);
            default -> new Vec3(m02, m12, m22);
        };
    }
}
