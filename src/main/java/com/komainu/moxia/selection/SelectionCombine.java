package com.komainu.moxia.selection;

import java.util.Locale;

/**
 * Cómo se junta un trazo (un lazo, una pasada del pincel) con la selección que ya había: sin tecla la sustituye,
 * con Mayús se suma, con Ctrl se resta y con Alt se queda solo lo común.
 */
public enum SelectionCombine {
    REPLACE, ADD, SUBTRACT, INTERSECT;

    /** El modo de las teclas que se mantienen al empezar el trazo; si hay varias, gana Ctrl, luego Alt y luego Mayús. */
    public static SelectionCombine of(boolean shift, boolean control, boolean alt) {
        if (control) {
            return SUBTRACT;
        }
        if (alt) {
            return INTERSECT;
        }
        return shift ? ADD : REPLACE;
    }

    public static SelectionCombine byId(int id) {
        SelectionCombine[] values = values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Modo de selección desconocido: " + id);
        }
        return values[id];
    }

    /** La selección que queda: {@code current} no se toca. */
    public BlockSet apply(BlockSet current, BlockSet stroke) {
        BlockSet result;
        switch (this) {
            case REPLACE -> result = stroke.copy();
            case ADD -> {
                result = current.copy();
                result.addAll(stroke);
            }
            case SUBTRACT -> {
                result = current.copy();
                result.removeAll(stroke);
            }
            case INTERSECT -> {
                result = current.size() <= stroke.size() ? current.copy() : stroke.copy();
                result.retainAll(current.size() <= stroke.size() ? stroke : current);
            }
            default -> throw new IllegalStateException(name());
        }
        return result;
    }

    /** Si para este modo hace falta saber la selección que hay. */
    public boolean needsCurrent() {
        return this != REPLACE;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
