package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.NumericRange;

import java.util.Objects;

/**
 * Lo que pasa con una fila numérica entre pulsar y soltar: arrastrar el valor lo desliza (Mayús: fino) y un clic
 * sin mover abre el campo para escribirlo. Lo comparten todas las filas numéricas del Studio; cada una se reconoce
 * por su clave. No sabe nada de valores: el que lo usa lee y aplica.
 */
public final class NumberEdit {
    /** Píxeles que hay que mover para que una pulsación cuente como arrastre. */
    public static final int DRAG_THRESHOLD = 3;
    private static final int MAX_INPUT = 14;

    private Object editing;
    private String input = "";
    private Object dragging;
    private double dragStartX;
    private int dragStartValue;
    private boolean moved;

    /** Si se está escribiendo en la fila {@code key}. */
    public boolean editing(Object key) {
        return editing != null && editing.equals(key);
    }

    public boolean editing() {
        return editing != null;
    }

    public Object editingKey() {
        return editing;
    }

    public String input() {
        return input;
    }

    public boolean dragging(Object key) {
        return dragging != null && dragging.equals(key);
    }

    public boolean dragging() {
        return dragging != null;
    }

    public Object draggingKey() {
        return dragging;
    }

    /** Se pulsó el valor de {@code key}, que ahora vale {@code value}: puede empezar un arrastre. */
    public void press(Object key, int value, double mouseX) {
        dragging = Objects.requireNonNull(key);
        dragStartX = mouseX;
        dragStartValue = value;
        moved = false;
    }

    /**
     * El ratón se movió a {@code mouseX} con el botón pulsado.
     *
     * @return el valor nuevo, o {@code null} si aún no se movió lo bastante para ser un arrastre
     */
    public Integer drag(double mouseX, NumericRange range, boolean fine) {
        if (dragging == null) {
            return null;
        }
        double distance = mouseX - dragStartX;
        if (Math.abs(distance) >= DRAG_THRESHOLD) {
            moved = true;
        }
        return moved ? range.dragged(dragStartValue, distance, fine) : null;
    }

    /**
     * Se soltó el botón. Si no hubo arrastre se abre el campo con {@code text} para escribir.
     *
     * @return si se abrió el campo
     */
    public boolean release(String text) {
        if (dragging == null) {
            return false;
        }
        Object key = dragging;
        boolean typing = !moved;
        dragging = null;
        moved = false;
        if (typing) {
            editing = key;
            input = text == null ? "" : text;
        }
        return typing;
    }

    /** Abre el campo de {@code key} directamente (con el teclado). */
    public void open(Object key, String text) {
        editing = Objects.requireNonNull(key);
        input = text == null ? "" : text;
    }

    public void type(char character) {
        if (editing == null || input.length() >= MAX_INPUT) {
            return;
        }
        if (Character.isDigit(character) || character == '-' || character == '−' || character == '.'
                || character == ',') {
            input += character;
        }
    }

    public void erase(boolean all) {
        if (editing != null && !input.isEmpty()) {
            input = all ? "" : input.substring(0, input.length() - 1);
        }
    }

    /** Cierra el campo y devuelve lo escrito (vacío si no se escribía). */
    public String commit() {
        String typed = editing == null ? "" : input;
        cancel();
        return typed;
    }

    public void cancel() {
        editing = null;
        input = "";
    }

    /** Deja todo como al principio (al cambiar de etapa o de herramienta). */
    public void reset() {
        cancel();
        dragging = null;
        moved = false;
    }
}
