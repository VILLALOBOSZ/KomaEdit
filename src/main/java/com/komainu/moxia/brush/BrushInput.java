package com.komainu.moxia.brush;

/** Acciones de los controles de ezEdits con una herramienta en la mano. */
public enum BrushInput {
    RIGHT,
    SNEAK_RIGHT,
    LEFT,
    SNEAK_LEFT,
    /** Tecla de cambiar de mano (F por defecto). */
    SWAP,
    SNEAK_SWAP;

    public static BrushInput of(Base base, boolean sneaking) {
        return switch (base) {
            case RIGHT -> sneaking ? SNEAK_RIGHT : RIGHT;
            case LEFT -> sneaking ? SNEAK_LEFT : LEFT;
            case SWAP -> sneaking ? SNEAK_SWAP : SWAP;
        };
    }

    public boolean sneaking() {
        return this == SNEAK_RIGHT || this == SNEAK_LEFT || this == SNEAK_SWAP;
    }

    public boolean right() {
        return this == RIGHT || this == SNEAK_RIGHT;
    }

    public boolean left() {
        return this == LEFT || this == SNEAK_LEFT;
    }

    public boolean swap() {
        return this == SWAP || this == SNEAK_SWAP;
    }

    public enum Base {
        RIGHT, LEFT, SWAP
    }
}
