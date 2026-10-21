package com.komainu.moxia.performance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Reparto por turnos (round-robin) del tiempo de un tick entre jugadores: cada uno recibe la parte que le toca de lo
 * que queda; lo que uno no gasta (porque acaba antes) pasa a los siguientes, y si al final de la vuelta sobra tiempo
 * se da otra a los que aún tienen trabajo. El primero de la vuelta cambia en cada tick, así nadie va siempre detrás.
 * Una edición enorme no bloquea a las pequeñas de otros: todas avanzan en cada tick.
 *
 * <p>Java puro con el reloj por parámetro.</p>
 */
public final class FairShare {
    /** Vueltas como mucho en un tick (si todos acaban antes, no hacen falta más). */
    static final int MAX_ROUNDS = 4;
    /**
     * Si un turno dice que le queda trabajo pero no gasta ni esto, está esperando a otra cosa (un cálculo en otro
     * hilo): no se le dan más vueltas en este tick.
     */
    static final long IDLE_NANOS = 20_000L;

    private FairShare() {
    }

    /** El trabajo de un dueño hasta {@code deadline}. */
    @FunctionalInterface
    public interface Work<K> {
        /** @return {@code true} si aún le queda trabajo */
        boolean step(K owner, long deadline);
    }

    /**
     * Reparte el tiempo hasta {@code until} entre {@code owners}, empezando por el de la posición {@code offset}.
     *
     * @return nanosegundos que gastó cada dueño
     */
    public static <K> Map<K, Long> run(List<K> owners, int offset, long until, LongSupplier clock, Work<K> work) {
        Map<K, Long> used = new LinkedHashMap<>();
        if (owners.isEmpty()) {
            return used;
        }
        List<K> active = new ArrayList<>(owners.size());
        int start = Math.floorMod(offset, owners.size());
        for (int index = 0; index < owners.size(); index++) {
            active.add(owners.get((start + index) % owners.size()));
        }
        for (int round = 0; round < MAX_ROUNDS && !active.isEmpty(); round++) {
            List<K> next = new ArrayList<>(active.size());
            for (int index = 0; index < active.size(); index++) {
                long now = clock.getAsLong();
                if (now >= until) {
                    return used;
                }
                K owner = active.get(index);
                long share = Math.max(1L, (until - now) / (active.size() - index));
                boolean more = work.step(owner, now + share);
                long spent = Math.max(0L, clock.getAsLong() - now);
                used.merge(owner, spent, Long::sum);
                if (more && spent >= IDLE_NANOS) {
                    next.add(owner);
                }
            }
            active = next;
        }
        return used;
    }
}
