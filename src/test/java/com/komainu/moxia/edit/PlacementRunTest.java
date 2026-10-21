package com.komainu.moxia.edit;

import com.komainu.moxia.performance.FairShare;
import com.komainu.moxia.performance.TickBudget;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La colocación por ventanas con un mundo y un reloj de mentira que se portan como WorldEdit: lo escrito se queda en
 * un búfer hasta el vaciado, pasar el búfer al mundo no se puede cortar y la luz, los vecinos y el envío sí, bloque a
 * bloque. Se comprueba que cada tick respeta su presupuesto (también con el servidor a menos de 20 TPS), que todo
 * acaba en el mundo con sus efectos, que el historial deshace exactamente lo colocado (también si se cancela a
 * medias) y que lo que se lee al acabar cada ventana ya es lo colocado.
 */
class PlacementRunTest {
    private static final long US = 1_000L;
    private static final long MS = 1_000_000L;
    /** Lo que cuesta {@code setBlock} en la sesión (patrón, historial, búfer). */
    private static final long WRITE = 2 * US;
    /** Pasar un bloque del búfer al mundo (no se puede cortar). */
    private static final long FLUSH = US;
    /** Luz, vecinos y envío de un bloque (se corta al plazo). */
    private static final long EFFECTS = 4 * US;

    /** Reloj que solo avanza cuando algo gasta. */
    static final class Clock {
        long now;

        void spend(long nanos) {
            now += nanos;
        }
    }

    /** Un mundo con la sesión de WorldEdit: búfer, historial, vaciado y efectos. */
    static final class FakeWorld implements PlacementRun.Session {
        final Clock clock;
        final Map<Long, Integer> blocks = new HashMap<>();
        final Map<Long, Integer> buffer = new LinkedHashMap<>();
        final List<long[]> history = new ArrayList<>();
        final ArrayDeque<Long> effectsPending = new ArrayDeque<>();
        final Set<Long> effectsDone = new HashSet<>();
        /** Lo que cuestan ahora los efectos de cada bloque (una prueba lo sube para cortar una ventana a medias). */
        long effects = EFFECTS;
        int flushes;

        FakeWorld(Clock clock) {
            this.clock = clock;
        }

        int rawIdAt(long packed) {
            return blocks.getOrDefault(packed, 0);
        }

        void set(long packed, int state) {
            clock.spend(WRITE);
            history.add(new long[]{packed, buffer.getOrDefault(packed, rawIdAt(packed))});
            buffer.put(packed, state);
        }

        @Override
        public PlacementRun.Flush flush() {
            boolean[] moved = {false};
            return deadline -> {
                if (!moved[0]) {
                    for (Map.Entry<Long, Integer> entry : buffer.entrySet()) {
                        clock.spend(FLUSH);
                        blocks.put(entry.getKey(), entry.getValue());
                        effectsPending.add(entry.getKey());
                    }
                    buffer.clear();
                    moved[0] = true;
                    flushes++;
                    return false;
                }
                while (!effectsPending.isEmpty() && clock.now < deadline) {
                    clock.spend(effects);
                    effectsDone.add(effectsPending.poll());
                }
                return effectsPending.isEmpty();
            };
        }

        /** Lo que hace {@code EditSession.close()}: vaciarlo todo de golpe. */
        void close() {
            PlacementRun.Flush flush = flush();
            while (!flush.resume(Long.MAX_VALUE)) {
                // Hasta el final.
            }
        }

        void undo() {
            for (int index = history.size() - 1; index >= 0; index--) {
                long[] change = history.get(index);
                if (change[1] == 0) {
                    blocks.remove(change[0]);
                } else {
                    blocks.put(change[0], (int) change[1]);
                }
            }
            history.clear();
        }
    }

    /** Un plan aplicado como en {@code BatchEditService}: comprobar lo esperado, escribir y apuntar capturas. */
    static final class Job {
        final FakeWorld world;
        final PackedBlockPlan plan;
        final PlanApplication application;
        final PlacementRun run;
        final List<long[]> pendingCaptures = new ArrayList<>();
        final Map<Integer, Integer> captured = new HashMap<>();
        boolean bufferEmptyAtEveryWindow = true;

        Job(FakeWorld world, PackedBlockPlan plan) {
            this.world = world;
            this.plan = plan;
            this.application = new PlanApplication(plan);
            PlanApplication.Target target = new PlanApplication.Target() {
                @Override
                public int rawIdAt(int x, int y, int z) {
                    return world.rawIdAt(PackedBlockPlan.pack(x, y, z));
                }

                @Override
                public boolean write(int index, int x, int y, int z) {
                    long packed = PackedBlockPlan.pack(x, y, z);
                    world.set(packed, 100 + index % 50);
                    int capture = plan.captureIndex(index);
                    if (capture != PackedBlockPlan.NO_CAPTURE) {
                        pendingCaptures.add(new long[]{capture, packed});
                    }
                    return true;
                }
            };
            PlacementRun.Source source = new PlacementRun.Source() {
                @Override
                public int place(int maxEntries, long deadline) throws MaxChangedBlocksException {
                    return application.step(target, maxEntries, deadline, () -> world.clock.now);
                }

                @Override
                public boolean done() {
                    return application.done();
                }
            };
            this.run = new PlacementRun(source, world, () -> world.clock.now, this::windowFlushed);
        }

        private void windowFlushed() {
            bufferEmptyAtEveryWindow &= world.buffer.isEmpty();
            for (long[] capture : pendingCaptures) {
                captured.put((int) capture[0], world.rawIdAt(capture[1]));
            }
            pendingCaptures.clear();
        }

        /** Lo que hace {@code BatchEditService.finish} al cancelar: parar y cerrar la sesión. */
        void cancel() {
            application.stop();
            world.close();
            windowFlushed();
        }
    }

    /** Un //set de {@code sizeX}×{@code sizeY}×{@code sizeZ} con capturas (como un Build de instancia). */
    static PackedBlockPlan box(int sizeX, int sizeY, int sizeZ, int offsetX) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(sizeX * sizeY * sizeZ);
        int capture = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    builder.addCapturedPattern(BlockVector3.at(offsetX + x, 64 + y, z), capture++);
                }
            }
        }
        return builder.build();
    }

    private static Map<Long, Integer> initial(FakeWorld world, PackedBlockPlan plan) {
        for (int index = 0; index < plan.size(); index += 3) {
            world.blocks.put(plan.packedPosition(index), 1);
        }
        return new HashMap<>(world.blocks);
    }

    @Test
    void everyTickStaysWithinItsBudgetAndEverythingLands() throws MaxChangedBlocksException {
        Clock clock = new Clock();
        FakeWorld world = new FakeWorld(clock);
        PackedBlockPlan plan = box(40, 20, 40, 0);
        Map<Long, Integer> before = initial(world, plan);
        Job job = new Job(world, plan);
        long budget = 5 * MS;
        long worst = 0;
        int ticks = 0;
        while (!job.run.finished()) {
            long start = clock.now;
            job.run.step(start + budget, true);
            worst = Math.max(worst, clock.now - start);
            clock.now = start + 50 * MS;
            ticks++;
            assertTrue(ticks < 10_000, "tiene que acabar");
        }
        assertTrue(worst <= budget + EFFECTS, "ningún tick pasa del presupuesto: " + worst / 1_000 + " µs");
        assertTrue(job.run.windows() > 10, "va por ventanas, no todo de golpe al final");
        assertTrue(world.buffer.isEmpty());
        for (int index = 0; index < plan.size(); index++) {
            long packed = plan.packedPosition(index);
            assertEquals(100 + index % 50, world.rawIdAt(packed));
            assertTrue(world.effectsDone.contains(packed), "luz, vecinos y envío también");
        }
        assertTrue(job.bufferEmptyAtEveryWindow, "al acabar cada ventana ya está todo en el mundo");
        for (int index = 0; index < plan.size(); index++) {
            assertEquals(100 + index % 50, job.captured.get(plan.captureIndex(index)),
                    "la captura lee lo colocado, no lo que había antes");
        }
        world.undo();
        assertEquals(before, world.blocks, "un solo //undo lo deshace todo");
    }

    @Test
    void belowTwentyTpsItKeepsGoingWithTheMinimum() throws MaxChangedBlocksException {
        Clock clock = new Clock();
        FakeWorld world = new FakeWorld(clock);
        Job job = new Job(world, box(30, 10, 30, 0));
        TickBudget budget = new TickBudget(new TickBudget.Settings(6 * MS, MS));
        long worst = 0;
        int ticks = 0;
        while (!job.run.finished()) {
            // El servidor ya tarda 62 ms por tick él solo: 16 TPS.
            budget.startTick(clock.now);
            long start = clock.now;
            job.run.step(start + budget.allowance(), true);
            long used = clock.now - start;
            budget.spent(used);
            if (ticks > 3) {
                worst = Math.max(worst, used);
            }
            budget.endTick(start + 62 * MS + used);
            clock.now = start + 62 * MS + used;
            ticks++;
            assertTrue(ticks < 100_000);
        }
        assertEquals(MS, budget.allowance());
        assertTrue(worst <= MS + EFFECTS, "con el servidor atrasado solo usa el mínimo: " + worst / 1_000 + " µs");
    }

    @Test
    void cancellingMidWindowLeavesExactlyWhatUndoCovers() throws MaxChangedBlocksException {
        Clock clock = new Clock();
        FakeWorld world = new FakeWorld(clock);
        PackedBlockPlan plan = box(50, 10, 50, 0);
        Map<Long, Integer> before = initial(world, plan);
        Job job = new Job(world, plan);
        // Unos ticks y se corta con una ventana a medias (bloques en el mundo, efectos sin acabar): en el último, los
        // efectos cuestan diez veces más de lo medido y la ventana no da tiempo a vaciarse.
        for (int tick = 0; tick < 12; tick++) {
            if (tick == 11) {
                world.effects = 10 * EFFECTS;
            }
            long start = clock.now;
            job.run.step(start + 2 * MS, true);
            clock.now = start + 50 * MS;
        }
        assertTrue(job.run.flushing(), "la prueba corta con una ventana a medio vaciar");
        job.cancel();
        assertTrue(world.buffer.isEmpty());
        assertEquals(world.history.size(), job.application.processed(), "historial = lo recorrido");
        assertTrue(job.application.processed() < plan.size());
        for (int index = 0; index < job.application.processed(); index++) {
            assertEquals(100 + index % 50, world.rawIdAt(plan.packedPosition(index)));
        }
        for (int index = job.application.processed(); index < plan.size(); index++) {
            assertEquals(before.getOrDefault(plan.packedPosition(index), 0),
                    world.rawIdAt(plan.packedPosition(index)), "lo de después no se tocó");
        }
        world.undo();
        assertEquals(before, world.blocks);
    }

    @Test
    void pausedOnlyFinishesTheWindowInProgress() throws MaxChangedBlocksException {
        Clock clock = new Clock();
        FakeWorld world = new FakeWorld(clock);
        Job job = new Job(world, box(40, 10, 40, 0));
        job.run.step(clock.now + 3 * MS, true);
        long placed = job.application.processed();
        for (int tick = 0; tick < 50; tick++) {
            long start = clock.now;
            job.run.step(start + 3 * MS, false);
            clock.now = start + 50 * MS;
        }
        assertEquals(placed, job.application.processed(), "en pausa no escribe más");
        assertFalse(job.run.flushing(), "pero acaba de vaciar lo escrito: nada se queda sin luz ni sin enviar");
        assertTrue(world.buffer.isEmpty());
        job.run.step(clock.now + 3 * MS, true);
        assertTrue(job.application.processed() > placed, "al reanudar sigue donde iba");
    }

    @Test
    void severalEditsShareTheTickAndTheSmallOneIsNotStuckBehind() {
        Clock clock = new Clock();
        Map<String, Job> jobs = new LinkedHashMap<>();
        jobs.put("grande", new Job(new FakeWorld(clock), box(200, 40, 200, 0)));
        jobs.put("mediana", new Job(new FakeWorld(clock), box(60, 20, 60, 1_000)));
        jobs.put("pequeña", new Job(new FakeWorld(clock), box(20, 5, 20, 2_000)));
        long budget = 6 * MS;
        long worst = 0;
        int smallDoneAt = -1;
        for (int tick = 0; tick < 400 && smallDoneAt < 0; tick++) {
            List<String> owners = jobs.entrySet().stream().filter(entry -> !entry.getValue().run.finished())
                    .map(Map.Entry::getKey).toList();
            long start = clock.now;
            FairShare.run(owners, tick, start + budget, () -> clock.now, (owner, deadline) -> {
                try {
                    return jobs.get(owner).run.step(deadline, true);
                } catch (MaxChangedBlocksException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            worst = Math.max(worst, clock.now - start);
            clock.now = start + 50 * MS;
            if (jobs.get("pequeña").run.finished()) {
                smallDoneAt = tick;
            }
        }
        // 2 000 bloques a unos 7 µs son 14 ms: con un tercio del tick, en pocos ticks.
        assertTrue(smallDoneAt >= 0 && smallDoneAt < 15, "la pequeña acaba pronto: tick " + smallDoneAt);
        assertTrue(jobs.get("grande").application.processed() > 0, "la grande también avanza");
        assertTrue(jobs.get("mediana").application.processed() > 0);
        assertTrue(worst <= budget + EFFECTS + FLUSH * PlacementRun.Pacer.FIRST_WINDOW,
                "entre todas no pasan del tick: " + worst / 1_000 + " µs");
    }
}
