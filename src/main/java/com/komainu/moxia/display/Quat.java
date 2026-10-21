package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;

/**
 * Un cuaternión de giro (x, y, z, w), con las mismas convenciones que el {@code Quaternionf} de JOML que usan los
 * displays de Minecraft: {@code a.multiply(b)} gira primero con {@code b} y después con {@code a}. Lógica pura, para
 * que el cliente, el servidor y las pruebas giren con las mismas cuentas.
 */
public record Quat(double x, double y, double z, double w) {
    public static final Quat IDENTITY = new Quat(0, 0, 0, 1);
    private static final double EPSILON = 1.0E-9;

    /** Giro de {@code radians} alrededor de un eje (no hace falta que sea unitario). */
    public static Quat axisAngle(Vec3 axis, double radians) {
        Vec3 unit = axis.normalized();
        if (unit.length() < 0.5) {
            return IDENTITY;
        }
        double half = radians * 0.5;
        double sin = Math.sin(half);
        return new Quat(unit.x() * sin, unit.y() * sin, unit.z() * sin, Math.cos(half));
    }

    /** Giro alrededor del eje X (0), Y (1) o Z (2) del mundo. */
    public static Quat axis(int axis, double radians) {
        double half = radians * 0.5;
        double sin = Math.sin(half);
        double cos = Math.cos(half);
        return switch (axis) {
            case 0 -> new Quat(sin, 0, 0, cos);
            case 1 -> new Quat(0, sin, 0, cos);
            case 2 -> new Quat(0, 0, sin, cos);
            default -> throw new IllegalArgumentException("Eje desconocido: " + axis);
        };
    }

    /** {@code rotationYXZ} de JOML: Y, después X y después Z (en ese orden de multiplicación: Ry·Rx·Rz). */
    public static Quat yxz(double yRadians, double xRadians, double zRadians) {
        return axis(1, yRadians).multiply(axis(0, xRadians)).multiply(axis(2, zRadians));
    }

    /** {@code rotationXYZ} de JOML (Rx·Ry·Rz): el giro de las transformaciones de los modelos de objeto. */
    public static Quat xyz(double xRadians, double yRadians, double zRadians) {
        return axis(0, xRadians).multiply(axis(1, yRadians)).multiply(axis(2, zRadians));
    }

    /**
     * El giro que da Minecraft a un display fijo por la guiñada y el cabeceo de la entidad
     * ({@code rotationYXZ(-yaw, pitch, 0)} en radianes).
     */
    public static Quat entity(double yawDegrees, double pitchDegrees) {
        return yxz(-Math.toRadians(yawDegrees), Math.toRadians(pitchDegrees), 0.0);
    }

    /** {@code this · other}: primero {@code other}, después {@code this}. */
    public Quat multiply(Quat other) {
        return new Quat(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z);
    }

    public Quat conjugate() {
        return new Quat(-x, -y, -z, w);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z + w * w);
    }

    /** El mismo giro con largo 1; un cuaternión nulo o no finito vale la identidad. */
    public Quat normalized() {
        double length = length();
        if (!(length > EPSILON) || !Double.isFinite(length)) {
            return IDENTITY;
        }
        return new Quat(x / length, y / length, z / length, w / length);
    }

    public boolean finite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(w);
    }

    /** El vector girado. */
    public Vec3 rotate(Vec3 vector) {
        // v' = v + 2w(q×v) + 2 q×(q×v), con q la parte vectorial.
        double cx = y * vector.z() - z * vector.y();
        double cy = z * vector.x() - x * vector.z();
        double cz = x * vector.y() - y * vector.x();
        double ccx = y * cz - z * cy;
        double ccy = z * cx - x * cz;
        double ccz = x * cy - y * cx;
        return new Vec3(vector.x() + 2.0 * (w * cx + ccx), vector.y() + 2.0 * (w * cy + ccy),
                vector.z() + 2.0 * (w * cz + ccz));
    }

    /** El eje {@code axis} (0, 1, 2) del marco girado. */
    public Vec3 axisVector(int axis) {
        return rotate(switch (axis) {
            case 0 -> new Vec3(1, 0, 0);
            case 1 -> new Vec3(0, 1, 0);
            case 2 -> new Vec3(0, 0, 1);
            default -> throw new IllegalArgumentException("Eje desconocido: " + axis);
        });
    }

    /** Ángulo entre los dos giros, en radianes (0 si son el mismo; {@code q} y {@code -q} cuentan como iguales). */
    public double angleTo(Quat other) {
        double dot = Math.abs(x * other.x + y * other.y + z * other.z + w * other.w)
                / Math.max(EPSILON, length() * other.length());
        return 2.0 * Math.acos(Math.min(1.0, dot));
    }

    /** Matriz 3×3 por filas. */
    public double[] matrix() {
        Quat unit = normalized();
        double xx = unit.x * unit.x;
        double yy = unit.y * unit.y;
        double zz = unit.z * unit.z;
        double xy = unit.x * unit.y;
        double xz = unit.x * unit.z;
        double yz = unit.y * unit.z;
        double wx = unit.w * unit.x;
        double wy = unit.w * unit.y;
        double wz = unit.w * unit.z;
        return new double[]{
                1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy),
                2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx),
                2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy)};
    }

    /**
     * Ángulos de Euler en grados (X, Y y Z, como {@link #xyz}) para enseñarlos al jugador. Cerca de ±90° en Y el
     * reparto entre X y Z no es único; el giro sí.
     */
    public double[] eulerDegrees() {
        double[] m = matrix();
        double sy = Math.clamp(m[2], -1.0, 1.0);
        double ry = Math.asin(sy);
        double rx;
        double rz;
        if (Math.abs(sy) < 0.999_999) {
            rx = Math.atan2(-m[5], m[8]);
            rz = Math.atan2(-m[1], m[0]);
        } else {
            rx = Math.atan2(m[7], m[4]);
            rz = 0.0;
        }
        return new double[]{Math.toDegrees(rx), Math.toDegrees(ry), Math.toDegrees(rz)};
    }
}
