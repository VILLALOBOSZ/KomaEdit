package com.komainu.moxia.studio;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Deshacer y rehacer de los ajustes del Studio (no del mundo: eso lo hace WorldEdit).
 * Guarda estados enteros e inmutables. Los cambios seguidos del mismo control dentro de
 * {@link #COALESCE_MILLIS} se funden en un solo paso, para que arrastrar un valor o darle
 * a la rueda no llene el historial de pasos de uno en uno.
 */
public final class ParameterHistory<T> {
    public static final long COALESCE_MILLIS = 800L;

    private final int capacity;
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();
    private String lastGroup;
    private long lastAt = Long.MIN_VALUE;

    public ParameterHistory(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    /**
     * Anota el estado anterior a un cambio. {@code group} identifica el control tocado; {@code null}
     * no se funde nunca con el anterior.
     */
    public void record(T before, String group, long nowMillis) {
        Objects.requireNonNull(before, "before");
        boolean coalesce = group != null && group.equals(lastGroup)
                && nowMillis - lastAt <= COALESCE_MILLIS && !undo.isEmpty();
        if (!coalesce) {
            undo.push(before);
            while (undo.size() > capacity) {
                undo.removeLast();
            }
        }
        redo.clear();
        lastGroup = group;
        lastAt = nowMillis;
    }

    /** El estado al que volver, o {@code null} si no hay; {@code current} pasa a rehacer. */
    public T undo(T current) {
        if (undo.isEmpty()) {
            return null;
        }
        redo.push(current);
        breakCoalescing();
        return undo.pop();
    }

    public T redo(T current) {
        if (redo.isEmpty()) {
            return null;
        }
        undo.push(current);
        breakCoalescing();
        return redo.pop();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public int undoSize() {
        return undo.size();
    }

    public void clear() {
        undo.clear();
        redo.clear();
        breakCoalescing();
    }

    private void breakCoalescing() {
        lastGroup = null;
        lastAt = Long.MIN_VALUE;
    }
}
