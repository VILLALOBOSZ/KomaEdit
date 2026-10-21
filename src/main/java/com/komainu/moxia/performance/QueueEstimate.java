package com.komainu.moxia.performance;

/**
 * Cuándo empezará cada edición que espera en la cola. Las que colocan se reparten el ritmo total a partes iguales
 * ({@link FairShare}); cuando una acaba, entra la primera que espera. Se simula ese reparto con el ritmo medido.
 *
 * <p>Java puro.</p>
 */
public final class QueueEstimate {
    /** Ritmo que se supone mientras no se ha medido ninguno: unos 1 000 bloques por tick. */
    public static final double DEFAULT_BLOCKS_PER_SECOND = 20_000.0;

    private QueueEstimate() {
    }

    /**
     * @param activeRemaining bloques que le quedan a cada edición que ya coloca
     * @param waiting         bloques de cada edición que espera, por orden de llegada
     * @param slots           ediciones que pueden colocar a la vez ({@code maxConcurrentJobs})
     * @param blocksPerSecond ritmo total de colocación (se reparte entre las que colocan)
     * @return segundos hasta que empiece cada edición que espera
     */
    public static double[] secondsUntilStart(long[] activeRemaining, long[] waiting, int slots,
                                             double blocksPerSecond) {
        double rate = blocksPerSecond > 0.0 ? blocksPerSecond : DEFAULT_BLOCKS_PER_SECOND;
        int capacity = Math.max(1, slots);
        double[] starts = new double[waiting.length];
        double[] running = new double[activeRemaining.length + waiting.length];
        int count = 0;
        for (long remaining : activeRemaining) {
            running[count++] = Math.max(0L, remaining);
        }
        int nextWaiting = 0;
        double now = 0.0;
        while (nextWaiting < waiting.length) {
            // Entran las que caben.
            while (count < capacity && nextWaiting < waiting.length) {
                starts[nextWaiting] = now;
                running[count++] = Math.max(0L, waiting[nextWaiting++]);
            }
            if (nextWaiting >= waiting.length) {
                break;
            }
            // La que antes acabe deja sitio: todas avanzan a partes iguales hasta entonces.
            int first = 0;
            for (int index = 1; index < count; index++) {
                if (running[index] < running[first]) {
                    first = index;
                }
            }
            double each = rate / count;
            double elapsed = running[first] / each;
            now += elapsed;
            double done = running[first];
            for (int index = 0; index < count; index++) {
                running[index] -= done;
            }
            running[first] = running[--count];
        }
        return starts;
    }
}
