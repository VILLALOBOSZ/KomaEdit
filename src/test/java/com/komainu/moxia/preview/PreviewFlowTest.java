package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewFlowTest {
    private static final long SECOND = 1_000_000_000L;
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** Mundo de prueba: id crudo por posición empaquetada; lo que no está es aire (0). */
    private static final class World implements ZoneCheck.World {
        private final Map<Long, Integer> blocks = new HashMap<>();
        private int reads;

        void set(int x, int y, int z, int rawId) {
            blocks.put(PackedBlockPlan.pack(x, y, z), rawId);
        }

        @Override
        public int rawId(int x, int y, int z) {
            reads++;
            return blocks.getOrDefault(PackedBlockPlan.pack(x, y, z), 0);
        }
    }

    /** Anota cada llamada de la vista previa. */
    private static final class Recorder implements PreviewFlow.Hooks<PackedBlockPlan> {
        private final List<String> calls = new ArrayList<>();
        private final List<PackedBlockPlan> applied = new ArrayList<>();
        private long mismatch;

        @Override
        public void hide(UUID player, PackedBlockPlan plan) {
            calls.add("hide " + name(player));
        }

        @Override
        public void apply(UUID player, PackedBlockPlan plan) {
            calls.add("apply " + name(player));
            applied.add(plan);
        }

        @Override
        public void changed(UUID player, PackedBlockPlan plan, long mismatch) {
            calls.add("changed " + name(player));
            this.mismatch = mismatch;
        }

        @Override
        public void expired(UUID player, PackedBlockPlan plan) {
            calls.add("expired " + name(player));
        }

        private static String name(UUID player) {
            return player.equals(ALICE) ? "alice" : "bob";
        }
    }

    /** Un muro de 4×3 de piedra (id 7) sobre lo que había en el mundo, como lo dejaría el cálculo. */
    private static PackedBlockPlan wall(World world) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(12);
        for (int x = 0; x < 4; x++) {
            for (int y = 60; y < 63; y++) {
                builder.addConditionalPattern(BlockVector3.at(x, y, 5), world.rawId(x, y, 5));
            }
        }
        return builder.build();
    }

    @Test
    void confirmedPlanIsTheSameOneComputedOnce() {
        World world = new World();
        world.set(1, 60, 5, 3);
        int[] computations = {0};
        Supplier<PackedBlockPlan> operation = () -> {
            computations[0]++;
            return wall(world);
        };
        Recorder recorder = new Recorder();
        PreviewFlow<PackedBlockPlan> flow = new PreviewFlow<>(recorder, 90 * SECOND);

        PackedBlockPlan computed = operation.get();
        flow.hold(ALICE, computed, ZoneCheck.Watch.of(computed), 0L);
        assertTrue(flow.confirm(ALICE, 5 * SECOND));
        assertFalse(flow.confirm(ALICE, 5 * SECOND), "confirmar dos veces no aplica dos veces");
        flow.tick(player -> world, 5 * SECOND, 1_000);

        assertEquals(List.of("hide alice", "apply alice"), recorder.calls);
        assertSame(computed, recorder.applied.getFirst(), "se aplica el mismo plan, sin recalcular");
        assertEquals(1, computations[0]);
        assertEquals(12, world.reads - 12, "solo se releen las posiciones vigiladas");
        assertEquals(0, flow.size());
    }

    @Test
    void changeInTheZoneIsDetectedAndNothingIsApplied() {
        World world = new World();
        PackedBlockPlan plan = wall(world);
        Recorder recorder = new Recorder();
        PreviewFlow<PackedBlockPlan> flow = new PreviewFlow<>(recorder, 90 * SECOND);
        flow.hold(ALICE, plan, ZoneCheck.Watch.of(plan), 0L);

        world.set(2, 61, 5, 9);
        assertTrue(flow.confirm(ALICE, SECOND));
        flow.tick(player -> world, SECOND, 1_000);

        assertEquals(List.of("hide alice", "changed alice"), recorder.calls);
        assertTrue(recorder.applied.isEmpty());
        assertEquals(PackedBlockPlan.pack(2, 61, 5), recorder.mismatch, "dice dónde cambió");
    }

    @Test
    void extraWatchCatchesChangesOutsideThePlan() {
        World world = new World();
        PackedBlockPlan plan = wall(world);
        PackedBlockPlan.Builder around = PackedBlockPlan.builder(1);
        around.addConditionalPattern(BlockVector3.at(9, 60, 5), 0);
        PackedBlockPlan neighbour = around.build();
        Recorder recorder = new Recorder();
        PreviewFlow<PackedBlockPlan> flow = new PreviewFlow<>(recorder, 90 * SECOND);
        flow.hold(ALICE, plan, ZoneCheck.Watch.of(plan).then(ZoneCheck.Watch.of(neighbour)), 0L);

        world.set(9, 60, 5, 4);
        flow.confirm(ALICE, SECOND);
        flow.tick(player -> world, SECOND, 1_000);
        assertEquals(List.of("hide alice", "changed alice"), recorder.calls, "un cambio junto al plan también cuenta");
    }

    @Test
    void checkingIsSpreadOverTicks() {
        World world = new World();
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(1_000);
        for (int index = 0; index < 1_000; index++) {
            builder.addConditionalPattern(BlockVector3.at(index, 64, 0), 0);
        }
        PackedBlockPlan plan = builder.build();
        Recorder recorder = new Recorder();
        PreviewFlow<PackedBlockPlan> flow = new PreviewFlow<>(recorder, 90 * SECOND);
        flow.hold(ALICE, plan, ZoneCheck.Watch.of(plan), 0L);
        flow.confirm(ALICE, 0L);
        for (int tick = 1; tick < 10; tick++) {
            flow.tick(player -> world, tick, 100);
            assertTrue(recorder.calls.isEmpty(), "aún comprobando en el tick " + tick);
            assertEquals(tick * 100, flow.status(ALICE, tick).orElseThrow().checked());
        }
        flow.tick(player -> world, 10, 100);
        assertEquals(List.of("hide alice", "apply alice"), recorder.calls);
    }

    @Test
    void previewsExpireAndAreHiddenExactlyOnce() {
        World world = new World();
        PackedBlockPlan plan = wall(world);
        Recorder recorder = new Recorder();
        PreviewFlow<PackedBlockPlan> flow = new PreviewFlow<>(recorder, 90 * SECOND);
        flow.hold(ALICE, plan, ZoneCheck.Watch.of(plan), 0L, 10 * SECOND);
        flow.hold(BOB, plan, ZoneCheck.Watch.of(plan), 0L, 10 * SECOND);

        flow.tick(player -> world, 9 * SECOND, 1_000);
        assertTrue(recorder.calls.isEmpty());
        assertEquals(SECOND, flow.status(ALICE, 9 * SECOND).orElseThrow().remainingNanos());

        assertFalse(flow.confirm(BOB, 10 * SECOND), "confirmar tarde no aplica");
        flow.tick(player -> world, 10 * SECOND, 1_000);
        flow.tick(player -> world, 20 * SECOND, 1_000);
        assertEquals(List.of("hide bob", "expired bob", "hide alice", "expired alice"), recorder.calls);
        assertEquals(0, flow.size());
        assertFalse(flow.discard(ALICE), "ya no queda nada que retirar");
    }

    @Test
    void everyWayOutHidesTheDisplays() {
        World world = new World();
        PackedBlockPlan first = wall(world);
        PackedBlockPlan second = wall(world);
        Recorder recorder = new Recorder();
        PreviewFlow<PackedBlockPlan> flow = new PreviewFlow<>(recorder, 90 * SECOND);

        flow.hold(ALICE, first, ZoneCheck.Watch.of(first), 0L);
        flow.hold(ALICE, second, ZoneCheck.Watch.of(second), 0L);
        assertEquals(List.of("hide alice"), recorder.calls, "una vista previa nueva retira la anterior");
        assertSame(second, flow.plan(ALICE).orElseThrow());

        assertTrue(flow.discard(ALICE), "cancelar");
        flow.hold(BOB, first, ZoneCheck.Watch.of(first), 0L);
        flow.confirm(BOB, 0L);
        flow.tick(player -> null, 0L, 1_000);
        assertEquals(List.of("hide alice", "hide alice", "hide bob"), recorder.calls,
                "si el jugador se va o cambia de mundo mientras se confirma, se retira sin aplicar");

        flow.hold(ALICE, first, ZoneCheck.Watch.of(first), 0L);
        flow.hold(BOB, second, ZoneCheck.Watch.of(second), 0L);
        flow.clear();
        assertEquals(5, recorder.calls.stream().filter(call -> call.startsWith("hide")).count(),
                "al parar el servidor se retiran todas");
        assertEquals(0, flow.size());
        assertTrue(recorder.applied.isEmpty());
    }

    @Test
    void zoneCheckSkipsUnconditionalEntriesAndStopsAtTheFirstChange() {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(4);
        builder.addPattern(0, 0, 0);
        builder.addConditionalPattern(BlockVector3.at(1, 0, 0), 5);
        builder.addConditionalPattern(BlockVector3.at(2, 0, 0), 6);
        builder.addConditionalPattern(BlockVector3.at(3, 0, 0), 0);
        World world = new World();
        world.set(1, 0, 0, 5);
        ZoneCheck check = new ZoneCheck(ZoneCheck.Watch.of(builder.build()));
        assertTrue(check.step(world, 10));
        assertTrue(check.changed());
        assertEquals(PackedBlockPlan.pack(2, 0, 0), check.mismatch());
        assertEquals(3, check.checked(), "no sigue tras la primera diferencia");
        assertEquals(2, world.reads, "la entrada sin estado esperado no se lee");

        assertTrue(new ZoneCheck(ZoneCheck.Watch.EMPTY).step(world, 1));
        assertFalse(new ZoneCheck(null).changed());
    }
}
