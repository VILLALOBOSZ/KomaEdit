package com.komainu.moxia.rebuild;

/**
 * Cuándo le toca a cada paso de una reconstrucción, en milésimas de tick de animación ({@link #UNIT} por tick).
 *
 * <p>El primer bloque sale tras la espera inicial; cada uno de los siguientes, {@code 1 / bloquesPorTick} ticks
 * después del anterior, más la pausa si empieza un grupo (capa, fase…) y el retraso extra según sea el mismo bloque
 * que el anterior o uno distinto. Con duración total, el intervalo entre bloques se ajusta para acabar justo a
 * tiempo; si las pausas y retrasos no caben, se encogen todos en proporción. Java puro.</p>
 */
public final class RebuildTimeline {
    /** Milésimas de tick por tick. */
    public static final long UNIT = 1_000L;

    private final long[] due;

    private RebuildTimeline(long[] due) {
        this.due = due;
    }

    public static RebuildTimeline of(RebuildModel model, RebuildSequence sequence, RebuildPace pace) {
        int steps = sequence.size();
        long[] due = new long[steps];
        if (steps == 0) {
            return new RebuildTimeline(due);
        }
        double[] extra = new double[steps];
        double extras = 0.0;
        int previousType = -1;
        for (int step = 0; step < steps; step++) {
            int type = model.type(model.target(sequence.entry(step)));
            if (step > 0) {
                extra[step] = (sequence.startsGroup(step) ? pace.layerPause() : 0.0)
                        + (type == previousType ? pace.sameDelay() : pace.otherDelay());
                extras += extra[step];
            }
            previousType = type;
        }
        double leadIn = pace.leadIn();
        double interval = 1.0 / pace.blocksPerTick();
        double scale = 1.0;
        if (pace.durationTicks() > 0.0) {
            double duration = pace.durationTicks();
            double fixed = leadIn + extras;
            if (steps == 1) {
                interval = 0.0;
                scale = fixed > duration && fixed > 0.0 ? duration / fixed : 1.0;
            } else if (fixed <= duration) {
                interval = (duration - fixed) / (steps - 1);
            } else {
                interval = 0.0;
                scale = duration / fixed;
            }
        }
        double time = leadIn * scale;
        due[0] = Math.round(time * UNIT);
        for (int step = 1; step < steps; step++) {
            time += interval + extra[step] * scale;
            due[step] = Math.round(time * UNIT);
        }
        return new RebuildTimeline(due);
    }

    public int size() {
        return due.length;
    }

    /** Cuándo le toca al paso, en milésimas de tick desde que empieza a levantarse. */
    public long due(int step) {
        return due[step];
    }

    /** Cuándo le toca al último paso (0 sin pasos). */
    public long end() {
        return due.length == 0 ? 0L : due[due.length - 1];
    }

    /**
     * Cuántos pasos tocan ya con el reloj en {@code clock}: el primero desde {@code from} al que aún no le toca (o
     * {@link #size()} si les toca a todos).
     */
    public int dueUntil(int from, long clock) {
        int low = Math.max(0, Math.min(from, due.length));
        int high = due.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (due[middle] <= clock) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }
}
