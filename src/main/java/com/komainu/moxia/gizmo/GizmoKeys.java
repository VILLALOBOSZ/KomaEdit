package com.komainu.moxia.gizmo;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Las teclas del fantasma. Cada acción tiene su atajo de serie y una entrada en los Controles de Minecraft para
 * cambiarlo. Las entradas vienen sin asignar a propósito: así no le quitan la tecla a nadie (la F de cambiar de mano,
 * la R de sacar Pokémon de Cobblemon…), porque Minecraft solo atiende una acción por tecla. Mientras no se asignen,
 * vale el atajo de serie; si se asigna una tecla, esa tecla sola (sin Ctrl) hace la acción mientras hay fantasma.
 *
 * <p>Nada de esto actúa sin un fantasma en pantalla, salvo pegar (Ctrl+V). Lógica pura: los códigos son los de
 * GLFW.</p>
 */
public final class GizmoKeys {
    public static final int UNBOUND = -1;
    // Códigos de GLFW (no se importa LWJGL en el código común).
    public static final int KEY_ENTER = 257;
    public static final int KEY_KP_ENTER = 335;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_BACKSPACE = 259;
    public static final int KEY_DELETE = 261;
    public static final int KEY_RIGHT = 262;
    public static final int KEY_LEFT = 263;
    public static final int KEY_DOWN = 264;
    public static final int KEY_UP = 265;
    public static final int KEY_PAGE_UP = 266;
    public static final int KEY_PAGE_DOWN = 267;
    public static final int MOD_SHIFT = 1;
    public static final int MOD_CONTROL = 2;
    public static final int MOD_ALT = 4;

    public enum Action {
        FORWARD("forward", KEY_UP, false),
        BACK("back", KEY_DOWN, false),
        LEFT("left", KEY_LEFT, false),
        RIGHT("right", KEY_RIGHT, false),
        UP("up", KEY_PAGE_UP, false),
        DOWN("down", KEY_PAGE_DOWN, false),
        /** Llevarlo a donde se mira. */
        HERE("here", 'G', false),
        ROTATE("rotate", 'R', true),
        FLIP("flip", 'F', true),
        GROUND("ground", 'B', true),
        KEEP("keep", 'E', true),
        AIR("air", 'X', true),
        /** Pegar el portapapeles como fantasma (también confirma uno abierto, como en Axiom). */
        PASTE("paste", 'V', true),
        CONFIRM("confirm", KEY_ENTER, false),
        CANCEL("cancel", KEY_ESCAPE, false);

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

        /** Tecla de serie (GLFW). */
        public int defaultKey() {
            return key;
        }

        /** Si el atajo de serie va con Ctrl. */
        public boolean defaultControl() {
            return control;
        }
    }

    /** Lo que pide una pulsación: la acción y si iba con Mayús (de 5 en 5, o al revés), Ctrl o Alt. */
    public record Press(Action action, boolean shift, boolean control, boolean alt) {
        /** Bloques que mueve una flecha: 1, o 5 con Mayús. */
        public int step() {
            return shift ? 5 : 1;
        }
    }

    private final Map<Action, Integer> bound = new EnumMap<>(Action.class);

    /** Asigna (o con {@link #UNBOUND}, quita) la tecla de Controles de una acción. */
    public GizmoKeys bind(Action action, int key) {
        bound.put(action, key);
        return this;
    }

    /**
     * La acción de una pulsación con el fantasma en pantalla.
     *
     * @param confirmKey tecla de «aplicar la vista previa» de Controles (Intro de serie): también coloca el fantasma
     * @param discardKey tecla de «descartar» (Retroceso de serie): también lo quita
     */
    public Optional<Press> match(int key, int modifiers, int confirmKey, int discardKey) {
        boolean shift = (modifiers & MOD_SHIFT) != 0;
        boolean control = (modifiers & MOD_CONTROL) != 0;
        boolean alt = (modifiers & MOD_ALT) != 0;
        for (Action action : Action.values()) {
            int custom = bound.getOrDefault(action, UNBOUND);
            boolean hit = custom != UNBOUND ? key == custom
                    : key == action.defaultKey() && (control || !action.defaultControl());
            if (hit) {
                return Optional.of(new Press(action, shift, control, alt));
            }
        }
        if (key == KEY_KP_ENTER || key == confirmKey && confirmKey != UNBOUND) {
            return Optional.of(new Press(Action.CONFIRM, shift, control, alt));
        }
        if (key == KEY_BACKSPACE || key == KEY_DELETE || key == discardKey && discardKey != UNBOUND) {
            return Optional.of(new Press(Action.CANCEL, shift, control, alt));
        }
        return Optional.empty();
    }

    /** Sin fantasma solo cuenta pegar. */
    public Optional<Press> matchIdle(int key, int modifiers) {
        int custom = bound.getOrDefault(Action.PASTE, UNBOUND);
        boolean control = (modifiers & MOD_CONTROL) != 0;
        boolean hit = custom != UNBOUND ? key == custom : key == Action.PASTE.defaultKey() && control;
        return hit ? Optional.of(new Press(Action.PASTE, (modifiers & MOD_SHIFT) != 0, control,
                (modifiers & MOD_ALT) != 0)) : Optional.empty();
    }
}
