package com.komainu.moxia.reference;

import com.komainu.moxia.gizmo.GizmoHandle;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.gizmo.Orientation;
import com.komainu.moxia.shape.Vec3;

/**
 * Arrastrar una pieza del gizmo de una imagen de referencia: se agarra con un rayo ({@link #begin}) y cada rayo
 * nuevo da el plano movido, girado o escalado ({@link #drag}). Lógica pura, probada con rayos a mano.
 *
 * <p>Pasos, para que lo calcado cuadre con los bloques: mover va de bloque en bloque, girar de 15° en 15° y escalar
 * deja la anchura en bloques enteros. Con {@code fine} (Ctrl) se afina: 1/16 de bloque, 1° y 1/16 de bloque.</p>
 */
public final class ReferenceDrag {
    public static final double FINE_MOVE = 1.0 / 16.0;
    public static final double ROTATE_STEP = 15.0;
    public static final double FINE_ROTATE_STEP = 1.0;
    /** Si el rayo va casi paralelo al plano de la imagen, el cubo la mueve por el suelo. */
    private static final double GRAZING = 0.12;

    private final ReferenceGizmo.Handle handle;
    private final WorldFrame start;
    private final int pixelWidth;
    private final int pixelHeight;
    private final Vec3 planeNormal;
    private final Vec3 planeU;
    private final Vec3 planeV;
    private final Vec3 grab;
    private final double along;
    private double lastAngle;
    private double turned;
    private double degrees;
    private final double cornerReach;

    private ReferenceDrag(ReferenceGizmo.Handle handle, WorldFrame start, int pixelWidth, int pixelHeight,
                          Vec3 planeNormal, Vec3 planeU, Vec3 planeV, Vec3 grab, double along, double angle,
                          double cornerReach) {
        this.handle = handle;
        this.start = start;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.planeNormal = planeNormal;
        this.planeU = planeU;
        this.planeV = planeV;
        this.grab = grab;
        this.along = along;
        this.lastAngle = angle;
        this.cornerReach = cornerReach;
    }

    /** Empieza a arrastrar {@code handle}; {@code null} si el rayo no sirve (va paralelo al eje o al plano). */
    public static ReferenceDrag begin(ReferenceGizmo.Handle handle, GizmoMath.Ray ray, WorldFrame frame,
                                      int pixelWidth, int pixelHeight) {
        if (handle == null || handle.none()) {
            return null;
        }
        Vec3 center = frame.center();
        if (handle.isCorner()) {
            Vec3 corner = frame.corners(pixelWidth, pixelHeight)[handle.corner()];
            Vec3 normal = frame.normal();
            Vec3 hit = onPlane(ray, center, normal);
            if (hit == null) {
                return null;
            }
            double reach = corner.subtract(center).length();
            return new ReferenceDrag(handle, frame, pixelWidth, pixelHeight, normal, frame.right(), frame.up(),
                    hit, 0.0, 0.0, reach);
        }
        GizmoHandle gizmo = handle.gizmo();
        if (gizmo.rotates()) {
            Vec3 axis = GizmoMath.unit(gizmo.axis());
            Vec3 hit = onPlane(ray, center, axis);
            if (hit == null) {
                return null;
            }
            return new ReferenceDrag(handle, frame, pixelWidth, pixelHeight, axis, null, null, hit, 0.0,
                    angle(hit, center, gizmo.axis()), 0.0);
        }
        if (gizmo == GizmoHandle.MOVE_PLANE) {
            boolean grazing = Math.abs(ray.direction().dot(frame.normal())) < GRAZING;
            Vec3 normal = grazing ? new Vec3(0, 1, 0) : frame.normal();
            Vec3 u = grazing ? new Vec3(1, 0, 0) : frame.right();
            Vec3 v = grazing ? new Vec3(0, 0, 1) : frame.up();
            Vec3 hit = onPlane(ray, center, normal);
            if (hit == null) {
                return null;
            }
            return new ReferenceDrag(handle, frame, pixelWidth, pixelHeight, normal, u, v, hit, 0.0, 0.0, 0.0);
        }
        GizmoMath.AxisHit hit = GizmoMath.axis(ray, center, gizmo.axis());
        if (hit == null) {
            return null;
        }
        return new ReferenceDrag(handle, frame, pixelWidth, pixelHeight, null, null, null, null, hit.along(), 0.0,
                0.0);
    }

    public ReferenceGizmo.Handle handle() {
        return handle;
    }

    public WorldFrame start() {
        return start;
    }

    /** Lo girado desde que se agarró el anillo, ya en sus pasos (positivo, por la regla de la mano derecha). */
    public double degrees() {
        return degrees;
    }

    /** El plano con el arrastre hasta {@code ray}, o {@code null} si el rayo no sirve (se deja como estaba). */
    public WorldFrame drag(GizmoMath.Ray ray, boolean fine) {
        if (handle.isCorner()) {
            return scale(ray, fine);
        }
        GizmoHandle gizmo = handle.gizmo();
        if (gizmo.rotates()) {
            return rotate(ray, fine);
        }
        if (gizmo == GizmoHandle.MOVE_PLANE) {
            Vec3 hit = onPlane(ray, start.center(), planeNormal);
            if (hit == null) {
                return null;
            }
            Vec3 delta = hit.subtract(grab);
            double stepU = snap(delta.dot(planeU), fine);
            double stepV = snap(delta.dot(planeV), fine);
            return start.moved(planeU.multiply(stepU).add(planeV.multiply(stepV)));
        }
        GizmoMath.AxisHit hit = GizmoMath.axis(ray, start.center(), gizmo.axis());
        if (hit == null) {
            return null;
        }
        return start.moved(GizmoMath.unit(gizmo.axis()).multiply(snap(hit.along() - along, fine)));
    }

    private WorldFrame rotate(GizmoMath.Ray ray, boolean fine) {
        int axis = handle.gizmo().axis();
        Vec3 hit = onPlane(ray, start.center(), planeNormal);
        if (hit == null) {
            return null;
        }
        double now = angle(hit, start.center(), axis);
        turned += GizmoMath.wrap(now - lastAngle);
        lastAngle = now;
        double step = fine ? FINE_ROTATE_STEP : ROTATE_STEP;
        degrees = Math.round(Math.toDegrees(turned) / step) * step;
        return start.withRotation(start.rotation().turned(axis, degrees));
    }

    private WorldFrame scale(GizmoMath.Ray ray, boolean fine) {
        Vec3 hit = onPlane(ray, start.center(), planeNormal);
        if (hit == null || cornerReach <= 1.0E-9) {
            return null;
        }
        // Lo que se aleja del centro la esquina agarrada, a lo largo de su diagonal.
        Vec3 corner = start.corners(pixelWidth, pixelHeight)[handle.corner()];
        Vec3 diagonal = corner.subtract(start.center()).normalized();
        double reach = cornerReach + hit.subtract(grab).dot(diagonal);
        double factor = Math.max(1.0E-3, reach / cornerReach);
        double blocks = start.widthBlocks(pixelWidth) * factor;
        double step = fine ? FINE_MOVE : 1.0;
        double snapped = Math.max(step, Math.round(blocks / step) * step);
        return start.withWidthBlocks(snapped, pixelWidth, pixelHeight);
    }

    /** Un desplazamiento en bloques enteros (o en dieciseisavos con {@code fine}). */
    public static double snap(double delta, boolean fine) {
        double step = fine ? FINE_MOVE : 1.0;
        return Math.floor(delta / step + 0.5) * step;
    }

    /**
     * Ángulo de un punto alrededor del centro, medido en el sentido en que gira {@link ReferenceRotation#about} (regla
     * de la mano derecha): así girar la diferencia lleva lo agarrado a donde está el rayo.
     */
    static double angle(Vec3 point, Vec3 center, int axis) {
        Vec3 offset = point.subtract(center);
        return switch (axis) {
            case Orientation.X -> Math.atan2(offset.z(), offset.y());
            case Orientation.Y -> Math.atan2(offset.x(), offset.z());
            default -> Math.atan2(offset.y(), offset.x());
        };
    }

    private static Vec3 onPlane(GizmoMath.Ray ray, Vec3 point, Vec3 normal) {
        double distance = GizmoMath.plane(ray, point, normal);
        return Double.isNaN(distance) ? null : ray.at(distance);
    }
}
