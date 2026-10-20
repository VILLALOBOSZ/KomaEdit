package com.komainu.moxia.gizmo;

/** Las piezas del gizmo que se pueden agarrar. */
public enum GizmoHandle {
    NONE(-1, false, false),
    /** Flechas: mueven por un eje. */
    MOVE_X(Orientation.X, true, false),
    MOVE_Y(Orientation.Y, true, false),
    MOVE_Z(Orientation.Z, true, false),
    /** El cubo del centro: arrastra sobre el plano del suelo (X y Z a la vez). */
    MOVE_PLANE(-1, true, false),
    /** Anillos: giran de cuarto en cuarto de vuelta alrededor de su eje. */
    RING_X(Orientation.X, false, true),
    RING_Y(Orientation.Y, false, true),
    RING_Z(Orientation.Z, false, true);

    private final int axis;
    private final boolean moves;
    private final boolean rotates;

    GizmoHandle(int axis, boolean moves, boolean rotates) {
        this.axis = axis;
        this.moves = moves;
        this.rotates = rotates;
    }

    /** Eje de la flecha o del anillo; -1 en el cubo y en {@link #NONE}. */
    public int axis() {
        return axis;
    }

    public boolean moves() {
        return moves;
    }

    public boolean rotates() {
        return rotates;
    }

    public static GizmoHandle arrow(int axis) {
        return switch (axis) {
            case Orientation.X -> MOVE_X;
            case Orientation.Y -> MOVE_Y;
            default -> MOVE_Z;
        };
    }

    public static GizmoHandle ring(int axis) {
        return switch (axis) {
            case Orientation.X -> RING_X;
            case Orientation.Y -> RING_Y;
            default -> RING_Z;
        };
    }
}
