package com.komainu.moxia.reference;

import com.komainu.moxia.gizmo.Orientation;
import com.komainu.moxia.shape.Vec3;

/**
 * El giro de una imagen de referencia en el mundo, como cuaternión unidad. Sin giro, la imagen queda de pie con la
 * cara mirando al sur (+Z): se lee bien desde el sur mirando al norte. Su derecha es +X (este) y su arriba, +Y.
 *
 * <p>Los anillos del gizmo giran alrededor de los ejes del mundo, así que cada giro nuevo se aplica después de los
 * que ya tenía ({@link #turned}). Un cuaternión no se enreda al encadenar giros como los ángulos de Euler.</p>
 */
public record ReferenceRotation(double x, double y, double z, double w) {
    public static final ReferenceRotation IDENTITY = new ReferenceRotation(0.0, 0.0, 0.0, 1.0);
    private static final Vec3 RIGHT = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 FRONT = new Vec3(0, 0, 1);

    public ReferenceRotation {
        double length = Math.sqrt(x * x + y * y + z * z + w * w);
        if (!(length > 1.0E-9) || !Double.isFinite(length)) {
            x = 0.0;
            y = 0.0;
            z = 0.0;
            w = 1.0;
        } else if (Math.abs(length - 1.0) > 1.0E-12) {
            // Ya unidad (lo guardado vuelve tal cual); si no, se normaliza.
            x /= length;
            y /= length;
            z /= length;
            w /= length;
        }
    }

    /** Giro de {@code degrees} alrededor de un eje del mundo, por la regla de la mano derecha. */
    public static ReferenceRotation about(int axis, double degrees) {
        double half = Math.toRadians(degrees) * 0.5;
        double sine = Math.sin(half);
        return switch (axis) {
            case Orientation.X -> new ReferenceRotation(sine, 0.0, 0.0, Math.cos(half));
            case Orientation.Y -> new ReferenceRotation(0.0, sine, 0.0, Math.cos(half));
            case Orientation.Z -> new ReferenceRotation(0.0, 0.0, sine, Math.cos(half));
            default -> throw new IllegalArgumentException("Eje desconocido: " + axis);
        };
    }

    /**
     * De pie, de cara a quien mira con esa guiñada (la de Minecraft: 0 mira al sur, 90 al oeste). Así la imagen sale
     * derecha y legible delante del jugador.
     */
    public static ReferenceRotation facing(double yawDegrees) {
        return about(Orientation.Y, 180.0 - yawDegrees);
    }

    /** Tumbada en el suelo, boca arriba, con su parte de arriba hacia donde mira el jugador. */
    public static ReferenceRotation flat(double yawDegrees) {
        return about(Orientation.X, -90.0).then(facing(yawDegrees));
    }

    /** Este giro y después {@code next}. */
    public ReferenceRotation then(ReferenceRotation next) {
        return next.multiply(this);
    }

    /** El giro de ahora seguido de uno de {@code degrees} alrededor de un eje del mundo. */
    public ReferenceRotation turned(int axis, double degrees) {
        return then(about(axis, degrees));
    }

    /** Producto de Hamilton: primero {@code other}, después este. */
    public ReferenceRotation multiply(ReferenceRotation other) {
        return new ReferenceRotation(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z);
    }

    /** El vector girado. */
    public Vec3 apply(Vec3 vector) {
        // v' = v + 2w (q × v) + 2 q × (q × v)
        Vec3 axis = new Vec3(x, y, z);
        Vec3 first = axis.cross(vector).multiply(2.0);
        return vector.add(first.multiply(w)).add(axis.cross(first));
    }

    /** La derecha de la imagen en el mundo. */
    public Vec3 right() {
        return apply(RIGHT);
    }

    /** El arriba de la imagen en el mundo. */
    public Vec3 up() {
        return apply(UP);
    }

    /** Hacia dónde mira la cara de la imagen. */
    public Vec3 normal() {
        return apply(FRONT);
    }

    /**
     * La guiñada de Minecraft (0 = sur, 90 = oeste, 180 o -180 = norte, -90 = este) de quien la lee de frente,
     * redondeada a la décima: de pie, quien mira contra su cara; tumbada, quien mira hacia su parte de arriba. Es la
     * inversa de {@link #facing} y de {@link #flat}.
     */
    public double readerYaw() {
        Vec3 normal = normal();
        Vec3 look = Math.abs(normal.y()) > 0.999 ? up() : normal.multiply(-1.0);
        double yaw = round(Math.toDegrees(Math.atan2(-look.x(), look.z())));
        return yaw == -180.0 ? 180.0 : yaw;
    }

    /** Cuánto se inclina la cara hacia arriba: 0 de pie, 90 tumbada boca arriba, -90 boca abajo. */
    public double tilt() {
        return round(Math.toDegrees(Math.asin(Math.clamp(normal().y(), -1.0, 1.0))));
    }

    /** El mismo giro (un cuaternión y su opuesto giran igual), con margen. */
    public boolean sameAs(ReferenceRotation other, double epsilon) {
        double dot = x * other.x + y * other.y + z * other.z + w * other.w;
        return 1.0 - Math.abs(dot) <= epsilon;
    }

    private static double round(double degrees) {
        double value = Math.round(degrees * 10.0) / 10.0;
        return value == -0.0 ? 0.0 : value;
    }
}
