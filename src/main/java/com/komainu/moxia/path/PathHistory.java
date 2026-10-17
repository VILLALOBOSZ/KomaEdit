package com.komainu.moxia.path;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Historial de deshacer y rehacer de la edición de puntos de un jugador. Guarda el estado anterior a cada cambio;
 * los arrastres seguidos del mismo punto se agrupan en un solo paso para que deshacer no vaya bloque a bloque.
 */
public final class PathHistory {
    public static final int MAX_STEPS = 64;
    private static final long DRAG_WINDOW_NANOS = 1_500_000_000L;

    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private int dragIndex = -1;
    private long dragTime;

    public record State(List<BlockVector3> points, boolean closed) {
        public State {
            points = List.copyOf(points);
        }
    }

    /** Anota el estado anterior a un cambio. */
    public void record(State before) {
        dragIndex = -1;
        push(before);
    }

    /**
     * Anota el estado anterior a mover el punto {@code index}. Si es el mismo punto que se movía hace menos de
     * un segundo y medio, el cambio se suma al paso anterior.
     */
    public void recordMove(State before, int index, long nanoTime) {
        boolean sameDrag = index == dragIndex && nanoTime - dragTime < DRAG_WINDOW_NANOS && !undo.isEmpty();
        dragIndex = index;
        dragTime = nanoTime;
        if (!sameDrag) {
            push(before);
        }
    }

    public Optional<State> undo(State current) {
        dragIndex = -1;
        if (undo.isEmpty()) {
            return Optional.empty();
        }
        redo.push(current);
        return Optional.of(undo.pop());
    }

    public Optional<State> redo(State current) {
        dragIndex = -1;
        if (redo.isEmpty()) {
            return Optional.empty();
        }
        undo.push(current);
        return Optional.of(redo.pop());
    }

    public int undoSteps() {
        return undo.size();
    }

    public int redoSteps() {
        return redo.size();
    }

    private void push(State before) {
        if (!undo.isEmpty() && undo.peek().equals(before)) {
            redo.clear();
            return;
        }
        undo.push(before);
        while (undo.size() > MAX_STEPS) {
            undo.removeLast();
        }
        redo.clear();
    }
}
