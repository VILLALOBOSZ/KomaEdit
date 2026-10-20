package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.shape.Vec3;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * El fantasma que se está colocando, en el cliente: la máquina de estados de mover, girar, voltear, confirmar,
 * cancelar y colocar copias. No dibuja ni lee teclas: recibe acciones ya traducidas (un paso de bloque, un rayo, un
 * giro) y dice qué mandar al servidor. Lógica pura, con pruebas.
 *
 * <ul>
 *     <li>{@link Phase#MOVING}: se mueve con el teclado, la rueda, «a donde miro» o el gizmo.</li>
 *     <li>{@link Phase#DRAGGING}: con una pieza del gizmo agarrada; al soltar vuelve a MOVING.</li>
 *     <li>{@link Phase#PLACING}: confirmado; espera la respuesta del servidor. Si lo rechaza (zona protegida,
 *     demasiado lejos…), vuelve a MOVING con el motivo; si lo coloca, CLOSED.</li>
 *     <li>Una copia ({@link #confirm} con {@code copy}) se coloca y el fantasma sigue en MOVING; hasta que el servidor
 *     responde no sale otra.</li>
 * </ul>
 */
public final class GhostSession {
    public enum Phase {
        MOVING,
        DRAGGING,
        PLACING,
        CLOSED
    }

    /** Lo que el servidor dice del objeto al abrirlo. */
    public record Info(int id, String description, BlockBounds localBounds, int blocks, int airBlocks,
                       GhostTransform start, GhostOptions options) {
        public boolean hasAir() {
            return airBlocks > 0;
        }
    }

    /** Si el objeto admite una orientación (sus escaleras, puertas… no se tumban). */
    @FunctionalInterface
    public interface Rules {
        boolean admits(Orientation orientation);
    }

    /** Cuánto bajar o subir para apoyarlo en el suelo con esa transformación (vacío: no hay suelo). */
    @FunctionalInterface
    public interface Ground {
        OptionalInt rest(GhostTransform transform);
    }

    /** Lo que se manda al confirmar: solo la transformación y las opciones. */
    public record Confirm(int id, GhostTransform transform, GhostOptions options, boolean copy) {
    }

    private final Info info;
    private GhostTransform transform;
    private GhostOptions options;
    private Phase phase = Phase.MOVING;
    private boolean copyPending;
    private String message = "";
    private int copies;

    private GizmoHandle dragged = GizmoHandle.NONE;
    private GhostTransform dragFrom;
    private Vec3 dragOrigin;
    private double dragStart;
    private Vec3 planeStart;
    private double lastAngle;
    private double turned;
    private int appliedTurns;
    private boolean dragMoved;

    public GhostSession(Info info) {
        this.info = info;
        this.transform = info.start();
        this.options = info.options();
    }

    public Info info() {
        return info;
    }

    public GhostTransform transform() {
        return transform;
    }

    public GhostOptions options() {
        return options;
    }

    public Phase phase() {
        return phase;
    }

    /** Se puede mover y confirmar (no está esperando al servidor ni cerrado). */
    public boolean active() {
        return phase == Phase.MOVING || phase == Phase.DRAGGING;
    }

    public boolean open() {
        return phase != Phase.CLOSED;
    }

    public boolean copyPending() {
        return copyPending;
    }

    /** Copias colocadas con éxito. */
    public int copies() {
        return copies;
    }

    /** El último aviso del servidor (por qué no se colocó), o vacío. */
    public String message() {
        return message;
    }

    public GizmoHandle dragged() {
        return dragged;
    }

    /** La caja del mundo que ocupa ahora. */
    public BlockBounds bounds() {
        return transform.bounds(info.localBounds());
    }

    /** Medidas con el giro de ahora: {x, y, z}. */
    public int[] size() {
        BlockBounds oriented = transform.oriented(info.localBounds());
        return new int[]{oriented.maximumX() - oriented.minimumX() + 1, oriented.maximumY() - oriented.minimumY() + 1,
                oriented.maximumZ() - oriented.minimumZ() + 1};
    }

    /** Lo movido desde donde se calculó: {dx, dy, dz}. */
    public int[] offset() {
        return new int[]{transform.x() - info.start().x(), transform.y() - info.start().y(),
                transform.z() - info.start().z()};
    }

    /**
     * El mismo objeto, recalculado con otros ajustes (la vista previa en vivo del Studio): sale movido lo mismo que el
     * de antes desde donde se calculó, con su giro (si este lo admite) y sus opciones. Lo que el jugador ya colocó con
     * el ratón o el gizmo no se pierde al tocar un ajuste.
     */
    public void follow(GhostSession previous, Rules rules) {
        if (previous == null || phase != Phase.MOVING) {
            return;
        }
        int[] moved = previous.offset();
        Orientation orientation = previous.transform.orientation();
        transform = new GhostTransform(rules.admits(orientation) ? orientation : Orientation.IDENTITY,
                info.start().x() + moved[0], info.start().y() + moved[1], info.start().z() + moved[2]);
        options = previous.options;
    }

    // ---------------------------------------------------------------- mover

    /** Mueve unos bloques. Moverlo en vertical a mano quita «apoyar en el suelo». */
    public boolean nudge(int dx, int dy, int dz, Ground ground) {
        if (phase != Phase.MOVING || dx == 0 && dy == 0 && dz == 0) {
            return false;
        }
        if (dy != 0 && options.ground()) {
            options = options.withGround(false);
        }
        transform = settle(transform.moved(dx, dy, dz), ground);
        message = "";
        return true;
    }

    /** Lo pone contra la cara {@code face} del bloque mirado (encima, debajo o al lado), centrado en él. */
    public boolean placeAgainst(int hitX, int hitY, int hitZ, int face, Ground ground) {
        if (phase != Phase.MOVING) {
            return false;
        }
        transform = settle(against(transform, info.localBounds(), hitX, hitY, hitZ, face), ground);
        message = "";
        return true;
    }

    /**
     * La transformación que apoya la caja del objeto en la cara {@code face} del bloque ({@code hitX}, {@code hitY},
     * {@code hitZ}): encima, centrada; debajo, colgando; en un lado, pegada a la pared con la base a la altura del
     * bloque mirado.
     */
    public static GhostTransform against(GhostTransform transform, BlockBounds local, int hitX, int hitY, int hitZ,
                                         int face) {
        BlockBounds oriented = transform.oriented(local);
        int axis = face >> 1;
        boolean positive = (face & 1) != 0;
        int anchorX = hitX + (axis == Orientation.X ? positive ? 1 : -1 : 0);
        int anchorY = hitY + (axis == Orientation.Y ? positive ? 1 : -1 : 0);
        int anchorZ = hitZ + (axis == Orientation.Z ? positive ? 1 : -1 : 0);
        int centerX = Math.floorDiv(oriented.minimumX() + oriented.maximumX(), 2);
        int centerZ = Math.floorDiv(oriented.minimumZ() + oriented.maximumZ(), 2);
        int x = anchorX - centerX;
        int y = anchorY - oriented.minimumY();
        int z = anchorZ - centerZ;
        switch (axis) {
            case Orientation.X -> x = positive ? anchorX - oriented.minimumX() : anchorX - oriented.maximumX();
            case Orientation.Y -> y = positive ? anchorY - oriented.minimumY() : anchorY - oriented.maximumY();
            default -> z = positive ? anchorZ - oriented.minimumZ() : anchorZ - oriented.maximumZ();
        }
        if (axis != Orientation.Y) {
            y = hitY - oriented.minimumY();
        }
        return transform.movedTo(x, y, z);
    }

    // ---------------------------------------------------------------- girar y voltear

    /** Gira sobre el pivote; no hace nada si el objeto no admite quedar así. */
    public boolean rotate(int axis, int quarterTurns, Rules rules, Ground ground) {
        if (phase != Phase.MOVING || Math.floorMod(quarterTurns, 4) == 0) {
            return false;
        }
        GhostTransform candidate = transform.turned(axis, quarterTurns);
        if (!rules.admits(candidate.orientation())) {
            return false;
        }
        transform = settle(candidate, ground);
        message = "";
        return true;
    }

    /** Voltea en un eje del mundo, sobre el pivote. */
    public boolean flip(int axis, Rules rules, Ground ground) {
        if (phase != Phase.MOVING) {
            return false;
        }
        GhostTransform candidate = transform.mirrored(axis);
        if (!rules.admits(candidate.orientation())) {
            return false;
        }
        transform = settle(candidate, ground);
        message = "";
        return true;
    }

    // ---------------------------------------------------------------- gizmo

    /** Agarra una pieza del gizmo con el rayo del punto de mira o del ratón. */
    public boolean beginDrag(GizmoHandle handle, GizmoMath.Ray ray, GizmoLayout layout) {
        if (phase != Phase.MOVING || handle == GizmoHandle.NONE) {
            return false;
        }
        Vec3 center = layout.center();
        if (handle.rotates()) {
            double distance = GizmoMath.plane(ray, center, GizmoMath.unit(handle.axis()));
            if (Double.isNaN(distance)) {
                return false;
            }
            lastAngle = GizmoMath.angle(ray.at(distance), center, handle.axis());
            turned = 0.0;
            appliedTurns = 0;
        } else if (handle == GizmoHandle.MOVE_PLANE) {
            double distance = GizmoMath.plane(ray, center, GizmoMath.unit(Orientation.Y));
            if (Double.isNaN(distance)) {
                return false;
            }
            planeStart = ray.at(distance);
        } else {
            GizmoMath.AxisHit hit = GizmoMath.axis(ray, center, handle.axis());
            if (hit == null) {
                return false;
            }
            dragStart = hit.along();
        }
        dragged = handle;
        dragFrom = transform;
        dragOrigin = center;
        dragMoved = false;
        phase = Phase.DRAGGING;
        message = "";
        return true;
    }

    /**
     * Sigue el rayo con la pieza agarrada: las flechas y el cubo mueven de bloque en bloque; los anillos giran de
     * cuarto en cuarto de vuelta.
     *
     * @return si cambió la transformación
     */
    public boolean drag(GizmoMath.Ray ray, Rules rules, Ground ground) {
        if (phase != Phase.DRAGGING) {
            return false;
        }
        GhostTransform before = transform;
        if (dragged.rotates()) {
            double distance = GizmoMath.plane(ray, dragOrigin, GizmoMath.unit(dragged.axis()));
            if (Double.isNaN(distance)) {
                return false;
            }
            double angle = GizmoMath.angle(ray.at(distance), dragOrigin, dragged.axis());
            turned += GizmoMath.wrap(angle - lastAngle);
            lastAngle = angle;
            dragMoved |= Math.abs(turned) > Math.toRadians(8.0);
            int turns = GizmoMath.quarterTurns(turned);
            if (turns != appliedTurns) {
                GhostTransform candidate = dragFrom.turned(dragged.axis(), turns);
                if (rules.admits(candidate.orientation())) {
                    transform = settle(candidate, ground);
                    appliedTurns = turns;
                }
            }
        } else if (dragged == GizmoHandle.MOVE_PLANE) {
            double distance = GizmoMath.plane(ray, planeStart, GizmoMath.unit(Orientation.Y));
            if (Double.isNaN(distance)) {
                return false;
            }
            Vec3 point = ray.at(distance);
            int dx = GizmoMath.steps(point.x() - planeStart.x());
            int dz = GizmoMath.steps(point.z() - planeStart.z());
            transform = settle(dragFrom.moved(dx, 0, dz), ground);
        } else {
            GizmoMath.AxisHit hit = GizmoMath.axis(ray, dragOrigin, dragged.axis());
            if (hit == null) {
                return false;
            }
            int steps = GizmoMath.steps(hit.along() - dragStart);
            int[] delta = {0, 0, 0};
            delta[dragged.axis()] = steps;
            if (dragged.axis() == Orientation.Y && steps != 0 && options.ground()) {
                options = options.withGround(false);
            }
            transform = settle(dragFrom.moved(delta[0], delta[1], delta[2]), ground);
        }
        dragMoved |= !transform.equals(dragFrom);
        return !transform.equals(before);
    }

    /** Suelta la pieza. Un clic en un anillo sin arrastrar gira un cuarto de vuelta. */
    public void endDrag(Rules rules, Ground ground) {
        if (phase != Phase.DRAGGING) {
            return;
        }
        GizmoHandle released = dragged;
        boolean clicked = !dragMoved;
        dragged = GizmoHandle.NONE;
        phase = Phase.MOVING;
        if (released.rotates() && clicked) {
            rotate(released.axis(), 1, rules, ground);
        }
    }

    // ---------------------------------------------------------------- opciones

    public GhostOptions toggleKeepExisting() {
        options = options.withKeepExisting(!options.keepExisting());
        return options;
    }

    public GhostOptions togglePasteAir() {
        options = options.withPasteAir(!options.pasteAir());
        return options;
    }

    /** Apoyar en el suelo: al encenderlo baja ya hasta el terreno. */
    public GhostOptions toggleGround(Ground ground) {
        options = options.withGround(!options.ground());
        if (options.ground() && active()) {
            transform = settle(transform, ground);
        }
        return options;
    }

    private GhostTransform settle(GhostTransform candidate, Ground ground) {
        if (!options.ground() || ground == null) {
            return candidate;
        }
        OptionalInt rest = ground.rest(candidate);
        return rest.isPresent() ? candidate.moved(0, rest.getAsInt(), 0) : candidate;
    }

    // ---------------------------------------------------------------- confirmar

    /**
     * Confirma: devuelve lo que hay que mandar al servidor, o vacío si ahora no se puede (agarrando el gizmo, ya
     * esperando, o con una copia aún en camino). Con {@code copy} el fantasma sigue para colocar otra.
     */
    public Optional<Confirm> confirm(boolean copy) {
        if (phase != Phase.MOVING || copyPending) {
            return Optional.empty();
        }
        if (copy) {
            copyPending = true;
        } else {
            phase = Phase.PLACING;
        }
        message = "";
        return Optional.of(new Confirm(info.id(), transform, options, copy));
    }

    /** El servidor lo colocó. */
    public void placed(boolean copy) {
        if (copy) {
            copyPending = false;
            copies++;
            return;
        }
        if (phase == Phase.PLACING) {
            phase = Phase.CLOSED;
        }
    }

    /** El servidor no lo colocó: vuelve a poder moverse, con el motivo. */
    public void rejected(String reason) {
        copyPending = false;
        if (phase == Phase.PLACING) {
            phase = Phase.MOVING;
        }
        message = reason == null ? "" : reason;
    }

    /**
     * Cancela: el fantasma se cierra sin colocar nada.
     *
     * @return {@code true} si estaba abierto (hay que avisar al servidor)
     */
    public boolean cancel() {
        if (phase == Phase.CLOSED) {
            return false;
        }
        phase = Phase.CLOSED;
        dragged = GizmoHandle.NONE;
        copyPending = false;
        return true;
    }

    /** El servidor lo cerró por su cuenta (cambio de mundo, otra orden…). */
    public void closed() {
        phase = Phase.CLOSED;
        dragged = GizmoHandle.NONE;
    }
}
