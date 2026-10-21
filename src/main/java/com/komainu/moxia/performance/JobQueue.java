package com.komainu.moxia.performance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * La cola de colocación: como mucho {@code slots} ediciones colocan a la vez y el resto espera por orden de llegada.
 * Una edición en pausa no ocupa sitio (entra la siguiente) ni entra mientras siga en pausa. Las que colocan se
 * reparten el tiempo con {@link FairShare}; aquí solo se lleva quién coloca, quién espera y en qué puesto.
 *
 * <p>Java puro: la clave es el dueño (el jugador) y cada dueño tiene como mucho una edición.</p>
 */
public final class JobQueue<K> {
    private final Map<K, Entry> entries = new LinkedHashMap<>();

    private static final class Entry {
        private final long size;
        private long done;
        private boolean active;

        private Entry(long size) {
            this.size = size;
        }
    }

    /** Pone la edición de {@code owner} al final de la cola. */
    public void add(K owner, long size) {
        if (entries.containsKey(owner)) {
            throw new IllegalStateException("Ese dueño ya tiene una edición en la cola");
        }
        entries.put(owner, new Entry(Math.max(0L, size)));
    }

    public boolean contains(K owner) {
        return entries.containsKey(owner);
    }

    public boolean remove(K owner) {
        return entries.remove(owner) != null;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Lo que lleva hecho (para el puesto y el tiempo de espera de las de detrás). */
    public void progress(K owner, long done) {
        Entry entry = entries.get(owner);
        if (entry != null) {
            entry.done = Math.max(0L, done);
        }
    }

    /**
     * Deja colocar a las que esperan, por orden, mientras haya sitio. Las que están en pausa no cuentan ni entran.
     *
     * @return dueños que acaban de entrar
     */
    public List<K> admit(int slots, Predicate<K> paused) {
        int running = 0;
        for (Map.Entry<K, Entry> entry : entries.entrySet()) {
            if (entry.getValue().active && !paused.test(entry.getKey())) {
                running++;
            }
        }
        List<K> admitted = new ArrayList<>();
        for (Map.Entry<K, Entry> entry : entries.entrySet()) {
            if (running >= slots) {
                break;
            }
            if (!entry.getValue().active && !paused.test(entry.getKey())) {
                entry.getValue().active = true;
                admitted.add(entry.getKey());
                running++;
            }
        }
        return admitted;
    }

    public boolean active(K owner) {
        Entry entry = entries.get(owner);
        return entry != null && entry.active;
    }

    /** Las que colocan, por orden de llegada. */
    public List<K> activeOwners() {
        List<K> owners = new ArrayList<>();
        entries.forEach((owner, entry) -> {
            if (entry.active) {
                owners.add(owner);
            }
        });
        return owners;
    }

    /** Puesto en la espera (1 la primera); vacío si ya coloca o no está. */
    public Optional<Integer> position(K owner) {
        int position = 0;
        for (Map.Entry<K, Entry> entry : entries.entrySet()) {
            if (!entry.getValue().active) {
                position++;
                if (entry.getKey().equals(owner)) {
                    return Optional.of(position);
                }
            }
        }
        return Optional.empty();
    }

    /** Cuántas esperan. */
    public int waiting() {
        int waiting = 0;
        for (Entry entry : entries.values()) {
            if (!entry.active) {
                waiting++;
            }
        }
        return waiting;
    }

    /**
     * Segundos hasta que empiece a colocar la edición de {@code owner} (0 si ya coloca), con el ritmo total
     * {@code blocksPerSecond}. Las que están en pausa no cuentan.
     */
    public double secondsUntilStart(K owner, int slots, double blocksPerSecond, Predicate<K> paused) {
        Entry target = entries.get(owner);
        if (target == null || target.active) {
            return 0.0;
        }
        List<Long> active = new ArrayList<>();
        List<Long> ahead = new ArrayList<>();
        for (Map.Entry<K, Entry> entry : entries.entrySet()) {
            Entry value = entry.getValue();
            if (paused.test(entry.getKey()) && !entry.getKey().equals(owner)) {
                continue;
            }
            if (value.active) {
                active.add(value.size - value.done);
            } else {
                ahead.add(value.size);
                if (entry.getKey().equals(owner)) {
                    break;
                }
            }
        }
        double[] starts = QueueEstimate.secondsUntilStart(
                active.stream().mapToLong(Long::longValue).toArray(),
                ahead.stream().mapToLong(Long::longValue).toArray(), slots, blocksPerSecond);
        return starts.length == 0 ? 0.0 : starts[starts.length - 1];
    }
}
