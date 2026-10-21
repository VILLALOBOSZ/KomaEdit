package com.komainu.moxia.display;

import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Un arrastre del gizmo sobre los displays elegidos. Guarda cómo estaban al agarrar y, con cada rayo nuevo, calcula
 * cómo quedan: siempre desde el principio, así los pasos no acumulan error. Lógica pura: el cliente le pasa el rayo
 * del punto de mira (o del cursor en el Studio) y manda al servidor lo que sale al soltar.
 *
 * <ul>
 *     <li><b>Mover</b> por una flecha o por el plano del cubo central. En modo global la esquina de la caja del objeto
 *     activo cae en la rejilla del paso del mundo (1, 1/4 o 1/16): un bloque entero queda alineado con los bloques y
 *     uno de medio bloque, con los píxeles. En local, el desplazamiento va de paso en paso por sus ejes.</li>
 *     <li><b>Girar</b> con un anillo, de paso en paso (15° o 5°) desde donde se agarró, alrededor del centro del objeto
 *     activo; un grupo gira entero alrededor de él.</li>
 *     <li><b>Escalar</b> con el cubo de un eje: cada objeto en su propio eje, sobre su centro. Con el cubo central, todo
 *     por igual alrededor del objeto activo (un grupo crece junto).</li>
 * </ul>
 *
 * Con {@code free} (la tecla de movimiento libre) no hay pasos.
 */
public final class DisplayDrag {
    /** Lo más pequeño que se puede escalar un eje. */
    public static final double MIN_SCALE = 1.0 / 64.0;

    /** Un display elegido: su id, cómo estaba y el volumen de su modelo. */
    public record Item(int id, DisplayPose pose, DisplayVolume volume) {
        /** El centro del modelo, en sus coordenadas. */
        public Vec3 localCenter() {
            return volume.center();
        }

        public Vec3 center() {
            return pose.world(volume.center());
        }

        /** La esquina más baja de la caja que ocupa en el mundo (alineada con los ejes). */
        public Vec3 minimum() {
            Vec3[] corners = volume.world(pose).unitCorners();
            double x = Double.POSITIVE_INFINITY;
            double y = Double.POSITIVE_INFINITY;
            double z = Double.POSITIVE_INFINITY;
            for (Vec3 corner : corners) {
                x = Math.min(x, corner.x());
                y = Math.min(y, corner.y());
                z = Math.min(z, corner.z());
            }
            return new Vec3(x, y, z);
        }

        public Item with(DisplayPose value) {
            return new Item(id, value, volume);
        }
    }

    private final DisplayGizmo gizmo;
    private final DisplayGizmo.Handle handle;
    private final List<Item> start;
    private final int primary;
    private final DisplaySteps steps;
    private final boolean global;
    private final Vec3 pivot;
    private Vec3 planeStart;
    private double alongStart;
    private Vec3 viewUp;
    private Vec3 viewNormal;
    private double angleStart;
    private double lastAngle;
    private double turned;
    private double applied;
    private List<Item> current;
    private boolean moved;

    private DisplayDrag(DisplayGizmo gizmo, DisplayGizmo.Handle handle, List<Item> start, int primary,
                        DisplaySteps steps, boolean global) {
        this.gizmo = gizmo;
        this.handle = handle;
        this.start = List.copyOf(start);
        this.primary = primary;
        this.steps = steps;
        this.global = global;
        this.pivot = start.get(primary).center();
        this.current = this.start;
    }

    /**
     * Agarra {@code handle} del gizmo con el rayo. Nulo si el rayo no sirve para esa pieza (mirando a lo largo de una
     * flecha o de canto a un anillo).
     *
     * @param global modo global: la rejilla del mundo al mover (el marco del gizmo ya es la identidad)
     */
    public static DisplayDrag begin(DisplayGizmo gizmo, DisplayGizmo.Handle handle, GizmoMath.Ray ray,
                                    List<Item> items, int primary, DisplaySteps steps, boolean global) {
        if (handle == DisplayGizmo.Handle.NONE || items.isEmpty() || primary < 0 || primary >= items.size()) {
            return null;
        }
        DisplayDrag drag = new DisplayDrag(gizmo, handle, items, primary, steps, global);
        return drag.grab(ray) ? drag : null;
    }

    private boolean grab(GizmoMath.Ray ray) {
        Vec3 center = gizmo.center();
        switch (gizmo.tool()) {
            case ROTATE -> {
                if (handle == DisplayGizmo.Handle.CENTER) {
                    return false;
                }
                double distance = DisplayGizmo.plane(ray, center, gizmo.axis(handle.axis()));
                if (Double.isNaN(distance)) {
                    return false;
                }
                angleStart = gizmo.angle(ray.at(distance), handle.axis());
                lastAngle = angleStart;
            }
            case MOVE -> {
                if (handle == DisplayGizmo.Handle.CENTER) {
                    double distance = DisplayGizmo.plane(ray, center, gizmo.axis(1));
                    if (Double.isNaN(distance)) {
                        return false;
                    }
                    planeStart = ray.at(distance);
                } else {
                    DisplayGizmo.LineHit hit = DisplayGizmo.line(ray, center, gizmo.axis(handle.axis()));
                    if (hit == null) {
                        return false;
                    }
                    alongStart = hit.along();
                }
            }
            case SCALE -> {
                if (handle == DisplayGizmo.Handle.CENTER) {
                    viewNormal = ray.direction().multiply(-1.0);
                    double distance = DisplayGizmo.plane(ray, center, viewNormal);
                    if (Double.isNaN(distance)) {
                        return false;
                    }
                    planeStart = ray.at(distance);
                    Vec3 up = new Vec3(0, 1, 0);
                    viewUp = up.subtract(ray.direction().multiply(ray.direction().dot(up))).normalized();
                    if (viewUp.length() < 0.5) {
                        viewUp = new Vec3(0, 0, 1);
                    }
                } else {
                    DisplayGizmo.LineHit hit = DisplayGizmo.line(ray, center, gizmo.axis(handle.axis()));
                    if (hit == null) {
                        return false;
                    }
                    alongStart = Math.max(gizmo.size() * 0.25, Math.abs(hit.along()));
                }
            }
        }
        return true;
    }

    public DisplayGizmo.Handle handle() {
        return handle;
    }

    public DisplayGizmo.Tool tool() {
        return gizmo.tool();
    }

    public DisplayGizmo gizmo() {
        return gizmo;
    }

    /** Cómo quedan ahora (los de partida si aún no se ha movido). */
    public List<Item> current() {
        return current;
    }

    public List<Item> start() {
        return start;
    }

    /** Si algo ha cambiado desde que se agarró. */
    public boolean moved() {
        return moved;
    }

    /** Lo girado hasta ahora (ya con el paso), en radianes. */
    public double turned() {
        return applied;
    }

    /** El ángulo del anillo al agarrarlo. */
    public double angleStart() {
        return angleStart;
    }

    /**
     * Sigue el rayo.
     *
     * @param free sin pasos (la tecla de movimiento libre)
     * @return si cambió algo desde la última vez
     */
    public boolean update(GizmoMath.Ray ray, boolean free) {
        List<Item> before = current;
        List<Item> next = switch (gizmo.tool()) {
            case MOVE -> move(ray, free);
            case ROTATE -> rotate(ray, free);
            case SCALE -> scale(ray, free);
        };
        if (next == null) {
            return false;
        }
        current = next;
        boolean changed = !samePoses(before, next);
        moved |= !samePoses(start, next);
        return changed;
    }

    // ---------------------------------------------------------------- mover

    private List<Item> move(GizmoMath.Ray ray, boolean free) {
        Vec3 delta;
        if (handle == DisplayGizmo.Handle.CENTER) {
            double distance = DisplayGizmo.plane(ray, planeStart, gizmo.axis(1));
            if (Double.isNaN(distance)) {
                return null;
            }
            Vec3 raw = ray.at(distance).subtract(planeStart);
            delta = global ? snapGlobal(raw, true, false, true, free)
                    : snapLocal(raw, 0, free).add(snapLocal(raw, 2, free));
        } else {
            DisplayGizmo.LineHit hit = DisplayGizmo.line(ray, gizmo.center(), gizmo.axis(handle.axis()));
            if (hit == null) {
                return null;
            }
            Vec3 raw = gizmo.axis(handle.axis()).multiply(hit.along() - alongStart);
            int axis = handle.axis();
            delta = global ? snapGlobal(raw, axis == 0, axis == 1, axis == 2, free) : snapLocal(raw, axis, free);
        }
        return translated(delta);
    }

    /** Desplazamiento que deja la esquina de la caja del objeto activo en la rejilla del mundo, en los ejes pedidos. */
    private Vec3 snapGlobal(Vec3 raw, boolean x, boolean y, boolean z, boolean free) {
        Vec3 corner = start.get(primary).minimum();
        Vec3 target = corner.add(raw);
        double dx = x ? steps.snapPosition(target.x(), free) - corner.x() : 0.0;
        double dy = y ? steps.snapPosition(target.y(), free) - corner.y() : 0.0;
        double dz = z ? steps.snapPosition(target.z(), free) - corner.z() : 0.0;
        return new Vec3(dx, dy, dz);
    }

    /** La parte de {@code raw} por el eje local, de paso en paso. */
    private Vec3 snapLocal(Vec3 raw, int axis, boolean free) {
        Vec3 direction = gizmo.axis(axis);
        double along = raw.dot(direction);
        return direction.multiply(free ? along : DisplaySteps.snap(along, steps.move().size()));
    }

    /** Todos desplazados lo mismo. */
    public List<Item> translated(Vec3 delta) {
        List<Item> result = new ArrayList<>(start.size());
        for (Item item : start) {
            result.add(item.with(item.pose().moved(delta)));
        }
        return result;
    }

    // ---------------------------------------------------------------- girar

    private List<Item> rotate(GizmoMath.Ray ray, boolean free) {
        int axis = handle.axis();
        double distance = DisplayGizmo.plane(ray, gizmo.center(), gizmo.axis(axis));
        if (Double.isNaN(distance)) {
            return null;
        }
        double angle = gizmo.angle(ray.at(distance), axis);
        turned += GizmoMath.wrap(angle - lastAngle);
        lastAngle = angle;
        applied = steps.snapAngle(turned, free);
        return rotatedBy(Quat.axisAngle(gizmo.axis(axis), applied));
    }

    /** Todos girados {@code delta} (marco del mundo) alrededor del centro del objeto activo. */
    public List<Item> rotatedBy(Quat delta) {
        List<Item> result = new ArrayList<>(start.size());
        for (Item item : start) {
            result.add(item.with(item.pose().rotated(delta, pivot)));
        }
        return result;
    }

    // ---------------------------------------------------------------- escalar

    private List<Item> scale(GizmoMath.Ray ray, boolean free) {
        if (handle == DisplayGizmo.Handle.CENTER) {
            double distance = DisplayGizmo.plane(ray, gizmo.center(), viewNormal);
            if (Double.isNaN(distance)) {
                return null;
            }
            double rise = ray.at(distance).subtract(planeStart).dot(viewUp);
            double factor = Math.max(0.05, 1.0 + rise / gizmo.size());
            DisplayPose primaryPose = start.get(primary).pose().folded();
            double reference = primaryPose.scale().y();
            double snapped = steps.snapScale(reference * factor, free, MIN_SCALE) / reference;
            return uniform(snapped);
        }
        int axis = handle.axis();
        DisplayGizmo.LineHit hit = DisplayGizmo.line(ray, gizmo.center(), gizmo.axis(axis));
        if (hit == null) {
            return null;
        }
        double factor = Math.max(0.01, hit.along() / alongStart);
        List<Item> result = new ArrayList<>(start.size());
        for (Item item : start) {
            DisplayPose pose = item.pose().folded();
            double[] scale = {pose.scale().x(), pose.scale().y(), pose.scale().z()};
            scale[axis] = steps.snapScale(scale[axis] * factor, free, MIN_SCALE);
            result.add(item.with(pose.withScale(new Vec3(scale[0], scale[1], scale[2]), item.localCenter())));
        }
        return result;
    }

    /** Todos escalados por igual alrededor del centro del objeto activo. */
    public List<Item> uniform(double factor) {
        List<Item> result = new ArrayList<>(start.size());
        for (Item item : start) {
            result.add(item.with(item.pose().scaledAround(factor, pivot)));
        }
        return result;
    }

    private static boolean samePoses(List<Item> a, List<Item> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int index = 0; index < a.size(); index++) {
            if (!a.get(index).pose().equals(b.get(index).pose())) {
                return false;
            }
        }
        return true;
    }
}
