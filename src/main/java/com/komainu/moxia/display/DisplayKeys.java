package com.komainu.moxia.display;

import com.komainu.moxia.gizmo.GizmoKeys;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Las teclas del editor de displays, con lo elegido en pantalla. Como las del fantasma ({@link GizmoKeys}): cada
 * acción tiene su atajo de serie y una entrada sin asignar en los Controles de Minecraft para cambiarlo, así no le
 * quitan la tecla a nadie. Los atajos van con Ctrl salvo borrar (Supr), soltar (Esc) y mover con las flechas.
 *
 * <p>Ctrl+1, 2 y 3 eligen mover, girar o escalar; Ctrl+4 cambia entre local y global; Ctrl+5 el paso de mover y
 * Ctrl+6 el de girar. Mantener Alt mientras se arrastra va libre, sin pasos. Lógica pura con los códigos de GLFW.</p>
 */
public final class DisplayKeys {
    private static final int KEY_1 = '1';

    public enum Action {
        MOVE("move", KEY_1, true),
        ROTATE("rotate", KEY_1 + 1, true),
        SCALE("scale", KEY_1 + 2, true),
        FRAME("frame", KEY_1 + 3, true),
        STEP("step", KEY_1 + 4, true),
        ANGLE("angle", KEY_1 + 5, true),
        COPY("copy", 'C', true),
        PASTE("paste", 'V', true),
        DUPLICATE("duplicate", 'D', true),
        /** Con Mayús, desagrupar. */
        GROUP("group", 'G', true),
        DELETE("delete", GizmoKeys.KEY_DELETE, false),
        CLEAR("clear", GizmoKeys.KEY_ESCAPE, false),
        FORWARD("forward", GizmoKeys.KEY_UP, false),
        BACK("back", GizmoKeys.KEY_DOWN, false),
        LEFT("left", GizmoKeys.KEY_LEFT, false),
        RIGHT("right", GizmoKeys.KEY_RIGHT, false),
        UP("up", GizmoKeys.KEY_PAGE_UP, false),
        DOWN("down", GizmoKeys.KEY_PAGE_DOWN, false);

        private final String id;
        private final int key;
        private final boolean control;

        Action(String id, int key, boolean control) {
            this.id = id;
            this.key = key;
            this.control = control;
        }

        public String id() {
            return id;
        }

        public int defaultKey() {
            return key;
        }

        public boolean defaultControl() {
            return control;
        }

        /** Las de mover con el teclado: se repiten si se deja pulsada la tecla. */
        public boolean repeats() {
            return switch (this) {
                case FORWARD, BACK, LEFT, RIGHT, UP, DOWN -> true;
                default -> false;
            };
        }

        /** Las que no necesitan nada elegido. */
        public boolean idle() {
            return switch (this) {
                case MOVE, ROTATE, SCALE, FRAME, STEP, ANGLE -> true;
                default -> false;
            };
        }
    }

    public record Press(Action action, boolean shift, boolean control, boolean alt) {
    }

    private final Map<Action, Integer> bound = new EnumMap<>(Action.class);

    public DisplayKeys bind(Action action, int key) {
        bound.put(action, key);
        return this;
    }

    /** La acción de una pulsación; Retroceso también borra. */
    public Optional<Press> match(int key, int modifiers) {
        boolean shift = (modifiers & GizmoKeys.MOD_SHIFT) != 0;
        boolean control = (modifiers & GizmoKeys.MOD_CONTROL) != 0;
        boolean alt = (modifiers & GizmoKeys.MOD_ALT) != 0;
        for (Action action : Action.values()) {
            int custom = bound.getOrDefault(action, GizmoKeys.UNBOUND);
            boolean hit = custom != GizmoKeys.UNBOUND ? key == custom
                    : key == action.defaultKey() && control == action.defaultControl();
            if (hit) {
                return Optional.of(new Press(action, shift, control, alt));
            }
        }
        if (key == GizmoKeys.KEY_BACKSPACE && !control) {
            return Optional.of(new Press(Action.DELETE, shift, false, alt));
        }
        return Optional.empty();
    }
}
