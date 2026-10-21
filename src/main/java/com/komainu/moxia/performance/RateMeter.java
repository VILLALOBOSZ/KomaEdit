package com.komainu.moxia.performance;

/**
 * Ritmo medio de algo que avanza a saltos (bloques colocados por segundo entre todas las ediciones): junta lo hecho
 * durante {@value #WINDOW_NANOS} ns y lo mezcla con la media anterior. Java puro.
 */
public final class RateMeter {
    static final long WINDOW_NANOS = 1_000_000_000L;
    private static final double SMOOTHING = 0.3;
    private long windowStart = Long.MIN_VALUE;
    private long count;
    private double perSecond = -1.0;

    public synchronized void record(long amount, long now) {
        if (windowStart == Long.MIN_VALUE) {
            windowStart = now;
        }
        count += Math.max(0L, amount);
        long elapsed = now - windowStart;
        if (elapsed >= WINDOW_NANOS) {
            double sample = count * 1_000_000_000.0 / elapsed;
            perSecond = perSecond < 0.0 ? sample : perSecond + SMOOTHING * (sample - perSecond);
            windowStart = now;
            count = 0L;
        }
    }

    /** Sin trabajo, la ventana se reinicia: el tiempo parado no baja la media. */
    public synchronized void idle() {
        windowStart = Long.MIN_VALUE;
        count = 0L;
    }

    /** Unidades por segundo; -1 si aún no se ha medido nada. */
    public synchronized double perSecond() {
        return perSecond;
    }
}
