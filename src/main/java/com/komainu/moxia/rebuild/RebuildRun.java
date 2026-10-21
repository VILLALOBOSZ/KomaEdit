package com.komainu.moxia.rebuild;

import com.sk89q.worldedit.MaxChangedBlocksException;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.function.LongSupplier;

/**
 * Una reconstrucción en marcha, sin Minecraft: vacía las posiciones de la obra, la levanta paso a paso cuando le toca
 * a cada bloque ({@link RebuildTimeline}) y atiende pausar, seguir, acelerar, saltar al final y cancelar.
 *
 * <ol>
 *     <li><b>Vaciar:</b> cada posición que sigue como se leyó pasa a aire. Lo que alguien haya cambiado desde entonces
 *     se deja como está y no se reconstruye ({@link #kept()}).</li>
 *     <li><b>Levantar:</b> el reloj de la animación avanza una vez por tick ({@link #tick}) según la velocidad; cada
 *     llamada a {@link #place} pone los bloques a los que ya les toca, siempre que su sitio siga vacío. Si el
 *     presupuesto del tick no da para todos, el reloj no avanza en el tick siguiente: la animación va más despacio
 *     en vez de amontonar bloques.</li>
 *     <li><b>Saltar al final</b> pone lo que falta sin esperar al reloj; <b>cancelar</b> devuelve cada posición tocada
 *     a lo que había antes (en la selección, lo mismo que la obra), salvo las que alguien cambió mientras tanto.</li>
 * </ol>
 *
 * {@link #place} es el {@code Source} de la colocación por ventanas: respeta el plazo y el máximo de entradas, y la
 * sesión de WorldEdit la vacía quien la llama. Lo escrito puede no estar en el mundo hasta ese vaciado, así que cada
 * posición se mira y se escribe una sola vez por ventana y una ventana nunca junta dos fases.
 */
public final class RebuildRun {
    /** En qué está. */
    public enum Phase {
        CLEARING("vaciando"),
        BUILDING("levantando"),
        FINISHING("terminando"),
        RESTORING("deshaciendo"),
        DONE("terminada"),
        CANCELLED("cancelada");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** El mundo donde se escribe. */
    public interface World {
        /** Id crudo del estado de la posición, comparable con {@link RebuildModel#raw}. */
        int rawAt(long position);

        /** Si ese id crudo es aire (de cualquier tipo). */
        boolean air(int raw);

        /** Escribe la entrada {@code palette} del modelo ({@link RebuildModel#AIR} para vaciar). */
        void write(long position, int palette) throws MaxChangedBlocksException;
    }

    public static final double MIN_SPEED = 1.0 / 16.0;
    public static final double MAX_SPEED = 64.0;

    static final byte UNTOUCHED = 0;
    static final byte CLEARED = 1;
    static final byte BUILT = 2;
    static final byte KEPT = 3;
    static final byte RESTORED = 4;

    private final RebuildModel model;
    private final RebuildSequence sequence;
    private final RebuildTimeline timeline;
    private final World world;
    private final LongSupplier clock;
    private final byte[] status;
    private final IntArrayList placed = new IntArrayList();
    private Phase phase = Phase.CLEARING;
    private boolean finishAfterClear;
    private boolean paused;
    /** Si el reloj ya avanzó levantando: lo que toca al acabar de vaciar no cuenta como ir con retraso. */
    private boolean ticking;
    private double speed = 1.0;
    private long animation;
    private int clearCursor;
    private int step;
    private int restoreCursor;
    private int kept;
    private int writes;
    private long slowTicks;

    /**
     * @param clock el reloj de los plazos de {@link #place} (nanosegundos)
     */
    public RebuildRun(RebuildModel model, RebuildSequence sequence, RebuildTimeline timeline, World world,
                      LongSupplier clock) {
        if (sequence.size() != timeline.size()) {
            throw new IllegalArgumentException("El orden y el ritmo no son de la misma obra");
        }
        this.model = model;
        this.sequence = sequence;
        this.timeline = timeline;
        this.world = world;
        this.clock = clock;
        this.status = new byte[model.size()];
    }

    // ------------------------------------------------------------------ Mandos

    /** Una vez por tick del servidor: avanza el reloj de la animación si se está levantando y no está en pausa. */
    public void tick() {
        if (phase != Phase.BUILDING || paused) {
            return;
        }
        if (ticking && step < timeline.size() && timeline.due(step) <= animation) {
            // El tick anterior no pudo con todo lo que tocaba: se espera a ponerlo en vez de dejar que se amontone.
            slowTicks++;
            return;
        }
        ticking = true;
        animation += Math.round(speed * RebuildTimeline.UNIT);
    }

    /** Pone o quita la pausa. Solo se para lo animado: terminar y deshacer siguen aunque esté en pausa. */
    public boolean pause(boolean pause) {
        if (!active() || paused == pause) {
            return false;
        }
        paused = pause;
        return true;
    }

    /** Multiplica la velocidad de la animación (1 = el ritmo pedido), entre {@link #MIN_SPEED} y {@link #MAX_SPEED}. */
    public void speed(double value) {
        if (!Double.isFinite(value) || value < MIN_SPEED || value > MAX_SPEED) {
            throw new IllegalArgumentException("La velocidad va de ×1/16 a ×64");
        }
        speed = value;
    }

    /** Pone todo lo que falta sin esperar al reloj (si aún vacía, en cuanto acabe de vaciar). */
    public boolean skipToEnd() {
        if (phase == Phase.CLEARING) {
            finishAfterClear = true;
            paused = false;
            return true;
        }
        if (phase == Phase.BUILDING) {
            phase = Phase.FINISHING;
            paused = false;
            return true;
        }
        return false;
    }

    /** Deja cada posición tocada como estaba antes de empezar. */
    public boolean cancel() {
        if (phase == Phase.CLEARING || phase == Phase.BUILDING || phase == Phase.FINISHING) {
            phase = Phase.RESTORING;
            paused = false;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Trabajo

    /**
     * Trabaja hasta {@code maxEntries} entradas o hasta {@code deadline}: vacía, pone los bloques a los que ya les
     * toca, termina o deshace, según la fase.
     *
     * @return entradas recorridas (escritas, comprobadas o dejadas como estaban)
     */
    public int place(int maxEntries, long deadline) throws MaxChangedBlocksException {
        int processed = 0;
        while (processed < maxEntries && clock.getAsLong() < deadline) {
            switch (phase) {
                case CLEARING -> {
                    if (paused) {
                        return processed;
                    }
                    if (clearCursor >= model.size()) {
                        phase = finishAfterClear ? Phase.FINISHING : Phase.BUILDING;
                        if (processed > 0) {
                            // Lo vaciado sigue en el búfer de la sesión: tiene que llegar al mundo antes de mirar
                            // si cada sitio está vacío para levantar.
                            return processed;
                        }
                        continue;
                    }
                    clear(clearCursor);
                    clearCursor++;
                }
                case BUILDING -> {
                    if (step >= sequence.size()) {
                        phase = Phase.DONE;
                        return processed;
                    }
                    if (paused || step >= timeline.dueUntil(step, animation)) {
                        return processed;
                    }
                    build(step, true);
                    step++;
                }
                case FINISHING -> {
                    if (step >= sequence.size()) {
                        phase = Phase.DONE;
                        return processed;
                    }
                    build(step, false);
                    step++;
                }
                case RESTORING -> {
                    if (restoreCursor >= model.size()) {
                        phase = Phase.CANCELLED;
                        return processed;
                    }
                    restore(restoreCursor);
                    restoreCursor++;
                }
                default -> {
                    return processed;
                }
            }
            processed++;
        }
        // Sin plazo para la última comprobación: si ya está todo, se dice ahora y no en la llamada siguiente.
        if (phase == Phase.BUILDING && step >= sequence.size() || phase == Phase.FINISHING && step >= sequence.size()) {
            phase = Phase.DONE;
        } else if (phase == Phase.RESTORING && restoreCursor >= model.size()) {
            phase = Phase.CANCELLED;
        }
        return processed;
    }

    private void clear(int entry) throws MaxChangedBlocksException {
        long position = model.position(entry);
        int before = model.before(entry);
        int current = world.rawAt(position);
        if (before == RebuildModel.AIR ? world.air(current) : current == model.raw(before)) {
            if (before != RebuildModel.AIR) {
                world.write(position, RebuildModel.AIR);
                writes++;
            }
            status[entry] = CLEARED;
        } else {
            status[entry] = KEPT;
            kept++;
        }
    }

    private void build(int buildStep, boolean animated) throws MaxChangedBlocksException {
        int entry = sequence.entry(buildStep);
        if (status[entry] != CLEARED) {
            return;
        }
        long position = model.position(entry);
        if (world.air(world.rawAt(position))) {
            world.write(position, model.target(entry));
            writes++;
            status[entry] = BUILT;
            if (animated) {
                placed.add(entry);
            }
        } else {
            status[entry] = KEPT;
            kept++;
        }
    }

    private void restore(int entry) throws MaxChangedBlocksException {
        byte current = status[entry];
        int before = model.before(entry);
        long position = model.position(entry);
        if (current == CLEARED) {
            if (before == RebuildModel.AIR) {
                status[entry] = RESTORED;
            } else if (world.air(world.rawAt(position))) {
                world.write(position, before);
                writes++;
                status[entry] = RESTORED;
            } else {
                status[entry] = KEPT;
                kept++;
            }
        } else if (current == BUILT) {
            if (before == model.target(entry)) {
                status[entry] = RESTORED;
            } else if (world.rawAt(position) == model.raw(model.target(entry))) {
                world.write(position, before);
                writes++;
                status[entry] = RESTORED;
            } else {
                status[entry] = KEPT;
                kept++;
            }
        }
    }

    // ------------------------------------------------------------------ Estado

    public Phase phase() {
        return phase;
    }

    /** Terminada o cancelada: ya no queda nada que escribir. */
    public boolean done() {
        return phase == Phase.DONE || phase == Phase.CANCELLED;
    }

    /** Aún se puede pausar, saltar al final o cancelar. */
    public boolean active() {
        return phase == Phase.CLEARING || phase == Phase.BUILDING;
    }

    public boolean paused() {
        return paused;
    }

    public double speed() {
        return speed;
    }

    /** Si hay algo que hacer ahora mismo (en el tick actual); levantando, solo si a algún bloque ya le toca. */
    public boolean hasWorkNow() {
        return switch (phase) {
            case CLEARING -> !paused;
            case BUILDING -> !paused && (step >= sequence.size() || step < timeline.dueUntil(step, animation));
            case FINISHING, RESTORING -> true;
            case DONE, CANCELLED -> false;
        };
    }

    /** Lo hecho en la fase actual, para la barra. */
    public long progressDone() {
        return switch (phase) {
            case CLEARING -> clearCursor;
            case BUILDING, FINISHING, DONE -> step;
            case RESTORING, CANCELLED -> restoreCursor;
        };
    }

    /** El total de la fase actual. */
    public long progressTotal() {
        return switch (phase) {
            case CLEARING, RESTORING, CANCELLED -> model.size();
            case BUILDING, FINISHING, DONE -> sequence.size();
        };
    }

    /** Pasos ya puestos (o comprobados). */
    public int step() {
        return step;
    }

    /** El reloj de la animación, en milésimas de tick. */
    public long animation() {
        return animation;
    }

    /** Ticks de servidor que le faltan a la animación a esta velocidad (0 fuera de levantar). */
    public double remainingTicks() {
        if (phase != Phase.BUILDING && phase != Phase.CLEARING) {
            return 0.0;
        }
        return Math.max(0L, timeline.end() - animation) / (double) RebuildTimeline.UNIT / speed;
    }

    /** El grupo que se está levantando («capa 64», «paredes»…). */
    public String groupLabel() {
        return sequence.groupLabel(Math.min(step, sequence.size() - 1));
    }

    /** Posiciones dejadas como estaban porque alguien las cambió mientras tanto. */
    public int kept() {
        return kept;
    }

    /** Escrituras hechas (vaciar, poner y deshacer). */
    public int writes() {
        return writes;
    }

    /** Ticks en los que el reloj esperó porque el presupuesto no dio para todo lo que tocaba. */
    public long slowTicks() {
        return slowTicks;
    }

    /** Las entradas puestas por la animación desde la última llamada (para partículas y sonido). */
    public IntArrayList drainPlaced() {
        IntArrayList drained = new IntArrayList(placed);
        placed.clear();
        return drained;
    }

    public RebuildModel model() {
        return model;
    }

    public RebuildSequence sequence() {
        return sequence;
    }

    public RebuildTimeline timeline() {
        return timeline;
    }
}
