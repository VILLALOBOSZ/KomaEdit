package com.komainu.moxia.performance;

/**
 * Cuánto tiempo puede usar KomaEdit en el hilo del servidor en cada tick. Mide la duración media de los ticks y el
 * ritmo al que llegan (TPS), descuenta lo que gasta KomaEdit y deja el resto al servidor:
 *
 * <ul>
 *     <li>con el servidor holgado, el presupuesto configurado ({@code tickBudgetMillis});</li>
 *     <li>si el servidor ya gasta casi todo el tick por su cuenta, solo lo que le sobre hasta 45 ms;</li>
 *     <li>si va por debajo de 20 TPS (los ticks llegan tarde), el mínimo ({@code tickBudgetMinMillis}), para que las
 *     ediciones sigan avanzando sin hundirlo más.</li>
 * </ul>
 *
 * Java puro con el reloj por parámetro: las pruebas lo llevan con un reloj de mentira.
 */
public final class TickBudget {
    public static final long TICK_NANOS = 50_000_000L;
    /** Margen que siempre se deja libre al servidor. */
    static final long RESERVE_NANOS = 5_000_000L;
    /** Ticks que llegan más espaciados que esto (unos 19,4 TPS) son un servidor que va atrasado. */
    static final double BEHIND_INTERVAL_NANOS = TICK_NANOS * 1.03;
    /** Media móvil de unos 20 ticks (un segundo). */
    private static final double SMOOTHING = 0.1;

    /**
     * @param budgetNanos  lo que puede usar como mucho cada tick
     * @param minimumNanos lo que usa aunque el servidor vaya mal (nunca 0: la edición tiene que acabar)
     */
    public record Settings(long budgetNanos, long minimumNanos) {
        public static final Settings DEFAULT = new Settings(6_000_000L, 1_000_000L);

        public Settings {
            if (minimumNanos <= 0 || budgetNanos < minimumNanos) {
                throw new IllegalArgumentException("El presupuesto por tick tiene que ser mayor que el mínimo");
            }
        }

        public static Settings ofMillis(double budgetMillis, double minimumMillis) {
            return new Settings(Math.round(budgetMillis * 1_000_000.0), Math.round(minimumMillis * 1_000_000.0));
        }
    }

    private Settings settings;
    private double averageTickNanos = -1.0;
    private double averageIntervalNanos = -1.0;
    private double averageOwnNanos = -1.0;
    private long tickStart = Long.MIN_VALUE;
    private long previousStart = Long.MIN_VALUE;
    private long ownThisTick;
    private long allowance;

    public TickBudget(Settings settings) {
        this.settings = settings;
        this.allowance = settings.budgetNanos();
    }

    public synchronized void configure(Settings settings) {
        this.settings = settings;
        this.allowance = compute();
    }

    public synchronized Settings settings() {
        return settings;
    }

    /** Al empezar cada tick del servidor: calcula el presupuesto de este tick. */
    public synchronized void startTick(long now) {
        if (previousStart != Long.MIN_VALUE) {
            averageIntervalNanos = smooth(averageIntervalNanos, now - previousStart);
        }
        previousStart = now;
        tickStart = now;
        ownThisTick = 0L;
        allowance = compute();
    }

    /** Al acabar cada tick: apunta lo que duró y lo que gastó KomaEdit. */
    public synchronized void endTick(long now) {
        if (tickStart == Long.MIN_VALUE) {
            return;
        }
        averageTickNanos = smooth(averageTickNanos, now - tickStart);
        averageOwnNanos = smooth(averageOwnNanos, ownThisTick);
        tickStart = Long.MIN_VALUE;
    }

    /** Lo que ha gastado KomaEdit en este tick. */
    public synchronized void spent(long nanos) {
        ownThisTick += Math.max(0L, nanos);
    }

    /** Lo que puede usar KomaEdit en el tick en curso. */
    public synchronized long allowance() {
        return allowance;
    }

    private long compute() {
        if (averageTickNanos < 0.0) {
            return settings.budgetNanos();
        }
        if (averageIntervalNanos > BEHIND_INTERVAL_NANOS) {
            return settings.minimumNanos();
        }
        double others = Math.max(0.0, averageTickNanos - Math.max(0.0, averageOwnNanos));
        double headroom = TICK_NANOS - RESERVE_NANOS - others;
        return Math.clamp((long) headroom, settings.minimumNanos(), settings.budgetNanos());
    }

    private static double smooth(double average, long sample) {
        return average < 0.0 ? sample : average + SMOOTHING * (sample - average);
    }

    /** Ticks por segundo medidos (20 como mucho). */
    public synchronized double tps() {
        return averageIntervalNanos <= 0.0 ? 20.0 : Math.min(20.0, 1_000_000_000.0 / averageIntervalNanos);
    }

    /** Milisegundos medios de cada tick entero (con lo de KomaEdit). */
    public synchronized double mspt() {
        return Math.max(0.0, averageTickNanos) / 1_000_000.0;
    }

    /** Milisegundos medios que usa KomaEdit en cada tick. */
    public synchronized double ownMillis() {
        return Math.max(0.0, averageOwnNanos) / 1_000_000.0;
    }
}
