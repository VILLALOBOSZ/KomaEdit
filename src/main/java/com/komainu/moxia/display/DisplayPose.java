package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;

/**
 * Dónde está y cómo se ve un display: la posición y el giro de la entidad, su transformación (traslación, giro
 * izquierdo, escala y giro derecho, como {@code AffineTransformation}) y su modo billboard. Minecraft lo dibuja con
 *
 * <pre>mundo(p) = posición + Rentidad · (traslación + izquierdo · escala · derecho · p)</pre>
 *
 * <p>donde {@code Rentidad} es el giro de la guiñada y el cabeceo (solo en {@link Billboard#FIXED}; con los demás modos
 * lo pone la cámara). Las ediciones se hacen sobre el display ya «doblado» ({@link #folded}): guiñada y cabeceo a 0 y
 * su giro dentro del izquierdo, que se ve igual y deja un solo giro que tocar.</p>
 *
 * <p>Lógica pura e inmutable. Mover, girar y escalar dejan quieto el punto que se pida (el centro del modelo o el de un
 * grupo): así un objeto gira sobre sí mismo aunque su entidad esté en otra esquina.</p>
 */
public record DisplayPose(Vec3 position, double yaw, double pitch, Vec3 translation, Quat left, Vec3 scale,
                          Quat right, Billboard billboard) {
    public static final Vec3 ONE = new Vec3(1, 1, 1);
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    /** Los modos billboard de Minecraft, en su orden. */
    public enum Billboard {
        FIXED("fixed"),
        VERTICAL("vertical"),
        HORIZONTAL("horizontal"),
        CENTER("center");

        private final String id;

        Billboard(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Billboard of(int ordinal) {
            Billboard[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FIXED;
        }

        /** Por su nombre de Minecraft o en español ({@code fijo}, {@code vertical}, {@code horizontal}, {@code centro}). */
        public static Billboard parse(String text) {
            String value = text == null ? "" : text.strip().toLowerCase(java.util.Locale.ROOT);
            return switch (value) {
                case "fixed", "fijo", "off", "no" -> FIXED;
                case "vertical", "v" -> VERTICAL;
                case "horizontal", "h" -> HORIZONTAL;
                case "center", "centro", "centrado", "c", "on" -> CENTER;
                default -> throw new IllegalArgumentException("Billboard desconocido: " + text
                        + " (fixed, vertical, horizontal o center)");
            };
        }
    }

    public DisplayPose {
        if (position == null || translation == null || left == null || scale == null || right == null
                || billboard == null) {
            throw new IllegalArgumentException("Pose incompleta");
        }
    }

    /** Un display en {@code position} sin giro ni escala. */
    public static DisplayPose at(Vec3 position) {
        return new DisplayPose(position, 0, 0, ZERO, Quat.IDENTITY, ONE, Quat.IDENTITY, Billboard.FIXED);
    }

    /**
     * Un display con la entidad en el centro de su modelo: la traslación lleva {@code localCenter} (el centro del
     * modelo, en sus coordenadas) a la posición de la entidad, así gira y escala sobre ella.
     */
    public static DisplayPose centered(Vec3 position, Quat left, Vec3 scale, Vec3 localCenter) {
        Vec3 offset = left.rotate(multiply(scale, localCenter)).multiply(-1.0);
        return new DisplayPose(position, 0, 0, offset, left.normalized(), scale, Quat.IDENTITY, Billboard.FIXED);
    }

    /** Giro de la entidad (guiñada y cabeceo) tal como lo aplica Minecraft en un display fijo. */
    public Quat entityRotation() {
        return Quat.entity(yaw, pitch);
    }

    /**
     * Con la guiñada y el cabeceo dentro del giro izquierdo y la traslación (se ve igual). En los modos billboard no
     * fijos la guiñada y el cabeceo no se ven, así que se dejan como están.
     */
    public DisplayPose folded() {
        if (billboard != Billboard.FIXED || yaw == 0.0 && pitch == 0.0) {
            return this;
        }
        Quat entity = entityRotation();
        return new DisplayPose(position, 0, 0, entity.rotate(translation), entity.multiply(left).normalized(), scale,
                right, billboard);
    }

    /** La transformación de Minecraft sin la entidad: traslación · izquierdo · escala · derecho. */
    public Affine transformation() {
        return Affine.translation(translation).multiply(Affine.rotation(left)).multiply(Affine.scale(scale))
                .multiply(Affine.rotation(right));
    }

    /** Del modelo al mundo en un display fijo. */
    public Affine model() {
        return model(entityRotation());
    }

    /**
     * Del modelo al mundo con la cámara que lo mira (para los modos billboard).
     *
     * @param cameraYaw   y {@code cameraPitch}, en grados, los de la cámara de Minecraft
     */
    public Affine model(double cameraYaw, double cameraPitch) {
        Quat facing = switch (billboard) {
            case FIXED -> entityRotation();
            case VERTICAL -> Quat.yxz(-Math.toRadians(cameraYaw - 180.0), Math.toRadians(pitch), 0.0);
            case HORIZONTAL -> Quat.yxz(-Math.toRadians(yaw), Math.toRadians(-cameraPitch), 0.0);
            case CENTER -> Quat.yxz(-Math.toRadians(cameraYaw - 180.0), Math.toRadians(-cameraPitch), 0.0);
        };
        return model(facing);
    }

    private Affine model(Quat facing) {
        return Affine.translation(position).multiply(Affine.rotation(facing)).multiply(transformation());
    }

    /** Dónde cae en el mundo el punto {@code local} del modelo (en un display fijo). */
    public Vec3 world(Vec3 local) {
        return model().apply(local);
    }

    /** El marco del objeto en el mundo: el giro izquierdo después del de la entidad (el «local» del gizmo). */
    public Quat frame() {
        return entityRotation().multiply(left).normalized();
    }

    // ---------------------------------------------------------------- ediciones

    public DisplayPose moved(Vec3 delta) {
        return new DisplayPose(position.add(delta), yaw, pitch, translation, left, scale, right, billboard);
    }

    /**
     * Girada {@code delta} (en el marco del mundo) alrededor de {@code pivot}: la entidad gira alrededor del pivote y
     * la traslación y el giro izquierdo giran con ella, así el modelo entero da la vuelta sobre ese punto.
     */
    public DisplayPose rotated(Quat delta, Vec3 pivot) {
        DisplayPose base = folded();
        Quat unit = delta.normalized();
        Vec3 position = pivot.add(unit.rotate(base.position.subtract(pivot)));
        return new DisplayPose(position, 0, 0, unit.rotate(base.translation), unit.multiply(base.left).normalized(),
                base.scale, base.right, base.billboard);
    }

    /**
     * Escalada en sus propios ejes por {@code factors}, con el punto {@code localPivot} del modelo quieto (su centro,
     * normalmente).
     */
    public DisplayPose scaledLocal(Vec3 factors, Vec3 localPivot) {
        DisplayPose base = folded();
        Vec3 scale = multiply(base.scale, factors);
        return base.withScale(scale, localPivot);
    }

    /** Con otra escala, sin mover el punto {@code localPivot} del modelo. */
    public DisplayPose withScale(Vec3 scale, Vec3 localPivot) {
        DisplayPose base = folded();
        Vec3 before = base.left.rotate(multiply(base.scale, base.right.rotate(localPivot)));
        Vec3 after = base.left.rotate(multiply(scale, base.right.rotate(localPivot)));
        return new DisplayPose(base.position, 0, 0, base.translation.add(before).subtract(after), base.left, scale,
                base.right, base.billboard);
    }

    /** Escala por igual alrededor de un punto del mundo (un grupo crece o encoge junto). */
    public DisplayPose scaledAround(double factor, Vec3 pivot) {
        DisplayPose base = folded();
        Vec3 position = pivot.add(base.position.subtract(pivot).multiply(factor));
        return new DisplayPose(position, 0, 0, base.translation.multiply(factor), base.left,
                base.scale.multiply(factor), base.right, base.billboard);
    }

    /**
     * Con el giro y la escala de otro (pegar la transformación), sin mover el punto {@code localPivot} del modelo en el
     * mundo.
     */
    public DisplayPose withRotationScale(Quat left, Quat right, Vec3 scale, Vec3 localPivot) {
        DisplayPose base = folded();
        Vec3 anchor = base.world(localPivot);
        DisplayPose turned = new DisplayPose(base.position, 0, 0, base.translation, left.normalized(), scale,
                right.normalized(), base.billboard);
        return turned.moved(anchor.subtract(turned.world(localPivot)));
    }

    public DisplayPose withBillboard(Billboard value) {
        return new DisplayPose(position, yaw, pitch, translation, left, scale, right, value);
    }

    /** Todos los números son finitos y los giros no son nulos. */
    public boolean finite() {
        return finite(position) && Double.isFinite(yaw) && Double.isFinite(pitch) && finite(translation)
                && finite(scale) && left.finite() && right.finite() && left.length() > 1.0E-6
                && right.length() > 1.0E-6;
    }

    /** ¿Se ve igual que {@code other}, a {@code tolerance} bloques en las esquinas del modelo? */
    public boolean sameAs(DisplayPose other, double tolerance) {
        if (billboard != other.billboard) {
            return false;
        }
        Vec3[] mine = model().unitCorners();
        Vec3[] theirs = other.model().unitCorners();
        for (int index = 0; index < mine.length; index++) {
            if (mine[index].distance(theirs[index]) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    static Vec3 multiply(Vec3 a, Vec3 b) {
        return new Vec3(a.x() * b.x(), a.y() * b.y(), a.z() * b.z());
    }
}
