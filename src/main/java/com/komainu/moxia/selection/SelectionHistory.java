package com.komainu.moxia.selection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.ToLongFunction;

/**
 * Las selecciones de antes de cada trazo, para deshacerlos de uno en uno. Guarda como mucho {@link #MAX_ENTRIES} y
 * {@link #MAX_WEIGHT} bloques entre todas: al pasarse, se olvidan las más viejas.
 *
 * @param <T> cómo se guarda una selección (los bloques o la forma de WorldEdit)
 */
public final class SelectionHistory<T> {
    public static final int MAX_ENTRIES = 16;
    public static final long MAX_WEIGHT = 4_000_000L;

    private final Deque<T> entries = new ArrayDeque<>();
    private final ToLongFunction<T> weight;
    private long total;

    public SelectionHistory(ToLongFunction<T> weight) {
        this.weight = weight;
    }

    /** Apunta la selección de antes de un trazo. Una que pesa más que todo el historial no se guarda. */
    public void push(T before) {
        long size = weight.applyAsLong(before);
        if (size > MAX_WEIGHT) {
            clear();
            return;
        }
        entries.addLast(before);
        total += size;
        while (entries.size() > MAX_ENTRIES || total > MAX_WEIGHT) {
            total -= weight.applyAsLong(entries.removeFirst());
        }
    }

    /** La selección de antes del último trazo, o {@code null} si no queda ninguna. */
    public T pop() {
        T last = entries.pollLast();
        if (last != null) {
            total -= weight.applyAsLong(last);
        }
        return last;
    }

    public int size() {
        return entries.size();
    }

    public long weight() {
        return total;
    }

    public void clear() {
        entries.clear();
        total = 0;
    }
}
